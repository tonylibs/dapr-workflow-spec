## 1. `EvaluateForActivity`: evaluate `for.in` to a collection

- [x] 1.1 Add `EvaluateForRequest(String taskName, JsonNode data, Map<String, JsonNode>
      variables)` implementing `StepRequest` (mirroring `EvaluateSwitchRequest`/
      `EvaluateSetRequest`/`RaiseErrorRequest` shape)
- [x] 1.2 Add `EvaluateForActivity` (in-process, no I/O — parallel to `EvaluateSwitchActivity`)
      that resolves the FOR task via `DefinitionLookup.taskByName`, evaluates `forTask.getFor()
      .getIn()` through `JqEvaluator.evaluate(expr, data, variables)` (which handles both
      `${...}`-wrapped and bare jq expressions via `unwrap`), and returns the resulting
      `JsonNode`
- [x] 1.3 Reject a non-array result with `IllegalStateException` carrying the task name in the
      message so replay stays deterministic and the failure reaches the standard task-failure
      path
- [x] 1.4 Register `EvaluateForActivity` in `WorkflowRuntimeBootstrap`
- [x] 1.5 Unit tests in `EvaluateForActivityTest`: bare-jq `.pets` expression, `${...}`-wrapped
      expression, scope-variable binding (a `for.in` that references a `$var` bound in
      `variables`), and non-array rejection

## 2. `EvaluateWhileActivity`: per-iteration boolean evaluation of `while`

- [x] 2.1 Add `EvaluateWhileRequest(String taskName, JsonNode data, Map<String, JsonNode>
      variables)` implementing `StepRequest`
- [x] 2.2 Add `EvaluateWhileActivity` (in-process, no I/O — parallel to `EvaluateSwitchActivity`)
      that resolves the FOR task, reads `forTask.getWhile()`, and delegates to
      `JqEvaluator.evaluateBoolean(expr, data, variables)`, returning the resulting `boolean`
- [x] 2.3 Register `EvaluateWhileActivity` in `WorkflowRuntimeBootstrap`
- [x] 2.4 Unit tests in `EvaluateWhileActivityTest`: truthy jq result (`.count > 0`), falsy
      result (`null`, `false`, missing field), variable-binding case (`while` references
      `$item` bound by the caller), and `${...}`-wrapped form

## 3. `DefinitionLookup.search`: recurse into `ForTask.getDo()`

- [x] 3.1 Add an `else if (item.getTask().getForTask() != null)` branch to
      `DefinitionLookup.search` mirroring the existing `TryTask` branch — recurse into
      `forTask.getDo()`, returning the found `Task` if any
- [x] 3.2 Unit test in `DefinitionLookupTest` (create if absent, mirroring existing
      per-unit test style): a task name declared inside `for.do` resolves against
      `DefinitionLookup.taskByName`; a name declared inside a `for` nested inside a `try` also
      resolves

## 4. `InterpreterWorkflow`: `dispatchFor` and dispatch wiring

- [x] 4.1 Replace the `case ForTask _ -> throw new UnsupportedOperationException(...)` stub in
      `dispatchConcreteTask` with `case ForTask forTask -> dispatchFor(ctx, forTask, name, data,
      context, variables, depth, events, mapper);`
- [x] 4.2 Add a private `dispatchFor` helper mirroring `dispatchTry`'s method shape. It calls
      `EvaluateForActivity` once via `ctx.callActivity(...)` to resolve the collection; when
      the result is empty, returns `new Body(data, context, FlowOutcome.of(forTask.getThen()),
      ScopeEnd.FELL_THROUGH)` without running the body
- [x] 4.3 For each element in the collection: clone `variables` into a new `HashMap`, bind
      `$<each>` (`forTask.getFor().getEach()`, defaulting to `"item"` when null/blank) to the
      current element and `$<at>` (`forTask.getFor().getAt()`, defaulting to `"index"`) to the
      current zero-based index as an `IntNode`
- [x] 4.4 When `forTask.getWhile()` is non-null and non-blank, call `EvaluateWhileActivity` via
      `ctx.callActivity(...)` with the current data and the scoped variables; when the result is
      false, break out of the loop and return `new Body(data, context, FlowOutcome.of(forTask
      .getThen()), ScopeEnd.FELL_THROUGH)` with the data as of the previous iteration's output
- [x] 4.5 Call `runTaskList(ctx, forTask.getDo(), data, context, scoped, depth + 1, events,
      mapper)`; thread the returned `data` forward to the next iteration and update `context` in
      the same way; respect the returned `ScopeEnd` — an inner `ScopeEnd.END` short-circuits the
      loop and returns `new Body(recovered.data(), recovered.context(), then, ScopeEnd.END)` so
      the caller unwinds the whole instance; `ScopeEnd.EXIT` completes only that iteration's
      scope and the loop continues
- [x] 4.6 After the loop, return `new Body(data, context, FlowOutcome.of(forTask.getThen()),
      ScopeEnd.FELL_THROUGH)` with the final iteration's data
- [x] 4.7 Confirm no change is needed to `taskTypeOf` — the existing `for` branch (lines
      482–483) is already correct

## 5. Integration tests in `InterpreterWorkflowIntegrationTest`

- [x] 5.1 Add a stub for `EvaluateForActivity` and `EvaluateWhileActivity` in the test's
      `stubContext(ctx)` (mirroring the existing stubs for `EvaluateSetActivity`/
      `EvaluateSwitchActivity`/`RaiseErrorActivity`) so each activity's real logic runs directly
      against the seeded `WorkflowSupport`
- [x] 5.2 Basic iteration case: a `for` over a three-element array, `for.do` is a `set` task
      that appends each `$item` into a running list on the data document; assert the final
      data document contains the accumulated list in element order
- [x] 5.3 Index-variable case: a `for` whose body records `$index` per iteration; assert the
      recorded indices are `[0, 1, 2, ...]`
- [x] 5.4 `while` early-exit case: a `for` over a five-element array whose `while` becomes false
      at index 2; assert only two iterations run and the final data reflects that
- [x] 5.5 Empty-collection case: a `for` whose `for.in` evaluates to `[]`; assert the body runs
      zero times, the incoming data flows through unchanged, and the `for` task's own
      lifecycle event pair still fires exactly once
- [x] 5.6 `for` nested inside a `try` case: a task inside `for.do` raises an error caught by
      the enclosing `try`'s `catch.errors.with`; assert the failure is caught and the workflow
      continues past the `try` task
- [x] 5.7 Non-array `for.in` case: a `for` whose `for.in` evaluates to a scalar; assert the
      task fails with a message naming the task and the standard instance-failure path fires

## 6. Verification and roadmap update

- [x] 6.1 Run `./mvnw verify` in `dws-orchestrator/`; confirm green
- [x] 6.2 Run `./mvnw test` in `dws-controller/`; confirm green and that a definition
      containing a `for` task compiles with no additional `StepService`/`TopicBinding` — the
      compiled resource set is identical to the same definition without the `for` — consistent
      with `WorkflowCompiler.walk()`'s existing no-op treatment (no controller code changes are
      made in this slice)
- [x] 6.3 Update `docs/roadmaps/openworkflow-features.md` §1 (`for` row: `⚠️` → `✅`, description
      updated to reference `dispatchFor` and the new `workflow-iteration` capability) and §4a
      (slice 2.3 row: `❌ not started` → `✅ done — openspec/changes/for-task`); do NOT touch
      `openwiki/architecture/roadmap.md` — stale generated mirror
