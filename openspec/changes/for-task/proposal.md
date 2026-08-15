## Why

`dws-orchestrator` recognises the DSL 1.0 `for` task and immediately throws
`UnsupportedOperationException`: no workflow can iterate a collection today. Every "run this
step once per record" case — process each item in an inbound batch, wait for a per-item event,
apply a per-item side effect — has to be modelled outside the workflow or unrolled by hand into a
fixed sequence. This is roadmap **Phase 2, slice 2.3**, unblocked by slices 2.1
(`openspec/changes/try-catch-retry`) and 2.2 (`openspec/changes/raise-task`): slice 2.1 built the
scope-aware task-list runner (`runTaskList`) plus the depth guard, and slice 2.2 shipped the
`RaiseErrorException`/`WorkflowErrors` short-circuit — `for` reuses the runner directly and
threads the same `variables` scope map `catch.do` already uses. The payoff is direct: a definition
can express "for each pet in the intake batch, wait for the checkup event and record it" as one
task instead of one-task-per-pet at compile time (which is impossible when the batch size varies at
runtime).

## What Changes

**`for` task interpretation (`dws-orchestrator`)**
- From: `for` is parsed, then rejected at dispatch with `UnsupportedOperationException`.
- To: `for` evaluates its `for.in` expression to a collection, and runs its `for.do` list once per
  element with `$<each>` bound to the current element and `$<at>` bound to the current index (both
  as scope-local jq variables, defaults `"item"` and `"index"` when the definition omits the
  names). When `while` is declared, it is re-evaluated at the top of each iteration and the loop
  stops when it becomes false. Iteration data threads forward: each iteration's body-output
  becomes the next iteration's input, matching the DSL's own sequential-loop convention.
- Reason: the DSL's only iteration construct.
- Impact: non-breaking — definitions without a `for` task behave identically.

**Task lookup (`dws-orchestrator`)**
- From: `DefinitionLookup.search` recurses into `try.try`/`try.catch.do` only; a `set`/`switch`/
  `raise` nested inside `for.do` would fail with `"definition has no task named '<x>'"` at the
  first activity call.
- To: `DefinitionLookup.search` also recurses into `ForTask.getDo()`, matching the incremental
  discipline slice 2.1 used when it first added the `TryTask` branch (one branch per new container
  task type; a general walker waits for slice 2.4's `fork`).
- Reason: prerequisite for any in-process task type to run inside `for.do`.
- Impact: non-breaking — task names are still unique across the whole definition, so extending the
  search space cannot introduce ambiguity.

**No controller changes** — confirmed by reading `WorkflowCompiler.walk()`, not assumed: its
existing comment already excludes both `for` itself and the task lists nested under `for.do` from
what it deploys (`// switch/set/wait/for/raise (and the task lists nested under for/fork) deploy
nothing.`). A `for` whose body uses only in-process task types (`set`/`switch`/`raise`/nested
`for`/nested `try`) works out of the box. Nesting `call`/`run` under `for.do` is slice 2.4's
generalised-nested-`do` problem and remains unresolved by this slice, exactly the same posture
`try-catch-retry` originally shipped with before its own controller-side walk was added.

Additions with no "before" state:
- `EvaluateForActivity`, a pure in-process activity that evaluates `for.in` once and returns the
  collection as a `JsonNode` (an array). Parallel to `EvaluateSwitchActivity`/`EvaluateSetActivity`.
- `EvaluateWhileActivity`, a pure in-process activity invoked once per iteration to evaluate
  `while`'s jq expression for truthiness against the current data with `$<each>`/`$<at>` bound.
  Skipped entirely when `while` is null/blank, so no cost when it isn't declared.
- A `dispatchFor` helper in `InterpreterWorkflow`, mirroring `dispatchTry`'s shape: called from
  `dispatchConcreteTask`'s `case ForTask ->` branch, loops over the resolved collection, binds the
  per-iteration variables into a scope-local `HashMap` (copying `variables` like `recover` already
  does), calls `runTaskList` at `depth + 1` for each iteration, and threads the resulting `data`
  forward to the next iteration.

## Capabilities

### New Capabilities
- `workflow-iteration`: bounded, sequential iteration of a task list over a collection, with
  optional early-exit condition and per-iteration scope-local bindings. Named separately from
  `workflow-error-handling` (which addresses failure semantics, not iteration) and any future
  parallelism capability (which slice 2.4's `fork` will introduce), because iteration is a
  distinct concern with its own vocabulary (`each`/`in`/`at`/`while`/`do`).

### Modified Capabilities
None.

## Impact

- **Components**: `dws-orchestrator` only. `dws-controller` is unaffected — verified against
  `WorkflowCompiler.walk()`'s actual code, which already treats both `for` itself and its nested
  task lists as no-deploy today. Independent builds and CI gates are preserved.
- **`dws-orchestrator` code**: `workflow/activity/EvaluateForActivity.java`,
  `workflow/activity/EvaluateForRequest.java`, `workflow/activity/EvaluateWhileActivity.java`,
  `workflow/activity/EvaluateWhileRequest.java` (all new, parallel to
  `EvaluateSetActivity`/`EvaluateSwitchActivity`), `workflow/activity/DefinitionLookup.java` (one
  added recursion branch), `workflow/InterpreterWorkflow.java` (a `case ForTask forTask ->` branch
  replacing the current stub in `dispatchConcreteTask`, and a `dispatchFor` helper), activity
  registration in `config/WorkflowRuntimeBootstrap.java`.
- **Deployed resources**: none change shape. A definition with a `for` task deploys exactly what
  it deployed before adding one (nothing new for `for` itself, and nothing new for anything
  nested under `for.do` either — this slice does not generalise nested `do`).
- **Dependencies**: none added.
- **Compatibility**: existing definitions are unaffected — none can contain a working `for` task
  today (it throws at dispatch), and every other task type's behaviour is unchanged.
- **Non-goals**: `fork` (parallel branches) — slice 2.4; generalising nested `do` so `call`/`run`
  can live inside `for.do`/`fork` — slice 2.4; per-iteration parallelism/mapping semantics — `for`
  is sequential, `fork` is the parallel counterpart; adding a per-iteration cap distinct from
  `MAX_STEPS`/`MAX_DEPTH` — no observed pathology today, `for.in`'s finiteness bounds iteration
  count intrinsically.
- **CI**: covered by the existing per-component path-filtered workflows; no CI changes.
