## 1. dws-orchestrator — kind-based activity dispatch

- [x] 1.1 Add a canonical step-activity name/constant and a `StepActivityRequest` record (app-id + workflow data) mirroring `CallRequest`, in `io.dws.orchestrator.workflow.activity`.
- [x] 1.2 In `InterpreterWorkflow.dispatchConcreteTask`, split the `CallTask` branch: `getCallHTTP() != null` → multi-app activity via `WorkflowTaskOptions(retryPolicy, TaskNaming.toKebabCase(name))`; `getCallOpenAPI() != null` → existing `CallServiceActivity` path.
- [x] 1.3 In the same method, route the `RunTask` branch (`getRunShell`/`getRunScript`) to the multi-app activity path; preserve `then`/flow-outcome handling.
- [x] 1.4 Carry the default retry policy from `WorkflowSupport.defaultTaskOptions()` into the new `WorkflowTaskOptions(retryPolicy, appId)`; treat a `null`/empty activity result as data-unchanged.
- [x] 1.5 Unit-test dispatch routing: `call:http`/`run:*` schedule the activity with the kebab app-id; `call:openapi` still uses `CallServiceActivity`; empty result leaves data unchanged. Run `./mvnw verify`.

## 2. dws-orchestrator — status-free error classification

- [x] 2.1 Define stable markers for activity-path upstream/transport vs configuration failures; extend `WorkflowErrors.classify`/`statusOf` so the upstream marker maps to `ErrorKind.COMMUNICATION` with the same status default as the `502` HTTP path.
- [x] 2.2 Ensure the failing-task attribution and `{type,status,instance,title,detail}` build are identical across both dispatch paths.
- [x] 2.3 Unit-test that an equivalent upstream failure on the activity path and the HTTP path yield the same `type`/`status`/`title`; a config failure classifies distinctly. Run `./mvnw verify`.

## 3. dws-call-http — activity worker

- [x] 3.1 Add the Dapr Go Workflow SDK dependency; register a canonical `Run` activity that wraps `runner.Run`, decoding input as workflow-data JSON (empty → `{}`).
- [x] 3.2 Replace the `net/http` server entrypoint in `main.go` with the activity worker; retain a minimal `GET /healthz` for Knative readiness.
- [x] 3.3 Map `UpstreamError`/`TransportError` to the upstream/transport-equivalent activity failure marker; config/shaping errors to the distinct non-retryable marker; preserve `OUTPUT=replace|merge` shaping and nil-result-unchanged behavior.
- [x] 3.4 Update/keep tests for runner behavior; add a worker/registration test. Run `go vet ./... && go test ./...`.

## 4. dws-run — activity worker

- [x] 4.1 Mirror task 3 for `dws-run`: register `Run` wrapping `runner.Run` across all three image modes; empty input → `{}`.
- [x] 4.2 Replace `main.go` server with the activity worker; retain `GET /healthz`.
- [x] 4.3 Map `ExitError`/`SpawnError` to the upstream/transport-equivalent marker (where `RETURN` does not treat the exit code as data); config/shaping errors to the non-retryable marker; preserve `OUTPUT` shaping.
- [x] 4.4 Update/keep runner tests; add a worker/registration test. Run `go vet ./... && go test ./...`.

## 5. dws-controller — conditional min-scale

- [x] 5.1 In `StackSynthesizer.stepAnnotations`, set `autoscaling.knative.dev/min-scale` from the step's `TaskKind`: `"1"` for `CALL_HTTP`/`RUN_SHELL`/`RUN_SCRIPT_JS`/`RUN_SCRIPT_PYTHON`, `"0"` for `CALL_OPENAPI`; thread `TaskKind`/`StepService` into the method as needed.
- [x] 5.2 Unit-test synthesized annotations: activity kinds → `min-scale=1`, openapi → `min-scale=0`, other annotations unchanged. Run `./mvnw test`.

## 6. Cross-app prerequisite + follow-up flag

- [x] 6.1 Verify (and document in the change) that the controller deploys the orchestrator Deployment and step Knative Services into the same namespace and the same Dapr workflow/actor state-store component; adjust synthesis if not already guaranteed.
- [x] 6.2 Record the Dapr version follow-up: `charts/dws/Chart.yaml` pins appVersion `1.16.0`; durable/deduplicated activity results need `1.17+`. Do not bump here.

## 7. Full verification

- [x] 7.1 Run each touched component's gate: orchestrator `./mvnw verify`; controller `./mvnw test`; `dws-call-http` and `dws-run` `go vet ./... && go test ./...`. Confirm `dws-call-openapi` is untouched.
