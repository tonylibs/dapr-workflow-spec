# activity-step-dispatch

## Purpose

How `dws-orchestrator` dispatches I/O steps: `call: http`, `run: shell`, and `run: script` are
invoked as Dapr Workflow multi-app activities targeting the task's app-id, while `call: openapi`
stays on the HTTP service-invocation path. Covers sub-kind detection and the shared failure-error
shape across both dispatch paths.

## Requirements

### Requirement: Migrated step kinds are dispatched as multi-app activities
`dws-orchestrator` SHALL dispatch `call: http`, `run: shell`, and `run: script` tasks as Dapr
Workflow **multi-app activities** targeting the task's Dapr app-id
(`TaskNaming.toKebabCase(taskName)`). The call MUST use `WorkflowTaskOptions` carrying both the
existing default retry policy and the target app-id, and MUST pass the current workflow data as
the activity input, using the returned value as the new data document. A `null`/empty activity
result MUST leave the data document unchanged.

#### Scenario: call:http task targets its app-id as an activity
- **WHEN** the interpreter reaches a `call: http` task named `checkInventory`
- **THEN** it schedules the canonical step activity with target app-id `check-inventory`
- **AND** it passes the current workflow data as the activity input
- **AND** the activity's returned value becomes the new workflow data

#### Scenario: run task is dispatched as an activity
- **WHEN** the interpreter reaches a `run: shell` or `run: script` task named `syncData`
- **THEN** it schedules the canonical step activity with target app-id `sync-data`
- **AND** the workflow instance continues rather than failing as an unsupported type

#### Scenario: empty activity result leaves data unchanged
- **WHEN** the dispatched step activity returns a `null` or empty result
- **THEN** the workflow data document is unchanged from the activity input

#### Scenario: configured retry policy is preserved
- **WHEN** a migrated step is dispatched as an activity
- **THEN** the activity options carry the same default retry policy used by the HTTP path

### Requirement: call:openapi stays on HTTP service invocation
`dws-orchestrator` SHALL continue to dispatch `call: openapi` tasks through the existing
`CallServiceActivity` HTTP service-invocation path (`POST /run`). The migration to multi-app
activity invocation MUST NOT change how `call: openapi` tasks are routed or invoked.

#### Scenario: openapi task uses the HTTP path
- **WHEN** the interpreter reaches a `call: openapi` task named `lookupPrice`
- **THEN** it invokes app-id `lookup-price` at `POST /run` via `CallServiceActivity`
- **AND** it does not schedule a multi-app activity for the task

### Requirement: Sub-kind is resolved from the SDK task accessors
`dws-orchestrator` SHALL distinguish `CALL_HTTP` from `CALL_OPENAPI`, and `RUN_SHELL`/`RUN_SCRIPT`
from other run forms, using the serverlessworkflow SDK sub-type accessors
(`getCallHTTP`/`getCallOpenAPI`, `getRunShell`/`getRunScript`) — the same distinction the
controller's compiler uses to assign `TaskKind`.

#### Scenario: http and openapi calls take different paths
- **WHEN** one definition contains both a `call: http` task and a `call: openapi` task
- **THEN** the `call: http` task is dispatched as a multi-app activity
- **AND** the `call: openapi` task is dispatched over HTTP service invocation

### Requirement: Activity dispatch failures produce the standard error shape
A failure of a dispatched step activity SHALL be classified into the same runtime error object
`{type, status, instance, title, detail}` produced for an HTTP step failure, so `catch` clauses
filter identically regardless of dispatch path. An upstream/transport-equivalent activity failure
MUST classify as a communication error matching today's `502` handling; a configuration/shaping
failure MUST classify as it does on the HTTP path.

#### Scenario: upstream activity failure is a communication error
- **WHEN** a dispatched step activity fails with an upstream/transport-equivalent fault
- **THEN** the interpreter surfaces a communication-kind error object
- **AND** a `catch` clause filtering on the communication kind matches it

#### Scenario: error shape matches the HTTP path
- **WHEN** an equivalent upstream failure occurs once on the activity path and once on the HTTP path
- **THEN** both produce an error object with the same `type`, `status`, and `title` fields
