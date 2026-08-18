## Purpose

Chart-manage the Redis-backed actor/workflow state store Dapr Component ahead of
`dws-orchestrator` adopting the Dapr Workflow runtime, so it is ready to consume once that lands.

## ADDED Requirements

### Requirement: The actor state store Component renders when Dapr is enabled

`charts/dws` SHALL render `templates/actor-statestore-component.yaml`, a Dapr `Component` of
type `state.redis`, whenever `.Values.dapr.enabled` is `true`. Its metadata SHALL include
`actorStateStore: "true"`, and its Redis connection metadata SHALL resolve to the chart's Redis
backend the same way as the `pubsub` Component (built-in or external, per `helm-redis-dependency`).

The Component SHALL declare `scopes: [dws-orchestrator]` at the top level so only orchestrator
pods' daprd sidecars initialize it — admin and controller sidecars never touch it. Beyond the
"nothing else uses it" reason, a `state.redis` init issues `CONFIG SET` for keyspace notifications
against Redis and can exceed Dapr's 5s per-component init timeout when Redis is still warming;
scoping avoids that being fatal to unrelated pods' daprd sidecars.

Owning component: `charts/dws` (`templates/actor-statestore-component.yaml`).

#### Scenario: Default render

- **WHEN** `helm template charts/dws` is run with default values (`dapr.enabled=true`)
- **THEN** a Dapr `Component` of type `state.redis` with `actorStateStore: "true"` metadata is
  rendered
- **AND** its top-level `scopes` field lists `dws-orchestrator`

#### Scenario: Dapr disabled

- **WHEN** `dapr.enabled=false`
- **THEN** no actor state store Component is rendered
