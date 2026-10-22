# CLAUDE.md

Guidance for this package only. Root-level rules (commits, gates, contract etiquette) are in the repo
[`CLAUDE.md`](../CLAUDE.md); behavior and the 4.1d contract are in [`README.md`](README.md).

## Commands

```shell
cd dws-mid-bearer
make lint     # go vet + gofmt check (+ golangci-lint if installed)
make test     # go test -race ./...
make docker   # image build; the only place TinyGo runs
```

CI gate: `go vet ./... && gofmt -l . && go test -race ./...`, then the image build and smoke check.

## Idioms

- **Two toolchains.** Plain Go (1.23 language level, see README) runs lint and tests; TinyGo 0.34.0, pinned
  in the Dockerfile, produces the `.wasm`. Never bump TinyGo or the `go` directive without re-running the
  probe. The host Go cannot reproduce a TinyGo-only failure.
- **Logic in `internal/bearer`, wiring in `main.go`.** Keep `main.go` a few lines. Handlers take
  `api.Request`; tests pass a fake that embeds the interface and overrides only what the handler uses.
- **The config is a secret.** Error messages are fixed strings. Never build one from the input, from a
  `json` error (those can quote it) or from a map key, and never add logging. A test per failure mode
  asserts the token is absent from the error text.
- **Fail closed at load, not per request.** A bad `guestConfig` must `panic` in `main` so daprd refuses the
  Component. Do not turn it into a pass-through.
- **`Set`, not `Add`.** One `Authorization` header must be forwarded, replacing the caller's.
- **No `dws-mid-basic` sharing.** The two guests are separate modules on purpose; duplicate the few matching
  lines rather than introduce a shared module.
