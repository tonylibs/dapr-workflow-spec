## Purpose

Chart-manage the Redis-backed actor/workflow state store Dapr Component ahead of
`dws-orchestrator` adopting the Dapr Workflow runtime, so it is ready to consume once that lands.

## ADDED Requirements

### Requirement: The actor state store Component renders when Dapr and Redis are available

`charts/dws` SHALL render `templates/actor-statestore-component.yaml`, a Dapr `Component` of
type `state.redis`, when `.Values.dapr.enabled` is `true` and a Redis connection is resolvable
(in-chart Redis enabled, or an external Redis configured). Its metadata SHALL include
`actorStateStore: "true"`, and its Redis connection metadata SHALL resolve to the chart's Redis
backend the same way as the `pubsub` Component.

Owning component: `charts/dws` (`templates/actor-statestore-component.yaml`).

#### Scenario: Default render

- **WHEN** `helm template charts/dws` is run with default values (`dapr.enabled=true`,
  `redis.enabled=true`)
- **THEN** a Dapr `Component` of type `state.redis` with `actorStateStore: "true"` metadata is
  rendered

#### Scenario: Dapr disabled

- **WHEN** `dapr.enabled=false`
- **THEN** no actor state store Component is rendered

#### Scenario: No Redis available

- **WHEN** `dapr.enabled=true`, `redis.enabled=false`, and no external Redis host is configured
- **THEN** no actor state store Component is rendered
