# helm-admin-deployment

## Purpose

Render the `dws-admin` read-model Deployment and Service with its documented container contract.

## Requirements

### Requirement: Admin resources render from values

The chart SHALL render the enabled `dws-admin` Deployment and Service from `admin` values. The
Deployment SHALL expose container ports 3000 and 3001. The Service SHALL expose port 3000
(the app port) unconditionally. When `.Values.auth.enabled` is `true`, the Service SHALL
additionally expose a second port that front-ports the Dapr sidecar's HTTP port (`3500`) —
mirroring the controller Service's Phase 2 front-port so callers reaching that second port
must invoke through Dapr and pass the bearer middleware. When `.Values.auth.enabled` is
`false` (the default), the Service SHALL expose only port 3000 as before (topological no-op).

#### Scenario: Default render

- **WHEN** `helm template charts/dws` is run with defaults
- **THEN** one admin Deployment and one admin Service are rendered
- **AND** the Service exposes only port 3000

#### Scenario: Disabled admin

- **WHEN** `admin.enabled=false`
- **THEN** no admin Deployment, Service, Secret, or test hook is rendered

#### Scenario: Auth-enabled Service exposes the sidecar port

- **WHEN** `helm template charts/dws --set auth.enabled=true --set auth.issuer=https://idp.example.com --set auth.audience=dws-console`
  is run
- **THEN** the admin Service exposes port 3000 (targeting the app's `http` port)
- **AND** the admin Service exposes a second port whose `targetPort` is 3500 (the Dapr
  sidecar's HTTP port)
- **AND** the existing read routes reachable on port 3000 are unchanged

### Requirement: Admin environment and health probes follow the container contract

The Deployment SHALL configure `DATABASE_URL` and `RUN_MIGRATIONS_ON_BOOT` unconditionally; its
liveness and readiness probes SHALL request `/health` on port 3000. The Deployment's
`dapr.io/enabled`/`dapr.io/app-id` pod annotations and its `DAPR_PUBSUB_NAME`/
`DAPR_PUBSUB_TOPIC`/`DAPR_APP_PORT` env vars SHALL render only when `.Values.dapr.enabled` is
`true`; when it is `false`, none of those annotations or env vars SHALL be present on the pod
template. When `.Values.auth.enabled` is `true`, the pod template SHALL additionally carry
`dapr.io/config: {{ include "dws.admin.fullname" . }}-config`, referencing the Dapr
Configuration rendered by `helm-admin-auth-middleware`. When `.Values.auth.enabled` is
`false`, the `dapr.io/config` annotation SHALL NOT be present.

Owning component: `charts/dws` (`templates/admin/deployment.yaml`).

#### Scenario: Dapr enabled (default)

- **WHEN** the chart renders with default values (`dapr.enabled=true`)
- **THEN** the admin Deployment's pod template carries `dapr.io/enabled: "true"`,
  `dapr.io/app-id`, and the container has `DAPR_PUBSUB_NAME`, `DAPR_PUBSUB_TOPIC`, and
  `DAPR_APP_PORT` env vars
- **AND** no `dapr.io/config` annotation is present

#### Scenario: Dapr disabled

- **WHEN** the chart renders with `dapr.enabled=false`
- **THEN** the admin Deployment's pod template has no `dapr.io/enabled` or `dapr.io/app-id`
  annotation, and the container has no `DAPR_PUBSUB_NAME`, `DAPR_PUBSUB_TOPIC`, or
  `DAPR_APP_PORT` env var

#### Scenario: Auth enabled adds dapr.io/config

- **WHEN** `helm template charts/dws --set auth.enabled=true --set auth.issuer=https://idp.example.com --set auth.audience=dws-console`
  is run
- **THEN** the admin Deployment's pod template carries
  `dapr.io/config: <admin fullname>-config`
- **AND** its other Dapr annotations (`dapr.io/enabled`, `dapr.io/app-id`,
  `dapr.io/app-port`) are still present unchanged

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
