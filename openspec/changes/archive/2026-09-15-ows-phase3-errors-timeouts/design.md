## Context

See `proposal.md` - Why/What Changes for motivation and scope. This section covers only the
mechanics that shape the approach.

- `InterpreterWorkflow` is a Dapr Durable Task orchestration: deterministic on replay, single Java
  thread, no background timers. The only way to "race" work against a deadline is
  `ctx.anyOf(taskA, taskB)` — awaiting whichever `Task` settles first, per DurableTask's Java client
  (`io.dapr.durabletask.TaskOrchestrationContext`, confirmed by inspecting the pinned
  `durabletask-client:1.18.0` jar — no `createTimer` overload or `Task` method accepts a
  cancellation token in this version). A timer that "loses" the race cannot be cancelled; a task
  that "loses" keeps running to completion in the background and its result is simply never
  awaited.
- `fork`'s `compete: true` branch (`InterpreterWorkflow.dispatchFork`) already races two `Task`
  handles this way and already accepts an un-awaited loser running to completion — this is shipped,
  accepted behavior, not a new risk this change introduces.
- `ForkBranchWorkflow` already establishes the pattern for "run one task item as its own child
  workflow instance": it re-enters `InterpreterWorkflow.dispatch` with the branch's task, name,
  data, context, variables, and depth, and publishes its own lifecycle events from
  `AdminEventBuilder.forContext(ctx)` sourced off the *child* instance's context. A child instance's
  task-started/task-completed events therefore already carry the child's own instance id today (for
  every fork branch ever executed) — again, not new.
- `TaskBase.getTimeout()` (`TaskTimeout`: inline `Timeout` or a `use.timeouts` reference) and
  `Workflow.getTimeout()` (`DoTimeout`, same inline/reference shape) both resolve to a `Timeout`
  wrapping a `TimeoutAfter` — the exact union type `durationOf` already converts for retry delay and
  listen timeout. `RetryLimitAttempt.getDuration()` is also a `TimeoutAfter`.
- `CatchPolicy.durationOf` and `InterpreterWorkflow.durationOf` are byte-for-byte duplicates today.

## Goals / Non-Goals

**Goals:**
- One mechanism for all three timeout sites (task, workflow, per-retry-attempt): run the guarded
  work as a child workflow instance, race it against a timer with `anyOf`, and raise a `timeout`
  error when the timer wins.
- Zero behavior change for any definition that declares no `timeout`/`limit.attempt.duration` —
  the existing in-instance dispatch path is untouched when no deadline applies.
- Reuse `ForkBranchWorkflow` as-is for task-level timeout (it already runs exactly one task item as
  a child instance); add exactly one new child-workflow type for the two *list*-shaped cases
  (workflow-level timeout over the top-level `do`, and per-attempt timeout over a `try` body).

**Non-Goals:**
- Cancelling the losing side of a race. Not possible with the pinned DurableTask client; the
  accepted `fork`/`compete: true` precedent stands for timeouts too (see Risks).
- Wiring `classify()` to ever produce `authorization` or `expression` kinds. Nothing in the
  orchestrator raises either today (auth is Phase 4; jq/transform failures are deliberately kept as
  `validation` per the existing, unarchived `workflow-error-handling` spec's "Data-flow failure is a
  validation error" scenario). Both kinds are added to the catalogue so `catch.errors.with.type` can
  already reference them and a future `raise` task can already construct them, but this change does
  not change what `classify()` emits for existing failure shapes.
- Rewriting `WorkflowErrors`'s five-field object shape, its message-based classification strategy,
  or the `raised error: ` passthrough. Reconciliation only, per the proposal.

## Decisions

### 1. Error catalogue: five kinds, one URI domain, `runtime` moves too
`ErrorKind` gains `AUTHORIZATION("authorization", 403, "Authorization error")` and
`EXPRESSION("expression", 400, "Expression error")`, plus `TIMEOUT("timeout", 408, "Timeout
error")` (this change is what makes `TIMEOUT` reachable). `TYPE_PREFIX` becomes
`https://serverlessworkflow.io/spec/1.0.0/errors/`.

`RUNTIME` is not part of the spec's catalogue but keeps existing as this runtime's own catch-all —
moved under the *same* new prefix (slug `runtime`) rather than left on the old invented domain.
Alternative considered: leave `runtime` on the old prefix since it's non-standard anyway. Rejected —
a `catch.errors.with.type` author would have to know which of five kinds lives on which of two
domains for no functional reason; one consistent domain is simpler to document and filter against,
and the spec does not forbid an implementation-defined slug under its own URI shape.

### 2. One child-workflow type for list-shaped guarded execution
New `ScopeRunnerWorkflow` (sibling to `ForkBranchWorkflow`, registered under its own stable name
like `dws-scope-runner`): input is `(items: List<TaskItem>, data, context, variables, depth)`,
body is exactly `InterpreterWorkflow.runTaskList(...)`, completing with the resulting
`ScopeResult`. Two call sites use it:
- **Workflow-level timeout**: when `Workflow.getTimeout()` is present, `execute()` starts one
  `ScopeRunnerWorkflow` child instance over the top-level `do` list instead of calling
  `runTaskList` in-process, races it against a timer created from the resolved deadline, and — if
  the timer wins — throws instead of completing. When absent, `execute()` calls `runTaskList`
  in-process exactly as today; this is the common path and stays untouched.
- **Per-attempt retry timeout**: when the resolved retry policy declares
  `limit.attempt.duration`, `dispatchTry` starts one `ScopeRunnerWorkflow` child instance over
  `tryTask.getTry()` for that attempt instead of calling `runTaskList` in-process, races it against
  a timer for the attempt's duration, and treats a timer win as that attempt's failure (feeding the
  existing `CatchDecisionActivity` call exactly as any other attempt failure would) — the
  surrounding retry loop (backoff, jitter, limits, catch filtering) is unchanged. When the policy
  declares no `limit.attempt.duration`, `dispatchTry` calls `runTaskList` in-process exactly as
  today.

**Task-level timeout** does not need `ScopeRunnerWorkflow` at all: `ForkBranchWorkflow` already runs
one task item as a child instance via `InterpreterWorkflow.dispatch`. When a task declares
`timeout`, `dispatch` starts a `ForkBranchWorkflow` child instance for that one task instead of
calling `dispatchBody` in-process, races it against a timer, and throws on a timer win. When
absent, `dispatch` calls `dispatchBody` in-process exactly as today.

Alternative considered: one universal wrapper type that always takes a list (wrapping a single task
as a one-element list). Rejected — `ForkBranchWorkflow` already exists, is already proven, and
already carries the richer `ForkBranchInput` (task, not task list); reusing it for the single-task
case avoids a second, near-duplicate child-workflow type whose only difference is cardinality.

### 3. Timeout is a plain marker-carrying failure, not a new exception type
A guarded-execution timeout throws `IllegalStateException` with a message the existing marker-based
classification can recognize — `"task '<name>' timed out after <duration>"` for a task or
per-attempt timeout, `"workflow timed out after <duration>"` at the top level — mirroring how
`StepInvocationException`/`DataFlowException` already fold a stable marker into their message rather
than relying on exception type (the activity boundary erases the type either way). `WorkflowErrors`
gains one new marker check (`"timed out"`) in `classify()`, ordered before the existing checks so it
is not shadowed, yielding `ErrorKind.TIMEOUT`. The existing `TASK_NAME` pattern
(`^task '([^']+)'`) already recovers the task name from the task/attempt message for
`failingTaskName`; the workflow-level message deliberately does not match it, since a workflow-level
timeout has no single failing task to attribute.

Alternative considered: a dedicated `TaskTimeoutException` (like `RaisedErrorException`). Rejected —
`RaisedErrorException` exists because it carries a structured payload (the author's own error
object) across the activity boundary; a timeout carries nothing beyond what its message already
conveys, so a plain marked exception is enough and avoids one more type doing what the existing
marker convention already does.

### 4. Workflow-level timeout is a hard instance failure, not independently catchable
The deadline race sits *outside and above* the top-level `do` list — any `try` task declared at the
top level lives *inside* the raced `ScopeRunnerWorkflow` child instance, so it can catch its own
tasks' failures exactly as today, but cannot catch the enclosing instance-wide deadline (there is
nothing above the top level for it to be caught by). A timer win at this level throws directly out
of `execute()`, following the existing `catch (RuntimeException e)` block that publishes
`instanceFailed` and rethrows — unchanged. This still goes through the same `WorkflowErrors`/
`ErrorKind.TIMEOUT` shape for the published failure detail, so "same fault path" (per the proposal)
means *same error-object construction and observability*, not *catchable from inside the instance it
terminates*.

### 5. `durationOf(TimeoutAfter)` consolidation
Both existing copies (`CatchPolicy`, `InterpreterWorkflow`) move to one static method on a shared,
dependency-free home — `WorkflowSupport` (the existing shared-utilities holder both classes already
import) — and both call sites are updated to call it. No behavior change; this is the reuse cleanup
the proposal calls out, done once rather than duplicated a third time for the new timeout call
sites.

## Risks / Trade-offs

- **[Risk] A timed-out task/attempt/workflow's losing child instance keeps running and can still
  produce side effects (e.g., an in-flight `call` completes) after the parent has already moved on
  or failed.** → Mitigation: this is the exact, already-accepted trade-off of `fork`'s
  `compete: true`; no new exposure is introduced. Callable step services are expected to be
  idempotent-adjacent for retry to be safe at all (Phase 2's retry already re-invokes them), so a
  stray extra invocation after a timeout is the same class of risk as a stray extra retry attempt.
- **[Risk] Lifecycle events from a timed-out task's `ForkBranchWorkflow`/`ScopeRunnerWorkflow` child
  instance carry the child's own instance id, not the parent's**, same as any fork branch today. A
  consumer (e.g. `dws-admin`) that does not already correlate fork-branch events to their parent
  instance will have the same gap for timed-out tasks. → Mitigation: out of scope here — this
  change does not alter event correlation, only reuses a mechanism (`ForkBranchWorkflow`) that
  already has this property in production.
- **[Risk] `ScopeRunnerWorkflow` duplicates `ScopeResult`/task-list execution across an
  instance boundary for the two list-shaped timeout sites**, so a workflow-level or per-attempt
  timeout adds one extra child-workflow instance's worth of history/orchestration overhead versus
  the in-process path. → Mitigation: strictly opt-in (only when `timeout`/
  `limit.attempt.duration` is declared); the common no-timeout path pays nothing.
- **[Trade-off] `runtime`'s URI moving to the new prefix is itself a breaking change for anyone
  already filtering `catch.errors.with.type` on the old invented `runtime` URI** — accepted as part
  of the same **BREAKING** migration the proposal already calls out for the other kinds, rather than
  leaving one kind on the old domain as an inconsistent exception.

## Migration Plan

- Single-PR change within `dws-orchestrator`; no data migration, no deployed-resource change (per
  `WorkflowCompiler.walk()` verification in the proposal), no controller involvement.
- Breaking `type` URI change ships in the same release as the new catalogue — there is no
  transition window where both prefixes are matched, consistent with how Phase 2's error shape
  itself shipped without a compatibility shim.
- Rollback is a plain revert: no persisted state depends on the new prefix or on `ScopeRunnerWorkflow` existing (workflow definitions are immutable and content-addressed; a rolled-back orchestrator image simply resumes interpreting with the old three-kind catalogue and no timeout support).
