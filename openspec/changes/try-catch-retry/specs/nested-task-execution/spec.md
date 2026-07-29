## ADDED Requirements

### Requirement: Task lists execute as independent scopes
`dws-orchestrator` SHALL execute any task list — the workflow's top-level `do`, a `try` task's `try`
list, and a `catch.do` list — through one shared runner that treats each list as its own scope. The
runner SHALL resolve a task's `then` target only against the tasks declared in that same list, and
SHALL fail with a clear message when a directive names a task that the current scope does not
declare. Owning component: `dws-orchestrator`.

#### Scenario: Directive jumps within its own scope
- **WHEN** a task inside a nested list declares a `then` naming another task in the same list
- **THEN** execution continues at that task

#### Scenario: Directive cannot target another scope
- **WHEN** a task inside a nested list declares a `then` naming a task declared outside that list
- **THEN** the task fails with a message naming the unresolvable target

#### Scenario: Top-level behaviour is unchanged
- **WHEN** a definition contains only top-level tasks
- **THEN** it executes exactly as before the shared runner was introduced

### Requirement: `exit` completes the current scope, `end` completes the instance
`dws-orchestrator` SHALL treat the `end` directive as terminating the whole workflow instance from
any nesting depth, and the `exit` directive as completing only the scope that declares it. At the
top level the two SHALL have the same effect; inside a nested list, `exit` SHALL return control to
the enclosing task, which then continues normally. Owning component: `dws-orchestrator`.

#### Scenario: `exit` inside a nested list returns to the enclosing task
- **WHEN** a task inside a `try` list declares `then: exit`
- **THEN** the remaining tasks in that list are skipped
- **AND** the enclosing `try` task completes and the workflow continues after it

#### Scenario: `end` inside a nested list completes the instance
- **WHEN** a task inside a `try` list declares `then: end`
- **THEN** the workflow instance completes and no task after the enclosing `try` task runs

#### Scenario: Running off the end of a nested list returns to the enclosing task
- **WHEN** the last task in a nested list completes without a directive
- **THEN** control returns to the enclosing task rather than continuing into the outer list

### Requirement: Nesting depth is bounded
`dws-orchestrator` SHALL enforce a maximum task-nesting depth and SHALL fail the workflow with a
clear message when a definition exceeds it, rather than exhausting the call stack. Owning component:
`dws-orchestrator`.

#### Scenario: Excessive nesting fails with a clear message
- **WHEN** a definition nests task lists beyond the maximum supported depth
- **THEN** the workflow fails with a message naming the depth limit

### Requirement: Tasks are resolvable by name at any depth
`dws-orchestrator` SHALL resolve a task by name across the whole definition, including tasks
declared inside a `try` list or a `catch.do` list, so that every in-process activity can look up the
task it is evaluating. Owning component: `dws-orchestrator`.

#### Scenario: Nested task is found by name
- **WHEN** an in-process activity resolves a task declared inside a `try` list
- **THEN** the task is found and evaluated

#### Scenario: Unknown name still fails
- **WHEN** a name matches no task at any depth
- **THEN** the lookup fails with a message naming the missing task

### Requirement: Task names are unique across the whole definition
`dws-controller` SHALL reject a definition that declares the same task name more than once at any
depth, naming the duplicated name. Uniqueness is required because a `call` or `run` task's Dapr
app-id — and therefore its deployed Knative Service name — is derived from its task name alone, and
because tasks are resolved by name at runtime. Owning component: `dws-controller`.

#### Scenario: Duplicate names are rejected at compile time
- **WHEN** a posted definition declares two tasks with the same name, whether at the same depth or
  at different depths
- **THEN** compilation fails with an error naming the duplicated task name
- **AND** nothing is deployed

#### Scenario: Distinct names compile
- **WHEN** every task name in a definition is distinct
- **THEN** compilation proceeds

### Requirement: Nested `call`, `run`, `emit`, and `listen` tasks compile to their resources
`dws-controller` SHALL walk the task lists nested under a `try` task's `try` and `catch.do` keys when
compiling a definition, and SHALL emit the same step services and topic bindings for the tasks it
finds there as it does for top-level tasks of the same kind. Task lists nested under task types other
than `try`/`catch` SHALL NOT be walked by this capability. Owning component: `dws-controller`.

#### Scenario: Call task inside `try` deploys a step service
- **WHEN** a definition declares a `call: http` task inside a `try` list
- **THEN** a step service is compiled for it using the same image and naming rule as a top-level
  `call: http` task

#### Scenario: Run task inside `catch.do` deploys a step service
- **WHEN** a definition declares a `run: shell` task inside a `catch.do` list
- **THEN** a step service is compiled for it using the same image and naming rule as a top-level
  `run: shell` task

#### Scenario: Emit and listen inside `try` produce topic bindings
- **WHEN** a definition declares an `emit` or `listen` task inside a `try` list
- **THEN** the same topic binding is produced as for the equivalent top-level task

#### Scenario: Definitions without nesting compile unchanged
- **WHEN** a definition declares no `try` task
- **THEN** the compiled set of step services and topic bindings is unchanged
