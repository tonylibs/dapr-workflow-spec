# helm-schedule-time-deployment

## Purpose
How `charts/dws` deploys `dws-schedule-time` in the Core layer: the `scheduleTime.*` values and their
schema, the read-only RBAC, the Dapr-enabled Deployment and its runtime env, and its own Dapr API token
when machine auth is on. Introduced by scheduling Phase 6a (ADR 0013).

## Requirements

### Requirement: Scheduler resources render in the Core layer

Owner: `charts/dws`. The chart SHALL render, under `templates/schedule-time/`, a ServiceAccount, Role,
RoleBinding and Deployment for `dws-schedule-time`. They SHALL be gated by `scheduleTime.enabled`, which
SHALL default to `true`, because triggers are Core (ADR 0007).

#### Scenario: Default render includes the scheduler
- **GIVEN** default values
- **WHEN** `helm template dws charts/dws` is run
- **THEN** the output contains exactly one ServiceAccount, Role, RoleBinding and Deployment for `dws-schedule-time`

#### Scenario: Scheduler can be disabled
- **GIVEN** `scheduleTime.enabled=false`
- **WHEN** the chart is rendered
- **THEN** no `dws-schedule-time` resources are rendered

### Requirement: Scheduler RBAC is read-only

Owner: `charts/dws`. The scheduler Role SHALL grant only `get`, `list` and `watch` on `configmaps`
and `apps/deployments` in the release namespace.

#### Scenario: Role grants only read access
- **GIVEN** default values
- **WHEN** the chart is rendered
- **THEN** the scheduler Role grants only `get`, `list` and `watch` on `configmaps` and `apps/deployments`

### Requirement: Scheduler Deployment is Dapr-enabled and values-driven

Owner: `charts/dws`. The scheduler Deployment SHALL carry `dapr.io/enabled: "true"`,
`dapr.io/app-id: dws-schedule-time` and an app port matching the container's HTTP port. Its image
SHALL come from `scheduleTime.image.{repository,tag,pullPolicy}`. `values.schema.json` SHALL describe
every new `scheduleTime.*` value.

#### Scenario: Dapr annotations present
- **GIVEN** default values
- **WHEN** the chart is rendered
- **THEN** the scheduler Deployment has `dapr.io/app-id: dws-schedule-time` and `dapr.io/enabled: "true"`

#### Scenario: Unknown scheduler value is rejected
- **GIVEN** `scheduleTime.bogus=true`
- **WHEN** `bash charts/dws/tests/values-schema-test.sh charts/dws` is run with that value
- **THEN** schema validation fails

### Requirement: Scheduler receives its runtime env

Owner: `charts/dws`. The scheduler container SHALL set:
- `DWS_LOG_FORMAT` through the shared `dws.observability.logFormatEnv` helper;
- `DWS_SCHEDULE_TIME_STATE_STORE` to the Core state store Component name;
- `DWS_SCHEDULE_TIME_AFTER_POLL_INTERVAL` from `scheduleTime.afterPollInterval` (default `PT15S`).

#### Scenario: Log format follows observability values
- **GIVEN** observability enabled with `observability.logs.format=json`
- **WHEN** the chart is rendered
- **THEN** the scheduler container has `DWS_LOG_FORMAT=json`, rendered by the same helper as the controller's

#### Scenario: State store and poll interval are set
- **GIVEN** default values
- **WHEN** the chart is rendered
- **THEN** the scheduler container has `DWS_SCHEDULE_TIME_STATE_STORE` set to the chart's state store Component name and `DWS_SCHEDULE_TIME_AFTER_POLL_INTERVAL=PT15S`

### Requirement: Scheduler gets its own Dapr API token with auth on

Owner: `charts/dws`. With `auth.enabled=true`, the chart SHALL render a Secret
`<release>-schedule-time-dapr-api-token` (key `token`). The scheduler Deployment SHALL carry
`dapr.io/api-token-secret` naming that Secret and SHALL set `DAPR_API_TOKEN` as a `secretKeyRef` to
it, matching the controller and admin pattern.

#### Scenario: Auth-enabled render wires the API token
- **GIVEN** `auth.enabled=true`
- **WHEN** the chart is rendered
- **THEN** the scheduler container has `DAPR_API_TOKEN` from a `secretKeyRef` with key `token`
- **AND** `bash charts/dws/tests/api-tokens-render-test.sh charts/dws` passes with the scheduler included
