## Why

Roadmap Phase 4 (`docs/roadmaps/openworkflow-features.md`) is unblocked once Phase 3 lands:
`basic` / `bearer` / `oauth2` authentication and `use.secrets` are the last cross-cutting DSL
features standing between the current runtime and Phase 5's new call protocols and Phase 7's
catalogs, both of which need auth to reach real external services. Today every `call: http` and
`call: openapi` request goes out unauthenticated, and there is no way for a definition to name a
secret at all — the pinned OWS DSL 1.0 schema accepts `authentication` / `use.secrets`, but the
compiler and step images ignore them, so a definition that declares them "works" while silently
dropping the credential.

## What Changes

- **New DSL surface**: `document.use.secrets: [NAME, ...]` (names only) and
  `document.use.authentications.<name>: { basic | bearer | oauth2 }`, either inline on an endpoint
  or referenced via `endpoint.authentication.use: <name>`. Mirrors the existing inline-or-named
  `use.retries` / `use.timeouts` pattern (`CatchPolicy.resolvePolicy`).
- **StepService env gains `secretKeyRef` support**
  - From: `StepService.env` is `Map<String, String>` — literal values only, mounted by
    `StackSynthesizer.envVars()` as literal `EnvVar`s.
  - To: env entries may reference a K8s `Secret` by name, mounted as `EnvVarSource` /
    `secretKeyRef`. Literal entries continue to work unchanged.
  - Reason: secret **values** must never appear in the compiled `StepService`, the definition
    ConfigMap, or the controller's own request path.
  - Impact: non-breaking for existing steps (no auth declared → env is literal-only exactly as
    today).
- **`WorkflowCompiler` compiles authentication**: `httpStep` / `openApiStep` translate the
  resolved authentication into `AUTH_SCHEME` + `*_SECRET_REF` env entries. Only secret **names**
  cross the compile boundary; values never do.
- **`StackSynthesizer` synthesises Dapr resources for `oauth2`**: one `HTTPEndpoint` per
  unique external host+policy, one `oauth2clientcredentials` `Component`, and a `Configuration`
  attaching the middleware to the step sidecar's `appHttpPipeline` with a tightly scoped
  `pathFilter` (guards against `dapr/dapr#6658`, a bleed-into-pub/sub scenario that is not expected
  to hit these pods but is scoped against defensively).
- **Step images attach the credential**: `dws-call-http` and `dws-call-openapi` read
  `AUTH_SCHEME` + the mounted secrets at request time. `basic` / `bearer` set the `Authorization`
  header directly. `oauth2` routes the call through
  `localhost:<DAPR_HTTP_PORT>/v1.0/invoke/<httpendpoint-name>/method/<path>` — Dapr's sidecar
  middleware fetches and caches the token. Scope is `client_credentials` grant only.
- **Orchestrator exposes `$secrets` to `jq`**: `WorkflowRuntimeBootstrap` loads `SECRET_*` env
  once at startup into a `Map<String, String>` bound as `$secrets` on the `JqEvaluator`, so `set`
  and `switch` expressions can reference `$secrets.NAME`. Same load-once contract as the
  definition itself.

## Capabilities

### New Capabilities
- `workflow-authentication`: The DSL surface for declaring `basic` / `bearer` / `oauth2` on a
  `call: http` or `call: openapi` endpoint (inline or via `use.authentications`); how
  `dws-controller` compiles it into `StepService` env plus, for `oauth2`, a per-workflow
  `HTTPEndpoint` / `Component` / `Configuration` triad; and how `dws-call-http` /
  `dws-call-openapi` attach the credential — a direct `Authorization` header for
  `basic` / `bearer`, a Dapr sidecar-routed invocation for `oauth2`.
- `workflow-secrets`: The `document.use.secrets` allow-list; the invariant that secret **values**
  never appear in a definition, in a compiled `StepService`, or in the definition ConfigMap; and
  the orchestrator-side `$secrets.NAME` binding available to `set` / `switch` `jq` expressions.

### Modified Capabilities
_None._ Existing specs do not describe step-service env plumbing at the level this change
introduces (env-as-`secretKeyRef` is new behaviour, not a modification of an existing
requirement).

## Impact

- **Code**:
  - `dws-controller` — `compile/WorkflowCompiler.java` (auth compilation on `httpStep` /
    `openApiStep`), `model/StepService.java` (secret-ref-capable env representation),
    `k8s/StackSynthesizer.java` (`EnvVarSource` / `secretKeyRef` env; new synthesis for
    `HTTPEndpoint` / `Component` (`oauth2clientcredentials`) / `Configuration.appHttpPipeline`).
  - `dws-orchestrator` — `config/WorkflowRuntimeBootstrap.java` loads `SECRET_*` once at startup;
    `JqEvaluator` receives a `$secrets` binding.
  - `dws-call-http` — `internal/config/config.go` adds `AuthScheme` / `AuthUsername` /
    `AuthPassword` / `AuthToken`; `internal/runner/runner.go`'s `buildRequest` attaches the
    header or rewrites the URL to the sidecar invoke path.
  - `dws-call-openapi` — same contract wired into the `swagger-client` request builder.
- **Deployed resources**: `oauth2`-using workflows gain three new resource kinds per unique
  external host+policy: `HTTPEndpoint`, `Component` (`oauth2clientcredentials`), and
  `Configuration`. All carry the same `dws.io/*` labels as the rest of the workflow's stack so
  they are garbage-collected with their version.
- **Compatibility**: any definition that declares no `authentication` and no `use.secrets` is
  unaffected — the compile path is a no-op, no new resources are synthesised, and step-image
  behaviour is identical to today. A definition that already declares `authentication` in the DSL
  (currently silently ignored) starts having its credentials attached; if the referenced secret
  names aren't in `document.use.secrets` or don't exist as `Secret`s, the controller MUST reject
  the definition at compile time rather than deploy a broken stack.
- **Prerequisite (out of scope, sequenced)**: `openspec/changes/ows-phase3-errors-timeouts` must
  be archived before this change's spec deltas are applied, so
  `workflow-error-handling` / `workflow-timeouts` land in `openspec/specs/` in the right order.
- **Non-goals**: `oauth2` grant types other than `client_credentials`; a shared cluster-wide
  token-broker service (would fix cross-replica cache duplication but breaks the workflow-scoped,
  GC'd-by-version topology — revisit only on evidence of IdP rate-limit pressure); guarding
  reads on the controller / admin surfaces (a separate concern tracked in `dws-auth.md`); Phase 5
  and later.
