## ADDED Requirements

### Requirement: A standalone tracing-only Configuration serves compiled workflow sidecars

When `observability.enabled=true` and `observability.workflows.enabled=true`, the chart SHALL render exactly one Dapr `Configuration` named `dws-tracing` in `dws.namespace`, whose `spec` contains only `tracing`. The tracing block SHALL be produced by the existing `dws.observability.daprTracing` helper, so the endpoint, security flag, protocol mapping, and literal `samplingRate: "1"` are identical to the controller and admin tracing blocks. The Configuration SHALL contain no `appHttpPipeline`, `httpPipeline`, or other middleware. Owning component: `charts/dws`.

#### Scenario: Enabled render contains dws-tracing with tracing only

- **WHEN** the chart is rendered with `observability.enabled=true` and default `observability.workflows.enabled`
- **THEN** exactly one `Configuration` named `dws-tracing` exists
- **AND** its spec has a `tracing` key and no `appHttpPipeline` or `httpPipeline`
- **AND** its `samplingRate` is the string `"1"`

#### Scenario: Translation matches the controller's tracing block

- **WHEN** the endpoint is `https://collector.example:4318` with protocol `http/protobuf`
- **THEN** `dws-tracing` has `endpointAddress: collector.example:4318`, `isSecure: true`, `protocol: http`, identical to the controller Configuration's tracing block

#### Scenario: Workflows gate disables it

- **WHEN** `observability.enabled=true` and `observability.workflows.enabled=false`
- **THEN** no `dws-tracing` Configuration renders

#### Scenario: Default render is unchanged

- **WHEN** the chart is rendered with default values
- **THEN** no `dws-tracing` Configuration renders
- **AND** the full manifest is byte-identical to the Phase 1 default baseline

#### Scenario: Auth configurations are undisturbed

- **WHEN** both `auth.enabled=true` and `observability.enabled=true`
- **THEN** the controller and admin Configurations still carry their pipelines exactly as before
- **AND** the controller and admin Deployments still reference only their own Configuration

### Requirement: The controller may read Dapr Configurations in its namespace

When `dws-tracing` renders (`observability.enabled` and `observability.workflows.enabled`), the chart Role bound to the controller's service account SHALL grant `get` on `configurations.dapr.io`; with the defaults the Role SHALL be unchanged so the default render stays byte-identical to the Phase 1 baseline. The raw `dws-controller/k8s/controller-rbac.yaml` SHALL grant `get` on `configurations.dapr.io` unconditionally. No other new verb or resource SHALL be added by this change. Owning component: `charts/dws` for the chart Role; `dws-controller` for the raw manifest.

#### Scenario: Role grants get on configurations when dws-tracing renders

- **WHEN** the chart is rendered with `observability.enabled=true`
- **THEN** the controller Role includes a rule with apiGroup `dapr.io`, resource `configurations`, verb `get`

#### Scenario: Default Role is unchanged

- **WHEN** the chart is rendered with default values
- **THEN** the controller Role has no `configurations` rule
