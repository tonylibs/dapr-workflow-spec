---
type: Event Integration
title: DWS lifecycle events
description: Shared Dapr pub/sub contract for advisory controller deployment and orchestrator instance/task lifecycle events.
tags: [dws, dapr, pubsub, events, observability]
---

# DWS lifecycle events

DWS publishes an advisory lifecycle stream so [DWS admin](admin-read-model.md) can observe platform activity without coupling the controller or orchestrator to the query service. The controller emits definition and deployment events; the orchestrator emits instance and task events while executing the [deployed workflow](../architecture/deployed-workflow.md).

All event types, payload examples, and transport details have a canonical source in `docs/events.md`. The source code implements that contract in `dws-controller/.../events/EventPublisher.java` and `dws-orchestrator/.../workflow/AdminEventBuilder.java`.

## Transport and delivery boundary

The documented binding is Dapr pub/sub component `pubsub` and topic `dws.events`. A Component with that name and the topic available must be installed in the target cluster; neither component's `k8s/` manifests provisions it. Dapr adds its own transport CloudEvent around the bytes it publishes; the DWS payload is a separate CloudEvents-style JSON envelope with `id`, `source`, `type`, `time`, `datacontenttype`, and `data`.

Publishing is best effort and does not add an outbox or persistence layer:

- Controller event publication catches and logs failures, so event failure does not make a stack apply fail (`EventPublisher.publish`).
- Orchestrator lifecycle events are sent through `AdminEventActivity`, which catches and logs publish failures so telemetry cannot fail a workflow instance.
- Ordinary DSL `emit` tasks are different: they use `EmitEventActivity` and its normal retry/failure behavior. They publish workflow data to the task-derived topic rather than this lifecycle envelope.

The orchestrator defaults `DWS_DEFAULT_PUBSUB` to `pubsub` (`dws-orchestrator/src/main/resources/application.yaml`); `AdminEventBuilder` currently uses that configured component for lifecycle events while retaining the fixed `dws.events` topic. Consumers should therefore rely on the documented default binding and verify deployment configuration when it is overridden.

## Controller events

`StackApplier` emits lifecycle events around materialization and rollout of a deployment plan:

| Type | Emitted when |
|---|---|
| `io.dws.definition.created` | A previously absent immutable definition ConfigMap is created. |
| `io.dws.definition.updated` | The requested immutable definition version was already present. |
| `io.dws.deployment.applied` | Resource application and rollout return successfully. |
| `io.dws.deployment.failed` | `apply` catches a `RuntimeException`; it then rethrows the original failure. |
| `io.dws.deployment.drained` | A superseded orchestrator Deployment receives the drain annotation. |
| `io.dws.deployment.collected` | A drained version reports zero replicas and its label-selected resources are deleted. |

The controller uses source `dws-controller`, a UUID event ID, and wall-clock timestamps because its apply process is not replayed. `deployment.applied` and `deployment.failed` include workflow/version, step-service names, and orchestrator app ID; the failure form adds an error. See `dws-controller/src/main/java/io/dws/controller/k8s/StackApplier.java` and `events/EventEnvelope.java`.

## Orchestrator events

The interpreter schedules lifecycle publishing as Dapr workflow activities before and after each task and around the full instance:

| Type | Emitted when |
|---|---|
| `io.dws.instance.started` | Before interpreter task execution begins. |
| `io.dws.instance.completed` | The interpreter reaches a normal terminal path. |
| `io.dws.instance.failed` | A runtime error terminates the interpreter; the error remains observable to the caller. |
| `io.dws.task.started` | Immediately before dispatching a task. |
| `io.dws.task.completed` | After task dispatch succeeds. |
| `io.dws.task.failed` | Task dispatch throws a runtime exception. |

`AdminEventBuilder` derives its event IDs from `<instanceId>-<sequence>` and timestamps from `WorkflowContext.getCurrentInstant()`. Delegating publication to `AdminEventActivity` rather than directly calling Dapr from workflow code is designed to make replay use deterministic envelopes rather than new UUIDs or wall-clock times. The source is `dws-orchestrator/<appId>`; the version is extracted from the immutable definition key. The current replay limitation below qualifies that design intent; the activity boundary remains part of the runtime flow described in [deployed workflow](../architecture/deployed-workflow.md#interpreter-conventions).

### Replay tracing and current limitation

The controller can opt a newly deployed orchestrator into Java-agent and Dapr-sidecar tracing through the configuration boundary described in [Helm chart packaging](../architecture/helm-chart-roadmap.md#control-plane-observability). Live evidence in `docs/adr/0009-trace-context-across-workflow-replay.md` found that the Dapr engine owns the workflow-level spans and that activity publication runs under a child span; creating spans in workflow code would therefore risk replay duplicates. With a local probe-only guard, a replacement pod continued the same workflow trace after a restart without repeating activity executions.

That guard is not committed. The current `InterpreterWorkflow` catches the Dapr blocked-workflow runtime exception around awaited execution and schedules failed lifecycle publication before rethrowing. This can make replay non-deterministic: the live probe recorded spurious `task.failed` and `instance.failed` events, duplicate completion warnings, and a stalled instance. Treat it as a workflow correctness defect, not an event-delivery guarantee or a tracing issue. Any change to the interpreter's catch paths or lifecycle scheduling must verify replay as well as the ordinary event tests.

## Change and verification guide

When changing event types, payloads, source conventions, topic/component behavior, or failure tolerance:

1. update `docs/events.md` as the cross-component contract;
2. update both publishers if the contract changes across the deployment/runtime boundary; and
3. run `cd dws-controller && ./mvnw test` and `cd dws-orchestrator && ./mvnw verify`.

Current focused coverage includes controller envelope/publisher tests and `StackApplier` failure/success wiring, plus orchestrator event-builder and interpreter scheduling tests. There is no in-repository end-to-end test against a real Dapr pub/sub component, so changes to cluster provisioning or delivery guarantees require integration validation outside these unit-level checks.
