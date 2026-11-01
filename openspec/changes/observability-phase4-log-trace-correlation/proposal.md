# Proposal

## Why

Phases 1 and 2a gave the control plane and the compiled orchestrator traces, but their logs are
still free-form text with no trace context: an operator who finds a slow or failed workflow run in
the trace backend cannot reach the log lines it produced, and the log lines carry nothing to join
on. Phase 4 of the observability roadmap closes that gap for the Track A components: every log line
written while a span is active carries that span's `trace_id` and `span_id` in a structured form a
collector can index. Phase 2a (ADR 0009) already established that activity code runs under a span
context, so the context to correlate with exists now.

## What Changes

- **A single structured-log contract**, implemented independently in `dws-controller`, `dws-admin`,
  `dws-orchestrator`, `dws-flow` and `dws-step`: in JSON mode each process writes exactly one JSON
  object per line to stdout with `timestamp`, `severity`, `logger`, `message`, and — only when a
  span context is active — `trace_id` and `span_id`. Field names, severity vocabulary, timestamp
  format and the omission rule are identical in all five packages and follow the OpenTelemetry log
  data model. Exceptions are carried inside the same object, never as extra lines.
- **One switch, one name**: every package reads `DWS_LOG_FORMAT` (`json` | `text`). Unset or
  unrecognised means `text`, which is each package's current output, so nothing changes for an
  install that does not opt in.
- **Secrets never reach a log line**: every existing log call site is audited, and each package's
  JSON formatter scrubs bearer tokens and credential-shaped key/value pairs as a defence in depth.
- **Chart value `observability.logs.format`** (`json` | `text`, default `json`, effective only when
  `observability.enabled` and `observability.logs.enabled` are true) is rendered as `DWS_LOG_FORMAT`
  on the controller and admin Deployments. `dws-controller` stamps the same value onto the
  orchestrator Deployment it compiles, on the same per-workflow condition that already gates
  tracing. The default and its reasoning are in `design.md`.
- **Logs travel one path: stdout → collector `filelog` receiver.** The OTLP log exporter of the
  OpenTelemetry agents is set to `none`, so a line is never delivered twice. The collector
  configuration this repository ships (the dev/eval fixture; see Impact) gains a `filelog` pipeline
  scoped to the DWS application containers that parses the JSON object into the OTLP log record, so
  `trace_id` and `span_id` land in the record's trace-context fields and a log backend can be queried
  by `trace_id`.
- **Evidence**: a live run on Tony's local cluster records one workflow trace and the log lines
  found for its `trace_id`, in `docs/roadmaps/observability-phase4-evidence.md`, and the roadmap's
  Phase 4 row is updated.

## Capabilities

### New Capabilities

- `structured-log-correlation`: the cross-package JSON log contract — field set and names, trace
  context population and omission, one-object-per-line, `DWS_LOG_FORMAT` semantics and default, and
  the secrets rule. Owned per package by `dws-controller`, `dws-admin`, `dws-orchestrator`,
  `dws-flow`, `dws-step`.
- `helm-log-format`: the chart surface — `observability.logs.format`, the `DWS_LOG_FORMAT`
  environment it renders, the agent OTLP log exporter being off, and the reference collector
  `filelog` configuration. Owned by `charts/dws`.

### Modified Capabilities

- `workflow-orchestrator-tracing`: an instrumented orchestrator Deployment additionally carries
  `DWS_LOG_FORMAT` when the controller was configured with a log format; an uninstrumented one is
  unchanged. Owned by `dws-controller`.
- `helm-controller-deployment`: the controller container gains `DWS_LOG_FORMAT` under the same
  condition. Owned by `charts/dws`.
- `helm-admin-deployment`: the admin container gains `DWS_LOG_FORMAT` under the same condition.
  Owned by `charts/dws`.

`helm-observability` (Phase 1) is still an unarchived change (`observability-template`), so it is
not under `openspec/specs/` and cannot be modified here; the chart requirements of this change live
in `helm-log-format` and should be reconciled with it when that change is archived.

## Impact

- **Packages and gates**: `dws-controller` (`./mvnw verify`), `dws-orchestrator` (`./mvnw verify`),
  `dws-step` (`./mvnw verify`), `dws-admin` (`pnpm lint && pnpm test && pnpm build`), `dws-flow`
  (`dotnet test test/dws-flow.Tests.csproj`), and `charts/dws` (the full chart gate in the root
  `CLAUDE.md`, Helm 4.3.0 for `observability-render-test.sh`). Build boundaries stay independent:
  the contract is specified, not shared as a library, so each package carries its own small
  implementation and its own conformance test against the same golden lines.
- **Cross-package contract added**: `DWS_LOG_FORMAT` and the six JSON field names. The same class of
  silent drift the root `CLAUDE.md` records for the orchestrator environment applies — a package
  that reads a different name falls back to text with no error — so the controller keeps a test that
  asserts the sibling `application.yaml` reads the name it stamps, mirroring
  `OrchestratorEnvContractTest`.
- **Deployed resources**: controller and admin Deployments (one env var, only when observability
  logs are on); orchestrator Deployment (one env var, only when instrumented); the `Instrumentation`
  resource's `OTEL_LOGS_EXPORTER` becomes `none`; the dev collector fixture. With
  `observability.enabled=false` the default render stays byte-identical to
  `tests/fixtures/default-render-baseline.yaml`.
- **DSL and runtime interpretation**: none. No workflow definition, content-addressed version,
  task-to-app-id mapping or step-service contract changes.
- **Dependencies**: `dws-controller` gains the Quarkus JSON logging extension (or a small custom
  formatter if the extension cannot emit top-level MDC keys — decided in the first task);
  `dws-admin` gains `@opentelemetry/api`; `dws-flow` gains none; the Spring Boot packages gain none.

## Non-goals and compatibility

- Step images (`dws-call-*`, `dws-run-*`; Go, Node, Python) stay out; they move with Phase 2b/3.
- Domain metrics (Phase 5) and the bundled collector subchart (Phase 6) stay out. The chart still
  ships no collector; `observability.collector.enabled` remains reserved.
- Dapr sidecar (`daprd`) logs are not reshaped and the `filelog` receiver excludes the `daprd`
  containers; their schema differs and is a separate follow-up.
- `dws-flow` and `dws-step` are not deployed by the chart or the controller yet (roadmap 2a′,
  blocked on runtime-v2 Phase 4). They implement the contract and are verified by their own tests
  and a local run; the cluster evidence comes from `dws-controller`, `dws-admin` and
  `dws-orchestrator`. Runtime-v2 Phase 4 synthesis must stamp `DWS_LOG_FORMAT`.
- Log lines emitted outside any span (startup, shutdown, scheduler ticks) carry no trace context by
  design. Lines for sampled-out traces carry a `trace_id` with no trace behind it.
- Existing installs and workflow definitions are unaffected: nothing changes unless
  `observability.enabled=true`, and per workflow only on its next deploy (the same accepted
  trade-off as the other stamped observability configuration).
