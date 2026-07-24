## Why

A future `dws-admin` service needs a live read model of what the platform is doing — which
definitions exist, which deployments succeeded or failed, and which workflow instances and tasks
are running — but today neither `dws-controller` nor `dws-orchestrator` emits any lifecycle signal
a reader could subscribe to. Both components already talk to Dapr (`dws-controller` deploys Dapr
resources; `dws-orchestrator` already publishes DSL `emit` events over pub/sub), so a shared
lifecycle-event stream on Dapr pub/sub is the natural, persistence-free way to feed that read model
without coupling either publisher to the reader. This change builds the publishers and the shared
event contract only — not `dws-admin` itself.

## What Changes

- Define a **shared event contract** (CloudEvents-style envelope + per-type payloads) documented at
  repo root in a new `docs/events.md`, since it spans two independently-built components.
- Publish all events to a single topic `dws.events` on the existing Dapr pub/sub component `pubsub`
  (the same component `dws.default-pubsub` already names for `emit`).
- **`dws-controller`** gains a fire-and-forget event publisher (new package `io.dws.controller.events`)
  wired into the apply pass (`StackApplier`), emitting:
  - `io.dws.definition.created` / `io.dws.definition.updated`
  - `io.dws.deployment.applied` / `io.dws.deployment.failed`
  - `io.dws.deployment.drained` / `io.dws.deployment.collected`
- **`dws-orchestrator`** gains a new Dapr workflow activity (`AdminEventActivity`, mirroring
  `EmitEventActivity`) called from `InterpreterWorkflow` at instance and task boundaries, emitting:
  - `io.dws.instance.started` / `io.dws.instance.completed` / `io.dws.instance.failed`
  - `io.dws.task.started` / `io.dws.task.completed` / `io.dws.task.failed`
- Add the Dapr Java SDK (`io.dapr:dapr-sdk`) to `dws-controller` (not currently a dependency).
- Document that a Dapr pub/sub `Component` named `pubsub` carrying topic `dws.events` is a
  **deployment prerequisite** — neither component's `k8s/` manifests provision it.
- Event publishing is **additive only**: no change to existing DSL semantics
  (`call`/`switch`/`set`/`wait`/`listen`/`emit`) and no new persistence in either component.
- **Out of scope**: the `dws-admin` service, any subscriber, and any read-model storage.

## Capabilities

### New Capabilities
- `lifecycle-events`: The shared event contract — the `dws.events` topic, the CloudEvents-style
  envelope, and the full catalog of event types and payloads that both publishers must honor and a
  future consumer can rely on. This is the cross-component contract.
- `controller-event-publishing`: `dws-controller`'s obligation to publish definition and deployment
  lifecycle events from the apply pass as a fire-and-forget side effect that never alters apply
  behavior or the "cluster is source of truth" invariant.
- `orchestrator-event-publishing`: `dws-orchestrator`'s obligation to publish instance and task
  lifecycle events from the interpreter, strictly through a Dapr activity so the workflow method
  stays deterministic under replay.

### Modified Capabilities
<!-- None. No existing spec under openspec/specs/ changes its requirements; event publishing is additive. -->

## Impact

- **New files**: `docs/events.md`; `io.dws.controller.events.*` (publisher + envelope + Dapr client
  producer) in `dws-controller`; `AdminEventActivity` + request record in
  `io.dws.orchestrator.workflow.activity`.
- **Modified code**: `dws-controller/pom.xml` (add `dapr-sdk` + a test-mock dependency),
  `StackApplier` (publish call sites), `WorkflowResource`/apply path unchanged in signature;
  `dws-orchestrator` `InterpreterWorkflow` (instance/task publish call sites),
  `WorkflowSupport`/`OrchestratorProperties`/`WorkflowRuntimeBootstrap` (surface `appId`, seed the
  activity, register it), `application.yaml` (admin-events topic + app-id binding).
- **Dependencies**: `io.dapr:dapr-sdk` added to `dws-controller`; orchestrator already has the SDK.
- **Deployment**: requires an in-cluster Dapr pub/sub `Component` `pubsub` with topic `dws.events`.
- **Runtime**: one extra Dapr pub/sub publish per lifecycle transition; publishes are best-effort and
  must not fail the apply pass or the workflow instance.
