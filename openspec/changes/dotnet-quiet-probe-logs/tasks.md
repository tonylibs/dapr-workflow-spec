## 1. dws-schedule-time — framework log-level default

Validation: `cd dws-schedule-time && dotnet test test/dws-schedule-time.Tests.csproj`

- [x] 1.1 Add `test/LoggingSetupTests.cs` (RED). Build `ServiceCollection().AddLogging(b => LoggingSetup.Configure(b, config, format))` and assert:
  - With empty config, `Microsoft.AspNetCore.Hosting.Diagnostics`, `Microsoft.AspNetCore.Routing.EndpointMiddleware` and `Microsoft.AspNetCore.Http.Result.OkObjectResult` are disabled at `Information` and enabled at `Warning`.
  - `Dws.ScheduleTime.ClusterWatcher` is enabled at `Information`.
  - With `Logging:LogLevel:Microsoft.AspNetCore=Information` in the config, the framework categories are enabled at `Information` (operator override wins).
  - With `format = "json"` the `DwsJsonConsoleFormatter` is registered as the console formatter. With `null` it is not.
- [x] 1.2 Add `src/LoggingSetup.cs` with `Configure(ILoggingBuilder, IConfiguration, string? logFormat)` (design D1). It moves the existing JSON-formatter block out of `Program.cs` unchanged, and adds `AddFilter("Microsoft.AspNetCore", LogLevel.Warning)` only when `Logging:LogLevel:Microsoft.AspNetCore` is unset. It does not touch `Environment.`. Make 1.1 pass.
- [x] 1.3 Add a `MapHealthz(this IEndpointRouteBuilder)` extension with the existing `Results.Ok(new { status = "ok" })` body. In `Program.cs`, replace the inline formatter block with `LoggingSetup.Configure(builder.Logging, builder.Configuration, Environment.GetEnvironmentVariable("DWS_LOG_FORMAT"))` and `app.MapGet("/healthz", ...)` with `app.MapHealthz()`. Confirm `SourceScanTests` still pass.
- [x] 1.4 Add `test/HealthzLoggingTests.cs` (design D3). Set up a minimal `WebApplication` with `LoggingSetup.Configure`, a capturing `ILoggerProvider` and `MapHealthz()`, listening on `http://127.0.0.1:0` (bound address read from `IServerAddressesFeature`, app disposed with `await using`). Assert:
  - `GET /healthz` returns `200` and captures zero records at `Information` or below.
  - Control case: with the override config, at least one `Microsoft.AspNetCore.Hosting.Diagnostics` record is captured.
- [x] 1.5 Confirm no new `PackageReference` was needed, and that `dws-schedule-time.Tests.csproj` builds with Kestrel through the project reference. If a package turns out to be needed, add it pinned and note why in the PR.

## 2. dws-schedule-time — log successful starts

Validation: `cd dws-schedule-time && dotnet test test/dws-schedule-time.Tests.csproj`

- [ ] 2.1 Add `CapturingLogger<T>` to `test/TestDoubles.cs`. It records level, category and formatted message, and is thread-safe like the other doubles.
- [ ] 2.2 In `ScheduledJobHandlerTests` (RED), use `CapturingLogger<ScheduledJobHandler>` and assert:
  - A fire answered `202` writes one `Information` record containing the workflow, the fire-key instance ID and `HTTP 202`.
  - A replayed tick whose claim is already started writes one `Information` record with `already started`.
  - A `503` start writes no "started" `Information` record, and the existing error record is still written.
- [ ] 2.3 In `ScheduledJobHandler.HandleAsync`, log `Fire started workflow {Workflow} instance {InstanceId} ({Detail})` at `Information` on `StartOutcome.Started` (design D2). Make 2.2 pass.
- [ ] 2.4 In `AfterLifecycleTests` (RED), assert one `Information` record with the workflow and instance ID in three cases: the first `after` start (seq 1), the next start from `Waiting` (for example `dws-after:order-sync:7`), and a successful restart in the poll path. Assert none when a start fails.
- [ ] 2.5 Add the matching `LogInformation` at the three `claim.StartAsync` call sites in `AfterLifecycle.cs` (initial start, next start, restart). Make 2.4 pass.
- [ ] 2.6 Run the full `dotnet test test/dws-schedule-time.Tests.csproj` and confirm it is green, including `DwsJsonConsoleFormatterTests` (formatter unchanged) and `SourceScanTests`.

## 3. dws-flow — same framework log-level default

Validation: `cd dws-flow && dotnet test test/dws-flow.Tests.csproj`

- [ ] 3.1 Add `test/LoggingSetupTests.cs` mirroring 1.1, with `Dws.Flow.FlowWorkflow` as the own-category case (RED).
- [ ] 3.2 Add `src/LoggingSetup.cs` (a copy of the scheduler's, namespace `Dws.Flow`) and `MapHealthz`. In `Program.cs`, replace the inline formatter block and `/healthz` mapping with them. Make 3.1 pass.
- [ ] 3.3 Add `test/HealthzLoggingTests.cs` mirroring 1.4.
- [ ] 3.4 Run the full `dotnet test test/dws-flow.Tests.csproj` and confirm it is green.

## 4. Docs

- [ ] 4.1 `docs/log-format.md`: add a short note that the .NET packages default `Microsoft.AspNetCore` to `Warning`, so health probes and request lifecycle write no `INFO` lines. Say that `Logging__LogLevel__Microsoft.AspNetCore=Information` turns them back on.
- [ ] 4.2 `docs/roadmaps/observability.md`: mark finding 11 (#206) as fixed by this change. Do not edit any ADR.
- [ ] 4.3 Update the `dws-schedule-time` and `dws-flow` READMEs only if they describe logging behaviour or `/healthz` logging. Otherwise leave them alone.

## 5. Verification

- [ ] 5.1 Run `openspec validate dotnet-quiet-probe-logs --type change --strict` and fix any findings.
- [ ] 5.2 Optional live check, if a cluster with the chart is at hand. Deploy the built `dws-schedule-time` image with `DWS_LOG_FORMAT=json`, wait at least 5 minutes, and confirm `kubectl logs` shows no `Microsoft.AspNetCore.*` lines. With an `every: PT1M` workflow deployed, confirm one `Fire started ...` line per minute. Record the result in the PR. If there is no cluster, say so in the PR rather than claiming live evidence.
