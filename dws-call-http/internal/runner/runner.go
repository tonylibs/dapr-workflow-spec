// Package runner executes the configured outbound HTTP call for a step.
package runner

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	neturl "net/url"
	"strings"

	"github.com/dws/dws-call-http/internal/config"
)

// UpstreamError is returned when the upstream call completes with a non-2xx
// status. The server maps it to a 502 with a {task, status, body} JSON body so
// the orchestrator's retry policy triggers.
type UpstreamError struct {
	Task   string
	Status int
	Body   string
}

func (e *UpstreamError) Error() string {
	return fmt.Sprintf("upstream call for task %q returned status %d", e.Task, e.Status)
}

// TransportError is returned when the request never produced a response
// (connection refused, DNS failure, timeout). It is also mapped to a 502 so the
// orchestrator retries, since these are typically transient.
type TransportError struct {
	Task string
	Err  error
}

func (e *TransportError) Error() string {
	return fmt.Sprintf("transport error for task %q: %v", e.Task, e.Err)
}

func (e *TransportError) Unwrap() error { return e.Err }

// Runner performs the configured HTTP call.
type Runner struct {
	cfg    config.Config
	client *http.Client
}

// New builds a Runner with an HTTP client honoring the configured timeout and
// TLS verification setting.
func New(cfg config.Config) *Runner {
	transport := http.DefaultTransport.(*http.Transport).Clone()
	if cfg.InsecureSkipVerify {
		transport.TLSClientConfig = &tls.Config{InsecureSkipVerify: true}
	}
	return &Runner{
		cfg: cfg,
		client: &http.Client{
			Timeout:   cfg.Timeout,
			Transport: transport,
		},
	}
}

// Run executes the outbound call for the given input workflow data and returns
// the result shaped per the configured OUTPUT mode.
func (r *Runner) Run(ctx context.Context, input map[string]any) (any, error) {
	req, err := r.buildRequest(ctx, input)
	if err != nil {
		return nil, err
	}

	resp, err := r.client.Do(req)
	if err != nil {
		return nil, &TransportError{Task: r.cfg.Task, Err: err}
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, &TransportError{Task: r.cfg.Task, Err: err}
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, &UpstreamError{Task: r.cfg.Task, Status: resp.StatusCode, Body: string(respBody)}
	}

	return r.shapeOutput(input, respBody)
}

// buildRequest interpolates the endpoint, query, and body, then assembles the
// *http.Request with the configured method and headers.
func (r *Runner) buildRequest(ctx context.Context, input map[string]any) (*http.Request, error) {
	target, err := Interpolate(r.cfg.Endpoint, input)
	if err != nil {
		return nil, err
	}

	target, err = r.appendQuery(target, input)
	if err != nil {
		return nil, err
	}

	body, hasJSONBody, err := r.buildBody(input)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequestWithContext(ctx, r.cfg.Method, target, body)
	if err != nil {
		return nil, fmt.Errorf("build request: %w", err)
	}
	if hasJSONBody {
		req.Header.Set("Content-Type", "application/json")
	}
	for k, v := range r.cfg.Headers {
		req.Header.Set(k, v)
	}
	return req, nil
}

func (r *Runner) appendQuery(target string, input map[string]any) (string, error) {
	if len(r.cfg.Query) == 0 {
		return target, nil
	}
	values := neturl.Values{}
	for k, v := range r.cfg.Query {
		iv, err := Interpolate(v, input)
		if err != nil {
			return "", err
		}
		values.Set(k, iv)
	}
	sep := "?"
	if strings.Contains(target, "?") {
		sep = "&"
	}
	return target + sep + values.Encode(), nil
}

// buildBody returns the request body reader and whether a JSON Content-Type
// should be set.
func (r *Runner) buildBody(input map[string]any) (io.Reader, bool, error) {
	switch r.cfg.BodyMode {
	case config.BodyPassthrough:
		b, err := json.Marshal(input)
		if err != nil {
			return nil, false, fmt.Errorf("marshal passthrough body: %w", err)
		}
		return bytes.NewReader(b), true, nil
	case config.BodyTemplate:
		s, err := Interpolate(r.cfg.BodyTemplate, input)
		if err != nil {
			return nil, false, err
		}
		return strings.NewReader(s), true, nil
	case config.BodyNone:
		return nil, false, nil
	default:
		return nil, false, fmt.Errorf("unknown body mode %q", r.cfg.BodyMode)
	}
}

// shapeOutput turns the upstream response bytes into the value returned to the
// caller according to the OUTPUT mode.
func (r *Runner) shapeOutput(input map[string]any, respBody []byte) (any, error) {
	trimmed := bytes.TrimSpace(respBody)

	if r.cfg.Output == config.OutputMerge {
		upstream := map[string]any{}
		if len(trimmed) > 0 {
			if err := json.Unmarshal(trimmed, &upstream); err != nil {
				return nil, fmt.Errorf("decode upstream response for merge (expected JSON object): %w", err)
			}
		}
		merged := make(map[string]any, len(input)+len(upstream))
		for k, v := range input {
			merged[k] = v
		}
		for k, v := range upstream {
			merged[k] = v
		}
		return merged, nil
	}

	// OutputReplace: return the upstream body verbatim (object, array, or scalar).
	if len(trimmed) == 0 {
		return map[string]any{}, nil
	}
	var upstream any
	if err := json.Unmarshal(trimmed, &upstream); err != nil {
		return nil, fmt.Errorf("decode upstream response: %w", err)
	}
	return upstream, nil
}
