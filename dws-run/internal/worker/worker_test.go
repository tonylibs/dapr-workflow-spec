package worker

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/dapr/durabletask-go/workflow"
	"github.com/dws/dws-run/internal/config"
	"github.com/dws/dws-run/internal/runner"
)

func newWorker(t *testing.T, cfg config.Config) *Worker {
	t.Helper()
	return New(cfg, runner.New(cfg), slog.New(slog.DiscardHandler))
}

func shellCfg(command string, ret config.ReturnMode, out config.OutputMode) config.Config {
	return config.Config{
		Mode: config.ModeShell, Task: "t", Command: command,
		Return: ret, Output: out, Timeout: 10 * time.Second,
	}
}

func TestEmptyInputIsEmptyData(t *testing.T) {
	w := newWorker(t, shellCfg("cat", config.ReturnStdout, config.OutputReplace))
	// nil input must be treated as {} rather than an error, and `cat` echoes
	// the empty-object stdin back.
	got, err := w.RunActivity(context.Background(), nil)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	obj, ok := got.(map[string]any)
	if !ok {
		t.Fatalf("result: got %T, want map", got)
	}
	if len(obj) != 0 {
		t.Errorf("result: got %#v, want empty object", obj)
	}
}

func TestSuccessShapesOutput(t *testing.T) {
	w := newWorker(t, shellCfg(`echo '{"answer":42}'`, config.ReturnStdout, config.OutputReplace))
	got, err := w.RunActivity(context.Background(), map[string]any{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	obj, ok := got.(map[string]any)
	if !ok {
		t.Fatalf("result: got %T, want map", got)
	}
	if obj["answer"] != float64(42) {
		t.Errorf("answer: got %#v, want 42", obj["answer"])
	}
}

func TestExitErrorIsUpstreamFailure(t *testing.T) {
	w := newWorker(t, shellCfg("printf 'boom' >&2; exit 2", config.ReturnStdout, config.OutputReplace))
	_, err := w.RunActivity(context.Background(), map[string]any{})
	if err == nil {
		t.Fatal("expected error, got nil")
	}
	if !strings.Contains(err.Error(), "step 't' upstream failure:") {
		t.Errorf("message: got %q, want upstream-failure marker", err.Error())
	}
}

func TestSpawnErrorIsUpstreamFailure(t *testing.T) {
	cfg := shellCfg("sleep 1", config.ReturnStdout, config.OutputReplace)
	cfg.Timeout = time.Millisecond // force a timeout → SpawnError
	w := newWorker(t, cfg)
	_, err := w.RunActivity(context.Background(), map[string]any{})
	if err == nil {
		t.Fatal("expected error, got nil")
	}
	if !strings.Contains(err.Error(), "step 't' upstream failure:") {
		t.Errorf("message: got %q, want upstream-failure marker", err.Error())
	}
}

func TestMergeShapeErrorIsConfigFailure(t *testing.T) {
	// OUTPUT=merge requires an object result; "hello" is a plain string, so
	// shape() returns a plain error — non-retryable.
	w := newWorker(t, shellCfg("echo hello", config.ReturnStdout, config.OutputMerge))
	_, err := w.RunActivity(context.Background(), map[string]any{})
	if err == nil {
		t.Fatal("expected error, got nil")
	}
	if !strings.Contains(err.Error(), "step 't' config failure:") {
		t.Errorf("message: got %q, want config-failure marker", err.Error())
	}
	if strings.Contains(err.Error(), "upstream failure") {
		t.Errorf("merge-shape failure must not be upstream: %q", err.Error())
	}
}

func TestHealthz(t *testing.T) {
	w := newWorker(t, shellCfg("true", config.ReturnStdout, config.OutputReplace))
	req := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	rec := httptest.NewRecorder()
	w.HealthHandler().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status: got %d, want 200", rec.Code)
	}
	var payload map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &payload); err != nil {
		t.Fatalf("body is not JSON: %s", rec.Body.String())
	}
	if payload["status"] != "ok" {
		t.Errorf("status: got %#v, want \"ok\"", payload["status"])
	}
	if payload["task"] != "t" {
		t.Errorf("task: got %#v, want \"t\"", payload["task"])
	}
}

func TestRegisterCanonicalActivity(t *testing.T) {
	w := newWorker(t, shellCfg("true", config.ReturnStdout, config.OutputReplace))
	reg := workflow.NewRegistry()
	if err := w.Register(reg); err != nil {
		t.Fatalf("Register: %v", err)
	}
	// The activity name is fixed and shared with the Java orchestrator.
	if ActivityName != "Run" {
		t.Errorf("ActivityName: got %q, want \"Run\"", ActivityName)
	}
	// Registering the same name twice must be rejected by the registry,
	// confirming "Run" is the registered name.
	if err := w.Register(reg); err == nil {
		t.Error("expected duplicate registration of \"Run\" to fail")
	}
}
