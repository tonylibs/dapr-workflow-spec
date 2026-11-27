## ADDED Requirements

### Requirement: The chart provisions stable default and override zero-RBAC step ServiceAccounts

Owning component: `charts/dws`. When `controller.enabled=true`, `charts/dws` SHALL render two zero-RBAC ServiceAccounts in the workload namespace: `<fullname>-task` and `<fullname>-task-override`, and SHALL set `DWS_TASK_SERVICE_ACCOUNT` and `DWS_TASK_OVERRIDE_SERVICE_ACCOUNT` on the controller Deployment to those exact names. Neither account SHALL receive chart RBAC. When `controller.enabled=false`, neither env var nor `<fullname>-task-override` SHALL render.

#### Scenario: Enabled controller receives both default and override zero-RBAC step accounts

- **WHEN** an enabled controller renders under the default or a custom Helm release name (regardless of `imagePullServiceAccount.enabled`)
- **THEN** the chart renders `dws-task` and `dws-task-override` for release `dws` (and `myrel-dws-task` and `myrel-dws-task-override` for release `myrel`)
- **AND** `DWS_TASK_SERVICE_ACCOUNT` equals the `<fullname>-task` ServiceAccount name and `DWS_TASK_OVERRIDE_SERVICE_ACCOUNT` equals the `<fullname>-task-override` ServiceAccount name
- **AND** no chart RoleBinding or ClusterRoleBinding grants permissions to either account

#### Scenario: Disabling the controller suppresses controller step wiring and the override account

- **WHEN** the chart renders with `controller.enabled=false`
- **THEN** neither `<fullname>-task-override` nor `DWS_TASK_SERVICE_ACCOUNT` nor `DWS_TASK_OVERRIDE_SERVICE_ACCOUNT` is rendered
- **AND** `<fullname>-task` is omitted when `imagePullServiceAccount.enabled=false`

#### Scenario: Toggling shared non-RBAC ServiceAccount mode preserves the stable task ServiceAccount name

- **WHEN** an enabled controller renders with `imagePullServiceAccount.enabled=true`
- **THEN** `<fullname>-task` remains rendered under the exact same name and `DWS_TASK_SERVICE_ACCOUNT` is unchanged
- **AND** `DWS_ORCHESTRATOR_SERVICE_ACCOUNT` remains `<fullname>-orchestrator` with its separate read-only `configmaps` Role and RoleBinding

### Requirement: Generated Knative steps use the configured default or override ServiceAccount

Owning component: `dws-controller`. `dws-controller` SHALL resolve `dws.task.service-account` (default `dws-task`) and `dws.task.override-service-account` (default `dws-task-override`) and set each generated Knative Service's `serviceAccountName` to the default account or, when that `TaskKind` has a pull-secret override, the override account. Standalone manifests SHALL supply both accounts with no RBAC.

#### Scenario: Standalone deployment uses the default step identity for un-overridden steps

- **GIVEN** the standalone controller manifests are installed with stock settings
- **WHEN** the controller applies a workflow with deployable steps and no per-image pull-secret overrides
- **THEN** every generated Knative Service names `dws-task` in its Revision template
- **AND** both `dws-task` and `dws-task-override` exist in the standalone manifests in the workload namespace with no RBAC permissions

#### Scenario: Configured default and override identities reach generated steps

- **GIVEN** the controller runs with `DWS_TASK_SERVICE_ACCOUNT=custom-task`, `DWS_TASK_OVERRIDE_SERVICE_ACCOUNT=custom-task-override`, and `DWS_IMAGES_PULL_SECRETS_CALL_HTTP='["http-cred"]'`
- **WHEN** the controller applies a workflow with both a `call: http` step and a `call: openapi` step
- **THEN** the `call: http` Knative Service names `custom-task-override` in its Revision template
- **AND** the `call: openapi` Knative Service names `custom-task` in its Revision template

#### Scenario: An existing workflow adopts the configured account on resubmission

- **GIVEN** a workflow with an existing Knative Service that uses the namespace `default` account or an earlier step account name
- **WHEN** the same workflow definition is submitted to the updated controller with its configured step account provisioned
- **THEN** the controller updates the existing Knative Service's Revision template to name the configured account
- **AND** it preserves the Service name and the immutable workflow definition version
