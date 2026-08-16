// Package worker runs the step as a Dapr Workflow activity worker. Each
// deployed step registers a single canonical activity named "Run" (wrapping
// runner.Run) against its Dapr app-id, and keeps a minimal GET /healthz
// endpoint so Knative can gate readiness. Dispatch is disambiguated by app-id,
// not activity name, so the activity name is stable across every step image.
package worker

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"

	"github.com/dapr/durabletask-go/workflow"
	"github.com/dws/dws-run/internal/config"
	"github.com/dws/dws-run/internal/runner"
)

// ActivityName is the single canonical Dapr Workflow activity every deployed
// step registers.
const ActivityName = "Run"

// Worker wraps the runner and config as a Dapr Workflow activity handler plus a
// health endpoint.
type Worker struct {
	cfg    config.Config
	runner *runner.Runner
	log    *slog.Logger
}

// New constructs a Worker.
func New(cfg config.Config, r *runner.Runner, log *slog.Logger) *Worker {
	return &Worker{cfg: cfg, runner: r, log: log}
}

// Register adds the canonical "Run" activity to the registry.
func (w *Worker) Register(r *workflow.Registry) error {
	return r.AddActivityN(ActivityName, w.activity)
}

// activity adapts the Dapr activity context to RunActivity: it decodes the
// current workflow data (absent/empty input → {}) and runs the step. Input that
// cannot be decoded as a JSON object is a permanently broken invocation, so it
// fails as a configuration error without ever spawning a subprocess.
func (w *Worker) activity(actx workflow.ActivityContext) (any, error) {
	var input map[string]any
	if err := actx.GetInput(&input); err != nil {
		w.log.Error("decode activity input failed", "task", w.cfg.Task, "err", err)
		return nil, w.configFailure(fmt.Errorf("decode input: %w", err))
	}
	return w.RunActivity(actx.Context(), input)
}

// RunActivity is the transport-independent core of the "Run" activity: it runs
// the step for the given workflow data and shapes the outcome into the Dapr
// activity contract. A nil input is treated as empty workflow data ({}). On
// success it returns the value runner.Run produced (OUTPUT/RETURN shaping
// already applied). On failure it maps a non-zero exit (where RETURN does not
// treat the code as data) or a spawn failure to a retryable upstream marker,
// and any other error (config/shaping) to a non-retryable config marker.
func (w *Worker) RunActivity(ctx context.Context, input map[string]any) (any, error) {
	if input == nil {
		input = map[string]any{}
	}
	out, err := w.runner.Run(ctx, input)
	if err != nil {
		return nil, w.mapError(err)
	}
	return out, nil
}

// mapError classifies a runner failure. ExitError and SpawnError are
// transport-equivalent (retryable); everything else is a configuration or
// shaping fault (non-retryable).
func (w *Worker) mapError(err error) error {
	var exitErr *runner.ExitError
	var spawnErr *runner.SpawnError
	if errors.As(err, &exitErr) || errors.As(err, &spawnErr) {
		w.log.Warn("step upstream failure", "task", w.cfg.Task, "err", err)
		return w.upstreamFailure(err)
	}
	w.log.Error("step config failure", "task", w.cfg.Task, "err", err)
	return w.configFailure(err)
}

// upstreamFailure builds the retryable marker the orchestrator classifies as a
// communication error (the activity-path equivalent of the old HTTP 502).
func (w *Worker) upstreamFailure(err error) error {
	return fmt.Errorf("step '%s' upstream failure: %s", w.cfg.Task, err.Error())
}

// configFailure builds the non-retryable marker (the activity-path equivalent
// of the old HTTP 500).
func (w *Worker) configFailure(err error) error {
	return fmt.Errorf("step '%s' config failure: %s", w.cfg.Task, err.Error())
}

// HealthHandler returns the minimal health endpoint used for Knative readiness.
func (w *Worker) HealthHandler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", w.handleHealthz)
	return mux
}

func (w *Worker) handleHealthz(rw http.ResponseWriter, _ *http.Request) {
	rw.Header().Set("Content-Type", "application/json")
	rw.WriteHeader(http.StatusOK)
	if err := json.NewEncoder(rw).Encode(map[string]any{"status": "ok", "task": w.cfg.Task}); err != nil {
		w.log.Error("write health response failed", "task", w.cfg.Task, "err", err)
	}
}
