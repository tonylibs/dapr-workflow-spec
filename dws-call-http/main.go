// Command dws-call-http is the generic, prebuilt step image for `call: http`
// tasks in the DWS platform. A single image serves every HTTP call step;
// behavior is defined entirely by environment configuration. It runs as a
// Knative service with a Dapr sidecar and is invoked by dws-orchestrator via
// Dapr service invocation.
package main

import (
	"log/slog"
	"net/http"
	"os"
	"time"

	"github.com/dws/dws-call-http/internal/config"
	"github.com/dws/dws-call-http/internal/runner"
	"github.com/dws/dws-call-http/internal/server"
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

	srv := server.New(cfg, runner.New(cfg), log)

	addr := ":" + cfg.Port
	log.Info("starting dws-call-http",
		"addr", addr,
		"task", cfg.Task,
		"method", cfg.Method,
		"endpoint", cfg.Endpoint,
		"bodyMode", cfg.BodyMode,
		"output", cfg.Output,
		"timeout", cfg.Timeout.String(),
	)

	httpServer := &http.Server{
		Addr:              addr,
		Handler:           srv.Handler(),
		ReadHeaderTimeout: readHeaderTimeout,
	}

	if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Error("server terminated", "err", err)
		os.Exit(1)
	}
}
