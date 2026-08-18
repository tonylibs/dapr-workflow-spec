## Purpose

Provide the optional, dev/eval-grade Redis backend for the chart's Dapr `pubsub`,
`configuration.redis`, and `state.redis` Components through a conditional Bitnami Redis
subchart, with an external-Redis escape hatch — mirrors `helm-postgres-deployment`.

## ADDED Requirements

### Requirement: The Bitnami Redis dependency follows the Dapr toggle

`charts/dws/Chart.yaml` SHALL declare the Bitnami Redis chart with condition `dapr.enabled` —
Redis exists solely to back the chart's Dapr Components, so it has no independent enable toggle
of its own; it installs whenever Dapr does and installs nothing when Dapr is disabled. The
subchart SHALL own the Redis workload, Service, persistence, and credentials Secret.

Owning component: `charts/dws` (`Chart.yaml`, `values.yaml`).

#### Scenario: Default render

- **WHEN** `helm template charts/dws` is run with default values
- **THEN** the Bitnami Redis workload, Service, and credentials Secret are rendered

#### Scenario: Dapr disabled

- **WHEN** `dapr.enabled=false`
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

When `redis.external.host` is unset, the chart's Dapr Redis Components (`pubsub`,
`dws-definitions`, actor state store) SHALL resolve their `redisHost`/`redisPassword` from the
in-chart Bitnami Redis subchart's own Service and Secret. When `redis.external.host` is set, they
SHALL resolve those values from that external Redis host and `redis.external.existingSecret`/
`existingSecretKey` instead, mirroring how `admin.database.url`/`existingSecret` supports an
external Postgres — independent of whether the in-chart Bitnami Redis subchart also installed
(it still installs whenever `dapr.enabled` is true, per the requirement above).

Owning component: `charts/dws` (`_helpers.tpl`, Dapr `Component` templates).

#### Scenario: In-chart Redis

- **WHEN** the chart renders with default values (no `redis.external.host` set)
- **THEN** the Dapr Redis Components reference the Bitnami Redis subchart's Service host and its
  auto-created Secret

#### Scenario: External Redis

- **WHEN** the chart renders with `redis.external.host` and an existing Secret configured
- **THEN** the Dapr Redis Components reference that external host and Secret
