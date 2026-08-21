<!--
Raw capture of superpowers:brainstorming output.
-->

## Background

Roadmap Phase 2 slice 2.4 (`docs/roadmaps/openworkflow-features.md` §4a): `fork` (parallel
branches) + generalizing nested `do` to any container task type. Flagged as "hardest slice: needs
a concurrent execution model, not just another `runTaskList` caller." Unblocked by slices 2.1-2.3
(`try-catch-retry`, `raise-task`, `for-task`, all merged), which built the scope-aware task-list
runner (`runTaskList`), the depth guard, and the `DefinitionLookup.search` recursion pattern
(one branch per container task type).

Requester's brief supplied most of the shape up front (fields, join/race semantics, the
controller-side open question to flag). Rather than a multi-turn back-and-forth, this session
verified every load-bearing claim against the actual pinned SDK jars (`javap`) and the current
source, then resolved the open questions the brief flagged plus one it didn't — recorded below as
a decision chain.

## Decision chain

### Q1: What does `ForkTaskConfiguration.getBranches()` actually return?

Brief said `branches: map[string,task]`. Verified via `javap -classpath
.../serverlessworkflow-types-7.26.0.Final.jar io.serverlessworkflow.api.types.ForkTask
io.serverlessworkflow.api.types.ForkTaskConfiguration`:

```
public class ForkTaskConfiguration {
  public ForkTaskConfiguration(List<TaskItem>);
  public List<TaskItem> getBranches();
  public boolean isCompete();
}
```

**Finding**: `branches` is `List<TaskItem>` — the *exact same shape* as `do`, `try.try`, and
`try.catch.do`/`for.do` already use. Each branch is one named `TaskItem` (name + a single `Task`),
not a raw map of task bodies. This is not a divergence from the brief's intent (each branch still
has a name and a body) but it changes the mechanics significantly: `branches` already fits the
`List<TaskItem>` signature every existing recursive helper (`DefinitionLookup.search`,
`WorkflowCompiler.walk`, `runTaskList`) expects. No new container shape needs modeling.

### Q2: Can the existing "await eagerly" dispatch pipeline run branches concurrently?

Checked `io.dapr.durabletask.Task<V>` (the handle type every `ctx.callActivity`/`createTimer`/
`waitForExternalEvent` returns): `await()`, `thenApply(Function<V,U>)`, `thenAccept(Consumer<V>)`.
No `thenCompose`/flatMap. `thenApply`'s function returns a *plain value*, not another `Task`, so
you cannot chain a second `ctx.callActivity(...)` off the result without calling `.await()` first
— the SDK offers no monadic bind. That means `InterpreterWorkflow`'s existing dispatch methods
(`dispatch`, `dispatchBody`, `runTaskList`'s for-loop, `dispatchTry`'s retry loop) cannot be
mechanically converted into "build N unawaited `Task<Body>` chains, then combine" for anything
beyond a single leaf activity call — a branch that is itself a `try` or `for` (multiple sequential
`ctx.callActivity` calls) has no way to become one non-blocking `Task` under this API.

**Rejected approach**: refactor the whole interpreter to a fully async/monadic dispatch style so
every branch produces one unawaited `Task<Body>` combinable via `allOf`/`anyOf`. The SDK doesn't
support the chaining this needs; it would also touch every task type's dispatch code for a
generalization only `fork` needs, and would risk behavioral drift between "await-eagerly" and
"async-chained" paths for the other nine task types.

### Q3: What primitive *does* let two independent multi-step task sequences run concurrently?

`WorkflowContext.allOf(List<Task<V>>)` and `anyOf(List<Task<?>>)` exist and are the SDK's
documented fan-out/fan-in primitives — confirmed via `javap` on
`io.dapr.workflows.WorkflowContext`. Each element of the list must already be *one* `Task` handle
from the underlying context. `WorkflowContext.callChildWorkflow(String name, Object input, String
instanceId, Class<V> resultType)` also exists and returns exactly one `Task<V>` — and a child
workflow is a **full, independent, deterministic workflow instance** with its own
`WorkflowContext`, capable of running an arbitrary nested dispatch (including its own `try`/`for`/
nested `fork`) entirely synchronously *inside that instance*, while still surfacing as a single
`Task<V>` to the parent.

**Decision**: each fork branch runs as its own **child workflow instance**. `dispatchFork` starts
one child workflow per branch via `callChildWorkflow` (not awaited), collects the `Task<JsonNode>`
handles, and combines them with `allOf` (`compete: false`) or `anyOf` (`compete: true`). This
reuses 100% of the existing sequential dispatch machinery unchanged inside each branch (a branch
being a `try` works exactly like a top-level `try` today) — concurrency is achieved by running N
separate deterministic instances side by side, not by rewriting how any one instance dispatches.

### Q4: How does the branch's child workflow reach the same dispatch code without duplicating it?

`InterpreterWorkflow.dispatch(...)` is currently `private`; `runTaskList(...)` is already
package-private (for `InterpreterWorkflowIntegrationTest`'s stub-based tests). Widening `dispatch`
to package-private lets a new `ForkBranchWorkflow` (same package, one `Workflow` implementation
registered under its own fixed name) call `new InterpreterWorkflow().dispatch(...)` directly for
the one `TaskItem` its branch names — full data-flow pipeline, nested control flow, everything,
with zero duplicated logic. Because branch task names are already globally unique (the existing
invariant every other slice relies on), the branch's input to its child workflow needs only the
branch's task name plus data/context/variables/depth — `DefinitionLookup.taskByName` resolves the
rest, the same way every in-process activity already resolves its target.

**Rejected approach**: a second copy of the dispatch switch statement inside `ForkBranchWorkflow`.
Rejected — two copies of the same logic drift out of sync (a bug fixed in one dispatch path and
not the other), and there's no need: the reuse above needs one visibility change and no
duplication.

### Q5: `DefinitionLookup.search` — what does the roadmap doc claim, and is it still true?

The requirement brief and `docs/roadmaps/openworkflow-features.md`'s "nested `do`" row both say
"only wired to `try`/`catch.do`". Reading the actual `DefinitionLookup.java` shows this is **stale
as of `for-task`**: it already has a `ForTask` branch (`search(forTask.getDo(), taskName)`),
shipped in slice 2.3. The roadmap's summary row was never updated after `for-task` merged.

**Finding, not a decision**: on the interpreter (orchestrator) side, `for.do` task-name resolution
is already generalized. What slice 2.4 actually still needs on `DefinitionLookup` is exactly one
new branch — `ForkTask` → recurse into `forkTask.getFork().getBranches()` — following the same
one-branch-per-container discipline `for-task`'s D6 established. `docs/roadmaps/
openworkflow-features.md`'s "nested `do`" row and its own text need correcting as part of this
slice's roadmap update, not left to propagate the stale claim.

### Q6: The controller-side open question — does `WorkflowCompiler.walk()` need to change, and how?

Read `WorkflowCompiler.walk()` and `collectTaskNames()`. Both currently have exactly one
container-recursion branch: `TryTask` (`try`/`catch.do`). Neither has a `ForTask` branch (`for.do`)
or would have a `ForkTask` branch. This is the *same* `List<TaskItem>` shape by construction (Q1),
so extending `walk()` to `fork` branches is mechanically identical to the existing `TryTask`
extension — no new container shape to design for. The real question is scope: fix `fork` only, or
also close `for-task`'s explicitly-logged gap ("nesting `call`/`run` under `for.do` remains a
slice 2.4 problem", `for-task/proposal.md` line 44) at the same time?

**Decision**: do both. The requirement brief's scope point 2 explicitly asks for nested `do` to
work "inside fork branches AND `for.do`", and the roadmap's "nested `do`" row is scored against
both. Extending `walk()` with a `ForTask` branch alongside the new `ForkTask` branch is the same
one-line-shape change twice, not a scope expansion — it's finishing the row `for-task` opened. Also
extend `collectTaskNames` (duplicate-name validation) with the same two branches: it currently only
recurses into `TryTask`, so a duplicate name inside `for.do` is *undetected today* — a latent gap
predating this slice, worth closing here since fork branches make the same soundness property
(`DefinitionLookup`'s "first match is the only match" comment) load-bearing for a second container
type in the same change.

**Flagged for design review** (per the requirement brief's explicit instruction): walking fork
branches means a definition nesting `call`/`run` inside a `fork` branch will deploy real
`StepService`s it didn't deploy before — this is the same category of "new deployed resources"
`try-catch-retry` already introduced for `try`, not a new category of change, but it is a real
behavior change worth a human confirming before `/opsx:apply` proceeds.

### Q7: `compete: false` (join) — what does the combined output look like, and does `$context` thread?

Brief: "wait for all branches, return array of outputs in branch order." `ctx.allOf(List<Task<V>>)`
returns `List<V>` preserving input-list order (not completion order) — matches directly; branches
are iterated off `forkTask.getFork().getBranches()` in declared order, so the array is naturally in
branch-declaration order.

`$context`: each branch runs as an independent child-workflow instance starting from a *snapshot*
of the incoming context. There is no sound way to merge N independently-mutated context documents
back into one (no merge semantics defined anywhere in the DSL or this codebase), and `for`'s own
design doc (D4) already earmarked "each iteration starts from the same immutable input, collecting
an array of per-iteration outputs" as `fork`'s semantics, not `for`'s. Decision: `fork` does not
thread `$context` between branches or back out — the context leaving the `fork` task is the same
context that entered it, unchanged. A branch's own `export.as` is visible only within that branch's
own child-workflow instance and is discarded when the branch completes. Symmetrical with how
`data` for a losing/joined branch is fully independent, not threaded.

### Q8: `compete: true` (race) — what does "abandon the rest" mean given the SDK's actual API?

`anyOf(List<Task<?>>)` returns `Task<Task<?>>` — the first task to *settle* (success or failure),
not necessarily succeed. Checked `Task<V>`'s public surface again: no `cancel()` method exposed,
and `WorkflowContext` exposes no "terminate child workflow" call either (termination-by-instance-id
is a Dapr Workflow *management* API concern, not something the workflow's own deterministic replay
code can safely invoke inline — doing so would be a side effect outside activity/timer/event
primitives, breaking replay determinism unless routed through yet another activity).

**Decision**: "abandon" means exactly what the SDK gives for free — the parent never calls
`.await()` on the losing branches' `Task` handles, so their eventual results (success or failure)
are never observed or surfaced. It does **not** mean the losing branches' child-workflow instances
are terminated or their in-flight activities cancelled; they run to completion in the background,
publishing their own `taskStarted`/`taskCompleted`/`taskFailed` lifecycle events same as any other
branch (an observer sees a full branch execution trail for every branch, winner or not). True
cancellation of losing branches is out of scope for this slice — flagged as an explicit
Risk/non-goal, the same posture `try-catch-retry` took on `retry.limit.attempt.duration` (deferred
to Phase 3's fault-tolerance work rather than invented ad hoc here).

### Q9: Does a branch's internal `end`/`exit` flow directive reach past the branch?

A branch's single task can itself be `try`/`for`, whose body can use `end`/`exit`. Since the branch
runs as its own child-workflow instance, `ScopeEnd.END`/`EXIT` inside it only ever unwinds that
instance's own scope stack — there is no mechanism for a child workflow to signal "terminate my
parent" today, and the DSL doesn't define what `end` means from inside a parallel branch either.
Decision: a branch's `ScopeEnd` (however it finishes) only ever means "this branch is done, return
its data" — it does not propagate to end the enclosing `fork` task's siblings or the outer
workflow instance. Only the branch's final `data` crosses the child-workflow boundary.

### Q10: Failure propagation — does `fork` need new error-handling code?

`CompositeTaskFailedException` (thrown by `allOf` when any branch fails) `extends RuntimeException`
— it already satisfies `runTaskList`'s existing `catch (RuntimeException e)` wrapping and
`dispatchTry`'s existing `catch (RuntimeException failure)` catch-and-classify path with zero new
code. For `compete: true`, calling `.await()` on the winning `Task` a second time throws if the
winner itself failed — same existing `RuntimeException` propagation, no special-casing. `fork`
composes into the existing `try`/`catch`/`retry` machinery for free.

## Converged shape

- `dws-orchestrator`: `case ForkTask forkTask -> dispatchFor...` — no, `dispatchFork`, mirroring
  `dispatchTry`/`dispatchFor`'s existing shape. Starts one child workflow (`ForkBranchWorkflow`,
  registered under a fixed name, reusing `InterpreterWorkflow.dispatch` package-privately) per
  branch via `ctx.callChildWorkflow(..., instanceId, JsonNode.class)`, combines with
  `ctx.allOf`/`ctx.anyOf` per `compete`. `DefinitionLookup.search` gains a `ForkTask` branch.
- `dws-controller`: `WorkflowCompiler.walk()` gains `ForkTask` (branches) and `ForTask` (`for.do`)
  branches — same `List<TaskItem>` recursion the `TryTask` branch already does. `collectTaskNames`
  gains the same two branches for duplicate-name detection.
- New capability spec: `workflow-parallelism` (parallel to `workflow-iteration`'s precedent from
  `for-task` — a distinct vocabulary/concern from iteration and error-handling).
- Explicit non-goals carried into design/proposal: no true cancellation of losing `compete: true`
  branches; no `$context` merge/threading across branches; `if` task-level conditions (pre-existing
  gap, all task types); timeouts/Problem Details (Phase 3, unchanged from every prior slice's
  non-goal list).
