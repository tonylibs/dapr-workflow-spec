# helm-pubsub-component

## Purpose

Chart-manage the `pubsub` Dapr Component that `dws-controller` and `dws-orchestrator` publish
`dws.events` lifecycle events through, so a chart-managed install satisfies the deployment
prerequisite documented in `docs/events.md` instead of requiring a hand-applied manifest.

## Requirements

### Requirement: The pubsub Component always renders

`charts/dws` SHALL render `templates/pubsub-component.yaml`, a Dapr `Component` of type
`pubsub.redis` named `pubsub`, on every render — NOT gated by `.Values.dapr.enabled`.
`dapr.enabled=false` means "Dapr is externally managed and its CRDs are already in the
cluster" (the preflight check enforces this), so a `dapr.io/v1alpha1` Component applies
cleanly whether Dapr was chart-installed or not. Only the Dapr *control plane* is gated by
`dapr.enabled`, not the Components consumed by that control plane. The component name SHALL
be exactly `pubsub` — the same name `admin.pubsub.name`, `dws-orchestrator`'s `emit` tasks,
and `dws-controller`'s event publisher already assume; it SHALL NOT be configurable to a
different name.

Owning component: `charts/dws` (`templates/pubsub-component.yaml`).

#### Scenario: Default render

- **WHEN** `helm template charts/dws` is run with default values (`dapr.enabled=true`)
- **THEN** a Dapr `Component` named `pubsub` of type `pubsub.redis` is rendered

#### Scenario: Dapr externally managed

- **WHEN** `helm template charts/dws --set dapr.enabled=false` is run
- **THEN** the same `pubsub` Component is still rendered — the operator's externally-managed
  Dapr control plane picks it up the same way the chart-installed one would

### Requirement: The pubsub Component targets the topic dws.events

The rendered `pubsub` Component's Redis connection metadata SHALL resolve to the chart's Redis
backend (in-chart or external, per `helm-redis-dependency`), and its auth SHALL reference a
Kubernetes Secret via `secretKeyRef` rather than an inline password.

Owning component: `charts/dws` (`templates/pubsub-component.yaml`).

#### Scenario: Redis connection metadata is populated

- **WHEN** the `pubsub` Component renders
- **THEN** its `spec.metadata` includes a `redisHost` entry pointing at the resolved Redis host
  and a `redisPassword` entry sourced via `secretKeyRef`
