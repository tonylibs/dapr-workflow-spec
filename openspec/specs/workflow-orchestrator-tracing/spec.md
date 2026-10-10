# workflow-orchestrator-tracing Specification

## Purpose
TBD - created by archiving change observability-orchestrator-tracing. Update Purpose after archive.
## Requirements
### Requirement: Orchestrator tracing is off unless the config store turns it on

`dws-controller` SHALL read the keys `observability.enabled` and `observability.instrumentation` from the Dapr configuration store `dws-controller-config` once per workflow deploy, in a single bounded call. Observability SHALL be on only when `observability.enabled` equals `true` after trimming and case-insensitive comparison. An absent key, any other value, a null or empty store response, a store error, or a timeout SHALL all mean off. A failure to read the store SHALL NOT fail the deploy. Owning component: `dws-controller`.

#### Scenario: Flag absent keeps today's orchestrator

- **WHEN** a workflow is deployed and `observability.enabled` is absent from the store
- **THEN** the orchestrator Deployment equals the Deployment produced before this change

#### Scenario: Flag false keeps today's orchestrator

- **WHEN** a workflow is deployed and `observability.enabled` is `false`
- **THEN** the orchestrator Deployment equals the Deployment produced before this change

#### Scenario: Unreachable store keeps today's orchestrator and the deploy succeeds

- **WHEN** a workflow is deployed and the store read throws, returns empty, or exceeds its timeout
- **THEN** the orchestrator Deployment equals the Deployment produced before this change
- **AND** the deploy completes without error

#### Scenario: Flag change applies on the next deploy only

- **WHEN** `observability.enabled` changes while a workflow version is already deployed
- **THEN** the existing orchestrator Deployment is not modified
- **AND** the next deploy of that workflow reflects the new value

### Requirement: Instrumented orchestrator pod carries targeted injection and the sidecar tracing reference

When observability is on and the Dapr Configuration `dws-tracing` exists in the workflow namespace, the orchestrator pod template SHALL carry `instrumentation.opentelemetry.io/inject-java` set to `observability.instrumentation` (default `true`), `instrumentation.opentelemetry.io/container-names` set exactly to `orchestrator`, and `dapr.io/config` set to `dws-tracing`, in addition to its existing `dapr.io/enabled`, `dapr.io/app-id` and `dapr.io/app-port` annotations. No injection annotation SHALL target the Dapr sidecar and no other pod or container SHALL be changed. Owning component: `dws-controller`.

#### Scenario: Enabled orchestrator is instrumented for the app container only

- **WHEN** observability is on, `dws-tracing` exists, and a workflow is deployed
- **THEN** the orchestrator pod template has `inject-java`, `container-names: orchestrator` and `dapr.io/config: dws-tracing`
- **AND** its existing three Dapr annotations are unchanged

#### Scenario: Custom Instrumentation reference is honoured

- **WHEN** `observability.instrumentation` is `dws-system/dws-instrumentation`
- **THEN** `inject-java` is `dws-system/dws-instrumentation`

#### Scenario: Missing Configuration falls back to today's spec

- **WHEN** observability is on but `dws-tracing` is absent, the lookup is forbidden, or the lookup errors
- **THEN** the orchestrator Deployment equals the Deployment produced before this change
- **AND** one warning naming the cause is logged and the deploy succeeds

#### Scenario: An existing dapr.io/config is never replaced

- **WHEN** the orchestrator pod template already has a `dapr.io/config` value
- **THEN** that value is preserved
- **AND** no tracing annotation is stamped

#### Scenario: Other workloads are untouched

- **WHEN** observability is on
- **THEN** Knative step Services, ConfigMaps, Components and other generated resources are identical to their observability-off output

### Requirement: Orchestrator telemetry carries the workflow's identity

When the orchestrator is instrumented, the `orchestrator` container SHALL define `OTEL_SERVICE_NAME` equal to its Dapr app ID and `OTEL_RESOURCE_ATTRIBUTES` containing `dws.workflow.name` equal to the workflow name and `dws.workflow.version` equal to the content-addressed version ID. Values SHALL be percent-encoded for `,` and `=`. An existing `OTEL_RESOURCE_ATTRIBUTES` SHALL be appended to, not replaced. The attributes `dws.node.id` and `dws.node.kind` SHALL NOT be emitted. Owning component: `dws-controller`.

#### Scenario: Identity values are asserted

- **WHEN** a workflow named `order-fulfilment` with version ID `v1a2b3c4d` is deployed with observability on
- **THEN** the orchestrator container has `OTEL_SERVICE_NAME=order-fulfilment`
- **AND** `OTEL_RESOURCE_ATTRIBUTES` contains `dws.workflow.name=order-fulfilment` and `dws.workflow.version=v1a2b3c4d`
- **AND** it contains no `dws.node.id` or `dws.node.kind`

#### Scenario: Special characters are percent-encoded

- **WHEN** an identity value contains `,` or `=`
- **THEN** it appears percent-encoded in `OTEL_RESOURCE_ATTRIBUTES`

#### Scenario: Existing resource attributes survive

- **WHEN** the container already defines `OTEL_RESOURCE_ATTRIBUTES`
- **THEN** the identity attributes are appended and the original attributes remain

### Requirement: The agent is the only sampling root

The controller SHALL NOT read or stamp any sampling setting. The orchestrator sidecar SHALL sample through the chart's `dws-tracing` Configuration pinned to `samplingRate: "1"`, so the sidecar honours the parent decision of the app agent. Owning component: `dws-controller` (no sampling input) and `charts/dws` (the pinned rate).

#### Scenario: No sampling input

- **WHEN** the instrumented orchestrator spec is inspected
- **THEN** it contains no sampler or sampling-rate env var or annotation set by the controller

