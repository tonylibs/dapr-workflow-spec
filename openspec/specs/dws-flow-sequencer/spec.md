# dws-flow-sequencer Specification

## Purpose
TBD - created by archiving change workflow-runtime-v2-phase3a-flow-core. Update Purpose after archive.
## Requirements
### Requirement: Single node, single workflow type
The Flow host SHALL load exactly one node definition at startup and register exactly one workflow type,
and SHALL fail startup when the definition is invalid.

#### Scenario: Invalid definition
- **WHEN** the definition fails validation
- **THEN** startup fails with a definition load error

### Requirement: Sequencer runs tasks in order
A node with scope `main` or `do` SHALL run its tasks in source order, passing each task's output data as
the next task's input data, and complete with the final data.

#### Scenario: Two tasks
- **WHEN** tasks `a` then `b` run and `a` returns data X
- **THEN** `b` receives X and the workflow output is `b`'s result

### Requirement: Then directives
After each task the Sequencer SHALL resolve `then`: absent or `continue` advances to the next task, a
task name jumps to that task in the same scope, `end` and `exit` complete the workflow with current
data. An unknown target SHALL fail with `flow references task '<t>', which is not declared in this task
scope`. Running past the last task SHALL complete the workflow. More than 10000 executed steps SHALL
fail with `workflow exceeded 10000 steps; check for a definition loop`.

#### Scenario: Jump forward
- **WHEN** task `a` has `then: c` and the list is `a, b, c`
- **THEN** `b` does not run

#### Scenario: Unknown target
- **WHEN** `then` names an undeclared task
- **THEN** the workflow fails with the not-declared message

### Requirement: Children called by type
A Step child SHALL be called as activity `Step` on the child's own app ID. A Flow child SHALL be called as
a child workflow on its own app ID with instance ID `<root instance>:<node path>:<iteration>`, where node
path is the child app ID. The Sequencer SHALL NOT call a function image directly.

#### Scenario: Step child
- **WHEN** a task is a `call`, `run`, `set` or `switch` task
- **THEN** activity `Step` is invoked with app ID `children[task]`

#### Scenario: Flow child instance ID
- **WHEN** a `for`, `try`, `fork`, `wait` or `listen` task runs at iteration `1.0`
- **THEN** a child workflow starts on `children[task]` with instance ID `<root>:<children[task]>:1.0`

### Requirement: Child envelope
Each child SHALL receive current workflow data, scope-local variables, the root workflow instance ID and
the iteration index, and SHALL return the new workflow data. Data-flow transforms SHALL NOT be applied.

#### Scenario: Untouched data
- **WHEN** a task defines `input.from` or `output.as`
- **THEN** the child still receives and returns data unmodified by the Sequencer

### Requirement: Failure propagation
A failing child SHALL fail the Sequencer with the child's failure message unchanged.

#### Scenario: Message preserved
- **WHEN** a child fails with message M
- **THEN** the node fails with message M

### Requirement: Task timeout
A task with `timeout` whose child call has not completed in time SHALL fail with
`task '<name>' timed out after <ISO-8601 duration>`.

#### Scenario: Timer wins
- **WHEN** a child does not complete within the timeout
- **THEN** the node fails with the timed-out message

### Requirement: Controller scopes routed
A node whose scope is `for`, `try-catch` or `fork` SHALL fail when run with
`config failure: scope '<scope>' is not implemented yet`, selected through one scope dispatch point.

#### Scenario: For scope
- **WHEN** a node with scope `for` runs
- **THEN** it fails with the not-implemented configuration failure

### Requirement: Replay safety
Workflow code SHALL be deterministic: no clock, randomness or I/O outside Dapr workflow calls.

#### Scenario: Replay
- **WHEN** a recorded history is replayed
- **THEN** the workflow issues the same commands and reaches the same result

