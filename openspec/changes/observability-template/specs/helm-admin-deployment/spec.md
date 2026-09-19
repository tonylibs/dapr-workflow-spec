## MODIFIED Requirements

### Requirement: Admin environment and health probes follow the container contract

The Deployment SHALL configure `DATABASE_URL` and `RUN_MIGRATIONS_ON_BOOT` unconditionally; its liveness and readiness probes SHALL request `/health` on the container's port 3000. When Dapr is enabled, or when auth or observability requires an externally managed Dapr sidecar, the pod template SHALL carry `dapr.io/enabled`, `dapr.io/app-id`, and `dapr.io/app-port: "3000"`, and the container SHALL receive `DAPR_PUBSUB_NAME`, `DAPR_PUBSUB_TOPIC`, and `DAPR_CONTROLLER_APP_ID`. It SHALL NOT receive `DAPR_APP_PORT`, and no port-3001 annotation, environment value, or container port SHALL render.

When auth or observability is enabled, the pod SHALL reference exactly one shared admin Configuration. When observability is enabled, the pod SHALL also carry the Node.js injection annotation referencing the chart's Instrumentation resource and `instrumentation.opentelemetry.io/container-names: admin`. When observability is disabled, no OpenTelemetry injection annotation SHALL render. Owning component: `charts/dws`.

#### Scenario: Dapr enabled uses Nest as the one app port

- **WHEN** the chart renders with Dapr enabled
- **THEN** the admin pod carries `dapr.io/app-port: "3000"`
- **AND** pub/sub name/topic and controller app-id environment variables are present
- **AND** `DAPR_APP_PORT` and container port 3001 are absent

#### Scenario: No Dapr-dependent feature renders no sidecar contract

- **WHEN** Dapr, auth, and observability are all disabled and the gateway is disabled
- **THEN** the admin pod has no Dapr sidecar annotations or Dapr pub/sub environment variables

#### Scenario: Auth enabled references the shared Configuration

- **WHEN** auth is enabled and observability is disabled
- **THEN** the pod references `<admin fullname>-config` while keeping app-port 3000
- **AND** it has no OpenTelemetry injection annotation

#### Scenario: Observability enabled targets the admin container

- **WHEN** observability is enabled
- **THEN** the admin pod references exactly one `<admin fullname>-config` while keeping app-port 3000
- **AND** it has Node.js injection referencing the release Instrumentation
- **AND** the injection container-names annotation is exactly `admin`

