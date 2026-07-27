## ADDED Requirements

### Requirement: Per-task data-flow pipeline
The orchestrator SHALL apply a data-flow pipeline around every task's body, uniformly for every task
type, in the order: transform input (`input.from`), validate input (`input.schema`), run the task
body, transform output (`output.as`), validate output (`output.schema`), write the workflow context
(`export.as`/`export.schema`). The task body SHALL receive the transformed input rather than the raw
data document, and the transformed output SHALL become the raw input of the next task.

#### Scenario: Input transform feeds the task body
- **WHEN** a task declares `input.from` and the incoming data document is passed to it
- **THEN** the task body operates on the transformed input produced by `input.from`, not on the raw data

#### Scenario: Output transform flows to the next task
- **WHEN** a task declares `output.as` and completes
- **THEN** the next task's raw input is the transformed output produced by `output.as`

#### Scenario: Pipeline order is input then body then output then export
- **WHEN** a task declares `input.from`, `output.as`, and `export.as`
- **THEN** `input.from` is applied before the body, and `output.as` then `export.as` are applied after the body, in that order

### Requirement: No pipeline overhead for tasks without data flow
For a task that declares none of `input`, `output`, or `export`, the orchestrator SHALL pass the data
document through unchanged and SHALL NOT perform any transform or validation step for that task.

#### Scenario: Task without data flow is unchanged
- **WHEN** a task declares no `input`, `output`, or `export`
- **THEN** the task body receives the incoming data document unchanged
- **AND** no transform or validation is performed for that task

### Requirement: Runtime expression forms for `from`/`as`
The orchestrator SHALL evaluate `input.from`, `output.as`, and `export.as` with the jq expression
dialect. It SHALL support the string form (a single jq program producing the whole transformed
document) and the object form (a structured literal), and SHALL make the workflow context available
to every such expression as the `$context` variable.

#### Scenario: String form transforms the whole document
- **WHEN** `output.as` is the string `{ id: .orderId }`
- **THEN** the transformed output is an object with `id` set to the input's `orderId`

#### Scenario: Object form evaluates wrapped strings and keeps literals
- **WHEN** `output.as` is an object whose one value is the wrapped expression `${ .total }` and whose other value is the plain string `active`
- **THEN** the wrapped value is replaced by the evaluated expression result
- **AND** the plain string `active` is kept as a literal

#### Scenario: Expression can read the workflow context
- **WHEN** an `output.as` or `export.as` expression references `$context`
- **THEN** it evaluates against the current workflow context document

### Requirement: Workflow context document persists across tasks
The orchestrator SHALL maintain a workflow context document, separate from the data document,
initialised to an empty object at instance start. `export.as` SHALL compute a new context from the
task's transformed output (with the current context in scope), and that new context SHALL replace the
prior context and persist for the remainder of the instance. The context SHALL NOT be included in the
instance's completion output.

#### Scenario: Export writes context read by a later task
- **WHEN** an earlier task's `export.as` writes a value into the context
- **AND** a later task's expression reads that value via `$context`
- **THEN** the later task observes the value written by the earlier task's export

#### Scenario: Context starts as an empty object
- **WHEN** the first task reads `$context` before any export has run
- **THEN** `$context` is an empty object rather than null

#### Scenario: Context is not part of completion output
- **WHEN** an instance completes after tasks have written the context
- **THEN** the completion output is the final data document and does not include the context document

### Requirement: JSON Schema validation of input and output
The orchestrator SHALL validate a task's transformed input against `input.schema` and its transformed
output against `output.schema` using JSON Schema (draft 2020-12 by default) when an inline schema is
declared. Validation SHALL pass when the document conforms and SHALL fail when it does not.

#### Scenario: Conforming document passes
- **WHEN** a task's transformed input satisfies its inline `input.schema`
- **THEN** validation passes and the task body runs

#### Scenario: Non-conforming document fails the task
- **WHEN** a task's transformed output violates its inline `output.schema`
- **THEN** validation fails and the task fails

#### Scenario: Unsupported schema form is rejected
- **WHEN** a task declares an external schema (`schema.external`) or a non-`json` schema format
- **THEN** the orchestrator fails the task, naming the unsupported schema form

### Requirement: Minimal data-flow fault signal
When a `from`/`as` expression cannot be evaluated or a schema validation fails, the orchestrator SHALL
fail the task with a fault that identifies the task name, the pipeline phase (input, output, or
export), and the failure detail (including the offending location for a validation failure). The fault
SHALL surface through the existing task-failure and instance-failure path. RFC 7807 Problem Details
formatting is out of scope for this capability.

#### Scenario: Validation failure names the offending field
- **WHEN** a task's output fails `output.schema` validation on a specific field
- **THEN** the task fails with a fault message naming the task, the output phase, and the offending field

#### Scenario: Fault fails the instance through the standard path
- **WHEN** a data-flow fault is raised for a task
- **THEN** the task is reported failed and the workflow instance fails, using the same failure path as any other task exception
