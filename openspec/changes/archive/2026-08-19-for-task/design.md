## Context

`dws-orchestrator` interprets its one pinned Open Workflow Specification definition as a
program-counter loop (`InterpreterWorkflow`). `for` is recognised at parse time and immediately
rejected at dispatch: `InterpreterWorkflow.dispatchConcreteTask`'s `case ForTask _` (lines
343–345) throws `UnsupportedOperationException("... uses for, which is recognised but not yet
interpreted")`. `taskTypeOf` (lines 482–483) already returns `"for"`, so lifecycle event
labelling is already correct.

This change implements the OWS DSL 1.0 `for` task — roadmap **Phase 2, slice 2.3** — unblocked by
slice 2.1 (`try`/`catch`/`retry`, `openspec/changes/try-catch-retry`, merged) and slice 2.2
(`raise`, `openspec/changes/raise-task`, merged). Slice 2.1 built the reusable scope-aware
task-list runner (`runTaskList`, `InterpreterWorkflow.java:95–158`), the `MAX_DEPTH` guard, and
the pattern `recover` (lines 442–464) uses to bind a scope-local jq variable into the `variables`
map before calling `runTaskList(..., scoped, depth + 1, ...)`. Slice 2.3 reuses those directly.

**Current-state facts, read from the code rather than assumed:**

- `runTaskList` takes `List<TaskItem> items`, `JsonNode data`, `JsonNode context`,
  `Map<String, JsonNode> variables`, `int depth` and returns a `ScopeResult(data, context, end)`.
  It enforces `MAX_DEPTH` (16) and `MAX_STEPS` (10 000) per call. One call = one scope.
- `recover` clones `variables` into a `HashMap`, binds one entry
  (`scoped.put(decision.errorVariable(), decision.error())`), and calls `runTaskList(..., scoped,
  depth + 1, ...)`. Exact template for binding the loop variables.
- `DefinitionLookup.search` (`DefinitionLookup.java:31–52`) recurses into `TryTask.getTry()` and
  `TryTask.getCatch().getDo()` only. Every in-process activity resolves its target task through
  this method, so a task nested under `for.do` is currently invisible to lookup.
- `JqEvaluator.evaluate(expr, input, variables)` handles jq expressions with named-variable
  binding; `JqEvaluator.evaluateBoolean(expr, input, variables)` (lines 108–123) already applies
  the standard jq truthiness rule (`null`/`false` → false, everything else → true).
  `JqEvaluator.unwrap` transparently accepts `${...}`-wrapped or bare jq expressions.
- `WorkflowCompiler.walk()`'s existing comment reads
  `switch/set/wait/for/raise (and the task lists nested under for/fork) deploy nothing.` Confirmed
  by reading the code: **no controller change is needed**, and nesting `call`/`run` under
  `for.do` remains a slice 2.4 problem (identical posture to `try-catch-retry` before it added
  its own controller walk).

**SDK facts, verified by `javap -classpath …/serverlessworkflow-types-7.26.0.Final.jar
io.serverlessworkflow.api.types.ForTask io.serverlessworkflow.api.types.ForTaskConfiguration`:**

- `ForTask extends TaskBase` — the existing per-task data-flow pipeline
  (`input.from`/`output.as`/`export.as`) wraps `for` unchanged, exactly as it wraps `try`/`raise`.
- `ForTask.getFor()` → `ForTaskConfiguration`; `ForTask.getWhile()` → `String`;
  `ForTask.getDo()` → `List<TaskItem>`.
- `ForTaskConfiguration.getEach()` → `String` (variable name, not an expression).
- `ForTaskConfiguration.getIn()` → `String` (jq expression, plain — no literal/expression
  one-of like `raise.error.type` has).
- `ForTaskConfiguration.getAt()` → `String` (variable name, not an expression).
- `while` is a plain `String` (jq expression). Sibling of `for`, not nested under it.
- No `TryTaskCatch`-style nested config wrapper.

Defaults per DSL 1.0 spec (dsl-reference.md, confirmed against source): `each` defaults to
`"item"`, `at` defaults to `"index"`. Applied at read time in `dispatchFor` when the field is
null or blank.

## Goals / Non-Goals

**Goals:**
- Interpret `for`: evaluate `for.in` to a collection, run `for.do` once per element with
  `$<each>`/`$<at>` bound as scope-local jq variables, thread each iteration's body output as the
  next iteration's input data, and stop early when `while` becomes false.
- Reuse the scope-aware runner (`runTaskList`) and the scope-local-variable pattern (`recover`)
  slices 2.1/2.2 already established, rather than duplicating either.
- Extend `DefinitionLookup.search` to recurse into `ForTask.getDo()` — the minimum change any
  in-process task inside `for.do` needs to work.
- `./mvnw verify` green in `dws-orchestrator`; `./mvnw test` green in `dws-controller` (confirming
  no unintended compile-path change, since none is expected).

**Non-Goals:**
- Any `dws-controller` change — confirmed unnecessary by reading `WorkflowCompiler.walk()`, not
  assumed. Nested `call`/`run` under `for.do` is slice 2.4's problem (generalised nested `do`).
- `fork` (parallel branches) and generalised nested `do` — slice 2.4.
- Per-iteration parallelism or `map`-style output accumulation — `for` is sequential by design;
  `fork` will introduce parallel semantics.
- A new iteration cap distinct from `MAX_STEPS`/`MAX_DEPTH` — `for.in`'s finiteness bounds
  iteration count intrinsically (see D5); no observed pathology today.
- Extending `DefinitionLookup.search` beyond the one branch this slice needs — `fork`'s branch
  waits for slice 2.4, following slice 2.1's incremental precedent.

## Decisions

### D1: `EvaluateForActivity` returns the resolved collection once; the workflow method loops

- **Choice**: a new in-process activity `EvaluateForActivity`, parallel to
  `EvaluateSwitchActivity`/`EvaluateSetActivity`, evaluates `for.in` against the current data with
  the current `variables` scope and returns the result as a `JsonNode` (expected to be an array;
  a non-array yields an `IllegalStateException` with the task name in the message so replay stays
  deterministic on any subsequent call for the same activity). The workflow method
  (`dispatchConcreteTask`'s `case ForTask forTask ->` branch) calls a new `dispatchFor` helper,
  which reads the collection back and loops.
- **Why**: every jq evaluation in this codebase runs inside an activity — that is the load-bearing
  invariant keeping the workflow method's replay loop free of computation Dapr did not record.
  Introducing an inline eval here for one task type would break the pattern uniformly across
  `set`/`switch`/`catch`/`raise`.
- **Alternative considered — reuse `EvaluateSetActivity`**: rejected. `EvaluateSetActivity`
  evaluates a structured `set` map, not a bare jq expression on `for.in`. Different input
  contract, wrong reuse.

### D2: `EvaluateWhileActivity` is called once per iteration; skipped entirely when `while` is absent

- **Choice**: a second in-process activity `EvaluateWhileActivity` takes `(taskName, data,
  variables)` and returns a `boolean` — a thin wrapper over
  `JqEvaluator.evaluateBoolean(forTask.getWhile(), data, variables)`. `dispatchFor` calls it once
  per iteration, immediately after binding the iteration variables and before running the body.
  When `forTask.getWhile()` is `null` or blank, the workflow method skips the activity call
  entirely — no crossing in the common case.
- **Why**: `while` must see the *current* iteration's variables and the current data, so it
  cannot be pre-evaluated in `EvaluateForActivity` (D1). One activity call per iteration matches
  the one-crossing-per-decision shape `EvaluateSwitchActivity` already sets. Skipping the call
  when `while` is absent keeps the common case free.
- **Alternative considered — inline `JqEvaluator.evaluateBoolean` in the workflow method**:
  rejected. Breaks the "no jq eval in the replay loop" invariant for a single-line saving.
- **Alternative considered — fold `while` into `EvaluateForActivity`**: rejected. Impossible;
  `while` must see per-iteration state that is only known after the previous iteration completed
  (D5).

### D3: `dispatchFor` clones `variables` into a scope-local map per iteration; the loop lives in the workflow method

- **Choice**: a new private helper `dispatchFor(ctx, forTask, name, data, context, variables,
  depth, events, mapper)` mirrors `dispatchTry`'s method shape. Per iteration, it copies
  `variables` into a `HashMap`, binds `$<each>` (`for.each`, defaulting to `"item"`) to the
  current element and `$<at>` (`for.at`, defaulting to `"index"`) to the current index as a
  `JsonNode` (`IntNode`), evaluates `while` if declared (D2), then calls `runTaskList(ctx,
  forTask.getDo(), data, context, scoped, depth + 1, events, mapper)` — the same call shape
  `recover` already uses. The returned `ScopeResult`'s `data` becomes the next iteration's input
  `data` (D4); its `context` threads forward too (matching how sequential tasks in a `do` list
  update `$context`); its `end` is respected — an iteration that ends the whole instance
  (`ScopeEnd.END`) short-circuits the loop and propagates up, and `ScopeEnd.EXIT` completes only
  that iteration's scope (the loop continues).
- **Why**: identical to `recover`'s established shape, differing only in that the bound variables
  change per iteration and the runner is called in a loop. Only the workflow method can invoke
  `runTaskList` (which itself calls `ctx.callActivity`/`ctx.createTimer`) — an activity cannot.
- **Alternative considered — a super-activity that runs the whole loop internally**: rejected.
  Impossible; activities cannot drive `runTaskList`.

### D4: Iterations thread `data` forward; the final iteration's output is the `for` body's output

- **Choice**: iteration N + 1's input `data` is iteration N's `runTaskList` result's `data`. The
  final iteration's output `data` is what `dispatchFor` returns to `dispatchConcreteTask` as the
  `for` body's `data`, which then flows through the `for` task's own `output.as`/`export.as` via
  `dispatch`'s existing data-flow pipeline wrap.
- **Why**: matches the DSL's sequential-loop convention (a `do` list's tasks thread data
  sequentially; `for.do` is a `do` list re-run per element, so element-to-element must thread
  too). The DSL spec's own example uses `output.as: '.pets + [{ "id": $pet.id }]'` — the body's
  own output-shaping accumulates by appending to `.pets` in the threaded `data`, which only makes
  sense if `data` threads. Zero iterations (empty collection, or `while` false at entry) return
  the input `data` unchanged, so a downstream `output.as` still sees the incoming document.
- **Alternative considered — each iteration starts from the same immutable input, collecting an
  array of per-iteration outputs**: rejected. That's `fork`/`map` semantics — parallel branches
  each producing an output. `for` is the sequential counterpart; parallel semantics belong to
  slice 2.4's `fork`.

### D5: No new iteration cap; `MAX_STEPS`/`MAX_DEPTH`'s existing purposes are preserved

- **Choice**: no `MAX_ITERATIONS` guard added. Each iteration's `runTaskList` call gets its own
  `steps` counter starting at 0, so `MAX_STEPS`'s per-scope purpose is preserved for the body.
  Iterations happen at the same depth (they are siblings in `dispatchFor`'s own loop, not nested
  in one another), so `MAX_DEPTH`'s guard is unchanged regardless of iteration count. `for.in`
  evaluates a jq expression over the finite input document, producing a finite array; `while`
  provides only early-exit, not extension beyond that array.
- **Why**: no observed pathology requires a new guard. `for.in`'s finiteness bounds iteration
  count intrinsically. Adding `MAX_ITERATIONS` today would guard against a scenario nobody has
  seen; if it becomes real, add it then.
- **Alternative considered — add `MAX_ITERATIONS` (e.g. 100 000) prophylactically**: rejected as
  speculative. This project's existing guards were added in response to real concerns
  (definition loops between tasks for `MAX_STEPS`, call-stack exhaustion for `MAX_DEPTH`); the
  same discipline applies here.

### D6: `DefinitionLookup.search` recurses into `ForTask.getDo()`, one branch mirroring the `TryTask` branch

- **Choice**: extend `DefinitionLookup.search` with an `else if (item.getTask().getForTask() !=
  null) { nested = search(item.getTask().getForTask().getDo(), taskName); ... }` branch, in the
  same shape as the existing `TryTask` branch.
- **Why**: without this, any in-process task nested under `for.do` (`set`/`switch`/`raise`/
  nested `try`/`for`) fails at its first activity call with `"definition has no task named
  '<x>'"`. The change is one branch, keeping the search's discipline of one branch per new
  container task type — the same incremental posture slice 2.1 took when it added the `TryTask`
  branch.
- **Alternative considered — build a general "walk any container's nested lists" search now**:
  rejected. Doing that correctly requires knowing every container's shape; slice 2.4's `fork`
  will introduce a container whose shape isn't finalised yet. YAGNI; each container type adds its
  own branch when it arrives.

### D7: `for` gets its own `workflow-iteration` capability spec

- **Choice**: capability additions live at
  `openspec/changes/for-task/specs/workflow-iteration/spec.md` as a new capability, not `##
  ADDED Requirements` under `workflow-error-handling` (slice 2.2's home) or any other existing
  capability.
- **Why**: `for`'s vocabulary (`each`/`in`/`at`/`while`/`do`) and semantics (bounded sequential
  iteration) are genuinely new — the existing capabilities cover data flow, error handling, and
  step invocation. Attaching iteration to any of those would force a reader to cross-reference a
  spec whose title suggests a different concern.
- **Alternative considered — extend `workflow-error-handling`**: rejected. Iteration is not an
  error concern.
- **Alternative considered — extend a not-yet-existing `workflow-control-flow` covering
  `switch`/`for`/`fork`**: rejected. `switch` is already implicit in the existing
  interpreter/spec surface without a named capability; retrofitting one now would spawn a scope
  question this slice doesn't need to answer. Ship the narrow capability that describes `for`
  cleanly, and let 2.4's `fork` decide whether to join or spawn its own.

### D8: Zero-iteration and early-exit shapes return input data unchanged; task-lifecycle events fire once per iteration body

- **Choice**: when `for.in` evaluates to an empty array, or `while` is false on iteration 0's
  first evaluation, `dispatchFor` returns `(data, context, then, ScopeEnd.FELL_THROUGH)` without
  running `runTaskList`. Each iteration's body is a genuine `runTaskList` call, so the existing
  per-task `taskStarted`/`taskCompleted`/`taskFailed` events fire for each task inside `for.do`
  once per iteration — same event volume the same body would emit if unrolled at author-time.
  The `for` task itself emits exactly one pair of lifecycle events (`taskStarted` + `taskCompleted`
  or `taskFailed`) around the whole loop, produced by `runTaskList`'s existing wrap around
  `dispatch`.
- **Why**: matches the "each task = one lifecycle pair" contract lifecycle event consumers
  already rely on, without inventing per-iteration lifecycle events for the `for` task itself
  (which would be a new event shape).
- **Alternative considered — emit per-iteration `for.iterationStarted`/`for.iterationCompleted`
  events**: rejected. New event shape, no consumer today, and observers can already correlate
  iterations from the ordered per-body events (which carry the iteration's task names and the
  timestamps of their crossings).

### D9: Tests mirror the existing per-unit style; interpreter-loop scenarios extend `InterpreterWorkflowIntegrationTest`

- **Choice**: unit tests for `EvaluateForActivity` and `EvaluateWhileActivity` in the same style
  `RaiseErrorActivityTest` uses (seed `WorkflowSupport` from an inline YAML definition, call the
  activity directly, assert on the result). Integration coverage extends
  `InterpreterWorkflowIntegrationTest` (the requester's explicit instruction) with: basic
  iteration, `while` early-exit, index-variable usage, empty collection (zero iterations), a
  `for` nested inside a `try` (proving the scope-stacking works). `taskTypeOf`'s existing
  `"for"` return is already covered by the lifecycle-event assertions in the new integration
  cases.
- **Why**: matches this repo's per-unit test convention and slices 2.1/2.2's existing test-file
  layout.

## Risks / Trade-offs

- **[Risk] A definition nesting `call`/`run` inside `for.do` still fails at runtime** because
  slice 2.3 does not extend `WorkflowCompiler.walk()` (D0 non-goal). → Mitigation: documented in
  the proposal and specs as an explicit non-goal owned by slice 2.4. Behaviour is unchanged from
  today (any `call`/`run` nested in a control-flow task except `try`/`catch.do` is already
  unreachable at deploy time).
- **[Risk] A very large `for.in` collection could exhaust workflow-instance memory or produce a
  very long-running instance** (D5). → Mitigation: accepted as a definition-authoring concern for
  now, matching this repo's discipline of adding guards in response to observed pathologies.
  `for.in`'s finiteness plus `MAX_STEPS`'s per-body cap still bound total work per instance.
- **[Trade-off] Two new activities rather than one** (D1 + D2). → Accepted. Splitting by concern
  (evaluate once vs. evaluate per iteration) is the reason; merging them would either evaluate
  `while` too eagerly or make `EvaluateForActivity` a stateful iteration engine, which is
  impossible for a stateless activity that must be replay-deterministic.
- **[Trade-off] `DefinitionLookup.search` grows one branch rather than becoming a general
  walker** (D6). → Accepted. Same incremental discipline slice 2.1 used; 2.4's `fork` will add
  the next branch when its container shape is finalised.

## Migration Plan

1. `EvaluateForActivity` + `EvaluateForRequest` (record). Evaluate `for.in`, return the
   collection as `JsonNode`; reject non-array with the task name in the message. Register in
   `WorkflowRuntimeBootstrap`. Unit tests for literal `${...}`-wrapped and bare-jq expressions,
   scope-variable binding, and non-array rejection.
2. `EvaluateWhileActivity` + `EvaluateWhileRequest` (record). Delegate to
   `JqEvaluator.evaluateBoolean`. Register in `WorkflowRuntimeBootstrap`. Unit tests for
   truthy/falsy jq results, `${...}` wrapping, and variable bindings.
3. Extend `DefinitionLookup.search` to recurse into `ForTask.getDo()`. Direct unit test that a
   name defined inside `for.do` resolves.
4. `InterpreterWorkflow.dispatchConcreteTask`: replace the `case ForTask _` stub with
   `case ForTask forTask -> dispatchFor(ctx, forTask, name, data, context, variables, depth,
   events, mapper);`. Implement `dispatchFor` per D3/D4/D5/D8.
5. Integration coverage in `InterpreterWorkflowIntegrationTest`: basic iteration, `while`
   early-exit, index-variable usage, empty collection, `for` inside `try`.
6. `./mvnw verify` in `dws-orchestrator`; `./mvnw test` in `dws-controller` (confirming no
   unintended compile-path change).
7. Roadmap update: `docs/roadmaps/openworkflow-features.md` §1 (`for` row from ⚠️ to ✅), §4a
   (slice 2.3 row from ❌ to ✅). Do NOT touch `openwiki/architecture/roadmap.md` — stale
   generated mirror.

**Rollback**: purely additive and gated on a task type that currently throws
`UnsupportedOperationException("... uses for, which is recognised but not yet interpreted")`.
Reverting restores that failure; no definition without a `for` task is affected, no deployed
resource changes shape (confirmed none did), no stored data migrates.

## Open Questions

None blocking. Every SDK-shape question is answered by the `javap` output; every "does this reuse
existing machinery" question is answered by the codebase findings. The unrelated observation about
`openspec/changes/try-catch-retry` never having been run through `/opsx:archive` is flagged in
the requester's brief for later attention and is not this slice's problem.
