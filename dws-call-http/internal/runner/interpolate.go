package runner

import (
	"fmt"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

var placeholderRe = regexp.MustCompile(`\{([^{}]+)\}`)

// Interpolate replaces {key} tokens in s with the matching top-level value from
// data. It returns an error naming every placeholder that has no matching key,
// so misconfiguration surfaces clearly instead of sending a malformed request.
func Interpolate(s string, data map[string]any) (string, error) {
	var missing []string
	out := placeholderRe.ReplaceAllStringFunc(s, func(tok string) string {
		key := tok[1 : len(tok)-1]
		v, ok := data[key]
		if !ok {
			missing = append(missing, key)
			return tok
		}
		return valueToString(v)
	})
	if len(missing) > 0 {
		sort.Strings(missing)
		return "", fmt.Errorf("missing placeholder values for: %s", strings.Join(missing, ", "))
	}
	return out, nil
}

// valueToString renders a JSON-decoded value for use in a URL, query, or body
// template. JSON numbers decode as float64; integral values render without a
// trailing ".0" so paths like /orders/42 stay clean.
func valueToString(v any) string {
	switch t := v.(type) {
	case string:
		return t
	case float64:
		if t == float64(int64(t)) {
			return strconv.FormatInt(int64(t), 10)
		}
		return strconv.FormatFloat(t, 'f', -1, 64)
	case bool:
		return strconv.FormatBool(t)
	case nil:
		return ""
	default:
		return fmt.Sprint(t)
	}
}
