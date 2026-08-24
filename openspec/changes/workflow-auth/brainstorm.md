# Brainstorm: OWS Phase 4 — Authentication + Secrets

Raw capture. Design decisions were pre-adjudicated in an out-of-session handoff; this file records
them as a decision log so `proposal.md` / `design.md` can extract from a single source. The
brainstorming skill wasn't re-run in-session because every fork it would have explored is already
labelled below with the chosen branch and the branches rejected.

## Background

- Roadmap Phase 4 (`docs/roadmaps/openworkflow-features.md`, §4): `basic` / `bearer` / `oauth2`
  authentication on `call: http` / `call: openapi` endpoints, plus DSL `use.secrets` resolution.
- Phase 4 is unblocked once Phase 3 (`openspec/changes/ows-phase3-errors-timeouts`) lands. Phase 3
  is code-complete (147/147 tests green) but its openspec change is not yet archived — the roadmap
  page still lists it as "next up". Archival of Phase 3 is a hard prerequisite of this change's
  spec deltas so both changes land in `openspec/specs/` in an order that keeps requirement
  history coherent.
- Four components touch the auth surface:
  - `dws-controller` — DSL compilation, K8s / Dapr resource synthesis.
  - `dws-orchestrator` — the interpreter that would resolve `$secrets.NAME` inside a `jq`
    expression.
  - `dws-call-http`, `dws-call-openapi` — the two prebuilt step images that actually issue the
    outbound HTTP call and need to attach the `Authorization` header (or route through the sidecar
    for oauth2).

## Decision log

### Q1. DSL surface: how do authors declare an authentication scheme?

**Chosen**: mirror the existing inline-or-named `use.*` pattern already established for
`use.retries` and `use.timeouts` (see `CatchPolicy.resolvePolicy`).

```yaml
document:
  use:
    secrets: [GITHUB_TOKEN, OKTA_CLIENT_ID, OKTA_CLIENT_SECRET]
    authentications:
      github-app:
        bearer:
          token: { use.secret: GITHUB_TOKEN }
      okta-svc:
        oauth2:
          authority: https://okta.example/oauth2/default
          grant: client_credentials
          client:
            id:     { use.secret: OKTA_CLIENT_ID }
            secret: { use.secret: OKTA_CLIENT_SECRET }
do:
  - fetchIssues:
      call: http
      with:
        endpoint:
          uri: https://api.github.com/…
          authentication:
            use: github-app          # named reference
  - postMetric:
      call: http
      with:
        endpoint:
          uri: https://metrics.example/v1/…
          authentication:            # inline
            basic:
              username: { use.secret: OKTA_CLIENT_ID }
              password: { use.secret: OKTA_CLIENT_SECRET }
```

**Rejected alternatives**:
- Named-only (force every scheme through `use.authentications`): kills ergonomics for one-off
  endpoints and would diverge from the existing `use.retries` / `use.timeouts` precedent.
- Environment-only (skip the DSL, mount a `Secret`, let the step image read a magic env name): the
  authenticated call becomes non-portable — you can't reason about a workflow's outbound auth from
  the definition alone.

### Q2. Where do secret **values** live?

**Chosen**: never in the definition, never in compiled `StepService` env as plaintext. Only secret
**names** flow through `WorkflowCompiler`; values live in cluster K8s `Secret`s and are mounted at
deploy time via `EnvVarSource` / `secretKeyRef`. The DSL surface for consuming one is
`{ use.secret: NAME }`, and the definition-level `document.use.secrets: [NAME, ...]` is the allow-list
of names a definition is permitted to reference.

**Rejected alternatives**:
- Plaintext in the DSL, controller redacts before storing: still leaks through `POST` bodies, request
  logs, and any in-transit staging.
- Controller reads from a secret store at compile time and inlines: same leak, plus binds the
  compiled version to the value at that instant — content-addressed versioning breaks the moment a
  secret rotates.

### Q3. How does `basic` / `bearer` reach the wire?

**Chosen**: trivial and sidecar-free. `WorkflowCompiler` compiles an `AUTH_SCHEME` env plus the
required `*_SECRET_REF` names onto the `StepService`. `StackSynthesizer` mounts each `*_SECRET_REF`
as a `secretKeyRef`-backed env var. The step image (`dws-call-http`, `dws-call-openapi`) reads the
env at request time and sets the `Authorization` header. No Dapr components involved; no cross-pod
state.

### Q4. How does `oauth2` (`client_credentials`) reach the wire?

**Chosen**: Dapr-native, not hand-rolled. Every `CALL_HTTP` / `RUN_*` step already runs a Dapr
sidecar (see the `workflow-access-policy` capability). Rather than build a token-cache into every
step image, use Dapr's built-in
`middleware.http.oauth2clientcredentials` attached to a per-workflow-scoped `Configuration`'s
`appHttpPipeline`, and expose the external target as a `HTTPEndpoint` resource. The step image
routes oauth2-scheme calls through
`localhost:<DAPR_HTTP_PORT>/v1.0/invoke/<httpendpoint-name>/method/<path>` instead of the raw
`ENDPOINT`; `basic` / `bearer` calls hit the raw endpoint unchanged.

Scope: `client_credentials` grant only.

**Rejected alternatives**:
- Hand-roll `client_credentials` in each step image: two implementations to maintain
  (`dws-call-http` in Go and `dws-call-openapi` in Node), plus a per-replica cache that duplicates
  what Dapr already has.
- **Deferred** — Introduce a fifth service, a cluster-wide token broker: fixes cross-replica cache
  duplication, but breaks the "workflow-scoped, GC'd by version" architecture (a broker isn't owned
  by any one workflow version) and adds a whole component to the topology. Revisit only if
  per-sidecar cache duplication starts stressing an IdP's rate limit in practice.

### Q5. How does the orchestrator expose secret **values** to `jq` expressions?

**Chosen**: `dws-orchestrator`'s `WorkflowRuntimeBootstrap` reads `SECRET_*` env once at startup
(same load-once contract as the definition itself), strips the prefix, and populates a
`Map<String, String>` exposed to `JqEvaluator` as `$secrets`. A `set` / `switch` expression can
then reference `$secrets.NAME`.

**Rejected alternatives**:
- Late-bind by re-reading env on every jq call: throws away Phase 1's load-once contract and buys
  nothing (env is immutable inside a pod's lifetime).
- Push `$secrets` down as workflow input: pollutes the audit trail (secrets would then appear as
  input events) and makes the value visible to `dws-admin` via lifecycle events.

### Q6. Explicitly deferred

- **Grant types other than `client_credentials`.** Phase 4 draws the line here; adding
  `authorization_code` / `password` / `refresh` requires a redirect flow or user context neither
  step image has today. Revisit if a real workflow needs it.
- **Guarding reads (console/admin login).** This is a different concern tracked in `dws-auth.md` —
  who can call `POST /workflows/…` on the controller / admin. Not related to what a running
  workflow's outbound calls carry.
- **A shared cluster-wide token-broker service.** See Q4's rejected alternative — revisit only on
  evidence of IdP rate-limit pressure from per-sidecar cache duplication.

## Known risk to scope around

`dapr/dapr#6658` (closed-not-planned): `oauth2` middleware on `appHttpPipeline` can bleed into
pub/sub endpoints on the same sidecar if `pathFilter` isn't scoped tightly. Not expected to hit
`CALL_HTTP` / `RUN_*` pods (no pub/sub subscriptions there), but scope `pathFilter` tightly
regardless and verify against the Dapr version `charts/dws` pins.

## Concrete touch points (from handoff)

- `dws-controller/src/main/java/io/dws/controller/compile/WorkflowCompiler.java` —
  `httpStep` / `openApiStep` gain `authentication` compilation.
- `dws-controller/.../model/StepService.java` — needs a secret-ref-capable env representation
  (today's `Map<String, String>` env is literal-only).
- `dws-controller/.../k8s/StackSynthesizer.java` — `envVars()` only emits literal `EnvVar`; add
  `EnvVarSource` / `secretKeyRef` support, plus new synthesis for `HTTPEndpoint` /
  `Component` (`oauth2clientcredentials`) / `Configuration.appHttpPipeline`.
- `dws-orchestrator/.../config/WorkflowRuntimeBootstrap.java` — load `SECRET_*` env once at
  startup into a map exposed to `JqEvaluator` as `$secrets`.
- `dws-call-http/internal/config/config.go`, `internal/runner/runner.go` — `AuthScheme` /
  `AuthUsername` / `AuthPassword` / `AuthToken` fields; `buildRequest` sets `Authorization` header
  or routes through the sidecar invoke path for oauth2.
- `dws-call-openapi` — same contract, wired into the `swagger-client` request builder.

## Suggested test coverage (from handoff)

- Compiler: inline vs. `use:`-named auth resolution; secret-name-only compilation (assert no
  plaintext ever appears in `StepService` env or the definition ConfigMap).
- `StackSynthesizer`: `secretKeyRef` env shape; `HTTPEndpoint` / `Component` / `Configuration`
  synthesis for oauth2.
- `dws-call-http` / `dws-call-openapi`: unit tests for header attachment (basic / bearer);
  integration test against a mock `client_credentials` IdP for the oauth2 sidecar-routed path.
- Orchestrator: `$secrets.NAME` resolves in `set` / `switch` jq expressions.
