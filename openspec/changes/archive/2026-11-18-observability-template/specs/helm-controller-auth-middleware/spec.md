## MODIFIED Requirements

### Requirement: Configuration wires bearer middleware into the controller sidecar's inbound pipeline

When the controller is enabled and either `.Values.auth.enabled` or `.Values.observability.enabled` is `true`, `charts/dws` SHALL render exactly one Dapr `Configuration` (apiVersion `dapr.io/v1alpha1`) named `{{ include "dws.controller.fullname" . }}-config` in the release namespace. When auth is enabled, its `spec.appHttpPipeline.handlers` list SHALL contain exactly one handler of `type: middleware.http.bearer` referencing the auth Component by name. The pipeline SHALL apply to inbound HTTP calls addressed to the controller's app-id and SHALL NOT apply to other app-ids or the sidecar's own management/health endpoints. When auth is disabled, the Configuration SHALL NOT contain `appHttpPipeline`, even if it renders for observability. When observability is enabled, the same Configuration SHALL contain the `spec.tracing` contract defined by `helm-observability`; no second Configuration SHALL be created.

Owning component: `charts/dws` (`templates/controller/configuration.yaml`).

#### Scenario: Auth-only Configuration preserves appHttpPipeline

- **WHEN** the chart renders with auth enabled, observability disabled, and valid issuer/audience values
- **THEN** exactly one `Configuration` named `<controller fullname>-config` is rendered
- **AND** its `spec.appHttpPipeline.handlers[0]` references the controller auth Component with type `middleware.http.bearer`
- **AND** it has no `spec.tracing` key

#### Scenario: Observability-only Configuration has tracing without auth pipeline

- **WHEN** the chart renders with auth disabled and observability enabled
- **THEN** exactly one `Configuration` named `<controller fullname>-config` is rendered
- **AND** it contains the required tracing block
- **AND** it has no `spec.appHttpPipeline` key and no bearer handler

#### Scenario: Auth and observability share one Configuration

- **WHEN** both auth and observability are enabled
- **THEN** exactly one controller `Configuration` is rendered
- **AND** it contains both `spec.appHttpPipeline` and `spec.tracing`

#### Scenario: Configuration is absent when both features are disabled

- **WHEN** the chart renders with default values
- **THEN** no `Configuration` named `<controller fullname>-config` is rendered

### Requirement: Controller pod carries dapr.io/app-port and dapr.io/config annotations

The controller Deployment's pod template SHALL always carry `dapr.io/app-port: "8080"` (matching the container's `http` port). When `.Values.auth.enabled` or `.Values.observability.enabled` is `true`, the pod template SHALL carry exactly one `dapr.io/config: <controller fullname>-config`. Existing `dapr.io/enabled: "true"` and `dapr.io/app-id` annotations SHALL be preserved unchanged.

Owning component: `charts/dws` (`templates/controller/deployment.yaml`).

#### Scenario: app-port annotation is always present

- **WHEN** `helm template charts/dws` is run with any combination of Dapr, auth, and observability toggles
- **THEN** the controller pod template carries `dapr.io/app-port: "8080"`

#### Scenario: dapr.io/config follows either feature toggle

- **WHEN** auth or observability is enabled
- **THEN** the controller pod template carries exactly one `dapr.io/config: <controller fullname>-config`

#### Scenario: dapr.io/config is absent when both features are disabled

- **WHEN** the chart is rendered with default values
- **THEN** the controller pod template does NOT carry a `dapr.io/config` annotation

