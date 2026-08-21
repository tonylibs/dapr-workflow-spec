## MODIFIED Requirements

### Requirement: Runtime error object carries the DSL's five error fields
`dws-orchestrator` SHALL represent a caught failure as a JSON object with the fields `type`,
`status`, `instance`, `title`, and `detail`. `type` SHALL be a URI under the standard Open Workflow
Specification error-type catalogue's namespace (`https://serverlessworkflow.io/spec/1.0.0/errors/`)
identifying the failure's kind: `validation` (a transform/validation failure), `communication` (a
step-service communication failure), `timeout` (a task, workflow, or retry-attempt deadline
elapsing), or — for a failure this runtime does not classify into one of the standard kinds it
currently produces — its own `runtime` kind, published under the same namespace. `status` SHALL
carry the upstream HTTP status when one is recoverable and a per-kind default otherwise (`400` for
`validation`, `502` for `communication`, `408` for `timeout`, `500` for `runtime`), `instance` SHALL
identify the failing task's location in the definition, and `detail` SHALL carry the failure
detail. The catalogue also defines `authorization` (`403`) and `expression` (`400`) kinds, which
`catch.errors.with.type` MAY filter against, but this capability does not classify any failure into
either — authorization failures are out of scope until authentication exists, and
expression/transform failures continue to classify as `validation` per the scenario below.

#### Scenario: Step-service failure is a communication error
- **WHEN** a `call` task inside `try` fails because its step service returned an upstream failure
- **THEN** the error object's `type` identifies a communication failure under the standard
  namespace
- **AND** its `status` is the upstream HTTP status when one is recoverable

#### Scenario: Data-flow failure is a validation error
- **WHEN** a task inside `try` fails its `output.schema` validation
- **THEN** the error object's `type` identifies a validation failure under the standard namespace
- **AND** its `detail` names the offending field

#### Scenario: Error identifies the failing task
- **WHEN** any task inside `try` fails
- **THEN** the error object's `instance` identifies that task rather than the enclosing `try` task

#### Scenario: Unclassified failure is the runtime kind under the standard namespace
- **WHEN** a task inside `try` fails in a way this capability does not classify as `validation`,
  `communication`, or `timeout`
- **THEN** the error object's `type` identifies the `runtime` kind under the
  `https://serverlessworkflow.io/spec/1.0.0/errors/` namespace
- **AND** its `status` defaults to `500`

### Requirement: Backoff, jitter, and retry limits
`dws-orchestrator` SHALL compute an attempt's delay from the policy's `delay` and `backoff`:
`constant` (or absent) SHALL use the delay unchanged, `linear` SHALL scale it by the attempt number,
and `exponential` SHALL double it per attempt. When `jitter` is declared, a random duration drawn
from `[jitter.from, jitter.to]` SHALL be added. Retrying SHALL stop when `limit.attempt.count`
attempts have been made or when the elapsed time since the first failure exceeds `limit.duration`.
An attempt count of `0` SHALL be treated as absent. `limit.attempt.duration` bounds a single
attempt's own duration and, when it elapses before the attempt completes, that attempt SHALL be
treated as failed with a `timeout` error and counted toward `limit.attempt.count` and
`limit.duration` identically to any other attempt failure; the detailed timeout behavior is
specified by the `workflow-timeouts` capability's "Retry per-attempt timeout bounds a single
attempt" requirement.

#### Scenario: Exponential backoff grows the delay
- **WHEN** a policy declares `exponential` backoff and a base delay
- **THEN** each successive attempt waits twice as long as the previous one

#### Scenario: Attempt limit ends retrying
- **WHEN** a policy declares `limit.attempt.count` and every attempt fails
- **THEN** no more than that many attempts are made before the recovery path is taken

#### Scenario: Duration limit ends retrying
- **WHEN** a policy declares `limit.duration` and the elapsed time since the first failure exceeds it
- **THEN** no further attempt is made and the recovery path is taken

#### Scenario: Jitter does not break replay
- **WHEN** a workflow instance with a jittered retry is replayed
- **THEN** the same delay is used as on the original execution

#### Scenario: Per-attempt duration limit is enforced, not rejected
- **WHEN** a retry policy declares `limit.attempt.duration`
- **THEN** the policy is accepted, and an attempt exceeding that duration is treated as a failed
  attempt rather than causing the task to fail with an unsupported-configuration error
