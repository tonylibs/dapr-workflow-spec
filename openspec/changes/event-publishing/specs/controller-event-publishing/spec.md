## ADDED Requirements

### Requirement: Publish definition lifecycle events from the apply pass
`dws-controller` SHALL publish a definition event when the apply pass materializes a definition.
It MUST publish `io.dws.definition.created` when the definition version was newly created (the
immutable definition ConfigMap was absent and is created), and `io.dws.definition.updated` when the
apply pass resolves to an already-present definition version.

#### Scenario: New definition version applied
- **WHEN** a definition version is applied that was not previously present
- **THEN** the controller publishes `io.dws.definition.created` with `workflow`, `version`, `createdAt`

#### Scenario: Existing definition version re-applied
- **WHEN** an apply pass resolves to a definition version already present in the cluster
- **THEN** the controller publishes `io.dws.definition.updated` with `workflow`, `version`, `createdAt`

### Requirement: Publish deployment outcome events
`dws-controller` SHALL publish `io.dws.deployment.applied` after an apply pass completes successfully
and `io.dws.deployment.failed` when an apply pass throws. The `applied` payload MUST include the
step-service names and the orchestrator app-id from the plan; the `failed` payload MUST include the
`error`.

#### Scenario: Apply succeeds
- **WHEN** `StackApplier.apply` completes without throwing
- **THEN** the controller publishes `io.dws.deployment.applied` with `workflow`, `version`,
  `stepServices`, and `orchestratorAppId`

#### Scenario: Apply fails
- **WHEN** `StackApplier.apply` throws
- **THEN** the controller publishes `io.dws.deployment.failed` with `workflow`, `version`,
  `stepServices`, `orchestratorAppId`, and `error`
- **AND** the original exception still propagates to the caller unchanged

### Requirement: Publish garbage-collection events
`dws-controller` SHALL publish `io.dws.deployment.drained` when it marks a superseded orchestrator
version for drain, and `io.dws.deployment.collected` when it garbage-collects a drained version.
Both payloads MUST include `workflow`, `version`, and `orchestratorAppId`.

#### Scenario: Superseded version marked for drain
- **WHEN** the controller annotates a superseded orchestrator version for drain
- **THEN** it publishes `io.dws.deployment.drained` with `workflow`, `version`, `orchestratorAppId`

#### Scenario: Drained version collected
- **WHEN** the controller deletes the resources of a drained version
- **THEN** it publishes `io.dws.deployment.collected` with `workflow`, `version`, `orchestratorAppId`

### Requirement: Publishing is a fire-and-forget side effect
Event publishing in `dws-controller` SHALL be fire-and-forget: a failure to publish MUST NOT fail
the apply pass, MUST NOT alter the resources deployed, and MUST NOT introduce any persistence. The
cluster remains the single source of truth; events are a derived signal only.

#### Scenario: Publish failure does not break apply
- **WHEN** the event publish call fails (e.g. the pub/sub component is unavailable)
- **THEN** the apply pass still returns its normal `ApplyResult`
- **AND** no cluster resource is left in a different state than if publishing had succeeded

#### Scenario: No persistence added
- **WHEN** the controller answers any `GET`
- **THEN** it still reads exclusively from live cluster state selected by `dws.io/*` labels
