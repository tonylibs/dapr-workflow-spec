## 1. Error catalogue reconciliation (`dws-orchestrator`)

- [x] 1.1 In `ErrorKind.java`, change `TYPE_PREFIX` to
      `https://serverlessworkflow.io/spec/1.0.0/errors/` and add `AUTHORIZATION("authorization",
      403, "Authorization error")`, `EXPRESSION("expression", 400, "Expression error")`, and
      `TIMEOUT("timeout", 408, "Timeout error")`, keeping `VALIDATION`/`COMMUNICATION`/`RUNTIME`
      with their existing slugs, statuses, and titles (only the prefix and enum membership change
      for them).
- [x] 1.2 Update `ErrorKind`'s class-level Javadoc: it no longer describes "three is deliberately
      the whole list" — describe the standard catalogue, that `RUNTIME` is this runtime's own
      addition under the same namespace, and that `AUTHORIZATION`/`EXPRESSION` are not yet
      classified into by `WorkflowErrors.classify()`.
- [x] 1.3 Add a `WorkflowErrors.classify()` check for a timeout-marker failure message, ordered
      before the existing `DATA_FLOW_MARKER`/`CONFIG_MARKER`/`STEP_MARKER` checks, returning
      `ErrorKind.TIMEOUT`. Define the marker constant alongside the existing ones
      (`STEP_MARKER`/`DATA_FLOW_MARKER`/etc.).
- [x] 1.4 Update `WorkflowErrors`'s class-level Javadoc to drop the "Phase 3 is out of scope" note
      now that the catalogue and prefix are reconciled.
- [x] 1.5 Unit tests in `ErrorKindTest`/`WorkflowErrorsTest` (or existing equivalents): each kind's
      `typeUri()` starts with the new prefix and ends with its slug; `classify()` returns `TIMEOUT`
      for a timeout-marker message and is unaffected for every existing marker case (data-flow,
      config, step-status, upstream, unrecognized/runtime).

## 2. Shared duration helper

- [x] 2.1 Move `durationOf(TimeoutAfter)` onto `WorkflowSupport` as a single static method; update
      `CatchPolicy` and `InterpreterWorkflow` to call it and delete both local copies.
- [x] 2.2 Unit test (or confirm existing coverage still exercises it via both call sites) for the
      inline-duration, expression/literal-duration, and `null` cases.

## 3. `ScopeRunnerWorkflow` (new child workflow for list-shaped guarded execution)

- [x] 3.1 Add `ScopeRunnerInput` record: `(List<TaskItem> items, JsonNode data, JsonNode context,
      Map<String, JsonNode> variables, int depth)`.
- [x] 3.2 Add `ScopeRunnerWorkflow` (registered under a stable name distinct from
      `ForkBranchWorkflow.NAME`, e.g. `dws-scope-runner`): reads `ScopeRunnerInput`, calls
      `InterpreterWorkflow.runTaskList` with a fresh `AdminEventBuilder.forContext(ctx)`, completes
      with the resulting `ScopeResult`. Mirror `ForkBranchWorkflow`'s shape (extract the body into a
      directly-testable method the same way).
- [x] 3.3 Register `ScopeRunnerWorkflow` alongside `ForkBranchWorkflow` wherever workflow types are
      registered with the Dapr Workflow runtime (find and mirror `ForkBranchWorkflow`'s
      registration site).
- [x] 3.4 Unit test driving `ScopeRunnerWorkflow.execute` against a mocked `WorkflowContext`,
      matching the existing `ForkBranchWorkflow`/`InterpreterWorkflow` test style.

## 4. Task-level timeout (`dispatch`, reusing `ForkBranchWorkflow`)

- [x] 4.1 In `InterpreterWorkflow.dispatch`, when `DataFlowPipeline.baseOf(task).getTimeout()` is
      present, resolve it (inline `Timeout` or `use.timeouts` reference — mirror
      `CatchPolicy.resolvePolicy`'s inline-or-named pattern for the reference case) to a
      `TimeoutAfter`/`Duration` via the shared `durationOf`.
      Implementation note: the check ended up living in a new `dispatchWithTimeout` wrapper called
      from `runTaskList`'s one dispatch call site, not inside `dispatch` itself — putting it inside
      `dispatch` would recurse forever, since the reused `ForkBranchWorkflow` child instance calls
      `dispatch` again for the same task. See design.md's Decision #2 caveat; `dispatch` itself is
      unchanged and timeout-agnostic.
- [x] 4.2 When a timeout is resolved, start a `ForkBranchWorkflow` child instance for this task
      (same `ForkBranchInput` shape already used by `dispatchFork`) instead of calling
      `dispatchBody` in-process, and race it against `ctx.createTimer(duration)` via `ctx.anyOf`.
      On a task-body win, proceed with its result exactly as the in-process path does today. On a
      timer win, throw `IllegalStateException("task '" + name + "' timed out after " + duration)`.
      Implementation note: `ForkBranchWorkflow` now completes with the full `Dispatch` record
      (data/context/then/end), not just `.data()`, because the timeout wrapper needs the task's own
      flow directive to keep advancing correctly on a non-timeout completion — a fork branch's
      caller (`dispatchFork`) was updated to read `.data()` off the richer result, with no change in
      its own observable behavior.
- [x] 4.3 When no timeout is present, `dispatch` calls `dispatchBody` in-process exactly as before
      this change (no behavior change on the common path).
- [x] 4.4 Integration test: a task declaring `timeout` that does not complete in time fails with a
      `timeout`/408 error object; the same task completing in time is unaffected. Cover both
      inline `timeout` and a `use.timeouts` reference.
- [x] 4.5 Integration test: a `timeout`-declaring task nested inside `try` is caught by
      `catch.errors.with.type` filtering on the new timeout type URI.

## 5. Workflow-level timeout (`execute`)

- [x] 5.1 In `InterpreterWorkflow.execute`, when `WorkflowSupport.definition().getTimeout()` is
      present, resolve it the same way as task-level timeout (inline or `use.timeouts` reference)
      to a `Duration`, then a `ZonedDateTime` deadline off `ctx.getCurrentInstant()`.
- [x] 5.2 When a workflow-level timeout is resolved, start a `ScopeRunnerWorkflow` child instance
      over the top-level `do` list instead of calling `runTaskList` in-process, and race it against
      `ctx.createTimer(deadline)` via `ctx.anyOf`. On the scope winning, `ctx.complete` with its
      result exactly as today. On the timer winning, throw
      `IllegalStateException("workflow timed out after " + duration)` so it flows through the
      existing `catch (RuntimeException e)` block (`instanceFailed` publish + rethrow), unchanged.
- [x] 5.3 When no workflow-level timeout is declared, `execute` calls `runTaskList` in-process
      exactly as before this change.
- [x] 5.4 Integration test: a workflow document declaring `timeout` whose instance does not
      complete in time fails the instance with a `timeout`/408 error object in the failure detail;
      the same instance completing in time is unaffected.
- [x] 5.5 Integration test: a top-level `try` task cannot catch the instance-wide deadline — the
      instance still fails when the workflow-level timeout elapses while that `try` is running.

## 6. Retry per-attempt timeout (`dispatchTry`, `CatchPolicy`)

- [x] 6.1 Delete `CatchPolicy.rejectUnsupported` and its call site in `retryDelay`.
- [x] 6.2 In `InterpreterWorkflow.dispatchTry`, resolve the retry policy's `limit.attempt.duration`
      (via the shared `durationOf`) once per attempt, alongside the existing `firstFailureMillis`
      bookkeeping.
      Implementation note: resolution moved to a new public `CatchPolicy.perAttemptTimeout(clause)`
      (reusing the existing private `resolvePolicy`), since it must run in the workflow method
      before an attempt starts, not inside `CatchDecisionActivity` after one fails.
- [x] 6.3 When a per-attempt duration is resolved for the current attempt, run that attempt's
      `runTaskList(ctx, tryTask.getTry(), ...)` via a `ScopeRunnerWorkflow` child instance raced
      against `ctx.createTimer(duration)` instead of calling `runTaskList` in-process. On the
      timer winning, treat the attempt as failed with a message
      `"task '" + name + "' timed out after " + duration` and feed it into the same
      `CatchDecisionActivity` call the surrounding `catch` block already makes for any other
      attempt failure (do not special-case the retry/backoff/limit logic beyond that).
- [x] 6.4 When the policy declares no `limit.attempt.duration`, `dispatchTry` calls `runTaskList`
      in-process exactly as before this change.
- [x] 6.5 Unit test in `CatchPolicyTest`: `limit.attempt.duration` no longer throws from
      `rejectUnsupported`/`retryDelay`.
- [x] 6.6 Integration test: an attempt exceeding `limit.attempt.duration` counts toward
      `limit.attempt.count` and `limit.duration` identically to an ordinary attempt failure, and
      the retry/backoff/recovery path proceeds normally after it.

## 7. Verification

- [x] 7.1 `cd dws-orchestrator && ./mvnw verify` — this sandbox only has JDK 21 available (the repo
      pins `java.version=25`, per CLAUDE.md; JDK 25 could not be installed here), so `mvnw verify`
      itself could not be run unmodified in this environment. Verified instead with
      `-Dmaven.compiler.release=21 -Dmaven.compiler.enablePreview=true
      -Dmaven.compiler.compilerArgs="--enable-preview" -DargLine="--enable-preview"` (the codebase
      already uses unnamed-variable patterns, a preview feature pre-25): full `mvn test` run is
      green, **147/147 tests, 0 failures, 0 errors**. `mvnw verify`'s integration-test phase was not
      run (no `*IT.java` in this module). Re-run `./mvnw verify` unmodified in CI, which has JDK 25.
- [x] 7.2 Confirmed no `dws-controller` change is needed: `WorkflowCompiler.walk()` only emits
      `StepService`/`TopicBinding` for `call`/`run`/`emit`/`listen` and recurses into `try`/`fork`/
      `for` bodies for those — it never inspects `timeout` on any task or the document, so a
      `timeout` declaration deploys nothing and needs no controller-side handling, matching the
      `raise`/`try`/`catch`/`retry` precedent from Phase 2.
- [x] 7.3 Grepped the repo for the old invented type-URI prefix
      (`open-workflow-specification.org/dsl/errors/types/`). Found and fixed one live reference:
      `dws-orchestrator/src/test/resources/try-order.yaml`'s `catch.errors.with.type` filter (this
      broke `CatchPolicyTest`/`TryCatchInterpreterTest` under the new prefix until fixed — caught by
      the full test run in 7.1). Remaining references are historical records only (already-archived
      `openspec/changes/try-catch-retry/plan.md`, and this change's own `proposal.md`/`tasks.md`
      describing the migration) and were deliberately left as-is.
