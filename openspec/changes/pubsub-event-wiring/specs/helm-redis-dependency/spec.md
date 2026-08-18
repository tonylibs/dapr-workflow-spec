## Purpose

Provide the optional, dev/eval-grade Redis backend for the chart's Dapr `pubsub`,
`configuration.redis`, and `state.redis` Components through a conditional Bitnami Redis
subchart, with an external-Redis escape hatch — mirrors `helm-postgres-deployment`.

## ADDED Requirements

### Requirement: The Bitnami Redis dependency is conditional

`charts/dws/Chart.yaml` SHALL declare the Bitnami Redis chart with condition `redis.enabled`,
defaulting to `true`. The subchart SHALL own the Redis workload, Service, persistence, and
credentials Secret.

Owning component: `charts/dws` (`Chart.yaml`, `values.yaml`).

#### Scenario: Default render

- **WHEN** `helm template charts/dws` is run with default values
- **THEN** the Bitnami Redis workload, Service, and credentials Secret are rendered

#### Scenario: Disabled built-in Redis

- **WHEN** `redis.enabled=false`
- **THEN** no Bitnami Redis workload or chart-owned Redis connection resources are rendered

### Requirement: Bitnami Redis configuration is values-driven

The `redis` values SHALL configure standalone architecture, image selection, auth password,
primary persistence size, and `networkPolicy.enabled: false` for kind/dev use, mirroring the
existing `postgresql` values block. If the pinned Bitnami Redis chart version requires the same
relocated-image workaround as `postgresql.image.repository` (see `global.security.allowInsecureImages`),
`redis.image.repository` SHALL apply it the same way.

Owning component: `charts/dws` (`values.yaml`).

#### Scenario: Auth password is configurable

- **WHEN** `helm template charts/dws --set redis.auth.password=changeme` is run
- **THEN** the rendered Redis credentials reflect that password

### Requirement: Dapr Components resolve a Redis connection whether built-in or external

When `redis.enabled` is `true`, the chart's Dapr Redis Components (`pubsub`, `dws-definitions`,
actor state store) SHALL resolve their `redisHost`/`redisPassword` from the in-chart Bitnami
Redis subchart's own Service and Secret. When `redis.enabled` is `false`, they SHALL resolve
those values from an operator-provided external Redis host and existing Secret, mirroring how
`admin.database.url`/`existingSecret` supports an external Postgres.

Owning component: `charts/dws` (`_helpers.tpl`, Dapr `Component` templates).

#### Scenario: In-chart Redis

- **WHEN** the chart renders with default values (`redis.enabled=true`)
- **THEN** the Dapr Redis Components reference the Bitnami Redis subchart's Service host and its
  auto-created Secret

#### Scenario: External Redis

- **WHEN** the chart renders with `redis.enabled=false` and an external host/existing Secret
  configured
- **THEN** the Dapr Redis Components reference that external host and Secret, and no Bitnami
  Redis resources are rendered
