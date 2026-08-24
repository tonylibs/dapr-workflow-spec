## Context

See `proposal.md` — Why / What Changes for motivation and scope. This section covers only the
mechanics that shape the approach.

- **Every step already runs a Dapr sidecar.** The `workflow-access-policy` capability establishes
  that `CALL_HTTP` and `RUN_*` steps are dispatched via Dapr's activity mechanism, so their
  runtime pods always co-run a `daprd`. `CALL_OPENAPI` steps are HTTP-invoked and do not get a
  `WorkflowAccessPolicy` today, but they *do* run under Dapr for state / telemetry — the sidecar
  is available for the oauth2 middleware attachment too.
- **Compile-only, stateless controller.** `dws-controller` synthesises a whole stack per
  workflow version and never sees runtime traffic. Any per-request token cache MUST live where
  the request is issued (either the step image or the sidecar next to it), not in the controller.
- **Content-addressed, immutable definition storage.** The version SHA is over the canonicalised
  definition body. A secret **name** in that body is fine and stable; a secret **value** would
  make the version change every rotation. This is the other reason values never enter the
  definition path.
- **Existing inline-or-named `use.*` precedent.** `CatchPolicy.resolvePolicy` already resolves
  either an inline `retry` block or a `use.retries.<name>` reference on a `try` task; the same
  pattern is what `use.timeouts` follows. Authentication reuses that shape verbatim so authors
  aren't taught a third mechanism.
- **Existing env representation is literal-only.** `StepService.env` is `Map<String, String>`;
  `StackSynthesizer.envVars()` walks it and emits one literal-value `EnvVar` per entry. There is
  no room in this shape to say "this entry's value comes from a K8s `Secret`" — the shape has to
  grow before secrets can flow.

## Goals / Non-Goals

**Goals:**
- Every authenticated call goes out with the credential attached, whether the author wrote the
  scheme inline on the endpoint or via `use.authentications`.
- Secret **values** never appear in the definition body, the definition ConfigMap, the
  controller's request path, or the compiled `StepService`'s env manifest — only secret **names**
  do, and values enter each pod exclusively via K8s `Secret` → `secretKeyRef` → env.
- Zero behaviour change for any definition that declares no `authentication` and no
  `use.secrets`: same env shape, same synthesised resources, same step-image code path.
- One consistent DSL surface across `basic` / `bearer` / `oauth2`; the difference in wire
  mechanism (header vs. sidecar-routed invoke) is invisible to the definition author.
- Reuse Dapr's `oauth2clientcredentials` middleware rather than hand-rolling a token cache in each
  step image — one implementation to reason about, and no cross-language duplication between
  `dws-call-http` (Go) and `dws-call-openapi` (Node).

**Non-Goals:**
- `oauth2` grant types other than `client_credentials`. `authorization_code` / `password` /
  `refresh` need a user context or redirect flow neither step image has, and Phase 4's roadmap
  entry deliberately scopes to the machine-to-machine case.
- A cluster-wide token-broker service. Solves cross-replica cache duplication, but breaks the
  "workflow-scoped, GC'd by version" architecture and adds a sixth component; revisit only if
  per-sidecar cache duplication starts stressing an IdP's rate limit in practice.
- Guarding **reads** — who can `POST` to the controller or call the admin. That's a different
  concern tracked in `dws-auth.md` (console / admin login work).
- Changing the DSL surface for anything other than `authentication` / `use.secrets`. `input` /
  `output` / retry / timeout / raise / try / for / fork all keep their current shape.

## Decisions

### D1. DSL surface mirrors the existing inline-or-named `use.*` pattern
An endpoint's `authentication` accepts either the inline form
(`authentication: { basic: {...} }` / `{ bearer: {...} }` / `{ oauth2: {...} }`) or a named
reference (`authentication: { use: <name> }`) that resolves against
`document.use.authentications.<name>`. This is the same shape `CatchPolicy.resolvePolicy` already
handles for `use.retries` and the `try` timeout wiring uses for `use.timeouts`.

**Alternative considered**: named-only — force every author through `use.authentications` even
for a single one-off endpoint. Rejected — punishes the small case (one endpoint, one scheme) for
no benefit, and diverges from a pattern the same schema already establishes for two other
concerns.

**Alternative considered**: environment-only (skip the DSL, mount a `Secret` and let step images
read a magic env name). Rejected — the authenticated call becomes non-portable and non-auditable
from the definition alone; a reader can't tell whether an endpoint is authenticated or how
without inspecting cluster state.

### D2. Secret **values** never cross the compile boundary; only **names** do
A DSL value declared as `{ use.secret: NAME }` compiles to an env entry naming the K8s `Secret`
key (`AUTH_TOKEN_SECRET_REF=github-app:GITHUB_TOKEN`, meaning "read env `AUTH_TOKEN` from `Secret`
`github-app` key `GITHUB_TOKEN`"). `StackSynthesizer` reads the ref and emits a
`secretKeyRef`-backed `EnvVar`. The controller never reads a `Secret` value; the definition
ConfigMap only ever stores the name.

**Alternative considered**: controller reads from a `Secret` at compile time and inlines the
value into the `StepService`. Rejected — content-addressed versioning breaks (version SHA now
depends on a rotating value), and every compiled version is a leak surface (in-transit,
staged-request logs, controller memory).

**Alternative considered**: DSL carries a plaintext value the controller redacts before storing.
Rejected — same leak surface reached through a different door, plus the definition is no longer
authoritative for what a version does.

### D3. `basic` / `bearer` are direct-header, sidecar-free
The step image (`dws-call-http` / `dws-call-openapi`) reads `AUTH_SCHEME` plus the mounted
credential env at request time and sets `Authorization` on the outbound request directly. No
Dapr component is synthesised; no HTTPEndpoint is involved. `basic` mounts `AUTH_USERNAME` /
`AUTH_PASSWORD` (both `secretKeyRef`); `bearer` mounts `AUTH_TOKEN`.

**Alternative considered**: route `basic` / `bearer` through the sidecar too, for symmetry with
`oauth2`. Rejected — sidecar invoke adds one extra hop, one extra path for the middleware
`pathFilter` to reason about, and buys nothing (the token is not being fetched; it's already in
memory). Symmetry inside the step image is fine — `AUTH_SCHEME` selects the strategy — but
symmetry on the wire would cost latency for no gain.

### D4. `oauth2` is Dapr-native via `oauth2clientcredentials` middleware
For each unique (external-host, oauth2 policy) pair in a workflow, `StackSynthesizer` emits:

- one `HTTPEndpoint` naming the external host with a stable name derived from the host + policy
  hash (`ep-<sanitized-host>-<hash8>`);
- one `Component` of type `middleware.http.oauth2clientcredentials` (name derived the same way),
  referencing the IdP token URL and pulling `clientId` / `clientSecret` from their K8s `Secret`
  keys via the component's `secretKeyRef` metadata;
- one `Configuration` (per step, `cfg-<step-name>`) whose `appHttpPipeline` attaches the
  middleware with `pathFilter` narrowed to the specific `HTTPEndpoint`'s invoke path only.

The step image, when `AUTH_SCHEME=oauth2` and `AUTH_HTTPENDPOINT_NAME=<name>` are set, rewrites
the outbound URL to `http://localhost:${DAPR_HTTP_PORT}/v1.0/invoke/${AUTH_HTTPENDPOINT_NAME}/method/<path+query>`
and drops the direct `Authorization` header. Dapr's middleware fetches, caches, and injects the
token; the step image never sees it.

**Alternative considered**: hand-roll `client_credentials` in each step image (token endpoint
call, expiry cache, refresh on `401`). Rejected — two implementations (Go and Node) doing what
Dapr already ships tested; per-image caches per replica anyway; and every future auth-related
bug becomes two bugs.

**Alternative considered**: a shared cluster-wide token-broker service. Rejected explicitly (see
Non-Goals) — it fixes per-sidecar cache duplication but at the cost of a sixth component whose
lifecycle isn't owned by any one workflow version; the "GC'd with its version" story of the
whole stack breaks.

**Scope of `pathFilter`**: `dapr/dapr#6658` (closed-not-planned) notes that
`middleware.http.oauth2clientcredentials` attached at `appHttpPipeline` can bleed into pub/sub
endpoints on the same sidecar if `pathFilter` isn't scoped tightly. The step pods have no pub/sub
subscriptions, so the bleed can't hit anything in practice; nonetheless `pathFilter` narrows to
the specific `/v1.0/invoke/<httpendpoint-name>/method/*` prefix so any future capability that
does add a subscription to a step pod is safe by construction. The Dapr version pinned by
`charts/dws` is checked against the issue's discussion at implementation time.

### D5. `oauth2` HTTPEndpoint/Component/Configuration deduplication key
Two `oauth2` policies that point at the *same* external host with the *same* IdP + client
credentials share one triad; different hosts or different credentials get separate triads. The
dedup key is `sha256(host + issuer + client_id_secret_ref + client_secret_secret_ref +
scopes-sorted).substring(0, 8)`.

**Alternative considered**: one triad per policy declaration, no dedup. Rejected — an
`use.authentications` entry referenced by two endpoints on the same host would otherwise deploy
two identical middleware components, doubling the sidecar's cache footprint for no reason.

**Alternative considered**: dedup by policy name only (`authentications.<name>`). Rejected —
correctness gap when two endpoints on different hosts both `use:` the same policy name; each
`HTTPEndpoint` must name exactly one host, so name-based dedup would either misroute or reject a
valid definition.

### D6. Compile-time validation of secret references
`document.use.secrets: [NAME, ...]` is the allow-list; a `{ use.secret: X }` where `X` is not in
the allow-list fails compile with a clear diagnostic. This catches the typo before the deploy
loop. The controller does **not** attempt to verify the `Secret` object exists in the cluster at
compile time — that's a runtime concern (K8s will refuse to start the pod if the mount fails),
and coupling compile to cluster read state would defeat pure-function compilation.

**Alternative considered**: skip the allow-list, let any `use.secret` reference any name.
Rejected — the allow-list is DSL 1.0's stated intent and gives a definition reader one place to
see every secret a workflow can touch, matching the audit posture the rest of the design assumes.

### D7. Orchestrator exposes `$secrets` to `jq` via load-once at startup
`WorkflowRuntimeBootstrap` reads env matching `SECRET_*` once at startup (stripping the prefix
for the map key), populates a `Map<String, String>` on `WorkflowRuntimeState`, and passes it to
`JqEvaluator`, which exposes it as `$secrets`. A `set` / `switch` expression can now say
`$secrets.OKTA_CLIENT_ID`. This mirrors the definition load-once contract Phase 1 established.

**Alternative considered**: late-bind, re-reading env on every jq call. Rejected — env is
immutable inside a pod's lifetime, so re-reads buy nothing but throw away the load-once contract.

**Alternative considered**: push `$secrets` down as workflow input. Rejected — secrets would then
flow through the lifecycle-events pipeline (`workflow-access-policy`'s `dws.events`) and become
visible to `dws-admin`, exactly the leak the whole `secretKeyRef` approach exists to prevent.

### D8. `dws-call-openapi` reuses the exact same env contract
`dws-call-openapi` reads the same `AUTH_SCHEME` / `AUTH_USERNAME` / `AUTH_PASSWORD` / `AUTH_TOKEN`
/ `AUTH_HTTPENDPOINT_NAME` env vars as `dws-call-http`, wired into `swagger-client`'s request
builder (Node has no `net/http` equivalent, but `swagger-client` accepts a `requestInterceptor`
that gets the same job done). Ensuring both step images speak the same env contract keeps the
"one contract, three implementations" precedent from `run-step-configuration`.

## Risks / Trade-offs

- **[Risk] Per-sidecar oauth2 token-cache duplication under IdP rate limits.** Each replica of
  each step pod runs its own daprd with its own cache; a horizontally-scaled step whose IdP
  imposes tight rate limits could exhaust them on a scale-up burst. → Mitigation: accept for now
  (see Non-Goals); a cluster-wide token broker is a follow-up if this actually bites. Roadmap
  note in `dws-auth.md` covers this.
- **[Risk] `dapr/dapr#6658` — oauth2 middleware bleeding into pub/sub endpoints.** →
  Mitigation: `pathFilter` narrowed to the exact `/v1.0/invoke/<httpendpoint-name>/method/*`
  prefix, and step pods host no pub/sub subscriptions today. Verified against the Dapr version
  pinned by `charts/dws` at implementation time.
- **[Risk] A definition declaring `authentication` today (silently ignored) starts sending
  credentials the moment this ships.** → Mitigation: it's currently sending them *unauthenticated*
  and failing the call anyway; the compile-time allow-list check (D6) catches the case where the
  secret name isn't registered, so the failure mode is a clean compile error, not a runtime
  surprise.
- **[Trade-off] Two wire mechanisms (direct header vs. sidecar-routed) for the same DSL surface.**
  Definition authors don't see the difference, but operators debugging a specific request need
  to know which scheme routes where. → Mitigation: documented in the capability's spec; the
  scheme name (`AUTH_SCHEME=oauth2`) is visible in the pod's env for at-a-glance debug.
- **[Trade-off] `oauth2` steps deploy three extra Dapr resource kinds per unique host+policy.**
  → Mitigation: dedup by (host, credential-refs, scopes) hash (D5) keeps the count minimal;
  labels garbage-collect them with the version like everything else in the stack.
- **[Trade-off] `StepService.env` shape change ripples through every existing call site.** →
  Mitigation: the ripple is mechanical — every literal-value call site continues to work; new
  ref-value call sites live only in the auth compile path.

## Migration Plan

- **Prerequisite (out of scope of this change but blocks its spec-delta apply)**: archive
  `openspec/changes/ows-phase3-errors-timeouts` first, so `workflow-error-handling` /
  `workflow-timeouts` deltas land before this change's deltas.
- Single feature branch across four components; land in one release since the compile output and
  the step-image reader are two ends of the same env contract. There is no meaningful shim: an
  old step image reading a new `AUTH_*` env just falls into its existing "no auth" branch, and a
  new step image reading a compilation without `AUTH_*` set does the same.
- Rollback is a plain revert of the four components' images plus the controller-side changes to
  `WorkflowCompiler` / `StackSynthesizer`. Existing deployed stacks with `oauth2` triads will be
  garbage-collected by label the next time the definition is re-applied without auth compiled
  in; no cluster-side cleanup script is required.
- No persisted-state migration. Definitions are immutable and content-addressed; a definition
  written against Phase 4 semantics will have a different version SHA from any prior form, so
  there's no in-place upgrade path to reason about — old versions keep working with the old
  compiler, new versions get compiled with the new one, both coexist by design.
- **Rollout order across components**: (1) `dws-controller` (compilation + synthesis, no runtime
  impact until a v4-authored definition is deployed), (2) `dws-orchestrator` (`$secrets`
  binding — inert if no definition uses `$secrets.NAME`), (3) `dws-call-http` /
  `dws-call-openapi` (wire behaviour, inert if `AUTH_SCHEME` is unset). Any of the three can be
  bumped first; behaviour is only observable once a definition using auth is `POST`ed.

## Open Questions

- **Which K8s `Secret` naming convention?** Provisionally: authors are responsible for creating
  `Secret` `<workflow-name>` with keys matching the `document.use.secrets` names, but "one
  `Secret` per workflow vs. one per named auth policy" is worth confirming during
  implementation. The compile output can accept either shape without a spec change (both fit
  `secretKeyRef`).
- **Should `dws-call-openapi` also get a `WorkflowAccessPolicy`** now that it interacts with the
  sidecar for oauth2? Per `workflow-access-policy`, `CALL_OPENAPI` today gets none because it's
  HTTP-invoked from the orchestrator; oauth2's sidecar hop is *outbound from* the step, not
  *inbound to* it, so likely no policy change is needed. Confirm during implementation.
- **`pathFilter` regex flavour and exact anchor.** Dapr's middleware
  spec accepts a subset of Go's `regexp` syntax; the exact anchoring (`^` vs. bare prefix) needs
  a quick empirical check against the pinned Dapr version to make sure a narrow filter isn't
  silently matching more than expected.
