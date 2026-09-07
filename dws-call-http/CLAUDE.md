# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this package.

Root-level cross-cutting rules (commits, contract-change etiquette, per-package gate commands) are in
the repo root [`CLAUDE.md`](../CLAUDE.md). This file is dws-call-http-specific idioms only.

**Note on this package's own README**: it documents a `POST /run` HTTP handler with 200/400/502
semantics. The code no longer has that route — invocation is a Dapr Workflow `activity.Handler`
callback, the only real HTTP route is `GET /healthz`. Don't take the README's HTTP framing as
current; read `internal/activity/activity.go` instead.

## Commands

```shell
cd dws-call-http
make build   # compile bin/dws-call-http
make test    # go test -race ./...
make vet     # go vet ./...
make lint    # vet + gofmt check (+ golangci-lint if installed)
make docker  # build registry.io/dws/dws-call-http:1.0
go test ./internal/runner/ -run TestInterpolate   # single test
```

CI gate: `go vet ./... && go test -race ./...`. No `.golangci.yml` exists anywhere in the repo —
`golangci-lint` runs only if already installed locally; `gofmt` and `go vet` are the only mandatory
checks.

## Style guide

### Error handling: wrap with `%w`, classify with `errors.As` on typed errors — no sentinels, no panic

```go
// runner.go:23-45 — typed errors, not sentinel vars
type UpstreamError struct {
    Status int
    Body   string
}

func (e *UpstreamError) Error() string { return fmt.Sprintf("upstream %d", e.Status) }

// runner.go:121 — wrap at the translation boundary
req, err := http.NewRequestWithContext(ctx, method, url, body)
if err != nil {
    return nil, fmt.Errorf("build request: %w", err)
}

// activity.go:54-60 — classify via errors.As at the package boundary, not errors.Is
var upstream *UpstreamError
if errors.As(err, &upstream) {
    return nil, fmt.Errorf("step '%s' upstream failure: %s", task, upstream.Error())
}
```

`errors.Is` is for stdlib sentinels only (e.g. `errors.Is(ctx.Err(), context.DeadlineExceeded)`) —
don't use it for this package's own error types.

### Loops: plain `for range`, no `slices`/`maps` helpers

```go
// runner.go:126
for k, v := range r.cfg.Headers {
    req.Header.Set(k, v)
}
```

### Testability: define a narrow interface at the consumer boundary, test with a fake

```go
// activity.go:23-25
type StepRunner interface {
    Run(ctx context.Context, input map[string]any) (any, error)
}

func Handler(r StepRunner, task string) workflow.Activity {
    return func(ctx workflow.ActivityContext) (any, error) {
        var input map[string]any
        if err := ctx.GetInput(&input); err != nil {
            return nil, err
        }
        return r.Run(ctx.Context(), input)
    }
}
```

```go
// activity_test.go:15-24
type fakeRunner struct {
    gotInput map[string]any
    result   any
    err      error
}

func (f *fakeRunner) Run(_ context.Context, input map[string]any) (any, error) {
    f.gotInput = input
    return f.result, f.err
}
```

`dws-run` doesn't follow this pattern (injects the concrete runner, tests against real subprocess
execution) — that's a documented, accepted divergence, not something to "fix" by converting one
package to match the other.

### Config: hand-rolled `os.Getenv`-based loader, validate immediately after populating

```go
// config.go — pattern, not verbatim
func Load() (Config, error) {
    cfg := Config{
        Endpoint: getenv("ENDPOINT", ""),
        Timeout:  parseTimeout(getenv("TIMEOUT", "30s")),
    }
    if strings.TrimSpace(cfg.Endpoint) == "" {
        return Config{}, fmt.Errorf("ENDPOINT is required")
    }
    return cfg, nil
}
```

No config library (no envconfig/viper) — don't add one for a new env var, extend the existing
`getenv`/`parseTimeout`/`parseBool` helpers instead.

### Logging: `slog` JSON handler, confined to `main.go`

```go
// main.go:33
log := slog.New(slog.NewJSONHandler(os.Stdout, nil))
```

`internal/activity` never logs — it's a pure function returning an error, and `main.go` is the only
place that writes log lines. If you're tempted to add a logger parameter to a runner/activity
function, don't; that's `dws-run`'s (accepted, divergent) pattern, not this package's.

### Tests: stdlib `testing`, table-driven, same-package, `t.Setenv`

```go
// config_test.go pattern
func TestLoad(t *testing.T) {
    tests := []struct {
        name string
        env  map[string]string
        want Config
    }{
        {name: "defaults", env: nil, want: Config{Timeout: 30 * time.Second}},
    }
    for _, tt := range tests {
        t.Run(tt.name, func(t *testing.T) {
            for k, v := range tt.env {
                t.Setenv(k, v)
            }
            got, err := Load()
            if err != nil {
                t.Fatalf("Load() error = %v", err)
            }
            if !reflect.DeepEqual(got, tt.want) {
                t.Errorf("got %#v, want %#v", got, tt.want)
            }
        })
    }
}
```

No testify — assertions are `reflect.DeepEqual` + `t.Fatalf`/`t.Errorf`, `"got %#v, want %#v"` style.
Test files use `package activity`/`package config` (same-package), not a `_test` suffix package.
