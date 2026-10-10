# Observability Phase 2a — live evidence

Change: `observability-orchestrator-tracing`  
Authoring date: 2026-10-06 (unit/render tests) · **Live run: 2026-10-08** (this file's live sections)  
Built from: branch `feat/observability-orchestrator-tracing` at `ab87b6f`  
Namespace: `dws-system` · Workflow: `tracedemo` · Backend: Jaeger 1.62.0 (all-in-one)

**Result in one line:** the instrumented orchestrator was injected and identified as designed, and
Dapr Workflow replay is clean for the span model — but only after working around an unrelated
defect in `InterpreterWorkflow` that makes replay non-deterministic (see
[Run 1 vs runs 2–4](#run-1-vs-runs-24)). Per-step-call spans do **not** appear (Track B).

Read the [deviations](#deviations-from-the-intended-procedure) before treating any of this as a
production-shaped run.

## Traces

| Run | Instance | Orchestrator image | What was done | Trace ID | Spans |
|---|---|---|---|---|---|
| 1 | `run-1` | branch HEAD, unmodified | started; pod killed mid-timer | `5f94c8e6352bbf63e23219291f0489e7` | 83 |
| 2 | `run-2` | HEAD + local guard (not committed) | started, no restart | `830a2bceca74cbb6d5233e878a94b199` | 51 |
| 3 | `run-3` | HEAD + local guard (not committed) | started; **orchestrator pod deleted at 16:03:06, mid 45 s timer**; replacement pod finished it | `cc16b7c4170ca17927986d3bb3a628a4` | 51 |
| 4 | `run-4` | HEAD + local guard (not committed) | started with an inbound `traceparent` (`0af7651916cd43dd8448eb211c80319c`) | `634fbcd8c5eb05ac74721695efec227a` | 51 |

The workflow (`tracedemo`): `call: http` (`fetch`) → `wait: 45 s` (`pause`) → `call: http`
(`fetchAgain`). Runs 2–4 reached `COMPLETED` with output `{"ok":true}`; the lifecycle events arrived
in order (`started → fetch → pause → fetchAgain → completed`).

## Admission and identity checks (procedure step 5)

| Check | Result |
|---|---|
| Store key | `observability.enabled` = `true` set in the `dws-controller-config` Redis store |
| `dws-tracing` | present; `spec` is `tracing` only: `endpointAddress: dws-otel-collector:4318`, `isSecure: false`, `protocol: http`, `samplingRate: "1"` |
| Orchestrator annotations | `dapr.io/config: dws-tracing`, `instrumentation.opentelemetry.io/inject-java: "true"`, `instrumentation.opentelemetry.io/container-names: orchestrator` |
| Injection target | one init container, `opentelemetry-auto-instrumentation-java`; `JAVA_TOOL_OPTIONS=-javaagent:…` on `orchestrator`; **no** `OTEL_*`/`JAVA_TOOL_OPTIONS` on `daprd`, which has no init container |
| `daprd` config | started with `--config dws-tracing` |
| Identity env (as stamped by the controller, kept by the Operator) | `OTEL_SERVICE_NAME=tracedemo`; `OTEL_RESOURCE_ATTRIBUTES=dws.workflow.name=tracedemo,dws.workflow.version=v48c11a13,` followed by the Operator's `k8s.*` / `service.*` attributes |
| Jaeger service list | `dws-controller`, `tracedemo` (+ Jaeger's own). The service is **`tracedemo` (the app ID), not `tracedemo-v48c11a13`** — the Operator kept the container-defined `OTEL_SERVICE_NAME`, confirming the ADR 0005 addendum live |
| Agent and `daprd` spans | both report `service.name=tracedemo`; they appear as one service |
| Sampling | `parentbased_traceidratio`, argument `1` for this run. The chart default `0.1` was not exercised |

## Replay probe (procedure steps 6–7)

Runs 2, 3 and 4 produced **identical span structure** (51 spans each): one root
`create_orchestration||tracedemo`, one `orchestration||tracedemo`, one `timer`, and eight
`AdminEventActivity` executions.

| Observation | Result | Clean? |
|---|---|---|
| Workflow root span ID across replays | one root per trace; `run-3`'s trace holds spans emitted by **both** the killed pod (`…mfm9g`) and its replacement (`…ktdcf`) under the same root | **Clean** |
| `activity||<name>` span count per execution | `run-2/3/4` each have eight, with `durabletask.task.task_id` `[0,1,3,4,6,7,9,10]` — identical in all three and **unique within each trace**, so no execution is repeated | **Clean** |
| Trace ID across a restart | one trace ID for the whole of `run-3`, 16:02:56 → 16:03:41, spanning the pod kill | **Clean** |
| `timer` spans | exactly one per 45 s wait | **Clean** |
| Caller context → workflow | `run-4` supplied `traceparent`; the Dapr HTTP start span (`/v1.0/workflows/dapr/tracedemo/start`) joined the supplied trace (1 span), but `create_orchestration` and every later span are in a **separate** trace. The workflow does not inherit the caller's trace | **Not linked** — recorded, not designed around |
| Context inside activity code | the tree is `activity||X` (daprd) → `activity:X` (Java SDK, agent) → `Dapr/PublishEvent` (agent) → `PublishEvent` (daprd). The activity code *does* run under a span context, contrary to the Dapr documentation's statement | Observation |

Compact tree for the first activity of `run-3` (not a raw Jaeger export):

```text
create_orchestration||tracedemo
  orchestration||tracedemo
    activity||…AdminEventActivity            (daprd)
      activity:…AdminEventActivity           (Java SDK, agent)
        dapr.proto.runtime.v1.Dapr/PublishEvent        (agent)
          /dapr.proto.runtime.v1.Dapr/PublishEvent     (daprd)
        TaskHubSidecarService/CompleteActivityTask     (agent)
          /TaskHubSidecarService/CompleteActivityTask  (daprd)
```

## Not met: one client-side span per step call

The `fetch`/`fetchAgain` calls are remote activities (`Run`) executed by the step apps. Their
sidecars run the default Dapr configuration, which has no tracing, and the step code is not
instrumented. **No span for either step call appears in any trace.** The procedure's expectation of
"one client-side span per step call" is not satisfied by Phase 2a; that visibility is Track B
(Phase 2b / 3), as the procedure already stated. There is no span inside the step pods either.

## Run 1 vs runs 2–4

Run 1 used the orchestrator exactly as committed. It did not behave like the later runs:

- 83 spans (not 51), 13 `AdminEventActivity` executions (not 8), and **three** `timer` spans with
  task IDs 5, 6 and 7 for a single wait.
- the Java SDK logged `Discarding a potentially duplicate TaskCompleted event with ID = 7/8/9` on
  every replay;
- the lifecycle stream carried `io.dws.task.failed` and `io.dws.instance.failed` events whose error
  was `The orchestrator is blocked and waiting for new inputs. This Throwable should never be caught
  by user code.`;
- after the timer fired the instance stayed `RUNNING` indefinitely; no `fetchAgain` activity was
  ever scheduled.

Cause (source-confirmed, then confirmed by the fix): `InterpreterWorkflow` has
`catch (RuntimeException e)` blocks (lines 98, 201, 623 at `ab87b6f`) that call `publish(…)` — a
`ctx.callActivity` — before rethrowing. The Dapr Java SDK signals "waiting for a result" with
`OrchestratorBlockedException`, a `RuntimeException`. The catch block therefore schedules extra
activities during the blocked unwind, so the first execution and every replay allocate task IDs in
a different order. That is workflow non-determinism, and it is what produced the extra spans and the
stall.

Runs 2–4 used the same code with a three-line guard that rethrows `OrchestratorBlockedException`
first. The guard exists only in a scratch copy used to build the probe image; **it is not in this
repository.** It removed the failure events, the duplicate warnings and the stall in one step.

This is not a tracing problem and is out of scope here, but it means a replay probe on the committed
orchestrator cannot be called clean. It is recorded in
[ADR 0009](../adr/0009-trace-context-across-workflow-replay.md) and below.

## Other defects found while getting the workflow to run

All were hit on an unmodified branch and none concerns tracing. Each was worked around **in the
live cluster only**; the repository is unchanged.

| # | Symptom | Cause (as observed) | Live workaround |
|---|---|---|---|
| 1 | `POST /workflows` → HTTP 500 | The controller creates `workflowaccesspolicies.dapr.io`; neither `charts/dws/templates/controller/rbac.yaml` nor `dws-controller/k8s/controller-rbac.yaml` grants it | added the verbs to the live Role |
| 2 | orchestrator `daprd` crash-loops: `failed to sync informer cache for ConfigMap "dws-def-…"` | the `configuration.kubernetes` definition store reads a ConfigMap as the pod's service account (`default`); I found no grant for it in the chart or the standalone manifests | Role + RoleBinding for `default` |
| 3 | orchestrator logs `configuration store dws-definitions not found` | the controller stamps `DEFINITION_STORE=dws-def-<wf>-<ver>`; the orchestrator reads `DAPR_CONFIG_STORE` (default `dws-definitions`) | set `DAPR_CONFIG_STORE` on the Deployment |
| 4 | start → `the state store is not configured to use the actor runtime` | `dws-actor-statestore` is scoped to `dws-orchestrator`; the compiled orchestrator's app ID is the workflow name and the step apps are workflow workers too | widened the Component `scopes` |
| 5 | `task.failed` / `instance.failed` noise, duplicate-event warnings, stall after the first await | the `OrchestratorBlockedException` catch described above | the local guard (runs 2–4 only) |

## Deviations from the intended procedure

The authoring sandbox for this run had Docker and `kind`, but not the network or kernel
capabilities the procedure assumes. Everything below is a substitution, not an equivalent:

- **Cluster:** `kind` v0.32.0, Kubernetes v1.36.1, on a cgroup v1 host. The kubelet needed
  `failCgroupV1: false`. The sandbox lacks `CAP_SYS_RESOURCE`, so runc could not apply the negative
  `oomScoreAdj` kubelet requests; the node image wraps `runc` to drop that field. Neither affects
  tracing.
- **Registry access:** `ghcr.io` blob downloads, `quay.io`, `registry.k8s.io` and the OTel/Jetstack
  Helm repositories were blocked by egress policy. So:
  - **OTel Operator 0.159.0 was built from source** (tag `v0.159.0`), not pulled. Chart `0.123.0`
    was taken from its git tag. **cert-manager was not used**; the chart's `autoGenerateCert` issued
    the webhook certificate. The pin and injection behaviour are the intended ones; the certificate
    path is not.
  - The Java agent image `autoinstrumentation-java:2.31.1` was rebuilt locally from the agent jar on
    Maven Central (same version the Operator pins), tagged with the Operator's default name.
  - Dapr 1.18.1 images came from `docker.io/daprio/*` (the chart default is `ghcr.io/dapr`).
- **Images built locally from the branch:** `dws-controller`, `dws-orchestrator` and `dws-call-http`
  (the controller image needed a different base and a copied `node` binary because the repo
  Dockerfile's base image is on a blocked registry).
- **Receiver:** Jaeger all-in-one directly, exposed as Service `dws-otel-collector`, instead of the
  Phase 1 Collector fixture. Jaeger rejects OTLP logs and metrics with 404; that noise is expected.
- **Knative Serving was not installed** (CRDs only). The two step Services therefore had no pods. I
  ran each as a plain Deployment using the pod template the controller generated, with the real
  `dws-call-http` code built from this repository and a small HTTP target answering
  `example.local`. The step calls succeeded; this is a stand-in for Knative, not a Knative run.
- **Admin, console and Postgres were disabled**; the admin projection path was not exercised.
- **Not exercised:** controller-initiated instance start (the controller has no start endpoint, so
  instances were started through the orchestrator's Dapr API), activity retries, `fork`/scope child
  workflows, `ContinueAsNew`, more than one restart, more than one replica, and chart-default
  sampling (`0.1`).

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

Image check: `dws-orchestrator/Dockerfile` runs `java -jar` on `eclipse-temurin:25-jre` — a real
JVM. The live run confirmed the agent (2.31.1) attaches on Temurin 25 / Spring Boot 4.1.0.

## Reproducing the probe

1. Cluster with the OTel Operator (chart `0.123.0` / operator `0.159.0`), cert-manager, Dapr, and an
   OTLP receiver plus Jaeger, as in the Phase 1 evidence run.
2. Build `dws-orchestrator` and point `controller.images.orchestrator` at it.
3. Install the chart with `observability.enabled=true`, an OTLP endpoint reaching the receiver, and
   `observability.traces.samplingRate=1` for the run.
4. Set `observability.enabled` to `true` in the `dws-controller-config` store; confirm
   `kubectl get configuration dws-tracing`.
5. Deploy a workflow with call steps and a `wait`. Confirm the Java init container is on
   `orchestrator` only, `dapr.io/config=dws-tracing`, and `OTEL_SERVICE_NAME` equals the workflow
   name.
6. Start an instance and delete the orchestrator pod during the wait.
7. In Jaeger record the trace ID, the service list, and the span counts per activity.

On the committed orchestrator step 6 currently exposes defect 5 above; fix it first or the result
will not describe tracing.
