# orchestrator-event-publishing

## Purpose

`dws-orchestrator`'s publishing of instance and task lifecycle events to the shared `dws.events`
topic, scheduled through a Dapr workflow activity so publishing stays deterministic under replay.
See `docs/events.md` for the shared envelope and event catalog, and `lifecycle-events` for the
cross-component contract.

## Requirements

### Requirement: Publish instance lifecycle events
`dws-orchestrator` SHALL publish `io.dws.instance.started` once at the beginning of interpreting a
workflow instance, and exactly one terminal instance event at the end: `io.dws.instance.completed`
when the interpreter loop completes normally, or `io.dws.instance.failed` when it terminates with an
error. Payloads MUST include `instanceId`, `workflow`, `version`, and `appId`.

#### Scenario: Instance starts
- **WHEN** the interpreter begins executing an instance
- **THEN** the orchestrator publishes `io.dws.instance.started` with `instanceId`, `workflow`,
  `version`, `appId`, and `startedAt`

#### Scenario: Instance completes normally
- **WHEN** the interpreter loop completes without error
- **THEN** the orchestrator publishes exactly one `io.dws.instance.completed` (and no
  `io.dws.instance.failed`) with `endedAt`

#### Scenario: Instance fails
- **WHEN** the interpreter loop terminates with an error
- **THEN** the orchestrator publishes exactly one `io.dws.instance.failed` with `error` and `endedAt`
- **AND** the original error still propagates

### Requirement: Publish task lifecycle events
`dws-orchestrator` SHALL publish `io.dws.task.started` before dispatching each task item in the
interpreter loop, `io.dws.task.completed` after it dispatches successfully, and
`io.dws.task.failed` when the dispatch throws. Payloads MUST include `instanceId`, `taskName`,
`taskType`, and `timestamp`; `failed` MUST include `error`.

#### Scenario: Task succeeds
- **WHEN** a task item is dispatched and returns
- **THEN** the orchestrator publishes `io.dws.task.started` before dispatch and
  `io.dws.task.completed` after, each with `instanceId`, `taskName`, `taskType`, `timestamp`

#### Scenario: Task fails
- **WHEN** a task item dispatch throws
- **THEN** the orchestrator publishes `io.dws.task.failed` with `error`

### Requirement: Publish only through a Dapr activity
The event publish for orchestrator lifecycle events SHALL be performed through a Dapr workflow
activity (mirroring the existing `EmitEventActivity`), never by calling the Dapr client directly
inside `InterpreterWorkflow.execute`. The workflow method MUST remain deterministic under Dapr
Workflow replay: any timestamp or identifier placed in an event MUST be derived from
replay-safe workflow context values (e.g. the workflow's current instant and instance id), not from
wall-clock or random sources read inside the workflow method.

#### Scenario: Publish is scheduled as an activity
- **WHEN** the interpreter needs to publish a lifecycle event
- **THEN** it schedules the admin-event activity via the workflow context
- **AND** it does not call the Dapr client directly from `execute`

#### Scenario: Deterministic under replay
- **WHEN** the workflow is replayed
- **THEN** the event timestamps and ids are identical to the original execution
- **AND** are sourced from the workflow context, not `Instant.now()` or a random generator

### Requirement: Instance events carry the owning app-id
Orchestrator lifecycle events SHALL include the `appId` of the orchestrator that owns the instance,
sourced from orchestrator configuration, so a consumer can resolve which orchestrator deployment
produced an instance.

#### Scenario: appId is resolvable from config
- **WHEN** an orchestrator lifecycle event is published
- **THEN** its payload `appId` reflects the orchestrator's configured application identity
</content>
