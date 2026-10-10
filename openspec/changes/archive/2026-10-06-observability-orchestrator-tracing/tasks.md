## 1. Controller: settings reader (dws-controller)

- [x] 1.1 Add an `ObservabilitySettings` value (`OFF` default; enabled, instrumentation reference) and a reader that fetches `observability.enabled` and `observability.instrumentation` from `dws-controller-config` in one bounded (1 s) call, treating absent/blank/other value, null client, empty Mono, exception and timeout as off.
- [x] 1.2 Add the existence check for Dapr Configuration `dws-tracing` in the workflow namespace; absent, forbidden or error returns `OFF` and logs a single WARN.
- [x] 1.3 Wire the settings into `StackApplier.apply` per deploy and pass them to `StackSynthesizer`; expose the constant names (`dws-tracing`, key names, container name) in one place.

## 2. Controller: orchestrator stamping (dws-controller)

- [x] 2.1 In `StackSynthesizer`, with settings off take the exact current code path; with settings on stamp `inject-java`, `container-names: orchestrator` and put-if-absent `dapr.io/config: dws-tracing` on the pod template.
- [x] 2.2 Stamp `OTEL_SERVICE_NAME` (app ID) and `OTEL_RESOURCE_ATTRIBUTES` (`dws.workflow.name`, `dws.workflow.version`, percent-encoded, appended to any existing value) on the `orchestrator` container only; emit no node attributes and no sampling setting.
- [x] 2.3 Add raw `get` on `configurations.dapr.io` to `dws-controller/k8s/controller-rbac.yaml`.

## 3. Controller: tests (dws-controller)

- [x] 3.1 Unit tests: flag absent, flag false, store throws, store empty, store timeout each yield an orchestrator Deployment equal to the pre-change spec (compare against the off-settings output and keep `noSecretWorkflowKeepsLiteralOrchestratorEnvironment` unchanged).
- [x] 3.2 Unit tests: flag on yields the instrumented spec; assert annotations, `container-names` exactly `orchestrator`, the identity env values (`OTEL_SERVICE_NAME`, `dws.workflow.name`, `dws.workflow.version`) and absence of node/sampling settings; assert percent-encoding and append-to-existing.
- [x] 3.3 Unit tests: missing/forbidden/erroring `dws-tracing` falls back to the off spec; existing `dapr.io/config` is preserved; other generated resources are unchanged by the flag.
- [x] 3.4 Extend `StackApplierTest` (Fabric8 mock server) so apply with the flag on and `dws-tracing` present stores the instrumented Deployment, and with it absent stores today's.

## 4. Chart: dws-tracing Configuration (charts/dws)

- [x] 4.1 Add `templates/observability/workflow-tracing-configuration.yaml` rendering the tracing-only `dws-tracing` Configuration through `dws.observability.daprTracing`, gated by `observability.enabled` and `observability.workflows.enabled`.
- [x] 4.2 Add `get` on `dapr.io/configurations` to the controller Role in `templates/controller/rbac.yaml`.
- [x] 4.3 Update the `observability.workflows.enabled` comment in `values.yaml`, the `config-component.yaml` header comment and the chart README observability section to document the store keys, next-deploy-only semantics and the standalone Configuration.

## 5. Chart: render tests (charts/dws)

- [x] 5.1 Extend `charts/dws/tests/observability-render-test.sh` to assert `dws-tracing` renders only when both gates are on, has tracing and no pipeline, shares the controller's translation, and that the controller and admin Configurations and Deployments are unchanged in all four auth/observability combinations.
- [x] 5.2 Assert the Role `get` rule and that the default render still byte-matches the Phase 1 baseline fixture.

## 6. Documentation

- [x] 6.1 Add an addendum to ADR 0005 recording the `OTEL_SERVICE_NAME` finding (Operator override) and the orchestrator's omission of node attributes.
- [x] 6.2 Add `docs/roadmaps/observability-phase2a-evidence.md` recording the environment, what was exercised, and the replay probe procedure and expected observations; state the live run status truthfully.

## 7. Verification

- [x] 7.1 Run the controller gate (`./mvnw verify` in `dws-controller`) and the chart gate from root `CLAUDE.md` (helm 3.19.0).
- [x] 7.2 Run a live cluster trace and replay probe, record the trace ID, and write the replay ADR if replay is clean. **Done 2026-10-08, with deviations** (local kind cluster; Operator built from source without cert-manager; Jaeger direct; Knative absent so steps ran as Deployments; see `docs/roadmaps/observability-phase2a-evidence.md`). Trace IDs: `5f94c8e6352bbf63e23219291f0489e7` (committed orchestrator), `830a2bceca74cbb6d5233e878a94b199` / `cc16b7c4170ca17927986d3bb3a628a4` (pod killed mid-run) / `634fbcd8c5eb05ac74721695efec227a` (patched orchestrator). Replay is clean for the span model, so `docs/adr/0009-trace-context-across-workflow-replay.md` was written. The committed `InterpreterWorkflow` is **not** replay-deterministic (it catches `OrchestratorBlockedException`); the clean runs used an uncommitted local guard. Per-step-call spans do not appear (Track B).
