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

## Shared task account and per-step-image override (task 7.1)

Date: 2026-11-26  
Cluster: Docker Desktop Kubernetes (`docker-desktop`), three Ready nodes  
Namespace: `dws-pull-accept-ovr`  
Release: `pullovr`  
Tree: working tree on top of `1921f7dd` with tasks 5.1–6.3 applied (controller image built from it
after `./mvnw verify` passed, 469 tests)

### Registry and credentials

The same authenticated registry as the task 4.3 run (`172.18.0.8:5000`, anonymous `/v2/` → 401)
gained a second htpasswd user, `stepuser`. The namespace holds two pull Secrets: `regcred`
(the existing `dws` user) and `step-cred` (`stepuser`).

| Workload | Tag | Image digest |
| --- | --- | --- |
| Controller | `controller-ovr-20261126` | built from this tree |
| Console | `console-ovr-20261126` | local `dws-console:gw-e2e` re-tagged |
| Admin, scheduler, orchestrator, HTTP step | `*-task-20261126-1211` | as in the task 4.3 run |
| Shell step | `run-shell-ovr-20261126` | `sha256:62f2b7885904775f6a7a016f0c1c5aa5392b5ddeaf011df261bdca494bf8e50f` (built from `dws-run`, new to every node) |

### Installation

Values: `imagePullSecrets: [{name: regcred}]`, `imagePullServiceAccount.enabled=true`,
`console.enabled=true`, `dapr.enabled=false` (test Redis created as before), private image
overrides as above, and `controller.images.run.shell.imagePullSecrets: [{name: step-cred}]`.
Controller, admin, console and scheduler were each 1/1 Ready.

- ServiceAccounts: `pullovr-dws-controller`, `pullovr-dws-orchestrator`,
  `pullovr-dws-schedule-time` and `pullovr-dws-task` each carry `imagePullSecrets: [regcred]`;
  `pullovr-dws-task-override` carries none.
- RoleBindings name only the controller, orchestrator and scheduler accounts; no RoleBinding or
  ClusterRoleBinding names `pullovr-dws-task` or `pullovr-dws-task-override`.
- Pods: admin and console run as `pullovr-dws-task`; controller and scheduler keep their own
  accounts. All four have pod-level `imagePullSecrets: [regcred]`.
- Controller env: `DWS_TASK_SERVICE_ACCOUNT=pullovr-dws-task`,
  `DWS_TASK_OVERRIDE_SERVICE_ACCOUNT=pullovr-dws-task-override`,
  `DWS_ORCHESTRATOR_SERVICE_ACCOUNT=pullovr-dws-orchestrator`,
  `DWS_IMAGES_PULL_SECRETS_RUN_SHELL=["step-cred"]`, and no other `DWS_IMAGES_PULL_SECRETS_*`.

### Workflow execution

Submitted `private-pull-ovr@v766aca29` (HTTP 201): `probeAdmin` (`call: http`, no override) then
`shellEcho` (`run: shell`, `echo override-ok`, overridden).

| Knative Service | Revision `serviceAccountName` | Revision `imagePullSecrets` | Resolved digest |
| --- | --- | --- | --- |
| `probe-admin` | `pullovr-dws-task` | none | `172.18.0.8:5000/dws-acceptance@sha256:7925b6af…6c55fa` |
| `shell-echo` | `pullovr-dws-task-override` | `[step-cred]` | `172.18.0.8:5000/dws-acceptance@sha256:62f2b788…f8e50f` |

- Both Revisions resolved their tags to digests and became Ready (`-00001`); step Pods 3/3 Ready.
- `probe-admin` Pod: account `pullovr-dws-task`, effective pull secrets `[regcred]` (inherited).
- `shell-echo` Pod: account `pullovr-dws-task-override`, effective pull secrets `[step-cred]` only.
  Kubernetes recorded `Successfully pulled image …@sha256:62f2b788…` for it, so the uncached image
  was pulled with `step-cred` alone.
- Orchestrator Pod: account `pullovr-dws-orchestrator`, inherited `[regcred]`, 2/2 Ready.

Started `pull-ovr-run-1` (HTTP 202). It reached `COMPLETED` with output `override-ok`.

The `call-http`, orchestrator and admin image references were already cached on nodes from the
earlier runs ("already present"); their pull path through `regcred` was proven in the task 4.3
run. The `[]` (public image) variant was not exercised live, because every DWS image on GHCR
requires authentication; it is covered by chart render and controller tests. No ServiceAccount or
workload was patched by hand.
