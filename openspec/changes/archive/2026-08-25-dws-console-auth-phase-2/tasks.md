## 1. `charts/dws` values contract

- [x] 1.1 Add `auth:` block to `charts/dws/values.yaml` with `enabled: false`, `issuer: ""`,
  `audience: ""`, `jwksURL: ""`, `dex.enabled: false` and inline documentation of the two
  configuration modes (bundled Dex vs external OIDC).
- [x] 1.2 Add helper templates in `charts/dws/templates/_helpers.tpl` for `dws.auth.issuer`,
  `dws.auth.audience`, `dws.auth.jwksURL`, and `dws.auth.configName` — the first three derive
  from Dex when `auth.dex.enabled=true`, from `auth.*` values otherwise; the fourth returns
  `{{ include "dws.controller.fullname" . }}-config`. Use `required` inside these helpers to
  fail render with an explicit message when `auth.enabled=true` and no source is available.
- [x] 1.3 Verify: `helm lint charts/dws`.

## 2. `charts/dws` Dapr Component + Configuration templates

- [x] 2.1 Add `charts/dws/templates/controller/auth-component.yaml` rendering a
  `dapr.io/v1alpha1 Component` of type `middleware.http.bearer`, gated on
  `.Values.auth.enabled`, with `scopes: [<controller fullname>]` and `spec.metadata` containing
  `issuer`, `audience`, and (when non-empty) `jwksURL`.
- [x] 2.2 Add `charts/dws/templates/controller/auth-configuration.yaml` rendering a
  `dapr.io/v1alpha1 Configuration` named `<controller fullname>-config`, gated on
  `.Values.auth.enabled`, whose `spec.appHttpPipeline.handlers` list references the auth Component
  by name and type `middleware.http.bearer`.
- [x] 2.3 Verify: `helm template charts/dws --set auth.enabled=true --set auth.issuer=https://idp.example.com --set auth.audience=dws-controller --set auth.jwksURL=https://idp.example.com/keys | Select-String -Pattern 'middleware.http.bearer'` (PowerShell — Bash equivalent: `grep 'middleware.http.bearer'`) — expect exactly two matches (Component `type`, Configuration handler `type`).
- [x] 2.4 Verify: `helm template charts/dws` (defaults) rendered output contains no
  `middleware.http.bearer` string.

## 3. `charts/dws` controller Deployment annotations

- [x] 3.1 Update `charts/dws/templates/controller/deployment.yaml`: add
  `dapr.io/app-port: "8080"` to the pod template annotations unconditionally; add
  `dapr.io/config: {{ include "dws.auth.configName" . }}` gated on `.Values.auth.enabled`.
- [x] 3.2 Keep existing `dapr.io/enabled` and `dapr.io/app-id` annotations unchanged.
- [x] 3.3 Keep liveness/readiness probes on the controller container's `http` port.
- [x] 3.4 Verify: `helm template charts/dws --set auth.enabled=true --set auth.issuer=https://idp.example.com --set auth.audience=dws-controller` renders the pod template with all four
  `dapr.io/*` annotations.

## 4. `charts/dws` controller Service front-porting

- [x] 4.1 Update `charts/dws/templates/controller/service.yaml`: when `.Values.auth.enabled=true`,
  set `targetPort: 3500` (Dapr sidecar HTTP port); when false, keep the existing
  `targetPort: http` behavior.
- [x] 4.2 Verify: `helm template charts/dws --set auth.enabled=true --set auth.issuer=https://idp.example.com --set auth.audience=dws-controller` renders `targetPort: 3500` on the controller
  Service.
- [x] 4.3 Verify: `helm template charts/dws` (defaults) renders `targetPort: http` on the
  controller Service (unchanged from pre-Phase-2).

## 5. Chart tests (`charts/dws/templates/tests/`)

- [x] 5.1 Add a Helm test that installs the release then executes an in-cluster `busybox` /
  `curlimages/curl` pod which `POST`s the controller Service with no `Authorization` header and
  asserts HTTP 401 from the Dapr sidecar. Gated on `.Values.auth.enabled=true`.
- [x] 5.2 Add a Helm test that `POST`s directly to the controller pod IP on port 8080 and asserts
  the request bypasses middleware only when `auth.enabled=false`; when `auth.enabled=true`, the
  test asserts the Service-fronted path returns 401 and documents (via test annotation) that pod
  IP is not a supported client contract. This test is a defense-in-depth signal, not a
  network-policy enforcement claim.
- [x] 5.3 Verify (LIVE — Docker Desktop k8s, ns dws-phase2, 2026-08-25): Job-based test PASSED (bearer middleware rejected unauth → 401, sidecar shutdown clean, Job succeeded=1); rendered from `helm test <release> --namespace <ns>` in the reachable Dapr+Dex environment.

## 6. OpenSpec sync

- [ ] 6.1 (deferred to archive) Apply the delta at `openspec/changes/dws-console-auth-phase-2/specs/helm-controller-deployment/spec.md` into `openspec/specs/helm-controller-deployment/spec.md` at
  archive time (openspec CLI handles this).
- [ ] 6.2 (deferred to archive) Promote the new `helm-controller-auth-middleware` spec into `openspec/specs/` at
  archive time.
- [x] 6.3 Verify: `openspec validate` before archive. (Ran `openspec validate dws-console-auth-phase-2 --strict` — valid.)

## 7. Roadmap and docs

- [x] 7.1 Update `docs/roadmaps/dws-auth.md` Phase 2 row: keep ⚠️ until live evidence is in.
  Correct the "Phase 2's chart implementation and local render gates are landed" paragraph in
  Current progress to reflect what actually lands in this change.
- [x] 7.2 Do NOT edit generated OpenWiki pages under `openwiki/`. If a page becomes stale, note
  it in the roadmap and let the scheduled OpenWiki workflow regenerate it.

## 8. Live verification (required to mark Phase 2 complete)

- [x] 8.1 Install into a reachable cluster (Docker Desktop k8s, kind, or a real cluster) with
  Dapr and Dex both reachable in-cluster. Record the exact `helm install` invocation.
- [x] 8.2 Mint a valid JWT from Dex for the `dws-controller` audience, `POST` a trivial workflow
  definition via Dapr service invocation
  (`POST http://<controller-service>:80/v1.0/invoke/<app-id>/method/api/workflows` with
  `Authorization: Bearer <jwt>`), and assert 2xx.
- [x] 8.3 Repeat with (a) no `Authorization` header, (b) an expired JWT, (c) a JWT signed by a
  different key, (d) a JWT with wrong `iss`, (e) a JWT with wrong `aud`. Assert 401 for each
  before the controller container is reached (verify via controller logs showing no request-log
  entry for the failing attempts).
- [~] 8.4 Attempt `POST` directly to the controller pod IP on port `8080` from another pod and
  assert the request is refused by the Service topology (Service targets sidecar port; pod-IP
  traffic is not a supported contract but is documented). Alternatively, if a NetworkPolicy is
  in use, confirm the direct-pod path is blocked.
- [x] 8.5 Confirm kubelet liveness/readiness probes stay Ready throughout.
- [x] 8.6 Record commands, evidence, and cluster context in
  `openspec/changes/dws-console-auth-phase-2/verify.md`.
- [x] 8.7 If 8.1–8.5 pass, flip the roadmap Phase 2 row to ✅. Otherwise keep ⚠️ and file
  follow-ups.
