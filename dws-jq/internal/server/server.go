// Package server serves GET /healthz and POST /run for the one jq expression
// a dws-jq deployment is configured with (design D8).
package server

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"maps"
	"net/http"
	"sync/atomic"
	"time"

	"github.com/dws/dws-jq/internal/expr"
	"github.com/dws/dws-jq/internal/logging"
	"github.com/dws/dws-jq/internal/secrets"
)

// MaxBodyBytes is the largest accepted /run body, Dapr's default maximum
// request body size.
const MaxBodyBytes = 4 << 20

// Evaluator is the compiled expression the server runs. *expr.Program
// implements it and is safe for concurrent use.
type Evaluator interface {
	Evaluate(ctx context.Context, timeout time.Duration, in expr.Input) (expr.Result, error)
	Variables() []string
	Expression() string
}

// Options configures a Server.
type Options struct {
	// Site is JQ_SITE, reported by /healthz and on every log record.
	Site string
	// Timeout bounds one evaluation (JQ_TIMEOUT).
	Timeout time.Duration
}

// readiness is what MarkReady publishes. It is never modified once stored.
type readiness struct {
	secrets  map[string]any
	redactor *secrets.Redactor
}

// Server answers /healthz and /run. It reports "starting" and refuses /run
// until MarkReady is called.
type Server struct {
	ev       Evaluator
	opts     Options
	log      *slog.Logger
	declared map[string]struct{}
	ready    atomic.Pointer[readiness]
}

// runRequest is the /run body. Variables stays raw so a non-object value can
// be told apart from an absent one.
type runRequest struct {
	Input     json.RawMessage `json:"input"`
	Variables json.RawMessage `json:"variables"`
}

// New returns a Server, not yet ready, that evaluates with ev.
func New(ev Evaluator, opts Options, log *slog.Logger) *Server {
	declared := make(map[string]struct{})
	for _, name := range ev.Variables() {
		declared[name] = struct{}{}
	}
	return &Server{ev: ev, opts: opts, log: log, declared: declared}
}

// Handler routes GET /healthz and POST /run.
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", s.healthz)
	mux.HandleFunc("POST /run", s.run)
	return mux
}

// MarkReady publishes the loaded secret values and the redactor built from
// them, and flips readiness, in one atomic step: a request either sees the
// server as starting or sees both. A nil redactor redacts nothing.
func (s *Server) MarkReady(secretValues map[string]any, r *secrets.Redactor) {
	s.ready.Store(&readiness{secrets: maps.Clone(secretValues), redactor: r})
}

func (s *Server) healthz(w http.ResponseWriter, _ *http.Request) {
	if s.ready.Load() == nil {
		respond(w, http.StatusServiceUnavailable, map[string]any{"status": "starting", "site": s.opts.Site})
		return
	}
	respond(w, http.StatusOK, map[string]any{"status": "ok", "site": s.opts.Site})
}

func (s *Server) run(w http.ResponseWriter, r *http.Request) {
	ctx := logging.WithTraceparent(r.Context(), r.Header.Get("traceparent"))
	state := s.ready.Load()
	if state == nil {
		respond(w, http.StatusServiceUnavailable, map[string]any{"error": "not ready"})
		return
	}

	in, err := s.decode(w, r)
	if err != nil {
		reason := state.redactor.Redact(err.Error())
		s.log.WarnContext(ctx, "bad request", "site", s.opts.Site, "detail", reason)
		respond(w, http.StatusBadRequest, map[string]any{"error": reason})
		return
	}
	in.Secrets = state.secrets

	res, err := s.ev.Evaluate(ctx, s.opts.Timeout, in)
	var evalErr *expr.EvaluationError
	switch {
	case errors.As(err, &evalErr):
		s.fault(ctx, w, state, evalErr.Detail)
		return
	case err != nil:
		s.log.ErrorContext(ctx, "evaluation failed", "site", s.opts.Site, "detail", state.redactor.Redact(err.Error()))
		respond(w, http.StatusInternalServerError, map[string]any{"error": "internal error"})
		return
	}

	body, err := expr.Encode(map[string]any{"result": res.Value, "truthy": res.Truthy})
	if err != nil {
		s.fault(ctx, w, state, fmt.Sprintf("cannot encode the result: %v", err))
		return
	}
	write(w, http.StatusOK, body)
}

// fault answers 422 with the redacted detail and logs it at INFO.
func (s *Server) fault(ctx context.Context, w http.ResponseWriter, state *readiness, detail string) {
	detail = state.redactor.Redact(detail)
	s.log.InfoContext(ctx, "expression fault", "site", s.opts.Site, "detail", detail)
	respond(w, http.StatusUnprocessableEntity, map[string]any{"fault": map[string]any{
		"type":       "expression",
		"expression": s.ev.Expression(),
		"detail":     detail,
	}})
}

// decode reads and validates the /run body. Every error it returns is a 400.
func (s *Server) decode(w http.ResponseWriter, r *http.Request) (expr.Input, error) {
	raw, err := io.ReadAll(http.MaxBytesReader(w, r.Body, MaxBodyBytes))
	if err != nil {
		var tooLarge *http.MaxBytesError
		if errors.As(err, &tooLarge) {
			return expr.Input{}, fmt.Errorf("request body is larger than %d bytes", MaxBodyBytes)
		}
		return expr.Input{}, fmt.Errorf("read request body: %w", err)
	}

	req, err := decodeRequest(raw)
	if err != nil {
		return expr.Input{}, err
	}
	variables, err := s.variables(req.Variables)
	if err != nil {
		return expr.Input{}, err
	}
	var data any
	if len(req.Input) > 0 {
		if data, err = expr.DecodeJSON(req.Input); err != nil {
			return expr.Input{}, fmt.Errorf("input: %w", err)
		}
	}
	return expr.Input{Data: data, Variables: variables}, nil
}

// decodeRequest parses raw as exactly one JSON object with no unknown fields.
func decodeRequest(raw []byte) (runRequest, error) {
	var req runRequest
	if trimmed := bytes.TrimLeft(raw, " \t\r\n"); len(trimmed) == 0 || trimmed[0] != '{' {
		return req, errors.New("request body must be a JSON object")
	}
	dec := json.NewDecoder(bytes.NewReader(raw))
	dec.DisallowUnknownFields()
	if err := dec.Decode(&req); err != nil {
		return req, fmt.Errorf("invalid request body: %w", err)
	}
	if _, err := dec.Token(); !errors.Is(err, io.EOF) {
		return req, errors.New("request body must hold exactly one JSON object")
	}
	return req, nil
}

// variables decodes the request's variables object, accepting declared names
// only ("secrets" is never declared).
func (s *Server) variables(raw json.RawMessage) (map[string]any, error) {
	if len(raw) == 0 {
		return nil, nil
	}
	if raw[0] != '{' {
		return nil, errors.New("variables must be a JSON object")
	}
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(raw, &fields); err != nil {
		return nil, fmt.Errorf("variables: %w", err)
	}
	out := make(map[string]any, len(fields))
	for name, value := range fields {
		if _, ok := s.declared[name]; !ok {
			return nil, fmt.Errorf("variable %q is not declared", name)
		}
		v, err := expr.DecodeJSON(value)
		if err != nil {
			return nil, fmt.Errorf("variable %q: %w", name, err)
		}
		out[name] = v
	}
	return out, nil
}

// respond writes body with jq's output rules, the same encoder as results.
func respond(w http.ResponseWriter, status int, body map[string]any) {
	b, err := expr.Encode(body)
	if err != nil {
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}
	write(w, status, b)
}

func write(w http.ResponseWriter, status int, body []byte) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_, _ = w.Write(body)
}
