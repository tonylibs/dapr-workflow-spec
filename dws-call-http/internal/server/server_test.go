package server

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/dws/dws-call-http/internal/config"
	"github.com/dws/dws-call-http/internal/runner"
)

func newTestServer(t *testing.T, upstreamURL string, cfg config.Config) http.Handler {
	t.Helper()
	cfg.Endpoint = upstreamURL
	if cfg.Method == "" {
		cfg.Method = "POST"
	}
	if cfg.BodyMode == "" {
		cfg.BodyMode = config.BodyPassthrough
	}
	if cfg.Output == "" {
		cfg.Output = config.OutputReplace
	}
	if cfg.Task == "" {
		cfg.Task = "check-inventory"
	}
	log := slog.New(slog.NewJSONHandler(io.Discard, nil))
	return New(cfg, runner.New(cfg), log).Handler()
}

func TestHealthz(t *testing.T) {
	h := newTestServer(t, "https://svc/x", config.Config{})
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status: got %d, want 200", rec.Code)
	}
	var body map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if body["status"] != "ok" {
		t.Fatalf("status field: got %v", body["status"])
	}
}

func TestRunSuccessReplace(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"available":true}`)
	}))
	defer upstream.Close()

	h := newTestServer(t, upstream.URL, config.Config{Output: config.OutputReplace})
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/run", strings.NewReader(`{"orderId":"o1"}`))
	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status: got %d, want 200", rec.Code)
	}
	var body map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if body["available"] != true {
		t.Fatalf("expected available=true, got %v", body)
	}
	if _, exists := body["orderId"]; exists {
		t.Fatalf("replace mode should not retain input keys, got %v", body)
	}
}

func TestRunUpstreamNon2xxMapsTo502(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusServiceUnavailable)
		_, _ = io.WriteString(w, `{"msg":"down"}`)
	}))
	defer upstream.Close()

	h := newTestServer(t, upstream.URL, config.Config{Task: "check-inventory"})
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/run", strings.NewReader(`{"orderId":"o1"}`))
	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadGateway {
		t.Fatalf("status: got %d, want 502", rec.Code)
	}
	var body map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if body["task"] != "check-inventory" {
		t.Fatalf("task: got %v", body["task"])
	}
	if body["status"] != float64(http.StatusServiceUnavailable) {
		t.Fatalf("status field: got %v, want 503", body["status"])
	}
	if body["body"] != `{"msg":"down"}` {
		t.Fatalf("body field: got %v", body["body"])
	}
}

func TestRunInvalidJSONBodyMapsTo400(t *testing.T) {
	h := newTestServer(t, "https://svc/x", config.Config{})
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/run", strings.NewReader(`{not-json`))
	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status: got %d, want 400", rec.Code)
	}
}

func TestRunEmptyBodyTreatedAsEmptyData(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		b, _ := io.ReadAll(r.Body)
		if string(b) != `{}` {
			t.Errorf("expected empty object body, got %q", b)
		}
		_, _ = io.WriteString(w, `{"ok":true}`)
	}))
	defer upstream.Close()

	h := newTestServer(t, upstream.URL, config.Config{})
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/run", http.NoBody)
	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status: got %d, want 200", rec.Code)
	}
}
