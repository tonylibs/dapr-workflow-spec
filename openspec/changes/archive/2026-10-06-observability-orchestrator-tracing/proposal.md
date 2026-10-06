## Why

Phase 1 made `dws-controller` and `dws-admin` emit traces, but the pod `dws-controller` creates for every deployed workflow, `dws-orchestrator`, still emits nothing. That pod is where Dapr Workflow runs and where an operator debugs a customer workflow. Phase 2a instruments it so one trace runs from the controller through the orchestrator to each step call, with the orchestrator named after the workflow it runs.

## What Changes

- `dws-controller` reads two observability keys from the existing `dws-controller-config` store at deploy time and, when observability is on, stamps the orchestrator Deployment with targeted Java auto-injection (`inject-java`, `container-names: orchestrator`), a `dapr.io/config: dws-tracing` sidecar reference, and workflow identity env (`OTEL_SERVICE_NAME` = Dapr app ID; `OTEL_RESOURCE_ATTRIBUTES` = `dws.workflow.name`, `dws.workflow.version`).
- Absent flag, flag off, unreachable store, or a missing `dws-tracing` Configuration all produce today's orchestrator Deployment unchanged; none can fail or noticeably slow a deploy.
- `charts/dws` renders one standalone, tracing-only Dapr `Configuration` named `dws-tracing` (reusing Phase 1's `dws.observability.daprTracing` helper, so the endpoint/protocol translation is not copied) when `observability.enabled` and `observability.workflows.enabled` are true.
- The chart Role and the raw controller RBAC manifest gain `get` on `configurations.dapr.io` so the controller can verify `dws-tracing` exists before referencing it.
- Documentation: the store keys, the next-deploy-only semantics, and an ADR 0005 addendum for the `OTEL_SERVICE_NAME` finding. A live evidence file records what could and could not be exercised.

## Capabilities

### New Capabilities

- `workflow-orchestrator-tracing`: Controller behavior that reads the observability keys and instruments the compiled orchestrator pod (flag handling, annotations, identity env, safe fallback, sampling ownership).
- `helm-workflow-tracing-configuration`: The chart-rendered `dws-tracing` Dapr Configuration, its gating, its tracing-only content, and the controller RBAC needed to verify it.

### Modified Capabilities

None. The Phase 1 `helm-observability` capability is still inside the unarchived `observability-template` change and has no base spec to modify; this change adds new capabilities instead of editing that change.

## Impact

- **Components:** `dws-controller` (Java/Quarkus) and `charts/dws` (Helm). `dws-orchestrator` is **not** modified; its image is already a real JVM (`eclipse-temurin:25-jre`).
- **Deployed behavior:** orchestrator Deployment annotations and container env change only when observability is on. One new chart resource (`dws-tracing`) renders only with observability on. Two RBAC rules added.
- **Runtime behavior:** the orchestrator JVM gets the OTel Java agent through the Operator webhook; DSL interpretation is unchanged.
- **Cross-component contract:** store keys `observability.enabled` and `observability.instrumentation`; Configuration name `dws-tracing` in the workflow namespace; orchestrator container name `orchestrator`.
- **Compatibility:** existing workflow definitions and versions are unaffected; definition content hashing is unchanged. Changing a flag affects a workflow only on its next deploy (already-deployed stacks are not retro-fitted).

## Non-goals

- `dws-flow` and `dws-step` (nothing deploys them yet; blocked on runtime-v2 Phase 4); Knative step services and the Go SDK (Track B); logs; domain metrics; the bundled Collector; routing step egress through Dapr; the admin's missing Postgres client span.
- Any span inside step pods. The trace shows the client-side span for each step call from the orchestrator and stops there (Track B).
- Redesigning the span model if Dapr Workflow replay misbehaves; that case is a stop-and-report.
- Edits to the roadmaps, the tracker, or Notion.
