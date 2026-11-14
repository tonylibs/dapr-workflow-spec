# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this package.

Root-level cross-cutting rules (commits, contract-change etiquette, per-package gate commands) are in
the repo root [`CLAUDE.md`](../CLAUDE.md). This file is dws-schedule-time-specific idioms only.

`dws-schedule-time` fires `schedule` time triggers (`every`, `cron`, `after`) for v1 workflows
(scheduling Phase 6a, [ADR 0013](../docs/adr/0013-time-triggers-in-dws-schedule-time.md), OpenSpec
change `scheduling-phase-6a-time-triggers`). It is early: today it holds the scaffold and the caller
side of the workflow start contract (`InstanceIds`, `FireKeys`, `IWorkflowStarter`,
`OrchestratorInvokeStarter`). The stack and layout copy `dws-flow`; its
[`CLAUDE.md`](../dws-flow/CLAUDE.md) style rules apply here unless this file says otherwise.

## Commands

```shell
cd dws-schedule-time
dotnet build
dotnet test test/dws-schedule-time.Tests.csproj
dotnet test test/dws-schedule-time.Tests.csproj --filter "FullyQualifiedName~FireKeysTests"   # one class
```

Always name the test project. A bare `dotnet test` in `dws-schedule-time/` resolves to
`dws-schedule-time.csproj`, which compiles none of `test/**`, so it runs nothing and exits 0.

No lint/format gate is configured (same as `dws-flow`); raise adding one as a separate decision.

## Style rules inherited from `dws-flow`

- Nullable reference types are on: no `== null`/`!= null`, no `!`; use `??`, pattern matching,
  `IsNullOrWhiteSpace`.
- No bare `catch (Exception)`: only `when`-filtered catches naming the types the call can throw
  (`OrchestratorInvokeStarter` filters transport failures and leaves the caller's cancellation alone).
- One `<summary>` line per public type, no per-member XML docs; non-obvious mechanisms get a plain
  comment in place.
- Tests: xUnit + FluentAssertions, present-tense names (`Rejects…`, `Maps…`), no mocking library.
  Seams are faked with small hand-written stand-ins (a recording `HttpMessageHandler` for the starter).
- `Environment.` is read only in `Program.cs`; `SourceRulesTests` scans `src/` for it. Pass values
  into classes instead.

## Cross-package contracts this package consumes

Changing one side without the other is not a compile error; it fails at runtime.

- **Orchestrator start endpoint** (`dws-orchestrator` `WorkflowController`):
  `POST workflows/{name}/instances?instanceId=<id>` with `Content-Type: application/json` (the
  orchestrator answers `400` without it). `202` = started, `200` = an instance with that ID already
  exists (any status), `400` = invalid ID. `GET workflows/instances/{id}` returns
  `{instanceId, runtimeStatus, output}` or `404`. Pinned here by `OrchestratorInvokeStarterTests` and
  there by `WorkflowControllerTest`.
- **Instance ID shape**: `<source>:<workflow>:<fire-key>`, 1-256 characters of `[A-Za-z0-9:._-]`,
  the orchestrator's validation. `InstanceIds.IsValid` mirrors that regex; keep them identical.
  Sources: `dws-sched`, `dws-after`, `dws-event` (reserved for Phase 6b `dws-schedule-event`, which
  reuses the contract unchanged).
- **Dapr service invocation**: `DaprClient.CreateInvokeHttpClient()` routes `http://<app-id>/<path>`
  to the sidecar's `/v1.0/invoke/<app-id>/method/<path>` with the query intact and adds
  `DAPR_API_TOKEN` when set. A test composes the SDK's `InvocationHandler` to pin that rewrite.
- **`DWS_LOG_FORMAT`** ([`docs/log-format.md`](../docs/log-format.md)): `DwsJsonConsoleFormatter` is a
  port of `dws-flow`'s; `DwsJsonConsoleFormatterTests` runs the 12 golden cases. Change keys in all
  packages together.
