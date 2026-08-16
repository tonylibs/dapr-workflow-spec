# Activity Invocation Refactor — Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development
> to implement this plan task-by-task. Each task ends at a commit point with its
> component gate green.

**Goal:** Migrate `call: http`, `run: shell`, and `run: script` step invocation from Dapr HTTP
service invocation to Dapr Workflow multi-app activity invocation, leaving `call: openapi` on HTTP.

**Architecture:** `dws-orchestrator` branches step dispatch by task kind and schedules a canonical
`Run` activity against the step's kebab-case app-id via `WorkflowTaskOptions(retryPolicy, appId)`.
The Go step images (`dws-call-http`, `dws-run`) become Dapr Workflow activity workers registering
that `Run` activity; `dws-controller` sets Knative `min-scale=1` for the activity-invoked kinds so
they stay live. Failure classification is marker-based so both dispatch paths surface one error shape.

**Tech Stack:** Java 25 / Dapr SDK 1.18.0 (orchestrator, controller), Go 1.26 + Dapr Go Workflow SDK
(step images), Quarkus/cdk8s (controller synthesis), JUnit + `go test`.

---

## Task 1: Orchestrator — step-activity request + kind-based dispatch

- [ ] **Step 1:** Add `StepActivityRequest(String appId, JsonNode data)` record in `io.dws.orchestrator.workflow.activity`, plus a `Run` activity-name constant. Reference: `CallRequest.java`.
- [ ] **Step 2:** Write a failing `InterpreterWorkflow` test: a `call:http` task named `checkInventory` schedules the `Run` activity with `WorkflowTaskOptions.getAppId() == "check-inventory"` and the current data as input (mock `WorkflowContext`, assert `callActivity` args).
- [ ] **Step 3:** In `dispatchConcreteTask`, split `case CallTask callTask`: if `callTask.get().getCallHTTP() != null` → `ctx.callActivity(RUN, data, new WorkflowTaskOptions(retryPolicy, TaskNaming.toKebabCase(name)), JsonNode.class)`; else (`getCallOpenAPI() != null`) → keep `CallServiceActivity`. Extract `retryPolicy` from `WorkflowSupport.defaultTaskOptions().getRetryPolicy()`.
- [ ] **Step 4:** Add a failing test for `run:shell`/`run:script` routing to the activity path; then route `case RunTask runTask` accordingly, preserving `FlowOutcome.of(runTask.getThen())`.
- [ ] **Step 5:** Add a test that a `null`/empty activity result leaves data unchanged; implement the guard in the `thenApply`.
- [ ] **Step 6:** Run `cd dws-orchestrator && ./mvnw verify`. Commit: `feat(orchestrator): dispatch call:http/run steps as multi-app activities`.

## Task 2: Orchestrator — status-free error classification

- [ ] **Step 1:** Write a failing `WorkflowErrorsTest` case: an activity failure message carrying the upstream marker classifies as `ErrorKind.COMMUNICATION` with the same status default as the `502` HTTP case.
- [ ] **Step 2:** Define marker constants (upstream/transport vs configuration) and extend `WorkflowErrors.classify`/`statusOf` to recognize the activity-path upstream marker alongside the existing `step '…'` marker.
- [ ] **Step 3:** Add a test asserting an equivalent upstream failure on the activity path and the HTTP path build the same `{type,status,title}`; a config failure classifies distinctly.
- [ ] **Step 4:** Run `./mvnw verify`. Commit: `feat(orchestrator): classify activity-path step failures into the shared error shape`.

## Task 3: dws-call-http — activity worker

- [ ] **Step 1:** `cd dws-call-http && go get` the Dapr Go Workflow SDK; add module deps.
- [ ] **Step 2:** Write a failing worker test: registering the `Run` activity and invoking it with a JSON object runs `runner.Run` and returns the shaped output; empty input → `{}`.
- [ ] **Step 3:** Implement the activity handler wrapping `runner.Run`, decoding input as workflow-data JSON, applying `OUTPUT=replace|merge` shaping, and returning data-unchanged on nil result.
- [ ] **Step 4:** Map `UpstreamError`/`TransportError` → upstream/transport activity-failure marker; other errors → non-retryable config marker (test each).
- [ ] **Step 5:** Replace `main.go` server startup with the activity worker; retain a minimal `GET /healthz`.
- [ ] **Step 6:** Run `go vet ./... && go test ./...`. Commit: `feat(call-http): run as a Dapr Workflow activity worker`.

## Task 4: dws-run — activity worker

- [ ] **Step 1:** Mirror Task 3 Step 1–3 for `dws-run` across all three modes; empty input → `{}`.
- [ ] **Step 2:** Map `ExitError`/`SpawnError` → upstream/transport marker (where `RETURN` does not treat the exit code as data); config/shaping → non-retryable marker (test each).
- [ ] **Step 3:** Replace `main.go` server with the activity worker; retain `GET /healthz`.
- [ ] **Step 4:** Run `go vet ./... && go test ./...`. Commit: `feat(run): run as a Dapr Workflow activity worker`.

## Task 5: dws-controller — conditional min-scale

- [ ] **Step 1:** Write a failing `StackSynthesizer` test: a `CALL_HTTP`/`RUN_SHELL`/`RUN_SCRIPT` step → `min-scale=1`; a `CALL_OPENAPI` step → `min-scale=0`; other annotations unchanged.
- [ ] **Step 2:** Change `stepAnnotations` to take the `StepService` (already carries `TaskKind`) and set `autoscaling.knative.dev/min-scale` by kind.
- [ ] **Step 3:** Run `cd dws-controller && ./mvnw test`. Commit: `feat(controller): keep activity-invoked steps live via conditional min-scale`.

## Task 6: Prerequisite verification + follow-up flag

- [ ] **Step 1:** Confirm `StackSynthesizer.orchestratorDeployment` and step Services deploy into the same namespace and reference the same Dapr workflow/actor state-store component; add/adjust an assertion if not already guaranteed.
- [ ] **Step 2:** Add a note (change docs / follow-up issue) recording the Dapr `1.17+` requirement for durable/deduplicated activity results vs the `1.16.0` pin in `charts/dws/Chart.yaml`. Do not bump.
- [ ] **Step 3:** Commit any doc/verification changes: `chore: verify shared dispatch backend; flag Dapr 1.17+ follow-up`.

## Task 7: Full verification

- [ ] **Step 1:** Run all touched gates: orchestrator `./mvnw verify`; controller `./mvnw test`; `dws-call-http` and `dws-run` `go vet ./... && go test ./...`.
- [ ] **Step 2:** Confirm `dws-call-openapi` has no diff. Final commit if needed.
