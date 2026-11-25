// Command dws-jq evaluates one jq expression, fixed at deployment, over HTTP
// (POST /run). It compiles the expression and reads any secrets it references
// at startup, and only then reports ready on GET /healthz (design D4).
package main

import (
	"context"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/dws/dws-jq/internal/config"
	"github.com/dws/dws-jq/internal/expr"
	"github.com/dws/dws-jq/internal/logging"
	"github.com/dws/dws-jq/internal/secrets"
	"github.com/dws/dws-jq/internal/server"
)

const (
	readHeaderTimeout = 10 * time.Second
	shutdownTimeout   = 10 * time.Second
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	code := run(ctx, os.Stdout)
	stop()
	os.Exit(code)
}

// run starts the service, logging to stdout, until ctx is cancelled (by
// SIGINT/SIGTERM in main), and returns the process exit code.
func run(ctx context.Context, stdout io.Writer) int {
	cfg, err := config.Load()
	if err != nil {
		log := logging.New(stdout, os.Getenv("DWS_LOG_FORMAT"), nil)
		// config.Load returned no Config, so read the two fields the record
		// names straight from the environment.
		startupError(log, config.Config{Site: siteFromEnv(), Expression: os.Getenv("JQ_EXPRESSION")},
			"invalid configuration: "+err.Error())
		return 1
	}

	// The redactor is only known once secrets are read; every record logged
	// from then on is redacted with it.
	var redactor atomic.Pointer[secrets.Redactor]
	log := logging.New(stdout, cfg.LogFormat, func(s string) string { return redactor.Load().Redact(s) })

	program, err := expr.Compile(cfg.Expression, cfg.Variables)
	if err != nil {
		startupError(log, cfg, "invalid expression: "+err.Error())
		return 1
	}
	names := program.SecretNames()
	switch {
	case len(names) > 0 && cfg.SecretStore == "":
		startupError(log, cfg, "JQ_SECRET_STORE is required because the expression reads $secrets")
		return 1
	case len(names) == 0 && cfg.SecretStore != "":
		log.Warn("JQ_SECRET_STORE is set but the expression reads no secrets",
			"site", cfg.Site, "store", cfg.SecretStore)
	}

	// Listen before reading secrets: daprd waits for the app port before it
	// serves the secrets API.
	ln, err := net.Listen("tcp", ":"+cfg.Port)
	if err != nil {
		log.Error("cannot listen: "+err.Error(), "site", cfg.Site, "port", cfg.Port)
		return 1
	}
	srv := server.New(program, server.Options{Site: cfg.Site, Timeout: cfg.Timeout}, log)
	httpSrv := &http.Server{Handler: srv.Handler(), ReadHeaderTimeout: readHeaderTimeout}
	serveErr := make(chan error, 1)
	go func() { serveErr <- httpSrv.Serve(ln) }()
	defer shutdown(log, httpSrv)

	values, err := readSecrets(ctx, cfg, names)
	if err != nil && ctx.Err() != nil {
		// A shutdown signal arrived while the secrets were being read.
		log.Info("shutting down", "site", cfg.Site)
		return 0
	}
	if err != nil {
		// No word containing "secret" may directly precede a colon: the JSON
		// scrubber would take the next word for a credential and mask it.
		log.Error("reading from the secret store failed; "+err.Error(),
			"site", cfg.Site, "expression", cfg.Expression, "store", cfg.SecretStore,
			"names", strings.Join(names, ","))
		return 1
	}
	plain := make([]string, 0, len(values))
	bound := make(map[string]any, len(values))
	for name, value := range values {
		plain = append(plain, value)
		bound[name] = value
	}
	r := secrets.NewRedactor(plain)
	redactor.Store(r)
	srv.MarkReady(bound, r)
	log.Info("ready", "site", cfg.Site, "port", cfg.Port, "variables", strings.Join(cfg.Variables, ","))

	select {
	case <-ctx.Done():
		log.Info("shutting down", "site", cfg.Site)
		return 0
	case err := <-serveErr:
		log.Error("server stopped: "+err.Error(), "site", cfg.Site)
		return 1
	}
}

// startupError writes the one ERROR record of a failed startup check, naming
// the site, the expression as configured and the reason.
func startupError(log *slog.Logger, cfg config.Config, reason string) {
	log.Error(fmt.Sprintf("%s; expression: %s", reason, cfg.Expression),
		"site", cfg.Site, "expression", cfg.Expression)
}

// siteFromEnv returns JQ_SITE, or the default when it is unset or empty.
func siteFromEnv() string {
	if site := os.Getenv("JQ_SITE"); site != "" {
		return site
	}
	return config.DefaultSite
}

// readSecrets reads every referenced secret from the Dapr sidecar. It does not
// contact the sidecar when names is empty.
func readSecrets(ctx context.Context, cfg config.Config, names []string) (map[string]string, error) {
	if len(names) == 0 {
		return map[string]string{}, nil
	}
	f := &secrets.Fetcher{BaseURL: "http://127.0.0.1:" + cfg.DaprHTTPPort, Token: cfg.DaprAPIToken}
	return f.Fetch(ctx, cfg.SecretStore, names)
}

func shutdown(log *slog.Logger, httpSrv *http.Server) {
	ctx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
	defer cancel()
	if err := httpSrv.Shutdown(ctx); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Warn("shutdown: " + err.Error())
	}
}
