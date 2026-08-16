## RENAMED Requirements

- FROM: `### Requirement: Run tasks are dispatched over the existing service-invocation path`
- TO: `### Requirement: Run tasks are dispatched over the multi-app activity path`

## MODIFIED Requirements

### Requirement: Run tasks are dispatched over the multi-app activity path
`run: shell` and `run: script` tasks SHALL be invoked as Dapr Workflow **multi-app activities**:
the orchestrator resolves the target app-id from the kebab-cased task name and schedules the
canonical `Run` activity against it, carrying the current workflow data as the activity input and
the existing default retry policy in the activity options. Reaching that dispatch requires an
explicit `run` branch in the interpreter's dispatch — a `run` task satisfies no other branch — so
`dws-orchestrator` SHALL recognize `run` tasks and MUST NOT fail them as an unsupported type.
`call: openapi` continues to use the HTTP service-invocation path unchanged.

#### Scenario: Routing is name-derived
- **WHEN** a workflow contains a `run` task named `syncInventory`
- **THEN** the orchestrator schedules the `Run` activity targeting Dapr app-id `sync-inventory`
- **AND** it passes the current workflow data as the activity input

#### Scenario: A run task is dispatched rather than rejected
- **WHEN** the interpreter reaches a `run` task
- **THEN** it dispatches the task as a multi-app activity and the workflow instance continues
- **AND** it does not fail the instance with an unsupported-type error

#### Scenario: Lifecycle events label the task type correctly
- **WHEN** a `run` task starts or completes
- **THEN** the emitted `io.dws.task.*` event reports its task type as `run`
- **AND** not as `unknown`
