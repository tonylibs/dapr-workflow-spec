# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this package.

Root-level cross-cutting rules (commits, contract-change etiquette, per-package gate commands) are in
the repo root [`CLAUDE.md`](../CLAUDE.md). This file is dws-mid-basic-specific idioms only. What the
guest does, its config contract and the deviations from the ADR are in [`README.md`](README.md).

## Go 1.23 and TinyGo 0.34.0 are pinned on purpose

This package is **not** Go 1.26 like the other Go packages. `go.mod` says `go 1.23`; do not
"modernize" it.

- `github.com/http-wasm/http-wasm-guest-tinygo` v0.4.0 works only with **TinyGo 0.34.0**. A newer
  TinyGo builds a module that loads but fails every request with `module closed with exit_code(0)`.
- TinyGo 0.34.0 supports Go 1.19 through 1.23, so the guest module cannot move past Go 1.23. Keep
  `golang.org/x/text` at a release whose own `go` line is below 1.23 (v0.22.0) so the directive
  stays `go 1.23`; check that `make wasm` still builds before bumping any dependency.
- `make wasm` refuses a local TinyGo that does not report 0.34.0. The Dockerfile's `build` stage is
  `FROM tinygo/tinygo:0.34.0 AS build` and must run the same command the Makefile does.
- The `wasmtest/` module is separate so wazero never enters the module TinyGo resolves. It pins
  `http-wasm-host-go` v0.7.0 and `wazero` v1.9.0 (v1.8.0 traps this module on Go 1.24+).

## Commands

```shell
cd dws-mid-basic
make lint       # go vet (guest + wasmtest) and gofmt check
make test       # go test ./... on the guest module; plain Go, no TinyGo
make wasm       # build bin/basic.wasm: Docker by default (needs Docker), or TINYGO=/path/to/tinygo (0.34.0)
make test-wasm  # run wasmtest against bin/basic.wasm; plain go, no Docker; fails if the file is missing
go test ./internal/basicauth/ -run TestMatch   # single test
```

Gate: `make lint && make test`, plus `make wasm && make test-wasm`. Use Go 1.23 for it
(`GOTOOLCHAIN=go1.23.12`), the same as CI. For a Docker-free build:
`GOTOOLCHAIN=go1.23.12 TINYGO=/path/to/tinygo make wasm` (TinyGo 0.34.0 rejects a `go` newer than
1.23 on the path). The build command, which the Dockerfile mirrors:

```shell
tinygo build -o bin/basic.wasm -scheduler=none --no-debug -target=wasi .
```

`bin/` is git-ignored. The binary budget is 1 MiB (currently about 283 KB); if it is ever exceeded,
design Decision 5 says to drop NFC and document it.

## Style guide

### Structure: thin `main.go`, every rule in `internal/basicauth`

`main.go` only wires the SDK to the package: read `handler.Host.GetConfig()`, `basicauth.Parse`,
register a handler that calls `Match` and `Headers().Set`. The SDK's host imports link only under
TinyGo, so anything in `main.go` is untestable with plain `go test`; keep it to wiring and let
`wasmtest` cover it. `internal/basicauth` must not import the SDK.

### No logging, ever

The guest writes nothing: no `handler.Host.Log`, no `fmt.Print*`, no `println`. The only text it
emits is the panic of a bad config, which names fields and rules, never a value. This is a spec
requirement (credentials never appear in logs or error text), so there is no debug or opt-in
logging either. `wasmtest` fails if the guest writes to the host log, stdout or stderr while
serving.

### Errors: one `*ConfigError`, all problems, no values, no `encoding/json` text

```go
// config.go: Parse collects every problem; Error() joins them
type ConfigError struct{ Problems []string }
```

Problem strings name a field and a rule (`password is required`, `username must not contain ':'
(RFC 7617)`). Never put a configured value, a slice of the input or an `encoding/json` error in
one: json errors quote the offending character and the input is a credential. Fields and their
rules are rows in `fieldSpecs` (`config.go`); a rule's `problem` text follows the field name, and
the known-field check reads the same table, so a new field or rule is one row there. Add a case to
`TestParseInvalid` for every new rule, and a value to `TestErrorsNeverEchoValues` if the rule sees
user data. Parsing is token-based (`json.Decoder.Token`) rather than `DisallowUnknownFields`, to get
case-sensitive field names, duplicate and trailing-data detection, and message control; it works
under TinyGo 0.34.0 (checked through the real wasm).

### Safe direction: do not match when unsure

`Match` returns false for anything it cannot prove is on the endpoint (dot segments, other
endpoint, other path). A missed match sends the request unauthenticated; a wrong match sends this
credential to the wrong target. Prefer the first.

### Tests: stdlib `testing`, table-driven, same-package

As in the other Go packages: no testify; `reflect.DeepEqual` plus `t.Fatalf`/`t.Errorf` with
`got ..., want ...` messages; test files are in-package. Write the table first. Expected base64
values come from an independent source (RFC 7617, python), not from the code under test.
`wasmtest` talks to the real `bin/basic.wasm`, so its assertions must stay behavioral (what the
echo server received), not about guest internals.

## Contract notes

- Image `ghcr.io/tonylibs/dws-mid-basic`, file `/basic.wasm`, loaded by the controller (4.1d) from
  `file:///mnt/dws-wasm/basic.wasm`. Renaming the file or changing the guest config fields breaks
  the controller silently; they are a cross-package contract.
- `dws-mid-bearer` (4.1c-bearer) copies this layout, Makefile targets, CI and release shape.
- `scripts/verify-live.sh` needs a cluster; it is not part of the gate.
