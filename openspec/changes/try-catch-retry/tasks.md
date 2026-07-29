## 1. Controller: nested compilation & name uniqueness (`dws-controller`)

- [ ] 1.1 Make `WorkflowCompiler.walk()` recurse into a `try` task's `getTry()` list and its `getCatch().getDo()` list, emitting the same `StepService`s and `TopicBinding`s for nested `call`/`run`/`emit`/`listen` tasks as at top level; leave `for`/`fork` lists unwalked and update the `// switch/set/wait/for/try/raise (and do/fork) deploy nothing.` comment to match reality
- [ ] 1.2 Add a definition-wide task-name uniqueness validation that collects names at every depth (top-level, `try`, `catch.do`) and raises a `CompilationException` naming the duplicated name — the app-id/Knative Service name is derived from the task name alone, so a duplicate is a deployed-resource collision
- [ ] 1.3 Unit tests in `WorkflowCompilerTest`: a `call: http` inside `try` compiles to a step service with the same image/naming rule as a top-level one; a `run: shell` inside `catch.do` likewise; `emit`/`listen` inside `try` produce topic bindings; a definition with no `try` compiles to an unchanged resource set; duplicate names (same depth and across depths) are rejected
- [ ] 1.4 Run `./mvnw test` in `dws-controller/` and confirm green

## 2. Orchestrator: scope-aware task-list runner

- [ ] 2.1 Extract the program-counter loop out of `InterpreterWorkflow.execute()` into a `runTaskList(ctx, items, data, context, scopeVariables, depth)` that builds its **own** `indexByName` from the list it is given, so a `then` target resolves only within its own scope (D1)
- [ ] 2.2 Give the runner a result type carrying `{data, context, scopeEnd}` where `scopeEnd` distinguishes "ran off the end", "exit" (complete this scope only) and "end" (complete the instance), and make `execute()` map those to `ctx.complete(data)` or fall-through as it does today
- [ ] 2.3 Add a maximum nesting-depth guard (mirroring `MAX_STEPS`) that fails with a message naming the limit rather than overflowing the stack; keep `MAX_STEPS` counting steps **within** a scope
- [ ] 2.4 Thread an optional scope-variable map (empty at top level; later carrying the caught error) through `runTaskList` → `dispatch` → the data-flow activity requests, so nested expressions can bind extra jq variables alongside `$context`
- [ ] 2.5 Confirm `./mvnw verify` is green with no behavior change yet — the top-level call is the old loop

## 3. Orchestrator: task lookup & error object

- [ ] 3.1 Make `DefinitionLookup.taskByName()` search recursively through `try` and `catch.do` lists (top-level first), keeping its existing "definition has no task named 'x'" failure for a genuine miss
- [ ] 3.2 Add a runtime error-object builder producing `{type, status, instance, title, detail}` from a failure: `DataFlowException` → validation type / status 400; a service-invocation failure → communication type / upstream HTTP status when recoverable else 502; any other `RuntimeException` → runtime type / status 500 (D5)
- [ ] 3.3 Set `instance` to a JSON-Pointer-shaped path identifying the **failing inner task**, not the enclosing `try` task, and `detail` to the exception message (which is the only thing that survives the activity boundary)
- [ ] 3.4 Unit tests for the builder: one case per failure class, the recoverable-status case, and the pointer naming the inner task

## 4. Orchestrator: catch-decision activity

- [ ] 4.1 Add `CatchDecisionActivity` (in-process, registered in `WorkflowRuntimeBootstrap` alongside the existing activities) taking `{tryTaskName, failedTaskName, errorKind, errorMessage, attempt, firstFailureAt, now, data, context}` and returning `{caught, retry, delayMillis, error}` (D3)
- [ ] 4.2 Static filter matching against `catch.errors.with`: compare only the fields the filter declares, treating `ErrorFilter.getStatus() == 0` as absent, and map the SDK's plural `getDetails()` to the error object's `detail`
- [ ] 4.3 Dynamic filtering: catch only when the static filter matches **and** `catch.when` is absent or truthy **and** `catch.exceptWhen` is absent or falsy, evaluating both with `$context` and the error bound under `catch.as` (default `error`)
- [ ] 4.4 Retry-policy resolution: inline `Retry.getRetryPolicyDefinition()` or `getRetryPolicyReference()` looked up in `Workflow.getUse().getRetries().getAdditionalProperties()`; an unresolvable name fails with a message naming the missing policy
- [ ] 4.5 Retry gating: evaluate the policy's `when`/`exceptWhen`; stop retrying at `limit.attempt.count` attempts (treating `0` as absent) or when `now − firstFailureAt` exceeds `limit.duration`; reject `limit.attempt.duration` with a message naming it as an unsupported per-attempt timeout (D7)
- [ ] 4.6 Delay computation: `constant`/absent → `delay`; `linear` → `delay × attempt`; `exponential` → `delay × 2^(attempt−1)`; add a uniform random draw from `[jitter.from, jitter.to]` when `jitter` is present — drawn **inside** the activity so Dapr's recorded result makes replay deterministic (D8); reuse the existing `TimeoutAfter` → `Duration` conversion
- [ ] 4.7 Unit tests for the activity: each filter outcome (matched / status mismatch / empty clause catches all), `when` and `exceptWhen` gating, inline vs named policy, missing named policy, each backoff shape, jitter within range, both limits ending retries, and the `limit.attempt.duration` rejection

## 5. Orchestrator: `try` dispatch and retry loop

- [ ] 5.1 Add a `try` branch to `dispatchBody` that runs the `try` list through `runTaskList`, and remove `getTryTask()` from the `UnsupportedOperationException` branch (leaving `for`)
- [ ] 5.2 On failure, call `CatchDecisionActivity`; when not caught, rethrow the original exception unchanged so it reaches the existing `taskFailed`/`instanceFailed` path
- [ ] 5.3 When the verdict is retry, `ctx.createTimer(delay).await()` and re-run the **whole** `try` list from the try task's original transformed input, incrementing the attempt counter (D2); take `firstFailureAt`/`now` from the workflow context's replay-safe instant, never `Instant.now()`
- [ ] 5.4 When the verdict is handled, run `catch.do` (when present) through `runTaskList` with the error bound in the scope-variable map under `catch.as`; its resulting data becomes the `try` task's raw output, which then goes through the try task's own `output.as`/`export.as`
- [ ] 5.5 A failure inside `catch.do` propagates and fails the `try` task; a `catch` with no `do` completes the `try` task with the data as of the failure
- [ ] 5.6 Continue by the `try` task's own `then` on the handled path (`catch.then` is absent from the pinned SDK — D9); report `try` in `taskTypeOf` and publish `taskCompleted` (not `taskFailed`) for a handled error, while inner tasks publish their own events per attempt (D12)

## 6. Integration tests & gate

- [ ] 6.1 Extend `InterpreterWorkflowIntegrationTest` with a caught-and-recovered case: an inner task fails, `catch.do` runs with the error readable as `$error`, and the workflow continues after the `try` task with the recovery block's data
- [ ] 6.2 Add a retry case that fails once then succeeds, asserting the whole `try` list re-ran and the attempt started from the try task's original input
- [ ] 6.3 Add a retries-exhausted case (`limit.attempt.count`) that falls through to `catch.do`, and a filtered-out case (`errors.with.status` mismatch) that propagates the original failure and fails the instance
- [ ] 6.4 Add a failure-inside-`catch.do` case asserting the `try` task fails, and a nested-scope case asserting `exit` inside `try` returns to the enclosing task while `end` completes the instance
- [ ] 6.5 Assert the error never reaches the completion output or the workflow context, and that a task inside `try` declaring `input.from`/`output.as`/`export.as` has them applied identically to a top-level task
- [ ] 6.6 Run `./mvnw verify` in `dws-orchestrator/` and `./mvnw test` in `dws-controller/`; confirm both green and record the results
