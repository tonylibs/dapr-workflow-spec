package bearer

import (
	"strings"
	"testing"
)

// secret is recognisable so a leak into any error text is caught.
const secret = "s3cr3t-T0KEN"

func TestParseValid(t *testing.T) {
	cfg, err := Parse([]byte(`{"endpoint":"pets-api","token":"` + secret + `"}`))
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}
	if cfg.endpoint != "pets-api" || cfg.token != secret {
		t.Fatalf("got endpoint %q, token present: %t", cfg.endpoint, cfg.token == secret)
	}
	if want := "/v1.0/invoke/pets-api/method"; cfg.prefix != want {
		t.Errorf("prefix = %q, want %q", cfg.prefix, want)
	}
	if want := "Bearer " + secret; cfg.header != want {
		t.Errorf("header = %q, want %q", cfg.header, want)
	}
}

// TestParseRejects pins the exact, fixed error text of every failure mode. Every
// message starts with "guestConfig", so a substring check could not tell the
// modes apart; the full text can.
func TestParseRejects(t *testing.T) {
	const (
		errEmpty          = "guestConfig is empty"
		errNotObject      = "guestConfig is not a JSON object"
		errUnknown        = "guestConfig has an unknown field"
		errEndpointEmpty  = "guestConfig.endpoint is missing or empty"
		errEndpointName   = "guestConfig.endpoint is not a valid endpoint name"
		errEndpointType   = "guestConfig.endpoint must be a string"
		errTokenEmpty     = "guestConfig.token is missing or empty"
		errTokenControl   = "guestConfig.token contains a control character (trailing newline?)"
		errTokenType      = "guestConfig.token must be a string"
		errUnknownScheme  = errUnknown + ": scheme"
		errUnknownUser    = errUnknown + ": username"
		errUnknownPath    = errUnknown + ": path"
		errUnknownPasswd  = errUnknown + ": password"
		validEndpointJSON = `"endpoint":"pets-api"`
	)
	tests := []struct {
		name  string
		input string
		want  string // exact error text
	}{
		{"missing token", `{` + validEndpointJSON + `}`, errTokenEmpty},
		{"empty token", `{` + validEndpointJSON + `,"token":""}`, errTokenEmpty},
		{"whitespace token", `{` + validEndpointJSON + `,"token":"   "}`, errTokenEmpty},
		{"null token", `{` + validEndpointJSON + `,"token":null}`, errTokenEmpty},
		{"token trailing newline", `{` + validEndpointJSON + `,"token":"` + secret + `\n"}`, errTokenControl},
		{"token embedded CR", `{` + validEndpointJSON + `,"token":"` + secret + `\rX: y"}`, errTokenControl},
		{"token DEL", `{` + validEndpointJSON + `,"token":"` + secret + `\u007f"}`, errTokenControl},
		{"wrong type token", `{` + validEndpointJSON + `,"token":123}`, errTokenType},
		{"missing endpoint", `{"token":"` + secret + `"}`, errEndpointEmpty},
		{"empty endpoint", `{"endpoint":"","token":"` + secret + `"}`, errEndpointEmpty},
		{"whitespace endpoint", `{"endpoint":"  ","token":"` + secret + `"}`, errEndpointEmpty},
		{"endpoint slash", `{"endpoint":"a/b","token":"` + secret + `"}`, errEndpointName},
		{"endpoint question", `{"endpoint":"a?b","token":"` + secret + `"}`, errEndpointName},
		{"endpoint hash", `{"endpoint":"a#b","token":"` + secret + `"}`, errEndpointName},
		{"endpoint control", `{"endpoint":"a\nb","token":"` + secret + `"}`, errEndpointName},
		{"wrong type endpoint", `{"endpoint":["` + secret + `"],"token":"` + secret + `"}`, errEndpointType},
		{"unknown scheme field", `{` + validEndpointJSON + `,"token":"` + secret + `","scheme":"bearer"}`, errUnknownScheme},
		{"unknown basic fields", `{` + validEndpointJSON + `,"username":"u","password":"` + secret + `"}`, errUnknownUser},
		{"unknown password field", `{` + validEndpointJSON + `,"token":"` + secret + `","password":"x"}`, errUnknownPasswd},
		{"unknown path field", `{` + validEndpointJSON + `,"token":"` + secret + `","path":"/x"}`, errUnknownPath},
		{"unknown arbitrary field", `{` + validEndpointJSON + `,"token":"` + secret + `","other":"x"}`, errUnknown},
		{"empty input", ``, errEmpty},
		{"whitespace input", " \n\t", errEmpty},
		{"not json", `token=` + secret, errNotObject},
		{"json null", `null`, errNotObject},
		{"json array", `["` + secret + `"]`, errNotObject},
		{"json string", `"` + secret + `"`, errNotObject},
		{"trailing data", `{` + validEndpointJSON + `,"token":"` + secret + `"} ` + secret, errNotObject},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			_, err := Parse([]byte(tc.input))
			if err == nil {
				t.Fatal("expected an error, got nil")
			}
			if err.Error() != tc.want {
				t.Errorf("error = %q, want %q", err, tc.want)
			}
			if strings.Contains(err.Error(), secret) {
				t.Errorf("error text leaks the token: %q", err)
			}
		})
	}
}

func TestParseUnknownKeyIsNotEchoed(t *testing.T) {
	_, err := Parse([]byte(`{"endpoint":"pets-api","token":"ok","` + secret + `":"x"}`))
	if err == nil {
		t.Fatal("expected an error")
	}
	if want := "guestConfig has an unknown field"; err.Error() != want {
		t.Errorf("error = %q, want %q", err, want)
	}
	if strings.Contains(err.Error(), secret) {
		t.Errorf("error text echoes an arbitrary config key: %q", err)
	}
}
