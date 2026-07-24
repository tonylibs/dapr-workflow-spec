## Context

DWS is a four-component monorepo with **no persistence layer**: `dws-controller` treats the cluster
as the source of truth, and each `dws-orchestrator` pod holds one immutable definition for its
lifetime. A future `dws-admin` service will need a read model, but nothing today emits lifecycle
signals. Both components already sit on Dapr:

- `dws-orchestrator` already publishes DSL `emit` events over Dapr pub/sub through
  `EmitEventActivity` (`ctx.callActivity(EmitEventActivity.class.getName(), EmitRequest, ...)`), using
  a shared static `WorkflowSupport.daprClient()` and `defaultPubsub()` (default component `pubsub`).
  The interpreter (`InterpreterWorkflow.execute`) is a program-counter loop over the definition's
  `do` list and must stay **deterministic** under Dapr Workflow replay — all I/O already goes through
  activities.
- `dws-controller` (Quarkus) has an apply pass in `StackApplier`: `apply(plan)` creates/updates the
  ConfigMap, Dapr Component, Knative Services, and the orchestrator Deployment, then `rollOut` marks
  superseded versions `dws.io/drain=true` and `collectIfDrained` deletes drained versions by label.
  `ReconcileJob` periodically calls `applier.reconcile()` for the same GC. The controller does **not**
  currently depend on the Dapr Java SDK.

This design adds a shared lifecycle-event stream on Dapr pub/sub (topic `dws.events`, component
`pubsub`) fed by both components, plus a root-level contract doc. It does not build any consumer.

## Goals / Non-Goals

**Goals:**
- One shared, versionable event contract (`docs/events.md`) with a CloudEvents-style envelope.
- Controller publishes definition + deployment(applied/failed/drained/collected) events from the
  apply pass, fire-and-forget, without weakening the "cluster is source of truth" invariant.
- Orchestrator publishes instance + task events from the interpreter, strictly via a Dapr activity,
  preserving replay determinism.
- Reuse each component's existing Dapr wiring and conventions (constructor injection in the
  controller; the `WorkflowSupport` static bridge + activity pattern in the orchestrator).

**Non-Goals:**
- The `dws-admin` service, any subscriber, or read-model storage.
- Provisioning the `pubsub` Dapr Component (a documented deployment prerequisite).
- Changing any existing DSL semantics or adding persistence to either component.
- Delivery guarantees beyond best-effort at-least-once from Dapr pub/sub; events are advisory.

## Decisions

### D1: Single topic `dws.events` on component `pubsub`, CloudEvents-style envelope
One topic keeps the consumer's subscription simple and lets event `type` drive routing. The envelope
mirrors CloudEvents (`id`, `source`, `type`, `time`, `datacontenttype`, `data`) rather than inventing
a bespoke shape, so a future consumer can use off-the-shelf tooling.
- *Alternative — topic-per-type*: rejected; multiplies Subscriptions and component config for no gain
  at this scale.
- *Alternative — rely on Dapr's own CloudEvents wrapping*: Dapr already wraps published bytes in a
  CloudEvent, but the two publishers are independent and the payload contract must be explicit and
  documented regardless, so we define our own `data` envelope and document it. Publishing a plain
  JSON object as `data` is sufficient; we do not hand-roll CloudEvents transport headers.

### D2: Controller — thin `EventPublisher` over the Dapr Java SDK, injected into `StackApplier`
Add `io.dapr:dapr-sdk` to `dws-controller/pom.xml` (version-managed to match the orchestrator's
`1.18.0`). New package `io.dws.controller.events`:
- `DaprClientProducer` — a CDI `@ApplicationScoped` `@Produces DaprClient` (with `@PreDestroy`/
  disposer close), mirroring the orchestrator's `daprClient()` bean, so the client is a normal
  injectable bean.
- `EventEnvelope` (record) — builds the CloudEvents-style map; a small `EventPublisher`
  (`@ApplicationScoped`, constructor-injects `DaprClient` + `DwsConfig`) exposes typed methods
  (`definitionCreated`, `deploymentApplied`, …) that assemble the envelope and call
  `client.publishEvent("pubsub", "dws.events", envelope)`. All publish calls are wrapped so any
  exception is logged and swallowed (fire-and-forget, per spec).
- Wire into `StackApplier` via constructor injection. Call sites:
  - `definition.created`/`definition.updated`: in `apply`, keyed on `!alreadyDeployed`.
  - `deployment.applied`: at the end of `apply` (after `rollOut`); `deployment.failed`: `apply`
    wrapped in try/catch that publishes then rethrows.
  - `deployment.drained`: inside `markForDrain` (only when it actually annotates).
  - `deployment.collected`: inside `collectIfDrained` (after `deleteByLabels`). This covers both the
    POST-driven `rollOut` path and the `ReconcileJob` path, since both funnel through these methods.
- `orchestratorAppId` and `stepServices` come from `plan.orchestrator().appId()` /
  `plan.steps()` on the apply path. On the GC paths (`reconcile`) only labels are known; the
  orchestrator app-id is derived from the Deployment's `dws.io/*` labels / name already in hand.

*Alternative — publish from `WorkflowResource`*: rejected; drain/collect happen in `StackApplier`
(and `ReconcileJob`), not the resource, so the applier is the only place that sees every transition.

### D3: Orchestrator — new `AdminEventActivity` mirroring `EmitEventActivity`
New `io.dws.orchestrator.workflow.activity.AdminEventActivity` + `AdminEventRequest(pubsub, topic,
envelope)` record. The activity does `WorkflowSupport.daprClient().publishEvent(...).block()`, exactly
like `EmitEventActivity`. Register it in `WorkflowRuntimeBootstrap` alongside the others.
`InterpreterWorkflow.execute` schedules it via `ctx.callActivity(AdminEventActivity.class.getName(),
req, defaultTaskOptions, Void.class).await()` at:
- instance start (after reading input), and instance end at both terminal points
  (`ctx.complete` off-the-end and via the `END`/`EXIT` directive in `advance`/`complete`);
- around each task-item dispatch: `task.started` before the type dispatch, `task.completed` after,
  `task.failed` in a catch that publishes then rethrows to `instance.failed`.

### D4: Determinism — timestamps and ids from the workflow context, not wall clock
The envelope's `time`/`timestamp` and `id` are produced **inside the workflow method**, so they must
be replay-stable. Use `ctx.getCurrentInstant()` for time and derive `id`/`instanceId` from
`ctx.getInstanceId()` (plus a deterministic per-instance counter) — never `Instant.now()`,
`UUID.randomUUID()`, or similar inside `execute`. The publish itself is non-deterministic I/O and
therefore lives in the activity, consistent with the existing `EmitEventActivity` boundary.

### D5: `appId` sourced from orchestrator config
Add an `appId` to `OrchestratorProperties` (bound from the Dapr-injected `APP_ID`/`dapr.app-id`
env, defaulting to the workflow name / definition key), seed it through
`WorkflowSupport.init(...)`, and read it via `WorkflowSupport.appId()` when building instance
payloads. `workflow` and `version` are split from the pod's `definitionKey`
(`order-workflow@v3` → workflow `order-workflow`, version `v3`) / `WorkflowSupport.workflowName()`.

### D6: Testing seams
- Controller: add a test-only mock capability. The apply-pass tests (`@QuarkusTest` +
  `@WithKubernetesTestServer`, e.g. `WorkflowResourceTest`/`StackApplierTest`) use
  `quarkus-junit5-mockito` `@InjectMock DaprClient` (add the dep) to capture `publishEvent` calls and
  assert type + payload. A plain unit test covers `EventEnvelope`/`EventPublisher` payload shape with
  a Mockito-mocked `DaprClient`.
- Orchestrator: extend the existing `InterpreterWorkflow` test (drives `execute` against a mocked
  `WorkflowContext`) to also stub `ctx.getInstanceId()` and `ctx.getCurrentInstant()`, and to verify
  `ctx.callActivity(eq(AdminEventActivity.class.getName()), captor, ...)` is scheduled at the right
  points with the right `AdminEventRequest` payloads. A unit test covers envelope construction.

## Risks / Trade-offs

- **[Non-deterministic time/id leaks into the workflow method]** → break replay. Mitigation: D4 —
  only `ctx.getCurrentInstant()` / `ctx.getInstanceId()`; add a replay-determinism assertion in tests
  and call it out in code comments at each call site.
- **[Extra activity call per task boundary triples orchestrator activity volume]** → latency/history
  growth. Mitigation: accept for now (events are the point); admin events use the same retry options,
  and publishing is cheap relative to `call` service invocations. Revisit batching if history size
  becomes a problem.
- **[`pubsub` component missing in-cluster]** → publishes fail. Mitigation: fire-and-forget in the
  controller (swallow+log); in the orchestrator the activity failure is retried per policy but must
  not deadlock the instance — instance/task-failed publishing itself must tolerate publish failure.
  Documented as a deployment prerequisite in `docs/events.md`.
- **[Controller gains a Dapr sidecar dependency it did not have]** → the controller now needs a Dapr
  sidecar to publish. Mitigation: publishing degrades gracefully when the sidecar/component is absent;
  no apply behavior depends on it.
- **[`definition.updated` semantics]** → an idempotent no-op re-POST of an already-present version
  emits `definition.updated`, which may look like a real change. Mitigation: documented; the consumer
  treats `updated` as "this version was (re)asserted", not "content changed" (content is immutable per
  the version hash).

## Migration Plan

1. Land `docs/events.md` (contract) first — it is the reference for both implementations.
2. Controller: add `dapr-sdk` (+ test mock dep), `io.dws.controller.events.*`, wire `StackApplier`,
   tests; `./mvnw test` green.
3. Orchestrator: add `AdminEventActivity`/`AdminEventRequest`, `appId` config + `WorkflowSupport`
   seeding, `InterpreterWorkflow` call sites, register activity, tests; `./mvnw test` green.
4. Deploy prerequisite: ensure a Dapr pub/sub `Component` `pubsub` with topic `dws.events` exists in
   the target cluster before rollout.
- **Rollback**: publishing is additive and best-effort; reverting the two components removes all
  publishing with no data migration. No consumer exists yet, so there is nothing to break downstream.

## Open Questions

- Exact `source` string convention (URN vs plain id) — pick a simple stable scheme in `docs/events.md`
  (e.g. `dws-controller` and `dws-orchestrator/<appId>`).
- Whether `stepServices` should be step names only or richer objects — start with names (matches the
  proposal's `stepServices[]`) and extend later if the read model needs more.
- Whether to add a Dapr `Subscription`/`Component` sample manifest to the repo for convenience — left
  out of scope here (prerequisite is documented, not provisioned).
