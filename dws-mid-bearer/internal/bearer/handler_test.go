package bearer

import (
	"testing"

	"github.com/http-wasm/http-wasm-guest-tinygo/handler/api"
)

// fakeHeaders is an in-memory api.Header; it embeds the interface so only the
// methods the handler uses need implementing.
type fakeHeaders struct {
	api.Header
	values map[string][]string
	adds   int
}

func newHeaders(kv ...string) *fakeHeaders {
	h := &fakeHeaders{values: map[string][]string{}}
	for i := 0; i < len(kv); i += 2 {
		h.values[kv[i]] = append(h.values[kv[i]], kv[i+1])
	}
	return h
}

func (h *fakeHeaders) Set(name, value string) { h.values[name] = []string{value} }
func (h *fakeHeaders) Add(name, value string) {
	h.adds++
	h.values[name] = append(h.values[name], value)
}
func (h *fakeHeaders) GetAll(name string) []string { return h.values[name] }
func (h *fakeHeaders) Get(name string) (string, bool) {
	v := h.values[name]
	if len(v) == 0 {
		return "", false
	}
	return v[0], true
}

type fakeRequest struct {
	api.Request
	uri     string
	headers *fakeHeaders
}

func (r *fakeRequest) GetURI() string      { return r.uri }
func (r *fakeRequest) Headers() api.Header { return r.headers }

// cfg is built through Parse, the only constructor, like in production.
var cfg = mustParse(`{"endpoint":"pets-api","token":"abc123"}`)

func mustParse(raw string) Config {
	c, err := Parse([]byte(raw))
	if err != nil {
		panic(err)
	}
	return c
}

func handle(t *testing.T, uri string, h *fakeHeaders) {
	t.Helper()
	next, _ := cfg.Handle(&fakeRequest{uri: uri, headers: h}, nil)
	if !next {
		t.Fatalf("Handle(%q) must always continue to the next handler", uri)
	}
}

func TestHandleAddsBearerOnOwnEndpoint(t *testing.T) {
	for _, uri := range []string{
		"/v1.0/invoke/pets-api/method/v1/pets",
		"/v1.0/invoke/pets-api/method/v1/pets?limit=2",
		"/v1.0/invoke/pets-api/method/",
		"/v1.0/invoke/pets-api/method",
		"/v1.0/invoke/pets-api/method?x=1",
	} {
		t.Run(uri, func(t *testing.T) {
			h := newHeaders()
			handle(t, uri, h)
			got := h.GetAll("Authorization")
			if len(got) != 1 || got[0] != "Bearer abc123" {
				t.Fatalf("Authorization = %q, want [Bearer abc123]", got)
			}
		})
	}
}

func TestHandleReplacesCallerSuppliedAuthorization(t *testing.T) {
	h := newHeaders("Authorization", "Bearer caller-supplied", "X-Other", "keep")
	handle(t, "/v1.0/invoke/pets-api/method/x", h)
	got := h.GetAll("Authorization")
	if len(got) != 1 || got[0] != "Bearer abc123" {
		t.Fatalf("Authorization = %q, want exactly [Bearer abc123]", got)
	}
	if h.adds != 0 {
		t.Errorf("Add used %d times; a second Authorization header would be forwarded", h.adds)
	}
	if v, _ := h.Get("X-Other"); v != "keep" {
		t.Errorf("unrelated header changed: %q", v)
	}
}

func TestHandlePassesEverythingElseThrough(t *testing.T) {
	for _, uri := range []string{
		"/v1.0/invoke/other-api/method/v1/pets",
		"/v1.0/invoke/pets-api-2/method/x",
		"/v1.0/invoke/pets-apix/method/x",
		"/v1.0/invoke/xpets-api/method/x",
		"/v1.0/invoke/pets-api/methods/x",
		"/v1.0/invoke/pets-api/methodology",
		"/v1.0/invoke/pets-api",
		"/v1.0/invoke/pets-api/",
		"/v1.0/state/store",
		"/v1.0/publish/pubsub/topic",
		"/v1.0/invoke/other/method/v1.0/invoke/pets-api/method/x",
		"/v1.0/invoke/PETS-API/method/x",
		"v1.0/invoke/pets-api/method/x",
		"/",
		"",
	} {
		t.Run(uri, func(t *testing.T) {
			h := newHeaders("Authorization", "Basic eDp5", "X-Other", "keep")
			handle(t, uri, h)
			if got := h.GetAll("Authorization"); len(got) != 1 || got[0] != "Basic eDp5" {
				t.Errorf("Authorization = %q, want the original untouched", got)
			}
			if h.adds != 0 || len(h.values) != 2 {
				t.Errorf("headers were modified: %v", h.values)
			}
		})
	}
}

func TestHandleNoAuthorizationAddedWhenNoneSent(t *testing.T) {
	h := newHeaders()
	handle(t, "/v1.0/invoke/other-api/method/x", h)
	if len(h.values) != 0 {
		t.Fatalf("headers added on a pass-through request: %v", h.values)
	}
}

func TestMatchesDeepPath(t *testing.T) {
	if !cfg.Matches("/v1.0/invoke/pets-api/method/a/b/c") {
		t.Error("deep path should match")
	}
}

// A Config that skipped Parse must fail closed: it matches nothing and so never
// stamps a header (an empty prefix would otherwise match every URI).
func TestZeroConfigMatchesNothing(t *testing.T) {
	var zero Config
	for _, uri := range []string{
		"/v1.0/invoke/pets-api/method/x",
		"/v1.0/invoke//method/x",
		"/anything",
		"?x=1",
		"/",
		"",
	} {
		t.Run(uri, func(t *testing.T) {
			if zero.Matches(uri) {
				t.Fatalf("zero Config matched %q", uri)
			}
			h := newHeaders()
			next, _ := zero.Handle(&fakeRequest{uri: uri, headers: h}, nil)
			if !next {
				t.Error("Handle must always continue to the next handler")
			}
			if len(h.values) != 0 {
				t.Errorf("zero Config modified headers: %v", h.values)
			}
		})
	}
}
