## Why

`dws-orchestrator` has no way for a workflow author to *deliberately* fail a task with a specific,
typed error — only implicit failures (a step-service error, a data-flow validation) produce the
runtime error object today, and an author-raised failure would currently be misclassified as a
generic runtime error by `WorkflowErrors.classify()`. This is roadmap **Phase 2, slice 2.2**,
unblocked by slice 2.1 (`try`/`catch`/`retry`, merged to `main`), whose `catch.errors.with`/
`catch.when` machinery `raise` is designed to feed rather than duplicate. The payoff: a definition
can construct and throw a precise, filterable error instead of relying on an incidental failure to
produce one.

## What Changes

**`raise` task interpretation (`dws-orchestrator`)**
- From: `raise` is not recognised by `dispatchBody`'s task-type dispatch; a `raise` task in a
  definition falls through to `IllegalStateException("... has an unsupported type")`.
- To: `raise` evaluates its configured error (inline or by `use.errors` reference) and fails the
  task with that exact error — surviving intact through `WorkflowErrors`, not reclassified.
- Reason: closes the DSL's only remaining gap in deliberate error construction.
- Impact: non-breaking — definitions without a `raise` task behave identically.

**Error classification (`WorkflowErrors`)**
- From: `classify()` recognises two markers (`STEP_MARKER`, `DATA_FLOW_MARKER`) and defaults
  anything else to `ErrorKind.RUNTIME`, discarding whatever a raised error's own `type`/`status`/
  `title` would have been.
- To: a third, distinct marker identifies a raised error's message; when present, `classify()`/`of()`
  short-circuits and returns the author's own five-field object unchanged, with no `ErrorKind`
  assigned.
- Reason: only an exception's message survives the Dapr activity boundary, so this is the only place
  a raised error's fields can be recovered — and they must come back unmodified for `raise` to be
  useful with `catch.errors.with`.
- Impact: non-breaking — the two existing markers and their classification are unchanged.

**No controller changes** — confirmed by reading `WorkflowCompiler.walk()`, not assumed: its existing
comment already excludes `raise` from what it deploys (`switch/set/wait/for/raise ... deploy
nothing`). `raise` joins `switch`/`set`/`wait` in the category of tasks interpreted entirely
in-process, with nothing new for the controller to compile.

Additions with no "before" state:
- `RaiseErrorActivity`, a pure in-process activity (parallel to `EvaluateSetActivity`) that resolves
  a `raise` task's configured error — literal or `${...}`-expression fields per the SDK's typed
  one-of model, `use.errors` references resolved the same way try-catch-retry resolves named retry
  policies — into the DSL's five-field runtime error object.
- `RaisedErrorException`, parallel to `StepInvocationException`, folding the resolved object into
  its message behind a dedicated marker.
- A raised error inside a `try` list reaches that `try`'s `catch` clause through the *existing*
  `CatchDecisionActivity` path with no new propagation code, because it is (by construction) an
  ordinary `RuntimeException` by the time it leaves the activity boundary.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `workflow-error-handling`: adds `raise` as a second producer of the capability's existing
  five-field runtime error object — evaluating `raise.error` (inline or `use.errors`-referenced),
  honouring an author-supplied `instance` or computing one from the raising task, and delivering the
  result unmodified to the same `catch.errors.with`/`catch.when` filtering slice 2.1 already
  defined. No existing requirement in this capability changes; this is purely additive.

## Impact

- **Components**: `dws-orchestrator` only. `dws-controller` is unaffected — verified against
  `WorkflowCompiler.walk()`'s actual code, which already treats `raise` as a no-deploy task type.
  Independent builds and CI gates are preserved.
- **`dws-orchestrator` code**: `error/RaisedErrorException.java` (new), `error/WorkflowErrors.java`
  (new marker + short-circuit branch), `workflow/activity/RaiseErrorActivity.java` and its
  request/response records (new, parallel to `EvaluateSetActivity`), `workflow/InterpreterWorkflow.java`
  (`dispatchBody`'s task-type list, a `RaiseTask` branch in `dispatchConcreteTask`, a `"raise"` case
  in `taskTypeOf`), and activity registration in `config/WorkflowRuntimeBootstrap.java`.
- **Deployed resources**: none change shape. A definition with a `raise` task deploys exactly what it
  deployed before adding one (nothing new for that task).
- **Dependencies**: none added.
- **Compatibility**: existing definitions are unaffected — none can contain a working `raise` task
  today (it fails at dispatch), and every other task type's behavior is unchanged.
- **Non-goals**: RFC 7807 Problem Details and the standard error-type catalogue (Phase 3, unchanged
  from try-catch-retry's own non-goal); `for`/`fork`/generalised nested `do` (later Phase 2 slices);
  a computed/expression form for `raise.error.status` — the pinned SDK
  (`serverlessworkflow-types:7.26.0.Final`) models `status` as a plain `int` with no expression
  variant, so this is documented as an SDK gap rather than worked around, the same treatment
  try-catch-retry gave `catch.then`'s absence.
- **CI**: covered by the existing per-component path-filtered workflows; no CI changes.
