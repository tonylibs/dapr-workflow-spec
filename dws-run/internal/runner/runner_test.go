package runner

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/dws/dws-run/internal/config"
)

func shellCfg(command string) config.Config {
	return config.Config{
		Mode:    config.ModeShell,
		Task:    "t",
		Command: command,
		Return:  config.ReturnStdout,
		Output:  config.OutputReplace,
		Timeout: 10 * time.Second,
	}
}

func TestStdinReceivesFullInput(t *testing.T) {
	r := New(shellCfg("cat"))
	out, err := r.Run(context.Background(), map[string]any{"order": map[string]any{"id": float64(7)}})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	m, ok := out.(map[string]any)
	if !ok {
		t.Fatalf("expected an object, got %#v", out)
	}
	order, ok := m["order"].(map[string]any)
	if !ok || order["id"] != float64(7) {
		t.Fatalf("input did not round-trip through stdin: %#v", m)
	}
}

func TestStdinIsClosed(t *testing.T) {
	// `cat` only exits when stdin reaches EOF; a hang here means stdin was
	// left open.
	r := New(shellCfg("cat > /dev/null; echo done"))
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	out, err := r.Run(ctx, map[string]any{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if out != "done" {
		t.Fatalf("got %#v, want \"done\"", out)
	}
}

func TestEnvironmentExtendsRatherThanReplaces(t *testing.T) {
	cfg := shellCfg(`printf '%s' "$API_TOKEN"; test -n "$PATH" || exit 9`)
	cfg.Environment = map[string]string{"API_TOKEN": "abc"}
	out, err := New(cfg).Run(context.Background(), map[string]any{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if out != "abc" {
		t.Fatalf("got %#v, want \"abc\" (and a preserved PATH)", out)
	}
}

func TestTimeoutTerminatesSubprocess(t *testing.T) {
	cfg := shellCfg("sleep 5")
	cfg.Timeout = 100 * time.Millisecond
	start := time.Now()
	_, err := New(cfg).Run(context.Background(), map[string]any{})
	if err == nil {
		t.Fatal("expected an error when the subprocess exceeds TIMEOUT")
	}
	if elapsed := time.Since(start); elapsed > 3*time.Second {
		t.Fatalf("subprocess was not terminated promptly (took %s)", elapsed)
	}
}

func TestCapturedStdoutTrimsTrailingNewline(t *testing.T) {
	// echo (unlike printf) always emits a trailing newline; the capture
	// should strip it rather than surface it as workflow data.
	r := New(shellCfg("echo hello"))
	res, err := r.execute(context.Background(), map[string]any{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if res.Stdout != "hello" {
		t.Fatalf("got %q, want %q", res.Stdout, "hello")
	}
}

func TestCapturedStderrTrimsTrailingNewline(t *testing.T) {
	// stdout and stderr must be trimmed identically, or RETURN=all and
	// ExitError.Stderr end up asymmetric with RETURN=stdout.
	r := New(shellCfg("echo oops >&2"))
	res, err := r.execute(context.Background(), map[string]any{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if res.Stderr != "oops" {
		t.Fatalf("got %q, want %q", res.Stderr, "oops")
	}
}

func TestNonZeroExitNearDeadlineIsNotMisreportedAsTimeout(t *testing.T) {
	// The subprocess exits on its own, just inside a short TIMEOUT, with a
	// genuine non-zero code. It must never be killed (killedByUs stays
	// false), so execute must return the populated result — not a
	// SpawnError claiming a timeout that never happened.
	cfg := shellCfg("sleep 0.28; echo done; exit 7")
	cfg.Timeout = 400 * time.Millisecond
	res, err := New(cfg).execute(context.Background(), map[string]any{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if res.Code != 7 {
		t.Fatalf("got code %d, want 7", res.Code)
	}
	if res.Stdout != "done" {
		t.Fatalf("got stdout %q, want %q", res.Stdout, "done")
	}
}

func TestCallerCancellationIsReportedAsCanceled(t *testing.T) {
	// TIMEOUT is generous; the parent context is canceled first, for a
	// reason unrelated to TIMEOUT (e.g. an upstream disconnect). The
	// resulting SpawnError must say so, not claim a timeout.
	cfg := shellCfg("sleep 5")
	cfg.Timeout = 10 * time.Second

	ctx, cancel := context.WithCancel(context.Background())
	go func() {
		time.Sleep(100 * time.Millisecond)
		cancel()
	}()

	start := time.Now()
	_, err := New(cfg).execute(ctx, map[string]any{})
	if elapsed := time.Since(start); elapsed > 3*time.Second {
		t.Fatalf("subprocess was not terminated promptly (took %s)", elapsed)
	}

	var spawn *SpawnError
	if !errors.As(err, &spawn) {
		t.Fatalf("expected *SpawnError, got %#v", err)
	}
	msg := spawn.Error()
	if strings.Contains(msg, "timed out") {
		t.Errorf("expected cancellation, not a timeout claim: %v", spawn)
	}
	if !strings.Contains(msg, "cancel") {
		t.Errorf("expected error to report cancellation: %v", spawn)
	}
}

// TestInvalidIdentifierIsNotSpawnError guards the request-time defense in
// depth in scriptSource: config.Load already rejects a bad argument name at
// startup, but if a Config bypassing Load ever carries one anyway, execute
// must not wrap it in *SpawnError (which the server maps to a retryable
// 502) — retrying can never fix a permanently invalid argument name, so it
// must surface as a plain error (mapped to 500 by the server's fallback
// branch) instead.
func TestInvalidIdentifierIsNotSpawnError(t *testing.T) {
	cfg := config.Config{
		Mode:      config.ModeScriptJS,
		Task:      "t",
		Script:    "1",
		Return:    config.ReturnStdout,
		Output:    config.OutputReplace,
		Timeout:   10 * time.Second,
		Arguments: []config.Argument{{Name: "const", Value: "x"}},
	}
	_, err := New(cfg).Run(context.Background(), map[string]any{})
	if err == nil {
		t.Fatal("expected an error for a JS-reserved argument name")
	}
	var spawn *SpawnError
	if errors.As(err, &spawn) {
		t.Fatalf("expected a plain (non-retryable) error, got *SpawnError: %v", spawn)
	}
	var invalidIdent *config.InvalidIdentifierError
	if !errors.As(err, &invalidIdent) {
		t.Fatalf("expected *config.InvalidIdentifierError, got %#v", err)
	}
}

func TestSpawnFailureIsSpawnError(t *testing.T) {
	cfg := shellCfg("x")
	cfg.Mode = config.ModeScriptPython
	cfg.Command = ""
	cfg.Script = "print(1)"
	r := New(cfg)
	r.interpreter = "definitely-not-an-interpreter-9f2a"
	_, err := r.Run(context.Background(), map[string]any{})
	var spawn *SpawnError
	if !errors.As(err, &spawn) {
		t.Fatalf("expected *SpawnError, got %#v", err)
	}
	if !strings.Contains(spawn.Error(), `"t"`) {
		t.Errorf("error should name the task: %v", spawn)
	}
}
