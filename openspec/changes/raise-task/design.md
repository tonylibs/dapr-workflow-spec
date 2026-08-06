## Context

`dws-orchestrator` interprets its one pinned Open Workflow Specification definition as a
program-counter loop (`InterpreterWorkflow`). `raise` is not recognised at all today:
`dispatchBody`'s `StreamEx.of(...)` task-type list omits `task.getRaiseTask()`, so a `raise` task
falls through to `IllegalStateException("task '" + name + "' has an unsupported type")` — not even
the dedicated `UnsupportedOperationException` `for` currently gets. `taskTypeOf` has no `raise`
branch either.

This change implements the OWS DSL 1.0 `raise` task — roadmap **Phase 2, slice 2.2** — the smallest
remaining slice of Phase 2, unblocked by slice 2.1 (`try`/`catch`/`retry`,
`openspec/changes/try-catch-retry`, merged to `main`, not yet archived). `raise` is designed to feed
that slice's `catch.errors.with`/`catch.when` machinery rather than duplicate it: a raised error is a
second *producer* of the same five-field runtime error object `workflow-error-handling` already
defines and filters on.

**Current-state facts, read from the code rather than assumed:**

- `WorkflowErrors.classify(message)` recognises exactly two markers — `DATA_FLOW_MARKER`,
  `STEP_MARKER` — and defaults anything else to `ErrorKind.RUNTIME`/500. Run through this logic
  unchanged, a raised error's own `type`/`status`/`title` would be discarded.
- Only an exception's **message** survives the Dapr activity boundary. `StepInvocationException`'s
  own Javadoc documents this, and it's *why* `WorkflowErrors` classifies from a message instead of an
  exception type in the first place.
- `EvaluateSetActivity` is the template for a pure, no-I/O, in-process activity: it exists as an
  activity purely to keep jq evaluation out of the workflow method's replay loop, registered in
  `WorkflowRuntimeBootstrap`, resolving its task via `DefinitionLookup.taskByName()`.
- `DefinitionLookup.taskByName()` already searches recursively into `try`/`catch.do` lists, so a
  `raise` task nested inside either is already resolvable by name — no change needed there.
- `dws-controller`'s `WorkflowCompiler.walk()` already excludes `raise` from what it deploys; its
  existing comment reads `// switch/set/wait/for/raise (and the task lists nested under for/fork)
  deploy nothing.` Confirmed by reading the code: **no controller change is needed**.
- `dispatchTry`'s `catch (RuntimeException failure)` block already offers *any* runtime failure from
  inside a `try` list to `CatchDecisionActivity`. A raised error needs no new propagation path, only
  to arrive there as an ordinary `RuntimeException`.

**SDK facts, verified by disassembling `serverlessworkflow-types:7.26.0.Final` with `javap`** (this
resolves the proposal's flagged risk — the assumption that all five error fields accept `${...}`
expressions the way `set`'s fields do is only partly true):

- `Task.getRaiseTask()` → `RaiseTask extends TaskBase`, so the existing data-flow pipeline wraps it
  unchanged, exactly as `TryTask` does.
- `RaiseTask.getRaise()` → `RaiseTaskConfiguration.getError()` → `RaiseTaskError`, a one-of:
  `getRaiseErrorDefinition()` → inline `Error`, or `getRaiseErrorReference()` → `String` naming an
  entry in `use.errors`.
- `Use.getErrors()` → `UseErrors.getAdditionalProperties()` → `Map<String, Error>` — the same shape
  as `Use.getRetries()`, which try-catch-retry's D7 already resolved for named retry policies.
- `Error`'s five fields are **not five string fields**:
  - `type` → `ErrorType`, one-of `getLiteralErrorType()` → `UriTemplate` (itself one-of a literal
    `URI` or a literal template `String`) or `getExpressionErrorType()` → `String`.
  - `status` → **plain primitive `int`. No expression variant exists.**
  - `instance` → `ErrorInstance`, one-of `getExpressionErrorInstance()` → `String` or
    `getLiteralErrorInstance()` → `String`.
  - `title` → `ErrorTitle`, one-of `getExpressionErrorTitle()` → `String` or
    `getLiteralErrorTitle()` → `String`.
  - `detail` → `ErrorDetails`, one-of `getExpressionErrorDetails()` → `String` or
    `getLiteralErrorDetails()` → `String`.
- Every field except `status` is typed as literal-or-expression at the model level — the SDK already
  disambiguates; no `${...}`-wrapper sniffing (the `set` convention) is needed or appropriate.

## Goals / Non-Goals

**Goals:**
- Interpret `raise`: evaluate its configured error (inline or `use.errors`-referenced), and fail the
  task with that error surviving intact — not reclassified by `WorkflowErrors.classify()`.
- A `raise` inside a `try` list is offered to that `try`'s `catch` clause through the same
  `CatchDecisionActivity` path a real failure already goes through, with no new propagation code.
- A `raise` outside any `try` fails the task and the instance through the existing
  `taskFailed`/`instanceFailed` path, identically to any other uncaught `RuntimeException` today.
- `./mvnw verify` green in `dws-orchestrator`; `./mvnw test` green in `dws-controller` (confirming no
  unintended compile-path change, since none is expected).

**Non-Goals:**
- Any `dws-controller` change — confirmed unnecessary by reading `WorkflowCompiler.walk()`, not
  assumed.
- RFC 7807 Problem Details / the standard OWS error-type catalogue — Phase 3, unchanged from
  try-catch-retry's own non-goal.
- `for`, `fork`, generalised nested `do` — later Phase 2 slices.
- A computed/expression form for `status` — the pinned SDK has none (D2).
- Extending `ErrorKind` with a fourth value for raised errors — a raised error is author-defined, not
  inferred from a failure class, so it does not participate in `ErrorKind` classification at all (D4).

## Decisions

### D1: Literal fields pass through; expression fields always evaluate — no `${...}` sniffing

- **Choice**: for `type`/`instance`/`title`/`detail`, branch on which SDK accessor is non-null. A
  literal value (`getLiteralError*()`) is used as-is (a `UriTemplate`'s literal URI/template
  converted to its string form for `type`). An expression value (`getExpressionError*()`) is always
  evaluated via `JqEvaluator.evaluate(expr, data, variables)` — unconditionally, no `${...}`-wrapper
  check.
- **Why**: the SDK's typed one-of is strictly more information than a bare string would be. Every
  other field's evaluation in this codebase (`set`) has to sniff for `${...}` because the model gives
  it only a `String`; here the parser has already resolved literal-vs-expression, so re-deriving it
  from string content would throw that information away and risk misreading a literal string that
  happens to start with `${`.
- **Alternative considered — treat every field as a `set`-style string, sniffing for `${...}`**:
  rejected; wrong model for a typed one-of the SDK already resolved.

### D2: `status` is a literal int; the missing expression variant is a documented SDK gap

- **Choice**: `Error.getStatus()` is used verbatim as the error object's `status`. No jq evaluation
  path exists for it, and none is added.
- **Why**: there is nothing to evaluate — this SDK version's `status` field simply isn't
  expression-typed, unlike the other four fields. This follows try-catch-retry's D9 precedent
  (`catch.then`'s absence documented as a known SDK gap with defined, additive fallback behaviour)
  rather than inventing a workaround.
- **Alternative considered — accept a string for `status` and jq-evaluate it to an int**: rejected;
  invents parallel syntax the SDK does not define, exactly what D9 rejected for `catch.then`.

### D3: Author-supplied `instance` is honoured; absent `instance` is computed from the raising task

- **Choice**: when `raise.error.instance` is present (literal or expression, per D1), it is used
  as-is. When absent, the runtime computes `/<taskName>` for the raising task — identical to how
  `WorkflowErrors.build()` already sets `instance` for every other error kind.
- **Why**: the SDK models `instance` as an optional, author-settable field (unlike the implicit
  `try`/`catch` error, which has no author to ask), so honouring it when given is the literal reading
  of the schema. Falling back to the raising task's location when absent keeps `raise` consistent
  with the one convention `WorkflowErrors` already established.
- **Alternative considered — always compute `instance` from the task, ignoring an author-supplied
  value**: rejected; discards a field the SDK explicitly exposes for authoring.

### D4: `RaisedErrorException` folds the resolved error into its message behind a distinct marker;
`WorkflowErrors` short-circuits on it instead of classifying

- **Choice**: a new `RaisedErrorException extends RuntimeException` carries the already-resolved
  five-field error object (as JSON) inside its message, behind a marker distinct from
  `STEP_MARKER`/`DATA_FLOW_MARKER` (a `raised error:` prefix followed by the JSON).
  `WorkflowErrors.classify()`/`of()` gets a short-circuit: when the marker is present, the embedded
  object is parsed back out and returned unchanged — no `ErrorKind` is assigned, no status/type is
  re-derived.
- **Why**: only the exception's message survives the Dapr activity boundary — the same constraint
  `StepInvocationException`'s Javadoc documents and `WorkflowErrors`'s own Javadoc gives as the
  reason it classifies from a message at all. A raised error is author-authoritative; running it back
  through `classify()`'s message-sniffing would either misclassify it (if `detail` happens to contain
  a marker substring) or silently discard the author's `type`/`title`. A dedicated marker plus
  short-circuit is the only way "survives intact" can actually hold.
- **Alternative considered — give `RaisedErrorException` typed fields and have `WorkflowErrors`
  accept a pre-built error object from the caller**: rejected; the caller (the workflow method,
  reading a `CatchDecisionRequest`) only has the exception's *message* to work with too, by the same
  activity-boundary constraint — no code path lets a typed exception field survive that hop.
- **No new `ErrorKind`**: the three existing kinds exist to give a *shape* to failures the runtime
  observes and must classify after the fact. A raised error already has its shape, supplied by the
  author — adding a fourth kind would imply a default type/status derived from the kind, backwards
  from the actual requirement that the author's values win.

### D5: `raise.error` reference resolves against `use.errors`; unresolvable name fails loudly

- **Choice**: `RaiseTaskError.getRaiseErrorReference()` is looked up in
  `Workflow.getUse().getErrors().getAdditionalProperties()`. A name that doesn't resolve fails the
  task with a message naming the missing error definition.
- **Why**: identical shape and identical justification to try-catch-retry's D7 (named retry policies
  resolved from `use.retries`) — reuse the convention for a structurally identical case rather than
  inventing a second one.
- **Alternative considered — reject unresolved references at compile time in `dws-controller`**:
  rejected for the same reason D7 rejected it for retries — splitting validation across components
  when all error semantics otherwise live in the orchestrator.

### D6: `RaiseErrorActivity` is pure evaluation returning the resolved object; it does not throw

- **Choice**: mirroring `EvaluateSetActivity`'s shape exactly, `RaiseErrorActivity` resolves
  `RaiseTaskConfiguration` (inline or by reference, per D5), evaluates each field per D1–D3, and
  **returns** the five-field `ObjectNode`. It does not itself throw `RaisedErrorException`. The
  workflow method (`dispatchConcreteTask`'s `RaiseTask` branch) receives that already-resolved,
  already-recorded value back from `ctx.callActivity(...).await()` and *then* throws
  `RaisedErrorException` with it folded into the message.
- **Why**: keeps the activity a pure decision/computation unit like
  `EvaluateSwitchActivity`/`EvaluateSetActivity` — testable directly, no exception-based control flow
  crossing the activity boundary — and the later throw is deterministic on replay because it's driven
  by an already-recorded activity result, not a fresh computation.
- **Alternative considered — throw `RaisedErrorException` from inside the activity**: rejected; it
  would make the activity's "successful" path indistinguishable from a genuine evaluation failure
  (e.g. a malformed jq expression in `raise.error.detail`), which should remain a plain activity
  failure, not a raised-error-shaped one.

### D7: Dispatch wiring reuses the existing failure path; no new propagation code

- **Choice**: add `task.getRaiseTask()` to `dispatchBody`'s `StreamEx.of(...)` list. Add a
  `case RaiseTask raiseTask ->` branch to `dispatchConcreteTask` that calls `RaiseErrorActivity`, then
  throws `RaisedErrorException` with the resolved object. Add
  `else if (task.getRaiseTask() != null) return "raise";` to `taskTypeOf`.
- **Why**: because this throw happens inside `runTaskList`'s existing
  `try { dispatch(...) } catch (RuntimeException e) { taskFailed; throw e; }`, and because
  `dispatchTry`'s own catch block already offers *any* runtime failure from inside a `try` list to
  `CatchDecisionActivity`, a raised error needs zero new propagation code — it is caught exactly like
  a real failure precisely because it now *is* a `RuntimeException` indistinguishable in shape from
  one. This is the entire payoff of D4.
- **Alternative considered — special-case `RaiseTask` inside `dispatchTry`/`runTaskList` to skip
  straight to `CatchDecisionActivity`**: rejected; duplicates logic the catch block already has, for
  no behavioural difference.

### D8: `raise` extends the `workflow-error-handling` capability rather than a new one

- **Choice**: the capability spec addition lives at
  `openspec/changes/raise-task/specs/workflow-error-handling/spec.md` as `## ADDED Requirements`
  against the same capability try-catch-retry defined, not a new capability.
- **Why**: `raise` produces exactly the five-field error object `workflow-error-handling` already
  defines and filters on — a second *producer* of that same shape, not a different concern.
  Splitting it into its own capability would force the spec reader to cross-reference two documents
  to understand one error shape.
- **Alternative considered — a new `error-raising` capability that composes with
  `workflow-error-handling`**: rejected; there is no independent behaviour to describe — every
  requirement `raise` needs ("the error survives intact", "it's caught by `catch.errors.with`") is
  phrased entirely in the existing capability's own vocabulary.

### D9: Tests mirror the existing per-unit style; one integration case added to the existing class

- **Choice**: unit tests for the marker round-trip in `WorkflowErrorsTest` (mirroring its existing
  classify/build cases), a new `RaiseErrorActivityTest` mirroring the direct-unit-test style used for
  activity logic in this codebase, covering literal fields, expression fields, the `use.errors`
  reference, an unresolved reference, and the `instance` present/absent cases. One new integration
  case in `InterpreterWorkflowIntegrationTest` — a `raise` inside `try`, caught by
  `catch.errors.with`/`catch.when` exactly like a real failure — added alongside the existing
  try/catch cases.
- **Why**: matches this repo's established per-unit test convention (one test class per
  activity/builder) and keeps the error-handling integration surface in one test class rather than
  fragmenting it.

## Risks / Trade-offs

- **[Risk] `status`'s missing expression variant surprises an author expecting `set`-style `${...}`
  everywhere** (D2) → Mitigation: documented plainly in the capability spec as a fixed, literal-only
  field, mirroring how try-catch-retry documented `catch.then`'s absence rather than silently
  ignoring an attempted expression.
- **[Risk] The message-marker approach is fragile if a JSON blob embedded in an exception message
  collides with logging/truncation elsewhere** (D4) → Mitigation: identical risk profile to
  `StepInvocationException`'s existing marker approach, already accepted for that exception; no new
  exposure introduced.
- **[Trade-off] `RaisedErrorException` has no typed accessors of its own for `type`/`status`/etc.**
  (D4) → Accepted: nothing downstream needs them typed — `WorkflowErrors.of()` is the only reader,
  and it works from the message by necessity (the activity boundary), not by choice.
- **[Trade-off] No `dws-controller` involvement at all** → Accepted and confirmed, not merely
  assumed: `WorkflowCompiler.walk()`'s existing no-op comment already lists `raise`.

## Migration Plan

1. `WorkflowErrors`: add the raised-error marker and the short-circuit branch in `classify()`/`of()`;
   add `RaisedErrorException` (parallel to `StepInvocationException`). Unit tests for the round-trip.
   `./mvnw verify` green — no behavior change for existing failure paths.
2. `RaiseErrorActivity` + its request/response records: resolve `RaiseTaskConfiguration` (inline or
   `use.errors` reference), evaluate each field per D1–D3, return the five-field object. Register in
   `WorkflowRuntimeBootstrap`. Unit tests covering literal/expression/reference/missing-reference/
   instance-present/instance-absent cases.
3. Dispatch wiring in `InterpreterWorkflow`: add `raise` to `dispatchBody`'s type list, add the
   `RaiseTask` branch to `dispatchConcreteTask` (call the activity, throw `RaisedErrorException`),
   add the `"raise"` case to `taskTypeOf`.
4. Integration test: extend `InterpreterWorkflowIntegrationTest` with a `raise`-inside-`try` case
   caught by `catch.errors.with`/`catch.when`.
5. Full gate: `./mvnw verify` in `dws-orchestrator`, `./mvnw test` in `dws-controller` (confirming no
   unintended compile-path change).

**Rollback**: purely additive and gated on a task type that currently throws
`IllegalStateException("... has an unsupported type")`. Reverting restores that failure; no
definition without a `raise` task is affected, no deployed resource changes shape (confirmed none did
to begin with), no stored data migrates.

## Open Questions

None blocking. The proposal's flagged risk (the SDK's actual shape for `raise`) is fully resolved by
the `javap` disassembly in Context above. The unrelated open item from try-catch-retry itself
(`catch.then`) stays open there and is out of scope here.
