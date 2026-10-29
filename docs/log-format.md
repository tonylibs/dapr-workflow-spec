# DWS structured log format

`dws-controller`, `dws-admin`, `dws-orchestrator`, `dws-flow` and `dws-step` share one structured
log format so that a log line written during a traced operation can be found from that trace's
`trace_id`, and a collector can index every component's lines without per-package parsing.

The format is a **contract, not a library**: each package carries its own small formatter and its own
conformance test over the golden cases below. Spec: `structured-log-correlation`
(`openspec/changes/observability-phase4-log-trace-correlation`, archived under `openspec/specs/` once
the change lands). Design rationale: that change's `design.md`.

## Switch: `DWS_LOG_FORMAT`

| Value (trimmed, case-insensitive) | Mode |
|---|---|
| `json` | JSON mode: one JSON object per line on stdout, as specified below. |
| unset, empty, `text`, or anything else | Text mode: the package's output before this contract existed. |

An unrecognised value never fails startup. In the chart, `observability.logs.format` (default `json`,
effective only when `observability.enabled` and `observability.logs.enabled` are both true) renders
`DWS_LOG_FORMAT` on the controller and admin; the controller stamps `DWS_LOG_FORMAT=json` on the
orchestrator Deployments it compiles when that deploy is instrumented and its own value is `json`.

## Record shape

Every record is exactly one JSON object followed by `\n`. Nothing else the logging system writes
goes to stdout, and no record spans more than one line: newlines inside values are JSON-escaped.

| Key | OTel log data model | Rule |
|---|---|---|
| `timestamp` | Timestamp | RFC 3339 UTC instant with a `Z` suffix, millisecond precision or finer. |
| `severity` | SeverityText | One of `TRACE` `DEBUG` `INFO` `WARN` `ERROR` `FATAL`. |
| `logger` | attribute `logger.name` | The logging category / class / context name. |
| `message` | Body | The fully rendered message, after scrubbing. |
| `trace_id` | TraceId | 32 lowercase hex. **Omitted** (not empty, not zeroed) when no valid span context is active. |
| `span_id` | SpanId | 16 lowercase hex. Omitted together with `trace_id`. |
| `exception.type` | attribute | Fully qualified exception type name. Present only when the record has an exception. |
| `exception.message` | attribute | The exception's message, after scrubbing. Present with `exception.type`. |
| `exception.stacktrace` | attribute | The whole stack trace as one escaped string, after scrubbing. Never extra stdout lines. |

Recommended key order is the table order; consumers must not depend on it. Additional keys (thread
name, MDC entries, `trace_flags`, ...) are allowed as long as they do not reuse any of the nine names
above. These names are a compatibility surface (ADR 0005): dashboards and the collector's `filelog`
operators key off them.

### Severity mapping

| Runtime | Native level → `severity` |
|---|---|
| JBoss Logging (`dws-controller`) | `FATAL`→`FATAL`, `ERROR`→`ERROR`, `WARN`/`WARNING`→`WARN`, `INFO`→`INFO`, `DEBUG`/`CONFIG`/`FINE`→`DEBUG`, `TRACE`/`FINER`/`FINEST`→`TRACE` |
| Logback (`dws-orchestrator`, `dws-step`) | `ERROR` `WARN` `INFO` `DEBUG` `TRACE` map to themselves; no native `FATAL` |
| NestJS (`dws-admin`) | `fatal`→`FATAL`, `error`→`ERROR`, `warn`→`WARN`, `log`→`INFO`, `debug`→`DEBUG`, `verbose`→`TRACE` |
| .NET (`dws-flow`) | `Critical`→`FATAL`, `Error`→`ERROR`, `Warning`→`WARN`, `Information`→`INFO`, `Debug`→`DEBUG`, `Trace`→`TRACE` |

### Trace context source

| Package | Source |
|---|---|
| `dws-controller` | MDC `trace_id` / `span_id` (OTel Java agent's JBoss logmanager MDC instrumentation) |
| `dws-orchestrator`, `dws-step` | MDC `trace_id` / `span_id` (OTel Java agent's Logback MDC instrumentation) |
| `dws-admin` | `trace.getActiveSpan()?.spanContext()` from `@opentelemetry/api` (global registered by the Node agent) |
| `dws-flow` | `Activity.Current` (`TraceId` / `SpanId`) |

A context whose trace ID is all zeros, or that is otherwise invalid, is treated as absent.

## Scrubber (JSON mode only)

Defence in depth on top of the call-site rule ("never pass a secret to a log call"). Applied to
`message`, `exception.message` and `exception.stacktrace`, **before** JSON escaping, as two global,
case-insensitive regular-expression replacements in this order. The expressions use only syntax that
Java, JavaScript and .NET interpret identically.

1. **Credential-named key/value** — replace with `$1$2[REDACTED]`:

   ```text
   ([\w.-]*(?:authorization|password|passwd|secret|token|api[-_]?key)[\w.-]*)("?\s*[:=]\s*"?)(?:(?:bearer|basic)\s+)?[^\s"',;&}]+
   ```

   Covers `key=value`, `key: value` and `"key":"value"`, including `Authorization: Bearer <token>`
   (scheme and token both replaced) and keys such as `client_secret`, `access_token`, `db.password`,
   `SECRET_DB_URL`.

2. **Bare `Bearer` / `Basic` credential** — replace with `$1 [REDACTED]`:

   ```text
   \b(bearer|basic)\s+[A-Za-z0-9._~+/-]{8,}=*
   ```

The placeholder is the fixed string `[REDACTED]`. The scrubber over-redacts on purpose (for example
`invalid token: expired` becomes `invalid token: [REDACTED]`); it cannot recognise an unlabelled
secret, and text mode does not run it. For those, the guarantee is the call-site rule.

## Golden cases

Every package's `LogFormatContractTest` (or equivalent) runs all applicable cases. A test compares the
**parsed** object, not bytes: `timestamp` is checked against
`^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$` (the lines below use a fixed illustrative value);
`logger` equals the logger name the test logs with (`dws.golden` below); `exception.type` equals the
runtime's fully qualified name of the exception the test throws, and `exception.stacktrace` is a
non-empty single string containing the exception message. All other listed values are exact. Each
case must also produce exactly one `\n`-terminated line, and the absent keys must be absent.

| # | Case | Input record | Asserts beyond the line below |
|---|---|---|---|
| 1 | plain | `INFO` `Starting Dapr workflow runtime`, no span context | no `trace_id`, no `span_id` |
| 2 | in-span | `INFO` `Invoking step` with active trace `4bf92f3577b34da6a3ce929d0e0e4736`, span `00f067aa0ba902b7` | both IDs exact |
| 3 | out-of-span | `WARN` (the runtime's warning level) `Definition store not reachable, retrying`, no span context | `severity` = `WARN`; no IDs |
| 4 | invalid-context | `INFO` `Invalid context` with an all-zero trace and span ID (or the runtime's "invalid" context) | no IDs |
| 5 | exception | `ERROR` `Activity failed` with an exception whose message is `boom` | `exception.*` present; one line |
| 6 | multi-line | `INFO` `line one` + newline + `line two` | `message` contains the newline; one line |
| 7 | bearer | `INFO` `calling admin with Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkd3MifQ.c2lnbmF0dXJl` | line contains no `eyJhbGciOi` |
| 8 | bare-bearer | `WARN` `upstream rejected Bearer eyJhbGciOiJIUzI1NiJ9.e30.sig12345` | line contains no `eyJhbGciOi` |
| 9 | password | `INFO` `connecting with user=dws password=hunter2` | line contains no `hunter2` |
| 10 | json-secret | `INFO` `payload {"client_secret":"s3cr3t-value","scope":"read"}` | line contains no `s3cr3t-value` |
| 11 | exception-secret | `ERROR` `Call failed` with an exception whose message is `401 for token=abc12345` | line contains no `abc12345` (message and stacktrace) |
| 12 | fatal | the runtime's most severe level (JBoss `FATAL`, Nest `fatal`, .NET `Critical`) `Runtime terminated` | `severity` = `FATAL`; not applicable to Logback |

Expected lines (one per case, in order):

```jsonl
{"timestamp":"2026-01-02T03:04:05.678Z","severity":"INFO","logger":"dws.golden","message":"Starting Dapr workflow runtime"}
{"timestamp":"2026-01-02T03:04:05.678Z","severity":"INFO","logger":"dws.golden","message":"Invoking step","trace_id":"4bf92f3577b34da6a3ce929d0e0e4736","span_id":"00f067aa0ba902b7"}
{"timestamp":"2026-01-02T03:04:05.678Z","severity":"WARN","logger":"dws.golden","message":"Definition store not reachable, retrying"}
{"timestamp":"2026-01-02T03:04:05.678Z","severity":"INFO","logger":"dws.golden","message":"Invalid context"}
{"timestamp":"2026-01-02T03:04:05.678Z","severity":"ERROR","logger":"dws.golden","message":"Activity failed","exception.type":"java.lang.IllegalStateException","exception.message":"boom","exception.stacktrace":"java.lang.IllegalStateException: boom\n\tat dws.golden.GoldenCase.run(GoldenCase.java:1)\n"}
{"timestamp":"2026-01-02T03:04:05.678Z","severity":"INFO","logger":"dws.golden","message":"line one\nline two"}
{"timestamp":"2026-01-02T03:04:05.678Z","severity":"INFO","logger":"dws.golden","message":"calling admin with Authorization: [REDACTED]"}
{"timestamp":"2026-01-02T03:04:05.678Z","severity":"WARN","logger":"dws.golden","message":"upstream rejected Bearer [REDACTED]"}
{"timestamp":"2026-01-02T03:04:05.678Z","severity":"INFO","logger":"dws.golden","message":"connecting with user=dws password=[REDACTED]"}
{"timestamp":"2026-01-02T03:04:05.678Z","severity":"INFO","logger":"dws.golden","message":"payload {\"client_secret\":\"[REDACTED]\",\"scope\":\"read\"}"}
{"timestamp":"2026-01-02T03:04:05.678Z","severity":"ERROR","logger":"dws.golden","message":"Call failed","exception.type":"java.lang.IllegalStateException","exception.message":"401 for token=[REDACTED]","exception.stacktrace":"java.lang.IllegalStateException: 401 for token=[REDACTED]\n\tat dws.golden.GoldenCase.run(GoldenCase.java:1)\n"}
{"timestamp":"2026-01-02T03:04:05.678Z","severity":"FATAL","logger":"dws.golden","message":"Runtime terminated"}
```

The `exception.type` and `exception.stacktrace` values above are Java-shaped illustrations; each
runtime produces its own type name and trace format.

## Rules for code that logs

- Never pass a secret, token, `Authorization` header, `SECRET_*` value, OTLP header or database
  password to a log call, in either format. Prefer logging identifiers (app IDs, definition keys,
  node IDs) over payloads, and be careful with exception messages from HTTP/Dapr clients, which can
  embed URLs and headers.
- `dws-orchestrator`: no log call inside workflow-function code (`InterpreterWorkflow` and the child
  workflows). It re-executes on replay and does not run under a stable span
  ([ADR 0009](adr/0009-trace-context-across-workflow-replay.md), decisions 1–2). Activities are fine.
- A package that reads a different variable name silently falls back to text. `dws-controller`'s
  `OrchestratorEnvContractTest` asserts the sibling orchestrator reads `DWS_LOG_FORMAT`.
