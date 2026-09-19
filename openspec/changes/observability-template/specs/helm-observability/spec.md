## ADDED Requirements

### Requirement: Observability values are opt-in and preserve the default render

The `charts/dws` chart SHALL expose a top-level `observability` values block with these defaults: `enabled: false`; `otlp.endpoint: http://dws-otel-collector:4318`; `otlp.protocol: http/protobuf`; `otlp.headers: {}`; `otlp.existingSecret: ""`; `traces.enabled: true`; `traces.samplingRate: 0.1`; `traces.daprSpans: true`; `metrics.enabled: true`; `logs.enabled: true`; `logs.format: json`; `workflows.enabled: true`; `operator.required: true`; `collector.enabled: false`; and `resourceAttributes: {}`. `observability.enabled` SHALL be the master gate for every Phase 1 resource and pod mutation. Owning component: `charts/dws`.

#### Scenario: Default values are a topological no-op

- **WHEN** the chart is rendered with its default values
- **THEN** no `Instrumentation`, OTLP Secret, tracing block, OpenTelemetry injection annotation, or observability-only Dapr `Configuration` is present
- **AND** the complete rendered manifest is byte-identical to the recorded pre-change default baseline

#### Scenario: Enabling observability activates the chart surface

- **WHEN** the chart is rendered with `observability.enabled=true` and the `opentelemetry.io/v1alpha1` API available
- **THEN** the Phase 1 Instrumentation, Dapr tracing, and controller/admin pod mutations are present
- **AND** no application source-code or image override is required

### Requirement: Instrumentation resource configures Phase 1 application agents

When `observability.enabled=true`, the chart SHALL render exactly one namespaced `opentelemetry.io/v1alpha1` `Instrumentation` resource for the release. It SHALL set the exporter endpoint from `observability.otlp.endpoint`, propagators `tracecontext` and `baggage`, sampler type `parentbased_traceidratio`, sampler argument from `observability.traces.samplingRate`, and `resource.resourceAttributes.service.namespace: dws`. It SHALL merge `observability.resourceAttributes` while preserving `service.namespace: dws`, and SHALL contain `java: {}`, `nodejs: {}`, and `dotnet: {}` sections. Common environment configuration SHALL select `otlp` or `none` for traces, metrics, and logs from their enabled flags and SHALL set `OTEL_EXPORTER_OTLP_PROTOCOL` from `observability.otlp.protocol`. Owning component: `charts/dws` (`templates/observability/instrumentation.yaml`).

#### Scenario: Default enabled observability renders the expected Instrumentation shape

- **WHEN** the chart is rendered with `observability.enabled=true` and otherwise default observability values
- **THEN** exactly one `Instrumentation` is rendered in `dws.namespace`
- **AND** it exports to `http://dws-otel-collector:4318` using `http/protobuf`
- **AND** its sampler is `parentbased_traceidratio` with argument `"0.1"`
- **AND** `service.namespace` is `dws`
- **AND** Java, Node.js, and .NET sections are present

#### Scenario: Signal and resource overrides reach the common agent configuration

- **WHEN** observability is enabled with metrics disabled, a custom sampling rate, and additional resource attributes
- **THEN** the Instrumentation common environment selects no metrics exporter while retaining the configured trace and log exporters
- **AND** the sampler argument equals the custom rate
- **AND** the additional resource attributes render without replacing `service.namespace=dws`

### Requirement: OTLP header credentials use a Secret reference

The chart SHALL pass application-agent OTLP headers through `OTEL_EXPORTER_OTLP_HEADERS` sourced from Secret key `headers`. When `observability.otlp.existingSecret` is set, the chart SHALL reference that Secret and SHALL NOT render a replacement. Otherwise, when `observability.otlp.headers` is non-empty, the chart SHALL render one release-named Opaque Secret whose `stringData.headers` is a deterministic comma-separated `key=value` list. When neither source is configured, neither the Secret nor the header environment variable SHALL render. Owning component: `charts/dws` (`templates/observability/otlp-secret.yaml` and `templates/observability/instrumentation.yaml`).

#### Scenario: Inline headers create one Secret

- **WHEN** observability is enabled with `observability.otlp.headers.api-key=secret-value` and no existing Secret
- **THEN** one Opaque OTLP Secret is rendered with `stringData.headers` containing `api-key=secret-value`
- **AND** the Instrumentation resource references its `headers` key
- **AND** the header value is not inlined in the Instrumentation resource

#### Scenario: Existing Secret takes precedence

- **WHEN** observability is enabled with `observability.otlp.existingSecret=platform-otlp` and inline headers are also supplied
- **THEN** no chart-owned OTLP Secret is rendered
- **AND** the Instrumentation resource references key `headers` in `platform-otlp`

#### Scenario: No header configuration emits no secret wiring

- **WHEN** observability is enabled with default header values
- **THEN** no chart-owned OTLP Secret is rendered
- **AND** the Instrumentation resource has no `OTEL_EXPORTER_OTLP_HEADERS` entry

### Requirement: Dapr tracing shares the component Configuration and preserves one sampling root

For each enabled controller or admin component, `observability.enabled=true` SHALL add `spec.tracing` to that component's single Dapr `Configuration`. The tracing block SHALL set `samplingRate: "1"` literally, set `otel.endpointAddress` from `observability.otlp.endpoint`, derive `otel.isSecure` from the endpoint's HTTPS scheme, and map `observability.otlp.protocol` to Dapr's `http` or `grpc` protocol. The tunable `observability.traces.samplingRate` SHALL NOT change the Dapr sampling value. The chart MUST NOT create a standalone tracing `Configuration` or more than one `dapr.io/config` annotation on a pod. Owning component: `charts/dws`.

#### Scenario: HTTP OTLP values render in both component configurations

- **WHEN** observability is enabled with the default `http/protobuf` endpoint and auth disabled
- **THEN** exactly one controller and one admin `Configuration` render
- **AND** each has `spec.tracing.samplingRate: "1"`, Dapr protocol `http`, and `isSecure: false`
- **AND** neither configuration contains an auth pipeline

#### Scenario: HTTPS gRPC values map to Dapr transport fields

- **WHEN** observability is enabled with an HTTPS OTLP endpoint and protocol `grpc`
- **THEN** each rendered tracing block uses that endpoint, `isSecure: true`, and protocol `grpc`

#### Scenario: Custom agent sampling does not alter Dapr sampling

- **WHEN** observability is enabled with `observability.traces.samplingRate=0.25`
- **THEN** the Instrumentation sampler argument is `"0.25"`
- **AND** every Dapr Configuration still has `samplingRate: "1"`

### Requirement: OpenTelemetry Operator availability is checked before rendering workloads

When both `observability.enabled` and `observability.operator.required` are true, the chart SHALL fail rendering if `.Capabilities.APIVersions` lacks `opentelemetry.io/v1alpha1`. The failure SHALL explain that the OpenTelemetry Operator requires cert-manager and SHALL name `observability.operator.required=false` as the explicit bypass. The Operator SHALL remain a documented prerequisite and MUST NOT be added to `Chart.yaml`. Owning component: `charts/dws` (`templates/_preflight.tpl` and `templates/preflight.yaml`).

#### Scenario: Missing Operator CRD fails by default

- **WHEN** observability is enabled, operator preflight is required, and `opentelemetry.io/v1alpha1` is unavailable
- **THEN** Helm rendering fails with the documented OpenTelemetry Operator and cert-manager guidance

#### Scenario: Installed Operator satisfies preflight

- **WHEN** observability is enabled and `opentelemetry.io/v1alpha1` is available
- **THEN** preflight succeeds and the Instrumentation resource renders

#### Scenario: Operator preflight can be explicitly bypassed

- **WHEN** observability is enabled with `observability.operator.required=false` and the API is unavailable
- **THEN** the observability preflight does not fail

### Requirement: Pod annotations inject only the intended application container

When observability is enabled, `dws.observability.podAnnotations` SHALL add Java injection to the controller pod and Node.js injection to the admin pod, with each annotation referencing the release's namespaced Instrumentation resource. It SHALL also set `instrumentation.opentelemetry.io/container-names` to `controller` or `admin` respectively so the Operator does not inject an application agent into `daprd`. When observability is disabled, the helper SHALL emit no annotations. Owning component: `charts/dws` (`templates/_helpers.tpl`, controller Deployment, and admin Deployment).

#### Scenario: Controller targets Java instrumentation

- **WHEN** observability is enabled and the controller Deployment renders
- **THEN** its pod template has `instrumentation.opentelemetry.io/inject-java` referencing the release Instrumentation
- **AND** `instrumentation.opentelemetry.io/container-names` is exactly `controller`

#### Scenario: Admin targets Node.js instrumentation

- **WHEN** observability is enabled and the admin Deployment renders
- **THEN** its pod template has `instrumentation.opentelemetry.io/inject-nodejs` referencing the release Instrumentation
- **AND** `instrumentation.opentelemetry.io/container-names` is exactly `admin`

#### Scenario: Disabled observability injects no agents

- **WHEN** the chart is rendered with `observability.enabled=false`
- **THEN** neither Deployment contains an `instrumentation.opentelemetry.io/*` annotation

### Requirement: Enabled Phase 1 produces connected control-plane telemetry

With observability enabled, the pinned OpenTelemetry Operator installed, and the configured OTLP receiver reachable, the chart-managed controller and admin application agents SHALL export enabled traces, metrics, and logs without application code changes, and their Dapr sidecars SHALL export tracing spans. A controller event processed through Dapr by admin and persisted to Postgres SHALL produce one connected trace across the controller, Dapr, admin, and database operation. Owning component: `charts/dws` deployment behavior.

#### Scenario: Connected trace reaches the operator receiver

- **WHEN** an enabled release processes a controller-originated lifecycle event through Dapr and admin persists it to Postgres
- **THEN** the configured receiver observes one trace containing controller, Dapr, admin, and Postgres spans with propagated parentage

#### Scenario: Application images remain unchanged

- **WHEN** observability is enabled
- **THEN** the controller and admin Deployments retain their configured application images
- **AND** telemetry activation is supplied only by rendered configuration, annotations, and Operator mutation

### Requirement: Observability render behavior is guarded in chart CI

The chart SHALL include `tests/observability-render-test.sh` and run it in the Helm verification workflow. The test SHALL exercise all four combinations of auth and observability, assert one component `Configuration` when either feature is active, exact controller/admin pipeline placement only under auth, tracing only under observability with Dapr sampling `"1"`, a matching single `dapr.io/config` annotation, targeted injection only under observability, and default baseline identity. The root chart gate documentation SHALL list this script. Owning component: `charts/dws` and `.github/workflows/helm.yml`.

#### Scenario: Four-way feature matrix is enforced

- **WHEN** the observability render test runs in CI
- **THEN** it validates auth off/observability off, auth on/observability off, auth off/observability on, and both on
- **AND** any duplicate Configuration, misplaced pipeline, conditional tracing error, sampling drift, annotation mismatch, or default-render change fails the job

