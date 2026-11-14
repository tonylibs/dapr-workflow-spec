# dws-schedule-time

`dws-schedule-time` fires DSL `schedule` time triggers (`every`, `cron`, `after`) for deployed v1
workflows (scheduling Phase 6a, [ADR 0013](../docs/adr/0013-time-triggers-in-dws-schedule-time.md),
OpenSpec change `scheduling-phase-6a-time-triggers`). It is a .NET 10 app with the same stack and
layout as `dws-flow`.

**Status: in progress.** This package currently holds the scaffold and the scheduler side of the
workflow start contract (deterministic instance IDs and the start seam). Trigger parsing, the
Kubernetes watch, Dapr Jobs registration, the start claim and the job handlers land with the rest of
the change; nothing fires yet.

## Workflow start contract (caller side)

A trigger starts an instance by naming the workflow, its active version's orchestrator app-id, the
input and a deterministic instance ID, so a repeated delivery of one fire starts at most one instance.

| Piece | Where |
|---|---|
| Instance ID `<source>:<workflow>:<fire-key>`; sources `dws-sched` (`every`, `cron`), `dws-after` (`after`, key = sequence number), `dws-event` reserved for Phase 6b | `InstanceIds` |
| Fire key from the delivery time (a Dapr job callback carries no due time): `cron` = the due minute, `every` = the anchor tick, both as UTC `yyyyMMddTHHmmssZ` | `FireKeys` |
| Time-triggered input is always `{}` | `TriggerStart.ForTimeTrigger` |
| One start seam: `StartAsync` / `GetStatusAsync` | `IWorkflowStarter` |
| Dapr service invocation of the orchestrator: `POST workflows/{name}/instances?instanceId=…` (`202` started, `200` already existed) and `GET workflows/instances/{id}` (`404` unknown) | `OrchestratorInvokeStarter` |

Failure classification: a transport error, `5xx` or `429` is retryable (the job handler reports the
fire as failed so Dapr retries it); any other `4xx` is not. Cross-app `DaprWorkflowClient` scheduling
was rejected by the Phase 6a spike (no target app-id, and a duplicate ID replaces a completed
instance), so it is not implemented.

`FireKeys` maps every delivery of one fire to one key: retries 10 s apart and a burst replayed after
downtime land on the latest due time at or before the delivery. A delivery that arrives slightly
early by this pod's clock still counts as the due fire (up to 2 s, capped at a quarter of an `every`
interval).

## Configuration

| Setting | Meaning |
|---|---|
| `DWS_LOG_FORMAT` | `json` (trimmed, case-insensitive) selects the structured log format of [`docs/log-format.md`](../docs/log-format.md); anything else keeps the default console output. |
| `DAPR_API_TOKEN` | Read by the Dapr SDK and sent to the sidecar when set (chart `auth.enabled=true`). |

## Build and test

```bash
dotnet test test/dws-schedule-time.Tests.csproj
```

Name the test project: a bare `dotnet test` in this directory only sees `dws-schedule-time.csproj`,
runs no tests and still exits 0. The version is in `version.txt` (bumped by release-please, read by
the build).

The image serves `GET /healthz` on port 8080:

```bash
docker build -t dws-schedule-time .
```
