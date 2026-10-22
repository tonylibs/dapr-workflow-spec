// Package wasmtest runs the real bin/basic.wasm, built by TinyGo 0.34.0, inside the same
// http-wasm host library daprd embeds (http-wasm-host-go on wazero), in front of an httptest
// echo server. It checks the one thing plain `go test` of the guest module cannot: that the
// TinyGo/SDK pairing builds a module that loads, validates its config and mutates requests.
//
// Build the module first with `make wasm`; `make test-wasm` runs these tests.
package wasmtest

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"reflect"
	"strings"
	"sync"
	"testing"

	"github.com/tetratelabs/wazero"

	"github.com/http-wasm/http-wasm-host-go/api"
	"github.com/http-wasm/http-wasm-host-go/handler"
	wasm "github.com/http-wasm/http-wasm-host-go/handler/nethttp"
)

// maxSize is the size budget for basic.wasm (design Decision 5).
const maxSize = 1 << 20

var (
	guestOnce sync.Once
	guestWasm []byte
	guestErr  error

	// One compilation cache for the whole run: compiling the module is by far the slowest step.
	cache = wazero.NewCompilationCache()
)

func loadGuest(t *testing.T) []byte {
	t.Helper()
	guestOnce.Do(func() {
		path := os.Getenv("BASIC_WASM")
		if path == "" {
			path = "../bin/basic.wasm"
		}
		guestWasm, guestErr = os.ReadFile(path)
		if guestErr != nil {
			guestErr = &missingError{path: path, err: guestErr}
		}
	})
	if guestErr != nil {
		t.Fatalf("%v", guestErr)
	}
	return guestWasm
}

type missingError struct {
	path string
	err  error
}

func (e *missingError) Error() string {
	return "cannot read the guest module " + e.path + ": " + e.err.Error() +
		" (run `make wasm` in dws-mid-basic/ first, or set BASIC_WASM)"
}

// seen is one request as the echo server received it.
type seen struct {
	Method string
	URI    string
	Header http.Header
	Body   string
}

// recorder is an echo server that records every request it receives.
type recorder struct {
	mu   sync.Mutex
	reqs []seen
}

func (r *recorder) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	body, _ := io.ReadAll(req.Body)
	r.mu.Lock()
	r.reqs = append(r.reqs, seen{req.Method, req.RequestURI, req.Header.Clone(), string(body)})
	r.mu.Unlock()
	w.WriteHeader(http.StatusOK)
	_, _ = io.WriteString(w, "ok")
}

func (r *recorder) all() []seen {
	r.mu.Lock()
	defer r.mu.Unlock()
	return append([]seen(nil), r.reqs...)
}

// syncBuffer collects the guest's stdout and stderr.
type syncBuffer struct {
	mu  sync.Mutex
	buf bytes.Buffer
}

func (b *syncBuffer) Write(p []byte) (int, error) {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.buf.Write(p)
}

func (b *syncBuffer) String() string {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.buf.String()
}

// hostLog records every message the guest sends to the host logger, at any level.
type hostLog struct {
	mu   sync.Mutex
	msgs []string
}

func (l *hostLog) IsEnabled(api.LogLevel) bool { return true }

func (l *hostLog) Log(_ context.Context, _ api.LogLevel, msg string) {
	l.mu.Lock()
	l.msgs = append(l.msgs, msg)
	l.mu.Unlock()
}

func (l *hostLog) all() []string {
	l.mu.Lock()
	defer l.mu.Unlock()
	return append([]string(nil), l.msgs...)
}

// rig is the middleware under test in front of an echo server.
type rig struct {
	echo   *recorder // behind the middleware
	direct *recorder // reference: same requests sent straight to an echo server
	front  *httptest.Server
	ref    *httptest.Server
	out    *syncBuffer // guest stdout + stderr
	logs   *hostLog
}

// load instantiates the guest with cfg as its guest config, as daprd does. It returns the
// middleware's error when the guest refuses to start.
func load(t *testing.T, cfg string) (*rig, error) {
	t.Helper()
	guest := loadGuest(t)

	r := &rig{echo: &recorder{}, direct: &recorder{}, out: &syncBuffer{}, logs: &hostLog{}}
	ctx := context.Background()
	mw, err := wasm.NewMiddleware(ctx, guest,
		handler.GuestConfig([]byte(cfg)),
		handler.Logger(r.logs),
		handler.ModuleConfig(wazero.NewModuleConfig().WithStdout(r.out).WithStderr(r.out)),
		handler.Runtime(func(ctx context.Context) (wazero.Runtime, error) {
			return wazero.NewRuntimeWithConfig(ctx, wazero.NewRuntimeConfig().WithCompilationCache(cache)), nil
		}),
	)
	if err != nil {
		return r, err
	}
	t.Cleanup(func() { _ = mw.Close(ctx) })

	r.front = httptest.NewServer(mw.NewHandler(ctx, r.echo))
	t.Cleanup(r.front.Close)
	r.ref = httptest.NewServer(r.direct)
	t.Cleanup(r.ref.Close)
	return r, nil
}

func mustLoad(t *testing.T, cfg string) *rig {
	t.Helper()
	r, err := load(t, cfg)
	if err != nil {
		t.Fatalf("guest refused to start with a valid config: %v\nguest output: %s", err, r.out.String())
	}
	return r
}

// request describes one client request.
type request struct {
	method string
	target string // path and query, sent verbatim
	header http.Header
	body   string
}

// send sends req to base and returns the status code.
func send(t *testing.T, base string, req request) int {
	t.Helper()
	method := req.method
	if method == "" {
		method = http.MethodGet
	}
	hr, err := http.NewRequest(method, base+req.target, strings.NewReader(req.body))
	if err != nil {
		t.Fatalf("building request %q: %v", req.target, err)
	}
	for k, vs := range req.header {
		for _, v := range vs {
			hr.Header.Add(k, v)
		}
	}
	resp, err := http.DefaultClient.Do(hr)
	if err != nil {
		t.Fatalf("sending %q: %v", req.target, err)
	}
	defer resp.Body.Close()
	_, _ = io.Copy(io.Discard, resp.Body)
	return resp.StatusCode
}

// through sends req via the middleware and via the reference echo server, and returns what each
// echo server saw.
func (r *rig) through(t *testing.T, req request) (got, ref seen) {
	t.Helper()
	nGot, nRef := len(r.echo.all()), len(r.direct.all())
	if status := send(t, r.front.URL, req); status != http.StatusOK {
		t.Fatalf("request %q through the middleware: status %d", req.target, status)
	}
	send(t, r.ref.URL, req)
	g, d := r.echo.all(), r.direct.all()
	if len(g) != nGot+1 || len(d) != nRef+1 {
		t.Fatalf("request %q: echo servers saw %d and %d new requests, want 1 and 1", req.target, len(g)-nGot, len(d)-nRef)
	}
	return g[len(g)-1], d[len(d)-1]
}

func cfgJSON(t *testing.T, fields map[string]any) string {
	t.Helper()
	b, err := json.Marshal(fields)
	if err != nil {
		t.Fatal(err)
	}
	return string(b)
}

func basicCfg(t *testing.T, endpoint, user, pass string) string {
	return cfgJSON(t, map[string]any{"endpoint": endpoint, "username": user, "password": pass})
}

func TestBinarySizeIsUnderBudget(t *testing.T) {
	guest := loadGuest(t)
	t.Logf("basic.wasm is %d bytes", len(guest))
	if len(guest) >= maxSize {
		t.Errorf("basic.wasm is %d bytes, want under %d (design Decision 5: drop NFC if needed)", len(guest), maxSize)
	}
}

func TestMatchingRequestCarriesBasicHeader(t *testing.T) {
	r := mustLoad(t, `{"endpoint":"pets","username":"alice","password":"s3cret"}`)
	got, _ := r.through(t, request{target: "/v1.0/invoke/pets/method/v1/pets/42?q=a"})
	if want := []string{"Basic YWxpY2U6czNjcmV0"}; !reflect.DeepEqual(got.Header.Values("Authorization"), want) {
		t.Errorf("Authorization = %q, want %q", got.Header.Values("Authorization"), want)
	}
}

func TestMatchingPaths(t *testing.T) {
	r := mustLoad(t, `{"endpoint":"pets","username":"alice","password":"s3cret"}`)
	for _, target := range []string{
		"/v1.0/invoke/pets/method",
		"/v1.0/invoke/pets/method/",
		"/v1.0/invoke/pets/method?q=a",
		"/v1.0/invoke/pets/method/search?q=../..",
		"/v1.0/invoke/pets/method/a..b/.hidden",
	} {
		got, _ := r.through(t, request{target: target})
		if want := []string{"Basic YWxpY2U6czNjcmV0"}; !reflect.DeepEqual(got.Header.Values("Authorization"), want) {
			t.Errorf("%s: Authorization = %q, want %q", target, got.Header.Values("Authorization"), want)
		}
	}
}

// The caller's Authorization value must not survive, and must not be forwarded next to ours.
func TestCallerHeaderIsReplaced(t *testing.T) {
	r := mustLoad(t, `{"endpoint":"pets","username":"alice","password":"s3cret"}`)
	for name, header := range map[string]http.Header{
		"one value":  {"Authorization": {"Bearer caller-supplied"}},
		"two values": {"Authorization": {"Bearer caller-supplied", "Basic Y2FsbGVyOnN1cHBsaWVk"}},
	} {
		got, _ := r.through(t, request{target: "/v1.0/invoke/pets/method/x", header: header})
		if want := []string{"Basic YWxpY2U6czNjcmV0"}; !reflect.DeepEqual(got.Header.Values("Authorization"), want) {
			t.Errorf("%s: Authorization = %q, want exactly %q", name, got.Header.Values("Authorization"), want)
		}
	}
}

// Only the Authorization header may differ between a matching request through the middleware and
// the same request sent straight to the echo server.
func TestMatchingRequestChangesNothingElse(t *testing.T) {
	r := mustLoad(t, `{"endpoint":"pets","username":"alice","password":"s3cret"}`)
	got, ref := r.through(t, request{
		method: http.MethodPost,
		target: "/v1.0/invoke/pets/method/v1/pets?x=1&y=%2Fz",
		header: http.Header{
			"Content-Type":  {"application/json"},
			"X-Request-Id":  {"abc-123"},
			"X-Multi":       {"one", "two"},
			"Authorization": {"Bearer caller-supplied"},
		},
		body: `{"name":"rex","note":"héllo"}`,
	})
	if got.Method != ref.Method || got.URI != ref.URI || got.Body != ref.Body {
		t.Errorf("method/URI/body changed: got %q %q %q, want %q %q %q", got.Method, got.URI, got.Body, ref.Method, ref.URI, ref.Body)
	}
	gotHeader, refHeader := got.Header.Clone(), ref.Header.Clone()
	gotHeader.Del("Authorization")
	refHeader.Del("Authorization")
	if !reflect.DeepEqual(gotHeader, refHeader) {
		t.Errorf("other headers changed: got %v, want %v", gotHeader, refHeader)
	}
}

// A request that does not match must reach the echo server exactly as it would have without the
// middleware: same method, URI, body and headers, including any Authorization the caller set.
func TestNonMatchingRequestsPassThroughUnchanged(t *testing.T) {
	r := mustLoad(t, `{"endpoint":"pets","username":"alice","password":"s3cret"}`)
	targets := []string{
		"/v1.0/invoke/orders/method/x",
		"/v1.0/invoke/pets-v2/method/x",
		"/v1.0/invoke/mypets/method/x",
		"/v1.0/invoke/pets/methods",
		"/v1.0/invoke/pets",
		"/v1.0/state/store",
		"/healthz",
		"/",
		"/v1.0/invoke/pets/method/../../orders/method/x",
		"/v1.0/invoke/pets/method/%2e%2e/%2e%2e/orders/method/x",
		"/v1.0/invoke/pets/method/%2E%2E/orders/method/x",
		"/v1.0/invoke/pets/method/./x",
		"/v1.0/invoke/orders/method/x?next=/v1.0/invoke/pets/method/y",
	}
	headers := map[string]http.Header{
		"no caller header":     {"X-Request-Id": {"abc-123"}},
		"caller Authorization": {"Authorization": {"Bearer other"}, "X-Request-Id": {"abc-123"}},
	}
	for _, target := range targets {
		for name, header := range headers {
			got, ref := r.through(t, request{method: http.MethodPost, target: target, header: header, body: "payload \u00e9"})
			if !reflect.DeepEqual(got, ref) {
				t.Errorf("%s (%s): request changed\n got  %+v\n want %+v", target, name, got, ref)
			}
		}
	}
	// And the unauthenticated case stays unauthenticated.
	got, _ := r.through(t, request{target: "/v1.0/invoke/orders/method/x"})
	if v := got.Header.Values("Authorization"); len(v) != 0 {
		t.Errorf("unrelated request gained Authorization %q", v)
	}
}

func TestCredentialEncodings(t *testing.T) {
	tests := []struct {
		name, user, pass, want string
	}{
		{"simple", "alice", "s3cret", "Basic YWxpY2U6czNjcmV0"},
		{"RFC 7617 example", "test", "123£", "Basic dGVzdDoxMjPCow=="},
		{"colons in the password", "alice", "pa:ss:word", "Basic YWxpY2U6cGE6c3M6d29yZA=="},
		{"non-ASCII user and password", "zoë", "пароль", "Basic em/DqzrQv9Cw0YDQvtC70Yw="},
		{"decomposed e is normalized", "e\u0301", "x", "Basic w6k6eA=="},
		{"JSON-special characters", "al\"ice", "back\\slash\u2028", ""},
	}
	// Expected values were computed independently of the guest (python base64). The last entry has
	// no literal: it only checks that JSON escaping of the config survives, using the test's own
	// base64.
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			r := mustLoad(t, basicCfg(t, "pets", tt.user, tt.pass))
			got, _ := r.through(t, request{target: "/v1.0/invoke/pets/method/x"})
			values := got.Header.Values("Authorization")
			if len(values) != 1 {
				t.Fatalf("Authorization = %q, want exactly one value", values)
			}
			want := tt.want
			if want == "" {
				want = "Basic " + b64(tt.user+":"+tt.pass)
			}
			if values[0] != want {
				t.Errorf("Authorization = %q, want %q", values[0], want)
			}
		})
	}
}

// Every invalid config must stop the guest at load, before any request is served: nothing may
// reach the echo server, and the panic text must name the problem but no value.
func TestInvalidConfigRefusesToStart(t *testing.T) {
	tests := []struct {
		name    string
		cfg     string
		want    []string // substrings the guest must report
		secrets []string // substrings that must appear nowhere in the error or guest output
	}{
		{"missing password", `{"endpoint":"pets","username":"alice"}`,
			[]string{"password is required"}, []string{"alice"}},
		{"empty password", `{"endpoint":"pets","username":"alice","password":""}`,
			[]string{"password is required"}, []string{"alice"}},
		{"null password", `{"endpoint":"pets","username":"alice","password":null}`,
			[]string{"password is required"}, []string{"alice"}},
		{"missing username", `{"endpoint":"pets","password":"s3cret"}`,
			[]string{"username is required"}, []string{"s3cret"}},
		{"empty username", `{"endpoint":"pets","username":"","password":"s3cret"}`,
			[]string{"username is required"}, []string{"s3cret"}},
		{"colon in username", `{"endpoint":"pets","username":"ali:ce","password":"s3cret"}`,
			[]string{"username must not contain ':'"}, []string{"ali:ce", "s3cret"}},
		{"newline in password", `{"endpoint":"pets","username":"alice","password":"bad\nvalue"}`,
			[]string{"password must not contain control characters"}, []string{"alice", "bad"}},
		{"scheme field", `{"scheme":"basic","endpoint":"pets","username":"alice","password":"s3cret"}`,
			[]string{"scheme is not a known field"}, []string{"alice", "s3cret"}},
		{"bearer-shaped config", `{"endpoint":"pets","token":"abc"}`,
			[]string{"token is not a known field", "username is required", "password is required"}, []string{"abc"}},
		{"field names are case sensitive", `{"endpoint":"pets","username":"alice","Password":"s3cret"}`,
			[]string{"Password is not a known field", "password is required"}, []string{"alice", "s3cret"}},
		{"duplicate field", `{"endpoint":"pets","username":"alice","password":"s3cret","password":"hunter2"}`,
			[]string{"password is specified more than once"}, []string{"alice", "s3cret", "hunter2"}},
		{"non-string password", `{"endpoint":"pets","username":"alice","password":12345}`,
			[]string{"password must be a string"}, []string{"alice", "12345"}},
		{"endpoint with a slash", `{"endpoint":"pets/v1","username":"alice","password":"s3cret"}`,
			[]string{"endpoint must not contain"}, []string{"alice", "s3cret", "pets/v1"}},
		{"malformed JSON", `{"endpoint":"pets","username":"alice","password":"s3cret"`,
			[]string{"config is not valid JSON"}, []string{"alice", "s3cret"}},
		{"trailing data", `{"endpoint":"pets","username":"alice","password":"s3cret"} x`,
			[]string{"config is not valid JSON"}, []string{"alice", "s3cret"}},
		{"not an object", `["pets","alice","s3cret"]`,
			[]string{"config must be a JSON object"}, []string{"alice", "s3cret"}},
		{"empty config", ``,
			[]string{"config is empty"}, nil},
		{"all problems at once", `{"scheme":"basic","endpoint":"a/b","username":"x:y","password":""}`,
			[]string{"scheme is not a known field", "endpoint must not contain", "username must not contain ':'", "password is required"}, []string{"x:y"}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			r, err := load(t, tt.cfg)
			if err == nil {
				// Not expected: the guest must refuse to start. Still prove the fail-closed claim.
				send(t, r.front.URL, request{target: "/v1.0/invoke/pets/method/x"})
				if n := len(r.echo.all()); n != 0 {
					t.Errorf("echo server received %d requests through a middleware that should not have started", n)
				}
				t.Fatalf("guest started with an invalid config")
			}
			if n := len(r.echo.all()); n != 0 {
				t.Errorf("echo server received %d requests, want 0", n)
			}
			combined := err.Error() + "\n" + r.out.String()
			for _, want := range tt.want {
				if !strings.Contains(combined, want) {
					t.Errorf("guest output does not report %q:\nerror: %v\nguest output: %s", want, err, r.out.String())
				}
			}
			for _, secret := range tt.secrets {
				if strings.Contains(combined, secret) {
					t.Errorf("guest output contains the value %q:\nerror: %v\nguest output: %s", secret, err, r.out.String())
				}
			}
			if msgs := r.logs.all(); len(msgs) != 0 {
				t.Errorf("guest wrote to the host log: %q", msgs)
			}
		})
	}
}

// The guest must emit nothing while serving: no host log line, no stdout or stderr, whatever
// the request, and nothing that contains the credential.
func TestGuestLogsNothing(t *testing.T) {
	r := mustLoad(t, `{"endpoint":"pets","username":"alice","password":"s3cret"}`)
	for _, target := range []string{
		"/v1.0/invoke/pets/method/x",
		"/v1.0/invoke/orders/method/x",
		"/healthz",
	} {
		r.through(t, request{target: target, header: http.Header{"Authorization": {"Bearer caller-supplied"}}})
	}
	if msgs := r.logs.all(); len(msgs) != 0 {
		t.Errorf("guest wrote to the host log: %q", msgs)
	}
	if out := r.out.String(); out != "" {
		t.Errorf("guest wrote to stdout/stderr: %q", out)
	}
}

// Many requests reuse pooled guest instances; the credential must be applied every time.
func TestRepeatedRequests(t *testing.T) {
	r := mustLoad(t, `{"endpoint":"pets","username":"alice","password":"s3cret"}`)
	for i := 0; i < 20; i++ {
		got, _ := r.through(t, request{target: "/v1.0/invoke/pets/method/x"})
		if v := got.Header.Values("Authorization"); len(v) != 1 || v[0] != "Basic YWxpY2U6czNjcmV0" {
			t.Fatalf("request %d: Authorization = %q", i, v)
		}
		other, _ := r.through(t, request{target: "/v1.0/invoke/orders/method/x"})
		if v := other.Header.Values("Authorization"); len(v) != 0 {
			t.Fatalf("request %d: unrelated request gained Authorization %q", i, v)
		}
	}
}

func b64(s string) string { return base64.StdEncoding.EncodeToString([]byte(s)) }
