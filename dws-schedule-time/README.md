# dws-schedule-time

`dws-schedule-time` fires the time triggers of the DSL's `schedule` block (`every`, `cron`, `after`)
for v1 workflows. It watches the cluster for trigger ConfigMaps, keeps one Dapr job per scheduled
workflow, and starts workflow instances on the matching orchestrator through Dapr service
invocation. It keeps no database: the cluster, its Dapr jobs, the `after` bookkeeping and the
per-instance start claims are the only state. Design: `openspec/changes/scheduling-phase-6a-time-triggers`
and `docs/adr/0013-time-triggers-in-dws-schedule-time.md`.

## Environment

| Variable | Default | Meaning |
|---|---|---|
| `DWS_SCHEDULE_TIME_STATE_STORE` | `statestore` | Dapr state store for start claims and `after` state. Must support ETags; `ttlInSeconds` is needed for claim expiry. |
| `DWS_SCHEDULE_TIME_AFTER_POLL_INTERVAL` | `PT15S` | How often an `after` workflow's current instance is polled (ISO-8601, at least 1 s). |
| `DWS_SCHEDULE_TIME_CLAIM_TTL` | `PT24H` | TTL of `claim:<instanceId>` state entries (ISO-8601). |
| `POD_NAMESPACE` | service account namespace file | Namespace to watch. Without either the watch is disabled (logged at error level). |
| `DWS_LOG_FORMAT` | text | `json` selects the contract formatter (`docs/log-format.md`). |
| `DAPR_API_TOKEN` | unset | Sidecar API token (chart: `dapr.io/api-token-secret`). `DaprClientBuilder` reads it for jobs and state; `Program.cs` passes it explicitly to the service-invocation client, which does not read it itself. |

An invalid duration stops the host at boot. The app serves `GET /healthz` (`{"status":"ok"}`) even with
no sidecar and no cluster; watch and reconcile failures are logged and retried in the background.

## Cluster inputs

- ConfigMaps labeled `dws.io/trigger=time` with key `trigger.json`:
  `{"workflow","version","appId","kind":"every|cron|after","schedule"}` where `schedule` is a canonical
  ISO-8601 duration (at least 1 s) for `every`/`after`, or a 5-field cron.
- Deployments labeled `dws.io/managed-by=dws-controller` (all of them are orchestrators). A workflow's
  active trigger is the one of its version whose Deployment is not draining. **Draining is the
  ANNOTATION `dws.io/drain: "true"`** (what `StackApplier.markForDrain` writes), not a label, even
  though the spec text says "labeled".
- Test fixtures `test/fixtures/trigger-*.json` are `dws-controller` output, not hand-written: the
  controller's `SchedulerContractTest` fails when they drift (see `test/fixtures/README.md`).

## Jobs

| Job | Schedule | Purpose |
|---|---|---|
| `dws-sched-<workflow>` | `@every <go duration>` (every) or `0 <5 fields>` (cron) | One recurring fire per workflow. Payload `{workflow, version, appId, kind, schedule, anchor}`. |
| `dws-after-poll-<workflow>` | `@every <poll interval>` | Polls the current `after` instance. |
| `dws-after-start-<workflow>` | one-shot, due time = terminal time + delay | Starts the next `after` instance. |

Every job uses a constant failure policy: 3 retries, 10 s apart. That spacing holds for an on-time fire only: ticks Dapr replays after the app was down are already overdue, and their retries arrive at once (live run, task 9.4: 4 ticks x 4 attempts within 0.2 s). So a callback that arrives before the first full cluster list is held, for up to 30 s, until the list completes instead of being answered with a retry; its fire key still comes from the delivery time. Durations are written in Go syntax
(`PT1H30M` -> `1h30m`, `PT90S` -> `1m30s`, `PT1.5S` -> `1s500ms`). The Jobs callback is `POST /job/<name>`
with the raw payload and no due time, so the fire key is computed from the delivery time.

Reconcile lists the app's jobs (`ListJobs` exists in `Dapr.Jobs` 1.18.10) and deletes any
`dws-sched-*`, `dws-after-poll-*` or `dws-after-start-*` job with no matching active trigger. A job is
created if absent and overwritten only when the registered payload's version, kind or schedule differs
(so an `every` anchor survives restarts and concurrent replicas). A fire for a workflow with no active
trigger deletes its job and starts nothing. Before the first full cluster list, a callback is held until the list completes (up to 30 s), then
answers 500 (retry) only if it still has not, instead of acting on an empty view.

## Instance IDs and timing

- `dws-sched:<workflow>:<yyyyMMddTHHmmssZ>` (`FireKeys`): cron uses the delivery time truncated to the
  minute; every uses `anchor + floor((delivery - anchor) / interval) * interval`. The delivery time is
  first moved forward by up to 2 s (at most a quarter of the interval), so an on-time delivery seen by a
  pod clock slightly behind the Dapr Scheduler's lands on its own key, not the previous one.
- `dws-after:<workflow>:<seq>`.
- All times are UTC. `every` first fires one interval after registration, `cron` at the next match,
  `after` starts its first instance on activation. A replayed burst after downtime lands on one key.
  A delivery delayed by more than one `every` interval can skip or merge ticks (accepted).

## Start contract

`POST workflows/{name}/instances?instanceId=<id>` on the target orchestrator app-id, body `{}`,
`Content-Type: application/json`. 202 and 200 mean started (or already existing). 5xx, 429 and
transport errors are retryable; other 4xx are permanent. Status is `GET workflows/instances/{id}`, whose
`runtimeStatus` field is read (`RUNNING`/`PENDING`/`SUSPENDED`/`CONTINUED_AS_NEW` running;
`COMPLETED`/`FAILED`/`TERMINATED`/`CANCELED` terminal; 404 not found). An instance ID outside the
orchestrator's `[A-Za-z0-9:._-]{1,256}` is rejected before any call (`InstanceIds.IsValid`).

Before each start a claim `claim:<instanceId>` (`{state: claiming|started, at}`) is taken first-write-wins
with an ETag. Lost to `started`: success without starting. Lost to a `claiming` younger than 30 s:
retryable. Older: ETag-guarded takeover. A failed start (retryable or not) deletes the claim so the
retry can start at once; the orchestrator's own existence check still guards a running instance. The
residual risk is an ambiguous transport timeout where the orchestrator did start and finished within
milliseconds.

The ETag create-if-absent path (`TrySaveStateAsync` with an empty ETag under first-write) has no unit
test; confirm it on a live cluster with the real state store (tasks 9.x).

## `after` lifecycle

State key `after:<workflow>`: `{version, appId, seq, phase: running|waiting, readyAt?}`, all writes
ETag-guarded. Activation with no state creates `seq 1` and starts it at once. The poll checks the stored
instance on the stored app-id: terminal (any of completed, failed, terminated) sets `waiting` with
`readyAt = now + delay` and schedules the one-shot; not found with a `started` claim counts as terminal,
otherwise the start of the same seq is retried; a `waiting` state overdue by `2 x poll interval` is started
by the poll itself. The one-shot resolves the current active trigger (takeover applies to the next start),
bumps `seq` and starts. When the trigger stops being active the poll and one-shot jobs are deleted but
`after:<workflow>` is kept, so sequence numbers stay monotonic and IDs never collide on re-activation.

## Build and test

```shell
cd dws-schedule-time
dotnet build
dotnet test test/dws-schedule-time.Tests.csproj
docker build -t dws-schedule-time:dev .
```
