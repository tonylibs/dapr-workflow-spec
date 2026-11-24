// Package expr prepares the deployed jq expression: it unwraps the DSL's
// ${ … } form and statically checks the parsed query before it is compiled.
package expr

import "strings"

// Unwrap returns the jq text of a JQ_EXPRESSION value the way v1 does: trim
// it, and if it starts with "${" and ends with "}", take the text between them
// and trim that. Anything else is bare jq and is returned trimmed.
func Unwrap(text string) string {
	trimmed := strings.TrimSpace(text)
	if strings.HasPrefix(trimmed, "${") && strings.HasSuffix(trimmed, "}") {
		return strings.TrimSpace(trimmed[len("${") : len(trimmed)-len("}")])
	}
	return trimmed
}
