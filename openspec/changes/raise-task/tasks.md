## 1. Error classification: raised-error marker and short-circuit

- [x] 1.1 Add `RaisedErrorException` (parallel to `StepInvocationException`) that folds a resolved
      five-field error object (`{type, status, instance, title, detail}`) into its message behind a
      new marker distinct from `STEP_MARKER`/`DATA_FLOW_MARKER`
- [x] 1.2 Add a short-circuit branch to `WorkflowErrors.classify()`/`of()`: when the new marker is
      present, parse the embedded object back out and return it unchanged — no `ErrorKind` is
      assigned, no status/type is re-derived from the message
- [x] 1.3 Unit tests in `WorkflowErrorsTest`: a raised error's `type`/`status`/`title`/`detail` round
      -trip unchanged through `classify()`/`of()`; the two existing markers' classification is
      unaffected

## 2. `RaiseErrorActivity`: pure evaluation of a raise task's configured error

- [ ] 2.1 Add `RaiseErrorActivity` (in-process, no I/O — parallel to `EvaluateSetActivity`) that
      resolves a `raise` task's `RaiseTaskConfiguration` via `DefinitionLookup.taskByName()` and
      returns the five-field error object; it does not throw `RaisedErrorException` itself
- [ ] 2.2 Resolve `RaiseTaskError`: an inline `Error` definition is used directly; a named reference
      is looked up in `Workflow.getUse().getErrors().getAdditionalProperties()`, failing with a
      message naming the missing error definition when unresolved
- [ ] 2.3 Evaluate `type`/`instance`/`title`/`detail`: a literal accessor value is used unchanged; an
      expression accessor value is evaluated via `JqEvaluator.evaluate(expr, data, variables)`
      unconditionally (no `${...}`-wrapper sniffing, unlike `set`)
- [ ] 2.4 Evaluate `status` as a literal `int`, used verbatim — no expression path exists for it in
      the pinned SDK
- [ ] 2.5 Resolve `instance`: use the declared value (literal or expression, per 2.3) when present;
      when absent, default to a JSON-Pointer-shaped reference to the raising task, matching
      `WorkflowErrors.build()`'s existing convention
- [ ] 2.6 Add `RaiseErrorRequest`/response records (mirroring `EvaluateSetRequest`'s shape: task
      name, data, scope variables)
- [ ] 2.7 Register `RaiseErrorActivity` in `WorkflowRuntimeBootstrap`
- [ ] 2.8 Unit tests in `RaiseErrorActivityTest`: inline literal fields, inline expression fields
      (reading task data), named `use.errors` reference applied identically to the same definition
      inline, unresolvable reference rejection, `instance` present vs. absent, and `status` used
      verbatim

## 3. Orchestrator: `raise` dispatch wiring

- [ ] 3.1 Add `task.getRaiseTask()` to `InterpreterWorkflow.dispatchBody`'s `StreamEx.of(...)`
      task-type list
- [ ] 3.2 Add a `case RaiseTask raiseTask ->` branch to `dispatchConcreteTask` that calls
      `RaiseErrorActivity`, then throws `RaisedErrorException` with the resolved error folded into
      its message
- [ ] 3.3 Add `else if (task.getRaiseTask() != null) return "raise";` to `taskTypeOf`
- [ ] 3.4 Confirm no change is needed to `runTaskList`'s failure handling or `dispatchTry`'s catch
      block — a raised error reaches both paths as an ordinary `RuntimeException`

## 4. Integration tests

- [ ] 4.1 Extend `InterpreterWorkflowIntegrationTest` with a `raise`-inside-`try` case: the raised
      error is caught by `catch.errors.with`/`catch.when` exactly like a real failure, and its
      `type`/`status`/`title`/`detail` reach `catch.do` unchanged under the bound error variable
- [ ] 4.2 Add a case asserting a `raise` inside `try` can trigger `catch`'s retry policy
      identically to a real failure
- [ ] 4.3 Add a case asserting a `raise` task outside any `try` fails the task and the workflow
      instance through the standard instance-failure path

## 5. Verification and controller confirmation

- [ ] 5.1 Run `./mvnw verify` in `dws-orchestrator/`; confirm green
- [ ] 5.2 Run `./mvnw test` in `dws-controller/`; confirm green and that the compiled resource set
      for a definition containing a `raise` task is unchanged (no `StepService`/`TopicBinding`
      emitted for it), consistent with `WorkflowCompiler.walk()`'s existing no-op treatment
- [ ] 5.3 Update `docs/roadmaps/openworkflow-features.md` §4a's Phase 2 slice table: mark the `raise`
      row (2.2) done, and update the task-type coverage table's `raise` row from ❌ to ✅
