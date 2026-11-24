// Package secrets reads the secrets an expression references from the Dapr
// sidecar's HTTP secrets API and redacts their values from faults and logs.
package secrets

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"time"
)

const (
	defaultBound   = 60 * time.Second
	defaultBackoff = 250 * time.Millisecond
	maxBackoff     = 5 * time.Second
	maxBodyBytes   = 1 << 20
	tokenHeader    = "dapr-api-token"
)

var errNoValue = errors.New(`response has no string "value" key`)

// Fetcher reads secrets from a Dapr sidecar.
type Fetcher struct {
	// BaseURL is the sidecar address, "http://127.0.0.1:" + DaprHTTPPort.
	BaseURL string
	// Token is DAPR_API_TOKEN; sent as the dapr-api-token header when non-empty.
	Token string
	// Client performs the requests; http.DefaultClient when nil.
	Client *http.Client
	// Bound limits all reads together, retries included. 60s when zero.
	Bound time.Duration
	// Backoff is the first wait between attempts; it doubles up to 5s.
	// 250ms when zero.
	Backoff time.Duration
}

// FetchError reports a secret that could not be read. It names the store and
// the secret, never a value or a response body.
type FetchError struct {
	Store, Name, Reason string
}

func (e *FetchError) Error() string {
	return fmt.Sprintf("secret %q in store %q: %s", e.Name, e.Store, e.Reason)
}

// Fetch reads each distinct name from store and returns name to value. It
// retries a failed read with backoff until the Bound is spent, so a sidecar
// that is still starting does not fail the deploy. A reply that is not a
// usable secret (no string "value") is final and is not retried.
func (f *Fetcher) Fetch(ctx context.Context, store string, names []string) (map[string]string, error) {
	out := make(map[string]string, len(names))
	if len(names) == 0 {
		return out, nil
	}
	for _, name := range names {
		if reason := invalidSegment("secret store name", store); reason != "" {
			return nil, &FetchError{Store: store, Name: name, Reason: reason}
		}
		if reason := invalidSegment("secret name", name); reason != "" {
			return nil, &FetchError{Store: store, Name: name, Reason: reason}
		}
	}

	bound := f.Bound
	if bound == 0 {
		bound = defaultBound
	}
	ctx, cancel := context.WithTimeout(ctx, bound)
	defer cancel()

	for _, name := range names {
		if _, done := out[name]; done {
			continue
		}
		value, err := f.fetchOne(ctx, store, name)
		if err != nil {
			return nil, err
		}
		out[name] = value
	}
	return out, nil
}

// fetchOne retries until the secret is read, the reply is final or ctx ends.
// When ctx ends, the error carries the reason of the last attempt that got an
// answer (or failed on its own), not the expiry that cut the next one short.
func (f *Fetcher) fetchOne(ctx context.Context, store, name string) (string, error) {
	backoff := f.Backoff
	if backoff == 0 {
		backoff = defaultBackoff
	}
	var last string
	gaveUp := func(fallback string) error {
		reason := last
		if reason == "" {
			reason = fallback
		}
		return &FetchError{Store: store, Name: name,
			Reason: fmt.Sprintf("%s (gave up: %v)", reason, ctx.Err())}
	}
	for {
		value, retry, err := f.attempt(ctx, store, name)
		if err == nil {
			return value, nil
		}
		if !retry {
			return "", &FetchError{Store: store, Name: name, Reason: err.Error()}
		}
		if errors.Is(err, context.DeadlineExceeded) || errors.Is(err, context.Canceled) {
			return "", gaveUp(err.Error()) // ctx cut this attempt short
		}
		last = err.Error()
		timer := time.NewTimer(backoff)
		select {
		case <-ctx.Done():
			timer.Stop()
			return "", gaveUp(last)
		case <-timer.C:
		}
		backoff = min(backoff*2, maxBackoff)
	}
}

// attempt makes one request. retry reports whether a failure may succeed later.
func (f *Fetcher) attempt(ctx context.Context, store, name string) (value string, retry bool, err error) {
	endpoint := f.BaseURL + "/v1.0/secrets/" + url.PathEscape(store) + "/" + url.PathEscape(name)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return "", false, fmt.Errorf("build request: %w", err)
	}
	if f.Token != "" {
		req.Header.Set(tokenHeader, f.Token)
	}
	client := f.Client
	if client == nil {
		client = http.DefaultClient
	}
	resp, err := client.Do(req)
	if err != nil {
		return "", true, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, maxBodyBytes))
		return "", true, fmt.Errorf("sidecar answered status %d", resp.StatusCode)
	}
	var body struct {
		Value *string `json:"value"`
	}
	// A 2xx reply that is not a JSON object with a string "value" is final:
	// asking again would return the same thing.
	if err := json.NewDecoder(io.LimitReader(resp.Body, maxBodyBytes)).Decode(&body); err != nil || body.Value == nil {
		return "", false, errNoValue
	}
	return *body.Value, false, nil
}

// invalidSegment explains why s cannot be a single path segment of the secrets
// API, or returns "". url.PathEscape leaves "." and ".." unchanged, and a
// client or proxy may resolve them to another route.
func invalidSegment(what, s string) string {
	switch s {
	case "":
		return what + " is empty"
	case ".", "..":
		return what + " is a dot segment"
	}
	return ""
}
