# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this package.

Root-level cross-cutting rules (commits, contract-change etiquette, per-package gate commands) are in
the repo root [`CLAUDE.md`](../CLAUDE.md). This file is dws-call-grpc-specific idioms only.

**Note on this package's own README**: it frames the design around HTTP-style request/response. The
actual invocation path is a Dapr Workflow `activity.Handler` callback — the only real HTTP route is
`GET /healthz`. Read `internal/activity/activity.go`, not the README, for the current contract.

**Scope** (from README, this is the one package with an explicit stated scope — keep respecting it):
unary methods only. Streaming methods, `.proto` source compilation, a `with.arguments` request
template, and `oauth2` for gRPC targets are explicitly out of scope.

## Commands

```shell
cd dws-call-grpc
make build   # compile
make test    # go test -race ./...
make vet     # go vet ./...
make lint    # vet + gofmt check (+ golangci-lint if installed)
make docker
```

CI gate: `go vet ./... && go test -race ./...`.

## Style guide

This package shares almost all conventions with `dws-call-http` byte-for-byte (same author intent,
same generation) — see that package's `CLAUDE.md` for the full detail on error wrapping, config
loading, `slog` usage, and test style, all identical here. This file covers only what's genuinely
`dws-call-grpc`-specific.

### Constructor can fail: `New` returns `(*Runner, error)`, unlike the other two Go packages

Because building a runner here means resolving a gRPC method descriptor (a real I/O/parse step),
`runner.New` takes a context and can error — `dws-call-http`/`dws-run`'s `New` never does:

```go
// runner.go:61
func New(ctx context.Context, cfg Config) (*Runner, error) {
    descriptor, err := resolveMethod(ctx, cfg.Endpoint, cfg.Method)
    if err != nil {
        return nil, fmt.Errorf("resolve method %q: %w", cfg.Method, err)
    }
    return &Runner{descriptor: descriptor, cfg: cfg}, nil
}
```

Follow this shape (return an error from `New`) only when construction genuinely does I/O — don't
add an error return to a constructor that can't fail just for symmetry.

### Testability: same `StepRunner` interface + fake pattern as dws-call-http

```go
// activity.go:22-24 — identical shape to dws-call-http
type StepRunner interface {
    Run(ctx context.Context, input map[string]any) (any, error)
}
```

### Test depth: this package alone stands up a real gRPC server for integration-style tests

`internal/runner/integration_test.go` + `helpers_test.go` spin up a real gRPC health server
(`newHealthRunner`) rather than testing purely against fakes — appropriate here because resolving/
dialing a real descriptor is the part of this package that can't be faked away. `dws-call-http` and
`dws-run` don't need the equivalent because they don't have a descriptor-resolution step to verify
against a live server. If you add a similarly network-dependent feature, add a matching
`integration_test.go` rather than trying to fake the gRPC reflection/descriptor layer.
