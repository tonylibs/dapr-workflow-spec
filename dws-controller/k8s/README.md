# Deploying the DWS controller

## Contents

| File | Purpose |
|------|---------|
| `controller-rbac.yaml` | ServiceAccount + Role + RoleBinding for the controller |
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
  also needs an `imagePullSecret` for `ghcr.io` (not currently wired into the generated
  Knative Services — either make the packages public or add pull-secret support).

## Install

```shell
kubectl apply -f k8s/controller-rbac.yaml
kubectl apply -f k8s/controller-deployment.yaml
kubectl rollout status deployment/dws-controller
```

The Role is namespaced to `default`. To manage workflows in another namespace, change
`dws.namespace` (env `DWS_NAMESPACE`) and move the Role/RoleBinding accordingly.

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
# deployment.apps/order-$V            orchestrator, DEFINITION_STORE=dws-def-order-$V
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
