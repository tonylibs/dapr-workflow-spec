# helm-admin-deployment

## Purpose

Render the `dws-admin` read-model Deployment and Service with its documented container contract.

## Requirements

### Requirement: Admin resources render from values

The chart SHALL render the enabled `dws-admin` Deployment and Service from `admin` values. The
Deployment SHALL expose container ports 3000 and 3001; the Service SHALL expose only port 3000.

#### Scenario: Default render

- **WHEN** `helm template charts/dws` is run with defaults
- **THEN** one admin Deployment and one admin Service are rendered

#### Scenario: Disabled admin

- **WHEN** `admin.enabled=false`
- **THEN** no admin Deployment, Service, Secret, or test hook is rendered

### Requirement: Admin environment and health probes follow the container contract

The Deployment SHALL configure `DATABASE_URL`, `RUN_MIGRATIONS_ON_BOOT`,
`DAPR_PUBSUB_NAME`, `DAPR_PUBSUB_TOPIC`, and `DAPR_APP_PORT`; its liveness and readiness probes
SHALL request `/health` on port 3000.

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
