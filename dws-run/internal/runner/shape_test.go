package runner

import (
	"context"
	"errors"
	"testing"

	"github.com/dws/dws-run/internal/config"
)

func runWith(t *testing.T, command string, ret config.ReturnMode) (any, error) {
	t.Helper()
	cfg := shellCfg(command)
	cfg.Return = ret
	return New(cfg).Run(context.Background(), map[string]any{})
}

func TestReturnStdout(t *testing.T) {
	out, err := runWith(t, "printf 'hello'", config.ReturnStdout)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if out != "hello" {
		t.Fatalf("got %#v, want \"hello\"", out)
	}
}

func TestReturnStderr(t *testing.T) {
	out, err := runWith(t, "printf 'oops' >&2", config.ReturnStderr)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if out != "oops" {
		t.Fatalf("got %#v, want \"oops\"", out)
	}
}

func TestReturnCodeIsDataOnNonZeroExit(t *testing.T) {
	out, err := runWith(t, "exit 3", config.ReturnCode)
	if err != nil {
		t.Fatalf("RETURN=code must not error on a non-zero exit: %v", err)
	}
	if out != float64(3) && out != 3 {
		t.Fatalf("got %#v, want 3", out)
	}
}

func TestReturnAllIsDataOnNonZeroExit(t *testing.T) {
	out, err := runWith(t, "printf 'out'; printf 'err' >&2; exit 1", config.ReturnAll)
	if err != nil {
		t.Fatalf("RETURN=all must not error on a non-zero exit: %v", err)
	}
	m, ok := out.(map[string]any)
	if !ok {
		t.Fatalf("expected an object, got %#v", out)
	}
	if m["code"] != 1 && m["code"] != float64(1) {
		t.Errorf("code: got %#v, want 1", m["code"])
	}
	if m["stdout"] != "out" || m["stderr"] != "err" {
		t.Errorf("stdout/stderr: got %#v / %#v", m["stdout"], m["stderr"])
	}
}

func TestReturnNoneYieldsEmptyObject(t *testing.T) {
	out, err := runWith(t, "printf 'ignored'", config.ReturnNone)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	m, ok := out.(map[string]any)
	if !ok || len(m) != 0 {
		t.Fatalf("got %#v, want an empty object", out)
	}
}

func TestNonZeroExitIsAnErrorUnderStdout(t *testing.T) {
	_, err := runWith(t, "printf 'boom' >&2; exit 2", config.ReturnStdout)
	var exitErr *ExitError
	if !errors.As(err, &exitErr) {
		t.Fatalf("expected *ExitError, got %#v", err)
	}
	if exitErr.Code != 2 {
		t.Errorf("code: got %d, want 2", exitErr.Code)
	}
	if exitErr.Stderr != "boom" {
		t.Errorf("stderr: got %q, want \"boom\"", exitErr.Stderr)
	}
	if exitErr.Task != "t" {
		t.Errorf("task: got %q, want \"t\"", exitErr.Task)
	}
}

func TestNonZeroExitIsAnErrorUnderNone(t *testing.T) {
	_, err := runWith(t, "exit 1", config.ReturnNone)
	var exitErr *ExitError
	if !errors.As(err, &exitErr) {
		t.Fatalf("expected *ExitError, got %#v", err)
	}
}

// The following three tests close out the ten-case matrix (five RETURN modes
// x zero/non-zero exit) that the brief's cases above don't hit directly. The
// exit-code rule is a single shared conditional, but given how costly getting
// it backwards would be, each combination gets its own explicit assertion
// rather than relying on inference from adjacent cases.

func TestNonZeroExitIsAnErrorUnderStderr(t *testing.T) {
	_, err := runWith(t, "printf 'boom' >&2; exit 4", config.ReturnStderr)
	var exitErr *ExitError
	if !errors.As(err, &exitErr) {
		t.Fatalf("expected *ExitError, got %#v", err)
	}
	if exitErr.Code != 4 {
		t.Errorf("code: got %d, want 4", exitErr.Code)
	}
}

func TestReturnCodeZeroExit(t *testing.T) {
	out, err := runWith(t, "exit 0", config.ReturnCode)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if out != 0 {
		t.Fatalf("got %#v, want 0", out)
	}
}

func TestReturnAllZeroExit(t *testing.T) {
	out, err := runWith(t, "printf 'out'; printf 'err' >&2", config.ReturnAll)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	m, ok := out.(map[string]any)
	if !ok {
		t.Fatalf("expected an object, got %#v", out)
	}
	if m["code"] != 0 {
		t.Errorf("code: got %#v, want 0", m["code"])
	}
	if m["stdout"] != "out" || m["stderr"] != "err" {
		t.Errorf("stdout/stderr: got %#v / %#v", m["stdout"], m["stderr"])
	}
}
