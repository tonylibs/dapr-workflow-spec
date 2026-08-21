## Why

`dws-orchestrator` recognises the DSL 1.0 `try` task and then throws
`UnsupportedOperationException` — a workflow has no way to survive a failing task. Any error
anywhere fails the whole instance, so every I/O step is effectively single-shot against services
that are, in practice, intermittently unavailable. This is roadmap **Phase 2, slice 1**, unblocked
now that Phase 1's data-flow pipeline has landed (`try`/`catch` read and write through
`input`/`output`/`$context`, so they could not be built correctly before it). It is also the phase
that defines the fault surface Phase 3's Problem Details and timeouts attach to. The payoff is
direct: a definition can retry a flaky call with backoff and recover, instead of failing.

## What Changes

**`try` task interpretation (`dws-orchestrator`)**
- From: `try` is parsed, then rejected at dispatch with `UnsupportedOperationException`.
- To: `try` runs its inner task list; a failure is matched against `catch` and is either handled
  here or propagated unchanged.
- Reason: the DSL's only error-handling construct.
- Impact: non-breaking — definitions without a `try` task behave identically.

**Interpreter loop shape (`dws-orchestrator`)**
- From: one program-counter loop welded to the top-level `do` list, with a single `indexByName`;
  `end` and `exit` are indistinguishable.
- To: a reusable scope-aware task-list runner, called for the top-level `do` and again for `try`
  and `catch.do`. Each scope gets its own index, so a flow directive resolves only within its own
  scope (the spec's own rule), and `exit` completes the current scope while `end` terminates the
  instance.
- Reason: nested task lists cannot be executed by a loop that only knows one list.
- Impact: non-breaking — at top level the behavior is unchanged.

**Task compilation (`dws-controller`)**
- From: `WorkflowCompiler.walk()` visits only the top-level `do` list, so a `call`/`run` nested
  inside `try` compiles to **no** `StepService` — the orchestrator would invoke a Dapr app-id that
  was never deployed.
- To: `walk()` recurses into a `try` task's `try` and `catch.do` lists, emitting step services and
  topic bindings for nested tasks exactly as at top level. `for`/`fork` lists are still not walked.
- Reason: retrying a flaky I/O call is the motivating case; without this, `try` only works for
  in-process bodies.
- Impact: **new deployed resources** for definitions that nest `call`/`run` inside `try` — those
  step services did not exist before. Existing definitions deploy an unchanged set.

**Task-name uniqueness (`dws-controller`)**
- From: unenforced. A `call` task's Dapr app-id *is* its kebab-cased name, so duplicate names would
  collide on a Knative Service name after deployment.
- To: rejected at compile time, naming both offending tasks.
- Reason: nested lists make collisions reachable; an implicit invariant becomes an explicit one.
- Impact: **potentially breaking** for a definition with duplicate names in nested lists — but such
  a definition never compiled those tasks to anything, so it was already broken.

Additions with no "before" state:
- Static error filtering (`catch.errors.with`), dynamic filtering (`catch.when`) and exclusion
  (`catch.exceptWhen`).
- A minimal five-field runtime error object (`type`, `status`, `instance`, `title`, `detail`)
  synthesised from the failure, bound as a jq variable named by `catch.as` (default `error`) and
  visible to the retry conditions and to every expression inside `catch.do`.
- Retry with `delay`, `backoff` (`constant`/`linear`/`exponential`), `jitter`,
  `limit.attempt.count` and `limit.duration`, written inline or referenced by name from
  `use.retries`; the delay is realised as a durable Dapr timer between attempts, and each attempt
  re-runs the whole `try` list.
- Loud rejection of `retry.limit.attempt.duration` (a per-attempt timeout, Phase 3) and of an
  unresolvable named retry policy.

## Capabilities

### New Capabilities
- `workflow-error-handling`: `dws-orchestrator`'s interpretation of `try`/`catch`/`retry` — running
  the try list, synthesising the runtime error object, static and dynamic error filtering, the
  error variable binding, retry policy resolution with backoff/jitter/limits, the `catch.do`
  recovery block, and propagation of unhandled faults. It also covers the **`try`-scoped** nesting
  behavior this slice needs: the `try` and `catch.do` lists run as their own scopes (scope-local
  flow directives, `exit` vs `end`, a nesting depth bound, task lookup at depth), and
  `dws-controller` compiles the tasks nested inside them while rejecting duplicate task names.

  A general `nested-task-execution` capability — the same scope rules applied to `for`, `fork`, and
  nested `do` for other task types — is **deliberately not proposed here**. It lands with the Phase 2
  slice that needs it. The requirements above are written against `try`/`catch` specifically so that
  later slice generalises them rather than inheriting a spec no code satisfies.

### Modified Capabilities
<!-- None. workflow-data-flow is consumed unchanged: tasks inside `try` and `catch.do` go through
     the same per-task pipeline as any other task, which is a property of the existing dispatch
     path rather than a new requirement of that spec. orchestrator-event-publishing is likewise
     consumed unchanged — a `try` task and its inner tasks publish through the existing
     taskStarted/taskCompleted/taskFailed path. -->

## Impact

- **Components**: `dws-orchestrator` (primary) and `dws-controller` (compile-path recursion +
  uniqueness validation). Independent builds and CI gates are preserved — each component is changed
  and validated on its own (`./mvnw verify` and `./mvnw test` respectively). No step image
  (`dws-call-http`, `dws-call-openapi`, `dws-run`) changes; the step-service HTTP contract is
  untouched.
- **`dws-orchestrator` code**: `workflow/InterpreterWorkflow.java` (scope-aware runner, `try`
  dispatch, retry loop and timer), a new catch-decision activity and its request/result records, a
  new error-object builder, `workflow/activity/DefinitionLookup.java` (recursive lookup), and
  activity registration in `config/WorkflowRuntimeBootstrap.java`.
- **`dws-controller` code**: `compile/WorkflowCompiler.java` only — recursion in the task walk and
  a duplicate-name validation.
- **Deployed resources**: a definition nesting `call`/`run` inside `try` now deploys those step
  services (it deployed none before). Definition storage, versioning, the Dapr Configuration
  component and the orchestrator Deployment are unchanged. No new Dapr component, no new image.
- **Dependencies**: none added in either component.
- **Compatibility**: existing definitions are unaffected — none can contain a working `try` task
  today, and the top-level execution path, data-flow pipeline, `$context` semantics, kebab-case
  app-id mapping and content-addressed versioning are all unchanged. The one behavioral tightening
  is the duplicate-task-name rejection.
- **Non-goals**: `raise`, `fork`, nested `do` for other task types (`for` keeps its
  `UnsupportedOperationException`), and the general nested-task-execution capability those slices
  need — the scope runner built here is exercised only by `try`/`catch`; RFC 7807 Problem Details
  and the standard error-type catalogue;
  task/workflow timeouts including `retry.limit.attempt.duration`; `catch.then`, which the pinned
  SDK (`serverlessworkflow-types:7.26.0.Final`) does not expose.
- **CI**: covered by the existing per-component path-filtered workflows; no CI changes.
