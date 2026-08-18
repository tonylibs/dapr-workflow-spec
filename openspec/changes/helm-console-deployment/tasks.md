## 1. Helpers and values

- [ ] 1.1 Add `dws.console.fullname` helper to `charts/dws/templates/_helpers.tpl` returning
  `<dws.fullname>-console`, and `dws.console.selectorLabels` extending `dws.selectorLabels` with
  `app.kubernetes.io/component: console` — same pattern as `dws.admin.*`
- [ ] 1.2 Add a `console:` block to `charts/dws/values.yaml`: `enabled: false`, `replicaCount: 1`,
  `image.{repository,tag,pullPolicy}` (`ghcr.io/tonylibs/dws-console`), `service.port: 3000`, and
  an `ingress:` sub-block: `enabled: false`, `className: ""`, `host: ""`,
  `annotations: {nginx.ingress.kubernetes.io/rewrite-target: /$2}` (ingress-nginx default),
  `adminPath: /dws-admin`, `tls: []`
- [ ] 1.3 Comment `values.yaml`'s `console.ingress.annotations` explaining the default targets
  ingress-nginx and what an operator on another controller needs to override, per design.md's
  path-prefix-stripping decision

## 2. Console templates

- [ ] 2.1 Write `templates/console/deployment.yaml` — gated by `.Values.console.enabled`;
  `replicas` from `console.replicaCount`; selector/pod labels from `dws.console.selectorLabels` +
  `dws.labels`; container image from `console.image.*`; container port 3000 (`http`); no
  `VITE_DWS_ADMIN_URL` or other runtime env var (build-time only, per design.md); liveness/
  readiness probes `httpGet` `/healthz` on port `http`; no `securityContext` (image's built-in
  non-root `node` user), mirroring `templates/admin/deployment.yaml`
- [ ] 2.2 Write `templates/console/service.yaml` — gated by `.Values.console.enabled`; `ClusterIP`;
  port from `console.service.port` targeting container port `http` (3000)
- [ ] 2.3 Write `templates/console/ingress.yaml` — gated by `.Values.console.enabled` AND
  `.Values.console.ingress.enabled`; `apiVersion: networking.k8s.io/v1`; one rule on
  `console.ingress.host` with two paths: `console.ingress.adminPath + "(/|$)(.*)"`
  (`pathType: ImplementationSpecific`) forwarding to the admin Service (`dws.admin.fullname` /
  `admin.service.port`), and `/` (`pathType: Prefix`) forwarding to the console Service; apply
  `console.ingress.className` (only when set) and `console.ingress.annotations`; render
  `spec.tls` from `console.ingress.tls` when non-empty

## 3. CI — template matrix

- [ ] 3.1 Add a "Template (console enabled)" step to `verify` in `.github/workflows/helm.yml`:
  `helm template ... --set console.enabled=true --set console.ingress.enabled=true --set
  console.ingress.host=console.example.test`, asserting a console Deployment/Service/Ingress
  render (mirrors the existing `controller.enabled=false` assertion step's grep pattern)
- [ ] 3.2 Add a "Template (console disabled, default)" step confirming no
  `app.kubernetes.io/component: console` resources render with default values (the current
  default already covers this implicitly, but assert it explicitly so a future default flip is
  caught if it happens by accident)

## 4. CI — Ingress reachability

- [ ] 4.1 Add a new `integration-console` job to `.github/workflows/helm.yml` (`needs: verify`,
  parallel to `integration`/`integration-dapr-preinstalled`): create a `kind` cluster with
  `extraPortMappings` for host ports 80/443 (per design.md's CI decision)
- [ ] 4.2 Install an ingress-nginx controller pinned to a released manifest version, using the
  standard kind-compatible manifest (`NodePort` service bound to the mapped host ports); wait for
  the ingress-nginx admission webhook Deployment to be ready before installing the chart
- [ ] 4.3 `helm install` with `controller.enabled=false`, `admin.enabled=true`,
  `postgresql.enabled=true`, `dapr.enabled=true`, `admin.image.tag=latest`,
  `console.enabled=true`, `console.ingress.enabled=true`,
  `console.ingress.host=console.127.0.0.1.nip.io` (or equivalent resolvable-to-localhost host),
  `console.ingress.className=nginx`, plus the ghcr pull secret (same pattern as `integration`)
- [ ] 4.4 Wait for the console and admin Deployment rollouts and the rendered Ingress's address/
  readiness
- [ ] 4.5 `curl` the mapped host port with the `Host:` header set to `console.ingress.host` for
  `/` (assert HTTP 200 and HTML content) and for `console.ingress.adminPath + "/health"` (assert
  HTTP 200 and admin's health JSON), proving the prefix-strip rewrite actually reaches admin
- [ ] 4.6 Add a debug-on-failure step (pod state, events, ingress-nginx controller logs, console/
  admin pod logs) mirroring the existing `integration` job's failure diagnostics

## 5. Verify

- [ ] 5.1 Run `helm lint charts/dws` — expect no errors
- [ ] 5.2 Run `helm template charts/dws` (defaults) — confirm no console resources render
- [ ] 5.3 Run `helm template charts/dws --set console.enabled=true --set console.ingress.host=console.example.test --set console.ingress.className=nginx` —
  confirm exactly one console Deployment/Service/Ingress render, the Ingress has the two expected
  path rules, and the admin rule carries the rewrite-target annotation
- [ ] 5.4 Run `helm template charts/dws --set console.enabled=true` (ingress left at its own
  default `false`) — confirm the console Deployment/Service render but no Ingress renders
- [ ] 5.5 Run `helm template charts/dws --set console.enabled=true --set console.ingress.enabled=true --set console.ingress.tls[0].secretName=console-tls --set console.ingress.tls[0].hosts[0]=console.example.test` —
  confirm `spec.tls` renders on the Ingress
- [ ] 5.6 On a real cluster (or via the new `integration-console` CI job locally with `act`/kind):
  confirm `/` serves the console shell and `console.ingress.adminPath + "/health"` proxies through
  to admin's health response
- [ ] 5.7 Show the rendered console Deployment, Service, and Ingress to the user for Phase 6
  sign-off

## 6. Docs (post-archive follow-up)

- [ ] 6.1 After this change is applied and archived, update `docs/roadmaps/helm-packaging.md`:
  flip Phase 6 to ✅, fill in the `console/` entry in the chart-layout tree, update Phase 7's
  ingress-values note (no longer blocked on Phase 6)
- [ ] 6.2 Update the Helm row in `docs/roadmaps/README.md` to reflect Phase 6 completion
- [ ] 6.3 Do not hand-edit `openwiki/` — it's synced by `.github/workflows/openwiki-update.yml`
