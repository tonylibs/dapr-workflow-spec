package runner

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/dws/dws-call-http/internal/config"
)

type capturedRequest struct {
	method string
	path   string
	query  string
	header http.Header
	body   string
}

// newUpstream returns a test server that records the request it receives and
// replies with the given status and body.
func newUpstream(t *testing.T, status int, respBody string, captured *capturedRequest) *httptest.Server {
	t.Helper()
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		b, _ := io.ReadAll(r.Body)
		if captured != nil {
			captured.method = r.Method
			captured.path = r.URL.Path
			captured.query = r.URL.RawQuery
			captured.header = r.Header.Clone()
			captured.body = string(b)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(status)
		_, _ = io.WriteString(w, respBody)
	}))
}

func TestRunOutputModes(t *testing.T) {
	tests := []struct {
		name       string
		output     config.OutputMode
		input      map[string]any
		respBody   string
		wantResult any
	}{
		{
			name:       "replace returns upstream object",
			output:     config.OutputReplace,
			input:      map[string]any{"orderId": "o1"},
			respBody:   `{"available": true, "qty": 5}`,
			wantResult: map[string]any{"available": true, "qty": float64(5)},
		},
		{
			name:       "merge shallow-merges upstream into input",
			output:     config.OutputMerge,
			input:      map[string]any{"orderId": "o1", "qty": float64(1)},
			respBody:   `{"available": true, "qty": 5}`,
			wantResult: map[string]any{"orderId": "o1", "available": true, "qty": float64(5)},
		},
		{
			name:       "replace with empty upstream body returns empty object",
			output:     config.OutputReplace,
			input:      map[string]any{"orderId": "o1"},
			respBody:   ``,
			wantResult: map[string]any{},
		},
		{
			name:       "merge with empty upstream body returns input unchanged",
			output:     config.OutputMerge,
			input:      map[string]any{"orderId": "o1"},
			respBody:   ``,
			wantResult: map[string]any{"orderId": "o1"},
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			upstream := newUpstream(t, http.StatusOK, tc.respBody, nil)
			defer upstream.Close()

			r := New(config.Config{
				Task:     "check-inventory",
				Endpoint: upstream.URL,
				Method:   "POST",
				BodyMode: config.BodyPassthrough,
				Output:   tc.output,
			})

			got, err := r.Run(context.Background(), tc.input)
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			assertJSONEqual(t, got, tc.wantResult)
		})
	}
}

func TestRunBodyModes(t *testing.T) {
	tests := []struct {
		name         string
		bodyMode     config.BodyMode
		bodyTemplate string
		input        map[string]any
		wantBody     string
		wantCT       string
	}{
		{
			name:     "passthrough forwards input as JSON",
			bodyMode: config.BodyPassthrough,
			input:    map[string]any{"orderId": "o1"},
			wantBody: `{"orderId":"o1"}`,
			wantCT:   "application/json",
		},
		{
			name:     "none sends no body",
			bodyMode: config.BodyNone,
			input:    map[string]any{"orderId": "o1"},
			wantBody: "",
			wantCT:   "",
		},
		{
			name:         "template renders placeholders",
			bodyMode:     config.BodyTemplate,
			bodyTemplate: `{"id":"{orderId}","n":{qty}}`,
			input:        map[string]any{"orderId": "o1", "qty": float64(3)},
			wantBody:     `{"id":"o1","n":3}`,
			wantCT:       "application/json",
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			var captured capturedRequest
			upstream := newUpstream(t, http.StatusOK, `{}`, &captured)
			defer upstream.Close()

			r := New(config.Config{
				Task:         "check-inventory",
				Endpoint:     upstream.URL,
				Method:       "POST",
				BodyMode:     tc.bodyMode,
				BodyTemplate: tc.bodyTemplate,
				Output:       config.OutputReplace,
			})

			if _, err := r.Run(context.Background(), tc.input); err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if captured.body != tc.wantBody {
				t.Fatalf("body: got %q, want %q", captured.body, tc.wantBody)
			}
			if got := captured.header.Get("Content-Type"); got != tc.wantCT {
				t.Fatalf("content-type: got %q, want %q", got, tc.wantCT)
			}
		})
	}
}

func TestRunRequestShape(t *testing.T) {
	var captured capturedRequest
	upstream := newUpstream(t, http.StatusOK, `{}`, &captured)
	defer upstream.Close()

	r := New(config.Config{
		Task:     "check-inventory",
		Endpoint: upstream.URL + "/orders/{orderId}",
		Method:   "GET",
		Headers:  map[string]string{"X-Api-Key": "secret"},
		Query:    map[string]string{"region": "{region}"},
		BodyMode: config.BodyNone,
		Output:   config.OutputReplace,
	})

	_, err := r.Run(context.Background(), map[string]any{"orderId": "o1", "region": "eu"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if captured.method != "GET" {
		t.Fatalf("method: got %q, want GET", captured.method)
	}
	if captured.path != "/orders/o1" {
		t.Fatalf("path: got %q, want /orders/o1", captured.path)
	}
	if captured.query != "region=eu" {
		t.Fatalf("query: got %q, want region=eu", captured.query)
	}
	if got := captured.header.Get("X-Api-Key"); got != "secret" {
		t.Fatalf("header: got %q, want secret", got)
	}
}

func TestRunErrorMapping(t *testing.T) {
	t.Run("non-2xx yields UpstreamError with status and body", func(t *testing.T) {
		upstream := newUpstream(t, http.StatusInternalServerError, `{"msg":"boom"}`, nil)
		defer upstream.Close()

		r := New(config.Config{
			Task:     "check-inventory",
			Endpoint: upstream.URL,
			Method:   "POST",
			BodyMode: config.BodyPassthrough,
			Output:   config.OutputReplace,
		})

		_, err := r.Run(context.Background(), map[string]any{})
		var ue *UpstreamError
		if !errors.As(err, &ue) {
			t.Fatalf("expected *UpstreamError, got %v", err)
		}
		if ue.Status != http.StatusInternalServerError {
			t.Fatalf("status: got %d, want 500", ue.Status)
		}
		if ue.Body != `{"msg":"boom"}` {
			t.Fatalf("body: got %q", ue.Body)
		}
		if ue.Task != "check-inventory" {
			t.Fatalf("task: got %q", ue.Task)
		}
	})

	t.Run("unreachable upstream yields TransportError", func(t *testing.T) {
		upstream := newUpstream(t, http.StatusOK, `{}`, nil)
		url := upstream.URL
		upstream.Close() // close so the connection is refused

		r := New(config.Config{
			Task:     "check-inventory",
			Endpoint: url,
			Method:   "POST",
			BodyMode: config.BodyNone,
			Output:   config.OutputReplace,
		})

		_, err := r.Run(context.Background(), map[string]any{})
		var te *TransportError
		if !errors.As(err, &te) {
			t.Fatalf("expected *TransportError, got %v", err)
		}
	})

	t.Run("missing placeholder errors before any call", func(t *testing.T) {
		r := New(config.Config{
			Task:     "check-inventory",
			Endpoint: "https://svc/orders/{orderId}",
			Method:   "POST",
			BodyMode: config.BodyNone,
			Output:   config.OutputReplace,
		})

		_, err := r.Run(context.Background(), map[string]any{})
		if err == nil {
			t.Fatal("expected error for missing placeholder")
		}
		var ue *UpstreamError
		var te *TransportError
		if errors.As(err, &ue) || errors.As(err, &te) {
			t.Fatalf("expected plain config error, got typed error: %v", err)
		}
	})
}

func assertJSONEqual(t *testing.T, got, want any) {
	t.Helper()
	gb, _ := json.Marshal(got)
	wb, _ := json.Marshal(want)
	var gm, wm any
	_ = json.Unmarshal(gb, &gm)
	_ = json.Unmarshal(wb, &wm)
	if string(gb) == string(wb) {
		return
	}
	// Fall back to deep compare via re-marshaled canonical form.
	gc, _ := json.Marshal(gm)
	wc, _ := json.Marshal(wm)
	if string(gc) != string(wc) {
		t.Fatalf("result mismatch:\n got:  %s\n want: %s", gb, wb)
	}
}
