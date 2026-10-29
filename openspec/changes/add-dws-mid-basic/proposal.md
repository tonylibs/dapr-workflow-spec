# Proposal

## Why

ADR 0010 Decision 4a moves `basic`/`bearer` credentials for `call: http`-style steps out of step
images and into a Wasm HTTP middleware on the step's Dapr sidecar, one component per scheme. The
spike (`spikes/wasm-auth-middleware/`) proved the mechanism with a throwaway guest that switched on
a `scheme` field. OWS roadmap row **4.1c-basic** (§4h) turns the `basic` half into a real,
released component. Controller wiring (4.1d) cannot pin an image digest until this one exists.

## What Changes

- **New top-level package `dws-mid-basic`** (Go source, built with TinyGo 0.34.0 pinned,
  `http-wasm-guest-tinygo` v0.4.0). It builds one Wasm guest, `basic.wasm`, that daprd loads
  through a `middleware.http.wasm` Component.
- The guest reads `endpoint`, `username` and `password` from its guest config. There is no
  `scheme` field: loading this component *is* choosing `basic`. Unknown fields are rejected.
- For a request on its endpoint's invocation path (`/v1.0/invoke/<endpoint>/method` and below),
  the guest sets `Authorization: Basic base64(username ":" password)`, encoded per RFC 7617
  (UTF-8 after Unicode NFC, password may contain `:`). Every other request passes through
  unchanged.
- **Missing or invalid credentials fail loudly at load, not per request**: an absent or empty
  `username`/`password`, a `:` in the username (RFC 7617 makes it invalid), or a control
  character makes the guest refuse to start, so the middleware never sends the call without
  credentials. The error text names the field and the rule, never a value.
- No credential appears in any log line or error text the guest emits.
- **Image `ghcr.io/tonylibs/dws-mid-basic`**: `busybox` base holding only `/basic.wasm`; an init
  container copies it into the step pod's shared `emptyDir`. Public on GHCR.
- **CI** (`.github/workflows/dws-mid-basic.yml`): unit tests, TinyGo build, a host test that runs
  the built `basic.wasm` in the same http-wasm host library daprd uses, then image build (push on
  `main` and on `dws-mid-basic-v*` tags).
- **Release**: its own release-please package (`simple`, `version.txt`, `skip-github-release`),
  added to `component-release.yml`; released by hand like every component.
- **Live probe** `dws-mid-basic/scripts/verify-live.sh`, derived from the spike's `verify.sh` but
  basic-only, for the done-when check on Tony's local cluster.
- **Docs**: package `README.md` and `CLAUDE.md`; root `CLAUDE.md` package map and gate table; ADR
  0010 amendment and roadmap §4h row recording the final name (`dws-mid-basic` instead of the
  proposed `dws-wasm-basic`) and status.

Non-goals:

- `dws-mid-bearer` (4.1c-bearer). This change sets the layout it should match.
- Controller wiring (4.1d): the composed `guestConfig` Secret, `HTTPEndpoint`, Wasm Component,
  init container, `emptyDir`, digest pin.
- Step image changes (4.1e) and promoting the probe to `scripts/` (4.1h).
- gRPC (`call: grpc` keeps its env-var credentials) and OAuth2 (unchanged).

Compatibility: additive. No DSL behavior, deployed resource or runtime interpretation changes for
existing workflow definitions: nothing loads `basic.wasm` until 4.1d. Existing `basic` auth keeps
its current env-var path in step images until 4.1d/4.1e ship together.

## Capabilities

### New Capabilities

- `basic-auth-middleware`: the `dws-mid-basic` Wasm guest's behavior on a Dapr sidecar — its
  guest-config contract, endpoint path matching, RFC 7617 header composition, fail-at-load
  validation, credential hygiene, and how its binary is packaged and delivered.

### Modified Capabilities

(none) — `workflow-authentication` changes only when the controller and step images switch to the
sidecar path (4.1d/4.1e).

## Impact

- **New**: `dws-mid-basic/` (Go module, Dockerfile, Makefile, tests, probe, docs),
  `.github/workflows/dws-mid-basic.yml`.
- **Edited**: `release-please-config.json`, `.release-please-manifest.json`,
  `.github/workflows/component-release.yml` (new choice), root `CLAUDE.md`,
  `docs/adr/0010-use-components-as-a-shared-store.md` (naming amendment),
  `docs/roadmaps/openworkflow-features.md` §4h.
- **Contract introduced** (consumed by 4.1d): image name, the in-image path `/basic.wasm`, and the
  guest-config JSON `{"endpoint","username","password"}`.
- **Dependencies**: `github.com/http-wasm/http-wasm-guest-tinygo` v0.4.0 and
  `golang.org/x/text` (NFC) in the guest; `github.com/http-wasm/http-wasm-host-go` in the host
  test only. Toolchain: TinyGo 0.34.0 in Docker (`tinygo/tinygo:0.34.0`).
