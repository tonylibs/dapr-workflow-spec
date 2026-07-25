package runner

import (
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

// shape turns the subprocess result into the value returned to the caller,
// enforcing the exit-code rule first.
func (r *Runner) shape(input map[string]any, res result) (any, error) {
	if res.Code != 0 && !r.exitCodeIsData() {
		return nil, &ExitError{Task: r.cfg.Task, Code: res.Code, Stderr: res.Stderr}
	}

	value, present := r.selectValue(res)
	if !present {
		return map[string]any{}, nil
	}
	return value, nil
}
