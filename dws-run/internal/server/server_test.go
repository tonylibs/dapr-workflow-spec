package server

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/dws/dws-run/internal/config"
	"github.com/dws/dws-run/internal/runner"
)

func handler(t *testing.T, command string, ret config.ReturnMode) http.Handler {
	t.Helper()
	cfg := config.Config{
		Mode: config.ModeShell, Task: "t", Command: command,
		Return: ret, Output: config.OutputReplace, Timeout: 10 * time.Second,
	}
	return New(cfg, runner.New(cfg), slog.New(slog.DiscardHandler)).Handler()
}

func do(t *testing.T, h http.Handler, method, path, body string) (*http.Response, string) {
	t.Helper()
	req := httptest.NewRequest(method, path, strings.NewReader(body))
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	res := rec.Result()
	b, _ := io.ReadAll(res.Body)
	return res, string(b)
}

func TestHealthz(t *testing.T) {
	res, body := do(t, handler(t, "true", config.ReturnStdout), http.MethodGet, "/healthz", "")
	if res.StatusCode != http.StatusOK {
		t.Fatalf("status: got %d, want 200", res.StatusCode)
	}
	if !strings.Contains(body, `"task":"t"`) {
		t.Errorf("body should name the task: %s", body)
	}
}

func TestEmptyBodyIsEmptyData(t *testing.T) {
	res, body := do(t, handler(t, "cat", config.ReturnStdout), http.MethodPost, "/run", "")
	if res.StatusCode != http.StatusOK {
		t.Fatalf("status: got %d, want 200 (body: %s)", res.StatusCode, body)
	}
}

func TestMalformedBodyIs400(t *testing.T) {
	res, _ := do(t, handler(t, "cat", config.ReturnStdout), http.MethodPost, "/run", "{not json")
	if res.StatusCode != http.StatusBadRequest {
		t.Fatalf("status: got %d, want 400", res.StatusCode)
	}
}

func TestNonZeroExitIs502UnderStdout(t *testing.T) {
	h := handler(t, "printf 'boom' >&2; exit 2", config.ReturnStdout)
	res, body := do(t, h, http.MethodPost, "/run", "{}")
	if res.StatusCode != http.StatusBadGateway {
		t.Fatalf("status: got %d, want 502", res.StatusCode)
	}
	var payload map[string]any
	if err := json.Unmarshal([]byte(body), &payload); err != nil {
		t.Fatalf("body is not JSON: %s", body)
	}
	if payload["exitCode"] != float64(2) {
		t.Errorf("exitCode: got %#v, want 2", payload["exitCode"])
	}
	if payload["stderr"] != "boom" {
		t.Errorf("stderr: got %#v, want \"boom\"", payload["stderr"])
	}
	if payload["task"] != "t" {
		t.Errorf("task: got %#v, want \"t\"", payload["task"])
	}
}

func TestNonZeroExitIs200UnderReturnCode(t *testing.T) {
	h := handler(t, "exit 2", config.ReturnCode)
	res, body := do(t, h, http.MethodPost, "/run", "{}")
	if res.StatusCode != http.StatusOK {
		t.Fatalf("status: got %d, want 200 (body: %s)", res.StatusCode, body)
	}
	if strings.TrimSpace(body) != "2" {
		t.Errorf("body: got %q, want \"2\"", strings.TrimSpace(body))
	}
}
