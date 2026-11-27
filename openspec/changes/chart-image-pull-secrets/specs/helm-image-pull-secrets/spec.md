## ADDED Requirements

### Requirement: The chart accepts namespace-local global image pull Secret references

Owning component: `charts/dws`. `charts/dws` SHALL accept a top-level `imagePullSecrets` array of references with a required, nonempty string `name`, defaulting to `[]`. Each name identifies an existing image pull Secret in the chart's effective workload namespace. The chart SHALL render references only and validate their shape.

#### Scenario: Valid global references pass Helm validation

- **WHEN** the operator renders the chart with `imagePullSecrets: [{name: regcred}, {name: mirror-creds}]`
- **THEN** Helm accepts the configured list

#### Scenario: Malformed global references fail Helm validation

- **WHEN** top-level `imagePullSecrets` is supplied as a non-array value, or an item lacks a nonempty string `name` or contains unexpected fields
- **THEN** Helm rejects the values with a schema error for `imagePullSecrets`

### Requirement: The chart supports sharing the stable zero-RBAC ServiceAccount across non-RBAC workloads

Owning component: `charts/dws`. `charts/dws` SHALL accept `imagePullServiceAccount.enabled` (boolean, default `false`). When `true`, it SHALL render `<fullname>-task` whenever any of `controller`, `admin`, or `console` is enabled and set `serviceAccountName: <fullname>-task` on enabled `admin` and `console` Deployments, while RBAC workloads (`controller`, `scheduleTime`, `orchestrator`, `daprReadyHook`) keep separate ServiceAccounts.

#### Scenario: Shared zero-RBAC ServiceAccount attaches admin and console to the stable task account

- **GIVEN** `controller`, `admin`, `console`, `scheduleTime`, and the Dapr readiness hook are enabled
- **WHEN** the chart renders with `imagePullServiceAccount.enabled=true` and `imagePullSecrets: [{name: regcred}]`
- **THEN** `<fullname>-task` is rendered with `imagePullSecrets: [{name: regcred}]` and no RoleBinding or ClusterRoleBinding
- **AND** the `admin` and `console` Deployments set `serviceAccountName` to `<fullname>-task`
- **AND** the controller Deployment sets `DWS_TASK_SERVICE_ACCOUNT` to `<fullname>-task`
- **AND** the `controller`, `scheduleTime`, `orchestrator`, and hook ServiceAccounts remain separate and each carries `imagePullSecrets: [{name: regcred}]`

#### Scenario: Shared zero-RBAC ServiceAccount still renders for admin or console when controller is disabled

- **WHEN** the chart renders with `imagePullServiceAccount.enabled=true`, `controller.enabled=false`, and `admin.enabled=true`
- **THEN** `<fullname>-task` is rendered with zero RBAC and `admin` Deployment sets `serviceAccountName` to `<fullname>-task`
- **AND** `<fullname>-task-override` is not rendered

#### Scenario: Shared zero-RBAC ServiceAccount is omitted when no non-RBAC workload is enabled

- **WHEN** the chart renders with `imagePullServiceAccount.enabled=true` and `controller.enabled=false`, `admin.enabled=false`, and `console.enabled=false`
- **THEN** no `<fullname>-task` ServiceAccount is rendered

#### Scenario: Non-boolean imagePullServiceAccount.enabled fails validation

- **WHEN** `imagePullServiceAccount.enabled` is set to a non-boolean value such as `"yes"`
- **THEN** Helm rejects the values with a schema error for `imagePullServiceAccount`

### Requirement: The chart accepts per-step-image pull Secret overrides

Owning component: `charts/dws`. Each of the eight generated step images under `controller.images` (`callHttp`, `callOpenapi`, `callGrpc`, `callAsyncapi`, `callA2a`, `run.shell`, `run.scriptJs`, `run.scriptPython`) SHALL accept an optional `imagePullSecrets` array of `{name}` references or `null`. Omitting it or setting `null` inherits the global list; supplying an array (including `[]`) replaces it.

#### Scenario: Valid per-step-image references pass Helm validation

- **WHEN** the operator renders the chart with `controller.images.callHttp.imagePullSecrets: [{name: http-cred}]` and `controller.images.run.shell.imagePullSecrets: []`
- **THEN** Helm accepts the configured values

#### Scenario: Malformed per-step-image references fail Helm validation

- **WHEN** any `controller.images.<step>.imagePullSecrets` is supplied as a non-array, non-null value, or an item lacks a nonempty string `name` or contains unexpected fields
- **THEN** Helm rejects the values with a schema error for that image's `imagePullSecrets`

### Requirement: Chart-owned accounts and Deployments carry global references while the override account stays empty

Owning component: `charts/dws`. `charts/dws` SHALL render top-level `imagePullSecrets` on every chart-owned ServiceAccount except `<fullname>-task-override` and on every first-party Deployment pod template when non-empty, omitting them when empty. `<fullname>-task-override` SHALL never carry `imagePullSecrets`. The chart SHALL leave the namespace `default` ServiceAccount untouched.

#### Scenario: All enabled chart resources carry global references while task-override omits them

- **GIVEN** the controller, scheduler, admin, console, and Dapr readiness hook are enabled
- **WHEN** the chart renders with `imagePullSecrets: [{name: regcred}]`
- **THEN** the controller, scheduler, orchestrator, task, and hook ServiceAccounts each have `imagePullSecrets: [{name: regcred}]`
- **AND** the `<fullname>-task-override` ServiceAccount omits `imagePullSecrets`
- **AND** the controller, scheduler, admin, and console Deployment pod templates each have `imagePullSecrets: [{name: regcred}]`
- **AND** the readiness Job references its hook ServiceAccount

#### Scenario: The empty list leaves pull-secret fields absent

- **WHEN** top-level `imagePullSecrets` is omitted or explicitly set to `[]` and no per-image overrides are set
- **THEN** each rendered chart-owned ServiceAccount and Deployment pod template omits `imagePullSecrets`
- **AND** both `dws-task` and `dws-task-override` still render when `controller.enabled=true` under default release naming

### Requirement: Chart forwards per-step-image overrides to the controller without mutating the default task ServiceAccount

Owning component: `charts/dws`. For each generated step image with an array `imagePullSecrets` override (including `[]`), `charts/dws` SHALL render `DWS_IMAGES_PULL_SECRETS_<KIND>` on the controller Deployment as a JSON string array of secret names, omit it for un-overridden images, and keep top-level `imagePullSecrets` intact on `<fullname>-task`.

#### Scenario: Per-image overrides emit only matching controller env vars and leave default accounts untouched

- **GIVEN** `controller.enabled=true` and top-level `imagePullSecrets: [{name: regcred}]`
- **WHEN** the chart renders with `controller.images.callHttp.imagePullSecrets: [{name: http-cred}]` and `controller.images.run.shell.imagePullSecrets: []`
- **THEN** `<fullname>-task` still carries `imagePullSecrets: [{name: regcred}]`
- **AND** `<fullname>-task-override` omits `imagePullSecrets`
- **AND** the controller Deployment sets `DWS_IMAGES_PULL_SECRETS_CALL_HTTP` to `["http-cred"]` and `DWS_IMAGES_PULL_SECRETS_RUN_SHELL` to `[]`
- **AND** no `DWS_IMAGES_PULL_SECRETS_*` env vars render for un-overridden step images

#### Scenario: Omitted or null overrides emit no pull-secret override env vars

- **WHEN** no `controller.images.<step>.imagePullSecrets` is set (or it is explicitly `null`)
- **THEN** the controller Deployment renders no `DWS_IMAGES_PULL_SECRETS_*` environment variables

### Requirement: Controller validates per-step-image pull-secret overrides

Owning component: `dws-controller`. `dws-controller` SHALL bind optional JSON string-array settings at `dws.images.pull-secrets.<kind>` (`DWS_IMAGES_PULL_SECRETS_<KIND>`) for all eight deployable `TaskKind`s. Malformed JSON or blank secret names SHALL fail fast with a clear configuration error naming the invalid key.

#### Scenario: Invalid pull-secret override configuration fails fast

- **GIVEN** `dws-controller` is configured with invalid JSON (such as `not-json`) or an array containing a blank string (`[""]`) in any `DWS_IMAGES_PULL_SECRETS_*`
- **WHEN** the controller resolves pull-secret configuration
- **THEN** it throws an explicit configuration error naming the invalid key

### Requirement: Controller isolates overridden Knative steps on the override ServiceAccount with exact replacement pull secrets

Owning component: `dws-controller`. For a step whose `TaskKind` has no override, `dws-controller` SHALL stamp `dws.task.service-account` and omit `spec.template.spec.imagePullSecrets`. For a step with an override, it SHALL stamp `dws.task.override-service-account` and set `spec.template.spec.imagePullSecrets` to the override list (omitting it for `[]`). Pull-secret settings SHALL NOT alter workflow definition versions or orchestrator Deployments.

#### Scenario: Per-image override isolates matching steps on the override account with replacement secrets

- **GIVEN** `dws-controller` runs with `DWS_TASK_SERVICE_ACCOUNT=dws-task`, `DWS_TASK_OVERRIDE_SERVICE_ACCOUNT=dws-task-override`, and `DWS_IMAGES_PULL_SECRETS_CALL_HTTP='["http-cred"]'`
- **WHEN** the controller applies a workflow containing both a `call: http` step and a `call: openapi` step
- **THEN** the `call: http` Knative Service sets `serviceAccountName` to `dws-task-override` and `spec.template.spec.imagePullSecrets` to `[{name: http-cred}]`
- **AND** the `call: openapi` Knative Service sets `serviceAccountName` to `dws-task` and omits `spec.template.spec.imagePullSecrets`
- **AND** the orchestrator Deployment omits `spec.template.spec.imagePullSecrets` and keeps its orchestrator ServiceAccount

#### Scenario: Explicit empty per-image override uses the override account with no pod pull secrets

- **GIVEN** `dws-controller` runs with `DWS_IMAGES_PULL_SECRETS_RUN_SHELL='[]'`
- **WHEN** the controller applies a workflow containing a `run: shell` step and a `call: http` step
- **THEN** the `run: shell` Knative Service sets `serviceAccountName` to `dws-task-override` and omits `spec.template.spec.imagePullSecrets`
- **AND** the `call: http` Knative Service sets `serviceAccountName` to `dws-task` and omits `spec.template.spec.imagePullSecrets`

#### Scenario: Resubmitting an existing workflow updates override identity and pull secrets without changing the definition version

- **GIVEN** a workflow is already deployed under an earlier pull-secret configuration
- **WHEN** the controller is updated with a new per-image `imagePullSecrets` override and the same workflow definition is resubmitted
- **THEN** the controller updates the matching Knative Service to `dws-task-override` and the new `spec.template.spec.imagePullSecrets`
- **AND** it preserves the Knative Service name and the immutable workflow definition version

### Requirement: Generated workloads can pull private DWS images using their ServiceAccounts or per-image overrides

Owning components: `charts/dws`, `dws-controller`. `charts/dws` and `dws-controller` SHALL let generated orchestrator and Knative step workloads pull private images using their ServiceAccount pull-secret references (default or shared zero-RBAC mode) or per-image replacement overrides on `<fullname>-task-override` without manual patches.

#### Scenario: A fresh installation runs a workflow from a private registry

- **GIVEN** a namespace with a valid `regcred` pull Secret, working Kubernetes, Dapr, and Knative prerequisites, and private DWS images referenced by the chart and controller
- **WHEN** the operator installs the chart with `imagePullSecrets: [{name: regcred}]` (with `imagePullServiceAccount.enabled` set to either `false` or `true`) and submits and runs a workflow with a deployable step
- **THEN** the controller, admin, scheduler, generated orchestrator, and Knative step successfully pull their DWS images and run
- **AND** the Knative Revision resolves its private step image tag to a digest
- **AND** the workflow completes without manual ServiceAccount or workload patches

#### Scenario: A step image with a per-image override pulls exclusively with its override credential

- **GIVEN** a namespace with two pull Secrets (`regcred` for default DWS images and `step-cred` for a separate private registry hosting one step image), with `imagePullSecrets: [{name: regcred}]` and `controller.images.callHttp.imagePullSecrets: [{name: step-cred}]`
- **WHEN** the operator installs the chart and runs a workflow with a `call: http` step and an un-overridden step
- **THEN** the generated `call: http` Knative Revision runs as `<fullname>-task-override` (which has no `imagePullSecrets`) with pod-level `imagePullSecrets: [{name: step-cred}]`
- **AND** the un-overridden step continues running as `<fullname>-task` (which retains `imagePullSecrets: [{name: regcred}]`)
- **AND** Knative resolves both image digests and the workflow completes without manual patches
