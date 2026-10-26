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
	"slices"
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

// The guest config's field names, compared case-sensitively.
const (
	fieldEndpoint = "endpoint"
	fieldUsername = "username"
	fieldPassword = "password"
)

// fieldKind is what kind of JSON value a present field held.
type fieldKind int

const (
	kindString    fieldKind = iota // a JSON string; value holds it
	kindNull                       // JSON null
	kindNonString                  // anything else: number, bool, object, array
)

// field is one field as it appeared in the config object. A missing field has no *field at all.
type field struct {
	kind  fieldKind
	value string // set when kind is kindString
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

	endpoint, endpointProblems := checkEndpoint(fields[fieldEndpoint])
	username, usernameProblems := checkUsername(fields[fieldUsername])
	password, passwordProblems := checkPassword(fields[fieldPassword])
	problems = slices.Concat(problems, endpointProblems, usernameProblems, passwordProblems)
	if len(problems) > 0 {
		return nil, &ConfigError{Problems: problems}
	}
	return &Config{Endpoint: endpoint, Username: username, Password: password}, nil
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
		case fieldEndpoint, fieldUsername, fieldPassword:
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

// decodeField classifies value, which the decoder has already checked is valid JSON. null is
// tested first because json.Unmarshal accepts it into a string as a no-op.
func decodeField(value json.RawMessage) *field {
	if string(bytes.TrimSpace(value)) == "null" {
		return &field{kind: kindNull}
	}
	var s string
	if err := json.Unmarshal(value, &s); err != nil {
		return &field{kind: kindNonString}
	}
	return &field{kind: kindString, value: s}
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
	case f == nil || f.kind == kindNull:
		return "", []string{name + " is required"}
	case f.kind == kindNonString:
		return "", []string{name + " must be a string"}
	case f.value == "":
		return "", []string{name + " is required"}
	}
	return f.value, nil
}

// Each check* returns the field's value when it is valid, or the problems that make it invalid.

func checkEndpoint(f *field) (string, []string) {
	v, problems := required(fieldEndpoint, f)
	if problems != nil {
		return "", problems
	}
	if strings.ContainsAny(v, "/?#") {
		return "", []string{"endpoint must not contain '/', '?' or '#'"}
	}
	return v, nil
}

func checkUsername(f *field) (string, []string) {
	v, problems := required(fieldUsername, f)
	if problems != nil {
		return "", problems
	}
	if strings.Contains(v, ":") {
		problems = append(problems, "username must not contain ':' (RFC 7617)")
	}
	if hasControl(v) {
		problems = append(problems, "username must not contain control characters")
	}
	if problems != nil {
		return "", problems
	}
	return v, nil
}

func checkPassword(f *field) (string, []string) {
	v, problems := required(fieldPassword, f)
	if problems != nil {
		return "", problems
	}
	if hasControl(v) {
		return "", []string{"password must not contain control characters"}
	}
	return v, nil
}

func hasControl(s string) bool {
	for _, r := range s {
		if unicode.IsControl(r) {
			return true
		}
	}
	return false
}
