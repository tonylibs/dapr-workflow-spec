# workflow-error-handling

## Purpose

`dws-orchestrator`'s `try`/`catch` error handling: running a task list under `try`, matching a
failure against `catch`'s static/dynamic filters, binding the caught error as a scope-local
expression variable, retrying with backoff/jitter/limits, and running a `catch.do` recovery block.
Also covers `dws-controller`'s compile-time handling of tasks nested under `try`/`catch.do`.
Established in `try-catch-retry` (OWS DSL roadmap Phase 2, slice 1).

## Requirements

### Requirement: `try` task runs its inner task list
`dws-orchestrator` SHALL interpret a `try` task by running the task list under its `try` key as a
task scope, and SHALL NOT reject the task type. When every task in the list completes, the `try`
task SHALL complete with the list's resulting data document and continue by its own `then`.

#### Scenario: Successful try body completes the task
- **WHEN** a `try` task's inner task list runs to completion without failing
- **THEN** the `try` task completes with the data produced by the last inner task
- **AND** the workflow continues with the `try` task's own `then`

#### Scenario: `try` is no longer unsupported
- **WHEN** a definition containing a `try` task is interpreted
- **THEN** no unsupported-task-type failure is raised for it

### Requirement: Failure in the try body is offered to the catch clause
When a task inside the `try` list fails, `dws-orchestrator` SHALL synthesise a runtime error object
for the failure and evaluate it against the `catch` clause. When the clause does not catch the
error, the original failure SHALL propagate unchanged, so the `try` task fails through the same
task-failure and instance-failure path as any other task.

#### Scenario: Uncaught error propagates unchanged
- **WHEN** a task inside `try` fails and the `catch` clause does not match the error
- **THEN** the `try` task fails with the original failure detail
- **AND** the workflow instance fails through the standard task-failure path

#### Scenario: Caught error does not fail the try task
- **WHEN** a task inside `try` fails and the `catch` clause matches the error
- **THEN** the `try` task does not fail
- **AND** the workflow continues after the `try` task

### Requirement: Runtime error object carries the DSL's five error fields
`dws-orchestrator` SHALL represent a caught failure as a JSON object with the fields `type`,
`status`, `instance`, `title`, and `detail`. `type` SHALL identify the failure class — a
transform/validation failure, a step-service communication failure, or any other runtime failure —
`status` SHALL carry the upstream HTTP status when one is recoverable and a per-class default
otherwise, `instance` SHALL identify the failing task's location in the definition, and `detail`
SHALL carry the failure detail. RFC 7807 Problem Details formatting and the standard Open Workflow
Specification error-type catalogue are out of scope for this capability.

#### Scenario: Step-service failure is a communication error
- **WHEN** a `call` task inside `try` fails because its step service returned an upstream failure
- **THEN** the error object's `type` identifies a communication failure
- **AND** its `status` is the upstream HTTP status when one is recoverable

#### Scenario: Data-flow failure is a validation error
- **WHEN** a task inside `try` fails its `output.schema` validation
- **THEN** the error object's `type` identifies a validation failure
- **AND** its `detail` names the offending field

#### Scenario: Error identifies the failing task
- **WHEN** any task inside `try` fails
- **THEN** the error object's `instance` identifies that task rather than the enclosing `try` task

### Requirement: Static error filtering by `catch.errors.with`
`dws-orchestrator` SHALL catch an error only when every field present in `catch.errors.with` equals
the corresponding field of the runtime error object. A field absent from the filter SHALL NOT
constrain the match. An integer filter field whose value is `0` SHALL be treated as absent, because
the parsed model cannot distinguish an omitted integer from an explicit zero.

#### Scenario: Matching type and status is caught
- **WHEN** `catch.errors.with` declares a `type` and `status` that both equal the error's
- **THEN** the error is caught

#### Scenario: Non-matching status is not caught
- **WHEN** `catch.errors.with` declares a `status` that differs from the error's
- **THEN** the error is not caught and the failure propagates

#### Scenario: Empty catch clause catches everything
- **WHEN** a `catch` clause declares no `errors`, no `when`, and no `exceptWhen`
- **THEN** any error raised in the try body is caught

### Requirement: Dynamic filtering by `catch.when` and exclusion by `catch.exceptWhen`
`dws-orchestrator` SHALL catch an error only when the static filter matches **and** `catch.when` is
absent or evaluates truthy **and** `catch.exceptWhen` is absent or evaluates falsy. Both expressions
SHALL be evaluated in the jq dialect with the workflow context available as `$context` and the error
available under the name given by `catch.as`.

#### Scenario: `when` gates a statically matched error
- **WHEN** the static filter matches but `catch.when` evaluates falsy
- **THEN** the error is not caught and the failure propagates

#### Scenario: `exceptWhen` vetoes a match
- **WHEN** the static filter matches and `catch.when` is absent but `catch.exceptWhen` evaluates truthy
- **THEN** the error is not caught and the failure propagates

#### Scenario: Condition reads the error variable
- **WHEN** `catch.when` references the error by the name declared in `catch.as`
- **THEN** it evaluates against the runtime error object

### Requirement: Caught error is bound as a scope-local expression variable
`dws-orchestrator` SHALL bind the caught error as a runtime-expression variable named by `catch.as`,
defaulting to `error`, visible to `catch.when`/`catch.exceptWhen`, to the retry policy's
`when`/`exceptWhen`, and to every runtime expression evaluated by a task inside `catch.do`. The
error SHALL NOT be merged into the data document and SHALL NOT be written into the workflow context,
so it is not visible outside the `catch` scope and never reaches the instance's completion output.

#### Scenario: Recovery task reads the error
- **WHEN** a task inside `catch.do` evaluates an expression referencing the error variable
- **THEN** the expression sees the runtime error object

#### Scenario: Custom variable name is honoured
- **WHEN** `catch.as` declares a name other than `error`
- **THEN** the error is bound under that name and not under `error`

#### Scenario: Error does not leak past the catch scope
- **WHEN** a `try` task handles an error and the workflow continues to a later task
- **THEN** neither the data document nor the workflow context contains the error object

### Requirement: Retry policy resolved inline or by name
`dws-orchestrator` SHALL accept `catch.retry` either as an inline retry policy or as a string naming
a policy in the definition's document-level `use.retries` set, and SHALL apply the two forms
identically. A name that does not resolve SHALL fail the task with a message naming the missing
policy.

#### Scenario: Inline policy is applied
- **WHEN** `catch.retry` is an inline policy object
- **THEN** retries follow that policy

#### Scenario: Named policy is resolved from `use.retries`
- **WHEN** `catch.retry` names a policy defined under the document's `use.retries`
- **THEN** retries follow the named policy identically to the same policy written inline

#### Scenario: Unresolvable policy name fails loudly
- **WHEN** `catch.retry` names a policy that `use.retries` does not define
- **THEN** the task fails with a message naming the missing policy

### Requirement: Retry re-runs the whole try list after a durable delay
When the caught error is retryable, `dws-orchestrator` SHALL wait for the policy's computed delay
using a durable timer and then re-run the **entire** `try` task list from its first task, against the
`try` task's original transformed input. Each attempt SHALL be counted, and the retry decision SHALL
be made so that it is stable across workflow replay.

#### Scenario: Retry succeeds on a later attempt
- **WHEN** a task inside `try` fails on its first attempt and succeeds on the next
- **THEN** the whole try list is re-run
- **AND** the `try` task completes with the successful attempt's data

#### Scenario: Attempt starts from the original input
- **WHEN** an attempt fails after an earlier inner task has already transformed the data
- **THEN** the next attempt starts from the `try` task's original transformed input, not from the
  partial data of the failed attempt

#### Scenario: Retry conditions gate the retry
- **WHEN** the retry policy declares a `when` that evaluates falsy, or an `exceptWhen` that
  evaluates truthy
- **THEN** no further attempt is made and the recovery path is taken

### Requirement: Backoff, jitter, and retry limits
`dws-orchestrator` SHALL compute an attempt's delay from the policy's `delay` and `backoff`:
`constant` (or absent) SHALL use the delay unchanged, `linear` SHALL scale it by the attempt number,
and `exponential` SHALL double it per attempt. When `jitter` is declared, a random duration drawn
from `[jitter.from, jitter.to]` SHALL be added. Retrying SHALL stop when `limit.attempt.count`
attempts have been made or when the elapsed time since the first failure exceeds `limit.duration`.
An attempt count of `0` SHALL be treated as absent. `limit.attempt.duration` is a per-attempt timeout
and SHALL be rejected with a message naming it as unsupported rather than silently ignored.

#### Scenario: Exponential backoff grows the delay
- **WHEN** a policy declares `exponential` backoff and a base delay
- **THEN** each successive attempt waits twice as long as the previous one

#### Scenario: Attempt limit ends retrying
- **WHEN** a policy declares `limit.attempt.count` and every attempt fails
- **THEN** no more than that many attempts are made before the recovery path is taken

#### Scenario: Duration limit ends retrying
- **WHEN** a policy declares `limit.duration` and the elapsed time since the first failure exceeds it
- **THEN** no further attempt is made and the recovery path is taken

#### Scenario: Jitter does not break replay
- **WHEN** a workflow instance with a jittered retry is replayed
- **THEN** the same delay is used as on the original execution

#### Scenario: Per-attempt duration limit is rejected
- **WHEN** a retry policy declares `limit.attempt.duration`
- **THEN** the task fails with a message naming it as an unsupported knob

### Requirement: Recovery block runs when retries are exhausted
When the error is caught and no further retry applies, `dws-orchestrator` SHALL run the task list
under `catch.do` as a task scope, with the error bound as an expression variable. Its resulting data
document SHALL become the `try` task's raw output, and the `try` task SHALL then complete and
continue by its own `then`. When `catch.do` is absent, the `try` task SHALL complete with the data as
of the failure without running any recovery tasks. A failure inside `catch.do` SHALL propagate,
failing the `try` task.

#### Scenario: Recovery output becomes the try task's output
- **WHEN** `catch.do` runs and produces a data document
- **THEN** that document is the `try` task's raw output and feeds the `try` task's own `output.as`

#### Scenario: Catch without `do` completes the task
- **WHEN** a `catch` clause matches an error and declares no `do`
- **THEN** no recovery task runs and the `try` task completes

#### Scenario: Failure inside the recovery block propagates
- **WHEN** a task inside `catch.do` fails
- **THEN** the `try` task fails with that failure and the workflow instance fails

### Requirement: Try and catch bodies use the standard per-task pipeline
Every task inside `try` and inside `catch.do` SHALL be executed through the same dispatch path as a
top-level task, so its `input.from`/`input.schema`, body, `output.as`/`output.schema`, and
`export.as`/`export.schema` are applied identically, and its lifecycle events are published
identically. The `try` task itself SHALL also be wrapped by that pipeline as an ordinary task.

#### Scenario: Nested task's data flow is applied
- **WHEN** a task inside `try` declares `input.from` and `output.as`
- **THEN** both are applied exactly as they would be for a top-level task

#### Scenario: Nested task exports to the workflow context
- **WHEN** a task inside `catch.do` declares `export.as`
- **THEN** the workflow context it writes is visible to later tasks outside the `try` task

#### Scenario: Nested tasks publish lifecycle events
- **WHEN** tasks inside `try` run
- **THEN** each publishes its own task-started and task-completed or task-failed events

#### Scenario: Handled error does not report the try task as failed
- **WHEN** an inner task fails and the error is caught and handled
- **THEN** the inner task is reported failed
- **AND** the `try` task is reported completed rather than failed

### Requirement: Try and catch bodies run as their own task scope
`dws-orchestrator` SHALL run the `try` list and the `catch.do` list each as its own task scope. A
task's `then` target SHALL resolve only against the tasks declared in the same list, and a directive
naming a task the scope does not declare SHALL fail with a message naming the unresolvable target.
`exit` SHALL complete only the scope that declares it, returning control to the enclosing `try` task;
`end` SHALL complete the whole workflow instance from any depth. The orchestrator SHALL enforce a
maximum nesting depth and fail with a clear message when a definition exceeds it, and SHALL resolve a
task by name at any depth so in-process activities can look up nested tasks. Scope rules for task
types other than `try`/`catch` are out of scope for this capability. Owning component:
`dws-orchestrator`.

#### Scenario: Directive jumps within the try list
- **WHEN** a task inside a `try` list declares a `then` naming another task in the same list
- **THEN** execution continues at that task

#### Scenario: Directive cannot leave the scope
- **WHEN** a task inside a `try` list declares a `then` naming a task declared outside that list
- **THEN** the task fails with a message naming the unresolvable target

#### Scenario: `exit` returns to the enclosing try task
- **WHEN** a task inside a `try` list declares `then: exit`
- **THEN** the remaining tasks in that list are skipped
- **AND** the enclosing `try` task completes and the workflow continues after it

#### Scenario: `end` completes the instance from inside a try list
- **WHEN** a task inside a `try` list declares `then: end`
- **THEN** the workflow instance completes and no task after the enclosing `try` task runs

#### Scenario: Running off the end of the try list returns to the enclosing task
- **WHEN** the last task in a `try` list completes without a directive
- **THEN** control returns to the `try` task rather than continuing into the outer list

#### Scenario: Excessive nesting fails with a clear message
- **WHEN** a definition nests `try` tasks beyond the maximum supported depth
- **THEN** the workflow fails with a message naming the depth limit

#### Scenario: Nested task is resolvable by name
- **WHEN** an in-process activity resolves a task declared inside a `try` or `catch.do` list
- **THEN** the task is found and evaluated

#### Scenario: Top-level execution is unchanged
- **WHEN** a definition contains no `try` task
- **THEN** it executes exactly as it did before this capability

### Requirement: Tasks nested in `try` and `catch.do` compile to their resources
`dws-controller` SHALL walk the task lists nested under a `try` task's `try` and `catch.do` keys when
compiling a definition, emitting the same step services and topic bindings for the tasks it finds
there as for top-level tasks of the same kind. Task lists nested under task types other than
`try`/`catch` SHALL NOT be walked. `dws-controller` SHALL additionally reject a definition that
declares the same task name more than once at any depth, naming the duplicated name — a `call` or
`run` task's Dapr app-id, and therefore its deployed Knative Service name, is derived from its task
name alone, and tasks are resolved by name at runtime. Owning component: `dws-controller`.

#### Scenario: Call task inside `try` deploys a step service
- **WHEN** a definition declares a `call: http` task inside a `try` list
- **THEN** a step service is compiled for it using the same image and naming rule as a top-level
  `call: http` task

#### Scenario: Run task inside `catch.do` deploys a step service
- **WHEN** a definition declares a `run: shell` task inside a `catch.do` list
- **THEN** a step service is compiled for it using the same image and naming rule as a top-level
  `run: shell` task

#### Scenario: Emit and listen nested in a try task produce topic bindings
- **WHEN** a definition declares an `emit` or `listen` task inside a `try` or `catch.do` list
- **THEN** the same topic binding is produced as for the equivalent top-level task

#### Scenario: Duplicate names are rejected at compile time
- **WHEN** a posted definition declares two tasks with the same name, whether at the same depth or
  at different depths
- **THEN** compilation fails with an error naming the duplicated task name
- **AND** nothing is deployed

#### Scenario: Definitions without a try task compile unchanged
- **WHEN** a definition declares no `try` task
- **THEN** the compiled set of step services and topic bindings is unchanged

### Requirement: Catch-path flow directive is not supported
`dws-orchestrator` SHALL continue after a handled error using the `try` task's own `then`. The
`catch.then` directive defined by the Open Workflow Specification is not available in the pinned SDK
model and SHALL NOT be honoured by this capability.

#### Scenario: Handled error follows the try task's `then`
- **WHEN** a `try` task handles an error and declares its own `then`
- **THEN** the workflow continues at that directive's target

### Requirement: `raise` task constructs and fails with an author-defined error
`dws-orchestrator` SHALL interpret a `raise` task by evaluating its configured error and failing the
task with that error, and SHALL NOT reject the task type. The error SHALL carry the same five fields
(`type`, `status`, `instance`, `title`, `detail`) this capability already defines for an implicitly
synthesised error. Owning component: `dws-orchestrator`.

#### Scenario: `raise` is recognised
- **WHEN** a definition containing a `raise` task is interpreted
- **THEN** no unsupported-task-type failure is raised for it

#### Scenario: `raise` fails the task with its configured error
- **WHEN** a `raise` task runs
- **THEN** the task fails
- **AND** the failure's error object carries the fields the `raise` task's `error` configuration
  resolves to

### Requirement: Raised error fields resolve literal or expression values
`dws-orchestrator` SHALL resolve each of a raised error's `type`, `instance`, `title`, and `detail`
fields according to whether the definition declares it as a literal value or a runtime expression. A
literal value SHALL be used unchanged. A runtime expression SHALL be evaluated in the jq dialect
against the task's current data, with the workflow context available as `$context`. Owning
component: `dws-orchestrator`.

#### Scenario: Literal field is used unchanged
- **WHEN** a raised error field is declared as a literal value
- **THEN** the resulting error object carries that value unchanged

#### Scenario: Expression field is evaluated
- **WHEN** a raised error field is declared as a runtime expression
- **THEN** the resulting error object carries the expression's evaluated result

#### Scenario: Expression field reads the task's data
- **WHEN** a raised error field's expression references the task's current data
- **THEN** it evaluates against that data, identically to any other runtime expression in the
  definition

### Requirement: Raised error status is a literal value
`dws-orchestrator` SHALL use a raised error's `status` exactly as declared. A runtime-expression form
for `status` is not available in the pinned Open Workflow Specification SDK model and SHALL NOT be
supported by this capability. Owning component: `dws-orchestrator`.

#### Scenario: Declared status is used verbatim
- **WHEN** a `raise` task declares a `status`
- **THEN** the resulting error object's `status` equals the declared value

### Requirement: Raised error `instance` defaults to the raising task's location
`dws-orchestrator` SHALL use a raised error's declared `instance` when the `raise` task's
configuration provides one. When no `instance` is declared, `dws-orchestrator` SHALL set `instance`
to a JSON-Pointer-shaped reference identifying the raising task, consistent with how this capability
sets `instance` for an implicitly synthesised error. Owning component: `dws-orchestrator`.

#### Scenario: Declared instance is honoured
- **WHEN** a `raise` task declares an `instance`
- **THEN** the resulting error object's `instance` equals the declared value

#### Scenario: Absent instance identifies the raising task
- **WHEN** a `raise` task declares no `instance`
- **THEN** the resulting error object's `instance` identifies the raising task

### Requirement: Named error definitions resolve from `use.errors`
`dws-orchestrator` SHALL accept a `raise` task's error either as an inline error definition or as a
string naming an entry in the definition's document-level `use.errors` set, and SHALL apply the two
forms identically. A name that does not resolve SHALL fail the task with a message naming the
missing error definition. Owning component: `dws-orchestrator`.

#### Scenario: Inline error definition is applied
- **WHEN** a `raise` task declares an inline error definition
- **THEN** the raised error carries that definition's fields, resolved per the rules above

#### Scenario: Named error definition is resolved from `use.errors`
- **WHEN** a `raise` task's error names a definition under the document's `use.errors`
- **THEN** the raised error is identical to the same definition written inline

#### Scenario: Unresolvable error name fails loudly
- **WHEN** a `raise` task's error names a definition that `use.errors` does not define
- **THEN** the task fails with a message naming the missing error definition

### Requirement: Raised error survives error classification unmodified
`dws-orchestrator` SHALL deliver a raised error's `type`, `status`, `instance`, `title`, and `detail`
unchanged to any consumer of this capability's runtime error object — in particular, `catch`'s error
classification SHALL NOT reassign or overwrite any field of a raised error. Owning component:
`dws-orchestrator`.

#### Scenario: Raised error's type is not reclassified
- **WHEN** a `raise` task's error is offered to a `catch` clause
- **THEN** the error object's `type` is the value the `raise` task's configuration resolved to, not
  a value derived from classifying the failure

#### Scenario: Raised error's title and detail are preserved
- **WHEN** a `raise` task's error propagates to any consumer of the runtime error object
- **THEN** its `title` and `detail` equal the values the `raise` task's configuration resolved to

### Requirement: Raised error inside `try` is offered to that try's catch clause
When a `raise` task runs inside a `try` list, `dws-orchestrator` SHALL offer its error to the
enclosing `try` task's `catch` clause through the same static filtering (`catch.errors.with`),
dynamic filtering (`catch.when`/`catch.exceptWhen`), and retry machinery this capability already
defines for any other failure inside a `try` list. Owning component: `dws-orchestrator`.

#### Scenario: Raised error is caught like a real failure
- **WHEN** a `raise` task inside a `try` list runs and the enclosing `catch` clause matches its error
- **THEN** the error is caught
- **AND** the `try` task does not fail

#### Scenario: Raised error is filtered like a real failure
- **WHEN** a `raise` task inside a `try` list runs and `catch.errors.with` does not match its error
- **THEN** the error is not caught and the failure propagates

#### Scenario: Raised error can trigger a retry
- **WHEN** a `raise` task inside a `try` list runs and the matched `catch` clause declares a retry
  policy
- **THEN** the `try` list is retried according to that policy, identically to a retry triggered by
  any other failure

### Requirement: Raised error outside any `try` fails the task and the instance
When a `raise` task runs outside any `try` list, `dws-orchestrator` SHALL fail the task and the
workflow instance through the same task-failure and instance-failure path used for any other
uncaught task failure. Owning component: `dws-orchestrator`.

#### Scenario: Top-level raise fails the instance
- **WHEN** a `raise` task not nested inside any `try` list runs
- **THEN** the task fails
- **AND** the workflow instance fails through the standard instance-failure path

### Requirement: Tasks nested under `raise` deploy no additional resources
`dws-controller` SHALL NOT deploy any additional resource for a `raise` task; it is interpreted
entirely in-process by `dws-orchestrator`, in the same category as `switch`, `set`, and `wait`.
Owning component: `dws-controller`.

#### Scenario: Definitions with a raise task compile unchanged
- **WHEN** a definition declares a `raise` task
- **THEN** the compiled set of step services and topic bindings is the same as if the `raise` task
  were absent
