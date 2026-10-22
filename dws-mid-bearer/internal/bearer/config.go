package bearer

import (
	"bytes"
	"encoding/json"
	"errors"
	"strings"
)

// Config is the guestConfig of the bearer component. It has no scheme field:
// which component Dapr loads is the scheme.
type Config struct {
	// Endpoint is the name of the Dapr HTTPEndpoint this credential applies to.
	Endpoint string
	// Token is the bearer credential. It is a secret: never log it or put it in
	// an error.
	Token string
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
	if len(fields) > len(known(fields)) {
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
	return Config{Endpoint: endpoint, Token: token}, nil
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

// known returns the recognised fields present in the config.
func known(fields map[string]json.RawMessage) []string {
	var present []string
	for _, name := range []string{"endpoint", "token"} {
		if _, ok := fields[name]; ok {
			present = append(present, name)
		}
	}
	return present
}
