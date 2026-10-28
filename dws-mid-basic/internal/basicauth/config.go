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

// Config is a validated guest config, reduced to what the guest serves with: the invocation path
// of its one endpoint and the encoded Basic credential. Its fields are unexported so a usable
// Config only comes from Parse; the zero Config matches no request.
type Config struct {
	methodPath string // "/v1.0/invoke/<endpoint>/method"
	header     string // the Authorization header value
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

// rule is one check on a present, non-empty string value. A value breaks the rule when broken
// reports true; problem completes the sentence that starts with the field name.
type rule struct {
	broken  func(string) bool
	problem string
}

// fieldSpec is one guest config field and the rules its value must keep beyond being required.
type fieldSpec struct {
	name  string
	rules []rule
}

// fieldSpecs is every field of the guest config, in the order their problems are reported.
var fieldSpecs = []fieldSpec{
	{name: fieldEndpoint, rules: []rule{
		{broken: func(v string) bool { return strings.ContainsAny(v, "/?#") }, problem: "must not contain '/', '?' or '#'"},
	}},
	{name: fieldUsername, rules: []rule{
		{broken: func(v string) bool { return strings.Contains(v, ":") }, problem: "must not contain ':' (RFC 7617)"},
		{broken: hasControl, problem: "must not contain control characters"},
	}},
	{name: fieldPassword, rules: []rule{
		{broken: hasControl, problem: "must not contain control characters"},
	}},
}

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

	values := make(map[string]string, len(fieldSpecs))
	for _, spec := range fieldSpecs {
		v, specProblems := spec.check(fields[spec.name])
		problems = append(problems, specProblems...)
		values[spec.name] = v
	}
	if len(problems) > 0 {
		return nil, &ConfigError{Problems: problems}
	}
	return newConfig(values[fieldEndpoint], values[fieldUsername], values[fieldPassword]), nil
}

// newConfig derives a Config from field values that have already passed every rule.
func newConfig(endpoint, username, password string) *Config {
	return &Config{methodPath: methodPath(endpoint), header: headerValue(username, password)}
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

		if !isKnownField(key) {
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

func isKnownField(name string) bool {
	return slices.ContainsFunc(fieldSpecs, func(spec fieldSpec) bool { return spec.name == name })
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

// check returns the field's value when it is valid, or every problem that makes it invalid.
func (spec fieldSpec) check(f *field) (string, []string) {
	v, problems := required(spec.name, f)
	if problems != nil {
		return "", problems
	}
	for _, r := range spec.rules {
		if r.broken(v) {
			problems = append(problems, spec.name+" "+r.problem)
		}
	}
	if problems != nil {
		return "", problems
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
