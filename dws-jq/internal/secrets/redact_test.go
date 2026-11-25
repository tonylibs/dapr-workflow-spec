package secrets

import (
	"encoding/json"
	"fmt"
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

// longSecrets are longer than gojq's 30-byte preview, so a preview only ever
// shows a prefix of their encoded form.
var longSecrets = []string{
	"ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDEFGHIJK",            // 47 bytes
	"QWE\"RTY\\ASD\tFGH☃JKL\x7fZXC<VBN>&MQW\u2028ERTYUIOPASDFGH", // every escape gojq or encoding/json writes
}

// encodedForms returns v raw and as gojq and encoding/json write it inside a
// string, the forms a leak could take.
func encodedForms(t *testing.T, v string) []string {
	t.Helper()
	forms := []string{v}
	for _, enc := range []func(any) ([]byte, error){gojq.Marshal, json.Marshal} {
		b, err := enc(v)
		if err != nil {
			t.Fatal(err)
		}
		forms = append(forms, string(b[1:len(b)-1]))
	}
	return forms
}

// leakedPiece returns a substring of at least minLeak bytes of any encoded form
// of secret that s contains, or "".
func leakedPiece(t *testing.T, s, secret string) string {
	t.Helper()
	const minLeak = 4
	for _, form := range encodedForms(t, secret) {
		for i := 0; i+minLeak <= len(form); i++ {
			if piece := form[i : i+minLeak]; strings.Contains(s, piece) {
				return piece
			}
		}
	}
	return ""
}

// previewContainers wraps v the ways an error message can show it: bare, and
// inside arrays and objects, as a value and as a key, at several offsets, so
// gojq's truncation cuts the encoded secret at many different places.
func previewContainers(v string) map[string]any {
	out := map[string]any{"bare": v}
	for _, offset := range []int{0, 1, 3, 7, 12, 18, 23} {
		pad := strings.Repeat("k", offset)
		out[fmt.Sprintf("array/%d", offset)] = []any{pad, v}
		out[fmt.Sprintf("object value/%d", offset)] = map[string]any{pad: v}
		out[fmt.Sprintf("object key/%d", offset)] = map[string]any{v: pad}
		out[fmt.Sprintf("nested/%d", offset)] = []any{map[string]any{"k": []any{pad + v}}}
	}
	return out
}

// TestRedactorTruncatedPreviews feeds the redactor the messages gojq builds
// with gojq.Preview, which cuts a long value after 30 bytes and appends " ...",
// so the whole secret never appears and only its prefix can be matched.
func TestRedactorTruncatedPreviews(t *testing.T) {
	for _, secret := range longSecrets {
		r := NewRedactor([]string{secret})
		for name, v := range previewContainers(secret) {
			t.Run(strings.ToValidUTF8(secret[:6], "?")+"/"+name, func(t *testing.T) {
				preview := gojq.Preview(v)
				msg := "cannot add: " + preview + " and number (1)"

				got := r.Redact(msg)

				if piece := leakedPiece(t, got, secret); piece != "" {
					t.Errorf("Redact(%q) = %q, still shows %q of the secret", msg, got, piece)
				}
				if !strings.Contains(got, " ...") {
					t.Errorf("Redact(%q) = %q, lost the truncation marker", msg, got)
				}
			})
		}
	}
}

// TestRedactorPreviewsExerciseTruncation guards the test above: the bare
// previews really do carry a cut prefix of the secret and not the whole value.
func TestRedactorPreviewsExerciseTruncation(t *testing.T) {
	for _, secret := range longSecrets {
		preview := gojq.Preview(secret)
		if strings.Contains(preview, encodedForms(t, secret)[1]) {
			t.Errorf("Preview(%q) = %q holds the whole value; the secret is too short", secret, preview)
		}
		if leakedPiece(t, preview, secret) == "" {
			t.Errorf("Preview(%q) = %q shows no prefix of the secret", secret, preview)
		}
	}
}

func TestRedactorPrefixNeedsMarker(t *testing.T) {
	r := NewRedactor([]string{longSecrets[0]})
	// A prefix of the secret not followed by gojq's marker is ordinary text.
	if got, want := r.Redact("ABCD and ABCDEF..."), "ABCD and ABCDEF..."; got != want {
		t.Errorf("Redact = %q, want %q", got, want)
	}
	if got, want := r.Redact(`"ABCDEFGH ..."`), `"*** ..."`; got != want {
		t.Errorf("Redact = %q, want %q", got, want)
	}
}

// TestRedactorOverlappingSecrets covers two secrets that overlap in the text:
// every byte that belongs to either is masked, not just the first match.
func TestRedactorOverlappingSecrets(t *testing.T) {
	tests := []struct {
		name   string
		values []string
		in     string
		want   string
	}{
		{"suffix of one is prefix of the other", []string{"PQRS", "RSTUVWXY"}, "x PQRSTUVWXY y", "x *** y"},
		{"reversed order", []string{"RSTUVWXY", "PQRS"}, "x PQRSTUVWXY y", "x *** y"},
		{"one inside the other", []string{"QRST", "PQRSTUV"}, "PQRSTUV", "***"},
		{"overlap into a truncated preview", []string{"PQRS", "RSTUVWXYZ0123456789ABCDEFGHIJKLMN"},
			`"PQRSTUVWXYZ0123456789ABC ..."`, `"*** ..."`},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := NewRedactor(tc.values).Redact(tc.in); got != tc.want {
				t.Errorf("Redact(%q) = %q, want %q", tc.in, got, tc.want)
			}
		})
	}
}
