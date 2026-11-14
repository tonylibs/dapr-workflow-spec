## Context

- **Today.** `schedule` is parsed but never acted on. A v1 workflow starts only through its
  orchestrator's `POST /workflows/{name}/instances` (`WorkflowController.start`), which always
  generates the instance ID. The cluster is the controller's only store: every resource carries
  `dws.io/workflow`, `dws.io/version` and `dws.io/managed-by`. An old version's orchestrator
  Deployment is annotated `dws.io/drain: "true"` (an annotation, not a label;
  `StackApplier.isDraining`) when a new version takes over, and
  `StackReader.current` defines the current version as the non-draining one.
- **Source idea.** `docs/draft-scheduler-event-trigger-split.md` splits Phase 6 into separate
  trigger components. This change is Phase 6a. Decisions taken for this change:
  - scope is 6a only;
  - stack is .NET;
  - cross-app start is spiked first, with fallback (b).
- **Platform.** Dapr 1.18 (chart pins 1.18.7). The Dapr Scheduler control-plane service, which
  backs the Jobs API, is installed by default. The .NET SDK is pinned at 1.18.10 in `dws-flow`.
- **In-force ADRs that constrain this design.**
  - ADR 0001: v1 and v2 runtimes coexist.
  - ADR 0002: two compiler strategies over a widened `DeploymentPlan`.
  - ADR 0005 and `docs/log-format.md`: the logging contract.
  - ADR 0006: `listen` is a Flow controller and is not a trigger; `schedule.on` is an event source.
  - ADR 0010: per-host env stamped by the controller.
- **Not in force.** ADR 0007 (chart layers, "triggers are Core") is still *in progress* and ADR 0008
  (bring-your-own infra) is *proposed*. This design follows their direction but does not depend on
  either being accepted.

## Goals / Non-Goals

**Goals:**
- Fire `every`, `cron` and `after` for v1 workflows from a component other than the controller.
- One runtime-neutral start contract, with deterministic instance IDs, that 6b reuses unchanged.
- The cluster stays the source of truth. The scheduler rebuilds everything from cluster state plus
  its Dapr jobs and keeps no database.
- Safe to run with more than one replica. No duplicate instances from duplicate delivery.

**Non-Goals:**
- `schedule.on` and `dws-schedule-event` (6b).
- v2 (`dws-flow`) targets.
- Catch-up of missed fires, per-workflow timezones, runtime-expression durations.

## Decisions

### 1. Separate `dws-schedule-time` component, not the controller

The controller compiles and applies. Firing on a clock is a different failure and scaling domain.
If the controller is down, already deployed schedules must keep firing. If the scheduler is down,
submissions must keep working. Alternative considered: Quarkus `@Scheduled` in the controller, as
in the original Phase 6 prompt. Rejected for the coupling above, and because replicating the
controller would duplicate fires. A single `dws-trigger` component with two modes (draft D6) was
also rejected: time and event triggers use different Dapr building blocks (Jobs vs pub/sub) and
scale differently.

### 2. Stack: .NET 10 with the Dapr .NET SDK

User decision. It matches `dws-flow`'s idioms, so `dws-flow/CLAUDE.md` style is the template for
the new package's `CLAUDE.md`. Packages:
- `Dapr.Client`, `Dapr.Workflow` and `Dapr.Jobs`, all pinned to the same 1.18.x as `dws-flow`;
- `KubernetesClient` for the watch;
- `Cronos` only if fire-time computation needs it (see Decision 6).

Trade-off: the draft leaned Java for expression reuse, but 6a evaluates no expressions. 6b's `with`
matching must solve expressions in .NET anyway, and the Flow expression spike (roadmap 2.0c) covers
that.

### 3. Controller normalizes `schedule` into a version-scoped trigger ConfigMap

The controller already parses the DSL with the Java SDK. It validates `schedule` in
`semanticErrors` and adds a `TriggerSpec` to `DeploymentPlan`, which `StackSynthesizer` renders as a
ConfigMap `Names.triggerResource(w, versionId)` with key `trigger.json` and label
`dws.io/trigger=time`. `StackApplier` already tears down by label, so deletion comes for free. The
scheduler therefore parses no DSL. It reads:
- `kind`;
- a canonical ISO-8601 duration or a 5-field cron;
- `appId`.

Alternatives considered:
- Scheduler reads the definition ConfigMap and parses `schedule` itself. Rejected: it duplicates DSL
  parsing in a second language.
- Annotation on the orchestrator Deployment. Rejected: it mixes concerns and the size limits are
  tighter.
- Controller pushes to the scheduler over HTTP (draft D3 option 2). Rejected: Dapr delivers a job
  callback to the app that created the job, so the scheduler must create its own jobs. A push API
  also adds a second source of truth.

Validation rules (at submit):
- exactly one of `every`/`cron`/`after`;
- duration at least 1 s;
- no runtime expressions;
- cron is standard 5-field (Unix definition, validated with `cron-utils`);
- `on` is rejected until 6b;
- `read` is rejected without `on`;
- the v2 strategy rejects `schedule` entirely.

The version ID is still the content digest of the definition text. Nothing changes there.

### 4. Active trigger = trigger config of the non-draining version

The scheduler runs two informers in its namespace: trigger ConfigMaps and managed orchestrator
Deployments. It applies the same rule as `StackReader.current`: the active version is the one whose
Deployment does not carry the `dws.io/drain: "true"` annotation (it is an annotation, not a
label, so the scheduler cannot select on it; it filters informer results instead). This makes the
drain annotation part of a cross-package
contract. The root `CLAUDE.md` contract section and a controller-side test pin it, as was done for
`OrchestratorEnvContractTest`.

### 5. Dapr Jobs API, one job per workflow, overwrite only on change

The scheduler registers job `dws-sched-<workflow>`. Its data is
`{workflow, version, appId, kind, schedule, anchor}`. Mapping:
- `every` → `@every <go-duration>`;
- `cron` → `0 <5 fields>`;
- `after` → no recurring start job (see Decision 7).

Reconcile reads the existing job (`GetJob`) and overwrites only when version, kind or schedule
differ. This keeps an `every` anchor stable across restarts and across replicas that reconcile
concurrently. The job callback `POST /job/<name>` is mapped by hand rather than with the SDK's
`MapDaprScheduledJobHandler`, so the handler decides explicitly between `200` (done or dropped) and
`500` (Dapr retries per the job's failure policy).

Why not a cron binding (draft D2): a binding fires in every replica, and with N replicas it would
start N instances. Jobs deliver each trigger to one replica of the app-id, and the Scheduler service
persists jobs across app restarts.

**Cleanup.** A ConfigMap delete event deletes the job. Reconcile also lists the app's jobs
(`ListJobs`, spike-confirmed in 1.18) and deletes any `dws-sched-*`/`dws-after-*` job with no active
trigger. Lazy cleanup stays as a backstop: a fire for a workflow with no active trigger deletes its
job and starts nothing.

### 6. Start contract and instance IDs

The instance ID is `<source>:<workflow>:<fire-key>`, and colon separators match `dws-flow`'s
`InstanceIds`. The 6a sources are `dws-sched` and `dws-after`. `dws-event` is reserved for 6b,
which uses a hash of the event IDs as its key.

Fire keys:
- `cron`: fire time truncated to the minute (UTC). Cron resolution is one minute, so retries a few
  seconds late map to the same key.
- `every`: the tick index from the anchor stored in the job data,
  `anchor + floor((now − anchor) / interval) × interval`, rendered as UTC `yyyyMMddTHHmmssZ`.
- `after`: a sequence number (Decision 7).
- Early-delivery tolerance (implementation refinement, `FireKeys`): `now` is first moved forward by
  2 s, capped at a quarter of an `every` interval. Without it, a scheduler clock slightly behind the
  Dapr Scheduler's puts an on-time delivery just before its due time and onto the previous key, while
  the retry lands on the right one, so one fire starts two instances.

The spike showed the job callback carries only the raw payload, with no due time, so the key is
always computed as above. Because it derives from the delivery time, ticks that Dapr replays after
downtime (see Decision 8) map to one key and collapse onto one instance.

The start seam is an `IWorkflowStarter` with `StartAsync(appId, workflow, instanceId, inputJson)`
(outcome `Started`, `Retryable` or `NonRetryable`) and `GetStatusAsync(appId, instanceId)` (`Running`,
`Terminal`, `NotFound` or `Error`, read from the orchestrator's `runtimeStatus`):
- **(a) `CrossAppWorkflowStarter`.** `DaprWorkflowClient` against the orchestrator's app-id.
  **Rejected by the spike (ADR 0013, Amendment 1):** the client has no target app-id and always
  schedules into its own app-id's engine, and a duplicate ID replaces a completed instance. Not built.
- **(b) `OrchestratorInvokeStarter`** (the mechanism). Dapr service invocation through the
  `HttpClient` from `DaprClient.CreateInvokeHttpClient()`, which carries `DAPR_API_TOKEN`, with
  `Content-Type: application/json` (the orchestrator answers `400` without it):
  - start: `POST workflows/{name}/instances?instanceId=…` on the orchestrator's app-id; `202` and
    `200` both mean success;
  - status: `GET workflows/instances/{id}`.

The orchestrator change is small. `WorkflowController.start` takes an optional `instanceId`,
validates it against `[A-Za-z0-9:._-]{1,256}`, checks `getWorkflowState(id, false)` first, and
returns `200` with the existing instance if it is found. "Found" is not `state != null`: the Java
SDK returns a state with empty name and epoch `createdAt` for an unknown ID (spike), so not-found is
that shape. The existence check matters because Dapr replaces a completed, terminated or failed
instance that has the same ID; only a running one is refused. Otherwise it calls
`scheduleNewWorkflow(name, input, instanceId)`. If a concurrent start wins the race, the
already-exists error is caught and also mapped to `200`. This ships regardless of the spike outcome:
it is the fallback's dependency and is useful to any caller that wants idempotent starts.

**Start claim (spike follow-up, decided).** The orchestrator's check-then-start is not atomic, and a
workflow that completes in milliseconds can be started twice by two replicas handling a replayed
burst. So before every start, `every`/`cron`/`after` alike, the scheduler takes a first-write-wins
claim in `DWS_SCHEDULE_TIME_STATE_STORE`:
- key `claim:<instanceId>`, value `{state: claiming|started, at}`, written with ETag first-write and
  a TTL (`ttlInSeconds`, default 24 h, `DWS_SCHEDULE_TIME_CLAIM_TTL`; the store must support it);
- claim won → start → set `state=started`;
- claim lost with `started` → success, start nothing;
- claim lost with `claiming` younger than 30 s → return a retryable failure so Dapr retries;
- claim lost with `claiming` older than 30 s → the holder crashed; take over and start (the
  orchestrator's own check still protects a still-running instance).
The orchestrator check stays: it covers claim expiry and callers other than the scheduler.

### 7. `after` uses polling and one-shot jobs, with bookkeeping in the state store

The state key is `after:<workflow>` in `DWS_SCHEDULE_TIME_STATE_STORE`. The value is
`{version, appId, seq, phase: running|waiting}`. Writes use ETags (first-write-wins), so concurrent
replicas cannot double-start.

Lifecycle:
1. Activation with no state starts `dws-after:<w>:1` immediately.
2. The poll job `dws-after-poll-<w>` runs `@every <DWS_SCHEDULE_TIME_AFTER_POLL_INTERVAL>` and checks
   the status of the current instance.
3. On a terminal status (completed, failed or terminated), the scheduler sets `phase=waiting` and
   schedules a one-shot job `dws-after-start-<w>` with `dueTime = now + after`.
4. That one-shot job starts `seq+1` on the *currently active* version and sets `phase=running`.

Lifecycle events are not used. Core does not publish them when the Events layer is off, which was
draft D4. Starting again after a failed run is a deliberate choice. The spec says "after it
completes" without qualifying the outcome, and stopping a schedule forever on a single failure is
the worse surprise. The ADR records this.

### 8. Timing and failure rules

These close draft D12 and D13, and the ADR records them:
- All times are UTC.
- `every` first fires one interval after registration. `cron` first fires at the next match.
- Overlap is allowed for `every` and `cron`, and is impossible for `after` by construction.
- The minimum interval is 1 s.
- Job failure policy: constant, 3 retries, 10 s apart. The handler returns failure for
  transport/5xx/429 and success after logging for any other 4xx (non-retryable).
- Fires missed beyond retries are dropped and logged at error level. The scheduler does no catch-up
  of its own, but Dapr replays ticks missed while the app was down as a burst on recovery (spike:
  4 callbacks in 25 ms). They collapse onto one instance through the delivery-time fire key.
- Dapr does not retry `400`/`404` responses and does retry `5xx`; the handler returns `5xx` only for
  retryable failures. The default job failure policy (3 retries, 1 s) is overridden explicitly.

### 9. Packaging and chart

New package `dws-schedule-time/`, laid out like `dws-flow`:
- `src/`, `test/` (xUnit + FluentAssertions);
- `version.txt`;
- Dockerfile with a `/healthz` smoke test;
- its own `CLAUDE.md`.

CI goes in `.github/workflows/dws-schedule-time.yml`, and the release-please component is added. Chart:
- `templates/schedule-time/` with SA, Role, RoleBinding and Deployment, gated by `scheduleTime.enabled`
  (default true);
- schema entries;
- the per-component API token Secret;
- `DWS_LOG_FORMAT` through the shared helper;
- a new `schedule-time-render-test.sh`.

The scheduler serves only Dapr callbacks and `/healthz`, so it gets no Service beyond what the Dapr
sidecar needs. The JSON log formatter follows `docs/log-format.md`, copied from `dws-flow`'s
formatter and pinned by the same 12 golden cases. There is no shared library by design.

### Cross-component contracts changed or added

| Contract | Producer → consumer | Pinned by |
|---|---|---|
| Trigger ConfigMap (`dws.io/trigger=time`, `trigger.json` shape) | controller → scheduler | controller synthesis test + scheduler parser test over a shared fixture in `dws-schedule-time/test/fixtures/` copied from controller output |
| Drain label meaning "not active" | controller → scheduler | controller test asserting the label name; scheduler active-version test |
| `instanceId` query parameter on start | scheduler → orchestrator | orchestrator controller test; scheduler starter test |
| Instance ID format `<source>:<workflow>:<key>` | scheduler (and 6b) | scheduler unit tests; documented in root `CLAUDE.md` |
| `DAPR_API_TOKEN` on scheduler | chart → scheduler | `api-tokens-render-test.sh` |
| `DWS_LOG_FORMAT` | chart → scheduler | `DwsJsonConsoleFormatterTests` in scheduler |

Task-name → app-id mapping and step-service HTTP behaviour are unchanged.

## Risks / Trade-offs

- [Cross-app start is unsupported, or replaces completed instances] → Spike first (task group 1).
  Fallback (b) needs no Dapr feature beyond service invocation.
- [The Jobs API callback or its due-time field differs from what the docs say] → Spike verifies the
  callback route, payload, delivery guarantees and the 6-field cron format before the scheduler is
  built. Fire-key computation is isolated, so only that function changes.
- [Jobs API is alpha/beta in 1.18 and its API may shift] → All Dapr job calls go behind one
  `IJobRegistry` seam, and the SDK version is pinned with `dws-flow`'s.
- [`every` fire key collides across a delivery delayed by more than one interval] → Accepted. This
  needs a delivery delay longer than the interval, and the 1 s minimum makes it possible only for
  tiny intervals. Documented.
- [Orchestrator Dapr access control later restricts invocation] → The scheduler app-id must be
  allowed. Noted in the root `CLAUDE.md` contract section.
- [Auth on: `/job/*` callbacks with an app token, or bearer middleware] → The scheduler gets no
  bearer middleware. A live check with `auth.enabled=true` is a required task (draft D11).
- [Existing definitions with `schedule` start firing, or newly fail validation, on redeploy] →
  Called out in the release notes and the chart `NOTES.txt`. Nothing changes until a redeploy.
- [State-store outage blocks starts] → Every start needs the claim, so `every`/`cron` now depend on
  the state store too. Fires are retried by job policy and dropped after the retries, as for any
  other start failure.

## Migration Plan

1. Ship the orchestrator `instanceId` support first. It is backward compatible.
2. Ship the controller validation and trigger ConfigMap. Without a scheduler, the ConfigMaps are
   inert.
3. Ship the `dws-schedule-time` image and the chart with `scheduleTime.enabled=true`.
4. Rollback: set `scheduleTime.enabled=false`. Jobs then stop being delivered because no app owns the
   callbacks, and they stay in the Dapr Scheduler until they are deleted. Removing them on disable
   is a documented manual step (`dapr` Jobs API `DELETE`). The controller and orchestrator changes
   are safe to keep.

## Open Questions

- **Spike outcomes** (task group 1): resolved in ADR 0013, Amendment 1, including both follow-ups:
  the state-store start claim (Decision 6) and the spec line, now "replayed ticks collapse onto one
  instance". Still unverified: `429` handling, `auth.enabled=true`, Dapr 1.18.7.
- **Should `every` also fire once at registration?** The spec is silent. Proposed: no (first fire
  after one interval). Confirm in the ADR.
- **ADR 0007** is not accepted. If it lands with a different layer for triggers, the
  `scheduleTime.enabled` default may move. No supersession is needed from this change.
- No in-force ADR needs revisiting.
