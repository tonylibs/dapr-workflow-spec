# OWS Phase 4 — Authentication + Secrets

## Background

Phase 3 (RFC 7807 errors and task/workflow timeouts) is merged in commit
`264316f8`, but `docs/roadmaps/openworkflow-features.md` still shows it as the
next phase. Phase 4 must correct that roadmap state and introduce authentication
and secret resolution for controller-compiled HTTP and OpenAPI calls without
placing plaintext credential material in workflow definitions, ConfigMaps, or
the controller's compiled step-service environment model.

This is an architectural change: it adds DSL contracts, typed deployment
metadata, Kubernetes Secret projections, Dapr resources, and runtime behavior
across `dws-controller`, `dws-orchestrator`, `dws-call-http`, and
`dws-call-openapi`.

## Decision chain

### Q1 — How does a declared DSL secret map to Kubernetes?

The Open Workflow DSL defines `use.secrets` as a list of logical names and
exposes resolved secrets through the `$secrets` map. It does not prescribe a
backing secret store nor a Kubernetes Secret data-key convention. Its basic-auth
example can reference a logical secret, but storage shape remains runtime-owned.

**Decision:** DWS adopts a scalar-secret convention. Every `use.secrets` entry
`NAME` maps to Kubernetes `secretKeyRef { name: NAME, key: value }`, and is
addressed as `$secrets.NAME`. Basic auth uses two declared scalar secrets (user
and password); bearer auth uses one; OAuth2 client credentials use the declared
client-id and client-secret names. This keeps the definition name-only and makes
every mounted value explicit and auditable.

### Q2 — How should OAuth2 client credentials be performed?

Three approaches were compared:

1. **Dapr-native, per endpoint policy** — synthesize version-scoped
   `HTTPEndpoint`, OAuth2 middleware Component, and Configuration resources;
   route the step runner through its local Dapr sidecar.
2. **Shared token broker** — a cluster service shares tokens across replicas.
3. **Runner-managed OAuth2** — the Go and TypeScript runners implement token
   acquisition and caching themselves.

**Decision:** use option 1. It preserves the workflow-version lifecycle and
keeps token handling out of application images. A resource name derives from the
external host plus canonicalized policy content, allowing equivalent policies to
share a resource within one workflow version. Only `client_credentials` is in
scope. The broker and runner-managed approaches are deferred.

### Q3 — Which Dapr documentation and compatibility baseline apply?

The current design references the latest Dapr `HTTPEndpoint` and OAuth2
client-credentials middleware documentation. Those references confirm scoped
external endpoint invocation, secret-backed endpoint headers, and regex
`pathFilter` support for least-privilege middleware application. The repository
previously pinned Dapr Helm chart `1.15.4`.

**Decision:** use the latest Dapr docs as the normative design reference and upgrade
`charts/dws` to stable Dapr Helm chart `1.18.1`, which implements `pathFilter`.
The Dapr OAuth integration suite remains version-parameterizable and defaults to
1.18.1. The generated middleware configuration must apply a tightly scoped
`pathFilter` and prove that the token reaches only the selected external invocation
path. This scopes the known `appHttpPipeline` pub/sub bleed risk from dapr/dapr#6658.

### Q4 — Where may `$secrets` be used?

Current Open Workflow guidance cautions that `$secrets` should be limited to
`input.from` to prevent accidental leakage. The Phase 4 handoff explicitly
requires `$secrets.NAME` in `set` and `switch` expressions.

**Decision:** follow the handoff. DWS will make `$secrets` available to `set`
and `switch` and document this as a DWS-specific extension for later review.
Tests and documentation must warn that those expressions can expose secret
material.

## Approved design

- `document.use.secrets` declares scalar secret names; auth policies are inline
  or reusable under `use.authentications`, and endpoint auth can inline a policy
  or reference one by name.
- The controller resolves inline/named policies and emits typed secret-reference
  values. It never serializes credential values into `StepService`, ConfigMaps,
  or Dapr resource literals.
- `StackSynthesizer` projects typed values through Kubernetes `secretKeyRef` to
  the orchestrator and only the runners that require them. The orchestrator
  loads `SECRET_*` values once at bootstrap and supplies them to jq as
  `$secrets`.
- Basic/bearer runners construct the `Authorization` header from injected
  values. OAuth2 runners invoke the synthesized Dapr external endpoint through
  their local sidecar and do not retrieve or cache tokens.
- The controller does not gain permission to read Secrets. Missing Secret/key
  references fail at Kubernetes workload startup; this rollout constraint is
  documented rather than weakened with broader controller RBAC.
- Existing standalone `dws-call-openapi` API-key and secret-store behavior is
  retained for compatibility. Controller-generated workflows use only the new
  secret-reference environment contract.
- Phase 3 status is corrected before Phase 4 work begins.

## Test and rollout decisions

- Compiler tests cover inline/named policy resolution, declaration validation,
  unsupported OAuth grants, and the absence of plaintext in compiled output.
- Synthesizer tests cover `secretKeyRef`, scoped `HTTPEndpoint`, OAuth Component,
  and Configuration resources.
- Go and TypeScript tests cover basic/bearer header construction and OAuth2
  sidecar invocation shape.
- Orchestrator tests cover `$secrets.NAME` in `set` and `switch` and label the
  behavior as the DWS extension above.
- A mock-IdP integration suite validates the Dapr path-filter behavior against
  the default 1.18.1 runtime and is parameterized for later Dapr upgrades.

## Explicit deferrals

- A shared token broker.
- OAuth grants other than `client_credentials`.
- Upgrading past the Phase 4 Dapr 1.18.1 baseline and enlarging its integration-test matrix.
- Console/admin authentication and read guards, which belong to `dws-auth.md`.
