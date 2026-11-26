# Verification report

Date: 2026-11-26  
Cluster: Docker Desktop Kubernetes (`docker-desktop`), three Ready nodes  
Namespace: `dws-pull-accept-3ee9534c`  
Release: `pullsec`

## Private registry setup

- Used an authenticated local registry exposed to the cluster as
  `host.docker.internal:5001/dws-acceptance`.
- Anonymous access to `/v2/` returned HTTP 401.
- Created namespace-local Secret `regcred` with type
  `kubernetes.io/dockerconfigjson`.
- Verified that no `dws-acceptance` images were present in containerd on any of the three nodes
  before installing the release.
- Used distinct uncached tags for every first-party runtime image:

| Workload | Tag | Pulled image digest |
| --- | --- | --- |
| Controller | `controller-20261126-1055` | `sha256:ee3a7491ce5cfa9e191908c8871fa80ef7fea9124c1757767a51bd2ebfdb28d5` |
| Admin | `admin-20261126-1055` | `sha256:b0d768dd192a9a480e1397830661c20aac4ea941049763d4a0230b81beef1e57` |
| Scheduler | `scheduler-20261126-1055` | `sha256:097b5e0a96d09d436df1fdfb72cbc5a41b18fdeefb90604ca0e69ad7f6c617db` |
| Orchestrator | `orchestrator-20261126-1055` | `sha256:962420c9dfba55bdeb107699beb855424abcb50bbccec196f154bba97f61b245` |
| HTTP step | `call-http-20261126-1055` | `sha256:7925b6af008bba242aec680b46473fccec8da8745752930e2d47ef681d6c55fa` |

The cluster's preinstalled Dapr and Knative installations were reused. The local registry serves
plain HTTP, so its address was added to containerd's per-registry hosts configuration and to
Knative's `registries-skipping-tag-resolving` acceptance-cluster setting. No application
ServiceAccount was patched.

## Chart installation

Installed `charts/dws` with:

- `imagePullSecrets: [{name: regcred}]`
- matching private image repository and tag overrides for controller, admin, scheduler,
  orchestrator, and HTTP steps
- `dapr.enabled=false`, because Dapr was already installed in the cluster
- `console.enabled=false`

Helm reported release revision 1 as `deployed`. The controller, admin, and scheduler Deployments
each reported one desired, Ready, and available replica. Kubernetes `Pulled` events recorded
authenticated pulls of every private image; the resulting Pod `imageID` values matched the
digests above.

## Workflow execution

Submitted workflow `private-pull@v78e7ecb9`. Its `probeAdmin` HTTP task calls the deployed admin
health endpoint and creates Knative Service `probe-admin`.

Observed runtime state:

- Orchestrator Deployment `private-pull-v78e7ecb9`: 1/1 Ready.
- Orchestrator Pod: 2/2 Ready, ServiceAccount `pullsec-dws-orchestrator`, and inherited
  `imagePullSecrets: [regcred]`.
- Knative Service `probe-admin`: Ready at revision `probe-admin-00002`.
- Step Pod: 3/3 Ready, ServiceAccount `pullsec-dws-step`, and inherited
  `imagePullSecrets: [regcred]`.
- Step container image ID:
  `host.docker.internal:5001/dws-acceptance@sha256:7925b6af008bba242aec680b46473fccec8da8745752930e2d47ef681d6c55fa`.
- The namespace's RoleBindings reference only the controller, orchestrator, and scheduler
  ServiceAccounts; `pullsec-dws-step` has no RBAC grant.

Started instance `pull-accept-run-1`. The start endpoint returned HTTP 202, and the instance
reached `COMPLETED` with output:

```json
{"details":{"database":{"status":"up"}},"error":{},"info":{"database":{"status":"up"}},"status":"ok"}
```

The acceptance run used the chart-created ServiceAccounts throughout. No ServiceAccount was
manually patched, and no private application image was preloaded onto a node.
