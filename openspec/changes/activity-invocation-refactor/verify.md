# Verification Report: activity-invocation-refactor

## Summary

| Dimension    | Status                                              |
|--------------|-----------------------------------------------------|
| Completeness | 21/21 tasks complete; 4 new + 2 modified specs covered |
| Correctness  | All requirements mapped to code + tests             |
| Coherence    | Design decisions D1–D7 followed                     |

Gates (re-run fresh): dws-orchestrator `./mvnw verify` 123 tests ✓ · dws-controller
`./mvnw test` 59 tests ✓ · dws-call-http `go vet && go test` ✓ · dws-run `go vet && go test` ✓.
PR #41 CI: all 10 checks green, `mergeable_state: clean`. `dws-call-openapi` untouched (0 diff vs `main`).

## Completeness

**Task completion:** 21/21 checkboxes `[x]`, 0 incomplete.

**Spec coverage — each requirement mapped to implementation + test:**

- `activity-step-dispatch` / *Migrated step kinds dispatched as multi-app activities* →
  `InterpreterWorkflow.java:294-301` (call:http + run branches), `dispatchStepActivity` at
  `InterpreterWorkflow.java:355-361` (`callActivity(StepActivity.NAME, data, new WorkflowTaskOptions(retryPolicy, kebab), …)`, null→data guard). Tests: `InterpreterWorkflowIntegrationTest`.
- `activity-step-dispatch` / *call:openapi stays on HTTP* → `InterpreterWorkflow.java:296` →
  `invokeStepService` (`CallServiceActivity`). Test: openapi routing case + `openapi.yaml` fixture.
- `activity-step-dispatch` / *sub-kind from SDK accessors* → `getCallHTTP()`/`getRunShell/Script`
  branches, mirroring `WorkflowCompiler.java:207-213`.
- `activity-step-dispatch` / *activity failures → standard error shape* → `WorkflowErrors.java:64-73`
  (`config failure:`→RUNTIME, `upstream failure:`/`step '`→COMMUNICATION). Test: `WorkflowErrorsTest`.
- `activity-step-worker` (+ modified `run-step-execution`) → `dws-call-http/internal/activity/activity.go`
  (`const Name="Run"`, `AddActivityN`, markers `activity.go:58,60`) and
  `dws-run/internal/worker/worker.go` (`const ActivityName="Run"`, markers `worker.go:91,97`). `GET /healthz`
  retained in both `main.go`. Tests: `activity_test.go`, `worker_test.go`.
- `step-service-scaling` / *conditional min-scale* → `StackSynthesizer.java:149,160-163`
  (`minScale(step.kind())`: activity kinds→`"1"`, `CALL_OPENAPI`→`"0"`). Test: `StackSynthesizerTest` (10 cases).
- `step-service-scaling` / *shared namespace + state store* → same-namespace verified in
  `StackApplier` (single `config.namespace()`); shared state-store recorded as deployment
  prerequisite in `follow-ups.md`.
- `run-task-compilation` (modified/renamed) / *run dispatched over activity path* →
  `InterpreterWorkflow.java:301`; lifecycle task-type still `run` (unchanged `taskTypeOf`).

## Correctness

- **Marker contract consistent across the Go→Java boundary:** Go emits `step '<task>' upstream failure: …`
  / `step '<task>' config failure: …`; Java `WorkflowErrors` matches the same substrings. Checked
  order-sensitivity: `config failure:` tested before the `step '` prefix (config messages also start
  `step '`), so config classifies RUNTIME rather than COMMUNICATION — correct.
- **Retry parity:** activity options carry `defaultTaskOptions().getRetryPolicy()`, matching the HTTP path.
- **Empty-input / nil-result:** Go workers treat absent input as `{}`; orchestrator returns incoming
  `data` on null activity result (`next == null ? data : next`). Both match prior HTTP semantics.
- **Scenario coverage:** each spec `#### Scenario:` has a corresponding unit test (routing, retry
  carriage, null-result, openapi-stays-HTTP, upstream vs config classification, min-scale per kind,
  empty input, exit/spawn→upstream, health endpoint).

## Coherence

- **Design adherence:** D1 (kind split), D2 (SDK accessors), D3 (`WorkflowTaskOptions(retryPolicy, appId)`),
  D4 (`Run` worker + health), D5 (marker classification), D6 (conditional min-scale), D7 (shared
  namespace verified / state store flagged) — all followed.
- **API deviation from plan (benign):** plan named `github.com/dapr/go-sdk/workflow`; that package does
  not exist — workflow authoring is in `github.com/dapr/durabletask-go` (via go-sdk
  `NewWorkflowClient()` + `registry.AddActivityN`). Implementation adapted; both Go modules build and test green.
- **Pattern consistency:** `dws-call-http` uses package `activity`, `dws-run` uses package `worker` —
  a minor naming divergence between the two Go step images. Non-blocking (see SUGGESTION).

## Issues

**CRITICAL:** none outstanding.
- (Resolved) Cross-app dispatch was initially blocked on Dapr 1.15.5/1.16.0 with
  `required metadata dapr-callee-app-id ... not found` — a runtime-version mismatch (1.16.0
  runtime vs 1.18-era client libs), not a branch logic defect. **Validated fixed on Dapr 1.18.0**
  via local e2e with the real worker: `RESULT status=ORCHESTRATION_STATUS_COMPLETED
  output={"hello":"world","stock":42}`, 0 dispatch failures. `charts/dws/Chart.yaml` `appVersion`
  bumped 1.16.0 → 1.18.0 (follow-ups.md item 3). The earlier "1.16.0 is sufficient" claim is retracted.

**WARNING:** none outstanding.
- (Resolved) `WorkflowAccessPolicy` for cross-app steps is now synthesized by `dws-controller`
  (`StackSynthesizer.workflowAccessPolicies`, applied + label-GC'd via `StackApplier`; tests in
  `StackSynthesizerTest`). Not the cause of the metadata failure, but the correct production
  security posture (follow-ups.md item 4).

**SUGGESTION:**
- The two Go activity-worker packages are named differently (`dws-call-http/internal/activity` vs
  `dws-run/internal/worker`). Harmless, but aligning them would ease cross-image reading. Optional.

## Final Assessment

**Code/unit level: complete.** 21/21 tasks, every requirement mapped to code + tests, all component
gates and PR CI green, design decisions followed.

**Runtime level: validated on Dapr 1.18.0.** Cross-app activity dispatch routes end-to-end with the
real `dws-call-http` worker — the `Run` activity dispatched to app-id `check-inventory`, executed
the runner, applied `OUTPUT=merge`, and the workflow completed (`{"hello":"world","stock":42}`, 0
dispatch failures). The chart's Dapr `appVersion` is bumped 1.16.0 → 1.18.0 to match the 1.18-era
client libraries the images/orchestrator already link. **Ready for archive/merge**, with the
`WorkflowAccessPolicy` and version prerequisites now both addressed.
