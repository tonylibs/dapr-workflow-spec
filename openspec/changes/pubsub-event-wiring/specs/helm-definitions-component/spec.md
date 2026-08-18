## Purpose

Chart-manage the `dws-definitions` Dapr Configuration Component that `dws-orchestrator` reads
workflow definitions through, replacing the hand-applied manifest that points at an unmanaged
Redis host.

## ADDED Requirements

### Requirement: The dws-definitions Component renders when Dapr is enabled

`charts/dws` SHALL render `templates/definitions-component.yaml`, a Dapr `Component` of type
`configuration.redis` named `dws-definitions`, whenever `.Values.dapr.enabled` is `true`. Its
Redis connection metadata SHALL resolve to the chart's Redis backend the same way as the `pubsub`
Component (built-in or external, per `helm-redis-dependency`).

The Component SHALL declare `scopes: [dws-orchestrator]` at the top level so only orchestrator
pods' daprd sidecars initialize it — admin and controller sidecars never touch it, since only
the orchestrator reads workflow definitions through the Configuration API.

Owning component: `charts/dws` (`templates/definitions-component.yaml`).

#### Scenario: Default render

- **WHEN** `helm template charts/dws` is run with default values (`dapr.enabled=true`)
- **THEN** a Dapr `Component` named `dws-definitions` of type `configuration.redis` is rendered
- **AND** its top-level `scopes` field lists `dws-orchestrator`

#### Scenario: Dapr disabled

- **WHEN** `dapr.enabled=false`
- **THEN** no `dws-definitions` Component is rendered
