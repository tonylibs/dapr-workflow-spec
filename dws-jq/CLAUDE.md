# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this package.

Root-level cross-cutting rules (commits, contract-change etiquette, per-package gate commands) are in
the repo root [`CLAUDE.md`](../CLAUDE.md). This file is dws-jq-specific idioms only. Behavior and
contracts are in [`README.md`](README.md), `docs/adr/0014-dws-jq-expression-function.md` and
`openspec/changes/dws-jq-function/design.md`.

## Commands

```shell
cd dws-jq
make build   # compile bin/dws-jq
make test    # go test -race ./...
make vet     # go vet ./...
make lint    # vet + gofmt check (+ golangci-lint if installed)
make docker  # build registry.io/dws/dws-jq:1.0
go test ./internal/expr/ -run TestCheck              # single test
go test ./internal/conformance/ -v                   # the expression corpus, one line per case
```

CI gate: `go vet ./... && gofmt -l . && go test -race ./...`. No `.golangci.yml` exists in the repo.

**No `-race` on windows/386.** The local Go toolchain here is windows/386, where the race detector is
unavailable; run `go test ./...` locally and leave `-race` to CI (linux/amd64).

## Style guide

### Error handling: typed errors, classify with `errors.As`

Each failure class is its own type, and callers branch with `errors.As`, never on message text:

```go
// internal/expr: PolicyError, SecretAccessError, CompileError, EvaluationError
// internal/secrets: FetchError   internal/config: FieldError
var evalErr *expr.EvaluationError
switch {
case errors.As(err, &evalErr):   // a jq fault -> 422
    ...
case err != nil:                 // anything else -> 500 "internal error"
    ...
}
```

`EvaluationError` is the only error `Evaluate` returns for expression faults; it is what separates a
422 from a 500. Wrap with `%w` at translation boundaries. No sentinels, no panics.

### Config: hand-rolled `getenv`, validate immediately

`internal/config` reads `os.Getenv` through `getenv(key, default)` plus `parseVariables` and
`parseTimeout`, and returns a `*FieldError` naming the variable. No config library. Every name read
must be in `config.Names`, and every name in `Names` must appear in a
`testdata/contract/env-*.json` fixture; `TestConfigContract` enforces both. Adding an env var means
adding it to `Names`, to a fixture, to the README table and to the root `CLAUDE.md` contract bullet.

### Logging: only `main.go` and `internal/server`

`internal/expr`, `internal/secrets` and `internal/config` never log; they return errors. `main.go`
logs startup, shutdown and server failures, and `internal/server` logs per-request 400/422. The
logger comes from `logging.New`; it applies the redactor (secret values) before the contract scrubber.
Never put a secret value, a request payload or a result in a log call. In an `ERROR` message, do not
place a word containing `secret` directly before a colon: the JSON scrubber would take the next word
for a credential and mask it.

### Tests: stdlib `testing`, table-driven, same-package

No testify. Assertions are `t.Fatalf`/`t.Errorf` with `"got %v, want %v"`; test files use the
package's own name (`package expr`, not `expr_test`). Use `t.Setenv` for env, `httptest` for the
server. The only test-only dependencies are `gopkg.in/yaml.v3` and
`github.com/santhosh-tekuri/jsonschema/v6` (the corpus runner); neither is linked into the binary.

### Policy walker is pinned to gojq v0.12.20

`internal/expr/policy.go` walks `gojq.Query` structure field by field, so a gojq upgrade can add a
new place an `env`/`$secrets` reference hides without any compile error. When bumping gojq, re-run
`go test ./internal/expr/` and the corpus, and re-read the `gojq` AST types for new fields before
trusting the walker.

### Expression corpus

`internal/conformance` runs `conformance/expressions/` (repo root) through the same
`expr.Compile` + `Evaluate` that `/run` uses, inside `go test`. The default path is
`../../../conformance/expressions` relative to the package; set `DWS_CONFORMANCE_EXPRESSIONS` to use
another copy. Never edit a case's expectation to make dws-jq pass: add it to
`conformance/expressions/divergences/dws-jq.yaml`, which fails the build when it goes stale.
