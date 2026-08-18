## Context

`dws-orchestrator` interprets its one pinned Open Workflow Specification definition as a
program-counter loop (`InterpreterWorkflow`). `fork` is recognised at parse time and immediately
rejected at dispatch: `InterpreterWorkflow.dispatchConcreteTask`'s `case ForkTask _` branch does
not exist yet — `fork` falls through the `dispatchBody` switch to `default -> throw new
IllegalStateException("task '" + name + "' has an unsupported type")` (there is no dedicated
`ForkTask` arm in `dispatchBody`'s `StreamEx.of(...)` list either, so `task.getForkTask()` is
never even offered to the dispatch chain today). `taskTypeOf` also has no `fork` branch — both gaps
close in this slice.

This change implements the OWS DSL 1.0 `fork` task — roadmap **Phase 2, slice 2.4**, the last slice
of Phase 2 — unblocked by slice 2.1 (`try`/`catch`/`retry`, merged), 2.2 (`raise`, merged), and 2.3
(`for`, merged). Those slices built the scope-aware task-list runner (`runTaskList`,
`InterpreterWorkflow.java:97-160`), the `MAX_DEPTH` guard, and the `DefinitionLookup.search`
recursion pattern (one branch per container task type: `TryTask`, then `ForTask`). Slice 2.4 adds
the third branch and, unlike 2.1-2.3, cannot reuse `runTaskList` directly for the body itself —
`fork`'s branches must run *concurrently*, and `runTaskList` is a single-threaded program-counter
loop over one scope.

**Current-state facts, read from the code rather than assumed:**

- `runTaskList` takes `List<TaskItem> items` and runs them sequentially in one scope, enforcing
  `MAX_DEPTH`/`MAX_STEPS`. It is the vehicle every other container task type (`try`, `for`) uses for
  its nested list — but a scope is inherently sequential (`pc` advances one task at a time), so it
  cannot express "run N lists at once."
- `io.dapr.durabletask.Task<V>` (the handle every `ctx.callActivity`/`createTimer`/
  `waitForExternalEvent` returns) exposes `await()`, `thenApply(Function<V,U>)`,
  `thenAccept(Consumer<V>)` — **no `thenCompose`/flatMap**. A `thenApply` lambda must return a
  plain value, not another `Task`, so the existing "await eagerly" dispatch style
  (`ctx.callActivity(...).thenApply(...).await()`, used throughout `dispatchConcreteTask`) cannot
  be mechanically turned into an unawaited multi-step chain for anything beyond a single leaf
  activity call. Verified via `javap` against the pinned `durabletask-client:1.18.0` jar.
- `WorkflowContext` (pinned `dapr-sdk-workflows:1.18.0`, verified via `javap`) exposes:
  - `<V> Task<List<V>> allOf(List<Task<V>>)` — throws `CompositeTaskFailedException` (which
    `extends RuntimeException`) if any input task fails; otherwise resolves to the results in
    **input-list order**, not completion order.
  - `Task<Task<?>> anyOf(List<Task<?>>)` — resolves to the first task to *settle* (succeed or
    fail), whichever comes first; does not touch the others.
  - `<V> Task<V> callChildWorkflow(String name, Object input, String instanceId, Class<V>
    resultType)` — starts an independent, deterministic child workflow instance and returns one
    `Task<V>` handle for it. This is the only primitive in the SDK that turns an arbitrary,
    multi-step, replay-safe computation into a single combinable `Task`.
- `ForkTaskConfiguration` (pinned `serverlessworkflow-types:7.26.0.Final`, verified via `javap`):
  `getBranches()` returns `List<TaskItem>` — the *same shape* `do`, `try.try`, `try.catch.do`, and
  `for.do` already use, not a raw map. `isCompete()` returns `boolean`, default `false`.
- `DefinitionLookup.search` (`DefinitionLookup.java:33-64`) already recurses into `TryTask.getTry()`
  /`getCatch().getDo()` and `ForTask.getDo()` (the latter shipped in `for-task`, slice 2.3 —
  `docs/roadmaps/openworkflow-features.md`'s "nested `do`" row, which says "only wired to
  `try`/`catch.do`", is **stale** as of that merge and is corrected as part of this slice's roadmap
  update).
- `WorkflowCompiler.walk()` and `collectTaskNames()` (`WorkflowCompiler.java:200-230`,
  `173-196`) recurse into `TryTask` only. Neither has a `ForTask` or `ForkTask` branch. The
  existing code comment (`// switch/set/wait/for/raise (and the task lists nested under
  for/fork) deploy nothing.`) is accurate today and becomes stale once this slice ships.
- `CompositeTaskFailedException extends RuntimeException` (verified via `javap`) — it already
  satisfies `runTaskList`'s `catch (RuntimeException e)` and `dispatchTry`'s `catch
  (RuntimeException failure)` blocks with no new error-handling code.

## Goals / Non-Goals

**Goals:**
- Interpret `fork`: run each branch under `fork.branches` concurrently, each starting from the same
  input data the `fork` task received; join (`compete: false`, default) waits for all branches and
  returns their outputs as a JSON array in declared branch order; race (`compete: true`) returns the
  first branch to settle and never awaits the rest.
- Reuse the existing per-task dispatch pipeline (`InterpreterWorkflow.dispatch`) unchanged inside
  each branch, rather than duplicating or rewriting it, by running each branch as its own child
  workflow instance.
- Extend `DefinitionLookup.search` to recurse into `ForkTask.getFork().getBranches()`.
- Extend `WorkflowCompiler.walk()` and `collectTaskNames()` to recurse into both `ForkTask` branches
  and `ForTask.getDo()` — closing `for-task`'s explicitly logged gap in the same change, since both
  are the same one-line-shape extension and the roadmap's "nested `do`" row covers both.
- `./mvnw verify` green in `dws-orchestrator`; `./mvnw test` green in `dws-controller`.

**Non-Goals:**
- True cancellation/termination of losing `compete: true` branches. The SDK's `Task` type exposes
  no `cancel()`, and `WorkflowContext` exposes no child-workflow-terminate call reachable from
  inside a deterministic replay without routing through yet another activity (a real option, but a
  Phase-3-shaped fault-tolerance feature, not this slice's job). Losing branches run to completion
  in the background; only their result is ignored.
- Merging `$context` across branches, or threading it out of `fork` at all. `fork` leaves `$context`
  unchanged from what entered it; a branch's own `export.as` is visible only inside that branch.
- Interpreting task-level `if` conditions. Pre-existing gap across every task type (`dispatch`/
  `dispatchBody` never read `task.getIf()` before this slice either) — not fork-specific, not
  addressed here.
- A per-branch or per-fork timeout distinct from the workflow's existing guards. Phase 3 territory,
  same posture `try-catch-retry` took on `retry.limit.attempt.duration`.
- RFC 7807 Problem Details, the standard error-type catalogue — Phase 3, unchanged non-goal.
- Extending `DefinitionLookup.search`/`walk()`/`collectTaskNames()` beyond the two branches this
  slice needs (`ForkTask`, `ForTask`) — no further container types exist in the DSL today.

## Decisions

### D1: Each `fork` branch runs as its own child Dapr workflow instance, combined via `allOf`/`anyOf`

- **Choice**: a new `Workflow` implementation, `ForkBranchWorkflow`, registered under a fixed name
  (not derived from `document.name`, avoiding collision with the top-level workflow registration).
  `dispatchFork` starts one child workflow per branch via `ctx.callChildWorkflow(ForkBranchWorkflow
  .NAME, new ForkBranchInput(branch.getName(), data, context, variables, depth + 1), instanceId,
  JsonNode.class)` — **not** awaited immediately — collects the resulting `Task<JsonNode>` handles
  into a list, then combines them with `ctx.allOf(handles)` (`compete: false`) or
  `ctx.anyOf(wildcardHandles)` (`compete: true`).
- **Why**: `allOf`/`anyOf` are the SDK's only fan-out/fan-in primitives, and each only combines
  independent `Task` handles that already exist. A branch that is itself a `try` or `for` needs
  multiple sequential `ctx.callActivity` calls — impossible to express as one non-blocking `Task`
  under this API (D-note: no `thenCompose`, see Context). A child workflow is a full, independent,
  deterministic `WorkflowContext` that can run that sequential dispatch entirely inside itself while
  still surfacing as exactly one `Task<V>` to the parent — this is the SDK's documented mechanism
  for exactly this shape of problem.
- **Alternative considered — refactor the whole interpreter to return unawaited `Task<Body>`
  chains**: rejected. The SDK gives no way to chain a second `ctx.callActivity` off a `thenApply`
  result without awaiting first, so this doesn't actually solve the problem for any branch with more
  than one step — and it would touch every task type's dispatch code for a generalization only
  `fork` needs, risking behavioral drift between two dispatch styles.
- **Alternative considered — run branches sequentially, disguised as "parallel"**: rejected as
  dishonest to the DSL's semantics; `compete` (race) is meaningless without genuine concurrency.

### D2: The branch workflow reuses `InterpreterWorkflow.dispatch` directly; no duplicated dispatch logic

- **Choice**: widen `InterpreterWorkflow.dispatch(...)` from `private` to package-private (same
  pattern `runTaskList` already uses, for the same reason — test/reuse access within the package).
  `ForkBranchWorkflow.execute(ctx)` reads a `ForkBranchInput(String taskName, JsonNode data,
  JsonNode context, Map<String, JsonNode> variables, int depth)`, resolves the branch's `Task` via
  `DefinitionLookup.taskByName(taskName)` (unchanged signature — branch task names are globally
  unique like every other task name), and calls `new InterpreterWorkflow().dispatch(ctx, task,
  taskName, data, context, variables, depth, events, mapper)` — the exact method every other task
  type's dispatch already goes through, including nested `try`/`for`/`fork` inside the branch.
- **Why**: task names are unique across the whole definition (existing invariant, enforced at
  compile time); a branch's own `TaskItem.getName()` is already sufficient to resolve it via the
  existing lookup signature — no new lookup key shape needed. Reusing `dispatch` verbatim means a
  bug fix or new task type automatically works inside a fork branch too, with zero duplicated
  switch statement.
- **Alternative considered — a second copy of `dispatchBody`'s switch inside `ForkBranchWorkflow`**:
  rejected. Two copies of the same dispatch logic drift out of sync; there is no need since the
  existing method is directly reusable with a visibility change alone.

### D3: `compete: false` returns a JSON array in declared branch order; `$context` does not thread across branches

- **Choice**: `ctx.allOf(handles)` — `handles` built by iterating `forkTask.getFork().getBranches()`
  in declared order — resolves to `List<JsonNode>` in that same order (the SDK's own contract:
  input-list order, not completion order). `dispatchFork` wraps that list into a `JsonNode` array
  and returns it as the `fork` task's body output. The context entering `fork` is returned unchanged
  as the context leaving it; each branch's own `export.as` (if any) is visible only within that
  branch's own child-workflow instance and is discarded when the branch completes.
- **Why**: matches the requirement directly ("array of outputs in branch order"). No context-merge
  semantics exist anywhere in this codebase or the DSL for concurrent writers — inventing one here
  would be speculative. This is exactly the "parallel branches each producing an output" semantics
  `for-task`'s own D4 explicitly earmarked as `fork`'s territory, not `for`'s.
- **Alternative considered — merge branch contexts with last-write-wins by branch order**: rejected.
  No requirement asks for this, and it would silently drop writes from every branch but the last —
  worse than not threading context at all, which is at least unsurprising.

### D4: `compete: true` races via `anyOf`; losing branches are never awaited, not terminated

- **Choice**: `ctx.anyOf(handles)` (built as `List<Task<?>>`, a wildcard copy of the same
  `Task<JsonNode>` handles used for D3) resolves to the winning `Task<?>`; `dispatchFork` calls
  `.await()` on that winning task a second time to obtain its value (or propagate its failure — see
  D6). The other branches' `Task` handles are simply never awaited. Their child-workflow instances
  continue running to completion server-side, independently, publishing their own lifecycle events;
  the parent observes nothing further about them.
- **Why**: this is exactly what the SDK's public surface supports — `Task` exposes no `cancel()`,
  and `WorkflowContext` exposes no in-band "terminate child workflow" call safe to invoke from
  replay-deterministic code (Dapr's workflow-management terminate-by-instance-id API is an external
  administrative call, not a `WorkflowContext` primitive — routing it through an activity is
  possible but is genuinely new fault-tolerance surface, not a `fork`-specific concern).
- **Alternative considered — call an activity that invokes the management API to terminate losing
  branches once the winner is known**: deferred, not rejected outright. Real and used in some
  Dapr Workflow race-pattern samples, but it adds an activity, a client dependency inside the
  activity, and a documented "best-effort, not guaranteed" caveat — scoped out of this slice
  (flagged as a Risk below, with a natural follow-up path).

### D5: A branch's internal `end`/`exit` only completes that branch, never the parent

- **Choice**: `ForkBranchWorkflow` returns only the branch's final `data` (`JsonNode`) across the
  child-workflow boundary — `ScopeEnd` is not part of `ForkBranchInput`'s output contract. Whatever
  `ScopeEnd` the branch's own dispatch produces (`FELL_THROUGH`, `EXIT`, or `END`, e.g. from a
  nested `try`/`for` inside the branch), it only unwinds that child instance's own scope stack;
  `dispatchFork` in the parent always treats a completed branch as "done, here is its data."
- **Why**: a child workflow has no mechanism to signal "terminate my parent," and the DSL does not
  define what `end` means from inside a parallel branch. Treating a branch's own termination as
  local to the branch is the only interpretation that doesn't require inventing new cross-instance
  semantics.
- **Alternative considered — propagate a branch's `ScopeEnd.END` to end the whole enclosing
  workflow instance**: rejected. No mechanism exists to do this safely, and the DSL brief did not
  ask for it.

### D6: No new failure-handling code — `CompositeTaskFailedException` and double-`await()` already fit

- **Choice**: `compete: false` failures propagate as `CompositeTaskFailedException` (already
  `extends RuntimeException`) straight out of `dispatchFork`, through `dispatch`'s and
  `runTaskList`'s existing `catch (RuntimeException e)` blocks, to any enclosing `try`'s
  `catch`/retry machinery — identical to how any other task's failure is handled today.
  `compete: true` failures surface only if the *winning* (first-to-settle) branch failed —
  `.await()` on the winning `Task` throws the branch's own `RuntimeException`, same path.
- **Why**: zero new error-handling code is needed; `fork` composes into the existing `try`/`catch`/
  `retry` machinery for free, which is the same posture every prior slice took (build on the
  scope-aware runner and existing failure path rather than inventing a parallel one).
- **Alternative considered — a dedicated `ForkFailureException` wrapping which branch(es) failed**:
  rejected as unnecessary; `CompositeTaskFailedException.getExceptions()` already carries the
  underlying failures for `compete: false`, and nothing downstream needs a fork-specific type today.

### D7: `WorkflowCompiler.walk()` and `collectTaskNames()` gain `ForkTask` and `ForTask` branches — flagged for review

- **Choice**: `walk()` gets `else if (task.getForkTask() != null) { walk(task.getForkTask()
  .getFork().getBranches(), steps, bindings); }`, mirroring the existing `TryTask` branch exactly
  (same `List<TaskItem>` shape, confirmed via `javap`). `walk()` also gets `else if
  (task.getForTask() != null) { walk(task.getForTask().getDo(), steps, bindings); }`, closing
  `for-task`'s logged gap. `collectTaskNames()` gets the same two branches for duplicate-name
  detection (previously only `TryTask` was covered — a duplicate name inside `for.do` was
  undetected before this slice, a latent gap predating `fork`).
- **Why**: without this, `call`/`run` nested inside a `fork` branch or `for.do` compiles to no
  `StepService`, so the orchestrator would invoke an undeployed Dapr app-id — the exact failure
  `try-catch-retry` fixed for `try` in slice 2.1. Both extensions are mechanically identical to the
  existing `TryTask` branch because `fork.branches` and `for.do` are both already `List<TaskItem>`.
- **Why flagged rather than just shipped**: the requirement brief explicitly asked for this to be a
  design-review checkpoint before implementation, since it's a **new-deployed-resources** change
  (definitions that nest `call`/`run` under `fork`/`for` will deploy step services they didn't
  before) — the same category `try-catch-retry` introduced, but worth a human confirming given it's
  compounded across two container types in one change.
- **Alternative considered — walk `fork`/`for` bodies but reject any `call`/`run` found inside them
  at compile time (keep them in-process-only, like `switch`/`set`)**: rejected. Actively hostile to
  the parallel-fan-out use case that motivates `fork` in the first place (calling several services
  at once *is* the headline use case), and inconsistent with `try`'s precedent.
- **Alternative considered — leave `for.do` walking out of scope, ship only the `fork` branch**:
  considered and rejected in brainstorming (Q6) — the requirement brief explicitly asks for both,
  and it is the same one-line change twice, not a scope expansion.

### D8: `fork` gets its own `workflow-parallelism` capability spec

- **Choice**: capability additions live at
  `openspec/changes/fork-task/specs/workflow-parallelism/spec.md` as a new capability, not folded
  into `workflow-iteration` (`for-task`'s home) or `workflow-error-handling` (`try-catch-retry`'s).
- **Why**: `fork`'s vocabulary (`branches`/`compete`) and concurrency model are genuinely distinct
  from sequential iteration and from failure handling — the same reasoning `for-task`'s D7 used to
  justify its own separate capability rather than overloading an existing one.
- **Alternative considered — extend `workflow-iteration` to cover both sequential and parallel
  looping**: rejected, `for-task`'s D7 already anticipated and rejected this shape of merge.

## Risks / Trade-offs

- **[Risk] Losing `compete: true` branches run to completion in the background, consuming resources
  and publishing lifecycle events for work whose result is never used.** → Mitigation: documented
  as an explicit non-goal (D4); an observer sees a full execution trail for every branch including
  losers, which is at least legible even if wasteful. Follow-up (not this slice): an activity that
  best-effort-terminates losing branches once the winner is known, using the Dapr workflow
  management API.
- **[Risk] Each `fork` execution creates one additional Dapr workflow instance per branch, per
  invocation** — operational and observability surface grows (more instances in the Dapr Workflow
  dashboard/state store, more instance-lifecycle overhead) compared to every prior task type, which
  added no new instances. → Mitigation: accepted as the necessary cost of the only concurrency
  primitive the SDK offers (D1); flagged here so operators are not surprised by instance-count growth
  once `fork`-using definitions deploy.
- **[Risk] `fork` branches containing `call`/`run` now deploy new `StepService`s** (D7), same
  category of change `try-catch-retry` shipped for `try` in slice 2.1, but compounded across two
  container types (`fork`, `for`) in one change. → Mitigation: explicitly flagged for design review
  before `/opsx:apply` proceeds, per the requirement brief's own instruction.
- **[Trade-off] No `$context` threading across branches or back out of `fork`** (D3). → Accepted.
  No merge semantics exist to invent soundly; a definition author who needs cross-branch
  communication should use the branch outputs array (`compete: false`) instead.
- **[Trade-off] Branch dispatch reuses `InterpreterWorkflow.dispatch` via a widened visibility
  modifier rather than an extracted shared interface** (D2). → Accepted. Matches the existing
  package-private pattern `runTaskList` already established for the same reuse need; introducing an
  interface for a single reuse site would be premature abstraction.

## Migration Plan

1. `DefinitionLookup.search`: add the `ForkTask` branch (`forkTask.getFork().getBranches()`).
2. `WorkflowCompiler.walk()` and `collectTaskNames()`: add `ForkTask` and `ForTask` branches.
3. `ForkBranchInput` (record) + `ForkBranchWorkflow` (new `Workflow`): resolve the branch's task by
   name, dispatch it via `InterpreterWorkflow.dispatch` (widened to package-private), return its
   resulting `data` as `JsonNode`.
4. `InterpreterWorkflow`: `case ForkTask forkTask -> dispatchFork(...)` wired into both
   `dispatchBody`'s `StreamEx.of(...)` list and `dispatchConcreteTask`'s switch; `dispatchFork`
   implemented per D1/D3/D4/D5/D6; `taskTypeOf` gains a `fork` branch.
5. `WorkflowRuntimeBootstrap`: register `ForkBranchWorkflow` under its fixed name.
6. Unit + integration coverage (see tasks.md/plan.md): `DefinitionLookupTest`, `WorkflowCompilerTest`,
   `InterpreterWorkflowIntegrationTest` (join order, race semantics, branch failure propagation,
   branch containing a nested `try`/`for`, `$context` isolation).
7. `./mvnw verify` in `dws-orchestrator`; `./mvnw test` in `dws-controller`.
8. Roadmap update: `docs/roadmaps/openworkflow-features.md` §1 (`fork` row ❌ → ✅; "nested `do`" row
   ⚠️ → ✅, correcting the stale "only wired to try/catch.do" text), §4a (slice 2.4 row ❌ → ✅), §3
   Phase dependency graph (Phase 2 done, Phase 3 next), and `docs/roadmaps/README.md`'s summary
   line. Do NOT touch `openwiki/architecture/roadmap.md` — stale generated mirror (same caveat
   `for-task` logged).

**Rollback**: purely additive and gated on a task type that currently throws
`UnsupportedOperationException("... uses fork, which is recognised but not yet interpreted")` (once
the stub is added) or falls through to the unsupported-type `IllegalStateException` (today, since no
`ForkTask` arm exists yet). Reverting restores that failure; no definition without a `fork` task is
affected. Definitions with `call`/`run` under existing `for.do` bodies that this slice newly deploys
(D7) would need those step services garbage-collected on rollback — same GC-by-label mechanism that
already handles a version dropping a step, no new mechanism needed.

## Open Questions

**Blocking, needs human confirmation before `/opsx:apply`:**
- D7 (`WorkflowCompiler.walk()`/`collectTaskNames()` extended to both `ForkTask` and `ForTask`) —
  the requirement brief explicitly asked for this to be flagged for design review before
  implementation. The design's recommendation is to ship both (they're the same one-line shape
  change, and the roadmap's "nested `do`" row scores both), but this is the one decision in this
  slice with a real "ship less" alternative (fork only, leave `for.do`'s controller gap for a
  follow-up change) that a reviewer might prefer.

**Non-blocking:**
- Whether a future slice should add best-effort termination of losing `compete: true` branches
  (D4's deferred alternative) — explicitly deferred, not decided against; revisit if the background
  resource usage proves problematic in practice.
- The unrelated observation that `try-catch-retry`, `raise-task`, and `for-task` have still not
  been run through `/opsx:archive` is carried forward from the requester's brief for later
  attention and is not this slice's problem.
