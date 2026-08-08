## 1. Helpers and values

- [x] 1.1 Add `dws.controller.fullname` helper to `charts/dws/templates/_helpers.tpl` returning `<dws.fullname>-controller`
- [x] 1.2 Add `dws.namespace` helper to `_helpers.tpl` resolving `namespaceOverride` then `.Release.Namespace`
- [x] 1.3 Add a `dws.controller.selectorLabels` (or inline `component: controller`) so the controller selector extends `dws.selectorLabels` with `app.kubernetes.io/component: controller`
- [x] 1.4 Add a `controller:` block to `charts/dws/values.yaml`: `enabled: true`, `replicaCount`, `image.{repository,tag,pullPolicy}`, `service.port`, and `images.{callHttp,callOpenapi,run,orchestrator}` with the current defaults from `controller-deployment.yaml`

## 2. Controller templates

- [x] 2.1 Write `templates/controller/serviceaccount.yaml` — gated by `controller.enabled`, name from `dws.controller.fullname`, namespace from `dws.namespace`, `dws.labels`
- [x] 2.2 Write `templates/controller/rbac.yaml` — Role + RoleBinding gated by `controller.enabled`; Role rules copied verbatim (configmaps; apps/deployments; serving.knative.dev/services; dapr.io/components) with the exact verbs; RoleBinding subject references the controller ServiceAccount and `dws.namespace`
- [x] 2.3 Write `templates/controller/deployment.yaml` — gated by `controller.enabled`; `replicas` from `controller.replicaCount`; selector/pod labels from the controller selector helper + `dws.labels`; `serviceAccountName` from `dws.controller.fullname`; container image composed from `controller.image`; port `http`/8080; liveness/readiness probes on `/q/health/{live,ready}`; env `DWS_NAMESPACE` (downward API) + four `DWS_IMAGES_*` from `controller.images.*`; resources copied from source
- [x] 2.4 Write `templates/controller/service.yaml` — gated by `controller.enabled`; port from `controller.service.port` targeting `http`; selector from the controller selector helper

## 3. Verify

- [x] 3.1 Run `helm lint charts/dws` — expect no errors
- [x] 3.2 Run `helm template charts/dws` — confirm exactly one controller ServiceAccount, Role, RoleBinding, Deployment, Service render
- [x] 3.3 Run `helm template charts/dws --set controller.enabled=false` — confirm no controller resources render
- [x] 3.4 Spot-check overrides: `--set controller.replicaCount=3`, `--set controller.image.tag=1.2.3`, `--set controller.service.port=9090`, `--set controller.images.run=...` reflect in output
- [x] 3.5 Diff rendered Role rules against `dws-controller/k8s/controller-rbac.yaml` to confirm scope is unchanged
- [x] 3.6 Show the rendered controller Deployment + RBAC to the user for Phase 3 sign-off
