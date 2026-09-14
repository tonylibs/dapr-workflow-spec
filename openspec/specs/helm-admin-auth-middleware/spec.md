# helm-admin-auth-middleware

## Purpose

The `charts/dws` Helm chart's rendering of Dapr bearer-middleware authentication in front of
the `dws-admin` sidecar — the `Component`, `Configuration`, and pod annotation that together
enforce JWT verification on inbound sidecar-invoke traffic addressed at the Phase 3 write-relay
route, while leaving releases with `auth.enabled=false` topologically unchanged. Mirrors the
Phase 2 controller-side pattern (`helm-controller-auth-middleware`) so both sidecars use the
same values contract.

## Requirements

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
release namespace whose `spec.httpPipeline.handlers` list contains exactly one entry — a handler
of `type: middleware.http.bearer` referencing the admin auth Component by name. The handler SHALL
be wired into `spec.httpPipeline` (the admin sidecar's own Dapr HTTP API surface), NOT
`spec.appHttpPipeline`. This gates the browser-facing reads, writes, OpenAPI, and SSE calls that
reach the admin sidecar as Dapr service invocations, while leaving daprd's internal app-channel
calls — its `GET /dapr/subscribe` discovery and `dws.events` pub/sub delivery — reachable without
a browser bearer token, so subscription registration and event ingestion continue under
`auth.enabled=true`.

This placement DELIBERATELY DIVERGES from `helm-controller-auth-middleware`, whose bearer
`Configuration` stays on `spec.appHttpPipeline`: the controller is reached only by Dapr service
invocation, which arrives over internal gRPC and never traverses `httpPipeline`, so moving the
controller handler would silently delete its gate. The divergence SHALL be pinned by a
`helm template` test so it cannot be reverted to matching without failing CI.

Owning component: `charts/dws` (`templates/admin/auth-configuration.yaml`).

#### Scenario: Configuration renders when auth is enabled

- **WHEN** `helm template charts/dws --set auth.enabled=true --set auth.issuer=https://idp.example.com --set auth.audience=dws-console`
  is run
- **THEN** the output contains exactly one `Configuration` named `<admin fullname>-config`
- **AND** its `spec.httpPipeline.handlers[0].name` matches the admin auth Component's name
- **AND** its `spec.httpPipeline.handlers[0].type` is `middleware.http.bearer`
- **AND** the Configuration has no `spec.appHttpPipeline` key

#### Scenario: Configuration is absent when auth is disabled

- **WHEN** `helm template charts/dws` is run with default values
- **THEN** no `Configuration` named `<admin fullname>-config` is rendered

#### Scenario: Admin and controller pipeline placements diverge and are pinned

- **WHEN** the chart is rendered with `auth.enabled=true`
- **THEN** the admin `Configuration` uses `spec.httpPipeline` and the controller `Configuration`
  uses `spec.appHttpPipeline`
- **AND** a chart test (`tests/auth-pipeline-placement-test.sh`) fails if either placement is
  changed to match the other

#### Scenario: Dapr subscription discovery reaches the app under auth

- **WHEN** `auth.enabled=true` and daprd issues its internal `GET /dapr/subscribe` discovery call
  and subsequently delivers a `dws.events` message to the app callback
- **THEN** neither call is bearer-gated by this Configuration (they travel the app channel, which
  the handler no longer sits on), so the subscription registers and event ingestion continues

### Requirement: Bearer middleware verifies tokens before the dws-admin app runs

At runtime, the shared API Gateway SHALL route every browser-facing `dws-admin` read, write,
OpenAPI, and SSE request to the admin app only through Dapr service invocation and the
admin sidecar's bearer middleware. The sidecar MUST reject a missing Authorization header, a
malformed token, a tampered signature, a wrong `aud`, or a wrong `iss` before Nest observes the
request. A valid token SHALL be forwarded to the matching Nest route. Dapr's internal
programmatic-subscription discovery and pub/sub callback delivery SHALL continue to reach the app
without requiring a browser bearer token. Owning component: `charts/dws`.

#### Scenario: Missing Authorization header on read
- **WHEN** the gateway sends `GET /instances` through the admin Dapr invoke path without an
  Authorization header
- **THEN** the sidecar responds 401 and Nest does not observe the request

#### Scenario: Valid bearer token reaches read and write routes
- **WHEN** requests for an admin GET route and `POST /workflows` carry a valid configured token
- **THEN** the sidecar forwards both requests to Nest on port 3000

#### Scenario: Invalid token does not reach SSE route
- **WHEN** an SSE request carries a malformed, tampered, wrong-audience, or wrong-issuer token
- **THEN** the sidecar rejects it with 401 and no SSE subscription is opened in Nest

#### Scenario: Pubsub callback remains internal
- **WHEN** Dapr discovers subscriptions or delivers a `dws.events` message to the app callback
- **THEN** the callback reaches Nest on app-port 3000 without an end-user bearer token and event
  ingestion continues
