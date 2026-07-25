// Package server exposes the step HTTP surface: POST /run and GET /healthz.
package server

import (
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"

	"github.com/dws/dws-run/internal/config"
	"github.com/dws/dws-run/internal/runner"
)

// Server wires the runner and config to HTTP routes.
type Server struct {
	cfg    config.Config
	runner *runner.Runner
	log    *slog.Logger
}

// New constructs a Server.
func New(cfg config.Config, r *runner.Runner, log *slog.Logger) *Server {
	return &Server{cfg: cfg, runner: r, log: log}
}

// Handler returns the routed HTTP handler.
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("POST /run", s.handleRun)
	mux.HandleFunc("GET /healthz", s.handleHealthz)
	return mux
}

func (s *Server) handleHealthz(w http.ResponseWriter, _ *http.Request) {
	s.writeJSON(w, http.StatusOK, map[string]any{"status": "ok", "task": s.cfg.Task})
}

// handleRun is the step entrypoint. The request body is the current workflow
// data (a JSON object); the response is the outcome shaped per RETURN/OUTPUT.
func (s *Server) handleRun(w http.ResponseWriter, req *http.Request) {
	input, err := decodeInput(req.Body)
	if err != nil {
		s.log.Warn("invalid request body", "task", s.cfg.Task, "err", err)
		s.writeJSON(w, http.StatusBadRequest, map[string]any{
			"task":  s.cfg.Task,
			"error": "invalid JSON body: " + err.Error(),
		})
		return
	}

	out, err := s.runner.Run(req.Context(), input)
	if err != nil {
		s.writeRunError(w, err)
		return
	}

	s.writeJSON(w, http.StatusOK, out)
}

// writeRunError maps runner failures to HTTP responses. Non-zero exits (where
// RETURN does not treat the code as data) and spawn failures become 502 so the
// orchestrator retries; configuration and shaping errors become 500, since
// retrying will not help.
func (s *Server) writeRunError(w http.ResponseWriter, err error) {
	var exitErr *runner.ExitError
	if errors.As(err, &exitErr) {
		s.log.Warn("subprocess exited non-zero", "task", exitErr.Task, "code", exitErr.Code)
		s.writeJSON(w, http.StatusBadGateway, map[string]any{
			"task":     exitErr.Task,
			"exitCode": exitErr.Code,
			"stderr":   exitErr.Stderr,
		})
		return
	}

	var spawnErr *runner.SpawnError
	if errors.As(err, &spawnErr) {
		s.log.Error("spawn failure", "task", spawnErr.Task, "err", spawnErr.Err)
		s.writeJSON(w, http.StatusBadGateway, map[string]any{
			"task":  spawnErr.Task,
			"error": spawnErr.Error(),
		})
		return
	}

	s.log.Error("run failed", "task", s.cfg.Task, "err", err)
	s.writeJSON(w, http.StatusInternalServerError, map[string]any{
		"task":  s.cfg.Task,
		"error": err.Error(),
	})
}

// decodeInput reads the request body as a JSON object. An empty body is
// treated as empty workflow data rather than an error.
func decodeInput(body io.Reader) (map[string]any, error) {
	dec := json.NewDecoder(body)
	var input map[string]any
	if err := dec.Decode(&input); err != nil {
		if errors.Is(err, io.EOF) {
			return map[string]any{}, nil
		}
		return nil, err
	}
	if input == nil {
		input = map[string]any{}
	}
	return input, nil
}

func (s *Server) writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(payload); err != nil {
		s.log.Error("write response failed", "task", s.cfg.Task, "err", err)
	}
}
