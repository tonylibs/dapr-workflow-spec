package basicauth

import (
	"encoding/base64"
	"testing"
)

func TestHeaderValue(t *testing.T) {
	tests := []struct {
		name     string
		username string
		password string
		want     string
	}{
		{"simple", "alice", "s3cret", "Basic YWxpY2U6czNjcmV0"},
		{"RFC 7617 section 2.1 example", "test", "123£", "Basic dGVzdDoxMjPCow=="},
		{"colons in the password are kept", "alice", "pa:ss:word", "Basic " + b64("alice:pa:ss:word")},
		{"password that is only a colon", "alice", ":", "Basic " + b64("alice::")},
		{"padding: one pad byte", "ab", "c", "Basic YWI6Yw=="},
		{"padding: no pad bytes", "abc", "d", "Basic YWJjOmQ="},
		{"non-ASCII username and password", "zoë", "пароль", "Basic " + b64("zoë:пароль")},
		{"decomposed e in the username is normalized to U+00E9", "é", "x", "Basic " + b64("é:x")},
		{"decomposed e in the password is normalized to U+00E9", "alice", "caf" + "é", "Basic " + b64("alice:café")},
		{"already composed input is unchanged", "é", "é", "Basic " + b64("é:é")},
		{"space is kept", "alice", " p w ", "Basic " + b64("alice: p w ")},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cfg := &Config{Endpoint: "pets", Username: tt.username, Password: tt.password}
			if got := cfg.HeaderValue(); got != tt.want {
				t.Errorf("HeaderValue() = %q, want %q", got, tt.want)
			}
		})
	}
}

// The decomposed form must encode as the two bytes C3 A9, not as the three bytes of e + U+0301.
func TestHeaderValueDecomposedBytes(t *testing.T) {
	cfg := &Config{Endpoint: "pets", Username: "é", Password: "x"}
	raw, err := base64.StdEncoding.DecodeString(cfg.HeaderValue()[len("Basic "):])
	if err != nil {
		t.Fatalf("header value is not standard padded base64: %v", err)
	}
	want := []byte{0xc3, 0xa9, ':', 'x'}
	if string(raw) != string(want) {
		t.Errorf("encoded bytes = % x, want % x", raw, want)
	}
}

func TestParseThenHeaderValue(t *testing.T) {
	cfg, err := Parse([]byte(`{"endpoint":"pets","username":"alice","password":"s3cret"}`))
	if err != nil {
		t.Fatalf("Parse() error = %v", err)
	}
	if got, want := cfg.HeaderValue(), "Basic YWxpY2U6czNjcmV0"; got != want {
		t.Errorf("HeaderValue() = %q, want %q", got, want)
	}
}

func b64(s string) string { return base64.StdEncoding.EncodeToString([]byte(s)) }
