## Purpose

The `charts/dws` Helm chart's rendering of the `dws-admin` read-model service as a values-driven
Deployment, Service, and Secret whose container contract matches `dws-admin`'s Dockerfile and
`.env.example` exactly, with its database connection resolvable from either an in-chart Postgres
or an external DSN.

## ADDED Requirements

### Requirement: Admin resources render from the chart

The `charts/dws` Helm chart SHALL render `dws-admin` as a Deployment, a Service, and a Secret
under `templates/admin/`, matching the container contract in `dws-admin/Dockerfile` and
`dws-admin/.env.example`: HTTP port `3000` (health, and future read API) and Dapr sidecar port
`3001` (`DAPR_APP_PORT`, not Service-exposed).

#### Scenario: Default render produces the admin stack
- **WHEN** `helm template charts/dws` is run with default values
- **THEN** the output contains exactly one admin Deployment, one admin Service, and one admin
  Secret
- **AND** the admin Service exposes only port `3000`, with no port `3001` entry
- **AND** the admin container declares container ports `3000` and `3001`

#### Scenario: Chart passes lint
- **WHEN** `helm lint charts/dws` is run
- **THEN** it reports no errors

### Requirement: Admin resources are gated by an enable toggle

Every admin resource SHALL be rendered only when `admin.enabled` is `true`, which SHALL be the
default. When `admin.enabled` is `false`, none of the admin Deployment, Service, or Secret SHALL
be rendered.

#### Scenario: Enabled by default
- **WHEN** `helm template charts/dws` is run with default values
- **THEN** all three admin resources are present

#### Scenario: Disabled toggle suppresses all admin resources
- **WHEN** `helm template charts/dws --set admin.enabled=false` is run
- **THEN** no admin Deployment, Service, or Secret appears in the output

### Requirement: Name and namespace are templatized

Admin resource names and their namespace SHALL be derived from chart helpers rather than
hardcoded. `_helpers.tpl` SHALL provide a `dws.admin.fullname` helper for the admin object name,
reuse the existing `dws.namespace` helper for the target namespace, and admin resources SHALL
carry the standard `dws.labels` plus a `dws.admin.selectorLabels` helper that extends
`dws.selectorLabels` with `app.kubernetes.io/component: admin`.

#### Scenario: Namespace follows the release namespace
- **WHEN** `helm template charts/dws --namespace dws-system` is run
- **THEN** every admin resource's `metadata.namespace` is `dws-system`

#### Scenario: Selector and pod labels agree
- **WHEN** the admin Deployment is rendered
- **THEN** its `spec.selector.matchLabels` is a subset of `spec.template.metadata.labels`

### Requirement: Image, replicas, and service port are configurable via values

The admin image (`repository`, `tag`, `pullPolicy`), replica count, and service port SHALL be
sourced from an `admin:` block in `values.yaml`. Overriding these values SHALL change the
rendered output accordingly.

#### Scenario: Image reference is composed from values
- **WHEN** `helm template charts/dws --set admin.image.repository=ghcr.io/tonylibs/dws-admin --set admin.image.tag=1.2.3` is run
- **THEN** the admin Deployment's container image is `ghcr.io/tonylibs/dws-admin:1.2.3`

#### Scenario: Replica count is configurable
- **WHEN** `helm template charts/dws --set admin.replicaCount=3` is run
- **THEN** the admin Deployment's `spec.replicas` is `3`

#### Scenario: Service port is configurable
- **WHEN** `helm template charts/dws --set admin.service.port=8080` is run
- **THEN** the admin Service exposes port `8080` targeting the container's `http` port (`3000`)

### Requirement: Admin env matches its documented container contract

The admin container SHALL set exactly the environment variables documented in
`dws-admin/.env.example`: `DATABASE_URL` (resolved per the database-resolution requirement below),
`RUN_MIGRATIONS_ON_BOOT` (default `"true"`), `DAPR_PUBSUB_NAME`, `DAPR_PUBSUB_TOPIC`, and
`DAPR_APP_PORT` (`3001`). `DAPR_PUBSUB_NAME`/`DAPR_PUBSUB_TOPIC` SHALL be sourced from
`admin.pubsub.{name,topic}`, defaulting to `pubsub`/`dws.events` to match `docs/events.md`.

#### Scenario: Pub/sub env defaults match the documented topic
- **WHEN** `helm template charts/dws` is run with default values
- **THEN** the admin container has env entries `DAPR_PUBSUB_NAME=pubsub` and
  `DAPR_PUBSUB_TOPIC=dws.events`

#### Scenario: Pub/sub env is overridable
- **WHEN** `helm template charts/dws --set admin.pubsub.topic=custom.events` is run
- **THEN** the admin container's `DAPR_PUBSUB_TOPIC` env entry is `custom.events`

#### Scenario: Migrations-on-boot defaults to enabled
- **WHEN** `helm template charts/dws` is run with default values
- **THEN** the admin container has env entry `RUN_MIGRATIONS_ON_BOOT=true`

### Requirement: Admin health probes target the documented health endpoint

The admin Deployment's liveness and readiness probes SHALL issue `GET /health` against container
port `3000`, matching `dws-admin`'s `@nestjs/terminus` health check.

#### Scenario: Probes target /health on the http port
- **WHEN** the admin Deployment is rendered
- **THEN** both `livenessProbe` and `readinessProbe` are `httpGet` probes with `path: /health` and
  `port: http`

### Requirement: Database connection resolves from in-chart Postgres or an external DSN

When `postgresql.enabled` is `true`, the admin Secret's `DATABASE_URL` SHALL be composed
automatically from the in-chart Postgres Secret's credentials and the in-chart Postgres Service's
in-cluster DNS name — no separate DSN input is required. When `postgresql.enabled` is `false`,
`DATABASE_URL` SHALL be sourced from `admin.database.url` (a literal connection string) or from an
existing Secret/key pair referenced by `admin.database.existingSecret` /
`admin.database.existingSecretKey`, letting the admin container consume the same env var either
way. This branching SHALL be implemented in the admin templates without creating a second,
duplicate Secret object for the in-chart case.

#### Scenario: In-chart Postgres wiring is automatic
- **WHEN** `helm template charts/dws` is run with default values (`postgresql.enabled` defaults to
  `true`)
- **THEN** the admin Secret's `DATABASE_URL` value decodes to a connection string whose host is
  the in-chart Postgres Service's DNS name and whose credentials match the in-chart Postgres
  Secret

#### Scenario: External database via literal URL
- **WHEN** `helm template charts/dws --set postgresql.enabled=false --set admin.database.url=postgres://user:pass@managed-db.example.com:5432/dws_admin` is run
- **THEN** the admin Secret's `DATABASE_URL` value decodes to
  `postgres://user:pass@managed-db.example.com:5432/dws_admin`
- **AND** no postgres StatefulSet, Service, or Secret is rendered

#### Scenario: External database via existing Secret reference
- **WHEN** `helm template charts/dws --set postgresql.enabled=false --set admin.database.existingSecret=my-db-secret --set admin.database.existingSecretKey=dsn` is run
- **THEN** the admin Deployment's `DATABASE_URL` env entry is a `secretKeyRef` to Secret
  `my-db-secret`, key `dsn`
- **AND** no admin-owned Secret carrying `DATABASE_URL` is rendered
