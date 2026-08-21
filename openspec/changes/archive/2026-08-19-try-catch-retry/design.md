## Context

`dws-orchestrator` interprets its one pinned Open Workflow Specification definition as a
program-counter loop over the top-level `do` list (`InterpreterWorkflow.execute()`). `try` is
recognised by the dispatcher and immediately rejected:

```java
} else if (task.getForTask() != null || task.getTryTask() != null) {
  throw new UnsupportedOperationException(
      "task '" + name + "' uses for/try, which is recognised but not yet interpreted");
}
```

This change implements the OWS DSL 1.0 `try`/`catch`/`retry` task — roadmap **Phase 2, slice 1**.
Phase 1 (`2026-07-27-data-flow-pipeline`) is the stated prerequisite and has landed: every task body
is already wrapped by `input.from`/`input.schema` → body → `output.as`/`output.schema`/`export.as`,
and a workflow `$context` document is threaded through the loop.

The shape being implemented:

```
try:                         run the inner task list
  ├─ success              →  try task completes; data flows on
  └─ failure              →  synthesise an error object {type,status,instance,title,detail}
        ├─ not matched    →  rethrow unchanged (instance fails, as today)
        └─ matched
              ├─ retry?   →  durable timer (delay × backoff + jitter), re-run the whole try list
              └─ done     →  run catch.do with the error bound as $<catch.as>, then continue
```

**Current-state facts, read from the code rather than assumed:**

- `execute()`'s loop is welded to one list: `items = definition().getDo()`, one `indexByName` built
  from it, and `advance()` resolving every `FlowOutcome` against that single index. There is no
  concept of a task scope.
- `dispatch()` already applies the data-flow pipeline to whatever task it is handed, reading
  `input`/`output`/`export` off `TaskBase` via `DataFlowPipeline.baseOf(task)`. Any task dispatched
  through it gets the pipeline for free.
- `DefinitionLookup.taskByName()` scans **only** the top-level `do` list. Every in-process activity
  (`EvaluateSetActivity`, `EvaluateSwitchActivity`, `DataFlowInputActivity`, `DataFlowOutputActivity`)
  resolves its task that way, so nested tasks are invisible to all of them.
- `WorkflowCompiler.walk()` (dws-controller) likewise walks only the top-level list — its own comment
  reads `// switch/set/wait/for/try/raise (and do/fork) deploy nothing.` A `call`/`run` nested in a
  `try` therefore compiles to **no** `StepService`.
- Only an exception's **message** survives the Dapr activity boundary; `DataFlowException` already
  works around this by folding task/phase/detail into the message.
- `JqEvaluator.evaluate(expr, input, variables)` binds arbitrary named jq variables (added in Phase 1
  for `$context`), so binding `$error` needs no evaluator change.

**Constraints inherited from the component:**

- **Determinism/replay.** `execute()` must stay replay-deterministic — no `Instant.now()`, no RNG,
  no wall-clock arithmetic. Anything impure runs in an activity, whose result Dapr records in the
  instance history and replays verbatim.
- **jq is the only expression language.** Reuse `JqEvaluator`.
- **No persistence, no new deployed resource.** All new state is threaded through the workflow.
- **Task name → kebab-case Dapr app-id** is a cross-component invariant shared by controller and
  orchestrator.

**SDK facts, verified against `serverlessworkflow-types:7.26.0.Final` by disassembly (`javap`), not
inferred from the published schema:**

- `TryTask extends TaskBase`, with `getTry()` → `List<TaskItem>` and `getCatch()` → `TryTaskCatch`.
  Because it is a `TaskBase`, the existing data-flow pipeline wraps the `try` task itself unchanged.
- `TryTaskCatch` exposes `getErrors()` → `CatchErrors`, `getAs()` → `String`, `getWhen()`,
  `getExceptWhen()`, `getRetry()` → `Retry`, `getDo()` → `List<TaskItem>`. **It has no `getThen()`** —
  the published schema's `catch.then` is not in this SDK version (see D9).
- `CatchErrors.getWith()` → `ErrorFilter`, whose accessors are `getType()`, `getStatus()` (primitive
  `int`), `getInstance()`, `getTitle()`, and **`getDetails()`** (plural — the current spec calls the
  error field `detail`).
- `Retry` is a one-of: `getRetryPolicyDefinition()` → `RetryPolicy` or `getRetryPolicyReference()` →
  `String`.
- `RetryPolicy`: `getWhen()`, `getExceptWhen()`, `getDelay()` → `TimeoutAfter`, `getBackoff()` →
  `RetryBackoff`, `getLimit()` → `RetryLimit`, `getJitter()` → `RetryPolicyJitter`.
- `RetryBackoff` is a one-of over `ConstantBackoff`/`ExponentialBackOff`/`LinearBackoff`, each
  wrapping a type (`Constant`/`Exponential`/`Linear`) that carries **no properties** — only free-form
  `additionalProperties`. There are no per-kind multipliers to read.
- `RetryLimit.getAttempt()` → `RetryLimitAttempt` (`getCount()` primitive `int`, `getDuration()` →
  `TimeoutAfter`), `RetryLimit.getDuration()` → `TimeoutAfter`.
- `RetryPolicyJitter.getFrom()`/`getTo()` → `TimeoutAfter`.
- `Workflow.getUse().getRetries()` → `UseRetries.getAdditionalProperties()` →
  `Map<String, RetryPolicy>`.
- Every duration knob is a `TimeoutAfter` — the exact type `InterpreterWorkflow.durationOf()` already
  converts to a `java.time.Duration`.
- The SDK's `Error` type is a **definition-side** model (one-of wrappers around
  `ErrorType`/`ErrorInstance`/`ErrorTitle`/`ErrorDetails`) intended for authoring `raise`. It is not
  suitable as a runtime error value and is not used here (see D5).

## Goals / Non-Goals

**Goals:**

- Interpret `try`: run the inner task list, and on failure decide — from `catch` — whether the error
  is handled here or propagates.
- Static error filtering (`catch.errors.with`), dynamic filtering (`catch.when`), and exclusion
  (`catch.exceptWhen`).
- Bind the caught error as a jq variable named by `catch.as` (default `error`), visible to
  `catch.when`/`exceptWhen`, to the retry policy's `when`/`exceptWhen`, and to every expression inside
  `catch.do`.
- Retry with `delay`, `backoff` (constant/linear/exponential), `jitter`, `limit.attempt.count` and
  `limit.duration`, written inline **or** referenced by name from `use.retries`.
- Run `catch.do` when retries are exhausted or no retry is configured; propagate the original fault
  unchanged when the error is not caught or when `catch.do` itself fails.
- Both the `try` body and `catch.do` run through the **same** per-task pipeline as any other task.
- `dws-controller` compiles `call`/`run` tasks nested inside `try`/`catch.do` into step services, and
  rejects duplicate task names.
- `./mvnw verify` green in both components.

**Non-Goals:**

- `raise`, `fork`, and nested `do` for task types other than `try`/`catch` — separate Phase 2 slices.
  `for` keeps its `UnsupportedOperationException`.
- RFC 7807 Problem Details and the standard OWS error-type catalogue — **Phase 3**. D5 defines only
  the minimal five-field error object the DSL's own filter needs.
- Task and workflow **timeouts**, including `retry.limit.attempt.duration` (a per-attempt timeout) —
  Phase 3. D7 rejects it loudly rather than accepting it as a no-op.
- Compensation/rollback semantics — not in the DSL.
- Changing any existing task body's behavior. Definitions with no `try` task are byte-for-byte
  unaffected.

## Decisions

### D1: Extract a scope-aware `runTaskList` from `execute()`; `try` and `catch.do` reuse it

The program-counter loop moves out of `execute()` into a routine that takes **any**
`List<TaskItem>` plus the current `data`/`context`/scope variables and returns how the scope ended.
`execute()` calls it for the top-level `do`; the `try` dispatcher calls it for `try` and again for
`catch.do`. Each invocation builds its **own** `indexByName`, so a flow directive resolves only
against its own scope.

Because the scope now matters, `end` and `exit` finally differ: `end` terminates the instance from
any depth; `exit` completes the **current** scope, which at top level is the same thing but nested
returns control to the enclosing `try`. `runTaskList` therefore returns a small result carrying
`{data, context, howTheScopeEnded}` rather than just data.

- **Why**: OWS states that flow directives "may only redirect to tasks declared within their own
  scope… they cannot target tasks at a different depth". A per-scope index *is* that rule. It is also
  a mechanical extraction of code that already exists.
- **Alternative — flatten nested lists into one global program at load time**, with synthetic jump
  targets: rejected. It makes the program counter global across scopes, contradicting the scope rule,
  and it makes lifecycle events and error `instance` pointers misreport where a task lives.
- **Guard**: `MAX_STEPS` counts steps within one scope; add a separate maximum nesting depth so a
  pathological definition fails with a clear message instead of a `StackOverflowError`.

### D2: Retry re-runs the **whole** `try` list, not just the failing task

Each attempt starts the `try` list again from its first task, against the `try` task's original
(pipelined) input — not against whatever partial data the failed attempt produced.

- **Why**: `retry` is a property of `catch`, which belongs to the **try task**. The spec's own
  framing is "the retry policy to use when catching errors" for that task, and other DSL runtimes
  retry the block. Re-seeding from the original input keeps attempt *n* independent of attempt
  *n−1*'s partial mutations, which is what makes a retry a retry rather than a resume.
- **Alternative — resume at the failing inner task**: rejected. It avoids re-running side effects but
  diverges from the spec and would make `try` semantics depend on which inner task failed.
- **Accepted consequence**: side-effecting tasks earlier in the block re-execute. This is the
  author's lever — the block boundary is theirs to draw. Documented, not worked around.

### D3: One in-process `CatchDecisionActivity` returns the whole verdict

On a failure inside the `try` list the workflow method calls a single activity with
`{tryTaskName, failedTaskName, errorMessage, errorKind, attempt, firstFailureAt, now, data, context}`
and gets back `{caught, retry, delayMillis, error}`. That activity does all of:

1. synthesise the error object (D5),
2. match `catch.errors.with` statically (D6),
3. evaluate `catch.when` / `catch.exceptWhen` with the error bound (D6),
4. resolve the retry policy — inline or by name from `use.retries` (D7),
5. evaluate the policy's `when` / `exceptWhen`, apply `limit.attempt.count` and `limit.duration`,
6. compute the delay from `delay` + `backoff` + `jitter` (D8).

The workflow method only branches on the verdict and, when retrying, awaits `ctx.createTimer(delay)`.

- **Why**: jitter needs randomness and `limit.duration` needs clock arithmetic — neither is allowed
  in the workflow method. Dapr records an activity's result in the instance history and replays it
  verbatim, so drawing the random value inside the activity is both genuinely random per run and
  perfectly deterministic on replay. Consolidating the rest of the logic there follows the existing
  `EvaluateSwitchActivity` convention (pure decision logic in an activity, thin branch in the
  workflow) and keeps one testable unit.
- **Alternative — seed jitter from `hash(instanceId, attempt)`**: pure and needs no activity record,
  but the jitter is then fixed per instance, which defeats the point of jitter.
- **Alternative — split into filter / policy / delay activities**: rejected; three history records
  per failed attempt instead of one, for no separation benefit.
- **Clock inputs**: `firstFailureAt` and `now` come from the workflow context's own replay-safe
  instant, not from `Instant.now()` inside the activity, so `limit.duration` accounting is stable
  across replays.

### D4: Nested tasks are addressed by name; names must be unique across the definition

`DefinitionLookup.taskByName()` becomes recursive over `try` and `catch.do` lists, and
`dws-controller` rejects a definition containing two tasks with the same name at any depth.

- **Why**: uniqueness is already an unstated platform invariant — a `call` task's Dapr app-id **is**
  its kebab-cased name, so duplicates at different depths would collide on a deployed Knative Service
  name no matter what the orchestrator did. Enforcing it converts a confusing post-deployment
  collision into a `POST`-time rejection, and it lets every existing activity keep taking a plain
  task name.
- **Alternative — address tasks by JSON path (`/do/2/try/0`)**: rejected; churns every activity
  request shape and every test, to preserve a name-shadowing capability nothing wants.

### D5: Minimal five-field error object, synthesised from the failure

The runtime error is a plain Jackson `ObjectNode`:

```json
{ "type": "…/dsl/errors/types/communication", "status": 502,
  "instance": "/do/1/fetchOrder", "title": "Communication error",
  "detail": "task 'fetchOrder' failed: 502 Bad Gateway from app-id fetch-order" }
```

Mapping from what actually failed:

| Failure | `type` suffix | `status` |
|---|---|---|
| `DataFlowException` (transform/validation) | `validation` | `400` |
| Service-invocation failure calling a step service | `communication` | upstream HTTP status when recoverable, else `502` |
| Any other `RuntimeException` | `runtime` | `500` |

`instance` is a JSON-Pointer-shaped path to the failing task within the definition; `detail` is the
exception message, which is why that message must stay self-contained.

- **Why**: `errors.with` filters on exactly `type`/`status`/`instance`/`title`/`detail`, so *something*
  must produce those five fields or `errors.with.status: 503` — the example straight out of the spec
  reference — is unexpressible. This is the same "minimal fault shape now, Problem Details later"
  split Phase 1 made for `DataFlowException`.
- **Alternative — filter on the exception message text only**: rejected; makes the DSL's own static
  filter useless.
- **Alternative — build the full RFC 7807 model and error-type catalogue now**: rejected; explicitly
  Phase 3, and it drags in timeouts and the wire format.
- **Alternative — reuse the SDK's `Error` type**: rejected; it is a definition-side authoring model of
  one-of wrappers, not a runtime value, and it does not round-trip as JSON for jq.
- **Naming note**: the SDK's `ErrorFilter.getDetails()` (plural) is matched against the error object's
  `detail` (singular, per the current spec). Recorded so the mismatch is not "fixed" later.

### D6: Filter = static match AND `when` AND NOT `exceptWhen`

An error is caught when **all** of these hold:

1. every field present in `catch.errors.with` equals the corresponding field of the error object
   (absent fields are not constraints; an `int` field reading `0` counts as absent — see Risks);
2. `catch.when` is absent or evaluates truthy;
3. `catch.exceptWhen` is absent or evaluates falsy.

An absent `catch.errors`, `when`, and `exceptWhen` therefore catches everything, which is the
spec's `catch: {}` behavior. Both expressions are evaluated with the current `data` as input and with
`$context` plus the error (under `catch.as`, default `error`) bound as variables.

- **Why**: this is the literal reading of the schema — `errors.with` is documented as "static values",
  `when` as the dynamic complement, `exceptWhen` as the exclusion. Making the conjunction explicit
  removes the ambiguity of "does `exceptWhen` override a static match?" (it does).
- **Alternative — `exceptWhen` only applies when `when` is present**: rejected; nothing in the schema
  couples them, and treating `exceptWhen` as an unconditional veto is the useful reading.

### D7: Retry policy resolved inline or by name; unsupported knobs rejected loudly

`catch.retry` is either an inline `RetryPolicy` or a string naming one in `use.retries`. An
unresolvable name fails the task with a message naming the missing policy. `limit.attempt.duration`
— a per-attempt timeout — fails the task naming it as an unsupported knob.

- **Why**: the repo's established stance (Phase 1 rejects `schema.external` rather than skipping it)
  is that a silently ignored knob is a post-deployment mystery. A per-attempt timeout needs
  cancellation machinery the interpreter does not have, and timeouts are Phase 3.
- **Alternative — accept and ignore `limit.attempt.duration`**: rejected for the reason above.
- **Alternative — reject it at compile time in the controller**: attractive, but the controller does
  not otherwise interpret retry policies; keeping all retry semantics in one place (the orchestrator)
  avoids splitting the rules across components. Revisit if more knobs get deferred.

### D8: Backoff and jitter arithmetic (our convention, because the schema defines none)

With `d` = `retry.delay` (default 1s when absent) and `n` = the 1-based attempt number:

| `backoff` | delay for attempt `n` |
|---|---|
| absent or `constant` | `d` |
| `linear` | `d × n` |
| `exponential` | `d × 2^(n−1)` |

`jitter` adds a uniform random draw from `[from, to]` to the computed delay. Retrying stops when
`limit.attempt.count` attempts have been made or when `now − firstFailureAt` exceeds
`limit.duration`; with neither limit set, a `retry` block retries indefinitely, which is what the
schema permits.

- **Why**: `ConstantBackoff`/`LinearBackoff`/`ExponentialBackOff` wrap parameterless types — the
  schema supplies no multiplier, base, or cap — so the convention has to be defined somewhere, and a
  design doc is the right somewhere. These are the conventional definitions.
- **Alternative — read multipliers from the backoff types' free-form `additionalProperties`**:
  rejected; inventing undocumented DSL syntax.
- **Note**: an unbounded retry is bounded in practice by the instance's own lifetime; `MAX_STEPS`
  does not apply because a retry is not a step.

### D9: The handled path continues with the try task's own `then`

After a caught error is handled — retries exhausted and `catch.do` (if any) completed — the `try`
task completes normally and flow continues via the try task's `then`, exactly as if the body had
succeeded. `catch.do`'s own output becomes the try task's raw output, which then goes through the try
task's `output.as`/`export.as`.

- **Why**: `TryTaskCatch` in the pinned SDK has **no `then`** (verified by disassembly), even though
  the published schema defines `catch.then`. There is no way to read a catch-path directive from a
  parsed definition, so the only coherent behavior is "a handled error leaves the try task completed".
- **Alternative — read `catch.then` from the task's free-form metadata**: rejected; inventing parallel
  syntax for one field is worse than the gap.
- **Follow-up**: revisit when the SDK regenerates against a schema that includes `catch.then`;
  supporting it later is additive (definitions that do not set it behave identically).

### D10: The error is a scope-local jq variable, not part of `data` or `$context`

The caught error is threaded down the `catch.do` scope as a named variable binding and disappears when
that scope ends. It is passed into the data-flow activities alongside `context`, so every
`input.from`/`output.as`/`export.as` inside `catch.do` — and every `set`/`switch` expression there —
can read `$error` (or whatever `catch.as` names it).

- **Why**: the DSL calls `as` "the name of the runtime expression **variable** to save the error as".
  `JqEvaluator` already binds named variables, so this is plumbing, not new capability.
- **Alternative — merge the error into `data`**: rejected; corrupts the document the recovery block
  exists to repair, and it would leak into `ctx.complete(data)`.
- **Alternative — write it into `$context`**: rejected and worse — `$context` persists for the whole
  instance, so the error would outlive the `catch` block and be visible to unrelated later tasks.

### D11: `dws-controller` recurses into `try`/`catch.do` when walking tasks

`WorkflowCompiler.walk()` recurses into a `try` task's `try` list and its `catch.do` list, emitting
`StepService`s and topic bindings for nested `call`/`run`/`emit`/`listen` tasks exactly as it does at
top level. `for`/`fork` nested lists are still not walked.

- **Why**: the motivating use of `try`/`catch`/`retry` is retrying a flaky I/O call. Without this, a
  `call` inside `try` deploys nothing and the orchestrator invokes an app-id that does not exist —
  the feature would work only for in-process bodies. Confirmed in scope with the requester.
- **Alternative — orchestrator-only, rejecting `call`/`run` inside `try` at compile time**:
  considered and rejected; it ships the feature without the case anyone wants.
- **Note**: nested step services are named by the same kebab-case rule, which is exactly why D4's
  uniqueness check is required alongside this.

### D12: `try` participates in lifecycle events as one task, with inner tasks reported too

The `try` task publishes `taskStarted`/`taskCompleted`/`taskFailed` with type `try`, and each task
inside `try`/`catch.do` publishes its own events as it runs. An attempt that fails and is retried
reports the inner task's `taskFailed`; the `try` task itself reports `taskFailed` only when the error
is **not** handled.

- **Why**: `AdminEventBuilder` events are per task item, and hiding inner tasks would make a retried
  block invisible in `dws-admin`. Reporting the `try` task as failed on a handled error would be a
  lie — the workflow continued.
- **Trade-off**: a retried block emits N sets of inner-task events, one per attempt. That is
  information, not noise: it is how an operator sees the retries happening.

## Risks / Trade-offs

- **[Risk] Re-running the whole `try` block re-executes side effects** (D2) → Mitigation: documented
  as the semantics, with the block boundary as the author's control. No code mitigation; a
  non-idempotent first task inside a retried `try` is an authoring choice.
- **[Risk] Primitive `int` accessors make "absent" indistinguishable from `0`** —
  `ErrorFilter.getStatus()` and `RetryLimitAttempt.getCount()` both return `int` → Mitigation: treat
  `0` as "not specified" (there is no HTTP status 0, and a zero-attempt retry policy is not a retry
  policy), and unit-test both cases so the convention is pinned.
- **[Risk] Unbounded recursion on deeply nested `try` tasks** → Mitigation: an explicit maximum
  nesting depth (D1) that fails with a clear message, mirroring `MAX_STEPS`.
- **[Risk] A retry with no `limit` retries forever**, holding a workflow instance open indefinitely →
  Mitigation: schema-permitted, so not rejected; called out in the capability spec, and Phase 3's
  workflow timeout is the real bound. The delay grows under exponential backoff, so the cost is
  bounded in throughput even when unbounded in time.
- **[Risk] One activity record per failed attempt grows instance history** (D3) → Mitigation:
  inherent to doing anything impure replay-safely; bounded by `limit` when set, and one record per
  attempt is the minimum possible (D3 rejected the three-activity split).
- **[Risk] `catch.then` is unsupported because the pinned SDK lacks it** (D9) → Mitigation: documented
  in the capability spec as a known gap with defined fallback behavior; adding it later is additive.
- **[Risk] Making task names globally unique may reject a definition that previously deployed**
  (D4) → Mitigation: only definitions with duplicate names in *nested* lists are affected, and those
  never compiled to anything before — such a definition was already broken. The compile error names
  both offending tasks.
- **[Trade-off] Retry semantics live entirely in the orchestrator, so the controller cannot reject a
  bad retry policy at `POST` time** (D7) → Accepted: splitting the rules across two components is
  worse than a late, clearly-worded runtime failure. Revisit if the deferred-knob list grows.
- **[Trade-off] The error object is ours, not the SDK's** (D5) → Accepted: Phase 3 will enrich the
  same five fields rather than replace the concept, because those fields are the DSL's own.

## Migration Plan

1. **Controller recursion + name uniqueness** (`dws-controller`): recurse `walk()` into
   `try`/`catch.do`; reject duplicate task names with a compile error. `./mvnw test` green. Shippable
   alone — it only deploys step services for tasks that were previously undeployable.
2. **Scope-aware runner** (`dws-orchestrator`): extract `runTaskList` from `execute()` with a per-scope
   index, a scope-end result, and a nesting-depth guard. No behavior change for existing definitions
   (the top-level call is the old loop). `./mvnw verify` green.
3. **Recursive `DefinitionLookup`** + the error-object builder and its exception→type/status mapping,
   with unit tests.
4. **`CatchDecisionActivity`**: filter matching, `when`/`exceptWhen`, policy resolution (inline and by
   name), limits, backoff and jitter — unit-tested directly, including the `0`-means-absent cases and
   the loud rejections.
5. **Wire `try` into the dispatcher**: attempt loop, durable timer between attempts, `catch.do`
   execution with the error bound as a jq variable, propagation when uncaught. Remove `try` from the
   `UnsupportedOperationException` branch (leaving `for`).
6. **Integration tests** through `InterpreterWorkflowIntegrationTest`: caught-and-recovered, retried
   then succeeded, retried then exhausted then recovered, filtered-out error propagating, and a
   failure inside `catch.do` propagating.
7. Full gate: `./mvnw verify` in `dws-orchestrator`, `./mvnw test` in `dws-controller`.

**Rollback**: additive and gated on a task type that currently throws. Reverting the orchestrator
changes restores the `UnsupportedOperationException`; no definition without a `try` task is affected,
no deployed resource changes shape, and no stored data migrates. The controller change is independently
revertible — it only stops emitting step services that nothing referenced before.

## Open Questions

- **`catch.then`** (D9): blocked on the SDK regenerating against a schema that includes it. Fallback
  behavior is defined and additive to replace.
- **Whether the error object should also be bound during the `try` body's *last* attempt** — i.e.
  whether an inner task can see the previous attempt's error. Not required by the spec; left unbound
  to avoid speculative surface.
- **Nested `try` inside `catch.do`** works by construction (D1 recursion) but is untested beyond one
  depth level; if a real definition needs deep nesting, add a case rather than assuming.
- **Whether `limit.attempt.duration` should move to a controller-side rejection** once Phase 3 adds
  timeouts (D7) — revisit then.
