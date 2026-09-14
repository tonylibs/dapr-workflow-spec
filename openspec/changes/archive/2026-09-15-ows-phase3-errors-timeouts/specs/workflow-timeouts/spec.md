## Purpose

Lets a workflow author bound how long a task, a retry attempt, or an entire workflow instance may
run before `dws-orchestrator` fails it with a standard, catchable `timeout` error instead of
letting it run forever.

## ADDED Requirements

### Requirement: Task-level timeout raises a catchable `timeout` error
When a task declares `timeout` (inline or by reference into `use.timeouts`), `dws-orchestrator`
SHALL fail that task with a runtime error object whose `type` identifies the standard `timeout`
kind and whose `status` is `408` if the task does not complete before the declared duration
elapses. The failure SHALL flow into the same `catch.errors.with`/`catch.when` machinery as any
other task failure, unchanged. A task that declares no `timeout` SHALL run exactly as before this
capability, with no deadline enforced. Owning component: `dws-orchestrator`.

#### Scenario: Task exceeding its timeout fails with a timeout error
- **WHEN** a task declares a `timeout` and does not complete before it elapses
- **THEN** the task fails with an error object whose `type` identifies the `timeout` kind and
  whose `status` is `408`

#### Scenario: Task completing within its timeout is unaffected
- **WHEN** a task declares a `timeout` and completes before it elapses
- **THEN** the task completes normally with its own result, and no timeout error is raised

#### Scenario: Timed-out task inside `try` is caught like any other failure
- **WHEN** a task inside a `try` list declares a `timeout` that elapses
- **THEN** the resulting `timeout` error is evaluated against the enclosing `catch` clause exactly
  as any other caught error

#### Scenario: Task without a declared timeout runs unbounded
- **WHEN** a task declares no `timeout`
- **THEN** the task runs to completion or failure with no deadline enforced by this capability

### Requirement: Workflow-level timeout fails the instance
When a workflow document declares a document-level `timeout` (inline or by reference into
`use.timeouts`), `dws-orchestrator` SHALL fail the entire workflow instance with a runtime error
object whose `type` identifies the `timeout` kind and whose `status` is `408` if the instance does
not complete before the declared duration elapses, measured from instance start. The instance-wide
deadline SHALL NOT be catchable by a `try` task declared inside the workflow, because it bounds the
whole instance rather than any task within it. A workflow document that declares no timeout SHALL
run exactly as before this capability, with no instance-wide deadline enforced. Owning component:
`dws-orchestrator`.

#### Scenario: Instance exceeding its workflow-level timeout fails
- **WHEN** a workflow document declares a `timeout` and the instance does not complete before it
  elapses
- **THEN** the instance fails with an error object whose `type` identifies the `timeout` kind and
  whose `status` is `408`

#### Scenario: Instance completing within its timeout is unaffected
- **WHEN** a workflow document declares a `timeout` and the instance completes before it elapses
- **THEN** the instance completes normally, and no timeout error is raised

#### Scenario: A top-level `try` cannot catch the instance-wide deadline
- **WHEN** a workflow document declares a `timeout` and a top-level `try` task is still running
  when that deadline elapses
- **THEN** the instance fails rather than being recovered by that `try` task's `catch` clause

#### Scenario: Workflow without a declared timeout runs unbounded
- **WHEN** a workflow document declares no `timeout`
- **THEN** the instance runs to completion or failure with no instance-wide deadline enforced by
  this capability

### Requirement: Retry per-attempt timeout bounds a single attempt
`dws-orchestrator` SHALL support `catch.retry`'s `limit.attempt.duration`: when a retry policy
declares it, each individual attempt at the `try` body SHALL fail as a `timeout` error if it does
not complete within that duration, and that failure SHALL be handled by the same
retry-decision path (backoff, jitter, `limit.attempt.count`, `limit.duration`, `catch.when`/
`exceptWhen`) as any other attempt failure — an attempt that times out counts toward
`limit.attempt.count` and toward `limit.duration` identically to a failure the attempt's own tasks
raised. A retry policy that declares no `limit.attempt.duration` SHALL bound no single attempt's
duration. Owning component: `dws-orchestrator`.

#### Scenario: Attempt exceeding its per-attempt duration is treated as a failed attempt
- **WHEN** a retry policy declares `limit.attempt.duration` and one attempt at the `try` body does
  not complete within it
- **THEN** that attempt is treated as failed with a `timeout` error
- **AND** the retry policy's backoff, limits, and conditions decide whether a further attempt is
  made exactly as they would for any other attempt failure

#### Scenario: Per-attempt timeout no longer rejected as unsupported
- **WHEN** a retry policy declares `limit.attempt.duration`
- **THEN** the policy is accepted and enforced rather than causing the task to fail with an
  unsupported-configuration error

#### Scenario: Attempt completing within its per-attempt duration is unaffected
- **WHEN** a retry policy declares `limit.attempt.duration` and an attempt completes before it
  elapses
- **THEN** the attempt's own result is used and no timeout error is raised for that attempt

#### Scenario: Policy without a per-attempt duration bounds nothing per attempt
- **WHEN** a retry policy declares no `limit.attempt.duration`
- **THEN** no individual attempt is bounded by this capability, though `limit.duration` and
  `limit.attempt.count` still apply as before
