## ADDED Requirements

### Requirement: Cross-app steps get a WorkflowAccessPolicy allowing the orchestrator
`dws-controller` SHALL synthesize one Dapr `WorkflowAccessPolicy` per activity-invoked step
(`CALL_HTTP`, `RUN_SHELL`, `RUN_SCRIPT_JS`, `RUN_SCRIPT_PYTHON`), scoped to that step's Dapr app-id,
whose single rule allows the workflow's orchestrator app-id to schedule the canonical `Run` activity
on that step. `CALL_OPENAPI` steps are HTTP-invoked and SHALL NOT get a policy. The policy MUST
carry the same `dws.io/*` labels as the rest of the stack so it is garbage-collected with its version.

#### Scenario: activity step gets a policy allowing the orchestrator
- **WHEN** the controller synthesizes the stack for a `CALL_HTTP`/`RUN_SHELL`/`RUN_SCRIPT` step named `syncInventory`
- **THEN** a `WorkflowAccessPolicy` is produced scoped to app-id `sync-inventory`
- **AND** its rule lists the orchestrator app-id as an allowed caller of the `Run` activity

#### Scenario: openapi step gets no policy
- **WHEN** the controller synthesizes the stack for a `CALL_OPENAPI` step
- **THEN** no `WorkflowAccessPolicy` is produced for that step

#### Scenario: policy is applied and garbage-collected with the stack
- **WHEN** a workflow version is applied and later drained
- **THEN** each step's `WorkflowAccessPolicy` is applied alongside its Knative Service
- **AND** it is deleted by the same `dws.io/*` label selector when the version is collected
