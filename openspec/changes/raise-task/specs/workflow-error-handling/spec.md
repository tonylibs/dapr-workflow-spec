## ADDED Requirements

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
