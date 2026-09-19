## MODIFIED Requirements

### Requirement: Configuration wires bearer middleware into the admin sidecar's inbound pipeline

When the admin is enabled and either `.Values.auth.enabled` or `.Values.observability.enabled` is `true`, `charts/dws` SHALL render exactly one Dapr `Configuration` (apiVersion `dapr.io/v1alpha1`) named `{{ include "dws.admin.fullname" . }}-config` in the release namespace. When auth is enabled, its `spec.httpPipeline.handlers` list SHALL contain exactly one handler of `type: middleware.http.bearer` referencing the admin auth Component. The handler SHALL be wired into `spec.httpPipeline`, NOT `spec.appHttpPipeline`, so browser-facing sidecar calls are bearer-gated while Dapr's app-channel subscription discovery and `dws.events` delivery remain reachable without a browser token. When auth is disabled, the Configuration SHALL NOT contain `httpPipeline`, even if it renders for observability. When observability is enabled, the same Configuration SHALL contain the `spec.tracing` contract defined by `helm-observability`; no second Configuration SHALL be created.

This placement SHALL continue to diverge from the controller's `spec.appHttpPipeline` placement and SHALL remain pinned by chart render tests.

Owning component: `charts/dws` (`templates/admin/configuration.yaml`).

#### Scenario: Auth-only Configuration preserves httpPipeline

- **WHEN** the chart renders with auth enabled, observability disabled, and valid issuer/audience values
- **THEN** exactly one `Configuration` named `<admin fullname>-config` is rendered
- **AND** its `spec.httpPipeline.handlers[0]` references the admin auth Component with type `middleware.http.bearer`
- **AND** it has no `spec.appHttpPipeline` or `spec.tracing` key

#### Scenario: Observability-only Configuration has tracing without auth pipeline

- **WHEN** the chart renders with auth disabled and observability enabled
- **THEN** exactly one `Configuration` named `<admin fullname>-config` is rendered
- **AND** it contains the required tracing block
- **AND** it has no `spec.httpPipeline`, `spec.appHttpPipeline`, or bearer handler

#### Scenario: Auth and observability share one Configuration

- **WHEN** both auth and observability are enabled
- **THEN** exactly one admin `Configuration` is rendered
- **AND** it contains both `spec.httpPipeline` and `spec.tracing`
- **AND** it has no `spec.appHttpPipeline`

#### Scenario: Configuration is absent when both features are disabled

- **WHEN** the chart renders with default values
- **THEN** no `Configuration` named `<admin fullname>-config` is rendered

#### Scenario: Admin and controller pipeline placements diverge and are pinned

- **WHEN** the chart is rendered with auth enabled
- **THEN** the admin Configuration uses `spec.httpPipeline` and the controller Configuration uses `spec.appHttpPipeline`
- **AND** chart render tests fail if either placement is changed to match the other

#### Scenario: Dapr subscription discovery reaches the app under auth and observability

- **WHEN** auth and observability are enabled and daprd discovers subscriptions or delivers a `dws.events` message
- **THEN** neither app-channel call is bearer-gated by the admin Configuration
- **AND** subscription registration and event ingestion continue

