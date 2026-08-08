## ADDED Requirements

### Requirement: Controller resources render from the chart

The `charts/dws` Helm chart SHALL render the `dws-controller` control plane as a ServiceAccount,
a namespaced Role, a RoleBinding, a Deployment, and a Service under
`templates/controller/`. The rendered objects SHALL be functionally equivalent to the reference
manifests in `dws-controller/k8s/controller-rbac.yaml` and
`dws-controller/k8s/controller-deployment.yaml`.

#### Scenario: Default render produces the full controller stack
- **WHEN** `helm template charts/dws` is run with default values
- **THEN** the output contains exactly one ServiceAccount, one Role, one RoleBinding, one
  Deployment, and one Service for the controller
- **AND** the Deployment mounts the rendered ServiceAccount via `serviceAccountName`
- **AND** the RoleBinding's subject references that same ServiceAccount name

#### Scenario: Chart passes lint
- **WHEN** `helm lint charts/dws` is run
- **THEN** it reports no errors

### Requirement: Controller resources are gated by an enable toggle

Every controller resource SHALL be rendered only when `controller.enabled` is `true`, which SHALL
be the default. When `controller.enabled` is `false`, none of the controller ServiceAccount, Role,
RoleBinding, Deployment, or Service SHALL be rendered.

#### Scenario: Enabled by default
- **WHEN** `helm template charts/dws` is run with default values
- **THEN** all five controller resources are present

#### Scenario: Disabled toggle suppresses all controller resources
- **WHEN** `helm template charts/dws --set controller.enabled=false` is run
- **THEN** no controller ServiceAccount, Role, RoleBinding, Deployment, or Service appears in the
  output

### Requirement: Name and namespace are templatized

Controller resource names and their namespace SHALL be derived from chart helpers rather than
hardcoded. `_helpers.tpl` SHALL provide a `dws.controller.fullname` helper for the controller
object name and a `dws.namespace` helper for the target namespace, and controller resources SHALL
carry the standard `dws.labels` and `dws.selectorLabels`.

#### Scenario: Namespace follows the release namespace
- **WHEN** `helm template charts/dws --namespace dws-system` is run
- **THEN** every controller resource's `metadata.namespace` is `dws-system`
- **AND** the RoleBinding subject's namespace matches

#### Scenario: Selector and pod labels agree
- **WHEN** the controller Deployment is rendered
- **THEN** its `spec.selector.matchLabels` is a subset of `spec.template.metadata.labels`

### Requirement: Image, replicas, and service port are configurable via values

The controller image (`repository`, `tag`, `pullPolicy`), replica count, and service port SHALL be
sourced from a `controller:` block in `values.yaml`. Overriding these values SHALL change the
rendered output accordingly.

#### Scenario: Image reference is composed from values
- **WHEN** `helm template charts/dws --set controller.image.repository=ghcr.io/tonylibs/dws-controller --set controller.image.tag=1.2.3` is run
- **THEN** the controller Deployment's container image is `ghcr.io/tonylibs/dws-controller:1.2.3`
- **AND** the container `imagePullPolicy` is the value of `controller.image.pullPolicy`

#### Scenario: Replica count is configurable
- **WHEN** `helm template charts/dws --set controller.replicaCount=3` is run
- **THEN** the controller Deployment's `spec.replicas` is `3`

#### Scenario: Service port is configurable
- **WHEN** `helm template charts/dws --set controller.service.port=9090` is run
- **THEN** the controller Service exposes port `9090` targeting the container's `http` port

### Requirement: Step-image env values are configurable

The controller Deployment SHALL expose the four `DWS_IMAGES_*` environment variables
(`DWS_IMAGES_CALL_HTTP`, `DWS_IMAGES_CALL_OPENAPI`, `DWS_IMAGES_RUN`, `DWS_IMAGES_ORCHESTRATOR`)
sourced from `controller.images.{callHttp,callOpenapi,run,orchestrator}` in `values.yaml`, and
SHALL continue to expose `DWS_NAMESPACE` from the pod's own namespace via the downward API.

#### Scenario: Step-image env is rendered from values
- **WHEN** `helm template charts/dws --set controller.images.run=my-registry/dws-run:2.0` is run
- **THEN** the controller container has an env entry `DWS_IMAGES_RUN` with value
  `my-registry/dws-run:2.0`
- **AND** env entries `DWS_IMAGES_CALL_HTTP`, `DWS_IMAGES_CALL_OPENAPI`, and
  `DWS_IMAGES_ORCHESTRATOR` are present with their configured values

#### Scenario: Namespace env uses the downward API
- **WHEN** the controller Deployment is rendered
- **THEN** the container has an env entry `DWS_NAMESPACE` sourced from `fieldRef`
  `metadata.namespace`

### Requirement: RBAC scope is preserved exactly

The controller Role SHALL grant exactly the permissions from
`dws-controller/k8s/controller-rbac.yaml` and no more:
`configmaps` (`get`, `list`, `create`, `delete`); `apps/deployments`
(`get`, `list`, `create`, `delete`, `update`, `patch`);
`serving.knative.dev/services` (`get`, `list`, `create`, `delete`, `update`, `patch`);
`dapr.io/components` (`get`, `list`, `create`, `delete`, `update`, `patch`).
No additional API groups, resources, or verbs SHALL be added.

#### Scenario: Role rules match the reference exactly
- **WHEN** the controller Role is rendered
- **THEN** it contains exactly four rules covering `configmaps`, `apps/deployments`,
  `serving.knative.dev/services`, and `dapr.io/components` with the verbs listed above
- **AND** no rule references `secrets`, `pods`, cluster-scoped resources, or the `*` wildcard
