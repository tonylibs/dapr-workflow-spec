## ADDED Requirements

### Requirement: `fork` task runs its branches concurrently
`dws-orchestrator` SHALL interpret a `fork` task by starting each branch declared under
`fork.branches` as an independent, concurrently-executing unit, and SHALL NOT reject the task
type. Owning component: `dws-orchestrator`.

#### Scenario: `fork` is recognised
- **WHEN** a definition containing a `fork` task is interpreted
- **THEN** no unsupported-task-type failure is raised for it

#### Scenario: Branches run concurrently, not sequentially
- **WHEN** a `fork` task declares two or more branches, each performing an I/O step with
  observable latency
- **THEN** the branches' step invocations overlap in time rather than the second branch waiting
  for the first branch to complete

### Requirement: Each branch starts from the `fork` task's own input data, independently
`dws-orchestrator` SHALL start every branch from the same data document the `fork` task itself
received (after its own `input.from` transform, if any), and branches SHALL NOT observe each
other's data mutations during execution. Owning component: `dws-orchestrator`.

#### Scenario: Branches do not see each other's writes
- **WHEN** two branches each mutate the data document they were given
- **THEN** neither branch's mutation is visible to the other branch during execution

### Requirement: `compete: false` (the default) waits for every branch and returns their outputs as an ordered array
When `fork.compete` is `false` or absent, `dws-orchestrator` SHALL wait for every branch to
complete and SHALL return a JSON array of the branches' resulting data documents, ordered to match
the branches' declaration order in `fork.branches` (not their completion order). Owning component:
`dws-orchestrator`.

#### Scenario: All branches join before the `fork` task completes
- **WHEN** a `fork` task with `compete: false` declares three branches
- **THEN** the `fork` task does not complete until all three branches have completed

#### Scenario: Output array preserves declaration order regardless of completion order
- **WHEN** branches declared in order A, B, C complete in order C, A, B
- **THEN** the `fork` task's output array is `[A's data, B's data, C's data]`

### Requirement: `compete: true` returns the first branch to settle and abandons the rest
When `fork.compete` is `true`, `dws-orchestrator` SHALL complete the `fork` task with the data
document of whichever branch settles (completes or fails) first, and SHALL NOT wait for or surface
the outcome of any other branch. Owning component: `dws-orchestrator`.

#### Scenario: The `fork` task completes as soon as one branch settles
- **WHEN** a `fork` task with `compete: true` declares branches that would otherwise take
  different amounts of time to complete
- **THEN** the `fork` task completes with the first branch's outcome, without waiting for the
  others

#### Scenario: Losing branches' outcomes are not surfaced
- **WHEN** a losing branch later completes or fails, after the `fork` task has already completed
  with the winning branch's outcome
- **THEN** the losing branch's outcome does not affect the already-completed `fork` task or the
  workflow instance

### Requirement: `fork` does not thread `$context` between branches or back to its caller
`dws-orchestrator` SHALL pass each branch a copy of the `$context` document as it stood when the
`fork` task started, SHALL NOT merge branches' `export.as` writes into a shared `$context`, and
SHALL leave `$context` unchanged, as of `fork`'s start, for the task that runs after `fork`.
Owning component: `dws-orchestrator`.

#### Scenario: A branch's `export.as` write does not leak to sibling branches or past `fork`
- **WHEN** a branch performs `export.as` inside a `fork` task
- **THEN** neither a sibling branch nor a task after the `fork` task observes that write

### Requirement: `fork` branches resolve by task name across nested scopes
`dws-orchestrator` SHALL resolve a branch's task by its declared name from within any activity
that looks tasks up by name, in the same way tasks nested under `try.try`, `try.catch.do`, and
`for.do` are already resolvable. Owning component: `dws-orchestrator`.

#### Scenario: A branch's task resolves by name
- **WHEN** a branch under `fork.branches` is dispatched
- **THEN** its declared name resolves against the pinned definition

### Requirement: `fork` composes with `try`/`catch`/`retry`
`dws-orchestrator` SHALL offer a `compete: false` `fork` task's join failure, or a `compete: true`
`fork` task's winning-branch failure, to an enclosing `try` task's `catch` clause through the same
static filtering, dynamic filtering, and retry machinery `workflow-error-handling` already defines
for any other failure. Owning component: `dws-orchestrator`.

#### Scenario: A branch failure is caught like any other failure (`compete: false`)
- **WHEN** any branch of a `compete: false` `fork` task fails and the enclosing `try` task's
  `catch` clause matches the failure
- **THEN** the failure is caught and the `try` task does not fail

#### Scenario: A winning branch's failure is caught like any other failure (`compete: true`)
- **WHEN** the first branch to settle in a `compete: true` `fork` task fails, and the enclosing
  `try` task's `catch` clause matches the failure
- **THEN** the failure is caught and the `try` task does not fail

### Requirement: A branch may nest any task type, including `try`, `for`, and another `fork`
`dws-orchestrator` SHALL allow a `fork` branch's task to be any task type the interpreter supports,
including a container task type whose own body is itself dispatched through the same pipeline as a
top-level task. Owning component: `dws-orchestrator`.

#### Scenario: A branch containing a `try` task runs its own retry/catch machinery
- **WHEN** a `fork` branch's task is a `try` task whose body fails and is retried
- **THEN** the retry runs entirely within that branch, independent of sibling branches

### Requirement: `fork` branches deploy resources exactly like their top-level equivalents
`dws-controller` SHALL compile a `call`/`run` task nested inside a `fork` branch into the same kind
of `StepService` it would compile for an equivalent top-level task, and SHALL compile an
`emit`/`listen` task nested inside a `fork` branch into the same kind of topic binding. Owning
component: `dws-controller`.

#### Scenario: A `call` task inside a `fork` branch deploys a `StepService`
- **WHEN** a definition declares a `fork` task with a branch containing a `call` task
- **THEN** the compiled deployment plan includes a `StepService` for that branch's `call` task,
  the same as if it were declared at the top level

#### Scenario: Duplicate task names across branches are rejected at compile time
- **WHEN** two branches of the same or different `fork` tasks declare tasks sharing a name, or a
  branch's task name collides with a task name declared elsewhere in the definition
- **THEN** compilation fails, naming both offending tasks
