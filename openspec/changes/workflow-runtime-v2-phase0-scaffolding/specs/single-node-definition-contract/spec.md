## ADDED Requirements

### Requirement: Single-node definition has a common envelope identifying the node
Every single-node definition payload SHALL include `workflow`, `version`, `nodeId`, and `kind`
(`"flow"` or `"step"`), regardless of which runtime consumes it.

#### Scenario: A definition missing a required envelope field is rejected
- **WHEN** a `dws-flow` or `dws-step` instance loads a single-node definition file missing
  `nodeId` or `kind`
- **THEN** the instance fails to start and logs which field is missing, rather than starting in a
  partially-initialized state

#### Scenario: nodeId must be a valid Dapr app ID
- **WHEN** a single-node definition's `nodeId` is not a valid DNS-1123 label (e.g. contains `.` or
  uppercase characters)
- **THEN** the instance fails to start and logs the invalid value

### Requirement: Flow node definitions carry their scope's task list and children's app IDs
A `kind: "flow"` definition SHALL include `scope` (one of `main`, `for`, `try`, `catch`,
`forkBranch`), `tasks` (that scope's own DSL 1.0 task objects, unmodified, in order), and
`children` (a map from task name to that task's compiled child node's Dapr app ID, covering every
task in `tasks` that compiles to its own Flow or Step node). A `catch` field (the attached catch
block's Dapr app ID) SHALL be present when the scope has one, and absent otherwise.

#### Scenario: A flow definition's children map covers every dispatched task
- **WHEN** a `dws-flow` instance loads a `kind: "flow"` definition whose `tasks` list includes a
  `call: http` leaf task and a nested `for` task
- **THEN** both task names appear as keys in `children`, each mapped to a Dapr app ID string

#### Scenario: A flow definition without an attached catch block omits the catch field
- **WHEN** a `dws-flow` instance loads a `kind: "flow"` definition for a scope with no `catch`
  block
- **THEN** the loaded definition has no `catch` field (not an empty string or null)

### Requirement: Step node definitions carry their single task and, for call/run, the function's app ID
A `kind: "step"` definition SHALL include `task` (the single DSL 1.0 task object this Step wraps,
unmodified). When `task.call` or `task.run` is set, the definition SHALL also include
`functionAppId` (the Dapr app ID of the underlying `dws-call-*`/`dws-run-*` Knative Service this
Step proxies to, per ADR 0001's `-fn` naming rule). For any other task kind (`set`, `switch`,
`wait`, `listen`, `emit`, `raise`), `functionAppId` SHALL be absent.

#### Scenario: A call task's step definition includes functionAppId
- **WHEN** a `dws-step` instance loads a `kind: "step"` definition whose `task` has `call: http`
  set
- **THEN** the loaded definition includes a `functionAppId` string field

#### Scenario: A set task's step definition has no functionAppId
- **WHEN** a `dws-step` instance loads a `kind: "step"` definition whose `task` has `set` set
- **THEN** the loaded definition has no `functionAppId` field

### Requirement: The contract is published as a checked-in JSON Schema
The single-node definition contract SHALL be expressed as a JSON Schema document checked in under
`openspec/schemas/`, covering both the `flow` and `step` shapes via a `kind`-discriminated union,
so that `dws-controller` (in a later phase) and both runtimes can validate against the same
machine-readable source of truth rather than three independent hand-written parsers agreeing only
by convention.

#### Scenario: The schema validates a well-formed flow definition
- **WHEN** the checked-in schema is used to validate a well-formed `kind: "flow"` sample document
- **THEN** validation succeeds

#### Scenario: The schema rejects a step definition with an unrecognized field shape
- **WHEN** the checked-in schema is used to validate a `kind: "step"` document that is missing
  `task`
- **THEN** validation fails
