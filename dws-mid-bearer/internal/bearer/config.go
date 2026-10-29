package bearer

import (
	"bytes"
	"encoding/json"
	"errors"
	"strings"
)

// Config is the validated guestConfig of the bearer component. It has no scheme
// field: which component Dapr loads is the scheme.
//
// The fields are private so that Parse is the only way to get a usable Config:
// a value that skipped validation cannot exist outside this package. The zero
// value matches nothing and so sets nothing.
type Config struct {
	endpoint string
	// token is the bearer credential. It is a secret: never log it or put it in
	// an error.
	token string

	// Derived once by Parse so the per-request path builds no strings.
	prefix string // "/v1.0/invoke/<endpoint>/method"
	header string // "Bearer <token>"
}

// misconfigured names the fields a controller would plausibly send to this
// component by mistake (another scheme's credentials, the spike's path filter).
// Naming them in the error helps; echoing any other key could echo secret
// material, so those stay anonymous.
var misconfigured = []string{"scheme", "username", "password", "path"}

// Parse validates a guestConfig. Every error message is a fixed string that
// names a field at most; none is built from the input, because guestConfig is
// delivered from a Secret and Go's JSON errors can quote the input.
func Parse(raw []byte) (Config, error) {
	if len(bytes.TrimSpace(raw)) == 0 {
		return Config{}, errors.New("guestConfig is empty")
	}
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(raw, &fields); err != nil || fields == nil {
		return Config{}, errors.New("guestConfig is not a JSON object")
	}
	// Fixed order so the message is deterministic (map iteration is not).
	for _, name := range misconfigured {
		if _, ok := fields[name]; ok {
			return Config{}, errors.New("guestConfig has an unknown field: " + name)
		}
	}
	if hasUnknownField(fields) {
		return Config{}, errors.New("guestConfig has an unknown field")
	}

	endpoint, err := stringField(fields, "endpoint")
	if err != nil {
		return Config{}, err
	}
	token, err := stringField(fields, "token")
	if err != nil {
		return Config{}, err
	}

	if strings.TrimSpace(endpoint) == "" {
		return Config{}, errors.New("guestConfig.endpoint is missing or empty")
	}
	if strings.ContainsAny(endpoint, "/?#") || hasControl(endpoint) {
		return Config{}, errors.New("guestConfig.endpoint is not a valid endpoint name")
	}
	if strings.TrimSpace(token) == "" {
		return Config{}, errors.New("guestConfig.token is missing or empty")
	}
	if hasControl(token) {
		return Config{}, errors.New("guestConfig.token contains a control character (trailing newline?)")
	}
	return Config{
		endpoint: endpoint,
		token:    token,
		prefix:   "/v1.0/invoke/" + endpoint + "/method",
		header:   "Bearer " + token,
	}, nil
}

// stringField returns the named field as a string; absent and null yield "".
func stringField(fields map[string]json.RawMessage, name string) (string, error) {
	v, ok := fields[name]
	if !ok {
		return "", nil
	}
	var s string
	if err := json.Unmarshal(v, &s); err != nil {
		return "", errors.New("guestConfig." + name + " must be a string")
	}
	return s, nil
}

// hasControl reports an ASCII control character or DEL, either of which would
// make an invalid or injectable header value or endpoint name.
func hasControl(s string) bool {
	for i := 0; i < len(s); i++ {
		if s[i] < 0x20 || s[i] == 0x7f {
			return true
		}
	}
	return false
}

// hasUnknownField reports a key other than "endpoint" or "token". It returns a
// bool, not the key, so the caller cannot put a key (possibly secret material)
// in an error.
func hasUnknownField(fields map[string]json.RawMessage) bool {
	for name := range fields {
		if name != "endpoint" && name != "token" {
			return true
		}
	}
	return false
}
