# Design

## Context

See `proposal.md` for motivation; the contract is in `specs/wasm-bearer-middleware/spec.md`. Inputs:
ADR 0010 Decisions 4 and 4a, roadmap §4h, and the spike under `spikes/wasm-auth-middleware/`.

What the spike's guest (`guest/main.go`) does, and what this change keeps or drops:

| Spike guest | `dws-mid-bearer` |
|---|---|
| One binary, `scheme` switch (bearer + basic) | Bearer only; no `scheme`, `username`, `password` |
| `path` field, exact match on one path | No `path` field; whole endpoint (D2) |
| `panic("invalid guestConfig")` on bad JSON only | Validates every field; fails closed (D3) |
| Logs `uri=` and the match target on every request | Logs nothing (D5) |
| Builds with `go mod tidy` in the image | Committed `go.sum`, `-mod=readonly` build |

Environment checked while writing this design: the `handler` package of `http-wasm-guest-tinygo` v0.4.0
compiles under plain `go` (it ships a stub for the host imports) and its `handler/api` package is only
interfaces, so the handler logic can be unit-tested without TinyGo. TinyGo itself is only needed to
produce the `.wasm`. The local TinyGo is 0.43.0, which is exactly the version that breaks the guest
(`exit_code(0)`); the pinned 0.34.0 is available only as the `tinygo/tinygo:0.34.0` image.

## Goals / Non-Goals

**Goals:** a bearer-only guest small enough to audit, tested under plain `go test`; an image whose only
contract is `/bearer.wasm`; CI, release-please package and probe that mirror the existing components.

**Non-goals:** anything in the proposal's non-goals, plus a host-level test that loads the built `.wasm`
under wazero (see Risks) and shared code with `dws-mid-basic` (the two guests are deliberately separate
modules; a few duplicated lines of matching logic is cheaper than a shared module neither release can
move independently).

## Decisions

### D1. Package layout: logic in `internal/bearer`, wiring in `main.go`

```
dws-mid-bearer/
  main.go                  # reads handler.Host.GetConfig(), wires HandleRequestFn; the only TinyGo-specific file
  internal/bearer/         # Config, Parse, (Config).Matches, (Config).Handle(api.Request)
  Dockerfile  Makefile  go.mod  go.sum  version.txt  CHANGELOG.md  README.md  CLAUDE.md
  probe/                   # bearer-only live probe (D7)
```

`Handle` takes `api.Request`, so tests pass a fake that embeds the interface and overrides only
`GetURI` and `Headers`. `go vet`, `gofmt` and `go test -race` run on the repo's normal Go toolchain;
only the Dockerfile uses TinyGo. `go.mod` says `go 1.23` (TinyGo 0.34.0 supports up to 1.23). Rejected:
putting everything in `package main` (the handler could then only be exercised through a built module).

### D2. Match on the endpoint's invocation prefix; no `path` field

ADR 0010 defines the policy per (base URL, policy): one `HTTPEndpoint` carries only `baseUrl`, and every
path under it belongs to the same policy. So the filter is the prefix `/v1.0/invoke/<endpoint>/method`
followed by `/`, `?` or end of string. The boundary check is what keeps `pets-api-2` and `pets-apix` out.

Consequence for the probe: the spike configured `path: /intended` and asserted that
`bearer-target/unrelated` gets **no** header. Under D2 that request is on the same endpoint and
correctly gets the header. The bearer probe therefore asserts endpoint isolation (a second endpoint
served by the same pod, and non-invocation paths) and drops the same-endpoint-other-path assertion. This
is a deviation from "the spike probe, unchanged" and is reported as such.
Alternative: keep an optional `path`. Rejected: nothing in ADR 0010 or 4.1d produces it, and an
unvalidated optional narrowing field is another way to end up with silently missing credentials.

### D3. Missing or empty token: abort at module init (fail closed)

`main` parses and validates `guestConfig` before registering the handler and `panic`s on any problem. In
daprd a Wasm middleware that cannot be instantiated fails component initialisation, which stops the
sidecar from starting (unless the Component sets `ignoreErrors: true`), so the pod never reaches the
point of sending an unauthenticated request. This is loud, early (the Secret is wrong at deploy, not at
first call) and needs no per-request branch.

Rejected alternatives:
- *Register a handler that answers 401/500 for the endpoint.* The pod starts healthy and fails only on the
  first call, which hides a bad Secret until traffic arrives.
- *Pass through and log a warning.* Sends the call without credentials, which the task explicitly rules out.

What is validated: valid JSON object; no unknown fields (a stray `scheme`/`username` means the controller
composed the wrong config for this component); `endpoint` non-empty, no `/ ? #`, no control characters;
`token` non-empty after trimming and free of control characters (a trailing `\n` from `echo` would yield
an invalid header value, and silently trimming would change the credential). I will check on the
**built** module, not just in unit tests, that TinyGo's `encoding/json` honours
`Decoder.DisallowUnknownFields`; if it does not, the check is done by decoding into
`map[string]json.RawMessage` first.

Open runtime assumption, verified in the probe rather than assumed: that daprd 1.18 really refuses the
component (and the sidecar) on a guest panic and does not skip the middleware. The probe adds a second
Deployment with an empty-token Component and records what daprd does. If daprd instead skips the handler,
D3 is insufficient and the guest must also fail closed per request.

### D4. Contract for 4.1d (documented, not implemented here)

- Component `url: file:///mnt/dws-wasm/bearer.wasm`; init container runs `cp /bearer.wasm
  /mnt/dws-wasm/bearer.wasm`; image pinned by digest.
- `guestConfig` is `{"endpoint": <HTTPEndpoint name>, "token": <value>}` composed by the controller.
- The controller **must not** set `ignoreErrors: true` on the Wasm Component, or D3's failure mode is
  neutralised. It should also write the token without a trailing newline.
- Recorded in the package README and the ADR 0010 status note so 4.1d finds it.

### D5. Silence

The spike's per-request `Host.Log` line goes. The guest never logs. Startup failures are `panic` with a
static string per field (`"dws-mid-bearer: guestConfig.token is missing or empty"`), never built from the
config bytes or from a `json` error (those can quote input). Because `guestConfig` can be delivered via a
`secretKeyRef`, the whole config is treated as secret.

### D6. Header semantics: `Set`, not `Add`

The task text says "appends"; ADR 0010 and the spike say "set, overwriting any caller-supplied value".
`Headers().Set` leaves exactly one `Authorization`; `Add` would forward two when a caller supplied one,
which targets handle unpredictably and which the spike's overwrite assertion forbids. Implemented as
`Set`. Reported as an interpretation, not a deviation from ADR.

### D7. Image, CI, release

- **Dockerfile:** stage 1 `tinygo/tinygo:0.34.0` runs `tinygo build -o /bearer.wasm -scheduler=none
  --no-debug -target=wasi .` (the spike's flags); stage 2 `busybox:1.37.0`, `COPY --chmod=0644` the file,
  `USER 65532:65532`. The tinygo stage runs as root (as in the spike) because the image's default user
  cannot write to `/src`. No entrypoint: the controller's init container supplies the command.
- **CI** (`.github/workflows/dws-mid-bearer.yml`, modelled on `dws-call-grpc.yml`): `test` job runs
  `make lint && make test`; `build-image` job builds with `load: true`, smoke-checks the magic bytes and a
  non-zero size, then pushes `:latest` / `:<sha>` / `:<version>` (tag `dws-mid-bearer-v*`) off PRs. Same
  load-then-push pattern as `dws-flow.yml`.
- **release-please:** package `dws-mid-bearer`, `release-type: simple`, `version.txt`, `CHANGELOG.md`,
  `skip-github-release: true`, manifest `0.1.0`; plus a `component-release` choice. First release follows
  the "First release of a new component" runbook (`force=true`, `bootstrap-sha` reviewed).
- **Public GHCR:** the first push creates a private package. Making it public is a one-time manual setting
  on GitHub; it is reported, not scripted.
- **Probe** (`probe/`): the spike's `verify.sh` pared down to one bearer endpoint, parameterised on the
  image, copying `/bearer.wasm` via an init container exactly as 4.1d will, plus the empty-token case.
  Promotion to `scripts/` is 4.1h.

## Risks / Trade-offs

- **TinyGo ≥ 0.35 breaks the guest at request time** → the Dockerfile pins 0.34.0 and the CI smoke check
  only proves the file is Wasm, not that it runs. The probe is the real gate. A wazero-based host test
  would catch a toolchain bump in CI but adds a dependency; deferred, noted in the README.
- **Plain-Go tests do not exercise the Wasm build** (TinyGo's `encoding/json`, `strings` behaviour) →
  the probe runs the actual module; the `DisallowUnknownFields` check in D3 is done against the built file.
- **The probe cannot run in CI or in this session's container** (it needs a Dapr cluster) → it is run by
  hand on Tony's local cluster; if it has not been run, the report says so instead of claiming it passed.
- **Same-endpoint other paths get the token** (D2) → intended; one policy per endpoint.
- **`dapr-app-id`-header invocations are not matched** → they pass through unauthenticated (never leak a
  credential); step images use the invoke URL form (4.1e).
- **Duplicated matching logic with `dws-mid-basic`** → accepted (see Non-goals).
- **Renaming from `dws-wasm-bearer`** → ADR 0010 and the roadmap are edited in this change so there is one
  name; the ADR said the names "may change in the 4.1c proposals".

## Migration Plan

Additive. Nothing deploys or references the image until 4.1d. Rollback is deleting the package, its
workflow and its release-please entries. After merge: seed the first release per the runbook, make the
GHCR package public, then 4.1d pins the resulting digest.
