# helm-actor-statestore-component

## Purpose

Chart-manage the Redis-backed actor/workflow state store Dapr Component ahead of
`dws-orchestrator` adopting the Dapr Workflow runtime, so it is ready to consume once that lands.

## Requirements

### Requirement: The actor state store Component always renders

`charts/dws` SHALL render `templates/actor-statestore-component.yaml`, a Dapr `Component` of
type `state.redis`, on every render — NOT gated by `.Values.dapr.enabled` (see
`helm-pubsub-component` for the rationale). Its metadata SHALL include `actorStateStore: "true"`,
and its Redis connection metadata SHALL resolve to the chart's Redis backend the same way as
the `pubsub` Component (built-in or external, per `helm-redis-dependency`).

The Component SHALL NOT declare `scopes`. Every Dapr Workflow host in the namespace needs the
actor state store to start the actor runtime, and those hosts run under app ids the chart cannot
know: a controller-compiled orchestrator runs as its workflow name and each activity-invoked step
service as its task name. A fixed scope such as `dws-orchestrator` therefore matches none of them
and starting a workflow fails with "the state store is not configured to use the actor runtime".
Actor state is keyed by app id, so an unscoped store does not mix workflows' state. The cost is
that unrelated sidecars (admin, controller) also initialize it; its `initTimeout` is extended so a
`state.redis` init, which issues `CONFIG SET` for keyspace notifications, survives a Redis that is
still warming instead of hitting Dapr's 5s default.

Owning component: `charts/dws` (`templates/actor-statestore-component.yaml`).

#### Scenario: Default render

- **WHEN** `helm template charts/dws` is run with default values (`dapr.enabled=true`)
- **THEN** a Dapr `Component` of type `state.redis` with `actorStateStore: "true"` metadata is
  rendered
- **AND** it declares no top-level `scopes` field

#### Scenario: Dapr externally managed

- **WHEN** `helm template charts/dws --set dapr.enabled=false` is run
- **THEN** the same actor state store Component is still rendered
