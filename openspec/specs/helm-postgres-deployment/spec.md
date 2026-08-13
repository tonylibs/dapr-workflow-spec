# helm-postgres-deployment

## Purpose

Provide the optional, dev/eval-grade PostgreSQL backend for `dws-admin` through the conditional
Bitnami PostgreSQL subchart.

## Requirements

### Requirement: The Bitnami PostgreSQL dependency is conditional

`charts/dws/Chart.yaml` SHALL declare the Bitnami PostgreSQL chart with condition
`postgresql.enabled`, which defaults to true. The subchart SHALL own the PostgreSQL StatefulSet,
Services, persistence, and credentials Secret.

#### Scenario: Default render

- **WHEN** `helm template charts/dws` is run with defaults
- **THEN** the Bitnami PostgreSQL StatefulSet, Services, and credentials Secret are rendered

#### Scenario: Disabled database

- **WHEN** `postgresql.enabled=false`
- **THEN** no Bitnami PostgreSQL workload or chart-owned connection Secret is rendered

### Requirement: Bitnami configuration is values-driven

The `postgresql` values SHALL configure standalone architecture, image selection, credentials,
and primary persistence size. Default credentials SHALL be `dws`/`dws`/`dws_admin`.

### Requirement: Admin receives a connection DSN

When PostgreSQL is enabled, `templates/postgres/secret.yaml` SHALL render a chart-owned
`database-url` Secret value from `postgresql.auth` and the Bitnami primary Service hostname.

#### Scenario: Service override

- **WHEN** `postgresql.fullnameOverride` is configured
- **THEN** the rendered DSN uses that primary Service hostname
