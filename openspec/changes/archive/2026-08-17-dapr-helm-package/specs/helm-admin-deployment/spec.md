## MODIFIED Requirements

### Requirement: Admin environment and health probes follow the container contract

The Deployment SHALL configure `DATABASE_URL` and `RUN_MIGRATIONS_ON_BOOT` unconditionally; its
liveness and readiness probes SHALL request `/health` on port 3000. The Deployment's
`dapr.io/enabled`/`dapr.io/app-id` pod annotations and its `DAPR_PUBSUB_NAME`/
`DAPR_PUBSUB_TOPIC`/`DAPR_APP_PORT` env vars SHALL render only when `.Values.dapr.enabled` is
`true`; when it is `false`, none of those annotations or env vars SHALL be present on the pod
template.

Owning component: `charts/dws` (`templates/admin/deployment.yaml`).

#### Scenario: Dapr enabled (default)

- **WHEN** the chart renders with default values (`dapr.enabled=true`)
- **THEN** the admin Deployment's pod template carries `dapr.io/enabled: "true"`,
  `dapr.io/app-id`, and the container has `DAPR_PUBSUB_NAME`, `DAPR_PUBSUB_TOPIC`, and
  `DAPR_APP_PORT` env vars

#### Scenario: Dapr disabled

- **WHEN** the chart renders with `dapr.enabled=false`
- **THEN** the admin Deployment's pod template has no `dapr.io/enabled` or `dapr.io/app-id`
  annotation, and the container has no `DAPR_PUBSUB_NAME`, `DAPR_PUBSUB_TOPIC`, or
  `DAPR_APP_PORT` env var
