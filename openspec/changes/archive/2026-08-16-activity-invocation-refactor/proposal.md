## Why

Every I/O step today is invoked the same way: a single Dapr **service-invocation** call
(`POST /run`) from `CallServiceActivity`, so migrated step kinds can never leave scale-to-zero
and the workflow runtime owns none of the retry/durability of the actual step work. Dapr now
supports **multi-app workflow activities** (schedule an activity that runs in another Dapr
app-id). Moving `call: http`, `run: shell`, and `run: script` onto that path lets the workflow
runtime own dispatch, retries, and result plumbing, and removes the bespoke HTTP contract for
those images. `call: openapi` cannot follow yet — the JS Workflow SDK has no multi-app support —
so it stays on HTTP, which keeps the migration cleanly scoped.

## What Changes

**Orchestrator step dispatch (`dws-orchestrator`)**
- From: `dispatchConcreteTask` routes every `call` and `run` task through `CallServiceActivity` (HTTP service invocation).
- To: it branches by task kind — `CALL_HTTP` / `RUN_SHELL` / `RUN_SCRIPT` schedule a **multi-app activity** targeting the task's kebab-case app-id (`WorkflowTaskOptions(retryPolicy, appId)`); `CALL_OPENAPI` keeps routing through `CallServiceActivity`.
- Reason: enable workflow-native step invocation where the SDK supports it.
- Impact: non-breaking to definitions; internal dispatch only.

**Go step services (`dws-call-http`, `dws-run`)**
- From: plain `net/http` server exposing `POST /run` + `GET /healthz`.
- To: a Dapr Workflow **activity worker** registering one canonical `Run` activity per deployed app-id, wrapping the unchanged `runner.Run`.
- Reason: multi-app dispatch requires the target app to register the activity.
- Impact: entrypoint/transport change only; step behavior (input, empty-result, `OUTPUT` shaping, error classification) is preserved.

**Step deployment scaling (`dws-controller`)**
- From: `autoscaling.knative.dev/min-scale` hardcoded `"0"` for every step.
- To: conditional on `TaskKind` — `"1"` for `CALL_HTTP`/`RUN_SHELL`/`RUN_SCRIPT` (must stay live to receive activities), `"0"` for `CALL_OPENAPI`.
- Reason: activity workers cannot scale to zero.
- Impact: migrated steps keep one standing pod; `CALL_OPENAPI` unchanged.

**Error classification (`dws-orchestrator`)**
- From: failures classified from HTTP status (502 upstream, 500 config) folded into `StepInvocationException`'s message.
- To: a status-free, marker-based classification so activity failures surface the same `{type,status,instance,title,detail}` error shape to `catch` filters.
- Reason: activity failures carry no HTTP status.
- Impact: `catch`-filter behavior preserved across both dispatch paths.

**Explicitly not changed:** `dws-call-openapi` (invocation mechanism and `/run` contract), the
content-addressed immutable versioning, and the task-name → kebab-case app-id routing.

## Capabilities

### New Capabilities
- `activity-step-dispatch`: how `dws-orchestrator` branches step dispatch by task kind and schedules multi-app activities for the migrated kinds while keeping `call: openapi` on HTTP.
- `activity-step-worker`: how the Go step images (`dws-call-http`, `dws-run`) run as Dapr Workflow activity workers registering a canonical `Run` activity while preserving today's step behavior.
- `step-service-scaling`: how `dws-controller` sets `min-scale` conditionally by task kind so activity-invoked steps stay live and HTTP-invoked steps stay scale-to-zero.

### Modified Capabilities
- `run-step-execution`: the shared step-service contract for `dws-run` moves from an HTTP `POST /run` server to an activity worker; retryable-failure semantics restated without HTTP status codes.
- `run-task-compilation`: `run` tasks are dispatched over the multi-app **activity** path rather than the existing service-invocation path.

## Impact

- **Code**: `dws-orchestrator` (`InterpreterWorkflow.dispatchConcreteTask`, a new step-activity request/name, `WorkflowErrors`/`StepInvocationException` classification); `dws-call-http` and `dws-run` (`main.go`, `internal/server`); `dws-controller` (`StackSynthesizer.stepAnnotations`).
- **Prerequisite**: orchestrator and Go step pods must share the same Dapr namespace and workflow/actor state-store component (verified in design).
- **Dependencies / follow-up (not addressed here)**: `charts/dws/Chart.yaml` pins Dapr appVersion `1.16.0`; durable/deduplicated activity results want `1.17+`.
- **Unaffected**: `dws-call-openapi`, definition versioning, DSL author-facing behavior.
