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
	t.Skip("shaped in Task 5 — placeholder shape returns a raw string")

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
