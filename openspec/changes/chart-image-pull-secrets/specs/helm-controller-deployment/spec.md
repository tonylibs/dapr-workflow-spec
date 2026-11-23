## ADDED Requirements

### Requirement: The chart provisions a dedicated step ServiceAccount

`charts/dws` SHALL render a release-named step ServiceAccount in the controller's workload namespace when `controller.enabled=true`. The chart SHALL set the controller Deployment's `DWS_STEP_SERVICE_ACCOUNT` to that same name and SHALL grant the account no RBAC permissions.

#### Scenario: The chart passes the step account name to the controller

- **WHEN** an enabled controller renders under the default or a custom Helm release name
- **THEN** its `DWS_STEP_SERVICE_ACCOUNT` is exactly the name of the rendered step ServiceAccount in the same namespace
- **AND** no chart RoleBinding or ClusterRoleBinding grants permissions to that account

#### Scenario: Disabling the controller suppresses its step account

- **WHEN** the chart renders with `controller.enabled=false`
- **THEN** no step ServiceAccount or `DWS_STEP_SERVICE_ACCOUNT` setting is rendered

### Requirement: Generated Knative steps use the configured ServiceAccount

`dws-controller` SHALL resolve `dws.step.service-account`, including `DWS_STEP_SERVICE_ACCOUNT`, with default `dws-step`. It SHALL set `spec.template.spec.serviceAccountName` on every Knative Service it generates for a deployable step. The standalone reference manifests SHALL supply the default account in the managed workload namespace with no RBAC grant.

#### Scenario: Standalone deployment uses the default step identity

- **GIVEN** the standalone controller manifests are installed and the step ServiceAccount setting is omitted
- **WHEN** the controller applies a workflow with deployable steps
- **THEN** every generated Knative Service names `dws-step` in its Revision template
- **AND** the standalone ServiceAccount with that name exists in the workload namespace
- **AND** the standalone manifests grant it no RBAC permissions

#### Scenario: The configured identity reaches every generated step

- **GIVEN** the controller runs with `DWS_STEP_SERVICE_ACCOUNT=custom-step` and that account exists in the workload namespace
- **WHEN** the controller applies a workflow with deployable `call` and `run` steps
- **THEN** every generated Knative Service names `custom-step` in its Revision template

#### Scenario: An existing workflow adopts the dedicated account on resubmission

- **GIVEN** a workflow with an existing Knative Service that uses the namespace `default` account
- **WHEN** the same workflow definition is submitted to the updated controller with its configured step account provisioned
- **THEN** the controller updates the existing Knative Service's Revision template to name the configured account
- **AND** it preserves the Service name and the immutable workflow definition version
