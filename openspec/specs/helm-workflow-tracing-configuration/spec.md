# helm-workflow-tracing-configuration Specification

## Purpose
TBD - created by archiving change observability-orchestrator-tracing. Update Purpose after archive.
## Requirements
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

The chart Role bound to the controller's service account SHALL grant `get` on `configurations.dapr.io` on every render, so the controller can verify that the tracing-only `dws-tracing` Configuration exists before stamping `dapr.io/config` onto a compiled orchestrator. The grant is not gated by `observability.*`: the controller already applies and deletes sidecar `Configuration` resources for OAuth workflows, so its Role carries the full managed-kind verb set on `configurations.dapr.io` (see `helm-controller-deployment`, "RBAC scope is preserved exactly"), of which `get` is a member. The raw `dws-controller/k8s/controller-rbac.yaml` SHALL grant the same. Owning component: `charts/dws` for the chart Role; `dws-controller` for the raw manifest.

#### Scenario: Role grants get on configurations when dws-tracing renders

- **WHEN** the chart is rendered with `observability.enabled=true`
- **THEN** the controller Role includes a rule with apiGroup `dapr.io`, resource `configurations`, verb `get`

#### Scenario: The Role does not depend on observability flags

- **WHEN** the chart is rendered with default values, with `observability.enabled=true`, and with `observability.enabled=true` plus `observability.workflows.enabled=false`
- **THEN** the controller Role is identical in all three renders

