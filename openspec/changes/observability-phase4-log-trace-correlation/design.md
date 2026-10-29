# Design

## Context

Phase 4 of `docs/roadmaps/observability.md`, Track A half. The pieces it builds on already exist:

- Phase 1 renders the `Instrumentation` resource and the controller/admin annotations. It already
  declares `observability.logs.enabled` and a reserved `observability.logs.format: json` in
  `values.yaml`, and sets `OTEL_LOGS_EXPORTER` to `otlp` when logs are enabled.
- Phase 2a instruments the compiled orchestrator. [ADR 0009](../../../docs/adr/0009-trace-context-across-workflow-replay.md)
  found that activity code runs under a span (`activity||X` → `activity:X`), that the engine owns the
  workflow-level spans, and that workflow-function code must not be given spans of its own.
- Every `LOG` call site in `dws-orchestrator` is in an activity or in startup/shutdown code, none in
  `InterpreterWorkflow`. `dws-controller` and `dws-flow` have no application call sites at all
  (framework output only); `dws-step` has one bootstrap class; `dws-admin` has three `Logger` users
  and a migration script using `console`.
- The chart ships no collector. The only collector configuration in the repository is the dev/eval
  fixture `charts/dws/tests/fixtures/otel-collector-dev.yaml`, which already has an unscoped
  `filelog` receiver that feeds only the `debug` exporter, and which would read its own `debug`
  output back in (a feedback loop) because it includes every pod.
- `dws-flow` and `dws-step` have no Deployment anywhere yet (roadmap 2a′), so nothing sets their
  environment today.

See `proposal.md` for motivation and scope.

## Goals / Non-Goals

**Goals:**

- One log line format, specified once, implemented independently in five packages, and proven
  identical by the same golden lines run in each package's tests.
- A `trace_id` taken from a workflow run's trace finds that run's log lines in a log backend.
- Zero change to any install that does not turn observability on.

**Non-Goals:**

- A shared logging library (would break the independent build boundaries).
- Reshaping `daprd` logs, adding `service.name` to log records, log-based metrics, retention or
  index tuning in the backend.
- Making `dws-flow`/`dws-step` deployable; only their logging contract lands here.

## Decisions

### D1. One contract, five small implementations — not a shared library

The packages are separate stacks (Quarkus, Spring Boot, NestJS, .NET) and the repo has no root build.
A published logging library per stack, or a polyglot one, would add a release path nothing else here
has. Instead the contract is the spec (`structured-log-correlation`), and each package carries a
formatter of its own plus a **conformance test**: the same set of input records (plain, in-span,
out-of-span, exception, multi-line, bearer token, credential key) and the same expected key sets,
written once in the spec scenarios and reproduced as fixtures in each package. Drift is caught by
five green test suites rather than by a runtime check. Alternative considered: OTel SDK log
appenders per language emitting OTLP directly — rejected in D5.

### D2. The contract and its mapping to the OTel log data model

| JSON key | OTel log data model field | Rule |
|---|---|---|
| `timestamp` | Timestamp | RFC 3339 UTC, millisecond precision or finer |
| `severity` | SeverityText | `TRACE` `DEBUG` `INFO` `WARN` `ERROR` `FATAL`; the collector derives SeverityNumber |
| `message` | Body | Fully rendered message, newlines escaped |
| `trace_id` | TraceId | 32 lowercase hex; key omitted when there is no valid context |
| `span_id` | SpanId | 16 lowercase hex; omitted with `trace_id` |
| `logger` | attribute `logger.name` | Logging category / class / type name |
| `exception.type` / `.message` / `.stacktrace` | attributes of the same names | OTel exception semantic conventions; present only with an exception |

Per-runtime severity mapping: JBoss `WARNING`→`WARN`, `FATAL`→`FATAL`, `CONFIG`/`FINE`→`DEBUG`,
`FINER`/`FINEST`→`TRACE`; Logback `WARN`, no native `FATAL`; Nest `log`→`INFO`, `verbose`→`TRACE`,
`fatal`→`FATAL`; .NET `Trace`/`Debug`/`Information`/`Warning`/`Error`/`Critical` →
`TRACE`/`DEBUG`/`INFO`/`WARN`/`ERROR`/`FATAL`. Extra keys (thread name, MDC entries, `trace_flags`)
are allowed so long as they do not reuse the six names; a formatter that gets them for free from its
framework need not suppress them.

The six names plus the `exception.*` trio are a compatibility surface in the sense of ADR 0005:
dashboards and collector parsers key off them, so they are renamed only deliberately.

### D3. `DWS_LOG_FORMAT`: package default `text`, chart default `json`

Two different defaults, deliberately, at two different layers.

- **Each package defaults to `text` when `DWS_LOG_FORMAT` is unset or unrecognised.** Unset means
  "today", and the chart's disabled render is byte-identical to the Phase 1 baseline, so an install
  that never opts in cannot observe any change. An unrecognised value falls back rather than failing
  startup: a typo in a logging switch must not take a control-plane pod down.
- **The chart's `observability.logs.format` defaults to `json`.** The value only takes effect when
  `observability.enabled` and `observability.logs.enabled` are true, so the default alters nothing for
  existing installs. For an operator who has switched observability on, `json` is the default that
  delivers what they turned it on for: `trace_id` as a first-class, collector-parseable field.
  Defaulting to `text` would make the headline capability a second, easily-forgotten opt-in and
  produce "I have traces but cannot find the logs" reports; correlating text lines needs a per-line
  regular expression in the collector, which breaks on the first message that contains the pattern.
  The cost of `json` is `kubectl logs` readability; `observability.logs.format=text`, or piping
  through `jq`, is the documented answer. This matches the default the roadmap's values table has
  carried since Phase 1.

`DWS_LOG_FORMAT` is the only new environment contract name. It is read with the same case-insensitive
trim in every package, and is added to the root `CLAUDE.md` cross-package contract notes.

### D4. How the value reaches compiled pods

`observability.logs.format` → `DWS_LOG_FORMAT` on the controller Deployment (chart) → the controller
logs with it itself, **and** stamps `DWS_LOG_FORMAT=json` onto the orchestrator Deployment it
compiles, only when that deploy is already instrumented (the existing `ObservabilitySettings.enabled`
gate from the `dws-controller-config` store) and the controller's own value is `json`. Text is
expressed by absence. An existing `DWS_LOG_FORMAT` in the compiled environment is never overwritten,
the same rule `OTEL_SERVICE_NAME` follows.

Alternatives considered:

- **A key in the `dws-controller-config` store.** That store's values are set out of band; the chart
  cannot template them, so the chart value and the store key could disagree and the operator would
  have two places to set one thing. Rejected. The roadmap's "not controller env vars" rule is about
  the per-workflow on/off flag the operator flips at runtime; this is a chart-owned deployment value,
  and the precedent for such values is `DWS_ORCHESTRATOR_SERVICE_ACCOUNT`.
- **Stamp on every orchestrator regardless of the instrumentation flag.** Would change uninstrumented
  orchestrators, breaking the "off equals today" equality the existing `StackSynthesizerTest` pins,
  for lines that have no trace context anyway.

Consequence, stated because it will be reported as a bug otherwise: like every other stamped
observability value, a format change reaches a given workflow only on its next deploy. The env var
lives on the Deployment, not in the definition ConfigMap, so **content-addressed definition versions
are unaffected**. The controller keeps a contract test that the sibling `dws-orchestrator`
`application.yaml` reads the name it stamps, mirroring `OrchestratorEnvContractTest`, because a
mismatch degrades silently to text.

Runtime-v2 Phase 4 deploy synthesis must stamp the same variable on `dws-flow` and `dws-step`
Deployments; that requirement is recorded in the roadmap's 2a′ row by this change.

### D5. One transport: stdout → `filelog`; agent OTLP log export off

`OTEL_LOGS_EXPORTER` in the `Instrumentation` resource becomes `none` unconditionally. With agent
OTLP log export left on, every line in JSON mode would arrive twice (once via the agent, once via
`filelog`), and the two copies differ (the agent copy has no `logger`/exception keys in this
contract's shape), which makes "the log lines for this trace" ambiguous. Choosing `filelog` as the
sole path also keeps `kubectl logs` and the backend showing the same record, covers `dws-flow`
(`.NET`) and `dws-step` without per-language OTLP log bridges, and needs no code in the process
beyond writing the line. Phase 1's live run already observed that the stock agents emitted no
separate OTLP log records. Cost: log delivery depends on a collector that can read node log files
(an agent or DaemonSet-style collector), which the dev fixture already assumes. `observability.logs.enabled`
keeps its meaning of "emit and deliver logs": it gates `DWS_LOG_FORMAT`; it no longer selects an
agent exporter.

### D6. Where the `filelog` configuration lives

"The chart's collector config" does not exist: the chart ships no collector (Phase 6, out of scope).
The one collector configuration in the repository is the dev fixture, so that is where the receiver
is changed, and the same receiver block is copied into the chart README as the reference an operator
adapts for their own collector. A render-test assertion reads the fixture so it cannot silently lose
the operators.

The receiver, replacing the unscoped one:

- `include` limited to the release namespace's pod directories; `exclude` the `daprd` container
  (different schema, out of scope) and the collector's own container (breaks the feedback loop).
- `container` operator (CRI parsing, `k8s.*` attributes from the path), then — only for bodies that
  start with `{` — `json_parser` with `timestamp`, `severity`, and `trace_parser`/`span_parser` bound
  to `trace_id`/`span_id`, moving `message` to the body and `logger` to `logger.name`. A non-JSON body
  passes through unparsed, which is the `text` mode and the startup banner of any non-DWS line.

The backend added to the fixture is **Loki** (named in the roadmap as a target backend; accepts OTLP
logs and stores `trace_id` as structured metadata, so it is queryable without being a label) behind an
`otlphttp` exporter on the logs pipeline, alongside the existing `debug` exporter. Alternative: leave
the `debug` exporter as the only sink — rejected, because "find the log lines by `trace_id`" needs a
queryable store and grepping collector output is not that. The exact Loki version and the OTLP
endpoint form are verified on the cluster before being pinned (see Open Questions).

### D7. Per-package mechanism

The contract is fixed; the mechanism is each stack's idiom. Where a mechanism depends on a library
behaviour that was not verifiable while writing this design, the first task of that package is a
short spike with a defined fallback, and the conformance test is the acceptance criterion.

| Package | Preferred mechanism | Verify first / fallback |
|---|---|---|
| `dws-orchestrator`, `dws-step` (Spring Boot 4.1.0, Logback) | Boot structured logging with a small custom `StructuredLogFormatter` class selected by `logging.structured.format.console`, activated from `DWS_LOG_FORMAT`. Trace IDs come from the agent's Logback MDC instrumentation (`trace_id`, `span_id` keys). The custom class (rather than the built-in `logstash` format plus `rename`) is needed because the scrubber (D8) cannot be expressed as properties | Confirm a blank/unset `logging.structured.format.console` leaves plain logging; if not, a tiny `EnvironmentPostProcessor` sets the property from `DWS_LOG_FORMAT` |
| `dws-controller` (Quarkus 3.37.4, JBoss Logging) | `quarkus-logging-json` console formatter with `key-overrides` to the contract names, MDC from the agent's JBoss logmanager MDC instrumentation | The extension nests MDC under an `mdc` object as far as known, which would violate "identical top-level names". If it cannot emit top-level keys, replace the console handler with a small custom formatter. Acceptance: records logged during boot are also one-line JSON, and the controller still starts in `quarkus:dev` and as the JVM image. Native images are not instrumented (roadmap) and not in scope |
| `dws-admin` (NestJS 11) | A `LoggerService` implementation installed with `bufferLogs` + `app.useLogger`; trace context read from `@opentelemetry/api` `trace.getActiveSpan()`, which resolves to the global registered by the Operator's Node agent | Confirm the API singleton is shared with the injected agent in the container image; the `console` calls in the migration script are a CLI, not the server, and stay |
| `dws-flow` (.NET 10) | A named `ConsoleFormatter` registered in `Program.cs`, reading `Activity.Current` (`TraceId`/`SpanId`, omitted when `default`) | Confirm whether `Activity.Current` is set inside a Dapr Workflow activity under `Dapr.Workflow` 1.18.10. If it is not, ship the format and document that worker-thread lines carry no context, rather than inventing propagation; the flow package has no call sites today so this affects only future lines |

The Java formatters read the OTel context through MDC rather than the OTel API so the packages take
no OTel dependency and degrade to "no `trace_id`" when no agent is attached.

#### Spike findings (recorded during apply)

- **6.1 — `dws-flow`: `Activity.Current` is set inside a Dapr Workflow activity** under
  `Dapr.Workflow` 1.18.10. The SDK's internal `Dapr.Workflow.Worker.WorkflowTrace` ("restores
  workflow trace context as `Activity.Current` for workflow and activity dispatch") owns an
  `ActivitySource` named `Dapr.Workflow`; `GrpcProtocolHandler.ProcessActivityAsync` calls
  `WorkflowTrace.StartActivityTrace(ActivityRequest)`, which parses the request's
  `ParentTraceContext` (`traceparent`/`tracestate`) with `ActivityContext.TryParse` and starts an
  `Activity` under it; a scope restores the previous `Activity.Current` on dispose. So activity log
  lines carry the workflow's trace context whenever the sidecar sends one. Evidence: IL of the
  referenced `net10.0` assembly, not a live sidecar run; with no inbound context, lines carry no or a
  fresh root context. No fallback needed — the formatter reads `Activity.Current` as designed.

### D8. Secrets and bearer tokens

The audit of today's call sites found no secret-bearing arguments: the orchestrator logs app IDs,
paths, pub/sub and topic names, definition keys and node IDs; `secretScope` documents that secret
values are deliberately never logged; the admin's three call sites log event types and a validation
message; the controller has none. The rule is therefore preventive, enforced two ways:

1. **Call-site audit as a task**, repeated against the final diff, covering exception messages that
   can embed a URL or header from an HTTP/Dapr client.
2. **A scrubber in each JSON formatter** (defence in depth, applied to `message`, `exception.message`
   and `exception.stacktrace`) replacing `Bearer <token>` / `Basic <token>` and the value of
   credential-named keys (`authorization`, `password`, `passwd`, `secret`, `token`, `api-key`,
   `apikey`, `client_secret`) in `key=value`, `key: value` and `"key":"value"` forms with a fixed
   `[REDACTED]`. It runs before escaping, so the JSON stays valid.

A scrubber cannot recognise an unlabelled secret, and the text format does not get it. That limit is
accepted and written down: the guarantee for text mode, and for anything the patterns do not name, is
the call-site rule, not the scrubber. A test per package logs a bearer token and a `password=` pair
and asserts neither value is in the output.

### D9. Replay

Following ADR 0009 decisions 1–2, no log call is added inside workflow-function code
(`InterpreterWorkflow` and the child workflows): it re-executes on replay, so a line there would be
repeated, and it does not run under a span the engine keeps stable. All existing call sites are in
activities, which run under the `activity:<name>` span and therefore carry context. This is recorded
as a rule in `dws-orchestrator`'s `CLAUDE.md` rather than enforced by a test.

### D10. Evidence

The "done" criterion is a live run, not a render test. It reuses the Phase 2a procedure
(`observability-phase2a-evidence.md`, "Reproducing the probe") with the new collector fixture:
`observability.enabled=true`, `observability.traces.samplingRate=1` for the run (the default `0.1`
would leave most `trace_id`s with no trace), a deployed workflow, one started instance, the trace ID
read from Jaeger, and the same ID queried in Loki. Recorded in
`docs/roadmaps/observability-phase4-evidence.md` with the deviations from the procedure.

## Risks / Trade-offs

- **Quarkus cannot emit top-level MDC keys** → D7 fallback (custom formatter); the conformance test
  decides, and the task is first so the cost is learned before anything depends on it.
- **`Activity.Current` absent in the .NET worker** → format ships, limitation documented; no
  cluster evidence for `dws-flow` either way because it is not deployed.
- **JSON default is harder to read in `kubectl logs`** → opt-in only, `text` is one value away.
- **Unsampled traces still stamp a `trace_id`** (the agent's MDC injects any valid context) → logs
  can point at a trace that does not exist. Inherent to parent-based sampling at `0.1`; documented in
  the operator guidance, and the reason the evidence run samples at `1`.
- **Log volume and index cost** (every line is now a record with trace attributes) → `observability.logs.enabled=false`
  turns the whole path off; backend retention is the operator's.
- **Loki OTLP specifics** (version, structured-metadata default, endpoint path) are unverified from
  here → pinned only after the live run; the fixture is dev-grade by design.
- **Collector must read node files** → documented prerequisite; same as the dev fixture's hostPath.
- **A package ignores a renamed variable silently** → controller contract test (D4) plus the identical
  conformance fixtures.
- **Controller and admin Deployments gain an env var** → pods restart on upgrade; only when
  observability is on.

## Migration Plan

1. Land package changes and the chart together; nothing is observable until `observability.enabled=true`.
2. Operators with observability already on pick up `json` on `helm upgrade`; controller/admin roll.
   Already-deployed workflows keep text logs until each is next deployed. Roll back by setting
   `observability.logs.format=text` (same per-workflow timing).
3. Re-record `default-render-baseline.yaml` only if the render test says the default render changed;
   it should not.
4. Update the roadmap Phase 4 row, add the 2a′ stamping note, add `DWS_LOG_FORMAT` to the root
   `CLAUDE.md` cross-package notes, and document the filelog block in the chart README.

## Open Questions

- Loki version and OTLP endpoint form for the fixture — answered by the live run, does not alter the
  specs.
- Whether a `k8sattributes`-style step should attach `service.name` (the Dapr app ID) to log records
  so backends can group by service — deferred; `trace_id` is the join key this phase promises.
- Whether `dapr.io/log-as-json` on the sidecars should join the `filelog` scope later — deferred
  follow-up.
