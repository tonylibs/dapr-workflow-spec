## Purpose

The `charts/dws` Helm chart's optional, in-chart Postgres backing `dws-admin`'s read model — a
single-replica, dev/eval-grade StatefulSet with a headless Service and a credentials Secret,
toggleable off in favor of an externally managed database.

## ADDED Requirements

### Requirement: Postgres resources render from the chart

The `charts/dws` Helm chart SHALL render an in-chart Postgres as a StatefulSet, a headless
Service, and a credentials Secret under `templates/postgres/`, using the `postgres:16-alpine`
image and defaults mirroring `dws-admin/docker-compose.yml` (`dws`/`dws`/`dws_admin` as
user/password/database).

#### Scenario: Default render produces the postgres stack
- **WHEN** `helm template charts/dws` is run with default values
- **THEN** the output contains exactly one postgres StatefulSet, one postgres Service, and one
  postgres Secret
- **AND** the postgres Service is headless (`clusterIP: None`)

#### Scenario: Chart passes lint
- **WHEN** `helm lint charts/dws` is run
- **THEN** it reports no errors

### Requirement: Postgres resources are gated by an enable toggle, default on

Every postgres resource SHALL be rendered only when `postgresql.enabled` is `true`, which SHALL be
the default. When `postgresql.enabled` is `false`, none of the postgres StatefulSet, Service, or
Secret SHALL be rendered.

#### Scenario: Enabled by default
- **WHEN** `helm template charts/dws` is run with default values
- **THEN** all three postgres resources are present

#### Scenario: Disabled toggle suppresses all postgres resources
- **WHEN** `helm template charts/dws --set postgresql.enabled=false` is run
- **THEN** no postgres StatefulSet, Service, or Secret appears in the output

### Requirement: Name and namespace are templatized

Postgres resource names and their namespace SHALL be derived from chart helpers rather than
hardcoded. `_helpers.tpl` SHALL provide a `dws.postgres.fullname` helper for the postgres object
name, reuse the existing `dws.namespace` helper, and postgres resources SHALL carry the standard
`dws.labels` plus a `dws.postgres.selectorLabels` helper that extends `dws.selectorLabels` with
`app.kubernetes.io/component: postgres`.

#### Scenario: Namespace follows the release namespace
- **WHEN** `helm template charts/dws --namespace dws-system` is run
- **THEN** every postgres resource's `metadata.namespace` is `dws-system`

### Requirement: Storage, image, and credentials are configurable via values

The postgres image, PVC storage size, and credentials (`username`, `password`, `database`, or an
`existingSecret` reference) SHALL be sourced from a `postgresql:` block in `values.yaml`.

#### Scenario: Storage size is configurable
- **WHEN** `helm template charts/dws --set postgresql.storage.size=5Gi` is run
- **THEN** the postgres StatefulSet's volume claim template requests `5Gi` of storage

#### Scenario: Credentials default to docker-compose parity
- **WHEN** `helm template charts/dws` is run with default values
- **THEN** the postgres Secret decodes to username `dws`, password `dws`, and database `dws_admin`

#### Scenario: Credentials are overridable
- **WHEN** `helm template charts/dws --set postgresql.auth.username=custom --set postgresql.auth.password=secret --set postgresql.auth.database=dws_admin` is run
- **THEN** the postgres Secret decodes to the overridden username, password, and database

### Requirement: Postgres credentials Secret is shared with the admin consumer

The same postgres credentials Secret rendered under `templates/postgres/` SHALL be the one
consumed by the admin Deployment's `DATABASE_URL` resolution when `postgresql.enabled` is `true`
(see the `helm-admin-deployment` capability) — no separate copy of the credentials SHALL be
rendered for the admin side.

#### Scenario: One credentials Secret backs both consumers
- **WHEN** `helm template charts/dws` is run with default values
- **THEN** exactly one Secret in the output carries the postgres username/password/database keys
- **AND** the admin Deployment's `DATABASE_URL` env entry references that same Secret's data (via
  the composed connection string) rather than a second Secret
