## Why

`dws-orchestrator` interprets a workflow's task list but has **no data-flow pipeline**:
`InterpreterWorkflow.dispatch()` passes the single `data` `JsonNode` straight through every task
branch untransformed. The OWS DSL 1.0 data-flow contract — `input.from`/`input.schema`,
`output.as`/`output.schema`, `export.as`/`export.schema`, and the workflow `$context` document —
is entirely unimplemented (roadmap §2, marked ❌). This is Phase 1 of the roadmap and the
foundation the later phases build on: `try`/`catch`/`retry` (Phase 2), Problem Details and timeouts
(Phase 3), and extensions (Phase 7) all read and write through `input`/`output`/`context`, so they
cannot be built correctly until a real pipeline exists to operate on.

## What Changes

- Add a per-task **data-flow pipeline** to `InterpreterWorkflow.dispatch()`, applied uniformly to
  every task type, in OWS order: `input.from` (transform raw task input) → `input.schema`
  (validate) → task body → `output.as` (transform raw task output) → `output.schema` (validate) →
  `export.as` (write the workflow context).
- Introduce a workflow **`$context` document** — a second JSON document, separate from `data`, that
  `export.as` writes into and that persists for the life of the workflow instance. Today `data` and
  context are the same single document; this splits them. `$context` is exposed to every jq
  expression as the `$context` variable. **BREAKING** to the internal expression environment only
  (no external/API contract changes).
- Reuse `JqEvaluator` (jackson-jq) for all three expressions (`from`/`as`), supporting both the
  string (bare jq) and object (structured literal with embedded runtime expressions) forms, and
  extend it to bind the `$context` variable. No second expression language is introduced.
- Add **real JSON Schema validation** for `input.schema`/`output.schema` via
  `com.networknt:json-schema-validator` (already on the classpath transitively through
  `serverlessworkflow-api`; promoted to a direct dependency), against inline schemas
  (`schema.document`).
- Define the **minimal fault shape** for a validation or transform failure: a dedicated unchecked
  exception carrying the task name, phase, and failure detail, surfaced through the existing
  dispatch failure path (`taskFailed` lifecycle event + instance failure). Full RFC 7807 Problem
  Details formatting is **explicitly Phase 3** and out of scope here.

## Capabilities

### New Capabilities
- `workflow-data-flow`: the orchestrator's per-task data-flow pipeline — `input.from`/`output.as`
  transformation, `input.schema`/`output.schema` JSON Schema validation, `export.as` writes into
  the persistent workflow `$context` document, the `$context` expression binding, and the minimal
  validation/transform fault shape.

### Modified Capabilities
<!-- None. Lifecycle event publishing (orchestrator-event-publishing) is consumed unchanged: a
     data-flow fault surfaces through the existing taskFailed / instanceFailed path, which is
     already how a task RuntimeException is reported. No requirement of that spec is modified. -->

## Impact

- **Component**: `dws-orchestrator/` only — `workflow/InterpreterWorkflow.java` (dispatch pipeline),
  `expr/JqEvaluator.java` (`$context` binding, object-form evaluation), a new schema-validation
  helper, a new data-flow fault exception, and a new in-process transform/validate activity (mirrors
  `EvaluateSetActivity`, keeping jq/validation out of the workflow replay loop). No changes to
  `dws-controller` or any step image.
- **New dependency**: `com.networknt:json-schema-validator` promoted from transitive (via
  `serverlessworkflow-api`) to a **direct** dependency with a pinned version. Jackson-2 based
  (`com.fasterxml.jackson.databind.JsonNode`), matching the orchestrator's existing `data`/`context`
  node type — no cross-Jackson conversion.
- **No new component and no new deployed resource**: entirely in-process in the orchestrator. No new
  Knative Service, no Dapr component, no controller compile-path change.
- **Non-goals**: RFC 7807 Problem Details + standard OWS error types (Phase 3); external schema
  resolution (`schema.external` / `SchemaExternal`) and non-`json` schema formats; workflow-level
  (top-level) `input`/`output` transformation — this change is task-level (`TaskBase`) only;
  `for`/`try` task interpretation (still `UnsupportedOperationException`).
- **CI**: covered by the existing `dws-orchestrator` gate (`./mvnw verify`); no CI workflow changes.
