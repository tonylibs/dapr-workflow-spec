## 1. Values contract and helpers

- [x] 1.1 Add `adminGateway.enabled: false` default plus `adminGateway.image.repository`, `adminGateway.image.tag`, `adminGateway.image.pullPolicy`, `adminGateway.service.type`, `adminGateway.service.port`, `adminGateway.corsOrigins: []`, and `adminGateway.replicaCount` to `charts/dws/values.yaml`, with brief comments matching the tone of the existing `admin.*` block.
- [x] 1.2 Add `dws.adminGateway.fullname` / `dws.adminGateway.selectorLabels` / `dws.adminGateway.serviceName` helpers to `charts/dws/templates/_helpers.tpl`, mirroring the existing `dws.controller.*` helpers.
- [x] 1.3 Add `dws.admin.auth.componentName` and `dws.admin.auth.configurationName` helpers to `_helpers.tpl` (fully qualified names to avoid confusion with Phase 2's plain `dws.auth.componentName`). Update the existing Phase 2 helpers' comment to explicitly note they are controller-scoped.

## 2. dws-admin sidecar bearer gate

- [x] 2.1 Create `charts/dws/templates/admin/auth-component.yaml`, mirroring `templates/controller/auth-component.yaml`, rendered only when `.Values.admin.enabled` and `.Values.auth.enabled` are both true. Scope to the `dws-admin` app-id via `scopes: [<admin fullname>]`.
- [x] 2.2 Create `charts/dws/templates/admin/auth-configuration.yaml`, mirroring `templates/controller/auth-configuration.yaml`; the `spec.appHttpPipeline.handlers` entry references the Component from 2.1.
- [x] 2.3 In `charts/dws/templates/admin/deployment.yaml`, add a conditional `dapr.io/config: {{ include "dws.admin.auth.configurationName" . }}` pod annotation guarded by `.Values.auth.enabled` (unchanged when auth is off).
- [x] 2.4 In `charts/dws/templates/admin/service.yaml`, add a conditional second port (name `dapr-http`, `port: 3500`, `targetPort: 3500`) guarded by `.Values.auth.enabled`. Existing port 3000 stays unconditional.

## 3. Admin gateway chart objects

- [x] 3.1 Create `charts/dws/templates/admin-gateway/configmap.yaml` — a `ConfigMap` carrying an `nginx.conf` (or a single `default.conf`) that (a) requires a matching `Origin` from `.Values.adminGateway.corsOrigins`, (b) returns `204` with the CORS response headers for `OPTIONS`, (c) `proxy_pass`es everything else to `http://{{ dws.admin.fullname }}.{{ .Release.Namespace }}.svc.cluster.local:3500/v1.0/invoke/{{ dws.admin.fullname }}/method/workflows`, preserving `Authorization`, `Content-Type`, request body, and the `dryRun` query parameter, and (d) rejects other origins with `403`.
- [x] 3.2 Create `charts/dws/templates/admin-gateway/deployment.yaml` — nginx `Deployment` with the ConfigMap mounted and `app.kubernetes.io/component: admin-gateway` in selector/labels.
- [x] 3.3 Create `charts/dws/templates/admin-gateway/service.yaml` — `Service` exposing the gateway's listen port with the same component label.
- [x] 3.4 Add a render-time guard (in a `_validation.tpl` helper or inline in the gateway templates) that `fail`s with an explicit message when `.Values.adminGateway.enabled` is true and `.Values.adminGateway.corsOrigins` is empty, or contains `"*"`.

## 4. Documentation

- [x] 4.1 ~~Add a `charts/dws/README.md` (or extend the existing one) section documenting the new `adminGateway.*` values and how `auth.enabled=true` reshapes the admin Service and pod template.~~ Skipped: `charts/dws` has no `README.md`, and the operator-facing documentation for the new `adminGateway.*` block already lives in `values.yaml`'s comments alongside the values themselves (matching the existing `admin.*`, `auth.*`, `dex.*`, `console.*` style). Creating a chart README solely for Phase 4 would fragment that pattern; if a chart README is added later it should describe the whole chart, not just this phase.
- [x] 4.2 Update `docs/roadmaps/dws-auth.md` Phase 4 row + Current progress section once implementation is verified.

## 5. Local chart gates (fast, in-repo)

- [x] 5.1 `helm lint charts/dws` clean with defaults.
- [x] 5.2 `helm lint charts/dws --set auth.enabled=true --set auth.issuer=https://idp.example.com --set auth.audience=dws-console` clean.
- [x] 5.3 `helm lint charts/dws --set adminGateway.enabled=true --set 'adminGateway.corsOrigins={https://console.example.com}'` clean.
- [x] 5.4 `helm template charts/dws` with defaults — confirm no admin-gateway objects, no admin `auth-*` objects, no `dapr.io/config` on the admin pod, admin Service exposes only port 3000.
- [x] 5.5 `helm template charts/dws --set auth.enabled=true …` — confirm admin `auth-*` Component + Configuration render, `dapr.io/config` annotation is on the admin pod, admin Service exposes port 3000 and a second port targeting 3500.
- [x] 5.6 `helm template charts/dws --set adminGateway.enabled=true --set 'adminGateway.corsOrigins={https://console.example.com}'` — confirm the three gateway objects render and the ConfigMap contains the expected `Access-Control-Allow-Origin`, `proxy_pass`, and `OPTIONS 204` blocks.
- [x] 5.7 `helm template charts/dws --set adminGateway.enabled=true` without `corsOrigins` — confirm it fails render with a message naming `adminGateway.corsOrigins`.
- [x] 5.8 `helm template charts/dws --set adminGateway.enabled=true --set 'adminGateway.corsOrigins={*}'` — confirm it fails render with a wildcard-not-permitted error.

## 6. Live acceptance (mirrors Phase 2's verify.md)

- [x] 6.1 `helm install dws-phase4 charts/dws -n dws-phase4 --create-namespace` against a local Docker Desktop cluster with `auth.enabled=true` (Dex-derived mode) and `adminGateway.enabled=true` with the console origin in `corsOrigins`. Admin + admin-gateway + controller Pods all reached Ready on 2026-09-01.
- [x] 6.2 OPTIONS preflight from the console origin returned 204 with the expected explicit-origin, methods, headers, and credentials response headers; a disallowed origin returned 403. nginx handled both locally and no request reached `dws-admin`.
- [ ] 6.3 **Blocked by the admin dual-listener contract.** With the chart-rendered `dapr.io/app-port: "3001"`, a valid JWT passes the admin sidecar but Dapr invokes the separate `@dbc-tech/nest-dapr` server, which has no `POST /workflows`, so the result is 404 `Cannot POST /workflows`. A temporary live patch to app-port `3000` proves the rest of the chain (200 idempotent apply; `dryRun=true` creates zero resources), but cannot ship because pub/sub callbacks are served on 3001. See `verify.md`.
- [x] 6.4 No-`Authorization` request returned 401 from the admin sidecar with exactly one credentialed CORS origin.
- [x] 6.5 Malformed token, a middle-signature-byte tamper, a separately minted wrong-`aud` token, and a token minted by the Phase 2 Dex issuer each returned 401 from the admin sidecar.
- [x] 6.6 Existing reads (`GET /workflows`, `GET /instances`, `/instances/events` SSE) returned 200 against the direct admin Service on port 3000 with no token attached (`text/event-stream` for SSE).
- [x] 6.7 `helm uninstall dws-phase4 -n dws-phase4` removed the Phase 4 chart objects; reinstall with `adminGateway.enabled=false` completed Ready with zero gateway Deployments, Services, or ConfigMaps. Test-created `phase4accept` resources were removed separately by their exact `dws.io/workflow` label.

## 7. Verify + capture evidence

- [x] 7.1 Write `openspec/changes/dws-console-auth-phase-4/verify.md`. Local `helm lint`/`helm template` gate evidence captured (three lint shapes; defaults render zero Phase 4 objects; `auth.enabled=true` renders both sidecars' bearer gate + `dapr-http` port + `dapr.io/config`; `adminGateway.enabled=true` renders the three gateway objects and the expected ConfigMap blocks; the two `fail`-render guards produce the expected messages). Live acceptance (§6.1–6.7) marked as owed with the exact steps to run, tracked in the same file — will be appended when a Docker Desktop run happens.
- [x] 7.2 Run `openspec validate --change dws-console-auth-phase-4 --strict` and address any findings.
