## ADDED Requirements

### Requirement: Controller pod carries Dapr sidecar annotations unconditionally

The controller Deployment's pod template SHALL carry `dapr.io/enabled: "true"` and
`dapr.io/app-id` annotations on every render — they are NOT gated by `.Values.dapr.enabled`
(unlike the admin Deployment). The annotations are harmless when Dapr is not installed (the
mutating admission webhook simply is not present to act on them), so rendering them
unconditionally keeps the controller ready for Dapr as soon as the control plane appears in the
cluster, whether it is installed by this chart or by an external operator. Because the
controller only publishes outbound (definition/deployment lifecycle events via the sidecar) and
never receives Dapr-routed inbound traffic, it SHALL NOT carry a `dapr.io/app-port` annotation
or a second container port.

Owning component: `charts/dws` (`templates/controller/deployment.yaml`).

#### Scenario: Dapr enabled (default)

- **WHEN** the chart renders with default values (`dapr.enabled=true`)
- **THEN** the controller Deployment's pod template carries `dapr.io/enabled: "true"` and
  `dapr.io/app-id`
- **AND** it carries no `dapr.io/app-port` annotation

#### Scenario: Dapr disabled

- **WHEN** the chart renders with `dapr.enabled=false`
- **THEN** the controller Deployment's pod template STILL carries `dapr.io/enabled: "true"` and
  `dapr.io/app-id`
- **AND** it still carries no `dapr.io/app-port` annotation
