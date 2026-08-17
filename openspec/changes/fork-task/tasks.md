## 1. `DefinitionLookup`: recurse into `ForkTask.getFork().getBranches()`

- [ ] 1.1 Add an `else if (task.getForkTask() != null)` branch to `DefinitionLookup.search`
      mirroring the existing `TryTask`/`ForTask` branches — recurse into
      `forkTask.getFork().getBranches()`, returning the found `Task` if any
- [ ] 1.2 Unit test in `DefinitionLookupTest` (mirroring existing per-unit test style): a task name
      declared inside a `fork` branch resolves against `DefinitionLookup.taskByName`; a name
      declared inside a `fork` branch nested inside a `try`, and inside a `for`, also resolves

## 2. `WorkflowCompiler`: walk `fork` branches and `for.do`, extend duplicate-name detection

- [ ] 2.1 Add an `else if (task.getForkTask() != null)` branch to `WorkflowCompiler.walk()`
      mirroring the existing `TryTask` branch — recurse into
      `task.getForkTask().getFork().getBranches()`, emitting step services/topic bindings for
      `call`/`run`/`emit`/`listen` tasks nested in any branch exactly as at top level
- [ ] 2.2 Add an `else if (task.getForTask() != null)` branch to `WorkflowCompiler.walk()` —
      recurse into `task.getForTask().getDo()`, closing `for-task`'s logged gap
- [ ] 2.3 Update the stale comment (`// switch/set/wait/for/raise (and the task lists nested under
      for/fork) deploy nothing.`) to reflect that `for`/`fork` bodies are now walked
- [ ] 2.4 Add the same two branches (`ForkTask`, `ForTask`) to `collectTaskNames()` so a duplicate
      task name inside a `fork` branch or `for.do` is rejected at compile time
- [ ] 2.5 Unit tests in `WorkflowCompilerTest`: a `call` task inside a `fork` branch compiles to a
      `StepService`; a `call` task inside `for.do` compiles to a `StepService`; a `fork` task whose
      branches contain only in-process tasks compiles to the same plan as if `fork` were absent; a
      duplicate task name inside a `fork` branch is rejected; a duplicate task name inside `for.do`
      is rejected

## 3. `ForkBranchWorkflow`: one branch, dispatched as its own child workflow

- [ ] 3.1 Add `ForkBranchInput(String taskName, JsonNode data, JsonNode context, Map<String,
      JsonNode> variables, int depth)` (record, Jackson-serializable — same shape discipline as
      `EvaluateForRequest`/`RaiseErrorRequest`)
- [ ] 3.2 Widen `InterpreterWorkflow.dispatch(...)` from `private` to package-private (same pattern
      `runTaskList` already uses) so `ForkBranchWorkflow` can call it directly
- [ ] 3.3 Add `ForkBranchWorkflow implements Workflow` with a fixed registration name constant
      (e.g. `ForkBranchWorkflow.NAME = "dws-fork-branch"`, not derived from `document.name`, to
      avoid colliding with the top-level workflow's registration). `execute(ctx)` reads
      `ForkBranchInput` from `ctx.getInput(...)`, resolves the branch's `Task` via
      `DefinitionLookup.taskByName(input.taskName())`, calls `new
      InterpreterWorkflow().dispatch(ctx, task, input.taskName(), input.data(), input.context(),
      input.variables(), input.depth(), events, mapper)`, and completes with the resulting body
      data (`JsonNode`) via `ctx.complete(...)`
- [ ] 3.4 Confirm `AdminEventBuilder.forContext(ctx)` works correctly when `ctx` is a child
      workflow's context (lifecycle events published from inside a branch carry that branch's own
      instance id) — adjust only if the existing builder assumes the top-level instance shape

## 4. `InterpreterWorkflow`: `dispatchFork` and dispatch wiring

- [ ] 4.1 Add `ForkTask` to `dispatchBody`'s `StreamEx.of(...)` list (alongside `getForTask()`,
      `getTryTask()`, `getRaiseTask()`, etc.) so a `fork` task reaches `dispatchConcreteTask`
- [ ] 4.2 Add `case ForkTask forkTask -> dispatchFork(ctx, forkTask, name, data, context,
      variables, depth, events, mapper);` to `dispatchConcreteTask`'s switch
- [ ] 4.3 Add a private `dispatchFork` helper mirroring `dispatchTry`/`dispatchFor`'s method shape.
      Validate `forkTask.getFork() != null && !forkTask.getFork().getBranches().isEmpty()`
      (fail fast with the task name in the message otherwise). For each branch `TaskItem` in
      declared order: build a deterministic instance id (e.g. `ctx.getInstanceId() + "/" + name +
      "/" + branch.getName()`), start `ctx.callChildWorkflow(ForkBranchWorkflow.NAME, new
      ForkBranchInput(branch.getName(), data, context, variables, depth + 1), instanceId,
      JsonNode.class)` — do **not** call `.await()` — and collect the `Task<JsonNode>` handles in
      declared order
- [ ] 4.4 `compete: false` (or `fork.isCompete() == false`): `ctx.allOf(handles).await()` →
      `List<JsonNode>`; wrap into a JSON array (`mapper.createArrayNode()`, preserving list order)
      and return `new Body(array, context, FlowOutcome.of(forkTask.getThen()),
      ScopeEnd.FELL_THROUGH)` — `context` is the same context that entered `fork`, unchanged
- [ ] 4.5 `compete: true`: build a `List<Task<?>>` wildcard copy of the handles, call
      `ctx.anyOf(...)`, `.await()` the result to get the winning `Task<?>`, `.await()` that task to
      get its `JsonNode` value (propagating its failure if it failed), and return `new Body(winner,
      context, FlowOutcome.of(forkTask.getThen()), ScopeEnd.FELL_THROUGH)`. Losing handles are
      never awaited
- [ ] 4.6 Add a `fork` branch to `taskTypeOf` (`task.getForkTask() != null` → `"fork"`)

## 5. Bootstrap registration

- [ ] 5.1 Register `ForkBranchWorkflow` in `WorkflowRuntimeBootstrap.startRuntime()`
      (`builder.registerWorkflow(ForkBranchWorkflow.NAME, ForkBranchWorkflow.class)`)

## 6. Integration tests in `InterpreterWorkflowIntegrationTest`

- [ ] 6.1 Add whatever stub/support the test harness needs for `ctx.callChildWorkflow`/`allOf`/
      `anyOf` (check how the existing test drives `WorkflowContext` — extend the same mocking
      approach `EvaluateForActivity`/`RaiseErrorActivity` stubs already use, adapted for a child
      workflow call rather than an activity call)
- [ ] 6.2 `compete: false` join case: a `fork` task with three branches, each a `set` task
      producing a distinct value; assert the output array matches declared branch order regardless
      of any simulated completion-order variation
- [ ] 6.3 `compete: true` race case: a `fork` task with two branches; assert the `fork` task
      completes with the first-settled branch's data and the workflow does not block on the other
- [ ] 6.4 Branch failure case (`compete: false`): one branch's task fails; assert the `fork` task
      fails with `CompositeTaskFailedException`-shaped detail and, when nested in a `try`, is
      caught by `catch.errors.with`
- [ ] 6.5 Winning-branch failure case (`compete: true`): the first-settled branch fails; assert the
      failure surfaces as the `fork` task's own failure
- [ ] 6.6 Nested-container branch case: a branch whose task is a `try` containing multiple steps
      with a retry; assert the retry runs correctly inside the branch
- [ ] 6.7 `$context` isolation case: a branch performs `export.as`; assert neither a sibling branch
      nor the task after `fork` observes that write
- [ ] 6.8 `fork` nested inside a `try` case: a failure inside a branch is caught by the enclosing
      `try`'s `catch.errors.with`; assert the workflow continues past the `try` task

## 7. Verification and roadmap update

- [ ] 7.1 Run `./mvnw verify` in `dws-orchestrator/`; confirm green
- [ ] 7.2 Run `./mvnw test` in `dws-controller/`; confirm green and that a `fork`/`for` task
      nesting only in-process tasks compiles to the same resource set as if absent, while nesting
      `call`/`run` inside either now deploys the expected `StepService`s
- [ ] 7.3 Update `docs/roadmaps/openworkflow-features.md` §1 (`fork` row: `❌` → `✅`; "nested `do`"
      row: `⚠️` → `✅`, correcting the stale "only wired to `try`/`catch.do`" text), §4a (slice 2.4
      row: `❌ not started` → `✅ done — openspec/changes/fork-task`), §3 Phase dependency graph
      (mark Phase 2 done, Phase 3 next); update `docs/roadmaps/README.md`'s summary line to match.
      Do NOT touch `openwiki/architecture/roadmap.md` — stale generated mirror
