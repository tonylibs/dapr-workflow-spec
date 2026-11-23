## Context

`dws-schedule-time` and `dws-flow` are the two .NET 10 packages. Both `Program.cs` files call `WebApplication.CreateBuilder(args)`, add `DwsJsonConsoleFormatter` when `DWS_LOG_FORMAT=json`, and map `GET /healthz`. Neither ships an `appsettings.json` (the Dockerfiles copy only `dotnet publish` output) or adds a log filter. So the default `Information` minimum applies to every category. ASP.NET Core then writes six records per request:

- `Hosting.Diagnostics`: request starting and finished
- `Routing.EndpointMiddleware`: executing and executed
- `Http.Result.OkObjectResult`: setting and writing

`DwsJsonConsoleFormatter` correctly stamps the request `Activity` IDs on those records. The scheduler exports no spans, so the IDs match no trace.

The scheduler's own code logs failures, refusals and job registry changes. It logs nothing when a start succeeds. Every `claim.StartAsync` call site:

- `ScheduledJobHandler.HandleAsync`: the `every`/`cron` fire
- `AfterLifecycle`: the first `after` start (seq 1), the next start from `Waiting`, and the restart in `PollRunningAsync`

Each of these only checks for a non-`Started` outcome. A `Started` result has three possible `Detail` values: `HTTP 202`, `HTTP 200` (already exists at the orchestrator) and `already started` (the claim says so). Today the only log evidence of a successful fire is the framework line `Request finished HTTP/1.1 POST /job/dws-sched-<wf> - 200`.

The tests use `NullLogger<T>.Instance`. Neither test project captures log output, references `Microsoft.AspNetCore.TestHost`/`Mvc.Testing`, or starts the web app. `SourceScanTests` forbids `Environment.` outside `Program.cs`.

Relevant in-force ADRs: 0013 (time triggers in `dws-schedule-time`) sets the scheduler's architecture. This change does not touch it. Nothing in `docs/adr/` governs log levels.

## Goals / Non-Goals

**Goals:**
- `GET /healthz` writes nothing at `Information` or below in either package, in text and JSON mode.
- Operators can still turn framework request logging back on through standard .NET configuration, with no rebuild.
- A successful scheduler start stays visible in the logs.
- Both packages behave the same way, each keeping its own code (no shared library).

**Non-Goals:**
- OTLP span export from `dws-schedule-time` (finding 6), and the request-`Activity` IDs on scheduler lines written during `POST /job/{name}`.
- Chart, probe or Dockerfile changes.
- Path-based filtering, or log-level changes in non-.NET packages.

## Decisions

### D1. Default the category in code, only when configuration does not set it

Each package gets a small static helper, `LoggingSetup.Configure(ILoggingBuilder logging, IConfiguration configuration, string? logFormat)`. It lives in `Dws.ScheduleTime` and in `Dws.Flow`, one copy per package, next to `DwsJsonConsoleFormatter`. It:

1. Registers the JSON console formatter when `DwsJsonConsoleFormatter.IsJsonMode(logFormat)`. This moves the existing `Program.cs` block unchanged.
2. Adds `logging.AddFilter("Microsoft.AspNetCore", LogLevel.Warning)` **only if** `configuration["Logging:LogLevel:Microsoft.AspNetCore"]` is null.

`Program.cs` reads `DWS_LOG_FORMAT` and passes it in, so `Environment.` stays in `Program.cs` and `SourceScanTests` stays green.

The guard is needed because of rule order. `CreateBuilder` registers the configuration-sourced filter rules first, and a code `AddFilter` rule for the same category and provider comes after them. In `LoggerFilterOptions` the later rule wins a tie. An unconditional `AddFilter` would therefore silently override the documented workaround `Logging__LogLevel__Microsoft.AspNetCore=Information`. A provider-specific entry such as `Logging:Console:LogLevel:Microsoft.AspNetCore` already beats a provider-less code rule, because rule selection prefers a provider match. So the guard needs to check only the provider-less key. The implementation must confirm this precedence in a test (see D3), not trust the reasoning alone.

Alternatives considered:
- **Ship `appsettings.json`** with `Logging:LogLevel:Microsoft.AspNetCore=Warning`, as the template does. Env vars override it naturally. But the file has to reach the image and the test output. The Web SDK copies it on publish, but the test project would run from its own content root, so a test that passes locally could still drift from the image. A missing file fails silently and brings the noise back. A code default cannot go missing.
- **Unconditional `AddFilter`**: simplest, but it breaks the operator override (see above).
- **`builder.Configuration.AddInMemoryCollection(...)`**: appended last, so it outranks env vars. Inserting it at source index 0 works, but it relies on configuration-source ordering, which is harder to read than the explicit guard.
- **Filter by path (`/healthz` only)**, for example by suppressing `Hosting.Diagnostics` per request: more code, and it leaves job-callback and Routing/Result noise in place. The template-level category default is the familiar .NET convention.

### D2. Log every `Started` outcome at each start call site

At each of the four call sites in Context, add one `LogInformation` when `result.Outcome == StartOutcome.Started`:

- `ScheduledJobHandler`: `Fire started workflow {Workflow} instance {InstanceId} ({Detail})`
- `AfterLifecycle`, initial start, next start and restart: `After start succeeded for workflow {Workflow} instance {InstanceId} ({Detail})`. The restart variant may say "Restarted".

The detail tells a fresh start (`HTTP 202`) apart from an idempotent replay (`HTTP 200`, `already started`). So a replayed burst shows as one 202 line plus "already started" lines, which helps operators.

Alternative: log inside `StartClaim.StartAsync`, at one site. Rejected because `StartClaim` does not know whether the caller is a fire or an `after` step, and the existing failure messages already sit at the call sites. Keeping success next to failure keeps each handler's log story in one place.

### D3. Tests

Both packages:
- **`LoggingSetupTests`** (unit): build a `ServiceCollection().AddLogging(b => LoggingSetup.Configure(b, config, format))` and resolve `ILoggerFactory`. Then:
  - With empty config: `Microsoft.AspNetCore.Hosting.Diagnostics` and `Microsoft.AspNetCore.Routing.EndpointMiddleware` are not enabled at `Information` but are at `Warning`. `Dws.ScheduleTime.ClusterWatcher` / `Dws.Flow.FlowWorkflow` are enabled at `Information`.
  - With `Logging:LogLevel:Microsoft.AspNetCore=Information` in the config: the framework category is enabled at `Information`. This pins the override precedence from D1.
  - With `logFormat` `json` / null: the formatter is registered or not. The existing `DwsJsonConsoleFormatterTests` stay untouched.
- **`HealthzLoggingTests`** (integration): build a minimal `WebApplication` with `WebApplication.CreateBuilder()`, call `LoggingSetup.Configure(builder.Logging, builder.Configuration, null)`, and add a capturing `ILoggerProvider`. It maps the same `/healthz` lambda through a shared `MapHealthz(this IEndpointRouteBuilder)` extension that `Program.cs` uses too, so the test exercises the real route. The app listens on `http://127.0.0.1:0`. The test sends `GET /healthz`, asserts `200`, and asserts the capture has no record at `Information` or below. A control case runs the same setup with the override config and asserts `Hosting.Diagnostics` records do appear, which proves the capture is wired. This adds no NuGet package: Kestrel comes from the `Microsoft.AspNetCore.App` framework reference, which flows from the Web-SDK project reference.

  The test builds its own minimal app rather than running the full `Program`, because the scheduler's `Program` starts `ClusterWatcher` (Kubernetes) and Dapr clients, and `dws-flow`'s loads a definition file. Sharing `LoggingSetup` and `MapHealthz` keeps the tested path identical to production.
- **`dws-schedule-time` handler tests**: add a `CapturingLogger<T>` to `TestDoubles.cs`. `ScheduledJobHandlerTests` and `AfterLifecycleTests` then assert one `Information` record with the workflow and instance ID for 202 and for "already started", and none for 503.

## Risks / Trade-offs

- [Warnings that used to sit next to request lines lose their context] → `Warning`+ framework records are still written. Unhandled exceptions are logged at `Error` by the diagnostics middleware.
- [D1's precedence reasoning is wrong for some .NET build] → the override unit test fails loudly. Fall back to the configuration-source approach.
- [Lines that some dashboard or alert relies on disappear] → no repo alert or dashboard matches these messages. The roadmap's Phase 6 evidence counted them as noise.
- [Scheduler lines during `POST /job` still carry IDs that match no span] → explicit non-goal, tracked with finding 6.
- [Ephemeral-port test is flaky on CI] → bind to `127.0.0.1:0`, read the bound address from `IServerAddressesFeature`, and dispose the app in `finally`/`await using`.

## Migration Plan

Code-only change in two images. On rollout the log volume drops immediately, and nothing needs migrating. To see framework request lines again, set `Logging__LogLevel__Microsoft.AspNetCore=Information` on the container. Rollback means redeploying the previous image tag.

## Open Questions

- None blocking. A later change should decide whether `dws-schedule-time` exports spans or stops carrying request IDs (finding 6).
