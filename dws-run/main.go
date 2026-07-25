// Command dws-run is the generic, prebuilt step image for `run: shell` and
// `run: script` tasks in the DWS platform. One binary serves all three images;
// MODE selects the interpreter and every other behavior is env-configured. It
// runs as a Knative service with a Dapr sidecar and is invoked by
// dws-orchestrator via Dapr service invocation.
package main

import (
	"log/slog"
	"net/http"
	"os"
	"time"

	"github.com/dws/dws-run/internal/config"
	"github.com/dws/dws-run/internal/runner"
	"github.com/dws/dws-run/internal/server"
)

const readHeaderTimeout = 10 * time.Second

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))

	cfg, err := config.Load()
	if err != nil {
		log.Error("invalid configuration", "err", err)
		os.Exit(1)
	}

	srv := server.New(cfg, runner.New(cfg), log)

	addr := ":" + cfg.Port
	log.Info("starting dws-run",
		"addr", addr,
		"task", cfg.Task,
		"mode", cfg.Mode,
		"return", cfg.Return,
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
