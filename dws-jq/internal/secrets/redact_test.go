package secrets

import "testing"

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
		{"html chars in json form", []string{"a<b"}, `"a<b"`, `"***"`},
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
