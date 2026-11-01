# Spec Delta

## Purpose

Defines the one structured log format that `dws-controller`, `dws-admin`, `dws-orchestrator`, `dws-flow` and `dws-step` write to stdout, so a log line written during a traced operation can be found from that trace's `trace_id` and a collector can index it without per-package parsing.

## ADDED Requirements

### Requirement: JSON mode writes one object per line with the required fields

When the log format is `json`, each of `dws-controller`, `dws-admin`, `dws-orchestrator`, `dws-flow` and `dws-step` SHALL write every log record to stdout as exactly one JSON object followed by a newline, containing `timestamp`, `severity`, `logger` and `message`. Nothing else SHALL be written to stdout by the logging system, and no record SHALL span more than one line.

#### Scenario: Plain record has the four required fields

- **WHEN** a component logs "Starting Dapr workflow runtime" at INFO with no active span, in JSON mode
- **THEN** stdout receives one line that parses as a JSON object
- **AND** the object has `timestamp`, `severity` = `INFO`, `logger` naming the logging class or category, and `message` = `Starting Dapr workflow runtime`
- **AND** the object has no `trace_id` and no `span_id`

#### Scenario: Multi-line message stays on one line

- **WHEN** a component logs a message containing newline characters in JSON mode
- **THEN** stdout receives one line, and the newlines are escaped inside the `message` string

### Requirement: Trace context is taken from the active span and omitted when there is none

In JSON mode a record logged while a valid OpenTelemetry span context is active on the logging thread or async flow SHALL carry `trace_id` (32 lowercase hexadecimal characters) and `span_id` (16 lowercase hexadecimal characters) of that context. A record logged with no active or an invalid (all-zero) context SHALL omit both fields entirely, not emit them empty or zeroed.

#### Scenario: Record inside a span carries the span's identifiers

- **WHEN** a component logs while a span with trace ID `4bf92f3577b34da6a3ce929d0e0e4736` and span ID `00f067aa0ba902b7` is active, in JSON mode
- **THEN** the line has `trace_id` = `4bf92f3577b34da6a3ce929d0e0e4736` and `span_id` = `00f067aa0ba902b7`

#### Scenario: Record outside any span omits both fields

- **WHEN** a component logs at startup, before any span exists, in JSON mode
- **THEN** the line has neither `trace_id` nor `span_id` as a key

#### Scenario: Invalid context is treated as absent

- **WHEN** the active span context is invalid (all-zero identifiers)
- **THEN** the line has neither `trace_id` nor `span_id` as a key

### Requirement: Field names and value vocabularies are identical in all five packages

The key names `timestamp`, `severity`, `logger`, `message`, `trace_id` and `span_id` SHALL be spelled identically in every package. `timestamp` SHALL be an RFC 3339 UTC instant. `severity` SHALL be one of the OpenTelemetry severity texts `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL`, with each runtime's native level mapped onto that set. Additional keys MAY be present but SHALL NOT reuse these six names.

#### Scenario: Same event, same shape in every runtime

- **WHEN** a warning is logged in each package in JSON mode
- **THEN** every line has `severity` = `WARN`, whatever the runtime's own name for that level (for example `WARNING` or `Warning`)
- **AND** every `timestamp` parses as an RFC 3339 UTC instant

#### Scenario: Critical level maps to FATAL

- **WHEN** a runtime logs at its most severe level (for example .NET `Critical`)
- **THEN** the line has `severity` = `FATAL`

### Requirement: Exceptions are carried inside the same JSON object

In JSON mode a record with an attached exception SHALL carry `exception.type`, `exception.message` and `exception.stacktrace` as keys of the same object, with the stack trace as a single escaped string. A stack trace SHALL NOT be written as additional lines on stdout.

#### Scenario: Logged exception stays one line

- **WHEN** a component logs an error with an attached exception in JSON mode
- **THEN** stdout receives exactly one line for the record
- **AND** that object has `exception.type`, `exception.message` and `exception.stacktrace`

### Requirement: DWS_LOG_FORMAT selects the format and defaults to text

Each package SHALL read the environment variable `DWS_LOG_FORMAT`. The value `json` (case-insensitive, trimmed) SHALL select JSON mode. An absent, empty or any other value SHALL select text mode, which is the package's output before this change, and SHALL NOT fail startup.

#### Scenario: Unset keeps today's output

- **WHEN** a package starts with `DWS_LOG_FORMAT` unset
- **THEN** its stdout log output is identical in shape to the output before this change

#### Scenario: json selects the structured format

- **WHEN** a package starts with `DWS_LOG_FORMAT=json`
- **THEN** every subsequent log record is a JSON object line as specified above

#### Scenario: Unrecognised value falls back to text

- **WHEN** a package starts with `DWS_LOG_FORMAT=yaml`
- **THEN** it starts normally and writes text-format logs

### Requirement: Secrets and bearer tokens never appear in log output

No log record, in either format, SHALL contain a bearer token, an `Authorization` header value, the value of a `SECRET_*` environment variable, an OTLP header value, or a database password. In JSON mode the formatter SHALL additionally replace a bearer token and the value of a credential-named key in message and exception text with a fixed placeholder before writing the line.

#### Scenario: Bearer token in a message is redacted

- **WHEN** a component logs a message containing `Authorization: Bearer eyJhbGciOi...` in JSON mode
- **THEN** the line does not contain `eyJhbGciOi`
- **AND** the line still parses as a JSON object

#### Scenario: Credential-named key is redacted

- **WHEN** a component logs a message containing `password=hunter2` in JSON mode
- **THEN** the line does not contain `hunter2`

#### Scenario: Existing call sites do not log secrets

- **WHEN** the orchestrator resolves `SECRET_*` values for a definition's `use.secrets` and runs a workflow
- **THEN** no log line in either format contains any resolved secret value
