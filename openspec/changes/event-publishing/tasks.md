# Tasks: event-publishing (Epic 1)

Maps to scope items S1.1–S1.7. Do S1 first (contract), then controller (S1.2–S1.4),
then orchestrator (S1.5–S1.6), then the prerequisite doc (S1.7).

## 1. Shared event contract — `docs/events.md` (S1.1)

- [x] 1.1 Create `docs/events.md` at repo root describing the CloudEvents-style envelope
  (`id`, `source`, `type`, `time`, `datacontenttype=application/json`, `data`).
- [x] 1.2 Document the transport binding: component `pubsub`, topic `dws.events`; state the
  `source` convention (`dws-controller`, `dws-orchestrator/<appId>`).
- [x] 1.3 Document every event type + payload: `io.dws.definition.created|updated`
  `{workflow, version, createdAt}`; `io.dws.deployment.applied|failed`
  `{workflow, version, stepServices[], orchestratorAppId, error?}`;
  `io.dws.deployment.drained|collected` `{workflow, version, orchestratorAppId}`;
  `io.dws.instance.started|completed|failed`
  `{instanceId, workflow, version, appId, startedAt, endedAt?, error?}`;
  `io.dws.task.started|completed|failed` `{instanceId, taskName, taskType, timestamp, error?}`.

## 2. dws-controller — dependencies & Dapr client (S1.2)

- [x] 2.1 Add `io.dapr:dapr-sdk` to `dws-controller/pom.xml` (version-managed to `1.18.0`, matching
  the orchestrator); add `io.quarkus:quarkus-junit5-mockito` as a test dependency.
- [x] 2.2 Add `io.dws.controller.events.DaprClientProducer`: `@ApplicationScoped` CDI producer for
  `DaprClient` with a disposer/`close()`, mirroring the orchestrator's `daprClient()` bean.

## 3. dws-controller — publisher (S1.3)

- [x] 3.1 Add `io.dws.controller.events.EventEnvelope` (record) that builds the CloudEvents-style
  map from `type` + `data` + `source`, using `Instant.now()` at the controller boundary (controller
  is not replay-constrained) and a generated `id`.
- [x] 3.2 Add `io.dws.controller.events.EventPublisher` (`@ApplicationScoped`, constructor-injects
  `DaprClient` + `DwsConfig`) with typed methods `definitionCreated/Updated`,
  `deploymentApplied/Failed`, `deploymentDrained/Collected`; each assembles the envelope and calls
  `publishEvent("pubsub", "dws.events", envelope)`.
- [x] 3.3 Make every publish fire-and-forget: catch and log any exception so publishing never
  propagates out of `EventPublisher`.

## 4. dws-controller — wire into StackApplier (S1.4)

- [x] 4.1 Constructor-inject `EventPublisher` into `StackApplier`.
- [x] 4.2 In `apply`: publish `definition.created` when `!alreadyDeployed`, else
  `definition.updated`; publish `deployment.applied` at the end (with `plan.steps()` names +
  `plan.orchestrator().appId()`); wrap the body so any throw publishes `deployment.failed` (with
  `error`) then rethrows unchanged.
- [x] 4.3 In `markForDrain`: publish `deployment.drained` only when it actually annotates
  (skip when already draining).
- [x] 4.4 In `collectIfDrained`: publish `deployment.collected` after `deleteByLabels`; derive
  `workflow`/`version`/`orchestratorAppId` from the Deployment labels/name available on the
  reconcile path (no plan there).

## 5. dws-controller — tests

- [x] 5.1 Unit-test `EventEnvelope`/`EventPublisher` payload shape against a Mockito-mocked
  `DaprClient` (assert component, topic, `type`, and `data` fields for each event).
- [x] 5.2 Extend `WorkflowResourceTest`/`StackApplierTest` (`@QuarkusTest` +
  `@WithKubernetesTestServer`) with `@InjectMock DaprClient`; assert `deployment.applied` fires on a
  successful POST and `deployment.failed` on an apply error, and that a publish failure does not
  break the apply pass.
  <!-- Env note: quarkus-junit5-mockito jar is unavailable in this environment's Maven mirror, so
       the DaprClient mock is supplied via a quarkus @Mock CDI alternative (MockDaprClientProducer)
       and the apply-error path is covered by a fast unit test (StackApplierFailurePublishTest). -->
- [x] 5.3 `cd dws-controller && ./mvnw test` (or `mvnw.cmd`) is green.

## 6. dws-orchestrator — admin event activity (S1.5)

- [x] 6.1 Add `io.dws.orchestrator.workflow.activity.AdminEventRequest(pubsub, topic, JsonNode data)`
  record (mirror `EmitRequest`).
- [x] 6.2 Add `AdminEventActivity implements WorkflowActivity` that
  `WorkflowSupport.daprClient().publishEvent(...).block()` (mirror `EmitEventActivity`), tolerating
  publish failure so a failed admin publish never wedges the instance.
- [x] 6.3 Register `AdminEventActivity` in `WorkflowRuntimeBootstrap.startRuntime()`.

## 7. dws-orchestrator — config & support (S1.6)

- [x] 7.1 Add `appId` to `OrchestratorProperties` bound from the Dapr-injected `APP_ID`/`dapr.app-id`
  env (default to workflow name / definition key); expose in `application.yaml`.
- [x] 7.2 Add `appId` param to `WorkflowSupport.init(...)` + `WorkflowSupport.appId()`; seed it from
  `WorkflowRuntimeBootstrap` and update `InterpreterWorkflowIntegrationTest`'s `init(...)` call.
- [x] 7.3 Add an envelope/payload builder helper that splits `definitionKey` into `workflow`+`version`
  and stamps `time`/`id` from `ctx.getCurrentInstant()` + `ctx.getInstanceId()` (replay-safe).

## 8. dws-orchestrator — wire into InterpreterWorkflow (S1.6)

- [x] 8.1 Publish `instance.started` after reading input, before the loop, via
  `ctx.callActivity(AdminEventActivity.class.getName(), req, defaultTaskOptions(), Void.class).await()`.
- [x] 8.2 Publish `task.started` before each task-type dispatch and `task.completed` after; on a
  dispatch throw, publish `task.failed` then rethrow (`taskType` from the matched task kind).
- [x] 8.3 Publish `instance.completed` at both normal terminal points (off-the-end `ctx.complete`
  and the `END`/`EXIT` directive path); publish `instance.failed` on any error escaping the loop,
  then rethrow.
- [x] 8.4 Ensure no `Instant.now()`/`UUID.randomUUID()` is introduced inside `execute` — all
  time/ids come from the workflow context (replay determinism).

## 9. dws-orchestrator — tests

- [x] 9.1 Extend `InterpreterWorkflowIntegrationTest`: stub `ctx.getInstanceId()` and
  `ctx.getCurrentInstant()`; verify `AdminEventActivity` is scheduled with the right
  `AdminEventRequest` for `instance.started`, per-task `task.started`/`task.completed`, and
  `instance.completed` — in order.
- [x] 9.2 Add a case where a task dispatch throws and assert `task.failed` + `instance.failed` fire.
- [x] 9.3 Unit-test the envelope/payload builder (workflow/version split, deterministic time/id).
- [x] 9.4 `cd dws-orchestrator && ./mvnw test` (or `mvnw.cmd`) is green.

## 10. Deployment prerequisite doc (S1.7)

- [x] 10.1 In `docs/events.md` (and a pointer from the root `README.md`), document that a Dapr
  pub/sub `Component` named `pubsub` carrying topic `dws.events` must exist in-cluster before
  either component's publishing works, and that it is **not** provisioned by either component's
  `k8s/` manifests.

## 11. Acceptance verification

- [x] 11.1 Both suites pass: `dws-controller` and `dws-orchestrator` `./mvnw test`.
- [x] 11.2 Confirm additive-only: no change to `call`/`switch`/`set`/`wait`/`listen`/`emit` behavior;
  no new persistence in the controller; orchestrator I/O stays on the activity boundary.
- [x] 11.3 `openspec verify event-publishing` (or `/opsx:verify`) passes.
  <!-- CLI: `openspec validate event-publishing --strict` -> "Change 'event-publishing' is valid". -->

