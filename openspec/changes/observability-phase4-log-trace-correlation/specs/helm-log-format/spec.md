# Spec Delta

## Purpose

Defines the Helm chart's log-format switch and the log delivery path it configures: one value that selects the structured log format for every DWS component, and the reference collector configuration that turns those stdout lines into trace-correlated log records.

## ADDED Requirements

### Requirement: observability.logs.format selects json or text and defaults to json

The chart SHALL accept `observability.logs.format` with the values `json` and `text`, defaulting to `json`. Any other value SHALL fail rendering with a message naming the two accepted values. The value SHALL have no effect while `observability.enabled` or `observability.logs.enabled` is false.

#### Scenario: Default value

- **WHEN** the chart is rendered with default values
- **THEN** `observability.logs.format` resolves to `json`
- **AND** no resource is changed, because `observability.enabled` is false

#### Scenario: Invalid value fails render

- **WHEN** the chart is rendered with `observability.enabled=true` and `observability.logs.format=yaml`
- **THEN** rendering fails with a message that names `json` and `text`

### Requirement: The format is rendered as DWS_LOG_FORMAT on the controller and admin

When `observability.enabled` and `observability.logs.enabled` are true, the chart SHALL set `DWS_LOG_FORMAT` to the value of `observability.logs.format` on the `dws-controller` container and the `dws-admin` container. When either toggle is false the variable SHALL NOT be rendered, and the default render SHALL stay byte-identical to the committed baseline.

#### Scenario: Enabled with json

- **WHEN** the chart is rendered with `observability.enabled=true` and defaults for the rest
- **THEN** the controller and admin containers each have `DWS_LOG_FORMAT` = `json`

#### Scenario: Enabled with text

- **WHEN** the chart is rendered with `observability.enabled=true` and `observability.logs.format=text`
- **THEN** the controller and admin containers each have `DWS_LOG_FORMAT` = `text`

#### Scenario: Logs signal disabled

- **WHEN** the chart is rendered with `observability.enabled=true` and `observability.logs.enabled=false`
- **THEN** neither container has `DWS_LOG_FORMAT`

#### Scenario: Disabled render is unchanged

- **WHEN** the chart is rendered with `observability.enabled=false`
- **THEN** the output is byte-identical to the committed default-render baseline

### Requirement: Application agents do not export logs over OTLP

The rendered `Instrumentation` resource SHALL set `OTEL_LOGS_EXPORTER` to `none` regardless of `observability.logs.enabled`, so that the collector's `filelog` receiver is the only path by which a component's log lines reach the backend and no line is delivered twice.

#### Scenario: Logs enabled still disables agent log export

- **WHEN** the chart is rendered with `observability.enabled=true` and `observability.logs.enabled=true`
- **THEN** the `Instrumentation` resource has `OTEL_LOGS_EXPORTER` = `none`
- **AND** its `OTEL_TRACES_EXPORTER` and `OTEL_METRICS_EXPORTER` follow their own toggles as before

### Requirement: The reference collector parses stdout JSON into trace-correlated records

The collector configuration shipped in this repository SHALL include a `filelog` receiver for the release namespace's DWS application containers that parses each JSON log line into an OTLP log record: `timestamp` as the record time, `severity` as its severity, `message` as its body, and `trace_id` and `span_id` as the record's trace context. A line that is not JSON SHALL be forwarded with the raw line as its body, not dropped.

#### Scenario: JSON line becomes a trace-correlated record

- **WHEN** the collector reads a line `{"timestamp":"...","severity":"INFO","logger":"x","message":"hi","trace_id":"<32 hex>","span_id":"<16 hex>"}`
- **THEN** the exported log record has body `hi`, severity `INFO`, and its trace ID and span ID set to those values

#### Scenario: Non-JSON line is kept

- **WHEN** a DWS container writes a line that is not a JSON object
- **THEN** a log record is exported with the raw line as its body and no trace context

### Requirement: The reference collector excludes sidecar and self logs

The `filelog` receiver SHALL NOT read the Dapr sidecar container's logs, whose schema is outside this contract, nor the collector's own container, so that the collector never re-ingests its own output.

#### Scenario: Sidecar logs are not collected

- **WHEN** a pod's `daprd` container writes a log line
- **THEN** the `filelog` receiver does not read it

#### Scenario: Collector output is not re-ingested

- **WHEN** the collector's debug exporter prints a log record to its own stdout
- **THEN** the `filelog` receiver does not read that line back

### Requirement: The reference collector delivers logs to a backend queryable by trace ID

The collector's logs pipeline SHALL export to a log backend in which the log lines of a trace can be retrieved by that trace's `trace_id`.

#### Scenario: Logs are retrievable by trace ID

- **WHEN** a sampled workflow run's `trace_id` is queried in the log backend
- **THEN** the log lines that component emitted inside that trace's spans are returned
