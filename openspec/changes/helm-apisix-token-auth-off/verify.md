# Verification: helm-apisix-token-auth-off

Evidence for tasks 4.x and 5.x. No token value is recorded here. Checks print presence or
absence, names and status codes only.

## Render gate (tasks 4.1–4.9), 2026-11-26

Helm `v4.3.0+gbec5b06` (official release zip; SHA-256 checked against get.helm.sh). Run from the
repository root.

| Check | Result |
|---|---|
| `helm lint charts/dws` | 1 chart linted, 0 failed (`icon is recommended` info only) |
| `helm template dws charts/dws` | OK |
| `values-schema-test.sh` | all checks passed |
| `api-gateway-render-test.sh` | all checks passed |
| `auth-pipeline-placement-test.sh` | all checks passed |
| `observability-render-test.sh` | all checks passed |
| `orchestrator-wiring-render-test.sh` | all checks passed |
| `api-tokens-render-test.sh` | all 34 check groups passed (RED before the values change: `auth off + bundled APISIX has a required reference to absent Secret(s): tokrel-admin-dapr-api-token`) |
| `schedule-time-render-test.sh` | all 15 check groups passed |

Default-values render vs the base commit (`git archive HEAD charts/dws`): identical once the
values that a `helm template` re-randomises on every run are masked (Bitnami `postgres-password`,
Dapr dev `jwt-token.pem`, and the `checksum/*` annotations derived from them). A literal byte
comparison is not possible for that reason. With `--set apisix.enabled=true`, the only masked
difference is the one added `optional: true` line on the APISIX `DWS_ADMIN_DAPR_API_TOKEN`
`secretKeyRef`.

## Live: auth off, APISIX-only (task 5.1), 2026-11-26

Cluster: docker-desktop (kind-based, Kubernetes v1.36.1), existing Dapr 1.19.1 control plane in
`dapr-system`. Throwaway namespace `dws-v177`, release `v177`, Helm 4.3.0:

```sh
helm install v177 charts/dws -n dws-v177 --create-namespace \
  --set auth.enabled=false --set apisix.enabled=true --set dapr.enabled=false \
  --set controller.enabled=false --set admin.enabled=false --set scheduleTime.enabled=false \
  --set console.enabled=false --set postgresql.enabled=false
```

- `deployment "v177-apisix" successfully rolled out`. The APISIX pod is `1/1 Running`, restarts 0,
  ready=true. The ingress controller is `2/2 Running`, and all three etcd pods are Running.
- Secrets in the namespace: only `sh.helm.release.v1.v177.v1` and `v177-etcd-jwt-token`. There is no
  `*-dapr-api-token` / `*-app-api-token` Secret.
- APISIX pod spec: `DWS_ADMIN_DAPR_API_TOKEN optional=true secret=v177-admin-dapr-api-token`.
  The referenced Secret does not exist.
- Inside the `apisix` container: `DWS_ADMIN_DAPR_API_TOKEN: unset`.
- `Failed` events matching `CreateContainerConfig` in the namespace: 0.

Before this change, the same values rendered a non-optional reference to the absent Secret, which
is the `CreateContainerConfigError` of issue #177.

Cleanup: `helm uninstall` and `kubectl delete ns dws-v177`. No cluster-scoped RBAC or IngressClass
objects were left. Helm does not delete `crds/`, so the APISIX and Gateway API CRDs this install
added stayed in place for the 5.2 run. See "Cluster cleanup" below.

## Live: auth on, bundled shared Gateway (task 5.2), 2026-11-26

`scripts/verify-gateway-sse-path.sh` (the Phase 10 gateway live check) on the same cluster, Helm
4.3.0, chart from this worktree (APISIX reference `optional: true`). The script runs in
namespace `dws-gw-e2e`, which it owns.

- **First run:** exit 1. Section 4b found `deployment/dws-controller never became ready` because
  `ghcr.io/tonylibs/dws-controller:1.18.0` could not be pulled: `failed to fetch anonymous token:
  ... 401 Unauthorized`. The image is private and this machine has no credentials for it. Nothing
  in this change caused it. APISIX, admin, console, etcd and Postgres had all started.
- **Second run** with the script's CI mode, `GW_E2E_INCLUDE_CONTROLLER=0 GW_E2E_SKIP_IMAGES=1`
  (reusing the dws-admin/dws-console images the first run had just built from this worktree):
  exit 0, 29 PASS, 0 FAIL. Relevant results:
  - `helm install succeeded (bundled APISIX + Gateway API, auth/admin/console enabled)`. Every
    Deployment and StatefulSet was Ready.
  - The admin pod carries the Dapr API and app token annotations. The token was read from Secret
    `dws-admin-dapr-api-token` and never printed.
  - `GET /dws-admin/instances` with a valid bearer returned 200 through Gateway → APISIX → Dapr →
    Nest. Missing and tampered bearers got 401.
  - `through the Gateway APISIX injects the admin Dapr API token and replaces a forged one (200
    with a valid bearer, still 401 without one)`. So the optional reference still resolves when
    the Secret exists.
  - Admin sidecar token matrix: 401 for every missing or wrong combination, 200 when both are valid.
    The admin app port rejects pod-IP calls that carry no token or a forged one.
  - SSE frame delivered while the connection was open, and the console route returned 200.
- **Not covered then:** the controller-side bearer matrix, which needs the private controller
  image. It is covered by the rerun below.

## Rerun after rebasing onto main with `imagePullSecrets` (#215), 2026-11-27

The branch was rebased onto `origin/main` (`d646b8b4`, chart `imagePullSecrets` and per-image
pull-secret overrides). The rebase was clean.

**Render gate**, Helm 4.3.0: `helm lint` and `helm template` pass, and so do all 8
`charts/dws/tests/*.sh`. That includes main's new `image-pull-secrets-render-test.sh`, and
`api-tokens-render-test.sh` still reports 34 check groups.

**5.1 again** (auth off, APISIX only, same command as above, namespace `dws-v177`): APISIX
rolled out, restarts 0, ready. The env shows `optional=true secret=v177-admin-dapr-api-token`
(absent), and inside the container the variable is unset. There were 0 `CreateContainerConfig`
events, and no token Secret was rendered.

**5.2 with the controller.** `verify-gateway-sse-path.sh` gained an opt-in
`GW_E2E_IMAGE_PULL_SECRET=<namespace>/<name>`. It copies a dockerconfigjson Secret into the test
namespace, without printing it, and installs with `imagePullSecrets[0].name`. The source was
`dws-system/ghcr-pull`. Three problems came up before a clean run, none of them caused by this
change:

- **Controller tag not published.** The script's empty `GW_E2E_CONTROLLER_TAG` falls back to the
  chart `appVersion` (`1.18.0`), and that tag returned `NotFound` once the 401 was gone. A probe pod
  using the pull secret found `main` and `latest` pullable, and the release-please version `1.4.0`
  not published. The rerun used `GW_E2E_CONTROLLER_TAG=main`.
- **`ErrImageNeverPull` for the local `dws-admin`/`dws-console:gw-e2e`.** The images were present
  and complete on every node. Kubelet's credential-verified pulls (`/var/lib/kubelet/image_manager/
  pulled`) held a record for the same image ID, pulled earlier as `172.18.0.8:5000/dws-acceptance`
  with `dws-pull-accept-ovr/regcred`. The image is therefore not treated as preloaded, and
  `pullPolicy: Never` cannot re-verify it. The fix was to rebuild both images with a unique
  `--label` (giving new image IDs with no pull records), load them into every node, and run with
  `GW_E2E_SKIP_IMAGES=1`. Kubelet state was not touched.
- **One run aborted in section 7c** when a `kubectl exec` stream from Windows dropped (`wsarecv:
  ... did not properly respond`). That was a transport error with no assertion failure, and the
  next run passed.

**Final run:**
`GW_E2E_IMAGE_PULL_SECRET=dws-system/ghcr-pull GW_E2E_CONTROLLER_TAG=main GW_E2E_SKIP_IMAGES=1`.
Result: exit 0, `36 assertions passed`, 0 FAIL, the same count as the Phase 10 baseline.

- The controller image was pulled with the chart's `imagePullSecrets`, and every workload was Ready.
- Through the Gateway, APISIX injects the admin Dapr API token and replaces a forged one: 200 with
  a valid bearer, 401 without one.
- Admin sidecar token matrix and admin app-port rejection both pass.
- Controller bearer matrix: no-auth, malformed, tampered, wrong-aud and wrong-iss all got 401, and
  valid got 200.
- SSE was delivered incrementally, the console route returned 200, and the admin prefix wins over
  the console catch-all.

## Cluster cleanup

Both scripts removed their namespaces (`dws-v177`, `dws-gw-e2e`). Helm leaves `crds/` behind on
uninstall, and the gateway script never deletes CRDs, so the 22 APISIX and Gateway API CRDs added
during 5.1 were still installed. None of them existed before this session (31 CRDs before), and no
custom resources of those kinds remained. They were deleted, and the CRD list now matches the
pre-session list exactly. No ClusterRole, ClusterRoleBinding or IngressClass was left behind.

The 2026-11-27 reruns got the same cleanup. The test namespaces (`dws-v177`, `dws-gw-e2e`,
`dws-gw-e2e-tagprobe`, `dws-gw-e2e-neverprobe`) are gone. The 22 CRDs those runs added had no
remaining objects and were deleted, so the CRD list again matches the pre-run set (31). The
relabelled `dws-admin`/`dws-console:gw-e2e` images stay in the host Docker and node containerd
stores, as after any run of the script.
