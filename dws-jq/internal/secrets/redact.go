package secrets

import (
	"encoding/json"
	"sort"
	"strings"

	"github.com/itchyny/gojq"
)

const mask = "***"

// Redactor replaces every secret value it was built from with "***".
type Redactor struct {
	replacer *strings.Replacer
}

// NewRedactor builds a Redactor covering each non-empty value in its raw form
// and in the JSON-escaped forms of gojq and encoding/json without the
// surrounding quotes, so a value that reaches a message through either encoder
// is still recognised. Longer forms
// are matched first, so a value that contains another is masked as a whole.
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
	if len(forms) == 0 {
		return &Redactor{}
	}
	sort.SliceStable(forms, func(i, j int) bool { return len(forms[i]) > len(forms[j]) })
	pairs := make([]string, 0, 2*len(forms))
	for _, f := range forms {
		pairs = append(pairs, f, mask)
	}
	return &Redactor{replacer: strings.NewReplacer(pairs...)}
}

// Redact returns s with every secret value replaced by "***". A nil Redactor
// returns s unchanged.
func (r *Redactor) Redact(s string) string {
	if r == nil || r.replacer == nil {
		return s
	}
	return r.replacer.Replace(s)
}

// unquote returns the body of an encoded JSON string without its quotes, or ""
// when encoding failed.
func unquote(b []byte, err error) string {
	if err != nil || len(b) < 2 {
		return ""
	}
	return string(b[1 : len(b)-1])
}
