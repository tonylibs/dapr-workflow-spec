package basicauth

import (
	"errors"
	"reflect"
	"strings"
	"testing"
)

func TestParseValid(t *testing.T) {
	tests := []struct {
		name string
		raw  string
		want Config
	}{
		{
			name: "basic",
			raw:  `{"endpoint":"pets","username":"alice","password":"s3cret"}`,
			want: Config{Endpoint: "pets", Username: "alice", Password: "s3cret"},
		},
		{
			name: "key order and whitespace do not matter",
			raw:  " {\n \"password\" : \"s3cret\",\n \"endpoint\":\"pets\", \"username\":\"alice\" }\n",
			want: Config{Endpoint: "pets", Username: "alice", Password: "s3cret"},
		},
		{
			name: "colon in password is allowed",
			raw:  `{"endpoint":"pets","username":"alice","password":"pa:ss:word"}`,
			want: Config{Endpoint: "pets", Username: "alice", Password: "pa:ss:word"},
		},
		{
			name: "non-ASCII values",
			raw:  `{"endpoint":"pets","username":"test","password":"123£"}`,
			want: Config{Endpoint: "pets", Username: "test", Password: "123£"},
		},
		{
			name: "JSON escapes are decoded",
			raw:  `{"endpoint":"pets","username":"alïce","password":"a\"b\\c"}`,
			want: Config{Endpoint: "pets", Username: "alïce", Password: `a"b\c`},
		},
		{
			name: "spaces inside a password are kept",
			raw:  `{"endpoint":"pets","username":"alice","password":" p w "}`,
			want: Config{Endpoint: "pets", Username: "alice", Password: " p w "},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := Parse([]byte(tt.raw))
			if err != nil {
				t.Fatalf("Parse() error = %v", err)
			}
			if !reflect.DeepEqual(*got, tt.want) {
				t.Errorf("got %#v, want %#v", *got, tt.want)
			}
		})
	}
}

// Every invalid config must be rejected, and the message must name every problem (and only
// problems), so one fix round is enough.
func TestParseInvalid(t *testing.T) {
	tests := []struct {
		name string
		raw  string
		want []string // exact problem list, in order
	}{
		{
			name: "scheme field is not known",
			raw:  `{"scheme":"basic","endpoint":"pets","username":"alice","password":"s3cret"}`,
			want: []string{"scheme is not a known field"},
		},
		{
			name: "scheme field with any value",
			raw:  `{"scheme":"","endpoint":"pets","username":"alice","password":"s3cret"}`,
			want: []string{"scheme is not a known field"},
		},
		{
			name: "bearer-shaped config",
			raw:  `{"endpoint":"pets","token":"abc"}`,
			want: []string{"token is not a known field", "username is required", "password is required"},
		},
		{
			name: "field names are case sensitive",
			raw:  `{"endpoint":"pets","username":"alice","Password":"s3cret"}`,
			want: []string{"Password is not a known field", "password is required"},
		},
		{
			name: "unknown field whose name is not an identifier is not echoed",
			raw:  `{"endpoint":"pets","username":"alice","password":"s3cret","s3cret value":1}`,
			want: []string{"a field with an unrecognized name is not a known field"},
		},
		{
			name: "missing password",
			raw:  `{"endpoint":"pets","username":"alice"}`,
			want: []string{"password is required"},
		},
		{
			name: "missing username",
			raw:  `{"endpoint":"pets","password":"s3cret"}`,
			want: []string{"username is required"},
		},
		{
			name: "missing endpoint",
			raw:  `{"username":"alice","password":"s3cret"}`,
			want: []string{"endpoint is required"},
		},
		{
			name: "null username and password",
			raw:  `{"endpoint":"pets","username":null,"password":null}`,
			want: []string{"username is required", "password is required"},
		},
		{
			name: "empty username",
			raw:  `{"endpoint":"pets","username":"","password":"s3cret"}`,
			want: []string{"username is required"},
		},
		{
			name: "empty password",
			raw:  `{"endpoint":"pets","username":"alice","password":""}`,
			want: []string{"password is required"},
		},
		{
			name: "empty endpoint",
			raw:  `{"endpoint":"","username":"alice","password":"s3cret"}`,
			want: []string{"endpoint is required"},
		},
		{
			name: "colon in username",
			raw:  `{"endpoint":"pets","username":"ali:ce","password":"s3cret"}`,
			want: []string{"username must not contain ':' (RFC 7617)"},
		},
		{
			name: "newline in password",
			raw:  `{"endpoint":"pets","username":"alice","password":"bad\nvalue"}`,
			want: []string{"password must not contain control characters"},
		},
		{
			name: "NUL in username",
			raw:  `{"endpoint":"pets","username":"bad\u0000value","password":"s3cret"}`,
			want: []string{"username must not contain control characters"},
		},
		{
			name: "DEL in password",
			raw:  `{"endpoint":"pets","username":"alice","password":"bad\u007fvalue"}`,
			want: []string{"password must not contain control characters"},
		},
		{
			name: "C1 control in password",
			raw:  `{"endpoint":"pets","username":"alice","password":"bad\u0085value"}`,
			want: []string{"password must not contain control characters"},
		},
		{
			name: "colon and control character in username",
			raw:  `{"endpoint":"pets","username":"a:b\tc","password":"s3cret"}`,
			want: []string{"username must not contain ':' (RFC 7617)", "username must not contain control characters"},
		},
		{
			name: "slash in endpoint",
			raw:  `{"endpoint":"pets/v1","username":"alice","password":"s3cret"}`,
			want: []string{"endpoint must not contain '/', '?' or '#'"},
		},
		{
			name: "question mark in endpoint",
			raw:  `{"endpoint":"pets?x","username":"alice","password":"s3cret"}`,
			want: []string{"endpoint must not contain '/', '?' or '#'"},
		},
		{
			name: "hash in endpoint",
			raw:  `{"endpoint":"pets#x","username":"alice","password":"s3cret"}`,
			want: []string{"endpoint must not contain '/', '?' or '#'"},
		},
		{
			name: "non-string values",
			raw:  `{"endpoint":1,"username":true,"password":["x"]}`,
			want: []string{"endpoint must be a string", "username must be a string", "password must be a string"},
		},
		{
			name: "duplicate field",
			raw:  `{"endpoint":"pets","username":"alice","password":"s3cret","password":"hunter2"}`,
			want: []string{"password is specified more than once"},
		},
		{
			name: "all problems are reported together",
			raw:  `{"scheme":"basic","endpoint":"a/b","username":"x:y","password":""}`,
			want: []string{
				"scheme is not a known field",
				"endpoint must not contain '/', '?' or '#'",
				"username must not contain ':' (RFC 7617)",
				"password is required",
			},
		},
		{name: "empty config", raw: ``, want: []string{"config is empty"}},
		{name: "blank config", raw: " \n\t", want: []string{"config is empty"}},
		{name: "malformed JSON", raw: `{"endpoint":"pets","username":"alice","password":"s3cret"`, want: []string{"config is not valid JSON"}},
		{name: "malformed JSON in the middle", raw: `{"endpoint":"pets" "username":"alice"}`, want: []string{"config is not valid JSON"}},
		{name: "not JSON at all", raw: `oops-value`, want: []string{"config is not valid JSON"}},
		{name: "trailing data", raw: `{"endpoint":"pets","username":"alice","password":"s3cret"} {}`, want: []string{"config is not valid JSON"}},
		{name: "array", raw: `["pets","alice","s3cret"]`, want: []string{"config must be a JSON object"}},
		{name: "string", raw: `"s3cret"`, want: []string{"config must be a JSON object"}},
		{name: "number", raw: `42`, want: []string{"config must be a JSON object"}},
		{name: "null", raw: `null`, want: []string{"config must be a JSON object"}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := Parse([]byte(tt.raw))
			if err == nil {
				t.Fatalf("Parse() = %#v, want an error", got)
			}
			if got != nil {
				t.Errorf("Parse() returned a config alongside an error: %#v", got)
			}
			var cerr *ConfigError
			if !errors.As(err, &cerr) {
				t.Fatalf("error %T is not a *ConfigError", err)
			}
			if !reflect.DeepEqual(cerr.Problems, tt.want) {
				t.Errorf("problems = %q, want %q", cerr.Problems, tt.want)
			}
			wantMsg := "dws-mid-basic: " + strings.Join(tt.want, "; ")
			if err.Error() != wantMsg {
				t.Errorf("Error() = %q, want %q", err.Error(), wantMsg)
			}
		})
	}
}

// Error text must name fields and rules only: no credential value, no fragment of the input and no
// encoding/json error text (which quotes the offending character) may appear in any message.
func TestErrorsNeverEchoValues(t *testing.T) {
	inputs := []string{
		`{"endpoint":"pets","username":"ali:ce","password":"s3cret"}`,
		`{"endpoint":"pets","username":"alice","password":"bad\nvalue"}`,
		`{"endpoint":"pets","username":"bad\u0000value","password":"hunter2"}`,
		`{"endpoint":"pets","token":"abc"}`,
		`{"endpoint":"pets","username":"alice","password":"s3cret"`,
		`{"endpoint":"pets" "username":"alice","password":"s3cret"}`,
		`oops-value`,
		`{"endpoint":"pets","username":"alice","password":"s3cret","s3cret value":"tops3cr3t"}`,
		`{"endpoint":"pets","username":"alice","password":"s3cret","password":"pa:ss:word"}`,
		`{"endpoint":"pets","username":123,"password":"s3cret"}`,
		`{"endpoint":"a/b","username":"x:y","password":""}`,
	}
	values := []string{"s3cret", "alice", "ali:ce", "hunter2", "pa:ss:word", "tops3cr3t", "oops-value", "bad\nvalue", "bad\x00value", "s3cret value"}
	for _, raw := range inputs {
		_, err := Parse([]byte(raw))
		if err == nil {
			t.Fatalf("Parse(%q) succeeded, want an error", raw)
		}
		for _, v := range values {
			if strings.Contains(err.Error(), v) {
				t.Errorf("error %q for input %q contains value %q", err.Error(), raw, v)
			}
		}
		if strings.ContainsAny(err.Error(), "\n\x00") {
			t.Errorf("error %q contains a control character", err.Error())
		}
	}
}
