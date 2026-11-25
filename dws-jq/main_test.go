package main

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/dws/dws-jq/internal/config"
)

const (
	startupLimit = 5 * time.Second
	pollInterval = 10 * time.Millisecond

	expressionSeparator = "; expression: "
)

var (
	buildOnce sync.Once
	binPath   string
	buildErr  error
)

// TestMain removes the binary built by binary, if any.
func TestMain(m *testing.M) {
	code := m.Run()
	if binPath != "" {
		_ = os.RemoveAll(filepath.Dir(binPath))
	}
	os.Exit(code)
}

// binary builds the dws-jq binary once per test run.
func binary(t *testing.T) string {
	t.Helper()
	buildOnce.Do(func() {
		dir, err := os.MkdirTemp("", "dws-jq-bin")
		if err != nil {
			buildErr = err
			return
		}
		binPath = filepath.Join(dir, "dws-jq")
		if runtime.GOOS == "windows" {
			binPath += ".exe"
		}
		out, err := exec.Command("go", "build", "-o", binPath, ".").CombinedOutput()
		if err != nil {
			buildErr = errors.New(string(out))
		}
	})
	if buildErr != nil {
		t.Fatalf("go build: %v", buildErr)
	}
	return binPath
}

// freePort returns a TCP port nothing listens on right now.
func freePort(t *testing.T) string {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	defer ln.Close()
	return strconv.Itoa(ln.Addr().(*net.TCPAddr).Port)
}

// environ is the parent environment without any variable dws-jq reads, plus
// JSON logging and the given settings.
func environ(settings map[string]string) []string {
	var env []string
	for _, kv := range os.Environ() {
		name, _, _ := strings.Cut(kv, "=")
		upper := strings.ToUpper(name)
		if strings.HasPrefix(upper, "JQ_") || strings.HasPrefix(upper, "DAPR_") ||
			upper == "PORT" || upper == "DWS_LOG_FORMAT" {
			continue
		}
		env = append(env, kv)
	}
	env = append(env, "DWS_LOG_FORMAT=json")
	for k, v := range settings {
		env = append(env, k+"="+v)
	}
	return env
}

// records parses every stdout line as a JSON log record.
func records(t *testing.T, stdout []byte) []map[string]any {
	t.Helper()
	var out []map[string]any
	for _, line := range strings.Split(strings.TrimSpace(string(stdout)), "\n") {
		if line == "" {
			continue
		}
		var rec map[string]any
		if err := json.Unmarshal([]byte(line), &rec); err != nil {
			t.Fatalf("stdout line %q is not JSON: %v", line, err)
		}
		out = append(out, rec)
	}
	return out
}

func withSeverity(recs []map[string]any, severity string) []map[string]any {
	var out []map[string]any
	for _, r := range recs {
		if r["severity"] == severity {
			out = append(out, r)
		}
	}
	return out
}

func TestMain_startupFailure(t *testing.T) {
	bin := binary(t)
	// reason must appear in the part of the ERROR message before the
	// expression, so a fragment that is also in the expression text proves
	// nothing on its own.
	const site = "runCheck.jq.when"
	const token = "dapr-token-not-for-logs"
	tests := []struct {
		name       string
		expression string
		variables  string
		reason     string
	}{
		{name: "missing expression", reason: "JQ_EXPRESSION"},
		{name: "malformed expression", expression: ".a |", reason: "unexpected EOF"},
		{name: "blocked builtin", expression: "${ { home: env.HOME } }", reason: `"env"`},
		{name: "undeclared variable", expression: "$item < 3", reason: "$item"},
		{name: "secret store unset", expression: "$secrets.a", reason: "JQ_SECRET_STORE"},
		{name: "dynamic secret access", expression: "$secrets[.k]", reason: "static"},
		{name: "duplicate variable", expression: " ${ .count } ", variables: `["item","item"]`, reason: "JQ_VARIABLES"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			port := freePort(t)
			settings := map[string]string{
				"JQ_EXPRESSION":  tt.expression,
				"JQ_SITE":        site,
				"DAPR_API_TOKEN": token,
				"PORT":           port,
			}
			if tt.variables != "" {
				settings["JQ_VARIABLES"] = tt.variables
			}
			ctx, cancel := context.WithTimeout(context.Background(), startupLimit)
			defer cancel()
			cmd := exec.CommandContext(ctx, bin)
			cmd.Env = environ(settings)
			var stdout bytes.Buffer
			cmd.Stdout = &stdout

			if err := cmd.Start(); err != nil {
				t.Fatalf("start: %v", err)
			}
			done := make(chan error, 1)
			go func() { done <- cmd.Wait() }()
			err := waitNeverListening(t, port, done)

			if ctx.Err() != nil {
				t.Fatalf("process did not exit within %s", startupLimit)
			}
			var exitErr *exec.ExitError
			if !errors.As(err, &exitErr) || exitErr.ExitCode() == 0 {
				t.Fatalf("exit = %v, want a non-zero exit code", err)
			}
			if strings.Contains(stdout.String(), token) {
				t.Fatal("stdout contains DAPR_API_TOKEN")
			}
			recs := records(t, stdout.Bytes())
			if len(recs) != 1 || recs[0]["severity"] != "ERROR" {
				t.Fatalf("want a single ERROR record; stdout:\n%s", stdout.String())
			}
			rec := recs[0]
			if rec["site"] != site {
				t.Errorf("site = %q, want %q", rec["site"], site)
			}
			if rec["expression"] != tt.expression {
				t.Errorf("expression = %q, want %q", rec["expression"], tt.expression)
			}
			message, _ := rec["message"].(string)
			reasonPart, expression, found := strings.Cut(message, expressionSeparator)
			if !found || expression != tt.expression {
				t.Errorf("ERROR message %q does not end with %q%s", message, expressionSeparator, tt.expression)
			}
			if !strings.Contains(reasonPart, tt.reason) {
				t.Errorf("reason %q of ERROR message %q does not contain %q", reasonPart, message, tt.reason)
			}
		})
	}
}

// waitNeverListening dials port until the process exits, failing the test if
// a connection is ever accepted, and returns the process's exit error.
func waitNeverListening(t *testing.T, port string, done <-chan error) error {
	t.Helper()
	for {
		select {
		case err := <-done:
			return err
		case <-time.After(pollInterval):
			if conn, err := net.DialTimeout("tcp", "127.0.0.1:"+port, pollInterval); err == nil {
				conn.Close()
				t.Errorf("port %s accepted a connection", port)
			}
		}
	}
}

// serving is a started dws-jq process.
type serving struct {
	baseURL string
	cmd     *exec.Cmd
	stdout  *bytes.Buffer
}

// serve starts the binary and waits until /healthz answers 200.
func serve(t *testing.T, settings map[string]string) *serving {
	t.Helper()
	port := freePort(t)
	settings["PORT"] = port
	cmd := exec.Command(binary(t))
	cmd.Env = environ(settings)
	var stdout bytes.Buffer
	cmd.Stdout = &stdout
	if err := cmd.Start(); err != nil {
		t.Fatalf("start: %v", err)
	}
	s := &serving{baseURL: "http://127.0.0.1:" + port, cmd: cmd, stdout: &stdout}
	t.Cleanup(func() { s.stop() })

	deadline := time.Now().Add(startupLimit)
	for time.Now().Before(deadline) {
		resp, err := http.Get(s.baseURL + "/healthz")
		if err == nil {
			resp.Body.Close()
			if resp.StatusCode == http.StatusOK {
				return s
			}
		}
		time.Sleep(pollInterval)
	}
	t.Fatalf("not ready within %s; stdout:\n%s", startupLimit, stdout.String())
	return nil
}

// stop kills the process and returns everything it wrote to stdout.
func (s *serving) stop() []byte {
	if s.cmd.ProcessState == nil {
		_ = s.cmd.Process.Kill()
		_ = s.cmd.Wait()
	}
	return s.stdout.Bytes()
}

func TestMain_servesWithSecrets(t *testing.T) {
	const value = `s3"cret`
	var reads atomic.Int32
	sidecar := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		reads.Add(1)
		if r.URL.Path != "/v1.0/secrets/kubernetes/a" || r.Header.Get("dapr-api-token") != "tok" {
			http.NotFound(w, r)
			return
		}
		_ = json.NewEncoder(w).Encode(map[string]string{"value": value})
	}))
	defer sidecar.Close()
	sidecarURL, err := url.Parse(sidecar.URL)
	if err != nil {
		t.Fatalf("parse sidecar URL: %v", err)
	}

	s := serve(t, map[string]string{
		"JQ_EXPRESSION":   "error($secrets.a)",
		"JQ_SECRET_STORE": "kubernetes",
		"JQ_SITE":         "site-1",
		"DAPR_HTTP_PORT":  sidecarURL.Port(),
		"DAPR_API_TOKEN":  "tok",
	})
	resp, err := http.Post(s.baseURL+"/run", "application/json", strings.NewReader(`{}`))
	if err != nil {
		t.Fatalf("POST /run: %v", err)
	}
	body, _ := io.ReadAll(resp.Body)
	resp.Body.Close()
	stdout := s.stop()

	if resp.StatusCode != http.StatusUnprocessableEntity || !strings.Contains(string(body), "***") {
		t.Errorf("POST /run = %d %s, want 422 with a redacted detail", resp.StatusCode, body)
	}
	if n := reads.Load(); n != 1 {
		t.Errorf("sidecar read %d times, want 1", n)
	}
	for _, form := range []string{value, `s3\"cret`} {
		if strings.Contains(string(body), form) || strings.Contains(string(stdout), form) {
			t.Errorf("secret form %q leaked; body %s; stdout:\n%s", form, body, stdout)
		}
	}
	ready := false
	for _, r := range withSeverity(records(t, stdout), "INFO") {
		if r["message"] == "ready" && r["site"] == "site-1" {
			ready = true
		}
	}
	if !ready {
		t.Errorf("no INFO ready record with the site; stdout:\n%s", stdout)
	}
}

func TestMain_warnsOnUnusedSecretStore(t *testing.T) {
	s := serve(t, map[string]string{"JQ_EXPRESSION": ".", "JQ_SECRET_STORE": "kubernetes"})
	stdout := s.stop()

	warns := withSeverity(records(t, stdout), "WARN")
	if len(warns) != 1 || !strings.Contains(warns[0]["message"].(string), "JQ_SECRET_STORE") {
		t.Errorf("WARN records = %v, want one naming JQ_SECRET_STORE", warns)
	}
}

// inProcessSecrets sets the environment for an in-process run of
// `$secrets.a` against a fake sidecar served by handler.
func inProcessSecrets(t *testing.T, handler http.Handler) {
	t.Helper()
	for _, name := range config.Names {
		t.Setenv(name, "")
	}
	sidecar := httptest.NewServer(handler)
	t.Cleanup(sidecar.Close)
	sidecarURL, err := url.Parse(sidecar.URL)
	if err != nil {
		t.Fatalf("parse sidecar URL: %v", err)
	}
	t.Setenv("JQ_EXPRESSION", "$secrets.a")
	t.Setenv("JQ_SECRET_STORE", "kubernetes")
	t.Setenv("DAPR_HTTP_PORT", sidecarURL.Port())
	t.Setenv("PORT", freePort(t))
	t.Setenv("DWS_LOG_FORMAT", "json")
}

// TestMain_cancelDuringSecretFetch runs in process because a signal cannot be
// sent to a child process on every platform; cancelling run's context is what
// SIGINT/SIGTERM does.
func TestMain_cancelDuringSecretFetch(t *testing.T) {
	inProcessSecrets(t, http.NotFoundHandler()) // never yields the secret
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	time.AfterFunc(300*time.Millisecond, cancel)
	var stdout bytes.Buffer

	code := run(ctx, &stdout)

	if code != 0 {
		t.Errorf("run() = %d, want 0", code)
	}
	recs := records(t, stdout.Bytes())
	if errs := withSeverity(recs, "ERROR"); len(errs) != 0 {
		t.Errorf("got ERROR records %v, want none", errs)
	}
	stopped := false
	for _, r := range withSeverity(recs, "INFO") {
		if r["message"] == "shutting down" {
			stopped = true
		}
	}
	if !stopped {
		t.Errorf("no INFO shutting down record; stdout:\n%s", stdout.String())
	}
}

// TestMain_secretFetchFailure uses a reply with no "value", which the fetcher
// does not retry, so the run fails at once instead of after the retry bound.
func TestMain_secretFetchFailure(t *testing.T) {
	inProcessSecrets(t, http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = io.WriteString(w, `{}`)
	}))
	var stdout bytes.Buffer

	code := run(context.Background(), &stdout)

	if code != 1 {
		t.Errorf("run() = %d, want 1", code)
	}
	errs := withSeverity(records(t, stdout.Bytes()), "ERROR")
	if len(errs) != 1 {
		t.Fatalf("got %d ERROR records, want 1; stdout:\n%s", len(errs), stdout.String())
	}
	message, _ := errs[0]["message"].(string)
	for _, want := range []string{`"kubernetes"`, `"a"`} {
		if !strings.Contains(message, want) {
			t.Errorf("ERROR message %q does not name %s", message, want)
		}
	}
	if strings.Contains(message, "[REDACTED]") {
		t.Errorf("ERROR message %q was altered by the log scrubber", message)
	}
}
