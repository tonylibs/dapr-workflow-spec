# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this package.

Root-level cross-cutting rules (commits, contract-change etiquette, per-package gate commands) are in
the repo root [`CLAUDE.md`](../CLAUDE.md). This file is dws-schedule-time-specific. The .NET style rules
are inherited from [`../dws-flow/CLAUDE.md`](../dws-flow/CLAUDE.md); read its style guide first. Purpose
and behaviour are in [`README.md`](README.md).

## Commands

```shell
cd dws-schedule-time
dotnet build
dotnet test test/dws-schedule-time.Tests.csproj
dotnet test test/dws-schedule-time.Tests.csproj --filter "FullyQualifiedName~AfterLifecycleTests"   # one class
```

Always name the test project. A bare `dotnet test` in this folder resolves to the app project, which has
no tests, so it exits 0 without running anything. No lint/format gate is configured (same as `dws-flow`).

## Style rules (inherited from dws-flow)

- Nullable reference types on; no `== null`/`!= null`, no `!` in `src/` (use `is null`, `??`,
  `IsNullOrWhiteSpace`, pattern matching).
- Catch only with a `when` filter naming the exception types, or a condition for background loops that must
  never crash the host (`when (!stoppingToken.IsCancellationRequested)`). No bare `catch (Exception)`.
- One-line `<summary>` per public type, no per-member XML docs.
- Tests: xUnit `[Fact]`/`[Theory]` + FluentAssertions, present-tense names, no mocking library: hand-written
  recording stand-ins live in `test/TestDoubles.cs` (`RecordingJobRegistry`, `InMemoryVersionedStore` with
  real ETag semantics, `RecordingStarter`, `ManualTime`).
- `Environment.` is read only in `Program.cs`, and wall-clock time only through an injected `TimeProvider`
  (`DateTime.UtcNow`/`Now` are banned too). `SourceScanTests` scans `src/` for both.

## Architecture: every Dapr/Kubernetes touch is behind a seam

| Seam | Production | Test stand-in |
|---|---|---|
| `IJobRegistry` | `DaprJobRegistry` (`DaprJobsClient`: get, list, schedule, delete) | `RecordingJobRegistry` |
| `IVersionedStore` (claims + `after` state; first-write ETags) | `DaprVersionedStore` | `InMemoryVersionedStore` |
| `IWorkflowStarter` | `OrchestratorInvokeStarter` (`HttpClient` from `CreateInvokeClient(token)`; tested with a recording `HttpMessageHandler` and a socket-level fake sidecar) | `RecordingStarter` |
| `IClusterState` | `ClusterSnapshot`, fed by `ClusterWatcher` (`KubernetesClient` list + watch) | `StaticClusterState` |

`Reconciler` (job set vs active triggers) and `AfterLifecycle` (activate/poll/one-shot) take these seams;
`ScheduledJobHandler` handles `dws-sched-*`; `JobDispatcher` routes `POST /job/{name}` by name prefix, after holding the callback (up to `ScheduleTimeOptions.InitialSyncWait`, 30 s) until the first full cluster list: Dapr delivers the ticks missed during downtime, and retries an overdue tick immediately, as soon as the app is reachable, so answering retry before the list completes loses them (live run task 9.4). The dispatcher reads the delivery time before waiting and passes it to `ScheduledJobHandler`, so the wait never moves a fire onto a later key.
`ReconcileLoop` reconciles on every `ChangeSignal` and every 60 s. `CrossAppWorkflowStarter` is deliberately
absent (ADR 0013 Amendment 1 rejected cross-app `DaprWorkflowClient` starts). `Program.cs` wiring has no unit
test; the live paths (`Dapr*`, `ClusterWatcher`) need a sidecar or cluster.

Job callbacks are mapped by hand (`app.MapPost("/job/{name}")`), not with the SDK's
`MapDaprScheduledJobHandler`, so the handler decides explicitly between 200 (done or dropped) and 500
(Dapr retries: 3 times, 10 s apart for an on-time fire; a tick replayed after downtime is overdue and its
retries arrive immediately, live run 9.4). Dapr does not retry 400/404.

## Cross-package contracts this package consumes

Change both sides together; none is a compile error.

- **Trigger ConfigMap** from `dws-controller`: label `dws.io/trigger=time`, key `trigger.json` =
  `{workflow, version, appId, kind, schedule}`. Fixtures in `test/fixtures/` must match controller output.
- **Drain marker**: Deployment ANNOTATION `dws.io/drain: "true"` (`StackApplier.markForDrain`/`isDraining`),
  not a label. Pinned by `ActiveTriggersTests.PinsTheDrainMarkerAsAnAnnotationWithValueTrue`. Managed
  orchestrator Deployments carry `dws.io/workflow`, `dws.io/version`, `dws.io/managed-by=dws-controller`.
- **Orchestrator start/status** (`dws-orchestrator` `WorkflowController`): `POST workflows/{name}/instances?instanceId=<id>`
  with body `{}` and `Content-Type: application/json` (400 without it); `202` = started, `200` = an instance with
  that ID already exists (any status), `400` = invalid ID. `GET workflows/instances/{id}` returns the
  `InstanceStatusResponse` `{instanceId, runtimeStatus, output}` (Java SDK enum names) or `404`. Pinned here by
  `OrchestratorInvokeStarterTests` and there by `WorkflowControllerTest`.
- **Instance IDs**: `<source>:<workflow>:<fire-key>` — `dws-sched:<w>:<yyyyMMddTHHmmssZ>` (key from `FireKeys`,
  which absorbs a little early delivery) and `dws-after:<w>:<seq>`; `dws-event` is reserved for Phase 6b
  `dws-schedule-event`. 1-256 characters of `[A-Za-z0-9:._-]`: `InstanceIds.IsValid` mirrors the orchestrator's
  regex, keep them identical.
- **Dapr service invocation**: `OrchestratorInvokeStarter.CreateInvokeClient(token)` wraps
  `DaprClient.CreateInvokeHttpClient`, which routes `http://<app-id>/<path>` to the sidecar's
  `/v1.0/invoke/<app-id>/method/<path>` with the query intact. A test composes the SDK's `InvocationHandler`
  to pin that rewrite.
- **`DAPR_API_TOKEN`**: stamped by the chart. `DaprClientBuilder` (jobs, state store) reads it from the
  environment, but `CreateInvokeHttpClient` does **not** (Dapr .NET SDK 1.18.10; live run task 9.5: every start
  got 401). So `Program.cs` reads it, the only `Environment.` read of a secret, and passes it to
  `CreateInvokeClient`; it is never put in `ScheduleTimeOptions`, which could be logged.
  `OrchestratorInvokeStarterTests.SendsTheDaprApiTokenThroughTheRealInvokeClient` checks the header on a real
  socket and `SourceScanTests.ProgramHandsTheDaprApiTokenToTheInvokeClient` pins the `Program.cs` wiring.
- **`DWS_LOG_FORMAT`**: `DwsJsonConsoleFormatter` is a copy of `dws-flow`'s, pinned by the same 12 golden cases
  in `DwsJsonConsoleFormatterTests`; it is one of the packages listed in `docs/log-format.md`.
