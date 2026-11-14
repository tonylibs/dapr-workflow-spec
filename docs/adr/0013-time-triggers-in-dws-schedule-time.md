# ADR 0013: Time Triggers Run in a Separate `dws-schedule-time` Component

- **Status:** Accepted. Decision 3's start mechanism is the Dapr service-invocation fallback; the
  Phase 6a spike confirmed it (see Amendment 1).
- **Date:** 2026-11-05
- **Amended:** 2026-11-07 — Phase 6a spike (task group 1): cross-app start rejected, service
  invocation of the orchestrator confirmed; Jobs API behaviour recorded. See Amendment 1.
- **Context:** [`docs/draft-scheduler-event-trigger-split.md`](../draft-scheduler-event-trigger-split.md),
  [`docs/roadmaps/openworkflow-features.md`](../roadmaps/openworkflow-features.md) Phase 6,
  `openspec/changes/scheduling-phase-6a-time-triggers`
- **Related:** [ADR 0002](0002-workflow-compiler-strategy-split.md) (compiler strategies),
  [ADR 0006](0006-wait-and-listen-as-flow-controllers.md) (`listen` is not a trigger;
  `schedule.on` is an event source), [ADR 0007](0007-chart-feature-layers.md) (in progress:
  "triggers are Core")

## Context

The DSL's `schedule` block (`every`, `cron`, `after`, `on`) decides when a workflow instance is
created. DWS ignores it today. A v1 workflow starts only through its orchestrator's
`POST /workflows/{name}/instances`.

The original Phase 6 plan put scheduling in `dws-controller`. That has three problems:
- It ties firing to the compiler's availability.
- Replicating the controller would duplicate fires.
- The controller would own a second, runtime-facing job next to compile-and-apply.

Event triggers (`schedule.on`) need pub/sub, matching and state. That is a different Dapr building
block and has a different scaling profile from clock-driven triggers.

## Decision 1: Triggers live outside the controller, one component per trigger family

- Time triggers (`every`, `cron`, `after`) run in a new component, `dws-schedule-time`. The event
  trigger (`schedule.on`) will run in a separate `dws-schedule-event` (Phase 6b).
- The controller only validates `schedule` at submit and stamps a normalized, version-scoped
  **trigger ConfigMap** (`dws.io/trigger=time`, key `trigger.json`) next to the definition.
- Trigger components derive everything from cluster state. They keep no database, and a workflow's
  active trigger is the one belonging to its non-draining version.
- The name `dws-listener` is not used, because it collides with the `listen` task.
- `dws-schedule-time` is written in .NET with the Dapr .NET SDK, on the same stack as `dws-flow`.

## Decision 2: Dapr Jobs API, not a cron binding

- The scheduler registers its own Dapr jobs: one per workflow (`dws-sched-<workflow>`) for
  `every`/`cron`, plus poll and one-shot jobs for `after`.
- It must create the jobs itself because the Jobs API delivers a callback only to the app that
  created the job.
- A cron input binding fires in every replica and would start duplicates. Jobs deliver each
  trigger to one replica and persist across app restarts.
- A job is overwritten only when its target version, kind or schedule changes, so the `every`
  anchor stays stable.
- When a fire finds no active trigger, the scheduler deletes the job (lazy cleanup), because the
  Jobs API has no list call.

## Decision 3: One runtime-neutral start contract with deterministic instance IDs

- Every trigger component starts an instance by naming the workflow, the target app-id, the input
  and an instance ID `<source>:<workflow>:<fire-key>`.
- Sources:
  - `dws-sched`: the `every`/`cron` fire tick in UTC;
  - `dws-after`: a sequence number;
  - `dws-event`: reserved for 6b (hash of event IDs).
- A repeated delivery of the same fire carries the same ID. The target starts at most one instance
  per ID and reports an existing one as success.
- Mechanism: Dapr service invocation of the v1 orchestrator's start endpoint, which gains an
  optional `instanceId` query parameter. A direct cross-app `DaprWorkflowClient` start was
  evaluated and rejected by the Phase 6a spike (Amendment 1).
- 6a targets the v1 orchestrator only. The contract does not assume a runtime.

## Decision 4: Timing and failure rules

- All schedule times are UTC. Cron is the standard 5-field form, registered as Dapr's 6-field form
  with seconds `0`.
- Minimum `every`/`after` duration is 1 second. Runtime-expression durations are rejected at submit.
- Exactly one of `every`, `cron` or `after` per definition.
- `every` first fires one interval after registration. `cron` first fires at the next match.
  `after` starts its first instance when it activates.
- `every`/`cron` may overlap running instances.
- `after` waits for *any* terminal status (completed, failed, terminated), then the delay. A failed
  run does not stop the schedule.
- `after` completion is detected by polling instance status, never by lifecycle events, so it works
  with the Events layer off.
- A fire whose start fails with a transport error, 5xx or 429 is retried 3 times, 10 s apart, then
  dropped and logged. Other 4xx are dropped without retry. The scheduler does no catch-up of its own;
  ticks Dapr replays after downtime collapse onto one instance (Amendment 1).

## Consequences

- Two new Core Deployments over time: `dws-schedule-time` now, `dws-schedule-event` in 6b. Each needs
  its own CI, release-please entry, chart values, RBAC and API-token wiring.
- New cross-package contracts:
  - the trigger ConfigMap shape and label;
  - the drain label meaning "not active";
  - the start endpoint's `instanceId`;
  - the instance-ID format.
  None of them is a compile-time check, so each is pinned by tests on both sides and listed in the
  root `CLAUDE.md`.
- `schedule` on the v2 compiler strategy is rejected until a later phase targets `dws-flow`.
- Existing definitions that carry `schedule` begin firing, or newly fail validation, on their next
  deploy.
- The scheduler depends on the Dapr Scheduler control-plane service and on the Core state store
  (start claims for every trigger, plus `after` bookkeeping; Amendment 1).

## Amendment 1 (2026-11-07): Phase 6a spike result

Run on a local docker-desktop (kind) cluster, throwaway namespace, since removed. Versions: Dapr
control plane and sidecars **1.18.2** (the chart pins 1.18.7; not re-run on it), `Dapr.Workflow`,
`Dapr.Jobs` and `Dapr.Client` **1.18.10** (.NET), `dapr-sdk-workflows` **1.18.1** (Java). Targets were
the real `dws-orchestrator` image hosting a fast (`set`) and a slow (`wait: PT120S`) definition. The
"scheduler" was a 2-replica .NET stand-in app that logged every callback.

### Decision 3 is settled: service invocation (option b)

- **Cross-app start is not possible with `DaprWorkflowClient`.** `ScheduleNewWorkflowAsync` has no
  target app-id parameter; `TargetAppId` exists only on in-workflow task options (activity and
  child-workflow calls). A client that schedules a workflow name it does not host runs the instance
  in its **own** app-id's workflow engine, where it fails at once (status `Failed`, state stored under
  the client's actor type; the real host never sees it). `WorkflowAccessPolicy` is not involved: it
  governs cross-app calls made from inside a workflow, and no policy existed or was needed. Not
  tested: a scheduler-hosted workflow that calls the orchestrator's workflow as a cross-app child.
- **Service invocation works.** From another app-id's sidecar,
  `POST /v1.0/invoke/<orchestrator>/method/workflows/<name>/instances` returns `202` with the
  `{instanceId, workflowName}` body. The request needs `Content-Type: application/json`; without it
  the orchestrator answers `400`. The `instanceId` query parameter is ignored today, so the
  orchestrator change (task group 2) is required, as designed.

### Duplicate instance IDs are not safe by themselves

Identical for the Java and .NET SDKs (the check is in the sidecar):

| Existing instance with the same ID | Result of a second start |
|---|---|
| Running | Fails: gRPC `ALREADY_EXISTS` ("an active workflow with ID '…' already exists") |
| Completed | **Succeeds and replaces it**: new `createdAt`, the workflow runs again |
| Terminated | **Succeeds and replaces it** (verified with the .NET SDK only; Java's `terminateWorkflow` call hung through the port-forward, but the check is sidecar-side) |
| Failed | **Succeeds and replaces it** |

The sidecar's `CreateInstanceRequest` carries an `orchestrationIdReusePolicy` field, but neither SDK
exposes it. So deterministic IDs dedupe only against *running* instances, and "start is idempotent
per instance ID" has to come from the orchestrator's existence check (task 2.2), not from Dapr.
The check-then-start sequence is not atomic: two replicas racing on a workflow that completes
within milliseconds can still start it twice. A first-write-wins claim on the instance ID in the
Core state store, taken before the start, would close the window. This is **not yet decided**; see
Follow-ups.

### Java SDK has no "not found" signal

`getWorkflowState` never returns `null`. For an unknown ID it returns `name=''`, `status=RUNNING`,
`createdAt=1970-01-01T00:00:00Z`. As a result the orchestrator's `GET /workflows/instances/{id}`
returns `200 RUNNING` for **any** ID (verified live), and its `state == null` check is dead code.
Not-found must be detected as empty name or epoch `createdAt`. The scheduler's status polling for
`after` depends on this being fixed. The .NET SDK's `WorkflowState.Exists` is correct.

### Jobs API behaviour (Dapr 1.18.2, `Dapr.Jobs` 1.18.10)

- **Callback:** `POST /job/<name>` on the app that created the job, `Content-Type: application/json`,
  body = the raw job payload. There is **no due time, schedule or attempt header**. The fire key
  therefore has to be computed (Decision 3 and design Decision 6 are unchanged).
- **Formats:** `@every 10s`, `@every 1m30s` and 6-field cron (`0 * * * * *`) are accepted. A 5-field
  cron and ISO-8601 (`PT10S`) are rejected with `InvalidArgument: Schedule is empty`.
- **Overwrite:** without `overwrite`, scheduling an existing name fails with `AlreadyExists` (job key
  `app||<namespace>||<app-id>||<name>`). With `overwrite`, an `@every` job is **re-anchored**: the
  next fire is one interval after the overwrite. Overwrite only on change, as designed.
- **One delivery:** with 2 replicas, every tick reached exactly one pod (no duplicates over about 20
  ticks, `@every` and cron); the receiving pod rotates.
- **Latency:** ticks arrived 0 to 1.4 s after the anchor, with one tick about 2.7 s late.
- **Failure policy:** the SDK default is constant, 3 retries, 1 s. With
  `JobFailurePolicyConstantOptions(10s){MaxRetries=3}` the interval is honoured (4 attempts in total,
  5 s apart in the test). A one-shot job is removed once its attempts are exhausted; a recurring job
  keeps ticking after a failed tick's retries run out. Responses `400` and `404` were **not retried**
  (one attempt, job removed for one-shot). `5xx` is retried. `429` was not tested.
- **List and delete:** `ListJobs` (name, schedule, payload, policy) and `DeleteJobsByPrefix` exist and
  work. The design's "the Jobs API has no list call" is wrong for 1.18.
- **Persistence and catch-up:** jobs survive a full restart of the app and keep their anchor. Ticks
  missed while the app was down are **replayed as a burst** on recovery (4 callbacks within 25 ms
  across both replicas after about 34 s of downtime on a 10 s job). The design's "no catch-up" holds
  only for fires that fail after delivery.

### Consequences for the design

- Fire-key derivation: unchanged, because no due time is delivered. Because the key is computed from
  the delivery time, a replayed burst maps to one key and collapses onto one instance, **provided**
  the existence check (or claim) sees the earlier start.
- Reconcile can list jobs and delete orphans, so lazy cleanup becomes a backstop (design Decision 5).
- The `CrossAppWorkflowStarter` is not built (task 6.2).
- Task 2.2 must detect not-found as above, and the status endpoint should return `404` for it.

### Follow-ups

1. **Decided:** close the check-then-start race with a first-write-wins state-store claim per
   instance ID, for `every` and `cron` as well as `after`. `every`/`cron` therefore depend on the
   Core state store too (design Decision 6, task 6.2a).
2. **Decided:** the spec no longer says "Missed fires SHALL NOT be replayed". It says the scheduler
   does no catch-up of its own and that replayed ticks collapse onto one instance.
3. Not verified: `429` handling, the `auth.enabled=true` path (task 9.5), Dapr 1.18.7.

## Phase 6a live verification (2026-11-14)

[Live evidence and follow-ups](../roadmaps/scheduling-phase6a-evidence.md) record group 9 on
Kubernetes 1.34.3 / Dapr 1.18.2 with branch-built images. Initial fires, takeover/deletion and
restart checks passed. Downtime replay failed during initial cluster synchronization;
auth-enabled workflow starts failed with 401 because the invocation client did not supply
the sidecar API token. Tasks 9.4 and 9.5 remain open; this change is not ready to archive.
The run also needed a controller jsii cache workaround. It does not verify Dapr 1.18.7.
