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

## Re-run after the `dws-task` rename (task 4.3)

Date: 2026-11-26  
Cluster: Docker Desktop Kubernetes (`docker-desktop`), three Ready nodes  
Namespace: `dws-pull-accept-task`  
Release: `pulltask`  
Controller commit: `e2653ba4` (image built from that tree)

### Knative tag resolution

The first run's registry address, `host.docker.internal:5001`, is listed in Knative's
`registries-skipping-tag-resolving`, so that run never exercised tag-to-digest resolution. This
re-run attached the same authenticated registry to the `kind` Docker network and referenced it
by its private address `172.18.0.8:5000`, which is not in the skip list. Knative's resolver
uses plain HTTP for RFC 1918 registry addresses, so no Knative configuration was changed. Each
node got a containerd `hosts.toml` entry for the address. Anonymous `/v2/` access returned HTTP
401 from every node. The namespace-local `regcred` Secret uses the same test credentials, keyed
by the new address.

| Workload | Tag | Image digest |
| --- | --- | --- |
| Controller | `controller-task-20261126-1211` | `sha256:3d3a60942108dff4751f238e8be2593ad2decb7f5b61b98bcbc07f21afb869ce` |
| Admin | `admin-task-20261126-1211` | `sha256:b0d768dd192a9a480e1397830661c20aac4ea941049763d4a0230b81beef1e57` |
| Scheduler | `scheduler-task-20261126-1211` | `sha256:097b5e0a96d09d436df1fdfb72cbc5a41b18fdeefb90604ca0e69ad7f6c617db` |
| Orchestrator | `orchestrator-task-20261126-1211` | `sha256:962420c9dfba55bdeb107699beb855424abcb50bbccec196f154bba97f61b245` |
| HTTP step | `call-http-task-20261126-1211` | `sha256:7925b6af008bba242aec680b46473fccec8da8745752930e2d47ef681d6c55fa` |

The controller image is new. The other tags are new references to the images from the first
run, so their layers may already have been cached on the nodes. Every reference was still
resolved and pulled through the authenticated registry, and Kubernetes recorded a `Pulled`
event for it.

### Installation

The chart was installed with the same values as the first run, plus the new image references.
`dapr.enabled=false` also disables the Redis dependency chart, so a test Redis Deployment,
Service and password Secret were created in the namespace, the same way as in the first run.
The controller, admin and scheduler Deployments were each 1/1 Ready.

- ServiceAccounts `pulltask-dws-controller`, `pulltask-dws-orchestrator`,
  `pulltask-dws-schedule-time` and `pulltask-dws-task` each carry `imagePullSecrets: [regcred]`.
  No `dws-step` ServiceAccount was rendered.
- The controller Deployment sets `DWS_TASK_SERVICE_ACCOUNT=pulltask-dws-task`.
- No RoleBinding or ClusterRoleBinding names `pulltask-dws-task`.

### Workflow execution

Submitted `private-pull-task@v0f0b9c3c` (HTTP 201). Its `probeAdmin` HTTP task calls the admin
`/health` endpoint.

- Orchestrator Pod: 2/2 Ready, ServiceAccount `pulltask-dws-orchestrator`, inherited
  `imagePullSecrets: [regcred]`.
- Knative Service `probe-admin`: Ready at `probe-admin-00001`. Its Revision template names
  `serviceAccountName: pulltask-dws-task`.
- Revision `status.containerStatuses[user-container].imageDigest`:
  `172.18.0.8:5000/dws-acceptance@sha256:7925b6af008bba242aec680b46473fccec8da8745752930e2d47ef681d6c55fa`.
  This digest was resolved from the tag `call-http-task-20261126-1211` in the Revision spec.
- Step Pod: 3/3 Ready, ServiceAccount `pulltask-dws-task`, inherited
  `imagePullSecrets: [regcred]`, user-container image ID matching the resolved digest.

Started instance `pull-task-run-1` (HTTP 202). It reached `COMPLETED` with output:

```json
{"details":{"database":{"status":"up"}},"error":{},"info":{"database":{"status":"up"}},"status":"ok"}
```

No ServiceAccount or workload manifest was patched by hand. `openspec validate
chart-image-pull-secrets --type change --strict` reported the change as valid.
