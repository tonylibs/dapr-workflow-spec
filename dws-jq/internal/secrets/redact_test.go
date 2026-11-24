package secrets

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/itchyny/gojq"
)

func TestRedactor(t *testing.T) {
	tests := []struct {
		name   string
		values []string
		in     string
		want   string
	}{
		{"raw value", []string{"s3cr3t-value", `a"b`}, "x s3cr3t-value y", "x *** y"},
		{"json-escaped form", []string{"s3cr3t-value", `a"b`}, `{"v":"a\"b"}`, `{"v":"***"}`},
		{"raw form with quote", []string{`a"b`}, `got a"b here`, "got *** here"},
		{"longest first", []string{"ab", "abc"}, "abc", "***"},
		{"longest first reversed input order", []string{"abc", "ab"}, "abc ab", "*** ***"},
		{"empty value ignored", []string{""}, "unchanged", "unchanged"},
		{"empty among real values", []string{"", "k"}, "a k b", "a *** b"},
		{"no values", nil, "unchanged", "unchanged"},
		{"every occurrence", []string{"k"}, "k-k-k", "***-***-***"},
		{"newline in json form", []string{"l1\nl2"}, `"l1\nl2"`, `"***"`},
		{"duplicate values", []string{"k", "k"}, "k", "***"},
		{"text without a secret", []string{"k"}, "nothing here", "nothing here"},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := NewRedactor(tc.values).Redact(tc.in); got != tc.want {
				t.Errorf("Redact(%q) = %q, want %q", tc.in, got, tc.want)
			}
		})
	}
}

func TestRedactorNilReceiver(t *testing.T) {
	var r *Redactor
	if got := r.Redact("pass through"); got != "pass through" {
		t.Errorf("nil Redactor changed the text: %q", got)
	}
}

// TestRedactorEncodedForms feeds the redactor text produced by the real
// encoders, with only the escaped form of the secret present, so the test
// tracks what gojq and encoding/json actually write.
func TestRedactorEncodedForms(t *testing.T) {
	values := []string{
		"has\x7fdel",
		"a<b>&c",
		"tab\tnl\nq\"bs\\",
		"café ☃",
		"ctl\x01\x1f",
		"line\u2028sep",
	}
	encoders := map[string]func(any) ([]byte, error){
		"gojq":          gojq.Marshal,
		"encoding/json": json.Marshal,
	}
	for _, v := range values {
		r := NewRedactor([]string{v})
		for name, enc := range encoders {
			t.Run(name+"/"+strings.ToValidUTF8(v, "?"), func(t *testing.T) {
				// An error message the way a jq program would emit it.
				b, err := enc(map[string]any{"a": v})
				if err != nil {
					t.Fatal(err)
				}
				got := r.Redact(string(b))
				if want := `{"a":"***"}`; got != want {
					t.Errorf("Redact(%q) = %q, want %q", b, got, want)
				}
			})
		}
	}
}
