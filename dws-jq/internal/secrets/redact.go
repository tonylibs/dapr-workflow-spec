package secrets

import (
	"encoding/json"
	"sort"
	"strings"

	"github.com/itchyny/gojq"
)

const (
	mask = "***"

	// previewMarker starts every suffix gojq.Preview appends when it cuts a
	// value short (` ..."`, ` ...]`, ` ...}` and ` ...`). gojq builds its type
	// and argument errors from previews, so a long secret reaches a message
	// only as a prefix of its encoded form followed by this marker.
	previewMarker = " ..."
	// previewWidth is gojq.Preview's width in bytes (gojq v0.12.20): a cut
	// preview shows fewer bytes of any value than this. Re-check it, with the
	// marker, when bumping gojq.
	previewWidth = 30
)

// Redactor replaces every secret value it was built from with "***".
type Redactor struct {
	forms []string
}

// span is the byte range [start, end) of a match.
type span struct{ start, end int }

// NewRedactor builds a Redactor covering each non-empty value in its raw form
// and in the JSON-escaped forms of gojq and encoding/json without the
// surrounding quotes, so a value that reaches a message through either encoder
// is still recognised.
//
// Each form is also matched as gojq's preview cuts it: any prefix of up to
// previewWidth bytes that is directly followed by previewMarker is masked,
// the marker itself is kept, so `"ABCDEF ..."` becomes `"*** ..."`. This
// over-masks text that merely starts like a secret and ends in " ...", which
// is the intended trade-off.
func NewRedactor(values []string) *Redactor {
	seen := make(map[string]struct{})
	var forms []string
	add := func(form string) {
		if form == "" {
			return
		}
		if _, dup := seen[form]; dup {
			return
		}
		seen[form] = struct{}{}
		forms = append(forms, form)
	}
	for _, v := range values {
		if v == "" {
			continue
		}
		add(v)
		// gojq writes the error messages and results, and escapes differently
		// from encoding/json (it escapes DEL, which encoding/json leaves raw, and
		// leaves <, > and & raw, which encoding/json escapes). Cover both.
		add(unquote(gojq.Marshal(v)))
		add(unquote(json.Marshal(v)))
	}
	return &Redactor{forms: forms}
}

// Redact returns s with every secret value replaced by "***". All matches are
// found first, overlapping ones included, and every maximal run of matched
// bytes becomes one "***", so two secrets that overlap in s (one's end is the
// other's start, or one inside the other) are masked as a whole rather than
// leaving the part of the second that the first match did not cover. A nil
// Redactor returns s unchanged.
func (r *Redactor) Redact(s string) string {
	if r == nil || len(r.forms) == 0 {
		return s
	}
	spans := r.matches(s)
	if len(spans) == 0 {
		return s
	}
	sort.Slice(spans, func(i, j int) bool { return spans[i].start < spans[j].start })
	var b strings.Builder
	last := 0
	for i := 0; i < len(spans); {
		cur := spans[i]
		for i++; i < len(spans) && spans[i].start <= cur.end; i++ {
			cur.end = max(cur.end, spans[i].end)
		}
		b.WriteString(s[last:cur.start])
		b.WriteString(mask)
		last = cur.end
	}
	b.WriteString(s[last:])
	return b.String()
}

// matches returns every occurrence of a whole form and every cut prefix of a
// form that ends right before a preview marker.
func (r *Redactor) matches(s string) []span {
	var out []span
	for _, f := range r.forms {
		for i := 0; ; {
			j := strings.Index(s[i:], f)
			if j < 0 {
				break
			}
			out = append(out, span{i + j, i + j + len(f)})
			i += j + 1
		}
	}
	for i := 0; ; {
		j := strings.Index(s[i:], previewMarker)
		if j < 0 {
			break
		}
		at := i + j
		for _, f := range r.forms {
			if n := cutPrefix(s[:at], f); n > 0 {
				out = append(out, span{at - n, at})
			}
		}
		i = at + 1
	}
	return out
}

// cutPrefix returns the length of the longest prefix of form, at most
// previewWidth bytes, that text ends with, or 0.
func cutPrefix(text, form string) int {
	for n := min(len(form), previewWidth, len(text)); n > 0; n-- {
		if strings.HasSuffix(text, form[:n]) {
			return n
		}
	}
	return 0
}

// unquote returns the body of an encoded JSON string without its quotes, or ""
// when encoding failed.
func unquote(b []byte, err error) string {
	if err != nil || len(b) < 2 {
		return ""
	}
	return string(b[1 : len(b)-1])
}
