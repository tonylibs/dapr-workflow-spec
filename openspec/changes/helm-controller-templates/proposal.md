## Why

The `dws-controller` control plane is currently deployed from raw, hardcoded manifests in
`dws-controller/k8s/` (`controller-rbac.yaml`, `controller-deployment.yaml`) that pin the
namespace to `default`, bake in image references, and cannot be parameterized per environment.
Phase 1 of the Helm chart roadmap scaffolded `charts/dws` but left the controller templates as
placeholders. Phase 2 turns those raw manifests into a configurable Helm chart so an operator can
install the controller with `helm install` and override image, replicas, namespace, and the
`DWS_IMAGES_*` step-image references without editing YAML.

## What Changes

- Port the controller's ServiceAccount, Role, RoleBinding, Deployment, and Service from
  `dws-controller/k8s/*.yaml` into real Helm templates under `charts/dws/templates/controller/`
  (`serviceaccount.yaml`, `rbac.yaml`, `deployment.yaml`, `service.yaml`), replacing the current
  placeholder files.
- Templatize resource name and namespace via `_helpers.tpl`, adding `dws.controller.fullname` and
  `dws.namespace` helpers and reusing the existing `dws.fullname`/`dws.labels`/`dws.selectorLabels`.
- Introduce a `controller:` key in `values.yaml` holding `enabled`, `replicaCount`,
  `image.{repository,tag,pullPolicy}`, `service.port`, and the four
  `DWS_IMAGES_*` env values (`callHttp`, `callOpenapi`, `run`, `orchestrator`).
- Gate every controller resource behind `controller.enabled` (default `true`).
- Preserve the RBAC scope exactly (configmaps; apps/deployments; serving.knative.dev/services;
  dapr.io/components) — no broadening of verbs or resources.
- Leave `dws-admin`, `postgres`, and `console` untouched (later phases).

## Capabilities

### New Capabilities
- `helm-controller-deployment`: Helm chart templates and values that render the `dws-controller`
  control plane (ServiceAccount, scoped RBAC, Deployment, Service) with parameterized name,
  namespace, image, replicas, service port, and step-image env, gated by a `controller.enabled`
  toggle.

### Modified Capabilities
<!-- None — no existing spec's requirements change. -->

## Impact

- **Chart templates**: `charts/dws/templates/controller/{serviceaccount,rbac,deployment,service}.yaml`
  replace placeholders; `charts/dws/templates/_helpers.tpl` gains two helpers.
- **Chart values**: `charts/dws/values.yaml` gains a `controller:` block.
- **Source manifests**: `dws-controller/k8s/controller-{rbac,deployment}.yaml` remain as the
  reference source of truth; the chart is the new install path (no removal in this phase).
- **No runtime/behavioral change** to the controller image or its RBAC surface — the rendered
  objects are functionally identical to the current manifests, only parameterized.
