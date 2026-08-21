# workflow-iteration

## Purpose

`dws-orchestrator`'s `for` task: iterating a task list once per element of a `for.in` collection,
binding `for.each`/`for.at` as scope-local jq variables, `while`-gated early termination, threading
the data document forward across iterations, and composing with `try`/`catch`/retry. Established in
`for-task` (OWS DSL roadmap Phase 2, slice 2.3).

## Requirements

### Requirement: `for` task iterates a task list over a collection
`dws-orchestrator` SHALL interpret a `for` task by evaluating its `for.in` expression to a
collection and running the task list under its `do` key once per element, in element order, and
SHALL NOT reject the task type. Owning component: `dws-orchestrator`.

#### Scenario: `for` is recognised
- **WHEN** a definition containing a `for` task is interpreted
- **THEN** no unsupported-task-type failure is raised for it

#### Scenario: Body runs once per element
- **WHEN** a `for` task's `for.in` evaluates to a collection of N elements and no `while`
  terminates the loop early
- **THEN** the task list under `do` runs N times, once per element, in element order
- **AND** the workflow continues by the `for` task's own `then` after the last iteration

#### Scenario: Empty collection runs the body zero times
- **WHEN** a `for` task's `for.in` evaluates to an empty collection
- **THEN** the task list under `do` is not run
- **AND** the `for` task completes with the data it received

### Requirement: Each iteration binds `for.each` and `for.at` as scope-local jq variables
`dws-orchestrator` SHALL bind the current element under the name declared by `for.each` (defaulting
to `item` when absent) and the current zero-based index under the name declared by `for.at`
(defaulting to `index` when absent), both as scope-local jq variables visible only to that
iteration's body and to `while` when evaluated for that iteration. The bindings SHALL NOT leak
out of the `for` task's scope. Owning component: `dws-orchestrator`.

#### Scenario: Element variable is bound to the current element
- **WHEN** an iteration runs
- **THEN** the jq variable named by `for.each` (or `$item` when `for.each` is absent) is bound to
  the current element inside that iteration's body

#### Scenario: Index variable is bound to the current zero-based index
- **WHEN** an iteration runs
- **THEN** the jq variable named by `for.at` (or `$index` when `for.at` is absent) is bound to
  the current zero-based index inside that iteration's body

#### Scenario: Bindings do not leak past the `for` task
- **WHEN** a task after the `for` task references the iteration variables
- **THEN** they are not defined for that task

### Requirement: `while` is re-evaluated per iteration and stops the loop when false
When a `for` task declares a sibling `while` expression, `dws-orchestrator` SHALL evaluate it at
the top of each iteration — after binding the iteration variables and before running the body —
and SHALL stop the loop without running that iteration's body when `while` evaluates to a falsy
value (jq truthiness: `null` and `false` are falsy, every other value is truthy). Owning
component: `dws-orchestrator`.

#### Scenario: Loop stops when `while` becomes false
- **WHEN** `while` evaluates to a falsy value at the top of an iteration
- **THEN** that iteration's body is not run
- **AND** no further iterations run
- **AND** the `for` task completes with the data as of the previous iteration's output

#### Scenario: `while` sees the iteration variables
- **WHEN** `while` references the variable named by `for.each` or `for.at`
- **THEN** it evaluates against that iteration's bindings

#### Scenario: Absent `while` does not stop the loop
- **WHEN** a `for` task declares no `while`
- **THEN** iteration is bounded only by the collection's length

### Requirement: Iterations thread the data document forward
`dws-orchestrator` SHALL pass each iteration's body-output data document as the next iteration's
input data. The final iteration's body-output data SHALL be the data the `for` task's own
`output`/`export` transforms receive. When the loop runs zero times, the input data SHALL flow
through unchanged. Owning component: `dws-orchestrator`.

#### Scenario: Iteration N + 1 sees iteration N's output
- **WHEN** iteration N completes with a body-output data document
- **THEN** iteration N + 1's body runs against that data document

#### Scenario: Final iteration's output is the `for` body's output
- **WHEN** the loop terminates (collection exhausted or `while` false)
- **THEN** the data document leaving the `for` task's body is the last iteration's output

#### Scenario: Zero iterations pass data through unchanged
- **WHEN** the loop runs zero times
- **THEN** the data document leaving the `for` task's body equals the data it received

### Requirement: `for.in` failing to evaluate to a collection fails the task
When `for.in` evaluates to a value that is not a collection (JSON array), `dws-orchestrator` SHALL
fail the `for` task with a message naming the `for` task, so the failure reaches the standard
task-failure and instance-failure paths unchanged. Owning component: `dws-orchestrator`.

#### Scenario: Non-array `for.in` fails the task
- **WHEN** `for.in` evaluates to a value that is not a JSON array
- **THEN** the `for` task fails with a message naming the task

### Requirement: `for.do` bodies resolve by task name across nested scopes
`dws-orchestrator` SHALL resolve a task nested under `for.do` by its declared name from within
any activity that looks tasks up by name, in the same way tasks nested under `try.try` and
`try.catch.do` are already resolvable. Owning component: `dws-orchestrator`.

#### Scenario: A `set`/`switch`/`raise` inside `for.do` resolves by name
- **WHEN** a task nested under `for.do` is dispatched
- **THEN** its declared name resolves against the pinned definition

### Requirement: `for` inside `try` composes with catch and retry
When a `for` task runs inside a `try` list, `dws-orchestrator` SHALL offer any failure originating
in `for` (its `for.in` evaluation, its `while` evaluation, or any task inside its `for.do` body)
to the enclosing `try` task's `catch` clause through the same static filtering
(`catch.errors.with`), dynamic filtering (`catch.when`/`catch.exceptWhen`), and retry machinery
that `workflow-error-handling` already defines for any other failure inside a `try` list. Owning
component: `dws-orchestrator`.

#### Scenario: Failure inside a `for` body is caught like any other failure
- **WHEN** a task inside `for.do` fails and the enclosing `try`'s `catch` clause matches the
  failure
- **THEN** the failure is caught
- **AND** the `try` task does not fail

### Requirement: `for` tasks deploy no additional resources
`dws-controller` SHALL NOT deploy any additional resource for a `for` task itself, in the same
category as `switch`, `set`, `wait`, and `raise`. `dws-controller` SHALL compile a `call`/`run`
task nested inside `for.do` into the same kind of `StepService` it would compile for an equivalent
top-level task, and SHALL compile an `emit`/`listen` task nested inside `for.do` into the same kind
of topic binding. Owning component: `dws-controller`.

#### Scenario: Definitions with an in-process-only `for` task compile unchanged
- **WHEN** a definition declares a `for` task whose `for.do` contains only tasks that themselves
  deploy no resources (`switch`, `set`, `wait`, `listen`, `emit`, `raise`, nested `for`, nested
  `try`/`fork` whose contents likewise deploy nothing)
- **THEN** the compiled set of step services and topic bindings is the same as if the `for` task
  were absent

#### Scenario: A `call` task inside `for.do` deploys a `StepService`
- **WHEN** a definition declares a `for` task with a `for.do` containing a `call` task
- **THEN** the compiled deployment plan includes a `StepService` for that task, the same as if it
  were declared at the top level

#### Scenario: Duplicate task names inside `for.do` are rejected at compile time
- **WHEN** a task name declared inside `for.do` collides with another task name declared anywhere
  else in the definition
- **THEN** compilation fails, naming both offending tasks
