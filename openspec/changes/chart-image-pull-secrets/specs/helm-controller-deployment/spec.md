## ADDED Requirements

### Requirement: The chart provisions a dedicated step ServiceAccount

`charts/dws` SHALL render a step ServiceAccount in the controller's workload namespace when `controller.enabled=true`. The chart SHALL derive the name by appending `-task` to the chart fullname and applying the chart's name length handling. The chart SHALL set the controller Deployment's `DWS_TASK_SERVICE_ACCOUNT` to that same name and SHALL grant the account no RBAC permissions.

#### Scenario: The chart passes the step account name to the controller

- **WHEN** an enabled controller renders under the default or a custom Helm release name
- **THEN** the chart renders `dws-task` for release `dws` and `myrel-dws-task` for release `myrel`, using the default chart naming settings
- **AND** its `DWS_TASK_SERVICE_ACCOUNT` is exactly the name of the rendered step ServiceAccount in the same namespace
- **AND** no chart RoleBinding or ClusterRoleBinding grants permissions to that account

#### Scenario: Disabling the controller suppresses its step account

- **WHEN** the chart renders with `controller.enabled=false`
- **THEN** no step ServiceAccount or `DWS_TASK_SERVICE_ACCOUNT` setting is rendered

### Requirement: Generated Knative steps use the configured ServiceAccount

`dws-controller` SHALL resolve `dws.task.service-account`, including `DWS_TASK_SERVICE_ACCOUNT`, with default `dws-task`. It SHALL set `spec.template.spec.serviceAccountName` on every Knative Service it generates for a deployable step. The standalone reference manifests SHALL supply the default account in the managed workload namespace with no RBAC grant.

#### Scenario: Standalone deployment uses the default step identity

- **GIVEN** the standalone controller manifests are installed with stock settings
- **WHEN** the controller applies a workflow with deployable steps
- **THEN** every generated Knative Service names `dws-task` in its Revision template
- **AND** the standalone ServiceAccount with that name exists in the workload namespace
- **AND** the standalone manifests grant it no RBAC permissions

#### Scenario: The configured identity reaches every generated step

- **GIVEN** the controller runs with `DWS_TASK_SERVICE_ACCOUNT=custom-task` and that account exists in the workload namespace
- **WHEN** the controller applies a workflow with deployable `call` and `run` steps
- **THEN** every generated Knative Service names `custom-task` in its Revision template

#### Scenario: An existing workflow adopts the dedicated account on resubmission

- **GIVEN** a workflow with an existing Knative Service that uses the namespace `default` account or an earlier step account name
- **WHEN** the same workflow definition is submitted to the updated controller with its configured step account provisioned
- **THEN** the controller updates the existing Knative Service's Revision template to name the configured account
- **AND** it preserves the Service name and the immutable workflow definition version
