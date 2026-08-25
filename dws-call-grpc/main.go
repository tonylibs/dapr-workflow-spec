// Command dws-call-grpc is the generic, prebuilt step image for `call: grpc`
// tasks in the DWS platform. A single image serves every gRPC call step;
// behavior is defined entirely by environment configuration. It runs as a
// Knative service with a Dapr sidecar and is dispatched by dws-orchestrator as
// a multi-app Dapr Workflow activity named "Run" targeting this app-id. A
// minimal GET /healthz endpoint is served alongside the worker for Knative
// readiness.
package main

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/dapr/durabletask-go/workflow"
	daprc "github.com/dapr/go-sdk/client"

	"github.com/dws/dws-call-grpc/internal/activity"
	"github.com/dws/dws-call-grpc/internal/config"
	"github.com/dws/dws-call-grpc/internal/runner"
)

const (
	readHeaderTimeout = 10 * time.Second
)

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))

	cfg, err := config.Load()
	if err != nil {
		log.Error("invalid configuration", "err", err)
		os.Exit(1)
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	// Resolve the target method descriptor (bundled FileDescriptorSet or server
	// reflection) and build the dynamic client. A resolution failure exits
	// non-zero so a misconfigured step never becomes ready.
	stepRunner, err := runner.New(ctx, cfg)
	if err != nil {
		log.Error("build runner", "err", err)
		os.Exit(1)
	}

	// Register the canonical "Run" activity wrapping the step runner and start
	// the Dapr Workflow worker.
	wfClient, err := daprc.NewWorkflowClient()
	if err != nil {
		log.Error("create workflow client", "err", err)
		os.Exit(1)
	}

	registry := workflow.NewRegistry()
	if err := registry.AddActivityN(activity.Name, activity.Handler(stepRunner, cfg.Task)); err != nil {
		log.Error("register activity", "activity", activity.Name, "err", err)
		os.Exit(1)
	}

	log.Info("starting dws-call-grpc activity worker",
		"activity", activity.Name,
		"task", cfg.Task,
		"serviceAddr", cfg.ServiceAddr,
		"service", cfg.Service,
		"method", cfg.Method,
		"descriptorSource", descriptorSource(cfg),
		"tls", cfg.TLS,
		"authScheme", cfg.Auth.Scheme,
		"output", cfg.Output,
		"timeout", cfg.Timeout.String(),
	)

	if err := wfClient.StartWorker(ctx, registry); err != nil {
		log.Error("start workflow worker", "err", err)
		os.Exit(1)
	}

	// Serve GET /healthz for Knative readiness alongside the worker.
	healthSrv := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           healthHandler(cfg.Task),
		ReadHeaderTimeout: readHeaderTimeout,
	}
	go func() {
		if err := healthSrv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Error("health server terminated", "err", err)
			stop()
		}
	}()

	<-ctx.Done()
	log.Info("shutting down dws-call-grpc")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), readHeaderTimeout)
	defer cancel()
	if err := healthSrv.Shutdown(shutdownCtx); err != nil {
		log.Warn("health server shutdown", "err", err)
	}
}

func descriptorSource(cfg config.Config) string {
	if cfg.ProtoEndpoint != "" {
		return "descriptor-set"
	}
	return "reflection"
}

// healthHandler serves the minimal readiness endpoint the Knative Service polls.
func healthHandler(task string) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_ = json.NewEncoder(w).Encode(map[string]any{"status": "ok", "task": task})
	})
	return mux
}
