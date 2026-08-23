## Context

DWS compiles Open Workflow definitions into immutable, content-addressed workflow versions and
dedicated step services. Phase 3 is merged but still shown as pending in the roadmap. Phase 4 adds
the missing protected-service path across the controller, orchestrator, Go HTTP runner, and
TypeScript OpenAPI runner.

The Open Workflow DSL treats `use.secrets` as logical names and leaves backing-store details to
runtimes. DWS must retain its least-privilege controller RBAC and must never serialize secret
values into definitions, ConfigMaps, or compiled plan literals. The latest Dapr documentation is
the normative resource reference, and `charts/dws` upgrades to stable Dapr 1.18.1 so the required
OAuth middleware `pathFilter` is available.

## Goals / Non-Goals

**Goals:**

- Support declared scalar secrets and `$secrets.NAME` runtime values.
- Support inline and named basic, bearer, and OAuth2 `client_credentials` policies on HTTP and
  OpenAPI calls.
- Make basic/bearer runner-local and OAuth2 Dapr-native, with resource lifetime bounded by the
  workflow version.
- Preserve existing workflows and standalone runner compatibility.

**Non-Goals:**

- A shared token broker, other OAuth grants, and console/admin login or read guards.
- Controller access to read or validate Secret values.
- Digest, OIDC, or API-key DSL authentication policies.

## Decisions

### D1: Scalar Kubernetes Secret convention

- **Choice:** A declared logical name `NAME` maps to
  `secretKeyRef { name: NAME, key: value }` and is exposed as `$secrets.NAME`. Basic policies use
  two scalar names, while bearer uses one and OAuth2 client credentials use two.
- **Rationale:** The DSL declares names, not a Kubernetes representation. A one-value convention
  is unambiguous, auditable, and lets each value be mounted via standard `secretKeyRef`.
- **Alternatives considered:** Structured Secret payloads were rejected because they invent an
  undocumented shape and complicate jq and runner contracts.

### D2: Typed secret-reference deployment model

- **Choice:** Replace literal-only `StepService` environment values with a typed literal-or-secret
  representation. `StackSynthesizer` emits literals as `EnvVar.value` and secrets as
  `EnvVar.valueFrom.secretKeyRef`. The orchestrator receives `SECRET_<NAME>` once at startup;
  runners receive only the auth values they need.
- **Rationale:** It keeps plaintext outside controller-owned objects while preserving current
  deployment shape and content-addressed versioning.
- **Alternatives considered:** Reading Secret values in the controller would require broader RBAC
  and risks leakage. A Dapr secret-store lookup in every runner duplicates resolution behavior.

### D3: Authentication resolution and runner contract

- **Choice:** Resolve endpoint authentication inline or by `use.authentications` name. Validate
  declared references, reject literals in controller-managed credential fields, and compile a
  normalized `AUTH_SCHEME` plus secret-reference env contract. Basic/bearer runners assemble the
  `Authorization` header from those injected values.
- **Rationale:** This follows the DSL's reusable-component pattern while making resolution a pure
  compiler responsibility.
- **Alternatives considered:** Passing policy fragments or plaintext credentials to runners would
  enlarge their contract and leak sensitive material.

### D4: Dapr-native OAuth2 client credentials

- **Choice:** For each canonical external-host plus OAuth policy, synthesize a scoped
  `HTTPEndpoint`, `middleware.http.oauth2clientcredentials` Component, and Configuration. The
  runner invokes the endpoint via its local Dapr sidecar; it neither retrieves nor caches tokens.
- **Rationale:** Dapr owns token acquisition and caching while generated resources remain
  workflow-version-scoped and garbage-collectable.
- **Alternatives considered:** A shared token broker adds a component and cross-version lifecycle;
  runner-managed OAuth2 duplicates security-critical behavior across Go and TypeScript.

### D5: OAuth path scope and Dapr compatibility

- **Choice:** Generate a narrow regex `pathFilter` for the invoked external path and scope Dapr
  resources to the requesting step app ID. Add a version-parameterized mock-IdP integration suite
  whose default is Dapr 1.18.1.
- **Rationale:** The latest middleware documentation makes path filtering the least-privilege
  mechanism. It constrains the known `appHttpPipeline`/pub-sub bleed risk while retaining a
  repeatable validation path for later Dapr upgrades.
- **Alternatives considered:** A broad or unfiltered pipeline is rejected because it can affect
  unrelated sidecar traffic; retaining Dapr 1.15.4 is rejected because it lacks `pathFilter`.

### D6: DWS `$secrets` expression extension

- **Choice:** Bind `$secrets` in `set` and `switch` as well as the usual expression path, and
  document that availability as DWS-specific.
- **Rationale:** The Phase 4 requirement explicitly needs it despite upstream guidance to confine
  secret access more narrowly.
- **Alternatives considered:** Enforcing the upstream restriction would violate the approved
  Phase 4 behavior. The trade-off is accepted with explicit documentation and tests.

## Risks / Trade-offs

- **[Missing Secret/key]** Kubernetes prevents the affected workload from starting rather than the
  controller returning a runtime authorization error → Mitigation: document the operator contract;
  do not weaken controller RBAC.
- **[OAuth middleware scope]** Dapr middleware can affect unintended sidecar paths → Mitigation:
  strict `pathFilter`, step-app scopes, and mock-IdP integration tests.
- **[Dapr version drift]** Latest Dapr documentation can diverge from the 1.18.1 baseline →
  Mitigation: retain that default test target and parameterize future version runs.
- **[Secret leakage]** `$secrets` in `set`/`switch` can move material into workflow data →
  Mitigation: label the behavior as a DWS extension and warn authors in docs/tests.
- **[Per-sidecar token caches]** OAuth tokens are duplicated across replicas → Mitigation: accept
  until real IdP rate-limit pressure justifies the deferred broker.

## Migration Plan

1. Update the roadmap to mark Phase 3 complete and document Phase 4's scalar-secret convention.
2. Ship compiler and synthesizer support with unit coverage before runner wiring.
3. Upgrade the in-chart Dapr dependency to 1.18.1, then deploy the resulting version-scoped
   resources with a workflow version; validate basic/bearer and mock-IdP OAuth2 calls against it.
4. Roll back by deleting the affected workflow version/resources and returning to a definition
   without authentication; existing definitions remain unchanged.

## Open Questions

- OAuth scopes are emitted as the comma-delimited form accepted by Dapr 1.18.1's released
  middleware implementation, despite the current documentation calling them space-delimited.
  Empty scope sets are rejected during compilation because that middleware requires a non-empty value.
