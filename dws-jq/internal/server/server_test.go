package server

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/dws/dws-jq/internal/expr"
	"github.com/dws/dws-jq/internal/logging"
	"github.com/dws/dws-jq/internal/secrets"
)

const (
	testSite        = "s"
	testTimeout     = 5 * time.Second
	testTraceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
)

// countingEvaluator is a fake Evaluator that records how often it is called
// and answers with a fixed result or error.
type countingEvaluator struct {
	calls     atomic.Int32
	variables []string
	result    expr.Result
	err       error
}

func (c *countingEvaluator) Evaluate(context.Context, time.Duration, expr.Input) (expr.Result, error) {
	c.calls.Add(1)
	return c.result, c.err
}

func (c *countingEvaluator) Variables() []string { return c.variables }
func (c *countingEvaluator) Expression() string  { return "fake" }

// newServer builds a Server over ev with a JSON logger that does no redaction
// of its own, so any redaction seen in the log is the server's.
func newServer(ev Evaluator, timeout time.Duration) (*Server, *bytes.Buffer) {
	var buf bytes.Buffer
	log := logging.New(&buf, "json", nil)
	return New(ev, Options{Site: testSite, Timeout: timeout}, log), &buf
}

// compiled compiles expression or fails the test.
func compiled(t *testing.T, expression string, variables []string) *expr.Program {
	t.Helper()
	p, err := expr.Compile(expression, variables)
	if err != nil {
		t.Fatalf("Compile(%q) error = %v", expression, err)
	}
	return p
}

// readyServer returns a ready server over the compiled expression.
func readyServer(t *testing.T, expression string, variables []string, timeout time.Duration) (*Server, *bytes.Buffer) {
	t.Helper()
	srv, buf := newServer(compiled(t, expression, variables), timeout)
	srv.MarkReady(nil, nil)
	return srv, buf
}

func do(srv *Server, method, path, body string, header http.Header) *httptest.ResponseRecorder {
	req := httptest.NewRequest(method, path, strings.NewReader(body))
	for k, vs := range header {
		for _, v := range vs {
			req.Header.Add(k, v)
		}
	}
	rec := httptest.NewRecorder()
	srv.Handler().ServeHTTP(rec, req)
	return rec
}

func decodeBody(t *testing.T, rec *httptest.ResponseRecorder) map[string]any {
	t.Helper()
	var body map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("response body %q is not a JSON object: %v", rec.Body.String(), err)
	}
	return body
}

// logRecords parses every JSON line of buf.
func logRecords(t *testing.T, buf *bytes.Buffer) []map[string]any {
	t.Helper()
	var records []map[string]any
	for _, line := range strings.Split(strings.TrimSpace(buf.String()), "\n") {
		if line == "" {
			continue
		}
		var rec map[string]any
		if err := json.Unmarshal([]byte(line), &rec); err != nil {
			t.Fatalf("log line %q is not JSON: %v", line, err)
		}
		records = append(records, rec)
	}
	return records
}

func faultOf(t *testing.T, rec *httptest.ResponseRecorder) map[string]any {
	t.Helper()
	if rec.Code != http.StatusUnprocessableEntity {
		t.Fatalf("status = %d, want 422; body %s", rec.Code, rec.Body)
	}
	fault, ok := decodeBody(t, rec)["fault"].(map[string]any)
	if !ok {
		t.Fatalf("body %s has no fault object", rec.Body)
	}
	return fault
}

func TestHealthz(t *testing.T) {
	srv, _ := newServer(compiled(t, ".", nil), testTimeout)

	rec := do(srv, http.MethodGet, "/healthz", "", nil)
	if rec.Code != http.StatusServiceUnavailable {
		t.Errorf("before MarkReady: status = %d, want 503", rec.Code)
	}
	if got, want := decodeBody(t, rec), map[string]any{"status": "starting", "site": testSite}; !equalJSON(got, want) {
		t.Errorf("before MarkReady: body = %v, want %v", got, want)
	}

	srv.MarkReady(nil, nil)

	rec = do(srv, http.MethodGet, "/healthz", "", nil)
	if rec.Code != http.StatusOK {
		t.Errorf("after MarkReady: status = %d, want 200", rec.Code)
	}
	if got, want := decodeBody(t, rec), map[string]any{"status": "ok", "site": testSite}; !equalJSON(got, want) {
		t.Errorf("after MarkReady: body = %v, want %v", got, want)
	}
	if got := rec.Header().Get("Content-Type"); got != "application/json" {
		t.Errorf("Content-Type = %q, want application/json", got)
	}
}

func TestRun_notReady(t *testing.T) {
	ev := &countingEvaluator{}
	srv, _ := newServer(ev, testTimeout)

	rec := do(srv, http.MethodPost, "/run", `{"input":1}`, nil)
	if rec.Code != http.StatusServiceUnavailable {
		t.Errorf("status = %d, want 503", rec.Code)
	}
	if n := ev.calls.Load(); n != 0 {
		t.Errorf("evaluator called %d times, want 0", n)
	}
}

func TestRun_success(t *testing.T) {
	tests := []struct {
		name       string
		expression string
		variables  []string
		body       string
		want       string
	}{
		{name: "first output", expression: ".[]", body: `{"input":[0,1]}`, want: `{"result":0,"truthy":true}`},
		{name: "no output", expression: "empty", body: `{"input":1}`, want: `{"result":null,"truthy":false}`},
		{name: "absent input is null", expression: ". == null", body: `{}`, want: `{"result":true,"truthy":true}`},
		{name: "declared variable is bound", expression: "$item + 1", variables: []string{"item"},
			body: `{"variables":{"item":2}}`, want: `{"result":3,"truthy":true}`},
		{name: "declared variable left out is null", expression: "$item == null", variables: []string{"item"},
			body: `{}`, want: `{"result":true,"truthy":true}`},
		{name: "false is falsy", expression: "false", body: `{}`, want: `{"result":false,"truthy":false}`},
		{name: "large integers keep precision", expression: ". + 1", body: `{"input":9007199254740993}`,
			want: `{"result":9007199254740994,"truthy":true}`},
		{name: "nan is encoded as null", expression: "nan", body: `{}`, want: `{"result":null,"truthy":true}`},
		{name: "infinity is clamped", expression: "infinite", body: `{}`,
			want: `{"result":1.7976931348623157e+308,"truthy":true}`},
		{name: "object result", expression: `{ "id": ([ .id ] | .[0]), "source": "api" }`, body: `{"input":{"id":7}}`,
			want: `{"result":{"id":7,"source":"api"},"truthy":true}`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			srv, buf := readyServer(t, tt.expression, tt.variables, testTimeout)

			rec := do(srv, http.MethodPost, "/run", tt.body, nil)

			if rec.Code != http.StatusOK {
				t.Fatalf("status = %d, want 200; body %s", rec.Code, rec.Body)
			}
			if got := rec.Body.String(); got != tt.want {
				t.Errorf("body = %s, want %s", got, tt.want)
			}
			if buf.Len() != 0 {
				t.Errorf("a success logged %q, want nothing", buf)
			}
		})
	}
}

// stringInputBody returns the valid body {"input":"aaa…"} of exactly size bytes.
func stringInputBody(size int) string {
	const prefix, suffix = `{"input":"`, `"}`
	return prefix + strings.Repeat("a", size-len(prefix)-len(suffix)) + suffix
}

func TestRun_bodyLimit(t *testing.T) {
	t.Run("a valid body one byte over the limit is rejected", func(t *testing.T) {
		ev := &countingEvaluator{}
		srv, _ := newServer(ev, testTimeout)
		srv.MarkReady(nil, nil)

		rec := do(srv, http.MethodPost, "/run", stringInputBody(MaxBodyBytes+1), nil)

		if rec.Code != http.StatusBadRequest {
			t.Fatalf("status = %d, want 400", rec.Code)
		}
		if msg, _ := decodeBody(t, rec)["error"].(string); !strings.Contains(msg, fmt.Sprint(MaxBodyBytes)) {
			t.Errorf("error = %q, want it to name the %d-byte limit", msg, MaxBodyBytes)
		}
		if n := ev.calls.Load(); n != 0 {
			t.Errorf("evaluator called %d times, want 0", n)
		}
	})

	t.Run("a body of exactly the limit is evaluated", func(t *testing.T) {
		ev := &countingEvaluator{}
		srv, _ := newServer(ev, testTimeout)
		srv.MarkReady(nil, nil)

		rec := do(srv, http.MethodPost, "/run", stringInputBody(MaxBodyBytes), nil)

		if rec.Code != http.StatusOK {
			t.Fatalf("status = %d, want 200; body %s", rec.Code, rec.Body)
		}
		if n := ev.calls.Load(); n != 1 {
			t.Errorf("evaluator called %d times, want 1", n)
		}
	})
}

func TestRun_badRequest(t *testing.T) {
	tests := []struct {
		name string
		body string
	}{
		{name: "empty body", body: ``},
		{name: "null", body: `null`},
		{name: "array", body: `[]`},
		{name: "string", body: `"x"`},
		{name: "truncated object", body: `{`},
		{name: "trailing data", body: `{}{}`},
		{name: "variables is an array", body: `{"variables":[]}`},
		{name: "variables is null", body: `{"variables":null}`},
		{name: "unknown field", body: `{"input":1,"expression":"."}`},
		{name: "case variant of input", body: `{"Input":1}`},
		{name: "case variant of variables", body: `{"Variables":{"item":1}}`},
		{name: "duplicate case variant of input", body: `{"input":1,"INPUT":2}`},
		{name: "undeclared variable", body: `{"variables":{"other":1}}`},
		{name: "case variant of a declared variable", body: `{"variables":{"Item":1}}`},
		{name: "secrets cannot be injected", body: `{"variables":{"secrets":{}}}`},
		{name: "invalid body over the limit", body: strings.Repeat("{", MaxBodyBytes+1)},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ev := &countingEvaluator{variables: []string{"item"}}
			srv, buf := newServer(ev, testTimeout)
			srv.MarkReady(nil, nil)

			rec := do(srv, http.MethodPost, "/run", tt.body, nil)

			if rec.Code != http.StatusBadRequest {
				t.Fatalf("status = %d, want 400; body %s", rec.Code, rec.Body)
			}
			if msg, _ := decodeBody(t, rec)["error"].(string); msg == "" {
				t.Errorf("body %s has no error message", rec.Body)
			}
			if n := ev.calls.Load(); n != 0 {
				t.Errorf("evaluator called %d times, want 0", n)
			}
			records := logRecords(t, buf)
			if len(records) != 1 || records[0]["severity"] != "WARN" || records[0]["site"] != testSite {
				t.Fatalf("log = %v, want one WARN record with site %q", records, testSite)
			}
			if detail, _ := records[0]["detail"].(string); detail == "" {
				t.Errorf("WARN record has no detail: %v", records[0])
			}
		})
	}
}

func TestRun_fault(t *testing.T) {
	const expression = "${ .a + 1 }"
	srv, _ := readyServer(t, expression, nil, testTimeout)

	fault := faultOf(t, do(srv, http.MethodPost, "/run", `{"input":{"a":"x"}}`, nil))

	if fault["type"] != "expression" {
		t.Errorf("fault.type = %v, want expression", fault["type"])
	}
	if fault["expression"] != expression {
		t.Errorf("fault.expression = %v, want %q", fault["expression"], expression)
	}
	if detail, _ := fault["detail"].(string); detail == "" {
		t.Errorf("fault.detail is empty")
	}
}

func TestRun_timeout(t *testing.T) {
	srv, _ := readyServer(t, "last(range(1; infinite))", nil, 50*time.Millisecond)

	fault := faultOf(t, do(srv, http.MethodPost, "/run", `{}`, nil))

	if detail, _ := fault["detail"].(string); !strings.Contains(detail, "time limit") {
		t.Errorf("fault.detail = %q, want it to mention the time limit", detail)
	}
}

func TestRun_redactsDetail(t *testing.T) {
	const value = `s3"cret`
	const escaped = `s3\"cret`
	srv, buf := newServer(compiled(t, "error($secrets.a)", nil), testTimeout)
	srv.MarkReady(map[string]any{"a": value}, secrets.NewRedactor([]string{value}))

	rec := do(srv, http.MethodPost, "/run", `{}`, nil)
	fault := faultOf(t, rec)

	detail, _ := fault["detail"].(string)
	if !strings.Contains(detail, "***") {
		t.Errorf("fault.detail = %q, want ***", detail)
	}
	for _, form := range []string{value, escaped} {
		if strings.Contains(detail, form) {
			t.Errorf("fault.detail %q contains the secret form %q", detail, form)
		}
		if strings.Contains(rec.Body.String(), form) {
			t.Errorf("response body %s contains the secret form %q", rec.Body, form)
		}
		if strings.Contains(buf.String(), form) {
			t.Errorf("log %q contains the secret form %q", buf, form)
		}
	}
	if !strings.Contains(buf.String(), "***") {
		t.Errorf("log %q does not carry the redacted detail", buf)
	}
}

func TestRun_logsTraceOnFailure(t *testing.T) {
	srv, buf := readyServer(t, ".a + 1", nil, testTimeout)
	header := http.Header{"Traceparent": {testTraceparent}}

	faultOf(t, do(srv, http.MethodPost, "/run", `{"input":{"a":"x"}}`, header))

	records := logRecords(t, buf)
	if len(records) != 1 {
		t.Fatalf("got %d log records, want 1: %v", len(records), records)
	}
	want := map[string]any{
		"severity": "INFO",
		"trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
		"span_id":  "00f067aa0ba902b7",
		"site":     testSite,
	}
	for key, value := range want {
		if records[0][key] != value {
			t.Errorf("log %s = %v, want %v", key, records[0][key], value)
		}
	}
	if detail, _ := records[0]["detail"].(string); detail == "" {
		t.Errorf("log record has no detail: %v", records[0])
	}

	buf.Reset()
	rec := do(srv, http.MethodPost, "/run", `{"input":{"a":1}}`, header)
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	if buf.Len() != 0 {
		t.Errorf("a 200 logged %q, want nothing", buf)
	}
}

func TestRun_internalError(t *testing.T) {
	ev := &countingEvaluator{err: errors.New("boom")}
	srv, buf := newServer(ev, testTimeout)
	srv.MarkReady(nil, nil)

	rec := do(srv, http.MethodPost, "/run", `{}`, nil)

	if rec.Code != http.StatusInternalServerError {
		t.Errorf("status = %d, want 500", rec.Code)
	}
	records := logRecords(t, buf)
	if len(records) != 1 || records[0]["severity"] != "ERROR" {
		t.Errorf("log = %v, want one ERROR record", records)
	}
}

func TestRun_unencodableResult(t *testing.T) {
	ev := &countingEvaluator{result: expr.Result{Value: struct{}{}, Truthy: true}}
	srv, _ := newServer(ev, testTimeout)
	srv.MarkReady(nil, nil)

	fault := faultOf(t, do(srv, http.MethodPost, "/run", `{}`, nil))

	if detail, _ := fault["detail"].(string); detail == "" {
		t.Errorf("fault.detail is empty")
	}
}

func TestRun_concurrent(t *testing.T) {
	srv, _ := readyServer(t, ".n", nil, testTimeout)
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	const workers = 50
	var wg sync.WaitGroup
	for i := range workers {
		wg.Add(1)
		go func() {
			defer wg.Done()
			resp, err := http.Post(ts.URL+"/run", "application/json",
				strings.NewReader(fmt.Sprintf(`{"input":{"n":%d}}`, i)))
			if err != nil {
				t.Errorf("request %d: %v", i, err)
				return
			}
			defer resp.Body.Close()
			body, err := io.ReadAll(resp.Body)
			if err != nil {
				t.Errorf("request %d: read body: %v", i, err)
				return
			}
			if want := fmt.Sprintf(`{"result":%d,"truthy":true}`, i); resp.StatusCode != http.StatusOK || string(body) != want {
				t.Errorf("request %d: got %d %s, want 200 %s", i, resp.StatusCode, body, want)
			}
		}()
	}
	wg.Wait()
}

func equalJSON(a, b map[string]any) bool {
	x, _ := json.Marshal(a)
	y, _ := json.Marshal(b)
	return bytes.Equal(x, y)
}
