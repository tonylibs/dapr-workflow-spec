## MODIFIED Requirements

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
