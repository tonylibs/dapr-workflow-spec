## Background

`dws-orchestrator` has no way for a workflow author to *deliberately* fail a task with a specific,
typed error. Every runtime error today is *implicit* — synthesised by `WorkflowErrors.of()` from
whatever failed (a step-service call, a data-flow validation) — and classified after the fact by
string-matching the failure message (`STEP_MARKER`/`DATA_FLOW_MARKER`). This is roadmap **Phase 2,
slice 2.2**: `raise`, the smallest remaining slice of Phase 2, unblocked by slice 2.1
(`try`/`catch`/`retry`, `openspec/changes/try-catch-retry`, merged to `main`, not yet archived),
which built the `catch.errors.with`/`catch.when` machinery `raise` is designed to feed rather than
duplicate.

**Current-state facts, read from the code:**

- `WorkflowErrors.classify(message)` recognises exactly two markers (`DATA_FLOW_MARKER`,
  `STEP_MARKER`) and falls back to `ErrorKind.RUNTIME` for anything else. A raised error's message
  would today be swallowed into `RUNTIME`/500, discarding the author's own `type`/`status`/`title`.
- `StepInvocationException` folds its structured fields into its exception *message* — the only
  thing that survives the Dapr activity boundary back to the workflow method — precisely so
  `WorkflowErrors` can read them back out downstream.
- `InterpreterWorkflow.dispatchBody`'s `StreamEx.of(...)` task-type list does **not** include
  `task.getRaiseTask()`. A `raise` task in a definition today falls through to
  `.orElseThrow(() -> new IllegalStateException("task '" + name + "' has an unsupported type"))` —
  not even the `UnsupportedOperationException` `for` gets. `taskTypeOf` likewise has no `raise`
  branch.
- `EvaluateSetActivity` is the existing template for a pure, no-I/O, in-process activity: it exists
  as an activity purely to keep jq evaluation out of the workflow method's replay loop, registered
  in `WorkflowRuntimeBootstrap`, resolving its task via `DefinitionLookup.taskByName()`.
- `DefinitionLookup.taskByName()` already searches recursively into `try`/`catch.do` lists, so a
  `raise` task nested inside either is already resolvable by name with no change needed.
- `dws-controller`'s `WorkflowCompiler.walk()` already excludes `raise` from what it deploys — its
  existing comment reads `// switch/set/wait/for/raise (and the task lists nested under for/fork)
  deploy nothing.` **No controller change is needed at all**; this was confirmed by reading the code,
  not assumed.
- `dispatchTry`'s catch block (`catch (RuntimeException failure)`) already offers *any* runtime
  failure from inside a `try` list to `CatchDecisionActivity` — a raised error needs no new
  propagation path, only to arrive as an ordinary `RuntimeException` at that point.

**SDK facts, verified by disassembling `serverlessworkflow-types:7.26.0.Final` with `javap`
(resolves the requirement's "known risk" — the requirement's assumption that all five error fields
take `${...}` jq expressions the same way `set`'s fields do turns out to be only partly true):**

- `Task.getRaiseTask()` → `RaiseTask extends TaskBase`. Because it's a `TaskBase`, the existing
  data-flow pipeline wraps it unchanged, exactly as `TryTask` does.
- `RaiseTask.getRaise()` → `RaiseTaskConfiguration.getError()` → `RaiseTaskError`, a one-of:
  `getRaiseErrorDefinition()` → inline `Error`, or `getRaiseErrorReference()` → `String` naming an
  entry in `use.errors`.
- `Use.getErrors()` → `UseErrors.getAdditionalProperties()` → `Map<String, Error>` — the exact same
  shape as `Use.getRetries()` → `UseRetries.getAdditionalProperties()`, which try-catch-retry's D7
  already resolved for named retry policies.
- `Error` has five fields, but **not five string fields**:
  - `type` → `ErrorType`, a one-of: `getLiteralErrorType()` → `UriTemplate` (itself one-of a literal
    `URI` or a literal template `String`), or `getExpressionErrorType()` → `String`.
  - `status` → **plain primitive `int`. There is no expression variant.** This is the actual gap the
    "known risk" was pointing at: the requirement's premise that `status` "can contain `${...}` jq
    expressions the same way `set`'s fields can" does not hold against this SDK version.
  - `instance` → `ErrorInstance`, a one-of: `getExpressionErrorInstance()` → `String` or
    `getLiteralErrorInstance()` → `String`.
  - `title` → `ErrorTitle`, a one-of: `getExpressionErrorTitle()` → `String` or
    `getLiteralErrorTitle()` → `String`.
  - `detail` → `ErrorDetails`, a one-of: `getExpressionErrorDetails()` → `String` or
    `getLiteralErrorDetails()` → `String`.
- Every field except `status` is therefore **typed** as literal-or-expression at the model level —
  a cleaner shape than `set`'s convention of sniffing every string value for a `${...}` wrapper. The
  SDK already tells the reader which case it is; no string inspection is needed.

## Goals / Non-Goals

**Goals:**
- Interpret `raise`: evaluate its configured error (inline or by `use.errors` reference), and fail
  the task with that error surviving intact — not reclassified by `WorkflowErrors.classify()`.
- A `raise` inside a `try` list is offered to that `try`'s `catch` clause through the *same*
  `CatchDecisionActivity` path a real failure already goes through, with no new propagation code.
- A `raise` outside any `try` fails the task and the instance through the existing
  `taskFailed`/`instanceFailed` path, same as any other uncaught `RuntimeException` today.
- `./mvnw verify` green in `dws-orchestrator`; `./mvnw test` green in `dws-controller` (confirming
  no unintended compile-path change, since none is expected).

**Non-Goals:**
- Any `dws-controller` change — confirmed unnecessary, not merely assumed.
- RFC 7807 Problem Details / the standard OWS error-type catalogue — Phase 3, unchanged from
  try-catch-retry's own non-goal.
- `for`, `fork`, generalised nested `do` — later Phase 2 slices.
- Extending `ErrorKind` — a raised error is author-defined, not inferred from a failure class, so it
  does not participate in `ErrorKind` classification at all (see D2).

## Decisions

### D1: Literal fields pass through; expression fields always evaluate — no `${...}` sniffing

For `type`/`instance`/`title`/`detail`, the SDK's one-of already tells the reader whether the author
wrote a literal or an expression. `RaiseErrorActivity` therefore branches on which accessor is
non-null: a literal value is used as-is (a `UriTemplate`'s literal URI/template converted to its
string form for `type`); an expression value is evaluated via `JqEvaluator.evaluate(expr, data,
variables)` unconditionally — no `${...}`-wrapper check, unlike `set`'s convention, because the
model already disambiguates.

- **Why**: the SDK's typed one-of is strictly more information than a bare string. Re-deriving
  "is this an expression" from string content when the parser already answered that question would
  throw away information and risk a literal string that happens to start with `${` being
  misinterpreted.
- **Alternative — treat every field as a `set`-style string, sniffing for `${...}`**: rejected; the
  requirement's own premise for this ("the same way `set`'s fields can"), and it's the wrong model
  for a typed one-of the SDK already resolved for us.

### D2: `status` is a literal int; document the missing expression variant as an SDK gap

`Error.getStatus()` is a plain `int` with no expression counterpart in this SDK version. `raise` uses
it verbatim; there is no computed-status case to support, and none is silently dropped since the
capability spec states plainly that `status` is author-fixed.

- **Why**: nothing to evaluate — the field simply isn't expression-typed here. Following
  try-catch-retry's D9 precedent (`catch.then`'s absence is documented as a known SDK gap with
  defined, additive fallback behaviour) rather than worked around with an invented syntax.
- **Alternative — accept a string for `status` and jq-evaluate it to an int**: rejected; invents
  parallel syntax the SDK does not define, exactly what D9 rejected for `catch.then`.

### D3: Author-supplied `instance` is honoured; absent `instance` is computed from the raising task

When `raise.error.instance` is present (literal or expression, per D1), it is used as the error
object's `instance`. When absent, the runtime computes it as `/<taskName>` for the raising task —
identical to how `WorkflowErrors.build()` already sets `instance` for every other error kind.

- **Why**: the SDK models `instance` as an optional, author-settable field (unlike `try`/`catch`'s
  synthesised error, which has no author to ask), so honouring it when given is the literal reading
  of the schema. Falling back to the raising task's location when absent keeps `raise` consistent
  with the one convention `WorkflowErrors` already established, rather than inventing a second rule.
- **Alternative — always compute `instance` from the task, ignoring an author-supplied value**:
  rejected; discards a field the SDK explicitly exposes for authoring.

### D4: A new exception folds the resolved error into its message behind a distinct marker;
`WorkflowErrors` short-circuits on it instead of classifying

A new `RaisedErrorException extends RuntimeException` carries the already-resolved five-field error
object (as JSON) inside its message, behind a marker distinct from `STEP_MARKER`/`DATA_FLOW_MARKER`
(e.g. a `raised error:` prefix followed by the JSON). `WorkflowErrors.classify()`/`of()` gets a
short-circuit: when the marker is present, the embedded object is parsed back out and returned
unchanged — no `ErrorKind` is assigned, no status/type is re-derived.

- **Why**: only the exception's message survives the Dapr activity boundary — the same constraint
  `StepInvocationException`'s own Javadoc documents and that `WorkflowErrors`'s own Javadoc explains
  as *why* it classifies from a message instead of an exception type. A raised error is
  author-authoritative; running it back through `classify()`'s message-sniffing would either
  misclassify it (if the author's `detail` happens to contain a marker substring) or silently
  discard the author's `type`/`title`. A dedicated marker + short-circuit is the only way to make
  "survives intact" actually hold.
- **Alternative — give `RaisedErrorException` fields and have `WorkflowErrors` accept an `Optional`
  pre-built error object from the caller**: rejected; the caller (the workflow method, reading a
  `CatchDecisionRequest`) only has the exception's *message* to work with too, by the same activity-
  boundary constraint — there is no code path where a typed exception field survives that hop.
- **No new `ErrorKind`**: a raised error is not "classified" as validation/communication/runtime —
  those three exist to give a *shape* to failures the runtime observes. A raised error already has
  its shape, supplied by the author. Adding a fourth `ErrorKind` would imply raised errors get a
  default type/status derived from the kind, which is backwards — the whole point is the author's
  values win.

### D5: `raise.error` reference resolves against `use.errors`, unresolvable name fails loudly

`RaiseTaskError.getRaiseErrorReference()` is looked up in `Workflow.getUse().getErrors()
.getAdditionalProperties()`. A name that doesn't resolve fails the task with a message naming the
missing error definition.

- **Why**: identical shape and identical justification to try-catch-retry's D7 (named retry policies
  resolved from `use.retries`) — reuse the convention rather than inventing a second one for a
  structurally identical case.
- **Alternative — reject unresolved references at compile time in `dws-controller`**: rejected for
  the same reason D7 rejected it for retries — splitting validation across components when all error
  semantics otherwise live in the orchestrator.

### D6: `RaiseErrorActivity` is pure evaluation, returning the resolved object; it does not throw

Mirroring `EvaluateSetActivity`'s shape exactly: `RaiseErrorActivity` resolves `RaiseTaskConfiguration`
(inline or by reference, per D5), evaluates each field per D1–D3, and **returns** the five-field
`ObjectNode` — it does not itself throw `RaisedErrorException`. The workflow method
(`dispatchConcreteTask`'s `RaiseTask` branch) receives that already-resolved, already-recorded value
back from `ctx.callActivity(...).await()` and *then* throws `RaisedErrorException` with it folded
into the message.

- **Why**: this is exactly the shape the requirement scoped ("`RaiseErrorActivity`... evaluates
  `raise.error`'s fields... and returns the resolved error object", "dispatch wiring... calls the new
  activity, then throws"). It also keeps the activity a pure decision/computation unit like
  `EvaluateSwitchActivity`/`EvaluateSetActivity` (testable directly, no exception-based control flow
  crossing the activity boundary), and the throw itself is deterministic on replay because it's
  driven by an already-recorded activity result, not a fresh computation.
- **Alternative — throw `RaisedErrorException` from inside the activity**: rejected; makes the
  activity's "successful" path indistinguishable from a genuine evaluation failure (a malformed jq
  expression in `raise.error.detail`, say), which *should* still be a plain activity failure, not a
  raised-error-shaped one.

### D7: Dispatch wiring reuses the existing failure path; no new propagation code

`task.getRaiseTask()` is added to `dispatchBody`'s `StreamEx.of(...)` list. `dispatchConcreteTask`
gets a `case RaiseTask raiseTask ->` branch that calls `RaiseErrorActivity`, then throws
`RaisedErrorException` with the resolved object. Because this throw happens inside `runTaskList`'s
existing `try { dispatch(...) } catch (RuntimeException e) { taskFailed; throw e; }`, and because
`dispatchTry`'s own `catch (RuntimeException failure)` already offers *any* runtime failure from
inside a `try` list to `CatchDecisionActivity`, a raised error needs zero new propagation code — it
is caught exactly like a real failure would be, precisely because it now *is* a `RuntimeException`
indistinguishable in shape from one. `taskTypeOf` gets an `else if (task.getRaiseTask() != null)
return "raise";` branch alongside the others.

- **Why**: this is the entire payoff of D4 — once a raised error is "just" a `RuntimeException` whose
  message happens to carry a parseable marker, every existing mechanism (task-failure events,
  instance-failure events, `try`/`catch` offering) already handles it with no new branches beyond
  recognising the task type and calling the activity.
- **Alternative — special-case `RaiseTask` inside `dispatchTry`/`runTaskList` to skip straight to
  `CatchDecisionActivity` without going through the generic exception path**: rejected; duplicates
  logic `dispatchTry`'s catch block already has, for no behavioural difference.

### D8: `raise` extends the `workflow-error-handling` capability rather than a new one

The capability spec addition lives in `openspec/changes/raise-task/specs/workflow-error-handling/
spec.md` as `## ADDED Requirements` against the *same* capability try-catch-retry defined, not a new
capability.

- **Why**: `raise` produces exactly the five-field error object `workflow-error-handling` already
  defines and filters on — it is a second *producer* of that same shape (the first being the
  runtime's own implicit synthesis), not a different concern. Splitting it into its own capability
  would force the spec reader to cross-reference two documents to understand one error shape.
- **Alternative — a new `error-raising` capability that composes with `workflow-error-handling`**:
  considered (the task description left this open); rejected because there is no independent
  behaviour to describe — every requirement `raise` needs ("the error survives intact", "it's caught
  by `catch.errors.with`") is phrased entirely in terms of the existing capability's own vocabulary.

### D9: Tests mirror the existing per-unit style; one integration case added to the existing class

Unit tests: a `RaisedErrorExceptionTest`-or-folded-into-`WorkflowErrorsTest` case for the marker
round-trip (mirrors `WorkflowErrorsTest`'s existing classify/build cases), and a
`RaiseErrorActivityTest` mirroring `EvaluateSetActivityTest`'s style (there isn't one today — the
closest sibling is `EvaluateSwitchActivity`'s test, folded into `CatchPolicyTest` for the decision
logic; the equivalent here is a direct unit test of `RaiseErrorActivity.apply(...)` against a
constructed `RaiseErrorRequest`, covering literal fields, expression fields, the `use.errors`
reference, an unresolved reference, and the `instance` present/absent cases). Integration: one new
case in `InterpreterWorkflowIntegrationTest` — a `raise` inside `try`, caught by
`catch.errors.with`/`catch.when` exactly like a real failure — added alongside the existing
try/catch cases rather than a parallel test class, per the requirement's explicit instruction.

- **Why**: matches this repo's established per-unit test convention (one test class per
  activity/builder) and the requirement's explicit "extend `InterpreterWorkflowIntegrationTest`...
  rather than adding a parallel test class."

## Risks / Trade-offs

- **[Risk] A literal `instance`/`title`/`detail` string that happens to look like it could be
  mistaken for something else** → not a risk in practice: D1's branch is on the SDK's one-of
  discriminant, never on string content, so there is no sniffing to fool.
- **[Risk] `status`'s missing expression variant surprises an author expecting `set`-style
  `${...}` everywhere** (D2) → Mitigation: documented plainly in the capability spec as a fixed,
  literal-only field, mirroring how try-catch-retry documented `catch.then`'s absence rather than
  silently ignoring an attempted expression.
- **[Risk] The message-marker approach is fragile if a JSON blob embedded in an exception message
  collides with logging/truncation** (D4) → Mitigation: identical risk profile to
  `StepInvocationException`'s existing marker approach, already accepted for that exception; no new
  exposure introduced.
- **[Trade-off] `RaisedErrorException` has no typed accessors of its own for `type`/`status`/etc.**
  (D4) → Accepted: by design, nothing downstream needs them typed — `WorkflowErrors.of()` is the only
  reader, and it works from the message by necessity (the activity boundary), not by choice.

## Migration Plan (outline; detailed in `tasks.md`)

1. `WorkflowErrors`: short-circuit marker + `RaisedErrorException` (orchestrator, no controller
   change).
2. `RaiseErrorActivity` + its request/response records, registered in `WorkflowRuntimeBootstrap`.
3. Dispatch wiring: `dispatchBody`'s type list, `dispatchConcreteTask`'s `RaiseTask` branch,
   `taskTypeOf`'s `raise` case.
4. Unit tests (`WorkflowErrors`/marker round-trip, `RaiseErrorActivity`).
5. Integration test: `raise` inside `try`, caught by `catch.errors.with`/`catch.when`.
6. `./mvnw verify` in `dws-orchestrator`, `./mvnw test` in `dws-controller` (confirming no
   unintended compile-path change).

**Rollback**: purely additive and gated on a task type that currently throws
`IllegalStateException("... has an unsupported type")`. Reverting restores that failure; no
definition without a `raise` task is affected, no deployed resource changes shape (confirmed: none
did to begin with), no stored data migrates.

## Open Questions

- None blocking. The one item the requirement flagged as a known risk (SDK shape of `raise`) is
  fully resolved by the `javap` disassembly above; the remaining open item from try-catch-retry
  itself (`catch.then`) is unrelated to this slice and stays open there.
