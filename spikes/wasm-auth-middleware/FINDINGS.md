# Wasm auth middleware spike findings (final report)

Date: 2026-10-10 (Asia/Saigon)

This report records the findings of the live Wasm authentication middleware spike for ADR 0010
Decision 4a. The spike was conducted in the existing Docker Desktop Kubernetes cluster (not a
disposable kind cluster). Both manual investigative checks and the end-to-end repeatable probe
(`verify.sh`) have been executed successfully.

## Environment and versions

| Tool/runtime | Observed |
|---|---|
| Docker Desktop engine | 29.8.0; reachable in Docker-enabled Bash environment |
| Kubernetes | Docker Desktop, Kubernetes 1.34.3, three nodes (`desktop-control-plane`, `desktop-worker`, `desktop-worker2`) |
| kubectl | 1.36.1 |
| Helm | 4.2.4; used per owner instruction (Helm 3 was not installed) |
| Dapr sidecar | `ghcr.io/dapr/daprd:1.18.1`, verified in caller pod spec and startup logs |
| Dapr control plane | Existing cluster control plane is 1.18.2; not changed. This is a deviation from an all-components-1.18.1 environment. |
| Knative Serving | 1.21.2 |
| TinyGo | Image `tinygo/tinygo:0.34.0` used for the successful guest. `0.43.0` produced a known http-wasm compatibility failure described below. |
| Wasm guest SDK | `github.com/http-wasm/http-wasm-guest-tinygo v0.4.0` |
| Guest Wasm size | 218,133 bytes with TinyGo 0.34.0, measured from the locally built BusyBox image |
| Guest image | `docker.io/library/dws-wasm-auth-spike@sha256:8eb0b35f940534d19ee6099ae99d6d7111023c608b11f0e7785f658d33c6c396`; imported into the three local Kubernetes nodes |

The initial TinyGo 0.43.0 command from the handoff compiled a 542,861-byte module, but the
request failed with `module closed with exit_code(0)`. A reactor-mode retry stopped that error but
did not register the handler. TinyGo 0.34.0 with the SDK example's command and registration in
`main` worked. A public report for this guest SDK identifies the same TinyGo >=0.35 behavior.

The cluster already had a Dapr 1.18.2 injector. A test pod with
`dapr.io/sidecar-image: ghcr.io/dapr/daprd:1.18.1` received the requested 1.18.1 sidecar. Direct
Pod creation did not inject because the injector rejected the `kubernetes-admin` user; creating a
Deployment caused its ReplicaSet-created Pod to be injected successfully.

### Dapr control-plane version deviation

The sidecar ran strictly on `ghcr.io/dapr/daprd:1.18.1`. However, the cluster's pre-installed
Dapr control plane components (`dapr-sidecar-injector`, `dapr-operator`, `dapr-placement`,
`dapr-sentry`) ran version 1.18.2. In this spike, the 1.18.2 injector successfully injected the
1.18.1 sidecar via `dapr.io/sidecar-image: ghcr.io/dapr/daprd:1.18.1` and all sidecar runtime
features functioned as expected. If strict homogeneous 1.18.1 parity across both control plane
and data plane is required, it must be validated in an isolated cluster with a downgraded control
plane.

## Results for items 1–9

| # | Result | Observed evidence / limits |
|---|---|---|
| 1. Environment | **Pass with deviations** | Docker, kubectl, Helm 4, the local cluster, and TinyGo container builds worked. Sidecars were pinned to and reported 1.18.1. The existing control plane is 1.18.2; the cluster was not replaced. No kind cluster or TinyGo host install was used, per owner direction. |
| 2. Bearer | **Pass** | Echo target returned `Bearer spike-bearer-token-731` for `/intended`; the value was supplied only in Kubernetes Secret `bearer-auth` JSON. |
| 3. Basic | **Pass** | Echo target returned `Basic c3Bpa2UtdXNlcjpzcGlrZS1wYXNzLTg0Mg==`, which is base64 of `spike-user:spike-pass-842`; both values came from Secret `basic-auth` JSON. |
| 4. Request mutation | **Pass** | The echo server's response body and server log showed the forwarded request's `Authorization` value. This was not inferred from a response header. |
| 5. Secret reference/no leakage | **Pass** | Both Components loaded with `guestConfig.secretKeyRef` and `auth.secretStore: kubernetes`; the built-in Kubernetes secret store loaded without a SecretStore Component. A search of Component, Configuration, HTTPEndpoint, Deployment, and Pod JSON found no bearer/basic values. Caller app env had none; daprd env contained only standard injector values and identity material. Default-level daprd logs did not contain the bearer token. The repeatable probe automated this check with a clean pass. |
| 6. Isolation/overwrite | **Pass** | Same caller pod: `bearer-target/intended` → `Bearer spike-bearer-token-731`; `basic-target/intended` → `Basic c3Bpa2UtdXNlcjpzcGlrZS1wYXNzLTg0Mg==`; `bearer-target/unrelated` → `<none>`; `unrelated-target/intended` → `<none>`. Sending `Authorization: Bearer caller-supplied` to the protected endpoint still yielded the middleware's `Bearer spike-bearer-token-731`. |
| 7. Delivery, restart, native sidecar | **Pass** | Classic sidecar: `install-wasm` completed before daprd loaded components on initial start. After `kubectl rollout restart`, the init container again copied the module and the Bearer call passed; status timestamps rounded to the same second (less than one second at displayed precision). With `dapr.io/enable-native-sidecar: "true"`, injected native daprd appeared after the app init container in the init-container list; copy completed at 05:42:35Z, daprd started at 05:42:36Z, loaded the Wasm component, and the Bearer call passed. Native sidecar worked in this cluster. |
| 8. Knative | **Pass after flags** | Initially rejected with `pod spec support for init-containers is off`. Enabled `kubernetes.podspec-init-containers` and `kubernetes.podspec-volumes-emptydir` via the KnativeServing CR, then a KService became Ready. A cold request returned Bearer in 2.831618s; an unrelated-path request returned `<none>` in 0.016531s. A no-init baseline KService cold request took 2.242702s: this single paired sample suggests +0.589s, but is noisy and not a controlled benchmark. Init container start/finish timestamps rounded to the same second. The first experiment without an app-side readiness retry got a 502 after 8.889539s because daprd was not listening when the app immediately called it; a retry after startup passed. Dapr metrics had to move from the default 9090 to 9095 because Knative's queue proxy already occupied 9090. Both Knative feature flags were removed from the KnativeServing config after the run; the original settings (absent) are restored. |
| 9. One Configuration per pod | **Pass** | The caller pod used one `dapr.io/config: wasm-auth-merged` Configuration containing both Wasm handlers and `spec.tracing`. Daprd logs showed the configured trace sampler and both middleware components loaded. A pod has one config annotation; compose the pipeline and tracing block into that Configuration. |

## Header evidence

Observed from caller requests to the echo target:

```text
bearer-target/intended:       Bearer spike-bearer-token-731
basic-target/intended:        Basic c3Bpa2UtdXNlcjpzcGlrZS1wYXNzLTg0Mg==
bearer-target/unrelated:      <none>
unrelated-target/intended:    <none>
caller-supplied Bearer header: replaced by Bearer spike-bearer-token-731
```

Echo server logs confirmed: `path=/intended authorization=Basic ...`,
`path=/unrelated authorization=<none>`, and `path=/intended authorization=Bearer ...`.

## Automated verification probe (`verify.sh`)

The standalone verification script `spikes/wasm-auth-middleware/verify.sh` was syntax-checked
(`bash -n`) and executed end-to-end against the cluster in a Docker-enabled Bash environment.

### Verification output

```text
namespace/dws-wasm-auth-spike created
PASS: Bearer and Basic reached the echo target; endpoint/path isolation and overwrite passed
Dapr image: ghcr.io/dapr/daprd:1.18.1; guest image: docker.io/library/dws-wasm-auth-spike@sha256:8eb0b35f940534d19ee6099ae99d6d7111023c608b11f0e7785f658d33c6c396
Guest size: 218133 /auth.wasm bytes
```

### Checks automated by the probe

1. Rebuilds the Wasm guest BusyBox image with TinyGo 0.34.0 and imports it into all Kubernetes cluster nodes.
2. Creates the isolated namespace `dws-wasm-auth-spike` and provisions runtime Secrets (`bearer-auth`, `basic-auth`).
3. Deploys echo server, HTTPEndpoints, Wasm middleware Components, and caller Deployment pinned to `daprd:1.18.1`.
4. Confirms `actual_image == ghcr.io/dapr/daprd:1.18.1`.
5. Probes service invocation through Dapr `http://127.0.0.1:3500/v1.0/invoke/<target>/method/<path>`.
6. Asserts Bearer token injection on `/intended`.
7. Asserts Basic credentials injection on `/intended`.
8. Asserts path and endpoint isolation (`bearer-target/unrelated` and `unrelated-target/intended` yield `<none>`).
9. Asserts header overwrite (caller-supplied `Authorization` header is overwritten by configured middleware).
10. Scans default-level daprd container logs to verify secret tokens were not leaked.
11. Exits cleanly with code 0 and automatically invokes the cleanup trap.

## Cluster cleanup and credential hygiene

- **Namespace cleanup**: The EXIT trap of `verify.sh` triggered deletion of namespace
  `dws-wasm-auth-spike`. Deletion completed fully and was verified with
  `kubectl wait --for=delete namespace/dws-wasm-auth-spike`.
- **Targeted scope**: Only `dws-wasm-auth-spike` was removed. All cluster system and platform
  namespaces (`cert-manager`, `dapr-system`, `default`, `flux-system`, `istio-system`, `kafka`,
  `knative-eventing`, `knative-operator`, `knative-serving`, `kube-system`, etc.) remained active
  and untouched.
- **Credential audit**: Cluster-wide audit confirmed that zero demo tokens or secrets remain in
  the cluster:
  - No Secrets, ConfigMaps, or Dapr CRDs (`Component`, `Configuration`, `HTTPEndpoint`) containing
    `spike-bearer`, `spike-user`, `spike-pass`, or `wasm-auth` exist anywhere in the cluster.
  - No leftover pods or resources from the spike remain.
- **KnativeServing configuration**: Feature flags enabled during manual testing
  (`kubernetes.podspec-init-containers`, `kubernetes.podspec-volumes-emptydir`) were reverted to
  their original absent state; the Knative operator reports Ready.

## Verdict

**Adopt Decision 4a with changes.** The central design works on Dapr 1.18.1:
1. The calling app's `httpPipeline` guest successfully mutates outgoing service-invocation requests.
2. Credentials securely resolve from Secret-backed `guestConfig` without logging or leaking into pod environments.
3. Target endpoint and path filtering provides strict isolation.
4. TinyGo 0.34.0 must be pinned for building `http-wasm-guest-tinygo v0.4.0` guests (TinyGo >=0.35 is incompatible).
5. Knative Services require enabling `podspec-init-containers` and `podspec-volumes-emptydir`, shifting Dapr metrics port to avoid queue-proxy collision (9090 -> 9095), and handling daprd readiness on cold starts.

## Remaining work

1. **Update ADR 0010**: Formalize Decision 4a as accepted in ADR 0010, noting TinyGo 0.34.0 pinning and Knative platform prerequisites.
2. **Compiler & Deployment integration**: When scheduled, update `dws-controller` to:
   - Generate the Wasm init-container definition and volume mounts for step services requiring auth.
   - Generate Secret-backed `middleware.http.wasm` Components and merged pipeline Configurations.
   - Configure Dapr metrics port to 9095 for Knative step services.
3. **Optional pure-1.18.1 control-plane testing**: If upstream requirements mandate testing against an environment where both Dapr control plane and data plane are 1.18.1, run `verify.sh` on an isolated test cluster with Dapr 1.18.1 control plane.
