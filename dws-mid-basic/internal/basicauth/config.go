// Package basicauth holds every rule of the dws-mid-basic Wasm guest: parsing and validating the
// guest config, deciding which requests it applies to, and encoding the Basic credential.
//
// It imports no http-wasm SDK package, so all of it runs under plain `go test`. It also never
// logs: the only text it produces is ConfigError, which names fields and rules, never values.
package basicauth

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"strings"
	"unicode"
)

// Config is a validated guest config: one endpoint and one Basic credential.
type Config struct {
	Endpoint string
	Username string
	Password string
}

// ConfigError reports every problem found in a guest config. Each problem names a field and the
// rule it breaks. It never contains a configured value or any text of the input, because
// encoding/json errors quote the offending character and the input holds a credential.
type ConfigError struct {
	Problems []string
}

func (e *ConfigError) Error() string {
	return "dws-mid-basic: " + strings.Join(e.Problems, "; ")
}

// field is one string field as it appeared in the config object.
type field struct {
	seen  bool   // key present (possibly null)
	null  bool   // value was JSON null
	typed bool   // value was something other than a string or null
	value string // string value, when it was one
}

// Parse validates raw, the bytes of the guest config. The config must be one JSON object with
// exactly the string fields endpoint, username and password; unknown, duplicate and mistyped
// fields are rejected. On failure it returns a *ConfigError listing every problem, so one fix
// round is enough, and a nil Config.
func Parse(raw []byte) (*Config, error) {
	fields, problems, fatal := readObject(raw)
	if fatal != "" {
		return nil, &ConfigError{Problems: []string{fatal}}
	}

	cfg := &Config{}
	problems = append(problems, checkEndpoint(fields["endpoint"], &cfg.Endpoint)...)
	problems = append(problems, checkUsername(fields["username"], &cfg.Username)...)
	problems = append(problems, checkPassword(fields["password"], &cfg.Password)...)
	if len(problems) > 0 {
		return nil, &ConfigError{Problems: problems}
	}
	return cfg, nil
}

// readObject streams the top-level object token by token. Doing it by hand (instead of
// Decoder.DisallowUnknownFields into a struct) gives exact, case-sensitive field names, detects
// duplicates and trailing data, and keeps the error text free of encoding/json messages.
// fatal is set when the config is not usable as an object at all.
func readObject(raw []byte) (fields map[string]*field, problems []string, fatal string) {
	if len(bytes.TrimSpace(raw)) == 0 {
		return nil, nil, "config is empty"
	}
	const notJSON = "config is not valid JSON"

	dec := json.NewDecoder(bytes.NewReader(raw))
	tok, err := dec.Token()
	if err != nil {
		return nil, nil, notJSON
	}
	if d, ok := tok.(json.Delim); !ok || d != '{' {
		return nil, nil, "config must be a JSON object"
	}

	fields = map[string]*field{}
	for dec.More() {
		keyTok, err := dec.Token()
		if err != nil {
			return nil, nil, notJSON
		}
		key, ok := keyTok.(string)
		if !ok {
			return nil, nil, notJSON
		}
		var value json.RawMessage
		if err := dec.Decode(&value); err != nil {
			return nil, nil, notJSON
		}

		switch key {
		case "endpoint", "username", "password":
		default:
			problems = append(problems, unknownFieldProblem(key))
			continue
		}
		if f := fields[key]; f != nil {
			problems = append(problems, key+" is specified more than once")
			continue
		}
		fields[key] = decodeField(value)
	}

	// The closing brace, then nothing else.
	if end, err := dec.Token(); err != nil {
		return nil, nil, notJSON
	} else if d, ok := end.(json.Delim); !ok || d != '}' {
		return nil, nil, notJSON
	}
	if _, err := dec.Token(); !errors.Is(err, io.EOF) {
		return nil, nil, notJSON
	}
	return fields, problems, ""
}

func decodeField(value json.RawMessage) *field {
	f := &field{seen: true}
	trimmed := bytes.TrimSpace(value)
	switch {
	case string(trimmed) == "null":
		f.null = true
	case len(trimmed) > 0 && trimmed[0] == '"':
		if err := json.Unmarshal(trimmed, &f.value); err != nil {
			f.typed = true
		}
	default:
		f.typed = true
	}
	return f
}

// unknownFieldProblem names the field only when it looks like an identifier. A key is not a
// credential value, but a malformed config could put one there, so anything else is not echoed.
func unknownFieldProblem(key string) string {
	if isIdentifier(key) {
		return key + " is not a known field"
	}
	return "a field with an unrecognized name is not a known field"
}

func isIdentifier(s string) bool {
	if s == "" || len(s) > 32 {
		return false
	}
	for _, r := range s {
		ok := r == '_' || (r >= '0' && r <= '9') || (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z')
		if !ok {
			return false
		}
	}
	return true
}

// required applies the rules every field shares: present, a string, not null and not empty. It
// returns the value, or the problems that make the field unusable.
func required(name string, f *field) (string, []string) {
	switch {
	case f == nil || f.null:
		return "", []string{name + " is required"}
	case f.typed:
		return "", []string{name + " must be a string"}
	case f.value == "":
		return "", []string{name + " is required"}
	}
	return f.value, nil
}

func checkEndpoint(f *field, out *string) []string {
	v, problems := required("endpoint", f)
	if problems != nil {
		return problems
	}
	if strings.ContainsAny(v, "/?#") {
		return []string{"endpoint must not contain '/', '?' or '#'"}
	}
	*out = v
	return nil
}

func checkUsername(f *field, out *string) []string {
	v, problems := required("username", f)
	if problems != nil {
		return problems
	}
	if strings.Contains(v, ":") {
		problems = append(problems, "username must not contain ':' (RFC 7617)")
	}
	if hasControl(v) {
		problems = append(problems, "username must not contain control characters")
	}
	if problems != nil {
		return problems
	}
	*out = v
	return nil
}

func checkPassword(f *field, out *string) []string {
	v, problems := required("password", f)
	if problems != nil {
		return problems
	}
	if hasControl(v) {
		return []string{"password must not contain control characters"}
	}
	*out = v
	return nil
}

func hasControl(s string) bool {
	for _, r := range s {
		if unicode.IsControl(r) {
			return true
		}
	}
	return false
}
