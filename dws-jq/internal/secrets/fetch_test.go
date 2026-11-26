package secrets

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

// fastFetcher returns a Fetcher against srv with test-sized timings.
func fastFetcher(srv *httptest.Server) *Fetcher {
	return &Fetcher{
		BaseURL: srv.URL,
		Client:  srv.Client(),
		Bound:   2 * time.Second,
		Backoff: time.Millisecond,
	}
}

func TestFetch(t *testing.T) {
	t.Run("reads value from the secrets path", func(t *testing.T) {
		var gotPath string
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			gotPath = r.URL.EscapedPath()
			_, _ = w.Write([]byte(`{"value":"abcd","Value":5,"unrelated":{"ok":true}}`))
		}))
		defer srv.Close()

		got, err := fastFetcher(srv).Fetch(context.Background(), "kubernetes", []string{"api-token"})
		if err != nil {
			t.Fatalf("Fetch: %v", err)
		}
		if gotPath != "/v1.0/secrets/kubernetes/api-token" {
			t.Errorf("path = %q", gotPath)
		}
		if len(got) != 1 || got["api-token"] != "abcd" {
			t.Errorf("got %v", got)
		}
	})

	t.Run("dapr-api-token header only when Token is set", func(t *testing.T) {
		for _, tc := range []struct {
			name, token string
			wantPresent bool
		}{
			{"set", "tok-1", true},
			{"unset", "", false},
		} {
			t.Run(tc.name, func(t *testing.T) {
				var vals []string
				var present bool
				srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
					vals, present = r.Header["Dapr-Api-Token"]
					_, _ = w.Write([]byte(`{"value":"v"}`))
				}))
				defer srv.Close()
				f := fastFetcher(srv)
				f.Token = tc.token
				if _, err := f.Fetch(context.Background(), "s", []string{"n"}); err != nil {
					t.Fatalf("Fetch: %v", err)
				}
				if present != tc.wantPresent {
					t.Fatalf("header present = %v, want %v", present, tc.wantPresent)
				}
				if tc.wantPresent && (len(vals) != 1 || vals[0] != tc.token) {
					t.Errorf("header = %v", vals)
				}
			})
		}
	})

	t.Run("missing lowercase value key is a FetchError naming value", func(t *testing.T) {
		for _, key := range []string{"other", "VALUE", "Value"} {
			t.Run(key, func(t *testing.T) {
				const value = "sidecar-value-not-for-logs"
				var calls atomic.Int32
				srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
					calls.Add(1)
					_, _ = w.Write([]byte(`{"` + key + `":"` + value + `"}`))
				}))
				defer srv.Close()

				_, err := fastFetcher(srv).Fetch(context.Background(), "s", []string{"n"})
				var fe *FetchError
				if !errors.As(err, &fe) {
					t.Fatalf("err = %v, want *FetchError", err)
				}
				if !strings.Contains(fe.Reason, "value") {
					t.Errorf("Reason = %q", fe.Reason)
				}
				if strings.Contains(err.Error(), value) {
					t.Error("Fetch error contains the response value")
				}
				if calls.Load() != 1 {
					t.Errorf("a malformed 200 must not be retried, calls = %d", calls.Load())
				}
			})
		}
	})

	t.Run("non-string or null value is rejected", func(t *testing.T) {
		for _, body := range []string{`{"value":5}`, `{"value":null}`, `not json`, `[]`} {
			srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				_, _ = w.Write([]byte(body))
			}))
			_, err := fastFetcher(srv).Fetch(context.Background(), "s", []string{"n"})
			srv.Close()
			var fe *FetchError
			if !errors.As(err, &fe) {
				t.Errorf("body %q: err = %v, want *FetchError", body, err)
			}
		}
	})

	t.Run("persistent 404 fails at the bound without leaking the body", func(t *testing.T) {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			http.Error(w, "do-not-leak", http.StatusNotFound)
		}))
		defer srv.Close()
		f := fastFetcher(srv)
		f.Bound = 200 * time.Millisecond

		start := time.Now()
		_, err := f.Fetch(context.Background(), "kubernetes", []string{"api-token"})
		// Only what holds however slow the machine is: the bound is honoured
		// and the error names the secret without the body. Which attempt the
		// bound cuts short is covered deterministically below.
		if elapsed := time.Since(start); elapsed > 5*time.Second {
			t.Errorf("took %v, Bound was 200ms", elapsed)
		}
		var fe *FetchError
		if !errors.As(err, &fe) {
			t.Fatalf("err = %v, want *FetchError", err)
		}
		if fe.Store != "kubernetes" || fe.Name != "api-token" {
			t.Errorf("FetchError = %+v", fe)
		}
		msg := err.Error()
		for _, want := range []string{"kubernetes", "api-token"} {
			if !strings.Contains(msg, want) {
				t.Errorf("Error() = %q, missing %q", msg, want)
			}
		}
		if strings.Contains(msg, "do-not-leak") {
			t.Errorf("Error() leaks the response body: %q", msg)
		}
	})

	t.Run("expiry reports the last real failure, retried", func(t *testing.T) {
		// The caller's context is cancelled from inside the second request, so
		// at least one 404 has completed by then, whatever the timing.
		ctx, cancel := context.WithCancel(context.Background())
		defer cancel()
		var calls atomic.Int32
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if calls.Add(1) == 2 {
				cancel()
			}
			http.Error(w, "do-not-leak", http.StatusNotFound)
		}))
		defer srv.Close()
		f := fastFetcher(srv)
		f.Bound = time.Minute

		_, err := f.Fetch(ctx, "kubernetes", []string{"api-token"})
		if calls.Load() < 2 {
			t.Errorf("requests = %d, want the 404 to be retried", calls.Load())
		}
		var fe *FetchError
		if !errors.As(err, &fe) {
			t.Fatalf("err = %v, want *FetchError", err)
		}
		if !strings.Contains(fe.Reason, "status 404") {
			t.Errorf("Reason = %q, want the last real failure (status 404)", fe.Reason)
		}
		if !strings.Contains(fe.Reason, "canceled") {
			t.Errorf("Reason = %q, want it to say why it gave up", fe.Reason)
		}
		if strings.Contains(err.Error(), "do-not-leak") {
			t.Errorf("Error() leaks the response body: %q", err.Error())
		}
	})

	t.Run("expiry before any answer reports the deadline", func(t *testing.T) {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			<-r.Context().Done() // never answers
		}))
		defer srv.Close()
		f := fastFetcher(srv)
		f.Bound = 100 * time.Millisecond

		_, err := f.Fetch(context.Background(), "s", []string{"n"})
		var fe *FetchError
		if !errors.As(err, &fe) {
			t.Fatalf("err = %v, want *FetchError", err)
		}
		if !strings.Contains(fe.Reason, "deadline exceeded") {
			t.Errorf("Reason = %q, want the deadline", fe.Reason)
		}
	})

	t.Run("503 twice then 200 succeeds", func(t *testing.T) {
		var calls atomic.Int32
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if calls.Add(1) <= 2 {
				w.WriteHeader(http.StatusServiceUnavailable)
				return
			}
			_, _ = w.Write([]byte(`{"value":"ok"}`))
		}))
		defer srv.Close()

		got, err := fastFetcher(srv).Fetch(context.Background(), "s", []string{"n"})
		if err != nil {
			t.Fatalf("Fetch: %v", err)
		}
		if got["n"] != "ok" || calls.Load() != 3 {
			t.Errorf("got %v after %d calls", got, calls.Load())
		}
	})

	t.Run("two names make two requests", func(t *testing.T) {
		var mu sync.Mutex
		var paths []string
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			mu.Lock()
			paths = append(paths, r.URL.EscapedPath())
			mu.Unlock()
			_, _ = w.Write([]byte(`{"value":"v-` + r.URL.Path[strings.LastIndex(r.URL.Path, "/")+1:] + `"}`))
		}))
		defer srv.Close()

		got, err := fastFetcher(srv).Fetch(context.Background(), "s", []string{"a", "b", "a"})
		if err != nil {
			t.Fatalf("Fetch: %v", err)
		}
		if len(paths) != 2 {
			t.Errorf("requests = %v, want one per distinct name", paths)
		}
		if got["a"] != "v-a" || got["b"] != "v-b" || len(got) != 2 {
			t.Errorf("got %v", got)
		}
	})

	t.Run("names are escaped into a single path segment", func(t *testing.T) {
		for _, tc := range []struct{ name, wantPath string }{
			{"a/b", "/v1.0/secrets/kubernetes/a%2Fb"},
			{"../x", "/v1.0/secrets/kubernetes/..%2Fx"},
			{"a b?c#d", "/v1.0/secrets/kubernetes/a%20b%3Fc%23d"},
		} {
			var gotURI string
			srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				gotURI = r.RequestURI
				_, _ = w.Write([]byte(`{"value":"v"}`))
			}))
			_, err := fastFetcher(srv).Fetch(context.Background(), "kubernetes", []string{tc.name})
			srv.Close()
			if err != nil {
				t.Errorf("%q: %v", tc.name, err)
				continue
			}
			if gotURI != tc.wantPath {
				t.Errorf("%q: request URI = %q, want %q", tc.name, gotURI, tc.wantPath)
			}
		}
	})

	t.Run("store is escaped too", func(t *testing.T) {
		var gotURI string
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			gotURI = r.RequestURI
			_, _ = w.Write([]byte(`{"value":"v"}`))
		}))
		defer srv.Close()
		if _, err := fastFetcher(srv).Fetch(context.Background(), "a/b", []string{"n"}); err != nil {
			t.Fatal(err)
		}
		if want := "/v1.0/secrets/a%2Fb/n"; gotURI != want {
			t.Errorf("request URI = %q, want %q", gotURI, want)
		}
	})

	t.Run("bulk route is rejected without a request", func(t *testing.T) {
		var calls atomic.Int32
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			calls.Add(1)
			_, _ = w.Write([]byte(`{"value":"v"}`))
		}))
		defer srv.Close()

		_, err := fastFetcher(srv).Fetch(context.Background(), "s", []string{"ok", "bulk"})
		var fe *FetchError
		if !errors.As(err, &fe) {
			t.Errorf("err = %v, want *FetchError", err)
		} else if fe.Store != "s" || fe.Name != "bulk" || !strings.Contains(fe.Reason, "bulk") {
			t.Errorf("FetchError = %+v, want store and name with reason mentioning the bulk route", fe)
		}
		if calls.Load() != 0 {
			t.Errorf("requests = %d, want 0", calls.Load())
		}
	})

	t.Run("empty or dot-segment store or name is rejected without a request", func(t *testing.T) {
		var calls atomic.Int32
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			calls.Add(1)
		}))
		defer srv.Close()
		f := fastFetcher(srv)

		for _, tc := range []struct {
			store string
			names []string
		}{
			{"s", []string{""}},
			{"s", []string{"ok", ""}},
			{"s", []string{"."}},
			{"s", []string{"ok", ".."}},
			{".", []string{"n"}},
			{"..", []string{"n"}},
			{"", []string{"n"}},
		} {
			_, err := f.Fetch(context.Background(), tc.store, tc.names)
			var fe *FetchError
			if !errors.As(err, &fe) {
				t.Errorf("store %q names %q: err = %v, want *FetchError", tc.store, tc.names, err)
			}
		}
		if calls.Load() != 0 {
			t.Errorf("requests = %d, want 0", calls.Load())
		}
	})

	t.Run("no names means no requests", func(t *testing.T) {
		var calls atomic.Int32
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			calls.Add(1)
		}))
		defer srv.Close()
		got, err := fastFetcher(srv).Fetch(context.Background(), "s", nil)
		if err != nil || len(got) != 0 || calls.Load() != 0 {
			t.Errorf("got %v, err %v, calls %d", got, err, calls.Load())
		}
	})

	t.Run("cancellation interrupts the backoff wait", func(t *testing.T) {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(http.StatusServiceUnavailable)
		}))
		defer srv.Close()
		f := fastFetcher(srv)
		f.Bound = time.Minute
		f.Backoff = time.Minute

		ctx, cancel := context.WithCancel(context.Background())
		time.AfterFunc(50*time.Millisecond, cancel)
		start := time.Now()
		_, err := f.Fetch(ctx, "s", []string{"n"})
		if elapsed := time.Since(start); elapsed > 5*time.Second {
			t.Errorf("Fetch took %v after cancel", elapsed)
		}
		var fe *FetchError
		if !errors.As(err, &fe) {
			t.Fatalf("err = %v, want *FetchError", err)
		}
	})

	t.Run("unreachable sidecar fails at the bound", func(t *testing.T) {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {}))
		f := fastFetcher(srv)
		srv.Close() // nothing listens at BaseURL any more
		f.Bound = 100 * time.Millisecond

		_, err := f.Fetch(context.Background(), "s", []string{"n"})
		var fe *FetchError
		if !errors.As(err, &fe) {
			t.Fatalf("err = %v, want *FetchError", err)
		}
	})
}

// TestFetch_retriesClientTimeout covers an attempt cut short by the HTTP
// client's own Timeout: its error matches context.DeadlineExceeded, but the
// Bound has time left, so the read is retried rather than given up.
func TestFetch_retriesClientTimeout(t *testing.T) {
	const slowAttempts = 2
	var requests atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if requests.Add(1) <= slowAttempts {
			select {
			case <-time.After(time.Second):
			case <-r.Context().Done():
			}
			return
		}
		_, _ = w.Write([]byte(`{"value":"v"}`))
	}))
	defer srv.Close()
	f := fastFetcher(srv)
	f.Client = &http.Client{Timeout: 50 * time.Millisecond}
	f.Bound = 5 * time.Second

	got, err := f.Fetch(context.Background(), "s", []string{"n"})

	if err != nil {
		t.Fatalf("Fetch: %v, want the read retried after the client timeouts", err)
	}
	if got["n"] != "v" {
		t.Errorf("got %v, want n=v", got)
	}
	if n := requests.Load(); n != slowAttempts+1 {
		t.Errorf("requests = %d, want %d", n, slowAttempts+1)
	}
}

func TestFetchErrorFormat(t *testing.T) {
	err := &FetchError{Store: "kubernetes", Name: "a", Reason: "status 404"}
	if got, want := err.Error(), `secret "a" in store "kubernetes": status 404`; got != want {
		t.Errorf("Error() = %q, want %q", got, want)
	}
}
