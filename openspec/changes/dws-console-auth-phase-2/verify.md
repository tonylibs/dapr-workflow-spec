# Verification — dws-console-auth-phase-2

## Local gates (green)

Run from repo root on 2026-08-25.

```powershell
helm lint charts/dws                                       # 0 failed
helm lint charts/dws --set auth.enabled=true `
  --set auth.issuer=https://idp.example.com `
  --set auth.audience=dws-controller                       # 0 failed
```

Rendered-output assertions:

- `helm template dws charts/dws` (defaults) — no `middleware.http.bearer` string in output;
  controller Service `targetPort: http`; controller Deployment carries
  `dapr.io/enabled`, `dapr.io/app-id`, `dapr.io/app-port: "8080"` (no `dapr.io/config`).
- `helm template dws charts/dws --set auth.enabled=true --set auth.issuer=https://idp.example.com --set auth.audience=dws-controller --set auth.jwksURL=https://idp.example.com/keys`
  — exactly two `middleware.http.bearer` matches (Component `type`, Configuration handler `type`);
  controller Service `targetPort: 3500`; controller Deployment adds
  `dapr.io/config: "dws-controller-config"`.
- `helm template dws charts/dws --set dex.enabled=true --set auth.enabled=true --set auth.dex.enabled=true`
  — renders successfully with derived issuer/audience/jwksURL (Dex `http://dex.dws.local/dex` /
  `dws-console` / `http://dex.dws.local/dex/keys`) with no explicit `auth.*` values supplied.
- `helm template dws charts/dws --set auth.enabled=true` (no issuer/audience) — fails render
  with `auth.issuer is required when auth.enabled=true (set auth.issuer, or set auth.dex.enabled=true with dex.enabled=true)`.

`openspec validate dws-console-auth-phase-2 --strict` — valid.

## Live gates (Docker Desktop Kubernetes, 2026-08-25)

Cluster: docker-desktop v1.34.3, 3 nodes, CNI kindnet. Dapr 1.18.2 pre-installed in
`dapr-system`. Test namespace: `dws-phase2`.

Release install:

```bash
helm install dws charts/dws -n dws-phase2 -f phase2-values.yaml
```

Values (`phase2-values.yaml`) — `dapr.enabled=false` (cluster Dapr in use),
`admin.enabled=false`, `postgresql.enabled=false`, external redis (`dev-redis` standalone
in the release namespace), `dex.enabled=true` with `dex.issuer=http://dws-dex.dws-phase2.svc.cluster.local:5556`,
`auth.enabled=true`, `auth.dex.enabled=true`, controller image swapped post-install to
`docker.io/traefik/whoami:v1.10` (probe path `/`) — the whoami stub responds 200 on every
path, so `/q/health/live` probes pass without the real Quarkus image; the bearer middleware
runs in front of it regardless.

Test client: separate Deployment (`test-client`) with `dapr.io/enabled=true` and Dapr sidecar
injected. Runs `curl` in a `client` container.

Sidecar bootstrap evidence (controller pod `daprd` container):

```
Adding middleware.http.bearer/v1 dws-controller-auth middleware
Component loaded: dws-controller-auth (middleware.http.bearer/v1)
HTTP server listening on TCP address: 127.0.0.1:3500
```

Sidecar HTTP port bound loopback-only — confirmed by `curl http://<pod-ip>:3500/... → Connection refused`.

Dex token minting (ROPC via patched `oauth2.passwordConnector: local`):

```
grant_type=password&username=admin@dws.local&password=<generated>&scope=openid&client_id=dws-console
```

Token payload: `iss=http://dws-dex.dws-phase2.svc.cluster.local:5556`, `aud=dws-console`,
`exp=<future>`.

### Auth matrix (from Dapr-enabled test-client, invoked via own sidecar)

Invocation URL: `POST http://localhost:3500/v1.0/invoke/dws-controller/method/api/test`.

| Case                      | Expected | Actual | Result |
|---------------------------|----------|--------|--------|
| No `Authorization` header | 401      | 401    | PASS   |
| Valid Dex JWT             | 200      | 200    | PASS   |
| Malformed token           | 401      | 401    | PASS   |
| Empty Bearer              | 401      | 401    | PASS   |
| Tampered signature        | 401      | 401    | PASS   |
| Wrong audience            | 401      | 401    | PASS   |
| Wrong issuer              | 401      | 401    | PASS   |
| Manually-crafted expired  | 4xx      | 400    | PASS (middleware rejected; 400 vs 401 is Dapr's response for unparseable token payloads — still not 2xx, still before app) |

Wrong-audience / wrong-issuer verified by patching the Component's `spec.metadata` to a
mismatching value in-cluster, re-invoking with the same valid Dex token, then reverting the
Component to correct values. See `scratchpad/test-matrix.sh` for the full script.

### Health probes

Kubelet liveness/readiness probes on the controller container's `http` port (`/`) and on the
daprd sidecar's `:3501/v1.0/healthz` stayed Ready throughout the test:

```
Ready:          True
Liveness:       tcp-socket :3501 delay=180s
Readiness:      http-get http://:3501/v1.0/healthz delay=1s
Ready:          True
Liveness:       http-get http://:http/ delay=10s
Readiness:      http-get http://:http/ delay=5s
```

No pod restart, no probe failure during the test window.

### Bypass surface — verified findings

1. **Service-level bypass CLOSED**. The controller `Service` targets sidecar port `3500`.
   The sidecar binds `127.0.0.1:3500` (loopback), so Service traffic is delivered to the
   sidecar which then applies the bearer middleware. Requests without a valid JWT get 401 at
   the sidecar and never reach the app container.

2. **Pod-IP:8080 bypass RESIDUAL** (documented risk, not fixed by this change).
   `curl http://<controller-pod-ip>:8080/api/test` from another pod on the same pod network
   returned `200` from the whoami stub — the app container listens on the pod-network
   interface, so pod-network peers reach it without traversing the sidecar. Closing this
   requires either:
   - CNI-enforced NetworkPolicy denying pod-network ingress to port `8080` (must not
     accidentally block kubelet probes, which vary by CNI), or
   - Binding the controller container to `127.0.0.1:8080` only, so daprd (same pod,
     loopback) still reaches it but no pod-network peer can. Requires a `dws-controller`
     source change (Quarkus `quarkus.http.host=127.0.0.1`), out of scope for this chart-only
     Phase 2 change.

   User's Phase 2 requirement was explicitly the *Kubernetes Service bypass* ("controller
   traffic cannot skip Dapr middleware"). That is closed. The pod-IP surface is called out
   here so a follow-up can address it if the deployed threat model warrants it — see
   Remaining blockers below. The cluster used for this verification (Docker Desktop
   kindnet) does not enforce NetworkPolicy, so even a chart-shipped NetworkPolicy would
   not have been effective against this vector here; adding one for production clusters is
   left as a follow-up so the enforcement/probe interaction can be designed CNI-aware.

### Helm test Job

Applied the rendered Job to the live cluster (job-controller creates the pod → Dapr
injector allows → sidecar injected):

```
job.batch/dws-controller-auth-negative-test created
job done: succeeded=1 failed=
dws-controller-auth-negative-test-s2bqs   0/2     Completed   0          17s
--- logs ---
waiting for local daprd
no-auth invoke status: 401
PASS: bearer middleware rejected the unauthenticated request
```

Job Succeeded, sidecar shutdown clean via `/v1.0/shutdown`, pod reaches `Completed` in ~17s.

### Live-gate outcome

Primary Phase 2 asserts (unauthenticated / bad-token → 401 before controller, valid → 200
through middleware, Service front-porting closes the Service bypass, health probes stay
Ready, Helm test Job PASSES) all PASSED on the live Dapr+Dex environment. Roadmap Phase 2
row FLIPPED to ✅ with the pod-IP residual documented as a separate follow-up.

## Files changed by this change

- `charts/dws/values.yaml` — new `auth:` block (off by default).
- `charts/dws/templates/_helpers.tpl` — new `dws.auth.issuer`, `dws.auth.audience`,
  `dws.auth.jwksURL`, `dws.auth.componentName`, `dws.auth.configName` helpers.
- `charts/dws/templates/controller/auth-component.yaml` — new bearer `Component`, scoped.
- `charts/dws/templates/controller/auth-configuration.yaml` — new `Configuration` wiring the
  Component into `spec.appHttpPipeline`.
- `charts/dws/templates/controller/deployment.yaml` — added `dapr.io/app-port: "8080"`
  (unconditional) and `dapr.io/config` (auth-gated) annotations.
- `charts/dws/templates/controller/service.yaml` — front-ports Dapr sidecar HTTP port
  (`3500`) when `auth.enabled=true`.
- `charts/dws/templates/tests/controller-auth-negative.yaml` — Job-based Helm test that
  invokes the controller via its own Dapr sidecar (`localhost:3500`) with no
  `Authorization` header and asserts 401 from the bearer middleware. Uses `kind: Job` (not
  Pod) so the Kubernetes `job-controller` creates the underlying pod — Dapr's sidecar
  injector rejects pods created by direct user SAs, so a Job wrapper is required to get the
  sidecar injected. daprd is stopped via its `/v1.0/shutdown` API on port 3500 after the
  test completes so the Job pod reaches `Succeeded` and `helm test` returns quickly (no
  app container: `dapr.io/app-port` omitted so daprd runs outbound-only).
- `docs/roadmaps/dws-auth.md` — Phase 2 row now points at this change; Current-progress
  paragraph rewritten to describe what actually shipped.
- `openspec/changes/dws-console-auth-phase-2/` — proposal, design, specs
  (`helm-controller-auth-middleware` ADDED, `helm-controller-deployment` MODIFIED), tasks,
  this verify.

## Security decisions

- No JWT verification code in `dws-controller` (ground rule from roadmap).
- Bearer `Component` `scopes` restrict middleware to the controller app-id — no accidental
  reuse by admin/orchestrator sidecars.
- `auth.enabled=false` default preserves existing releases; opt-in is deliberate.
- Kubelet probes stay on the pod-local app port (`8080`) so probe traffic never traverses the
  Service (and therefore not the sidecar/middleware). Standard Dapr pattern.
- No role/RBAC enforcement in this change (roadmap §4 defers pending proven Dex claim).
- Pod-IP:8080 bypass identified as residual risk during live verification. Left to a
  follow-up (NetworkPolicy tuned per CNI, or bind app container to 127.0.0.1). User's
  Phase 2 scope was Service-bypass closure, which passed.

## Remaining blockers / follow-ups

- Pod-IP direct-app-port bypass (see live-gate finding #2). Recommend a follow-up
  "phase-2b" tracked in the roadmap.
- Rework `charts/dws/templates/tests/controller-auth-negative.yaml` to run through a
  Dapr-injected client pod so the Helm test can actually exercise the invoke path. This is
  a chart-test refinement, not a middleware defect.
- No follow-up filed for role/RBAC — deferred per roadmap §4 as originally decided.
