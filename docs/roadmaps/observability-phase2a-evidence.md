# Observability Phase 2a — evidence

Change: `observability-orchestrator-tracing`  
Authoring date: 2026-10-06  
**Live cluster run: NOT EXECUTED.** See [Why the live run did not happen](#why-the-live-run-did-not-happen).
No trace ID is recorded in this file because none was produced. Do not read any section below as a
live result unless it says so.

## What was verified without a cluster

These are unit and render tests, not live evidence.

| Requirement | Covered by |
|---|---|
| Flag absent, flag `false`, store error, empty store and timeout each produce today's orchestrator spec | `ObservabilityFlagsTest`, `StackSynthesizerTest` (off-settings equality), `StackApplierTest` |
| Flag on produces the instrumented spec (`inject-java`, `container-names: orchestrator`, `dapr.io/config: dws-tracing`) | `StackSynthesizerTest`, `StackApplierTest` |
| Injection targets only the `orchestrator` container; existing `dapr.io/config` is never replaced | `StackSynthesizerTest` |
| Identity: `OTEL_SERVICE_NAME` = app ID, `dws.workflow.name`, `dws.workflow.version` | `StackSynthesizerTest` (values asserted) |
| Missing or forbidden `dws-tracing` falls back to today's spec and does not fail the deploy | `ObservabilityFlagsTest`, `StackApplierTest` |
| `dws-tracing` is tracing-only, pinned to `samplingRate: "1"`, shares the Phase 1 translation; no auth regression | `charts/dws/tests/observability-render-test.sh` |
| Default chart render is byte-identical to the Phase 1 baseline | `charts/dws/tests/observability-render-test.sh` |

Image check: `dws-orchestrator/Dockerfile` builds on `eclipse-temurin:25-jdk` and runs on
`eclipse-temurin:25-jre` with `java -jar`. It is a real JVM, not a native image, so the agent can
attach. The agent bundled with OTel Operator 0.159.0 is 2.31.1, whose support table lists Temurin
25 and Spring Boot 4.

## Why the live run did not happen

The authoring sandbox had no Kubernetes cluster and no kubeconfig, no Docker or other container
runtime, and no privileges (`CAP_SYS_ADMIN`, `/dev/kvm`) to start one. The orchestrator image is
also private on GHCR (Phase 1 evidence), so it would have had to be built and loaded locally.
Nothing was attempted or simulated in place of the run.

## Procedure to run it

1. Cluster with the OTel Operator (chart `0.123.0` / operator `0.159.0`), cert-manager, Dapr, and
   the Phase 1 fixture Collector plus Jaeger, as in the Phase 1 evidence run.
2. Build `dws-orchestrator` locally and load it into the cluster; point
   `controller.images.orchestrator` at it.
3. Install the chart with `observability.enabled=true`, an OTLP endpoint reaching the Collector, and
   `traces.samplingRate=1` for the run.
4. In the `dws-controller-config` store set `observability.enabled` to `true` (and optionally
   `observability.instrumentation`). Confirm `kubectl get configuration dws-tracing`.
5. Deploy a workflow with at least one call step. Confirm on the orchestrator pod: the Java init
   container is present for container `orchestrator` only and `daprd` has none;
   `dapr.io/config=dws-tracing`; `OTEL_SERVICE_NAME` equals the workflow name.
6. Start an instance. Restart the orchestrator pod mid-run (or use a workflow with a timer) so Dapr
   replays it.
7. In Jaeger record: the trace ID; the service list (must show the controller and the workflow's
   app ID); one client-side span per step call.

State in the final record that there is no span inside the step pods; that is Track B.

## What to record for the replay finding

| Observation | Clean | Not clean |
|---|---|---|
| Workflow root span ID across replays | Stable (durabletask-go persists it in `ExecutionStarted`) | New ID per replay |
| `activity:<name>` span count per activity execution | One | More than one per execution |
| Trace ID across a replay | Same | Restarted |
| Controller to orchestrator linkage | Same trace | Separate traces (record; Java SDK 1.18.0 may not propagate the caller context, SDK PR #1783) |

Decision rule: if replay is clean, write the ADR "trace context across Dapr Workflow replay" from
the recorded evidence. If spans duplicate or the trace restarts, stop and report; do not redesign
the span model inside this change.

Source-level expectation (not evidence): durabletask-go v0.12.1 reuses the persisted workflow span
ID and publishes it only on completion, so duplicates are not expected. The Dapr documentation also
says activity code does not see the trace context, while Java SDK 1.18.0 starts an
`activity:<name>` span; the live run should say which is right.
