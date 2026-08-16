## Context

`dws-orchestrator` interprets one immutable Open Workflow Specification definition as a
program-counter loop (`InterpreterWorkflow`). Every I/O task dispatches through a single Dapr
activity, `CallServiceActivity`, which makes a Dapr **service-invocation** call
(`POST /run`) to the target step's Knative Service. The target app-id is the kebab-cased task
name (`TaskNaming.toKebabCase`), and the step services (`dws-call-http` and `dws-run`, both Go;
`dws-call-openapi`, Node) each implement the same HTTP contract and sit at Knative
`min-scale: 0`, scaling up on the request.

Dapr's **multi-app workflows** let a workflow schedule an activity that executes in a different
Dapr app, targeted by app-id. The Go SDK exposes `WithActivityAppID`; the Java SDK (dapr-sdk
`1.18.0`, already pinned in the orchestrator pom) exposes it via
`WorkflowTaskOptions(retryPolicy, appId)` with a matching `getAppId()`. Java supports *activity
calls only* in the multi-app model — sufficient here. The **JS** Workflow SDK does not support
multi-app activities, so `dws-call-openapi` cannot become an activity worker.

Constraints carried from the platform:
- Task-name → kebab-case app-id routing is an implicit invariant relied on by both the
  controller (names the Knative Service/app-id) and the orchestrator (derives the same name).
- Only an exception **message** survives the Dapr activity boundary — `WorkflowErrors` already
  classifies failures by reading the message, not the type.
- Definition versioning is content-addressed and immutable; this change does not touch it.

## Goals / Non-Goals

**Goals:**
- Dispatch `CALL_HTTP`, `RUN_SHELL`, `RUN_SCRIPT` (js + python) as multi-app Dapr Workflow
  activities targeting the task's app-id.
- Keep `CALL_OPENAPI` on the existing HTTP service-invocation path, fully unchanged.
- Preserve step behavior exactly: input = current workflow data JSON; empty/nil result leaves
  data unchanged; `OUTPUT=replace|merge` shaping; upstream-vs-config failure distinction.
- Keep the `catch`-filterable error shape (`{type,status,instance,title,detail}`) identical
  across both dispatch paths.
- Make Knative `min-scale` conditional so activity workers stay live and HTTP steps stay
  scale-to-zero.

**Non-Goals:**
- Migrating `dws-call-openapi`'s invocation mechanism or its `/run` contract.
- Bumping the Dapr runtime version (`Chart.yaml` appVersion) — flagged as follow-up only.
- Changing DSL author-facing semantics, task-name routing, or definition versioning.
- Durable/deduplicated activity result semantics (needs Dapr `1.17+`).

## Decisions

### D1: Split step invocation by task kind, not uniformly
- **Choice:** In `InterpreterWorkflow.dispatchConcreteTask`, branch the `CallTask` and `RunTask`
  cases on sub-kind. `CALL_HTTP` / `RUN_SHELL` / `RUN_SCRIPT` → new multi-app activity;
  `CALL_OPENAPI` → existing `CallServiceActivity`.
- **Rationale:** Only the Go images can host an activity worker today. Splitting by kind leaves
  `dws-call-openapi` untouched and makes the migration reversible per-kind.
- **Alternative considered:** Migrate all kinds and proxy openapi through a Go shim — rejected;
  needless new component and contract surface.

### D2: Detect sub-kind via the serverlessworkflow SDK accessors
- **Choice:** Determine the kind from `callTask.getCallHTTP()` vs `callTask.getCallOpenAPI()`,
  and `run.getRunShell()` / `getRunScript()` — the same accessors `WorkflowCompiler`
  (`WorkflowCompiler.java:207-213`) uses to assign `TaskKind`.
- **Rationale:** The compiler already owns the authoritative sub-type→kind mapping. Reusing the
  same accessors keeps controller and orchestrator consistent (the existing cross-component
  invariant) without threading `TaskKind` through the deployed definition.
- **Alternative considered:** Encode `TaskKind` into the definition/config the orchestrator
  loads — rejected as a larger contract change for no added signal.

### D3: Java multi-app call = `WorkflowTaskOptions(retryPolicy, appId)`
- **Choice:** Schedule the migrated step as
  `ctx.callActivity("Run", <workflow-data>, new WorkflowTaskOptions(retryPolicy, appId), JsonNode.class)`
  where `appId = TaskNaming.toKebabCase(name)` and `retryPolicy` is carried from today's
  `defaultTaskOptions`.
- **Rationale:** Verified against dapr/java-sdk v1.18.0 — the `(WorkflowTaskRetryPolicy, String appId)`
  constructor plus `getAppId()` are exactly the multi-app targeting hook. Carrying the retry
  policy preserves current retry behavior.
- **Alternative considered:** A bare `WorkflowTaskOptions(appId)` — rejected; it would drop the
  configured retry policy.

### D4: Go step images become activity workers registering `Run`
- **Choice:** Replace the `net/http` server in `dws-call-http` and `dws-run` with a Dapr
  Workflow activity worker that registers a single canonical activity named `Run`, wrapping the
  existing `runner.Run`. Input is the current workflow-data JSON; a nil/empty runner result
  leaves the data document unchanged; `OUTPUT` shaping is applied as today.
- **Rationale:** Multi-app dispatch requires the target app to register the activity. Only the
  transport/entrypoint changes; the runner and its `OUTPUT`/interpolation logic are unchanged.
- **Activity name is stable (`Run`)** because dispatch is disambiguated by app-id, not by
  activity name — one canonical name across every deployed step keeps the orchestrator call
  site uniform.
- **`GET /healthz`:** the activity worker still needs a liveness/readiness signal for Knative;
  retain a minimal health endpoint (or the SDK's own) so the pod reports ready.

### D5: Status-free failure classification, marker-based
- **Choice:** The Go activity returns a structured failure whose message carries a stable marker
  distinguishing **upstream/transport** (retryable, maps to today's 502 → `ErrorKind.COMMUNICATION`)
  from **config/shaping** (non-retryable). On the Java side, extend the classification so an
  activity failure with the upstream marker is treated equivalently to a `502`
  `StepInvocationException`, yielding the same `{type,status,instance,title,detail}` object.
- **Rationale:** Only the message crosses the activity boundary; `WorkflowErrors.classify`
  already keys off message markers (`step '…'`, `data flow failed:`). Adding an activity-path
  marker keeps `catch` filters stable regardless of dispatch path.
- **Alternative considered:** Rely on the workflow retry policy alone and drop the upstream/
  config distinction — rejected; it would change author-visible error semantics.

### D6: Conditional Knative `min-scale`
- **Choice:** In `StackSynthesizer.stepAnnotations`, set `autoscaling.knative.dev/min-scale`
  from the step's `TaskKind`: `"1"` for `CALL_HTTP`/`RUN_SHELL`/`RUN_SCRIPT`, `"0"` for
  `CALL_OPENAPI`. `StepService` already carries `TaskKind`.
- **Rationale:** An activity worker must be running to receive dispatched activities; it cannot
  scale to zero. `CALL_OPENAPI` remains HTTP-triggered and keeps scale-to-zero.

### D7: Shared namespace + state store (dispatch prerequisite)
- **Choice:** Confirm and document that the controller deploys the orchestrator Deployment and
  the step Knative Services into the **same** namespace, wired to the **same** Dapr
  workflow/actor state-store component. Cross-app activity dispatch routes through the shared
  actor/workflow backend, so both sides must resolve the same store.
- **Rationale:** Multi-app activities are brokered by the workflow engine's state store; a split
  store or namespace silently breaks dispatch.

## Risks / Trade-offs

- [Trade-off] Migrated steps lose scale-to-zero → accepted: being an activity worker means a
  standing pod per migrated step; that is inherent to the model (D6).
- [Risk] JS SDK still lacks multi-app support → Mitigation: `CALL_OPENAPI` deliberately left on
  HTTP; no dependency on JS multi-app.
- [Risk] Cross-app dispatch is version-coupled → **Realized (2026-08-16), now BLOCKING.** Local e2e
  showed the callee-app-id metadata is not propagated on Dapr 1.15.5 (unsupported; needs ≥1.16.0) or
  1.16.0 (`dapr-callee-app-id or dapr-app-id not found`) — a runtime/SDK compatibility issue, not a
  branch logic bug (see follow-ups.md item 3 and [dapr/dapr#10039](https://github.com/dapr/dapr/issues/10039)).
  Mitigation: a validated (daprd, SDK/durabletask-go) version pairing MUST be identified, pinned in
  the chart + Go modules + orchestrator pom, and the e2e re-run before enabling this on a cluster.
  The earlier "1.16.0 is sufficient" assessment is retracted.
- [Risk] Dapr `1.16.0` lacks durable/deduplicated activity results → Mitigation: separate from the
  dispatch blocker above; `1.17+` enables dedup guarantees, a non-goal here beyond whatever version
  the dispatch fix requires.
- [Risk] Error-shape drift between HTTP and activity paths → Mitigation: single classification
  in `WorkflowErrors`, covered by tests asserting identical `{type,status,...}` for equivalent
  upstream/config failures on both paths.
- [Risk] Namespace/state-store mismatch breaks dispatch silently → Mitigation: D7 verification
  plus a smoke path in integration testing.

## Migration Plan

1. Land the Go activity-worker change behind the same image names (`dws-call-http`, `dws-run-*`);
   images now register the `Run` activity and keep a health endpoint.
2. Land the orchestrator kind-based dispatch + error classification.
3. Land the controller conditional `min-scale`.
4. Because definitions are content-addressed and immutable, a redeploy of an existing definition
   re-synthesizes the stack with the new `min-scale` and the orchestrator image that speaks the
   activity path; app-ids and Service names are unchanged, so the update is in place.
- **Rollback:** revert the three component changes; app-ids and the DSL are unchanged, so
  reverting the orchestrator + step images + controller restores the pure HTTP path with no
  definition migration.

## Open Questions

- Does the Go Dapr Workflow SDK activity worker need an explicit health endpoint for Knative
  readiness, or does the sidecar/SDK expose one already? (Resolve during D4 implementation.)
- Exact marker string and struct for the Go activity failure — chosen in implementation to line
  up with `WorkflowErrors`' existing prefix matching.
