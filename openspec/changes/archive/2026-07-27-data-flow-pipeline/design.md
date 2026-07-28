## Context

`dws-orchestrator` runs one immutable OWS definition as a program-counter loop over the `do` task
list (`InterpreterWorkflow.execute()`). Every task type dispatches through `dispatch()`, which
threads a single `data` `JsonNode` in and out of each branch. There is **no transform step**: raw
data flows through unmodified. `switch`/`set` already evaluate jq in in-process activities
(`EvaluateSwitchActivity`, `EvaluateSetActivity`) — running as `ctx.callActivity(...)` purely so
evaluation stays out of the workflow method's replay loop, uniformly with every other task type.

This change implements OWS DSL 1.0 **task-level** data flow — the pipeline every task runs around
its body:

```
raw input (= prior data)
  → input.from   (transform)
  → input.schema (validate)
  → TASK BODY
  → output.as    (transform)
  → output.schema(validate)
  → export.as    (write $context)   → next task's raw input = the transformed output
```

The workflow `$context` is a **new second document**, distinct from `data`, that `export.as` writes
and that persists for the instance's life. Today `data` and context are the same single document.

**Constraints inherited from the component:**

- **Determinism/replay.** `execute()` must stay replay-deterministic — no `Instant.now()`/random,
  and any state that flows between tasks (now `data` *and* `context`) must be threaded through the
  loop, derived only from activity results.
- **jq is the only expression language** (decided): reuse `JqEvaluator` (jackson-jq 1.2.0). No
  Knative EventTransform, no Camel, no Dapr middleware.
- **No persistence, no new deployed resource**: `$context` lives in the workflow instance's own
  durable state for the pod's lifetime; nothing is stored externally.

**SDK facts, verified against `serverlessworkflow-types:7.26.0.Final`** (`javap`, not assumed):

- `TaskBase` exposes `getInput()` → `Input`, `getOutput()` → `Output`, `getExport()` → `Export`
  (alongside the already-used `getIf()`/`getThen()`/`getTimeout()`). Every concrete task
  (`SetTask`, `CallTask`, `RunTask`, …) is a `TaskBase`, so the pipeline reads uniformly off the
  base type without switching on task kind.
- `Input.getFrom()` → `InputFrom`; `Output.getAs()` → `OutputAs`; `Export.getAs()` → `ExportAs`.
  Each of the three is a `OneOfValueProvider<Object>` with **exactly** `getString()` (jq expression
  form) and `getObject()` (structured-literal form) — so both forms must be handled.
- `Input`/`Output`/`Export` each expose `getSchema()` → `SchemaUnion`, whose members are
  `getSchemaInline()` (`SchemaInline.getDocument()` → `Object`, the inline JSON Schema) and
  `getSchemaExternal()` (`SchemaExternal.getResource()` → `ExternalResource`). `Schema.getFormat()`
  carries the format (default `json`).

**JSON Schema library, verified against `com.networknt:json-schema-validator:2.0.0`** (already on
the classpath transitively via `serverlessworkflow-api`):

- **It is Jackson-2 based.** Its pom pins `com.fasterxml.jackson.core:jackson-databind:2.18.3`, and
  every validate/getSchema signature takes `com.fasterxml.jackson.databind.JsonNode` — the *same*
  node type as the orchestrator's `data`/`context` (jackson-jq is likewise Jackson-2). No
  cross-Jackson conversion, despite Spring Boot 4.1 also dragging in Jackson-3 (`tools.jackson`) for
  its own core.
- **2.0.0 is a redesigned API** — not the widely-documented 1.x (`JsonSchemaFactory` /
  `SpecVersion.VersionFlag`). The verified 2.0.0 surface is:
  - `SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)` → a thread-safe
    `SchemaRegistry` (build once, reuse).
  - `registry.getSchema(JsonNode schemaDocument)` → `com.networknt.schema.Schema`.
  - `schema.validate(JsonNode instance)` → `java.util.List<com.networknt.schema.Error>`; an **empty
    list means valid**. `Error` exposes `getInstanceLocation()`, `getKeyword()`, and `getMessage()`
    for building the fault text.
  - `SpecificationVersion` constants: `DRAFT_4/6/7/2019_09/2020_12`.

## Goals / Non-Goals

**Goals:**

- A per-task data-flow pipeline in `dispatch()`, applied uniformly to every task type in OWS order,
  covering `input.from`, `input.schema`, `output.as`, `output.schema`, `export.as`, `export.schema`.
- A persistent workflow `$context` document, threaded through `execute()` alongside `data`, written
  by `export.as`, and exposed to every jq expression as `$context`.
- Real JSON Schema validation of `input`/`output` against inline schemas, with a minimal,
  self-contained fault signal on failure.
- Zero behavior change and zero extra activity call for tasks that declare no `input`/`output`/
  `export` (the existing definitions).
- `./mvnw verify` green, with unit coverage of transform + validation and an interpreter
  integration test exercising the pipeline end to end (including a validation-failure path).

**Non-Goals:**

- RFC 7807 Problem Details and the standard OWS error types — **Phase 3** (D5 fixes only the minimal
  shape).
- `schema.external` (`SchemaExternal`) resolution and non-`json` `schema.format` — needs a
  fetch-at-startup + caching story (mirrors `run.script.source`, deferred in the dws-run change).
- Workflow-level (top-level `Workflow.getInput()`/`getOutput()`) transformation — this change is
  **task-level** (`TaskBase`) only.
- `for`/`try` interpretation — still `UnsupportedOperationException`; the pipeline wraps them the
  moment they are implemented, but they are out of scope here.
- Changing how `switch`/`set`/`call`/`run`/`wait`/`listen`/`emit` bodies themselves behave — the
  pipeline only transforms what flows *into* and *out of* each unchanged body.

## Decisions

### D1: Pipeline wraps `dispatch()` uniformly; branch bodies consume transformed input, not `data`

`dispatch()` becomes: (1) compute the task's **input** = input-phase(rawInput=`data`, `context`),
(2) run the existing branch body against that input instead of `data`, (3) compute **output** =
output-phase(rawOutput=branch result, `context`), (4) compute the **new context** =
export-phase(output, `context`). It returns the transformed output (the next task's `data`) plus the
new context and the flow directive.

- **Why**: OWS defines the pipeline as a property of *every* task, not of specific kinds. Reading
  `input`/`output`/`export` off `TaskBase` and wrapping the dispatch once keeps a single
  implementation for all seven task types, versus threading it into each branch.
- **Alternative — per-branch handling**: rejected; duplicates the pipeline seven times and drifts.
- **Note**: `listen`'s existing event-merge and `set`/`switch`'s jq stay exactly as they are; the
  pipeline is strictly outside the body. For `listen`, `input.from` transforms the pre-event `data`
  and `output.as` transforms the post-merge result — consistent with treating the body as a black
  box.

### D2: Transform + validate run in in-process activities, mirroring `EvaluateSetActivity`

Add a `DataFlowActivity` (in-process `WorkflowActivity`) with two request/response shapes — an
**input phase** (`{taskName, rawInput, context}` → validated task input) and an **output phase**
(`{taskName, rawOutput, context}` → `{data, context}` after `output.as`/`output.schema`/`export.as`/
`export.schema`). `dispatch()` calls the input-phase activity before the body and the output-phase
activity after.

- **Why**: identical rationale to `EvaluateSetActivity` — jq and validation are pure and
  replay-safe, but the codebase's convention is to keep evaluation out of the workflow method by
  routing through `ctx.callActivity(...)`. Two phases because input must be computed *before* the
  body and output/export *after*. The activity reads the task's `Input`/`Output`/`Export` via the
  existing `DefinitionLookup.taskByName(...)`, exactly as `EvaluateSetActivity` reads `SetTask`.
- **Alternative — evaluate inline in `execute()`/`dispatch()`**: correct for determinism (jq is
  pure) but breaks the established "evaluation lives in an activity" convention and puts jq compile
  cost on the workflow thread; rejected for consistency.
- **Alternative — one activity, one call per task**: rejected; the output phase needs the body's
  result, which does not exist at the single call site before the body runs.

### D3: Skip the pipeline entirely when a task declares no input/output/export

`dispatch()` guards each phase: if `task.getInput() == null` (and likewise output/export), no
activity is invoked and the body consumes `data` directly, exactly as today.

- **Why**: every existing definition declares none of these, so the pipeline must be zero-cost and
  zero-behavior-change for them — no extra durable activity per task, no new failure surface. The
  guard is a null check on `TaskBase` getters, fully deterministic.
- **Alternative — always run the pipeline (identity transform when absent)**: rejected; adds one or
  two durable activities to every task in every existing workflow for no effect.

### D4: `$context` is threaded through `execute()` and bound as the jq `$context` variable

`execute()` holds a `context` `JsonNode` (initialised to an empty object) beside `data`, updated
only from the output-phase activity's result, and passed into both phase activities. `JqEvaluator`
gains an overload that binds named variables into the child scope via
`Scope.setValue("context", contextNode)` (verified present on jackson-jq 1.2.0 `Scope`) before
`query.apply(...)`, so every `from`/`as` expression can read `$context`.

- **Why**: OWS exposes the workflow context to runtime expressions as `$context`; `export.as` is how
  a task writes it. Threading it through the loop (not storing it externally) keeps replay
  determinism and needs no persistence. `context` is **not** part of `ctx.complete(data)` output —
  it is internal workflow state.
- **Scope of `export.as`**: per OWS, `export.as` evaluates over the task's transformed **output**
  (with the *current* `$context` in scope) and its result **replaces** `$context`. `export.schema`
  then validates the new context.
- **Alternative — fold context into `data`**: rejected; that is exactly today's conflated model the
  DSL separates, and later phases (retry/catch/extensions) read `$context` independently of `data`.
- **Alternative — a Dapr state store for context**: rejected; unnecessary (instance-scoped,
  in-memory-through-replay) and would add a deployed resource this change forbids.

### D5: Minimal fault shape — a dedicated unchecked exception, no RFC 7807

A transform compile/eval failure or a schema-validation failure throws a new
`DataFlowException extends RuntimeException` carrying `taskName`, a `phase`
(`INPUT`/`OUTPUT`/`EXPORT`), and a human-readable detail assembled from the validation `Error`s
(`instanceLocation: message`, joined). It is thrown inside `DataFlowActivity`; it propagates through
`dispatch()`'s existing `catch (RuntimeException e)` → `taskFailed(name, type, e.getMessage())`
lifecycle event → `instanceFailed` → instance fails.

- **Why**: this is the *minimal* signal the prompt asks for. It reuses the exact failure path
  `switch`/`set` already use (an unchecked exception out of an activity), so no new plumbing. Phase 3
  will replace the message with an RFC 7807 Problem Details body and standard error types.
- **Message must be self-contained**: across the Dapr activity boundary only the exception *message*
  is preserved on the workflow side (as with today's `IllegalStateException`s), so `DataFlowException`
  puts the task name, phase, and validation detail **into the message string**, not only in fields.
- **Alternative — return a validation-failure result object the workflow inspects**: rejected;
  diverges from the established throw-to-fail path and would make a validation failure look like a
  successful task to the lifecycle-event layer.
- **Alternative — build the Problem Details now**: rejected; explicitly Phase 3, and the error-type
  taxonomy it needs is not defined here.

### D6: `from`/`as` support both the string (jq) and object (structured-literal) forms

An `InputFrom`/`OutputAs`/`ExportAs` with `getString() != null` is a single jq program producing the
whole transformed document. With `getObject() != null` it is a structured literal: recurse the
object/array, and for each **string leaf**, evaluate it as a runtime expression **only if it is
`${ }`-wrapped** (via the existing `JqEvaluator.unwrap` convention); an unwrapped string is a
literal. Objects and arrays recurse; non-string scalars are literals.

- **Why**: OWS allows both forms, and only `${ }`-wrapped strings inside an object literal are
  expressions — a bare `"active"` is the literal string, not the jq program `active` (which would
  fail to compile). This is the OWS-correct rule.
- **Divergence noted**: `EvaluateSetActivity` treats *every* string value as a jq expression, which
  is fine because `set` values are conventionally expressions. The data-flow object form uses the
  stricter `${ }`-gated rule; this is intentional and called out so the two are not "unified" later
  by mistake. The single-string form is fully general (any object can be written as one jq program),
  so the object form is sugar — but supporting it matches the DSL surface authors expect.
- **Alternative — string form only in Phase 1**: rejected; leaves a visible DSL gap that later
  phases would trip over, for little saved code (one recursive helper).

### D7: Inline JSON Schema only, Draft 2020-12 default, built once

Validation resolves the schema from `SchemaUnion.getSchemaInline().getDocument()` (converted to a
`JsonNode` via the existing `ObjectMapper`) and validates with a single process-wide
`SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)` held in `WorkflowSupport`
(like `jq()`/`mapper()`). `schema.external` or a non-`json` `schema.format` throws
`DataFlowException` naming the unsupported form (compile-time-style rejection, not silent skip).

- **Why**: matches the verified 2.0.0 API; the registry is thread-safe and reusable, so building it
  once avoids per-validation setup. 2020-12 is the current JSON Schema draft the OWS spec targets.
- **Alternative — infer draft from the schema's `$schema`**: deferred; `withDefaultDialect` still
  honors an explicit `$schema` in the document when present, and a fixed default is sufficient for
  Phase 1. Not a contract-breaking choice.
- **Alternative — silently skip external/unknown-format schemas**: rejected; that is the
  post-deployment-mystery failure mode this repo avoids — reject loudly and name the form.

## Risks / Trade-offs

- **[Two Jackson majors on the classpath (2.21 `com.fasterxml` + 3.1 `tools.jackson` from Spring
  Boot 4.1)]** → picking the wrong `JsonNode` type would not compile or would silently double-parse.
  → **Mitigation**: verified (javap + pom) that jackson-jq **and** json-schema-validator 2.0.0 both
  use `com.fasterxml.jackson.databind.JsonNode`, identical to the orchestrator's `data`/`context`.
  A unit test validates a real node through the library to lock the type in.
- **[networknt 2.0.0 API differs from every 1.x tutorial]** → coding to `JsonSchemaFactory`/
  `SpecVersion` would not compile. → **Mitigation**: the verified 2.0.0 surface (`SchemaRegistry`,
  `SpecificationVersion.DRAFT_2020_12`, `Schema.validate → List<Error>`) is recorded above and is
  what the tasks reference; a first task stands up a one-line validate before the rest is built.
- **[Promoting a transitive dep to direct pins us to its version]** → a future
  `serverlessworkflow-api` bump could want a different validator version. → **Mitigation**: pin the
  version we verified (2.0.0) in `dws-orchestrator/pom.xml` with a property, matching the transitive
  version so nothing changes on the classpath today; revisit on SDK upgrade.
- **[Extra durable activities per task increase instance history size]** → up to two more activity
  records per task that declares data flow. → **Mitigation**: D3 skips them entirely when
  input/output/export are absent (all current definitions), so only tasks that opt in pay the cost —
  inherent to running transforms replay-safely as activities, consistent with `set`/`switch`.
- **[`$context` starts empty; an `export.as` that reads `$context` before any prior export sees
  `{}`]** → expressions like `$context.count + 1` must tolerate a null/absent field. → **Mitigation**:
  initialise `context` to an empty object (never null), documented; jq's `//` handles absence. This
  is OWS-correct (context accumulates across tasks).
- **[Only the exception message crosses the activity boundary]** → a `DataFlowException` whose detail
  lived only in fields would reach the workflow as an opaque activity failure. → **Mitigation**: D5
  puts task/phase/validation detail into the message string; an interpreter integration test asserts
  a validation failure fails the instance with the offending field named.

## Migration Plan

1. **Dependency + registry.** Pin `com.networknt:json-schema-validator:2.0.0` as a direct dep in
   `dws-orchestrator/pom.xml`; add the `SchemaRegistry` to `WorkflowSupport`. Verify `./mvnw verify`
   still green (no behavior yet).
2. **`JqEvaluator` `$context` binding + object-form helper**, with unit tests (`$context` read; a
   `${ }`-gated object literal).
3. **`DataFlowException` + a `SchemaValidator` helper** (inline-schema validate → `List<Error>` →
   message), with unit tests including a failing instance and an external/unknown-format rejection.
4. **`DataFlowActivity`** (input phase, output+export phase) reading `Input`/`Output`/`Export` via
   `DefinitionLookup`, with unit tests over the phase logic and `$context` write.
5. **Wire the pipeline into `InterpreterWorkflow`**: thread `context`, guard on presence (D3), call
   the phase activities. Add an interpreter integration test exercising input transform, output
   transform, export→context read-back by a later task, and a schema-validation failure.
6. Full gate: `./mvnw verify` green.
- **Rollback**: additive and guarded by D3 — reverting removes the phase calls; every existing
  definition (no input/output/export) is byte-for-byte unaffected. No deployed resource, no data
  migration.

## Open Questions

- **Default schema draft** if a schema omits `$schema`: `DRAFT_2020_12` chosen; revisit only if a
  real definition needs an older draft (widen without breaking).
- **Whether `$input`/`$output` should also be bound** (OWS exposes more than `$context`): only
  `$context` is required for Phase 1's cross-task persistence; binding the others is additive and can
  land with the phase that needs them (retry/catch reads `$input`), so deferred to avoid speculative
  surface.
