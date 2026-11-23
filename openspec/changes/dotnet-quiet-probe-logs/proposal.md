## Why

`dws-schedule-time` writes six `INFO` lines for every `/healthz` probe ([#206](https://github.com/tonylibs/dapr-workflow-spec/issues/206), Observability Phase 6 finding 11). In the live run, 1,656 of 1,662 lines (99.6%) came from probes. Each line carries a `trace_id`/`span_id` from the request `Activity`, but the scheduler exports no spans (finding 6), so no trace ever matches them. The bundled collector's `filelog` receiver ships about 70 such lines a minute per replica, and the scheduler's own messages are buried under them.

The cause is that `Program.cs` builds with `WebApplication.CreateBuilder` and has no `appsettings.json` and no log filter, so every `Microsoft.AspNetCore.*` category logs at `Information`. `dws-flow` has the same setup and the same `/healthz` route. It is not deployed yet (roadmap 2a′), but it will show the same noise once it is.

## What Changes

- **`dws-schedule-time` and `dws-flow`**: the `Microsoft.AspNetCore` log category defaults to `Warning`, as in the ASP.NET Core project template. The default applies in text and JSON mode. A `GET /healthz` writes no log line at `Information` or below.
- The default can still be overridden through standard .NET logging configuration, for example `Logging__LogLevel__Microsoft.AspNetCore=Information` on the container, so request logging can be turned back on while debugging without a rebuild.
- **`dws-schedule-time`**: a successful start logs one `Information` line from the scheduler, for an `every`/`cron` fire and for an `after` start, with the workflow and instance ID. Today the framework's `Request finished POST /job/... 200` line is the only trace a successful fire leaves in the logs, and the filter above removes it. The scheduler's own code logs only failures, refusals and job changes.
- Tests in both packages: a real `GET /healthz` writes no `Information` record, and a scheduler-owned `Information` record still gets through.
- `docs/log-format.md` notes the .NET framework-category default. `docs/roadmaps/observability.md` marks finding 11 as fixed.

Not breaking. No DSL, contract, chart value or deployed-resource change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `structured-log-correlation`: new requirement, owned by `dws-flow` and `dws-schedule-time`. ASP.NET Core framework categories log at `Warning` by default, so health probes and request lifecycle events write no log line, and the default stays overridable through configuration.
- `time-trigger-scheduling`: new requirement. A successful trigger start is logged at `Information` by the scheduler, so a fire stays visible in the logs without the framework request lines.

## Impact

- **Components**: `dws-schedule-time` (`src/Program.cs`, a small logging-setup helper, `ScheduledJobHandler`, `AfterLifecycle`, tests) and `dws-flow` (`src/Program.cs`, the same helper, tests). Each package keeps its own copy of the helper, as it already does for `DwsJsonConsoleFormatter`. There is no shared .NET library, and both packages still build on their own (`dotnet test test/<pkg>.Tests.csproj`).
- **DSL behaviour**: none. **Runtime interpretation**: none. Workflow execution, fire keys, job specs and start semantics are unchanged.
- **Deployed resources**: none. The chart, probes and env vars are unchanged. The fix is in code, so the image needs no `appsettings.json` and the Dockerfile is unchanged.
- **Log output**: fewer lines. `Microsoft.AspNetCore.*` `Information` records disappear, while `Warning` and above from those categories (for example unhandled-exception logs from `DeveloperExceptionPage`/`ExceptionHandler`) are still written. `dws-schedule-time` gains one `Information` line per successful start. Existing workflow definitions are unaffected.
- **Dependencies**: none new expected. The `/healthz` test uses Kestrel on an ephemeral loopback port and an in-test capturing `ILoggerProvider`, not `Microsoft.AspNetCore.Mvc.Testing`.

## Non-goals

- Adding an OTLP trace exporter to `dws-schedule-time` (finding 6). Scheduler-owned lines written inside a `POST /job/{name}` request still carry a request-`Activity` `trace_id` that matches no exported span. That needs a separate decision: export spans, or stop carrying IDs when nothing is exported.
- Changing probe paths, intervals or the chart's probe settings.
- Filtering by request path (for example, quieting only `/healthz`). The category default matches the template, and it also covers job callbacks and any route added later.
- Changing log levels in the Java, Node or Go packages.
