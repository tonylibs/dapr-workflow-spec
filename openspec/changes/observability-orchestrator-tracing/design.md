## Context

Phase 1 (`observability-template`) instrumented the chart-managed control plane. The compiled orchestrator is created by `dws-controller` (`StackSynthesizer.orchestratorDeployment`), has container `orchestrator`, and gets only `dapr.io/enabled`, `dapr.io/app-id`, `dapr.io/app-port` today. Its Dapr app ID is the workflow name; its Deployment name is `<workflow>-<versionId>`. `DeploymentPlan` exposes `workflow()`, `versionId()` and `version()`.

Facts that shape the design (verified against source; see `brainstorm.md`):

- Phase 1 added **no** keys to `dws-controller-config`, and its Dapr-tracing translation (`dws.observability.daprTracing`) exists only as Helm templating.
- `dapr.io/config` is single-valued, and a referenced-but-missing Configuration crashloops daprd.
- The OTel Operator 0.159.0 injects `OTEL_SERVICE_NAME` (from the Deployment name) unless the container already defines it, and `OTEL_SERVICE_NAME` outranks a `service.name` resource attribute. It appends to a container-supplied `OTEL_RESOURCE_ATTRIBUTES`.
- daprd's sampler is `ParentBased(TraceIDRatioBased(rate))`.
- The orchestrator image is a real JVM; the agent bundled with the Operator (2.31.1) supports Temurin 25 and Spring Boot 4.
- The chart Role has no `configurations` verb.

## Goals / Non-Goals

**Goals:**

- Off is truly off: the orchestrator Deployment is equal to today's with a missing flag, an off flag, an unreachable store, or an unusable `dws-tracing`.
- On means instrumented: agent injected into the `orchestrator` container only, sidecar pointed at a tracing-only Configuration, identity attributes correct.
- One sampling root (the agent); the sidecar pinned to `"1"`.
- No second copy of the endpoint/protocol translation.

**Non-Goals:**

- Anything listed under the proposal's Non-goals. No node attributes (`dws.node.id` / `dws.node.kind`): the orchestrator is not a compiled node.

## Decisions

### D1: The sidecar Configuration is a chart-rendered standalone `dws-tracing`

- **Choice:** the chart renders one Dapr `Configuration` named `dws-tracing` containing only `spec.tracing`, built by the existing `dws.observability.daprTracing` helper, when `observability.enabled` and `observability.workflows.enabled`. The controller stamps `dapr.io/config: dws-tracing`.
- **Why:** it exists at install time (before any compiled pod); it holds no pipeline so it cannot touch auth or any other sidecar; it reuses Phase 1's translation by calling the same helper; the fixed name matches the roadmap and the existing fixed `dws-controller-config`.
- **Alternatives rejected:**
  - *Controller synthesizes a per-workflow Configuration:* needs a Java copy of the endpoint/protocol translation (forbidden), the endpoint and protocol in the store, extra RBAC verbs and an apply-order dependency.
  - *Reuse the controller's own `<fullname>-config`:* with auth on it carries `appHttpPipeline`, which would apply bearer middleware to the orchestrator sidecar.
  - *Hook-created Configuration:* adds ordering risk with no benefit over a plain resource.
- **Merge rule:** nothing else gives the orchestrator a Dapr Configuration today. The synthesizer stamps `dapr.io/config` with put-if-absent semantics and never overwrites an existing value; if one exists it leaves the pod without tracing and logs it. A later change that adds an orchestrator Configuration must fold `spec.tracing` into it (as Phase 1 did for the controller and admin). `dapr.io/config` takes exactly one name, so a comma list is not a merge.

### D2: Two store keys, read once per deploy, defaulting to off

- **Choice:** `observability.enabled` (`true`, trimmed and case-insensitive, turns it on; everything else is off) and `observability.instrumentation` (optional; default `"true"`; `<name>` or `<ns>/<name>` as the Operator accepts). Both are fetched in a single `getConfiguration(store, keys)` call with a short bounded timeout (1 s) at deploy time, in `StackApplier`, not at startup.
- **Why:** reuses the established store and defaulting discipline (ADR 0002) with no new env vars. Deploy-time reads make "next deploy only" literal. One bounded round trip means an unreachable store costs at most the timeout and never fails the deploy. `CompilerProducer` reads with no timeout; that pre-existing behavior is left alone.
- **Alternatives rejected:** controller env vars (forbidden); the OTLP endpoint/protocol in the store (needs a Java translation copy); `Dapr subscribeConfiguration` live flips (changes semantics, not needed).
- **Consequence (stated per the handoff):** changing a flag affects a workflow only on its next deploy. Already-deployed orchestrators are not retro-fitted. Each version redeploys its whole pod set anyway.

### D3: Verify `dws-tracing` exists before stamping

- **Choice:** when the store says on, the controller does one Kubernetes GET for `configurations.dapr.io/dws-tracing` in the workflow namespace. Present -> instrument. Absent, forbidden, or any error -> render exactly as today and log one WARN naming the cause.
- **Why:** a referenced-but-missing Configuration crashloops daprd, so a half-configured install (store on, chart off) would break the deploy. This keeps "never fail a deploy" true and "Configuration exists before the pod needs it" enforced rather than hoped for.
- **Cost:** `get` on `configurations.dapr.io` in the chart Role and `dws-controller/k8s/controller-rbac.yaml`. Note: the Role also lacks verbs for configurations create/delete that the oauth path already needs; that pre-existing gap is out of scope and is not widened here beyond `get`.
- **Alternative rejected:** trust the flag blindly.

### D4: Workflow identity via `OTEL_SERVICE_NAME` plus `OTEL_RESOURCE_ATTRIBUTES`

- **Choice:** on the `orchestrator` container: `OTEL_SERVICE_NAME=<dapr app id>` and `OTEL_RESOURCE_ATTRIBUTES=dws.workflow.name=<workflow>,dws.workflow.version=<versionId>`. Values are percent-encoded for `,` and `=` per the SDK resource spec. Existing `OTEL_RESOURCE_ATTRIBUTES` is appended to, not replaced.
- **Why:** the Operator would otherwise inject `OTEL_SERVICE_NAME=<workflow>-<versionId>` and win over a `service.name` resource attribute, so the service list would not match the workflow the operator deployed. This departs from ADR 0005 Decision 1's literal wording (`service.name` inside `OTEL_RESOURCE_ATTRIBUTES`); the ADR gets an addendum.
- **Alternative rejected:** `resource.opentelemetry.io/service.name` pod annotation: works, but env on the container keeps identity beside the other identity env and is directly assertable in the Deployment spec.
- `dws.workflow.version` is the content-addressed `versionId` (`vXXXXXXXX`).

### D5: `StackSynthesizer` stays pure; the flag read lives beside `StackApplier`

- **Choice:** the synthesizer receives an `ObservabilitySettings` value whose default `OFF` takes the unmodified code path. A separate CDI bean resolves settings (store read + Configuration existence check) and `StackApplier` passes them in.
- **Why:** off-equals-today is then provable by equality in a plain unit test, and the stamping logic is testable without a cluster or mock Dapr client.
- **Alternative rejected:** reading the store inside the synthesizer (makes a pure compiler impure and the off path harder to prove).

### D6: Sampling

- The agent is the sampling root via the shared Instrumentation CR (`parentbased_traceidratio`, Phase 1). `dws-tracing` pins `samplingRate: "1"` through the shared helper. No sampling key is read from the store and no second sampler is introduced.

### D7: Replay is observed, not designed

- The replay finding needs a live run. If replay is clean, an ADR is written from the evidence; if spans duplicate or traces restart, work stops and reports. This change does not alter the orchestrator or its span model.

## Risks / Trade-offs

- [Store on but chart off] -> D3 existence check falls back to off with a WARN.
- [Operator not installed / agent not injected] -> pod runs untraced exactly as before; the Phase 1 preflight already covers the chart side.
- [Multiple Instrumentation CRs in a namespace with default `"true"`] -> operators set `observability.instrumentation` to a named CR; documented.
- [Per-deploy store read adds latency when the store is unreachable] -> 1 s bound; accepted.
- [Java agent / daprd interplay: gRPC `traceparent` from the agent and from the Dapr SDK interceptor, and workflow root-span linkage on SDK 1.18.0, are unverified] -> exactly what the live replay probe records; not designed around here.
- [Controller RBAC lacks `configurations` verbs] -> `get` added; if still forbidden at runtime the D3 fallback keeps deploys working.
- [Documented: service.name departs from ADR 0005 wording] -> ADR addendum.

## Migration Plan

1. Upgrade the chart (adds `dws-tracing` only if observability is on; adds the RBAC `get`). Default render is byte-identical to the Phase 1 baseline.
2. Roll out the controller image containing the reader and stamping. With keys unset nothing changes.
3. To enable: set `observability.enabled=true` in the chart, then set `observability.enabled` to `true` in `dws-controller-config` (and optionally `observability.instrumentation`). The next deploy of each workflow gets the instrumented orchestrator.
4. Rollback: set the store key to `false` or delete it; the next deploy of each workflow reverts. No data migration.

## Open Questions

- Live replay behavior and SDK 1.18.0 root-span linkage: pending a cluster run (not possible in the authoring environment; see `docs/roadmaps/observability-phase2a-evidence.md`).
