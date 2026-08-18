## Purpose

Chart-manage the `pubsub` Dapr Component that `dws-controller` and `dws-orchestrator` publish
`dws.events` lifecycle events through, so a chart-managed install satisfies the deployment
prerequisite documented in `docs/events.md` instead of requiring a hand-applied manifest.

## ADDED Requirements

### Requirement: The pubsub Component renders when Dapr is enabled

`charts/dws` SHALL render `templates/pubsub-component.yaml`, a Dapr `Component` of type
`pubsub.redis` named `pubsub`, whenever `.Values.dapr.enabled` is `true`. A Redis connection is
always resolvable in that case — built-in (per `helm-redis-dependency`, which installs Redis
whenever Dapr is enabled) or external — so no separate Redis-availability gate is needed. The
component name SHALL be exactly `pubsub` — the same name `admin.pubsub.name`,
`dws-orchestrator`'s `emit` tasks, and `dws-controller`'s event publisher already assume; it
SHALL NOT be configurable to a different name.

Owning component: `charts/dws` (`templates/pubsub-component.yaml`).

#### Scenario: Default render

- **WHEN** `helm template charts/dws` is run with default values (`dapr.enabled=true`)
- **THEN** a Dapr `Component` named `pubsub` of type `pubsub.redis` is rendered

#### Scenario: Dapr disabled

- **WHEN** `dapr.enabled=false`
- **THEN** no `pubsub` Component is rendered

### Requirement: The pubsub Component targets the topic dws.events

The rendered `pubsub` Component's Redis connection metadata SHALL resolve to the chart's Redis
backend (in-chart or external, per `helm-redis-dependency`), and its auth SHALL reference a
Kubernetes Secret via `secretKeyRef` rather than an inline password.

Owning component: `charts/dws` (`templates/pubsub-component.yaml`).

#### Scenario: Redis connection metadata is populated

- **WHEN** the `pubsub` Component renders
- **THEN** its `spec.metadata` includes a `redisHost` entry pointing at the resolved Redis host
  and a `redisPassword` entry sourced via `secretKeyRef`
