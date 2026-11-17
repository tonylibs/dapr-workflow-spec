# helm-admin-deployment

## Purpose

Render the `dws-admin` read-model Deployment and Service with its documented container contract.

## Requirements

### Requirement: Admin resources render from values

The chart SHALL render the enabled `dws-admin` Deployment and Service from `admin` values. The
Deployment SHALL expose only the Nest application container port 3000; port 3001 SHALL NOT be part
of the container contract. With `apiGateway.enabled=true`, the Service SHALL expose one logical
HTTP port whose `targetPort` is the Dapr sidecar HTTP port 3500, and it SHALL NOT expose a second
port targeting the app container. With `apiGateway.enabled=false`, the existing default Service
port targeting the Nest app MAY remain for the documented migration window. Owning component:
`charts/dws`.

#### Scenario: Default render retains migration-compatible Service
- **WHEN** `helm template charts/dws` is run with the API gateway disabled
- **THEN** one admin Deployment and one admin Service render
- **AND** the Deployment exposes port 3000 and no port 3001

#### Scenario: Disabled admin
- **WHEN** `admin.enabled=false`
- **THEN** no admin Deployment, Service, Secret, or test hook is rendered

#### Scenario: Secured gateway mode exposes only sidecar
- **WHEN** the chart renders with a valid `apiGateway.enabled=true` configuration
- **THEN** the admin Service has exactly one application-facing port targeting 3500
- **AND** no Service port targets the Nest app's port 3000

### Requirement: Admin environment and health probes follow the container contract

The Deployment SHALL configure `DATABASE_URL` and `RUN_MIGRATIONS_ON_BOOT` unconditionally; its liveness and readiness probes SHALL request `/health` on the container's port 3000. When Dapr is enabled, or when auth or observability requires an externally managed Dapr sidecar, the pod template SHALL carry `dapr.io/enabled`, `dapr.io/app-id`, and `dapr.io/app-port: "3000"`, and the container SHALL receive `DAPR_PUBSUB_NAME`, `DAPR_PUBSUB_TOPIC`, and `DAPR_CONTROLLER_APP_ID`. It SHALL NOT receive `DAPR_APP_PORT`, and no port-3001 annotation, environment value, or container port SHALL render.

When auth or observability is enabled, the pod SHALL reference exactly one shared admin Configuration. When observability is enabled, the pod SHALL also carry the Node.js injection annotation referencing the chart's Instrumentation resource and `instrumentation.opentelemetry.io/container-names: admin`. When observability is disabled, no OpenTelemetry injection annotation SHALL render. Owning component: `charts/dws`.

#### Scenario: Dapr enabled uses Nest as the one app port

- **WHEN** the chart renders with Dapr enabled
- **THEN** the admin pod carries `dapr.io/app-port: "3000"`
- **AND** pub/sub name/topic and controller app-id environment variables are present
- **AND** `DAPR_APP_PORT` and container port 3001 are absent

#### Scenario: Dapr disabled
- **WHEN** the chart renders with `dapr.enabled=false`, gateway disabled, and external Dapr is not
  otherwise required by auth or observability
- **THEN** the admin pod has no Dapr sidecar annotations or Dapr pub/sub environment variables

#### Scenario: Auth enabled adds admin Configuration
- **WHEN** the chart renders with `auth.enabled=true`
- **THEN** the pod references `<admin fullname>-config` while keeping app-port 3000

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

### Requirement: Database resolution supports Bitnami PostgreSQL and external DSNs

When `postgresql.enabled` is true, `DATABASE_URL` SHALL reference the chart-owned connection
Secret whose DSN targets the Bitnami PostgreSQL primary Service. When it is false, it SHALL use
`admin.database.url` or the configured existing Secret/key.

#### Scenario: In-chart database

- **WHEN** the chart renders with default values
- **THEN** `DATABASE_URL` references the chart-owned `database-url` key

#### Scenario: External database

- **WHEN** PostgreSQL is disabled and an existing Secret/key is configured
- **THEN** `DATABASE_URL` references that Secret/key without an admin-owned DSN Secret

### Requirement: Admin container carries the log format when observability logs are on

The admin container SHALL have the environment variable `DWS_LOG_FORMAT` set to `observability.logs.format` when `observability.enabled` and `observability.logs.enabled` are both true, and SHALL NOT have it otherwise. Owning component: `charts/dws`.

#### Scenario: Variable present when logs are on

- **WHEN** the chart is rendered with `observability.enabled=true`
- **THEN** the admin container has `DWS_LOG_FORMAT` = `json`

#### Scenario: Variable absent by default

- **WHEN** the chart is rendered with default values
- **THEN** the admin container has no `DWS_LOG_FORMAT`
