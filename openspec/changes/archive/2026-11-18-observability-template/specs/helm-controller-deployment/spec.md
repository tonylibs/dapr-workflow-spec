## MODIFIED Requirements

### Requirement: Controller pod carries Dapr sidecar annotations unconditionally

The controller Deployment's pod template SHALL carry `dapr.io/enabled: "true"`, `dapr.io/app-id`, and `dapr.io/app-port: "8080"` annotations on every render; they SHALL NOT be gated by `.Values.dapr.enabled`. Rendering these annotations unconditionally keeps the controller ready for chart-managed or externally managed Dapr.

When `.Values.auth.enabled` or `.Values.observability.enabled` is `true`, the pod template SHALL additionally carry exactly one `dapr.io/config: <controller fullname>-config` so the sidecar applies the shared component Configuration. When observability is enabled, the pod template SHALL also carry the Java injection annotation referencing the chart's Instrumentation resource and `instrumentation.opentelemetry.io/container-names: controller`. When observability is disabled, no OpenTelemetry injection annotation SHALL render.

Owning component: `charts/dws` (`templates/controller/deployment.yaml`).

#### Scenario: Default render keeps only baseline Dapr annotations

- **WHEN** the chart renders with default values
- **THEN** the controller pod carries `dapr.io/enabled: "true"`, `dapr.io/app-id`, and `dapr.io/app-port: "8080"`
- **AND** it has no `dapr.io/config` or OpenTelemetry injection annotation

#### Scenario: Dapr disabled preserves baseline annotations

- **WHEN** the chart renders with Dapr, auth, and observability disabled
- **THEN** the controller pod STILL carries the three baseline Dapr annotations
- **AND** it has no configuration or injection annotation

#### Scenario: Auth enabled references the shared Configuration

- **WHEN** auth is enabled with valid issuer/audience values and observability is disabled
- **THEN** the controller pod carries exactly one `dapr.io/config: <controller fullname>-config`
- **AND** it has no OpenTelemetry injection annotation

#### Scenario: Observability enabled targets the controller container

- **WHEN** observability is enabled
- **THEN** the controller pod carries exactly one `dapr.io/config: <controller fullname>-config`
- **AND** it has Java injection referencing the release Instrumentation
- **AND** the injection container-names annotation is exactly `controller`

