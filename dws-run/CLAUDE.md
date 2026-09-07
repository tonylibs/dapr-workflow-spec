# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this package.

Root-level cross-cutting rules (commits, contract-change etiquette, per-package gate commands) are in
the repo root [`CLAUDE.md`](../CLAUDE.md). This file is dws-run-specific idioms only.

**Note on this package's own README**: it documents a `POST /run` HTTP handler with a 502-retry
story. The code has no such route — invocation is a Dapr Workflow `activity.Handler` via
`Worker.Register`, the only real HTTP route is `GET /healthz`. Read `internal/worker/worker.go`, not
the README, for the current contract.

One Go codebase produces three images (`dws-run-shell`, `dws-run-script-js`, `dws-run-script-python`)
from three Dockerfiles sharing an identical Go build stage; `MODE` selects the interpreter at
runtime — don't fork the source per-mode, keep the branching inside the shared runner.

## Commands

```shell
cd dws-run
make build      # compile bin/dws-run
make test       # go test -race ./...
make vet        # go vet ./...
make fmt-check  # gofmt check
make lint       # vet + fmt-check (+ golangci-lint if installed)
make docker     # builds all three images
```

CI gate: `go vet ./... && go test -race ./...`.

## Style guide

Shares error-wrapping, config-loading, and stdlib table-driven test style with `dws-call-http` and
`dws-call-grpc` (see `dws-call-http/CLAUDE.md` for that detail — it's identical here). This file
covers the two places `dws-run` deliberately diverges from its sibling Go packages, both accepted
as per-package choices, not something to converge.

### Logger threaded into business logic, not confined to `main.go`

Unlike `dws-call-http`/`dws-call-grpc`, `Worker` holds a `*slog.Logger` and logs per-invocation
inside the activity path — because a failing shell/script step needs structured context (exit code,
stderr excerpt) logged at the point of failure, not just at startup:

```go
// worker.go:29-34
type Worker struct {
    cfg    Config
    runner *runner.Runner
    log    *slog.Logger
}

// worker.go:81-84
if err := w.runner.Run(ctx, input); err != nil {
    w.log.Error("step config failure", "task", w.cfg.Task, "err", err)
    return nil, err
}
```

### DI: concrete `*runner.Runner`, not an interface — tests exercise real subprocess execution

`dws-call-http`/`dws-call-grpc` define a `StepRunner` interface and test with a fake; `dws-run`
injects the concrete runner and its tests spawn real subprocesses:

```go
// worker.go:29,34 — concrete type, not an interface
type Worker struct {
    runner *runner.Runner
    // ...
}

// worker_test.go:23-31 — tests exercise the real shell, not a fake
cfg := shellCfg(t, `echo '{"answer":42}'`)
w := worker.New(cfg, runner.New(cfg), log)
```

This is appropriate here because the runner's entire job is process execution — a fake would just
restate the assertion. If you add a new dependency to `Worker` that has real failure modes worth
faking (e.g. a network call), define a narrow interface for that one dependency rather than
wrapping the whole runner.

### Activity registration: stateful `Worker.Register(*workflow.Registry) error`, not a free function

`dws-call-http`/`dws-call-grpc` expose `activity.Handler(r, task) workflow.Activity` as a free
function returning a closure. `dws-run` wraps the same concern as a method because `Worker` already
carries state (the logger, the runner) that the handler needs:

```go
// worker.go:39-41
func (w *Worker) Register(r *workflow.Registry) error {
    return r.AddActivityN(ActivityName, w.activity)
}
```

Health handler follows the same shape — `HealthHandler()`/`handleHealthz` are methods on `Worker` in
`internal/worker/worker.go`, not an inline closure in `main.go` like the other two packages. Match
this method-on-Worker style for anything else the worker needs to expose.
