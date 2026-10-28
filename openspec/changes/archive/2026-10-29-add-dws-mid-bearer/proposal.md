# Proposal

## Why

ADR 0010 Decision 4a moves `basic`/`bearer` credentials out of the step images and into a Wasm HTTP
middleware on the step's Dapr sidecar, one component per scheme. The spike
(`spikes/wasm-auth-middleware/`, 9/9 checks) proved the mechanism with one binary that switched on a
`scheme` field. Roadmap Phase 4.1c-bearer ships the production **bearer** component so the controller
(4.1d) has something to wire and pin. It is independent of 4.1c-basic, which runs in parallel.

## What Changes

- New top-level package `dws-mid-bearer`: a TinyGo Wasm guest for Dapr's `middleware.http.wasm`.
  - Reads `{ "endpoint": ..., "token": ... }` from its `guestConfig`. No `scheme` field, no other
    credential fields.
  - For a request on its own endpoint's invocation path (`/v1.0/invoke/<endpoint>/method/...`) it sets
    `Authorization: Bearer <token>`, replacing any caller-supplied value. Every other request is passed
    through untouched.
  - **Fails closed at load.** A `guestConfig` with a missing, empty or malformed token (or endpoint),
    or an unknown field, makes the module panic during init. daprd then refuses to load the component
    and no call is sent without credentials. The token never appears in a log line or error text.
- A small public GHCR image `ghcr.io/tonylibs/dws-mid-bearer` (busybox base) whose only job is to carry
  `bearer.wasm` for an init container to copy into a shared volume.
- CI workflow `dws-mid-bearer.yml` (vet, gofmt, tests, image build, push off-PR), a `release-please`
  package with `skip-github-release`, and a `component-release` choice. Releases stay manual.
- A bearer-only live probe derived from the spike's `verify.sh`, run against the built image.
- **Deviations from the written plan** (carried into the report): the component is named
  `dws-mid-bearer`, not the ADR's proposed `dws-wasm-bearer`, so ADR 0010 and the roadmap are updated to
  match; the guest has no `path` field (see design D2).

## Capabilities

### New Capabilities

- `wasm-bearer-middleware`: the observable contract of the bearer Wasm guest and its delivery image —
  guestConfig shape, which requests receive the header, pass-through, fail-closed config validation,
  no credential leakage, and the image/file layout the controller's init container relies on.

### Modified Capabilities

None. No existing spec changes: the controller does not emit this component yet (4.1d), step images are
unchanged (4.1e), and no DSL behavior moves.

## Impact

- **Owning component:** `dws-mid-bearer` (new; Go module built with TinyGo 0.34.0, independent build
  boundary, own gate `make lint && make test`).
- **Repo files touched:** `release-please-config.json`, `.release-please-manifest.json`,
  `.github/workflows/component-release.yml`, root `CLAUDE.md` (package map and gate table),
  `docs/release-process.md`, ADR 0010 and `docs/roadmaps/openworkflow-features.md` (name, 4.1c-bearer
  status, measured Wasm size).
- **Deployed behavior:** none until 4.1d. The image is built and published but nothing references it.
- **Runtime interpretation / DSL:** no change. Existing workflow definitions are unaffected.
- **Contract for later phases:** 4.1d consumes the guest config shape, the `file:///mnt/dws-wasm/bearer.wasm`
  naming and the image's `/bearer.wasm` location; it must pin the image by digest and must not set
  `ignoreErrors: true` on the Wasm Component (design D4).

## Non-goals

- `dws-mid-basic` (4.1c-basic), controller wiring (4.1d), step-image changes (4.1e), Knative
  `config-features` (4.1g), promoting the probe to `scripts/` (4.1h).
- Matching the `dapr-app-id` header form of invocation; only the `/v1.0/invoke/<endpoint>/method/` form
  that step images use.
- gRPC (the HTTP pipeline does not see it), OAuth2, token rotation or refresh, and reading the token from
  anywhere other than `guestConfig`.
- Cutting a release or flipping the GHCR package to public in code; both are manual steps reported at the
  end.
