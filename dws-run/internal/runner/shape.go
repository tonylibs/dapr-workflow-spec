package runner

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/dws/dws-run/internal/config"
)

// exitCodeIsData reports whether a non-zero exit should be returned as data
// rather than as a retryable failure. RETURN=code and RETURN=all mean the
// author explicitly asked to observe the exit code, so turning it into a 502
// would both hide the requested value and trigger pointless retries.
func (r *Runner) exitCodeIsData() bool {
	return r.cfg.Return == config.ReturnCode || r.cfg.Return == config.ReturnAll
}

// selectValue picks the raw value per RETURN. The bool reports whether a value
// is present at all — false only for RETURN=none.
func (r *Runner) selectValue(res result) (any, bool) {
	switch r.cfg.Return {
	case config.ReturnStderr:
		return res.Stderr, true
	case config.ReturnCode:
		return res.Code, true
	case config.ReturnAll:
		return map[string]any{
			"code":   res.Code,
			"stdout": res.Stdout,
			"stderr": res.Stderr,
		}, true
	case config.ReturnNone:
		return nil, false
	default: // ReturnStdout
		return res.Stdout, true
	}
}

// shape turns the subprocess result into the value returned to the caller:
// RETURN picks the raw value, then OUTPUT decides how it folds into the
// response.
func (r *Runner) shape(input map[string]any, res result) (any, error) {
	if res.Code != 0 && !r.exitCodeIsData() {
		return nil, &ExitError{Task: r.cfg.Task, Code: res.Code, Stderr: res.Stderr}
	}

	value, present := r.selectValue(res)
	if !present {
		if r.cfg.Output == config.OutputMerge {
			return cloneInput(input), nil
		}
		return map[string]any{}, nil
	}

	// Text captured from stdout/stderr is JSON if it parses, and a plain
	// string otherwise. Unlike dws-call-http, unparseable output is not an
	// error — plain text is the normal output of a shell command.
	if s, ok := value.(string); ok {
		value = parseJSONOrString(s)
	}

	if r.cfg.Output == config.OutputMerge {
		obj, ok := value.(map[string]any)
		if !ok {
			return nil, fmt.Errorf("OUTPUT=merge requires a JSON object result, got %T", value)
		}
		merged := cloneInput(input)
		for k, v := range obj {
			merged[k] = v
		}
		return merged, nil
	}
	return value, nil
}

// parseJSONOrString returns the parsed JSON value when the trimmed text is
// valid JSON, and the original string otherwise.
func parseJSONOrString(s string) any {
	trimmed := strings.TrimSpace(s)
	if trimmed == "" {
		return s
	}
	var v any
	if err := json.Unmarshal([]byte(trimmed), &v); err != nil {
		return s
	}
	return v
}

func cloneInput(input map[string]any) map[string]any {
	out := make(map[string]any, len(input))
	for k, v := range input {
		out[k] = v
	}
	return out
}
