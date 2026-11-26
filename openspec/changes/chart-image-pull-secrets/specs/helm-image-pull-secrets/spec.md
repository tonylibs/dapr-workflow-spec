## ADDED Requirements

### Requirement: The chart accepts namespace-local image pull Secret references

`charts/dws` SHALL accept a top-level `imagePullSecrets` array of references with a required, nonempty string `name`. The default SHALL be `[]`. Each name identifies an existing image pull Secret in the namespace rendered by the chart. The chart SHALL render references only. Chart validation covers the values shape. Kubernetes and Knative resolve the references at runtime.

#### Scenario: Valid references pass Helm validation

- **WHEN** the operator renders the chart with `imagePullSecrets: [{name: regcred}, {name: mirror-creds}]`
- **THEN** Helm accepts the configured list

#### Scenario: Malformed references fail Helm validation

- **WHEN** `imagePullSecrets` is supplied as a non-array value, or an item lacks a nonempty string `name`
- **THEN** Helm rejects the values with a schema error for `imagePullSecrets`

### Requirement: Chart-owned accounts and Deployments carry the configured list

`charts/dws` SHALL render the configured list at `imagePullSecrets` on every ServiceAccount emitted by its own templates and at `spec.template.spec.imagePullSecrets` on every Deployment emitted by those templates. It SHALL omit both fields when the list is empty. The chart SHALL leave the namespace `default` ServiceAccount untouched.

#### Scenario: All enabled chart resources carry the references

- **GIVEN** the controller, scheduler, admin, console, and Dapr readiness hook are enabled
- **WHEN** the chart renders with `imagePullSecrets: [{name: regcred}]`
- **THEN** the controller, scheduler, orchestrator, step, and hook ServiceAccounts each have the configured list
- **AND** the controller, scheduler, admin, and console Deployment pod templates each have the configured list
- **AND** the readiness Job references its hook ServiceAccount

#### Scenario: The empty list leaves pull-secret fields absent

- **WHEN** `imagePullSecrets` is omitted or explicitly set to `[]`
- **THEN** each rendered chart-owned ServiceAccount and Deployment pod template omits `imagePullSecrets`
- **AND** the dedicated step ServiceAccount still renders when `controller.enabled=true` as `dws-task` under the default release and naming settings

### Requirement: Generated workloads can pull private DWS images using their ServiceAccounts

`charts/dws` and `dws-controller` SHALL let generated orchestrator and Knative step workloads use the pull-secret references on their respective ServiceAccounts without an operator patch to the accounts or workload manifests.

#### Scenario: A fresh installation runs a workflow from a private registry

- **GIVEN** a namespace with a valid `regcred` pull Secret, working Kubernetes, Dapr, and Knative prerequisites, and private DWS images referenced by the chart and controller
- **WHEN** the operator installs the chart with `imagePullSecrets: [{name: regcred}]` and submits and runs a workflow with a deployable step
- **THEN** the controller, admin, scheduler, generated orchestrator, and Knative step successfully pull their DWS images and run
- **AND** the Knative Revision resolves its private step image tag to a digest
- **AND** the workflow completes without manual ServiceAccount or workload patches
