## Purpose

The `charts/dws` Helm chart's rendering of Dapr bearer-middleware authentication in front of
the `dws-admin` sidecar — the `Component`, `Configuration`, and pod annotation that together
enforce JWT verification on inbound sidecar-invoke traffic addressed at the Phase 3 write-relay
route, while leaving releases with `auth.enabled=false` topologically unchanged. Mirrors the
Phase 2 controller-side pattern (`helm-controller-auth-middleware`) so both sidecars use the
same values contract.

## ADDED Requirements

### Requirement: Admin bearer middleware Component renders when auth is enabled

When `.Values.auth.enabled` is `true`, `charts/dws` SHALL render a single Dapr `Component` of
`type: middleware.http.bearer` (apiVersion `dapr.io/v1alpha1`) named
`{{ include "dws.admin.fullname" . }}-auth` in the release namespace. The Component SHALL be
scoped to the `dws-admin` app-id only (`scopes: [<admin fullname>]`) so no sibling sidecar
inherits it. Its `spec.metadata` SHALL carry an `issuer` (from `.Values.auth.issuer` or the
derived Dex issuer), an `audience` (from `.Values.auth.audience` or the derived Dex client ID),
and, when configured, a `jwksURL` (from `.Values.auth.jwksURL` or the derived Dex JWKS URL) —
resolved through the same values plumbing already used by
`helm-controller-auth-middleware`. When `.Values.auth.enabled` is `false` (default), no admin
bearer Component SHALL be rendered.

Owning component: `charts/dws` (`templates/admin/auth-component.yaml`).

#### Scenario: Auth disabled (default) renders no admin Component
- **WHEN** `helm template charts/dws` is run with default values
- **THEN** no Component of type `middleware.http.bearer` named `<admin fullname>-auth`
  appears in the rendered output

#### Scenario: Auth enabled renders one scoped admin Component
- **WHEN** `helm template charts/dws --set auth.enabled=true --set auth.issuer=https://idp.example.com --set auth.audience=dws-console --set auth.jwksURL=https://idp.example.com/keys`
  is run
- **THEN** the output contains a Component of type `middleware.http.bearer` whose
  `metadata.name` is `<admin fullname>-auth`
- **AND** its `spec.metadata` contains `issuer=https://idp.example.com`,
  `audience=dws-console`, and `jwksURL=https://idp.example.com/keys`
- **AND** its `scopes` list contains exactly the admin fullname and nothing else (the
  controller's own bearer Component from Phase 2 remains separately scoped)

#### Scenario: Dex mode derives issuer/audience from in-chart Dex
- **WHEN** `helm template charts/dws --set dex.enabled=true --set auth.enabled=true --set auth.dex.enabled=true`
  is run
- **THEN** the admin bearer Component's `issuer` equals the in-chart Dex issuer URL
- **AND** its `audience` equals the same Dex client audience the controller's Component
  resolves to under the same mode (so a token minted for the console is accepted by both
  sidecars)

### Requirement: Configuration wires bearer middleware into the admin sidecar's inbound pipeline

When `.Values.auth.enabled` is `true`, `charts/dws` SHALL render a single Dapr `Configuration`
(apiVersion `dapr.io/v1alpha1`) named `{{ include "dws.admin.fullname" . }}-config` in the
release namespace whose `spec.appHttpPipeline.handlers` list contains exactly one entry — a
handler of `type: middleware.http.bearer` referencing the admin auth Component by name. The
pipeline SHALL apply to inbound sidecar-invoke calls addressed to the `dws-admin` app-id.

Owning component: `charts/dws` (`templates/admin/auth-configuration.yaml`).

#### Scenario: Configuration renders when auth is enabled
- **WHEN** `helm template charts/dws --set auth.enabled=true --set auth.issuer=https://idp.example.com --set auth.audience=dws-console`
  is run
- **THEN** the output contains exactly one `Configuration` named `<admin fullname>-config`
- **AND** its `spec.appHttpPipeline.handlers[0].name` matches the admin auth Component's
  name
- **AND** its `spec.appHttpPipeline.handlers[0].type` is `middleware.http.bearer`

#### Scenario: Configuration is absent when auth is disabled
- **WHEN** `helm template charts/dws` is run with default values
- **THEN** no `Configuration` named `<admin fullname>-config` is rendered

### Requirement: Bearer middleware verifies tokens before the dws-admin app runs

At runtime, when `auth.enabled=true`, an inbound request reaching the `dws-admin` sidecar via
Dapr service invocation SHALL be rejected by the sidecar with `401` (or `403` for role
failures, if a role middleware is present) when the request carries no `Authorization` header,
a malformed token, a token with a tampered signature, a wrong `aud`, or a wrong `iss`. In each
of those cases the `dws-admin` application container SHALL NOT observe the request in its own
access log. A valid token SHALL be forwarded and the application SHALL observe it in its access
log.

Owning component: `charts/dws` (`templates/admin/auth-component.yaml`,
`templates/admin/auth-configuration.yaml`, `templates/admin/deployment.yaml`).

#### Scenario: Missing Authorization header
- **WHEN** the sidecar's Dapr-invoke endpoint receives a request with no `Authorization`
  header addressed at the admin app-id
- **THEN** the sidecar responds `401`
- **AND** the `dws-admin` container's access log records no matching request

#### Scenario: Valid bearer token from the configured issuer/audience
- **WHEN** the sidecar's Dapr-invoke endpoint receives a request whose bearer token was
  signed by the configured issuer, carries the configured audience, and is unexpired
- **THEN** the sidecar forwards the request to the `dws-admin` container
- **AND** the container's access log records the request

#### Scenario: Malformed, tampered-signature, wrong-audience, or wrong-issuer token
- **WHEN** any of those failure cases is presented at the sidecar
- **THEN** the sidecar responds `401` (or `403` for role failures, if configured)
- **AND** the `dws-admin` container's access log records no matching request
