# Design

## Context

- ADR 0010 Decision 4a and roadmap §4h fix the mechanism: one Wasm guest per scheme, loaded by
  daprd's `middleware.http.wasm` Component from `file:///mnt/dws-wasm/<scheme>.wasm`, configured by
  `guestConfig` through a `secretKeyRef` to a controller-composed Secret (4.1d), delivered by an
  init container from a `busybox` image.
- The spike guest (`spikes/wasm-auth-middleware/guest/main.go`) is the starting point. It serves
  both schemes from a `scheme` field, matches one exact method path, logs every URI at info level,
  and panics with a fixed string on bad JSON.
- `http-wasm-guest-tinygo` v0.4.0 works only with **TinyGo 0.34.0**: newer TinyGo builds a module
  that fails at request time with `module closed with exit_code(0)` (spike finding). TinyGo 0.34.0
  ships with Go 1.23, so the guest module cannot use the repo's usual Go 1.26.
- 4.1c-bearer has not landed. There is no layout to match yet; this change sets it, and
  `dws-mid-bearer` should copy it.

## Goals / Non-Goals

**Goals:**

- Guest logic testable with plain `go test`, with no TinyGo or Wasm runtime needed.
- A test of the real `basic.wasm` built by TinyGo 0.34.0, running in the same http-wasm host
  library daprd embeds, so the TinyGo/SDK pairing is checked on every CI run.
- The layout, CI and release shape the bearer component can copy file for file.

**Non-Goals:**

- Anything the controller emits (4.1d), including the init container command and volume names.
- PRECIS profiles beyond NFC (see Decision 5).
- Supporting Dapr's header-based invocation (`dapr-app-id` header) or the gRPC pipeline.

## Decisions

### 1. Name: `dws-mid-basic`, binary `basic.wasm`

The ADR and roadmap use `dws-wasm-basic` as a *proposed* name, open to change in the 4.1c
proposals. The task names the component `dws-mid-basic` (and its sibling `dws-mid-bearer`). The
package directory, image, release-please component, CI workflow and tag prefix
(`dws-mid-basic-v*`) all use it. The binary keeps the ADR's `basic.wasm`, so 4.1d's
`url: file:///mnt/dws-wasm/basic.wasm` is unchanged. Commit scope: `mid-basic` (directory minus
`dws-`). The ADR gets a dated amendment line for the name; the roadmap row is updated.

### 2. Layout: thin `main.go`, pure `internal/basicauth`, separate host-test module

```
dws-mid-basic/
  go.mod                 # go 1.23; guest SDK + x/text only
  main.go                # TinyGo entry: read config, register handler, set the header
  internal/basicauth/    # Config parse/validate, Match(uri), HeaderValue(); no SDK import
  wasmtest/              # own go.mod: http-wasm-host-go + wazero; runs ../bin/basic.wasm
  scripts/verify-live.sh # basic-only live probe (Decision 9)
  Dockerfile Makefile version.txt README.md CLAUDE.md
```

`internal/basicauth` holds every rule in the spec and is covered by table tests run with the
host Go toolchain. `main.go` only wires it to the SDK, so the one piece plain `go test` cannot
reach is exercised by `wasmtest`. The host test lives in its own module so its large
dependencies (wazero) never enter the guest module that TinyGo resolves inside the Docker build.

Alternative considered: one `main.go` like the spike. Rejected: the SDK's host imports only link
under TinyGo, so nothing in it would be unit-testable.

### 3. Header: set, replacing a caller value

The task and the ADR table say the guest "appends" the header; the ADR's normative *Guest
behavior* paragraph says it sets `Authorization`, "overwriting any caller-supplied value", and the
spike's probe asserts the overwrite. With no caller header (the normal case once 4.1e makes step
images drop it) both give the same request. With one, a true append yields two `Authorization`
headers and the target may honor the caller's. The guest therefore uses `Headers().Set`: the
forwarded request always carries exactly one `Authorization: Basic …`. **Deviation from the task's
wording, reported.**

### 4. Missing or invalid credentials: refuse to start

Validation runs once, in `main`, before the handler is registered. On any error the guest panics
with a message that names the field and the rule (`dws-mid-basic: password is required`) and
never a value; for a JSON syntax error it says only that the config is not valid JSON, because
`encoding/json` errors quote the offending input character. Rules:

| Field | Rule |
|---|---|
| any | config must be one JSON object; unknown fields rejected (catches `scheme`, `token`, typos) |
| `endpoint` | non-empty; no `/`, `?`, `#` (it is a Dapr `HTTPEndpoint` name) |
| `username` | non-empty; no `:` (RFC 7617: a user-id with a colon is invalid); no control character |
| `password` | non-empty; no control character (RFC 7617) |

All errors are collected and reported together so one fix round suffices.

Why at load: a guest that cannot authenticate should make the sidecar visibly broken, not let the
step's call reach the target without credentials. A per-request `401`/`500` response from the
guest was considered: it is quieter (the pod looks healthy) and needs a second code path. If the
module does start anyway (host behavior we do not control), the handler is never registered and
http-wasm fails every request rather than forwarding it — fail closed either way.

Empty password is rejected although RFC 7617's grammar allows it: the controller composes the
config from operator Secrets, and an empty value there is far more likely a missing key than an
intended empty password. **Decision reported.** If an empty password turns out to be needed, it
becomes an explicit field later, not a silent default.

What daprd does exactly when a Wasm middleware fails to initialize (sidecar exits vs. component
skipped with an error) is checked by the live probe's negative case (Decision 9) and recorded in
the README.

### 5. Encoding: NFC, then UTF-8, then padded standard base64

RFC 7617 leaves the charset unspecified unless the server sends `charset="UTF-8"`, which is the
only value it defines, and then expects NFC + UTF-8. The guest never sees the challenge, so it
always uses the defined form: `norm.NFC.String` (from `golang.org/x/text/unicode/norm`) on each
part, join with `:`, base64 `StdEncoding`. Go strings from `encoding/json` are already UTF-8.
Test vectors include the RFC's own example (`test`/`123£` → `dGVzdDoxMjPCow==`), a password with
several colons, and a decomposed `é`.

The full PRECIS profiles (`UsernameCasePreserved`, `OpaqueString`: width mapping, disallowed code
points) are not applied — they would add size for no current consumer. **Deviation reported.**

Fallback if `x/text` does not build under TinyGo 0.34.0 or grows the binary unreasonably (budget:
under 1 MiB): drop normalization, encode verbatim UTF-8, and record it in the README and the
report. The tests for colon and non-ASCII still apply.

### 6. Path match

Match the path part of `GetURI()` (split at the first `?`) against
`/v1.0/invoke/<endpoint>/method`, exactly or followed by `/`. The boundary check stops `pets`
from matching `pets-v2`. A path with a `.`/`..` segment, raw or as `%2e`, does not match: if daprd
cleaned such a path after the guest it could route to another endpoint with our credential.
Not matching is the safe direction — the request goes out unauthenticated, never with a credential
for the wrong target. The spike's exact single-method `path` field is dropped: the ADR scopes the
credential to the endpoint, and 4.1d's config has no path.

### 7. Logging: none per request

The guest logs nothing on the request path. The spike's info-level URI log is removed (a URI is
not a credential, but it is noise at default level and a habit that leaks easily). The only text
the guest emits is the load-time error of Decision 4. A test asserts that no error message
contains any of the test credential strings.

### 8. Build, image, CI, release

- **Dockerfile**: `tinygo/tinygo:0.34.0` build stage
  (`tinygo build -o /basic.wasm -scheduler=none --no-debug -target=wasi .`, the spike's working
  flags), then `busybox:1.37.0` with only `/basic.wasm`. The build stage needs module downloads
  (go.sum committed, no `go mod tidy` in the image, unlike the spike).
- **Makefile**: `lint` (go vet + gofmt, as the other Go packages), `test` (`go test ./...` on the
  guest module), `wasm` (Docker build, extracts `bin/basic.wasm`), `test-wasm` (runs `wasmtest`
  against it). Gate: `make lint && make test`, plus `make test-wasm` when Docker is available.
- **CI** `.github/workflows/dws-mid-basic.yml`, shaped like `dws-call-http.yml`: job `test`
  (vet, gofmt, unit tests with Go 1.23); job `wasm` (Docker build of the build stage, host test,
  print `basic.wasm` size); job `build-image` (needs both; push on `main`, on
  `dws-mid-basic-v*` tags and on `workflow_dispatch`; tags `main`, short sha, `latest`, version).
  The job summary prints the pushed digest.
- **Release**: release-please package `dws-mid-basic` (`simple`, `version.txt`,
  `skip-github-release: true`); manifest entry `0.0.0`, so the first `feat(mid-basic)` opens a
  `0.1.0` release PR; `component-release.yml` gains the choice. Released by hand through
  `component-release` like every component. This differs from the `dws-step`/`dws-flow` bootstrap
  (manifest `0.1.0` + `force=true`) because this package has no pre-existing history to trim; the
  release-please behavior is checked with a dry run before merge (task 4.3).
- **Public**: GHCR packages linked to this repository inherit its visibility; the first push is
  checked and, if needed, flipped to public in the package settings (a manual step).

### 9. Live probe: `dws-mid-basic/scripts/verify-live.sh`

The spike's `verify.sh` cannot run this image unchanged: it builds the spike's dual-scheme guest,
writes a `scheme` field, loads `auth.wasm`, and its bearer checks need a bearer guest. The new
script keeps its structure (isolated namespace, image import into Docker Desktop nodes or a given
`IMAGE=` pulled by digest, daprd pinned to 1.18.1, echo target, EXIT cleanup) and checks basic
only: header at the target for `alice`/`s3cret`, a password with `:`, a non-ASCII credential;
other path and other endpoint get no header; a caller header is replaced; no credential in daprd
logs; and a negative case with a missing password showing that no unauthenticated request reaches
the echo target. **Deviation reported**: "the spike probe passes" is met by this derived probe,
not the spike file itself, which stays frozen as the spike's record. Running it on Tony's cluster
is a manual step; 4.1h later promotes a maintained probe to `scripts/`.

## Risks / Trade-offs

- [TinyGo 0.34.0 / SDK pairing breaks on a base image update] → both pinned; `wasmtest` runs the
  real module on every CI run and fails on `exit_code(0)`.
- [`x/text` too large or unsupported in TinyGo 0.34.0] → Decision 5 fallback; size printed in CI.
- [daprd keeps running with a failed middleware instead of exiting] → still fail-closed (no handler
  → request rejected by the host); probe negative case records the actual behavior.
- [Go 1.23 in this package while the repo uses 1.26] → forced by TinyGo 0.34.0; noted in the
  package `CLAUDE.md` so nobody "fixes" it.
- [Stray release-please PR at merge] → dry run before merge; if one appears anyway, close it and
  seed with `component-release` `force=true` per `docs/release-process.md`.
- [Image published private by default] → manual visibility check after the first push.

## Migration Plan

Additive; nothing consumes the image until 4.1d. Rollback is deleting the package, workflow and
release entries; the published image can stay unused.

## Open Questions

- The exact daprd reaction to a guest that panics at load (Decision 4) — measured by the probe, it
  changes only what the README says, not the guest.
