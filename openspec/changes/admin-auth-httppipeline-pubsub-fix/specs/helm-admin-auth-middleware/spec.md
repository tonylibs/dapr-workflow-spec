## MODIFIED Requirements

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
