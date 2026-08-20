## 1. Error catalogue reconciliation (`dws-orchestrator`)

- [ ] 1.1 In `ErrorKind.java`, change `TYPE_PREFIX` to
      `https://serverlessworkflow.io/spec/1.0.0/errors/` and add `AUTHORIZATION("authorization",
      403, "Authorization error")`, `EXPRESSION("expression", 400, "Expression error")`, and
      `TIMEOUT("timeout", 408, "Timeout error")`, keeping `VALIDATION`/`COMMUNICATION`/`RUNTIME`
      with their existing slugs, statuses, and titles (only the prefix and enum membership change
      for them).
- [ ] 1.2 Update `ErrorKind`'s class-level Javadoc: it no longer describes "three is deliberately
      the whole list" — describe the standard catalogue, that `RUNTIME` is this runtime's own
      addition under the same namespace, and that `AUTHORIZATION`/`EXPRESSION` are not yet
      classified into by `WorkflowErrors.classify()`.
- [ ] 1.3 Add a `WorkflowErrors.classify()` check for a timeout-marker failure message, ordered
      before the existing `DATA_FLOW_MARKER`/`CONFIG_MARKER`/`STEP_MARKER` checks, returning
      `ErrorKind.TIMEOUT`. Define the marker constant alongside the existing ones
      (`STEP_MARKER`/`DATA_FLOW_MARKER`/etc.).
- [ ] 1.4 Update `WorkflowErrors`'s class-level Javadoc to drop the "Phase 3 is out of scope" note
      now that the catalogue and prefix are reconciled.
- [ ] 1.5 Unit tests in `ErrorKindTest`/`WorkflowErrorsTest` (or existing equivalents): each kind's
      `typeUri()` starts with the new prefix and ends with its slug; `classify()` returns `TIMEOUT`
      for a timeout-marker message and is unaffected for every existing marker case (data-flow,
      config, step-status, upstream, unrecognized/runtime).

## 2. Shared duration helper

- [ ] 2.1 Move `durationOf(TimeoutAfter)` onto `WorkflowSupport` as a single static method; update
      `CatchPolicy` and `InterpreterWorkflow` to call it and delete both local copies.
- [ ] 2.2 Unit test (or confirm existing coverage still exercises it via both call sites) for the
      inline-duration, expression/literal-duration, and `null` cases.

## 3. `ScopeRunnerWorkflow` (new child workflow for list-shaped guarded execution)

- [ ] 3.1 Add `ScopeRunnerInput` record: `(List<TaskItem> items, JsonNode data, JsonNode context,
      Map<String, JsonNode> variables, int depth)`.
- [ ] 3.2 Add `ScopeRunnerWorkflow` (registered under a stable name distinct from
      `ForkBranchWorkflow.NAME`, e.g. `dws-scope-runner`): reads `ScopeRunnerInput`, calls
      `InterpreterWorkflow.runTaskList` with a fresh `AdminEventBuilder.forContext(ctx)`, completes
      with the resulting `ScopeResult`. Mirror `ForkBranchWorkflow`'s shape (extract the body into a
      directly-testable method the same way).
- [ ] 3.3 Register `ScopeRunnerWorkflow` alongside `ForkBranchWorkflow` wherever workflow types are
      registered with the Dapr Workflow runtime (find and mirror `ForkBranchWorkflow`'s
      registration site).
- [ ] 3.4 Unit test driving `ScopeRunnerWorkflow.execute` against a mocked `WorkflowContext`,
      matching the existing `ForkBranchWorkflow`/`InterpreterWorkflow` test style.

## 4. Task-level timeout (`dispatch`, reusing `ForkBranchWorkflow`)

- [ ] 4.1 In `InterpreterWorkflow.dispatch`, when `DataFlowPipeline.baseOf(task).getTimeout()` is
      present, resolve it (inline `Timeout` or `use.timeouts` reference — mirror
      `CatchPolicy.resolvePolicy`'s inline-or-named pattern for the reference case) to a
      `TimeoutAfter`/`Duration` via the shared `durationOf`.
- [ ] 4.2 When a timeout is resolved, start a `ForkBranchWorkflow` child instance for this task
      (same `ForkBranchInput` shape already used by `dispatchFork`) instead of calling
      `dispatchBody` in-process, and race it against `ctx.createTimer(duration)` via `ctx.anyOf`.
      On a task-body win, proceed with its result exactly as the in-process path does today. On a
      timer win, throw `IllegalStateException("task '" + name + "' timed out after " + duration)`.
- [ ] 4.3 When no timeout is present, `dispatch` calls `dispatchBody` in-process exactly as before
      this change (no behavior change on the common path).
- [ ] 4.4 Integration test: a task declaring `timeout` that does not complete in time fails with a
      `timeout`/408 error object; the same task completing in time is unaffected. Cover both
      inline `timeout` and a `use.timeouts` reference.
- [ ] 4.5 Integration test: a `timeout`-declaring task nested inside `try` is caught by
      `catch.errors.with.type` filtering on the new timeout type URI.

## 5. Workflow-level timeout (`execute`)

- [ ] 5.1 In `InterpreterWorkflow.execute`, when `WorkflowSupport.definition().getTimeout()` is
      present, resolve it the same way as task-level timeout (inline or `use.timeouts` reference)
      to a `Duration`, then a `ZonedDateTime` deadline off `ctx.getCurrentInstant()`.
- [ ] 5.2 When a workflow-level timeout is resolved, start a `ScopeRunnerWorkflow` child instance
      over the top-level `do` list instead of calling `runTaskList` in-process, and race it against
      `ctx.createTimer(deadline)` via `ctx.anyOf`. On the scope winning, `ctx.complete` with its
      result exactly as today. On the timer winning, throw
      `IllegalStateException("workflow timed out after " + duration)` so it flows through the
      existing `catch (RuntimeException e)` block (`instanceFailed` publish + rethrow), unchanged.
- [ ] 5.3 When no workflow-level timeout is declared, `execute` calls `runTaskList` in-process
      exactly as before this change.
- [ ] 5.4 Integration test: a workflow document declaring `timeout` whose instance does not
      complete in time fails the instance with a `timeout`/408 error object in the failure detail;
      the same instance completing in time is unaffected.
- [ ] 5.5 Integration test: a top-level `try` task cannot catch the instance-wide deadline — the
      instance still fails when the workflow-level timeout elapses while that `try` is running.

## 6. Retry per-attempt timeout (`dispatchTry`, `CatchPolicy`)

- [ ] 6.1 Delete `CatchPolicy.rejectUnsupported` and its call site in `retryDelay`.
- [ ] 6.2 In `InterpreterWorkflow.dispatchTry`, resolve the retry policy's `limit.attempt.duration`
      (via the shared `durationOf`) once per attempt, alongside the existing `firstFailureMillis`
      bookkeeping.
- [ ] 6.3 When a per-attempt duration is resolved for the current attempt, run that attempt's
      `runTaskList(ctx, tryTask.getTry(), ...)` via a `ScopeRunnerWorkflow` child instance raced
      against `ctx.createTimer(duration)` instead of calling `runTaskList` in-process. On the
      timer winning, treat the attempt as failed with a message
      `"task '" + name + "' timed out after " + duration` and feed it into the same
      `CatchDecisionActivity` call the surrounding `catch` block already makes for any other
      attempt failure (do not special-case the retry/backoff/limit logic beyond that).
- [ ] 6.4 When the policy declares no `limit.attempt.duration`, `dispatchTry` calls `runTaskList`
      in-process exactly as before this change.
- [ ] 6.5 Unit test in `CatchPolicyTest`: `limit.attempt.duration` no longer throws from
      `rejectUnsupported`/`retryDelay`.
- [ ] 6.6 Integration test: an attempt exceeding `limit.attempt.duration` counts toward
      `limit.attempt.count` and `limit.duration` identically to an ordinary attempt failure, and
      the retry/backoff/recovery path proceeds normally after it.

## 7. Verification

- [ ] 7.1 `cd dws-orchestrator && ./mvnw verify` passes.
- [ ] 7.2 Confirm no `dws-controller` change was needed: re-check `WorkflowCompiler.walk()` against
      the final diff (per the proposal's Impact section) and note the confirmation in the PR
      description rather than touching `dws-controller`.
- [ ] 7.3 Grep the repo for the old invented type-URI prefix
      (`open-workflow-specification.org/dsl/errors/types/`) to confirm nothing else (docs, other
      tests, `docs/roadmaps/openworkflow-features.md`'s Phase 3 row) still references it after this
      change, and update any that do.
