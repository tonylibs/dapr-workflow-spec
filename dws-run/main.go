// Command dws-run is the generic, prebuilt step image for `run: shell` and
// `run: script` tasks in the DWS platform. One binary serves all three images;
// MODE selects the interpreter and every other behavior is env-configured. It
// runs as a Knative service with a Dapr sidecar and is invoked by
// dws-orchestrator as a multi-app Dapr Workflow activity: it registers a single
// canonical activity named "Run" against its Dapr app-id and keeps a minimal
// GET /healthz endpoint for Knative readiness.
package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"time"

	"github.com/dapr/durabletask-go/workflow"
	daprc "github.com/dapr/go-sdk/client"
	"github.com/dws/dws-run/internal/config"
	"github.com/dws/dws-run/internal/runner"
	"github.com/dws/dws-run/internal/worker"
)

const readHeaderTimeout = 10 * time.Second

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))

	cfg, err := config.Load()
	if err != nil {
		log.Error("invalid configuration", "err", err)
		os.Exit(1)
	}

	w := worker.New(cfg, runner.New(cfg), log)

	reg := workflow.NewRegistry()
	if err := w.Register(reg); err != nil {
		log.Error("register activity failed", "err", err)
		os.Exit(1)
	}

	wfClient, err := daprc.NewWorkflowClient()
	if err != nil {
		log.Error("create workflow client failed", "err", err)
		os.Exit(1)
	}

	log.Info("starting dws-run activity worker",
		"activity", worker.ActivityName,
		"task", cfg.Task,
		"mode", cfg.Mode,
		"return", cfg.Return,
		"output", cfg.Output,
		"timeout", cfg.Timeout.String(),
	)

	// StartWorker connects to the Dapr sidecar and processes dispatched
	// activities on a background goroutine, returning once the stream is up.
	if err := wfClient.StartWorker(context.Background(), reg); err != nil {
		log.Error("start activity worker failed", "err", err)
		os.Exit(1)
	}

	// The health server keeps the process alive and reports readiness to
	// Knative alongside the running activity worker.
	addr := ":" + cfg.Port
	log.Info("serving health endpoint", "addr", addr)
	httpServer := &http.Server{
		Addr:              addr,
		Handler:           w.HealthHandler(),
		ReadHeaderTimeout: readHeaderTimeout,
	}
	if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Error("health server terminated", "err", err)
		os.Exit(1)
	}
}
