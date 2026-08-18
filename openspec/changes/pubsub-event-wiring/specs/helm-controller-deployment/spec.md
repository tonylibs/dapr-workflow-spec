## ADDED Requirements

### Requirement: Controller Dapr sidecar annotations are gated by the Dapr toggle

The controller Deployment's pod template SHALL carry `dapr.io/enabled: "true"` and
`dapr.io/app-id` annotations when `.Values.dapr.enabled` is `true`, matching the admin
Deployment's pattern. Because the controller only publishes outbound (definition/deployment
lifecycle events via the sidecar) and never receives Dapr-routed inbound traffic, it SHALL NOT
carry a `dapr.io/app-port` annotation or a second container port. When `.Values.dapr.enabled` is
`false`, the controller pod template SHALL carry no `dapr.io/*` annotations at all.

Owning component: `charts/dws` (`templates/controller/deployment.yaml`).

#### Scenario: Dapr enabled (default)

- **WHEN** the chart renders with default values (`dapr.enabled=true`)
- **THEN** the controller Deployment's pod template carries `dapr.io/enabled: "true"` and
  `dapr.io/app-id`
- **AND** it carries no `dapr.io/app-port` annotation

#### Scenario: Dapr disabled

- **WHEN** the chart renders with `dapr.enabled=false`
- **THEN** the controller Deployment's pod template has no `dapr.io/enabled`, `dapr.io/app-id`,
  or `dapr.io/app-port` annotation
