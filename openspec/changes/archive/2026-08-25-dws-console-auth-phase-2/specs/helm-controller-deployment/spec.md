## MODIFIED Requirements

### Requirement: Controller pod carries Dapr sidecar annotations unconditionally

The controller Deployment's pod template SHALL carry `dapr.io/enabled: "true"`,
`dapr.io/app-id`, and `dapr.io/app-port: "8080"` annotations on every render — they are NOT
gated by `.Values.dapr.enabled`. The annotations are harmless when Dapr is not installed (the
mutating admission webhook simply is not present to act on them), so rendering them
unconditionally keeps the controller ready for Dapr as soon as the control plane appears in the
cluster, whether it is installed by this chart or by an external operator.

Phase 2 (see the `helm-controller-auth-middleware` capability) makes the controller receive
inbound Dapr-routed traffic in addition to publishing outbound events, so `dapr.io/app-port` is
now required for the sidecar to proxy inbound HTTP to the controller container. When
`.Values.auth.enabled` is `true`, the pod template SHALL additionally carry
`dapr.io/config: <controller fullname>-config` so the sidecar applies the bearer-middleware
inbound pipeline defined in the paired `Configuration`.

Owning component: `charts/dws` (`templates/controller/deployment.yaml`).

#### Scenario: Dapr enabled (default)

- **WHEN** the chart renders with default values (`dapr.enabled=true`, `auth.enabled=false`)
- **THEN** the controller Deployment's pod template carries `dapr.io/enabled: "true"`,
  `dapr.io/app-id`, and `dapr.io/app-port: "8080"`
- **AND** it does NOT carry a `dapr.io/config` annotation

#### Scenario: Dapr disabled

- **WHEN** the chart renders with `dapr.enabled=false`
- **THEN** the controller Deployment's pod template STILL carries `dapr.io/enabled: "true"`,
  `dapr.io/app-id`, and `dapr.io/app-port: "8080"`
- **AND** it does NOT carry a `dapr.io/config` annotation

#### Scenario: Auth enabled adds dapr.io/config

- **WHEN** the chart renders with `auth.enabled=true` plus a valid issuer/audience (see the
  `helm-controller-auth-middleware` values contract)
- **THEN** the controller Deployment's pod template carries `dapr.io/enabled: "true"`,
  `dapr.io/app-id`, `dapr.io/app-port: "8080"`, AND `dapr.io/config: <controller fullname>-config`

### Requirement: Image, replicas, and service port are configurable via values

The controller image (`repository`, `tag`, `pullPolicy`), replica count, and service port SHALL be
sourced from a `controller:` block in `values.yaml`. Overriding these values SHALL change the
rendered output accordingly. The controller Service's `targetPort` behavior is governed by the
`helm-controller-auth-middleware` capability: when `auth.enabled=false` the Service targets the
controller container's `http` port (pre-Phase-2 behavior); when `auth.enabled=true` the Service
targets the Dapr sidecar HTTP port so callers cannot bypass the middleware pipeline.

#### Scenario: Image reference is composed from values
- **WHEN** `helm template charts/dws --set controller.image.repository=ghcr.io/tonylibs/dws-controller --set controller.image.tag=1.2.3` is run
- **THEN** the controller Deployment's container image is `ghcr.io/tonylibs/dws-controller:1.2.3`
- **AND** the container `imagePullPolicy` is the value of `controller.image.pullPolicy`

#### Scenario: Replica count is configurable
- **WHEN** `helm template charts/dws --set controller.replicaCount=3` is run
- **THEN** the controller Deployment's `spec.replicas` is `3`

#### Scenario: Service port is configurable
- **WHEN** `helm template charts/dws --set controller.service.port=9090` is run
- **THEN** the controller Service exposes port `9090`
- **AND** with `auth.enabled=false` its `targetPort` is the controller container's `http` port
- **AND** with `auth.enabled=true` its `targetPort` is the Dapr sidecar's HTTP port
