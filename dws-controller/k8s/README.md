# Deploying the DWS controller

## Contents

| File | Purpose |
|------|---------|
| `controller-rbac.yaml` | Controller and workload identities: controller RBAC, the read-only `dws-orchestrator` account, and the no-grant `dws-task` and `dws-task-override` accounts |
| `controller-deployment.yaml` | The controller's own Deployment and Service |
| `dapr-crds.yaml` | Pinned Dapr CRD bundle (input to `cdk8s import`, not applied by the controller) |
| `serving-crds.yaml` | Pinned Knative Serving CRD bundle (same) |

## Prerequisites

- Knative Serving installed (the step services are Knative Services).
- Dapr installed, v1.18+ (`configuration.kubernetes` is Alpha).
- The prebuilt step images reachable from the cluster: `ghcr.io/tonylibs/dws-call-http`,
  `ghcr.io/tonylibs/dws-call-openapi`, `sw-run`, `ghcr.io/tonylibs/dws-orchestrator`. Override the
  registry/tags via the `DWS_IMAGES_*` env vars in `controller-deployment.yaml`. `dws-call-http`,
  `dws-call-openapi` and `dws-orchestrator` are published to GHCR by their own CI
  (`.github/workflows/dws-call-http.yml`, `.github/workflows/dws-call-openapi.yml`,
  `.github/workflows/dws-orchestrator.yml`); if the GHCR packages are private, the cluster
  also needs an `imagePullSecret` for `ghcr.io`. Create it in the managed workload namespace and
  attach it to the `dws-orchestrator` and `dws-task` ServiceAccounts before submitting workflows.

## Install

```shell
kubectl apply -f k8s/controller-rbac.yaml
kubectl apply -f k8s/controller-deployment.yaml
kubectl rollout status deployment/dws-controller
```

Apply `controller-rbac.yaml` first. It provisions the workload identities before the controller
can create an orchestrator Deployment or Knative step Service. The dedicated `dws-task` and
`dws-task-override` accounts have no RoleBinding or ClusterRoleBinding from these manifests; add operator-owned grants only when
a step explicitly needs Kubernetes API access.

The resources are namespaced to `default`. To manage workflows in another namespace, update every
`metadata.namespace` and RoleBinding subject namespace in `controller-rbac.yaml`, create any image
pull Secrets there, and set `DWS_NAMESPACE` in `controller-deployment.yaml` to that workload
namespace. Apply the updated account/RBAC manifest before rolling out the controller.

Orchestrator pods run as the `dws-orchestrator` ServiceAccount, not `default`: the per-version
definition Component reads its ConfigMap with the pod's service account. The controller reads the
name from `dws.orchestrator.service-account` (env `DWS_ORCHESTRATOR_SERVICE_ACCOUNT`, default
`dws-orchestrator`), so if you rename the account in `controller-rbac.yaml` set that env var in
`controller-deployment.yaml` to match.

Knative step pods similarly run as `dws-task`, not `default`. The controller reads the name from
`dws.task.service-account` (env `DWS_TASK_SERVICE_ACCOUNT`, default `dws-task`). If the workload
namespace uses a differently named step account, create it first and set
`DWS_TASK_SERVICE_ACCOUNT` in `controller-deployment.yaml` to the exact same name. The account and
generated Knative Services must be in the namespace selected by `DWS_NAMESPACE`. The name keeps
this Kubernetes identity distinct from the `dws-step` runtime component. Earlier builds read
`DWS_STEP_SERVICE_ACCOUNT` (default `dws-step`) instead, and the current controller ignores that
variable: move any custom value to `DWS_TASK_SERVICE_ACCOUNT`, keep the old account until Revisions that name it stop serving, and
resubmit each workflow to move its step Services to the new account.

### Per-step-image pull-secret overrides

A step image can use its own pull Secrets instead of the ones attached to `dws-task`. Set
`DWS_IMAGES_PULL_SECRETS_<KIND>` (`dws.images.pull-secrets.<kind>`) on the controller to a JSON
array of Secret names in the workload namespace, for example
`DWS_IMAGES_PULL_SECRETS_CALL_HTTP='["http-cred"]'`, or `'[]'` for a public image. Kinds:
`CALL_HTTP`, `CALL_OPENAPI`, `CALL_GRPC`, `CALL_ASYNCAPI`, `CALL_A2A`, `RUN_SHELL`,
`RUN_SCRIPT_JS`, `RUN_SCRIPT_PYTHON`. The list replaces the default for that kind only.

Kubernetes and Knative add a ServiceAccount's `imagePullSecrets` to the pod's list instead of
replacing it, so overridden steps run as `dws-task-override`, which has no RBAC and must carry no
`imagePullSecrets`. The controller reads its name from `dws.task.override-service-account` (env
`DWS_TASK_OVERRIDE_SERVICE_ACCOUNT`, default `dws-task-override`) and stamps the override list on
the step's Revision template. Steps without an override keep running as `dws-task`, which is never
changed. Apply the updated `controller-rbac.yaml` (it adds `dws-task-override`) before upgrading
the controller, then resubmit the workflows that use an overridden step kind; the definition
version does not change. Malformed JSON or a blank name is rejected with an error naming the key.

## Example flow

Port-forward the controller, then post a definition:

```shell
kubectl port-forward svc/dws-controller 8080:80 &

# Dry run first — computes the plan, applies nothing.
curl -s -H 'Content-Type: application/yaml' \
     --data-binary @src/test/resources/fixtures/order.yaml \
     'http://localhost:8080/workflows?dryRun=true' | jq .

# Deploy the stack.
curl -s -H 'Content-Type: application/yaml' \
     --data-binary @src/test/resources/fixtures/order.yaml \
     http://localhost:8080/workflows | jq .
# => {"workflow":"order","versionId":"v1a2b3c4d","version":"order@v1a2b3c4d","created":true}
```

`order.yaml` is `checkInventory -> switch .inStock -> chargePayment | notifyOutOfStock`,
so the stack is three call-http steps plus one orchestrator:

```shell
export V=v1a2b3c4d   # the versionId returned above

kubectl get configmap,deployment,ksvc,components.dapr.io \
        -l dws.io/workflow=order

# NAME                                TYPE
# configmap/dws-def-order-$V          immutable definition, key `definition`
# deployment.apps/order-$V            orchestrator, DAPR_CONFIG_STORE=dws-def-order-$V
# service.serving.knative.dev/check-inventory        ghcr.io/tonylibs/dws-call-http:latest
# service.serving.knative.dev/charge-payment         ghcr.io/tonylibs/dws-call-http:latest
# service.serving.knative.dev/notify-out-of-stock    ghcr.io/tonylibs/dws-call-http:latest
# component.dapr.io/dws-def-order-$V  configuration.kubernetes, scoped to app-id `order`
```

Read live status (the cluster is the source of truth — the controller stores nothing):

```shell
curl -s http://localhost:8080/workflows | jq .
curl -s http://localhost:8080/workflows/order | jq .
curl -s http://localhost:8080/workflows/order/plan | jq .
```

### Rolling out a new version

Editing the definition and re-posting yields a new content hash, so a new stack is deployed
alongside the old one. Once the new version is up, the previous orchestrator is annotated:

```shell
kubectl get deployment -l dws.io/workflow=order \
        -o custom-columns=NAME:.metadata.name,DRAIN:.metadata.annotations.dws\\.io/drain
```

Draining itself is the orchestrator's job. The controller garbage-collects the old version's
resources once that Deployment reports zero replicas (checked on every apply and on the
`dws.reconcile.every` timer).

Re-posting *identical* content is a no-op: same hash, same version, HTTP 200 with
`"created": false`.

### Teardown

```shell
curl -s -X DELETE http://localhost:8080/workflows/order   # 204
kubectl get configmap,deployment,ksvc,components.dapr.io -l dws.io/workflow=order
# No resources found.
```

## Labels

Every managed resource carries:

| Label | Value |
|-------|-------|
| `dws.io/workflow` | workflow name, kebab-cased |
| `dws.io/version` | `v<sha256-8>` of the normalized definition |
| `dws.io/managed-by` | `dws-controller` |

These selectors drive list, rollout and teardown.
