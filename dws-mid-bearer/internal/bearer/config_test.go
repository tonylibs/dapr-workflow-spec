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
	if cfg.Endpoint != "pets-api" || cfg.Token != secret {
		t.Fatalf("got %+v", cfg)
	}
}

func TestParseRejects(t *testing.T) {
	tests := []struct {
		name  string
		input string
		field string // substring the error must contain
	}{
		{"missing token", `{"endpoint":"pets-api"}`, "token"},
		{"empty token", `{"endpoint":"pets-api","token":""}`, "token"},
		{"whitespace token", `{"endpoint":"pets-api","token":"   "}`, "token"},
		{"token trailing newline", `{"endpoint":"pets-api","token":"` + secret + `\n"}`, "token"},
		{"token embedded CR", `{"endpoint":"pets-api","token":"` + secret + `\rX: y"}`, "token"},
		{"token DEL", `{"endpoint":"pets-api","token":"` + secret + `\u007f"}`, "token"},
		{"null token", `{"endpoint":"pets-api","token":null}`, "token"},
		{"missing endpoint", `{"token":"` + secret + `"}`, "endpoint"},
		{"empty endpoint", `{"endpoint":"","token":"` + secret + `"}`, "endpoint"},
		{"endpoint slash", `{"endpoint":"a/b","token":"` + secret + `"}`, "endpoint"},
		{"endpoint question", `{"endpoint":"a?b","token":"` + secret + `"}`, "endpoint"},
		{"endpoint hash", `{"endpoint":"a#b","token":"` + secret + `"}`, "endpoint"},
		{"endpoint control", `{"endpoint":"a\nb","token":"` + secret + `"}`, "endpoint"},
		{"unknown scheme field", `{"endpoint":"pets-api","token":"` + secret + `","scheme":"bearer"}`, "scheme"},
		{"unknown basic fields", `{"endpoint":"pets-api","username":"u","password":"` + secret + `"}`, "username"},
		{"unknown path field", `{"endpoint":"pets-api","token":"` + secret + `","path":"/x"}`, "path"},
		{"empty input", ``, "guestConfig"},
		{"not json", `token=` + secret, "guestConfig"},
		{"json array", `["` + secret + `"]`, "guestConfig"},
		{"json string", `"` + secret + `"`, "guestConfig"},
		{"wrong type token", `{"endpoint":"pets-api","token":123}`, "token"},
		{"wrong type endpoint", `{"endpoint":["` + secret + `"],"token":"` + secret + `"}`, "endpoint"},
		{"trailing data", `{"endpoint":"pets-api","token":"` + secret + `"} ` + secret, "guestConfig"},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			_, err := Parse([]byte(tc.input))
			if err == nil {
				t.Fatal("expected an error, got nil")
			}
			if !strings.Contains(err.Error(), tc.field) {
				t.Errorf("error %q does not name %q", err, tc.field)
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
	if strings.Contains(err.Error(), secret) {
		t.Errorf("error text echoes an arbitrary config key: %q", err)
	}
}
