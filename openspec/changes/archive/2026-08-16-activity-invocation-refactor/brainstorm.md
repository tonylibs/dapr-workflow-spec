<!--
Raw capture of the design discussion for activity-invocation-refactor.
Scope was locked by the requirement before this change was opened, so this is a
decision log distilled from that requirement rather than an open-ended exploration.
-->

# Brainstorm — activity-invocation-refactor

## Background

Today `dws-orchestrator` invokes every I/O step the same way: `CallServiceActivity`
(a single Dapr activity) makes a Dapr **service-invocation** call — `POST /run` — to the
target step's Knative Service, resolved from the kebab-cased task name. This is true for
`call: http`, `call: openapi`, `run: shell`, and `run: script` alike. Each step service is a
plain `net/http` (Go) or Fastify (Node) server that stays scale-to-zero (`min-scale: 0`) and
scales up on the incoming HTTP request.

Dapr now supports **multi-app workflow activities**: a workflow in one app can schedule an
activity that is registered and executed in a *different* Dapr app, targeted by app-id
(Go's `WithActivityAppID`; the Java SDK exposes the same via `WorkflowTaskOptions(appId)`).
Moving step invocation onto this path removes the bespoke HTTP contract for the step kinds that
can support it, and lets the workflow runtime own retries, durability, and result plumbing.

The JS Workflow SDK does **not** yet support multi-app activities, so `dws-call-openapi`
(Node) cannot become an activity worker. It must stay on HTTP service invocation.

## Decisions

### Q1: Which step kinds migrate to multi-app activity invocation?
**Decision:** `CALL_HTTP`, `RUN_SHELL`, `RUN_SCRIPT` (js + python) migrate.
`CALL_OPENAPI` stays on HTTP service invocation.
**Why:** The Go step images (`dws-call-http`, `dws-run-*`) can host a Dapr Workflow activity
worker; the Node `dws-call-openapi` image cannot until the JS SDK ships multi-app support.
Splitting by kind keeps `dws-call-openapi` completely untouched.

### Q2: How does the orchestrator tell the kinds apart at runtime?
**Decision:** Branch inside `dispatchConcreteTask` on the serverlessworkflow SDK sub-type
accessors — `callTask.getCallHTTP()` vs `callTask.getCallOpenAPI()`, and
`run.getRunShell()` / `getRunScript()` — mirroring exactly how `WorkflowCompiler`
(`WorkflowCompiler.java:207-213`) already classifies them into `TaskKind`.
**Why:** The orchestrator currently treats `CallTask`/`RunTask` uniformly and has no notion of
sub-kind. The compiler already owns the authoritative sub-type→kind mapping; reusing the same
accessors keeps the two sides consistent (the existing cross-component invariant).
**Alternative rejected:** Threading `TaskKind` through the deployed definition — rejected as a
larger contract change; the SDK types already carry the distinction.

### Q3: What is the Java multi-app activity API?
**Decision:** `ctx.callActivity(activityName, input, new WorkflowTaskOptions(retryPolicy, appId), ReturnType)`.
**Why:** Verified against dapr/java-sdk v1.18.0 (`dapr.sdk.version` in the orchestrator pom):
`WorkflowTaskOptions` has a constructor `(WorkflowTaskRetryPolicy, String appId)` and a
`getAppId()`; the docs note Java supports *activity calls only* for multi-app, which is exactly
what we need. The retry policy from the existing `defaultTaskOptions` must be carried into the
new options so retry behavior is preserved.
**app-id target:** `TaskNaming.toKebabCase(taskName)` — unchanged from today's routing.

### Q4: What replaces the Go step HTTP server?
**Decision:** Replace the `net/http` server (`internal/server/server.go`) in `dws-call-http`
and `dws-run` with a Dapr Workflow **activity worker** that registers one canonical activity
(name `Run`) per deployed app-id. The activity handler wraps the existing `runner.Run`.
**Preserve behavior exactly:** input = current workflow-data JSON; empty/nil result leaves the
data document unchanged; `OUTPUT=replace|merge` shaping; upstream-vs-config error distinction.
**Why:** Multi-app dispatch requires the target app to register the activity in the shared
workflow runtime. The runner logic is unchanged — only the transport/entrypoint changes.

### Q5: How do activity failures get classified without HTTP status codes?
**Decision:** Define a status-free classification equivalent to today's 502-upstream /
500-config split. The Go activity returns a structured error whose message carries a stable
marker; `WorkflowErrors.classify` already reads the failure *message* (not the exception type),
so both dispatch paths surface the same `{type,status,instance,title,detail}` error shape to
workflow authors.
**Why:** `StepInvocationException` folds app-id + HTTP status into its message today precisely
because only the message survives the activity boundary. Activity failures carry no HTTP
status, so we mint an equivalent marker (upstream vs config) and map it to the same
`ErrorKind.COMMUNICATION` / status defaults, keeping `catch` filters stable.

### Q6: Knative min-scale — can migrated steps stay scale-to-zero?
**Decision:** No. Steps invoked as activities must stay live to receive dispatched work, so
`autoscaling.knative.dev/min-scale` becomes conditional on `TaskKind`:
`1` for `CALL_HTTP`/`RUN_SHELL`/`RUN_SCRIPT`, `0` for `CALL_OPENAPI` (unchanged).
Changed in `StackSynthesizer.stepAnnotations` (`StackSynthesizer.java:~148`).
**Trade-off accepted:** migrated steps lose scale-to-zero (a standing pod per migrated step);
that is inherent to being an activity worker rather than an HTTP-on-demand service.

## Cross-component invariants preserved
- Task-name → kebab-case app-id routing (`TaskNaming` / `Names.kebab`) — unchanged.
- Content-addressed, immutable definition versioning — unchanged.
- `call: openapi` HTTP `/run` contract and `dws-call-openapi` — untouched.
- `catch`-filterable error shape (`{type,status,instance,title,detail}`) — preserved across both paths.

## Out of scope / follow-ups
- **Dapr version:** `charts/dws/Chart.yaml` pins appVersion `1.16.0`. Durable, deduplicated
  activity results want `1.17+`. Flagged as a follow-up; **not** bumped here.
- `dws-call-openapi` invocation mechanism and its `/run` contract — out of scope.
- Any Dapr runtime version bump — out of scope, follow-up only.

## Open question carried into design
- Confirm the Go step pods and orchestrator pods share the same Dapr namespace and the same
  workflow/actor state-store component — a hard prerequisite for cross-app dispatch (Q in scope
  item 4). Design must state how this is guaranteed by the controller's deployment.
