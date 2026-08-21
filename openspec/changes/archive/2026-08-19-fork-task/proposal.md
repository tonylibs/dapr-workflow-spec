## Why

`dws-orchestrator` recognises the DSL 1.0 `fork` task and immediately throws
`UnsupportedOperationException`: no workflow can run branches in parallel today, so every
"call three services at once and join" or "race two providers, take whichever answers first" case
has to be modelled as a slow sequential chain instead. This is roadmap **Phase 2, slice 2.4**, the
last slice of Phase 2, unblocked by slices 2.1-2.3 (`try`/`catch`/`retry`, `raise`, `for` — all
merged), which built the scope-aware task-list runner and the `DefinitionLookup` recursion pattern
every earlier slice reused. It also closes `for-task`'s own explicitly-logged gap: `call`/`run`
nested under `for.do` still deploys nothing, because `WorkflowCompiler.walk()` was never extended
for it.

## What Changes

**`fork` task interpretation (`dws-orchestrator`)**
- From: `fork` is parsed, then rejected at dispatch with `UnsupportedOperationException`.
- To: `fork` runs each branch under `fork.branches` as its own child Dapr workflow instance,
  started concurrently via `ctx.callChildWorkflow` and combined with `ctx.allOf` (`compete: false`
  — wait for all branches, return their outputs as a JSON array in declared branch order) or
  `ctx.anyOf` (`compete: true` — take the first branch to settle, never await the rest).
- Reason: the DSL's only parallel-branch construct; the SDK's fan-out/fan-in primitives
  (`allOf`/`anyOf`) only combine independent `Task` handles, and a child workflow is the only way
  to turn a multi-step branch body into one such handle without rewriting the interpreter's
  sequential dispatch style.
- Impact: non-breaking — definitions without a `fork` task behave identically.

**Task lookup (`dws-orchestrator`)**
- From: `DefinitionLookup.search` recurses into `TryTask`'s and `ForTask`'s nested lists only.
- To: also recurses into `ForkTask.getFork().getBranches()` — the same `List<TaskItem>` shape the
  other two branches already use, so a branch's task resolves by name exactly like any other task.
- Impact: non-breaking — task names remain unique across the whole definition.

**Task compilation (`dws-controller`)**
- From: `WorkflowCompiler.walk()` and `collectTaskNames()` recurse into `TryTask` only; task lists
  nested under `for`/`fork` are explicitly commented as no-deploy, and duplicate names inside
  `for.do` go undetected.
- To: `walk()` gains a `ForkTask` branch (walks `fork.branches`, same shape as `try`) and a
  `ForTask` branch (walks `for.do`, closing `for-task`'s logged gap); `collectTaskNames()` gains
  the same two branches.
- Reason: without this, a `call`/`run` nested inside a `fork` branch or a `for` body compiles to no
  `StepService`, so the orchestrator would invoke a Dapr app-id that was never deployed — the exact
  failure mode `try-catch-retry` fixed for `try` in slice 2.1.
- Impact: **new deployed resources** for definitions that nest `call`/`run` inside `fork`/`for` —
  those step services did not exist before. Existing definitions deploy an unchanged set. **Flagged
  for design review**: this is the slice's main controller-side decision (see design.md).

Additions with no "before" state:
- `ForkBranchWorkflow`, a second `Workflow` registered under its own fixed name, whose `execute`
  resolves one branch's task by name (`DefinitionLookup`) and dispatches it via
  `InterpreterWorkflow.dispatch` (widened to package-private for reuse, no duplicated logic).
- A `dispatchFork` helper in `InterpreterWorkflow`, mirroring `dispatchTry`/`dispatchFor`'s shape:
  starts one child workflow per branch (deterministic instance id derived from the parent instance
  id, the `fork` task name, and the branch name), collects the `Task` handles, and joins/races them
  per `compete`.

## Capabilities

### New Capabilities
- `workflow-parallelism`: `dws-orchestrator`'s interpretation of `fork` — starting branches as
  concurrent child workflow instances, join (`compete: false`) and race (`compete: true`) semantics,
  branch-local `$context` (not threaded between branches or back out), and composition with the
  existing `try`/`catch`/`retry` failure path. Named separately from `workflow-iteration` (`for-task`,
  sequential) because `fork`'s vocabulary and concurrency model are a distinct concern, matching
  `for-task`'s own D7 rationale for not overloading one capability with both.

### Modified Capabilities
- `workflow-iteration` (`for-task`, `openspec/specs/workflow-iteration/spec.md` if archived, else
  `openspec/changes/for-task/specs/workflow-iteration/spec.md`): its "for tasks deploy no additional
  resources" requirement is superseded for the case where `for.do` nests `call`/`run` — those now
  deploy step services, closing the gap that requirement's scenario explicitly carved out.

## Impact

- **Components**: `dws-orchestrator` (primary — new task type, new child-workflow registration,
  `DefinitionLookup` recursion) and `dws-controller` (compile-path recursion for both `fork` and
  `for`, duplicate-name validation for both). Independent builds and CI gates are preserved.
- **`dws-orchestrator` code**: `workflow/InterpreterWorkflow.java` (`dispatchFork`, `dispatch`
  widened to package-private), `workflow/ForkBranchWorkflow.java` (new), `workflow/activity/
  DefinitionLookup.java` (one added recursion branch), `config/WorkflowRuntimeBootstrap.java`
  (register the new child workflow).
- **`dws-controller` code**: `compile/WorkflowCompiler.java` only — two added recursion branches
  (`walk()`, `collectTaskNames()`).
- **Deployed resources**: a definition nesting `call`/`run` inside `fork` branches or `for.do` now
  deploys those step services (it deployed none before). No new Dapr component type, no new image.
  Each `fork` task execution creates one additional Dapr workflow instance per branch per
  invocation (operational/observability impact — see design.md Risks).
- **Dependencies**: none added — `ctx.allOf`/`anyOf`/`callChildWorkflow` are already on the pinned
  `dapr-sdk-workflows:1.18.0` classpath.
- **Compatibility**: existing definitions are unaffected — none can contain a working `fork` task
  today (it throws at dispatch), and every other task type's behaviour is unchanged.
- **Non-goals**: true cancellation/termination of losing `compete: true` branches (they run to
  completion in the background; only their result is ignored) — Phase 3 fault-tolerance territory;
  `$context` merging across branches; task-level `if` conditions (pre-existing gap across all task
  types, not fork-specific); timeouts and RFC 7807 Problem Details — Phase 3, unchanged from every
  prior slice's non-goal list.
- **CI**: covered by the existing per-component path-filtered workflows; no CI changes.
