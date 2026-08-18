## Purpose

Chart-manage the `dws-definitions` Dapr Configuration Component that `dws-orchestrator` reads
workflow definitions through, replacing the hand-applied manifest that points at an unmanaged
Redis host.

## ADDED Requirements

### Requirement: The dws-definitions Component renders when Dapr and Redis are available

`charts/dws` SHALL render `templates/definitions-component.yaml`, a Dapr `Component` of type
`configuration.redis` named `dws-definitions`, when `.Values.dapr.enabled` is `true` and a Redis
connection is resolvable (in-chart Redis enabled, or an external Redis configured). Its Redis
connection metadata SHALL resolve to the chart's Redis backend the same way as the `pubsub`
Component.

Owning component: `charts/dws` (`templates/definitions-component.yaml`).

#### Scenario: Default render

- **WHEN** `helm template charts/dws` is run with default values (`dapr.enabled=true`,
  `redis.enabled=true`)
- **THEN** a Dapr `Component` named `dws-definitions` of type `configuration.redis` is rendered

#### Scenario: Dapr disabled

- **WHEN** `dapr.enabled=false`
- **THEN** no `dws-definitions` Component is rendered

#### Scenario: No Redis available

- **WHEN** `dapr.enabled=true`, `redis.enabled=false`, and no external Redis host is configured
- **THEN** no `dws-definitions` Component is rendered
