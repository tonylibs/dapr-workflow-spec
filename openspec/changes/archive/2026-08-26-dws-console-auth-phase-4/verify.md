# dws-console-auth-phase-4 — verify

Change: `dws-console-auth-phase-4` (auth roadmap Phase 4).

## Status

Chart-side implementation and local `helm lint` / `helm template` gates are green.
**Live acceptance matrix (§Live acceptance below) is not yet run** — it needs a Docker
Desktop cluster (or equivalent) with the `dapr` and `dex` subcharts installed. Update
this file with the live evidence when that run completes.

## Local chart gates (run from `charts/dws/`)

All commands executed against the current working tree; helm 3.19.

### `helm lint` — three shapes

```
$ helm lint .
==> Linting .
[INFO] Chart.yaml: icon is recommended

1 chart(s) linted, 0 chart(s) failed

$ helm lint . --set auth.enabled=true \
              --set auth.issuer=https://idp.example.com \
              --set auth.audience=dws-console \
              --set auth.jwksURL=https://idp.example.com/keys
==> Linting .
[INFO] Chart.yaml: icon is recommended

1 chart(s) linted, 0 chart(s) failed

$ helm lint . --set adminGateway.enabled=true \
              --set 'adminGateway.corsOrigins={https://console.example.com}'
==> Linting .
[INFO] Chart.yaml: icon is recommended

1 chart(s) linted, 0 chart(s) failed
```

### `helm template` — defaults render zero Phase 4 objects

```
$ helm template . | grep -cE 'admin-gateway|dapr.io/config|name: dapr-http|middleware.http.bearer'
0
```

The default render carries no `admin-gateway` object, no `middleware.http.bearer`
Component, no `dapr.io/config` annotation on the admin pod, and the admin Service exposes
only port `3000`. `helm upgrade` from a Phase 3 release without setting `auth.enabled` or
`adminGateway.enabled` is a topological no-op.

### `helm template` — `auth.enabled=true` renders both sidecars' bearer gate

```
$ helm template . --set auth.enabled=true --set auth.issuer=https://idp.example.com --set auth.audience=dws-console \
    | grep -E 'dapr.io/config|name: dapr-http|middleware.http.bearer|kind: Configuration|name:.*-auth$'
    - name: dapr-http                                # admin Service now front-ports 3500
        dapr.io/config: "release-name-dws-admin-config"       # admin pod picks up the new Configuration
        dapr.io/config: "release-name-dws-controller-config"  # controller unchanged (Phase 2)
  name: release-name-dws-admin-auth                  # NEW: admin bearer Component
  type: middleware.http.bearer
  name: release-name-dws-controller-auth             # unchanged (Phase 2)
  type: middleware.http.bearer
kind: Configuration
kind: Configuration
      - name: release-name-dws-admin-auth            # NEW: admin Configuration references it
        type: middleware.http.bearer
kind: Configuration
      - name: release-name-dws-controller-auth
        type: middleware.http.bearer
```

Confirms:
- Admin `-auth` Component and `-config` Configuration both render, scoped to the admin
  app-id (`scopes: [<admin fullname>]` in the Component — verified in full template
  output; grep above only shows the header lines).
- Admin pod carries `dapr.io/config: <admin fullname>-config` in addition to its existing
  Dapr annotations.
- Admin Service exposes a second port `dapr-http` targeting the sidecar's `3500`
  alongside the unconditional app port `3000`.
- Controller's Phase 2 objects are unaffected — separately named and scoped.

### `helm template` — gateway objects render and the ConfigMap has the expected blocks

```
$ helm template . --set adminGateway.enabled=true \
                  --set 'adminGateway.corsOrigins={https://console.example.com}' \
    | grep -E 'admin-gateway|Access-Control|proxy_pass|listen 80' | head -20
# Source: dws/templates/admin-gateway/configmap.yaml
  name: release-name-dws-admin-gateway
    app.kubernetes.io/component: admin-gateway
      listen 80 default_server;
          add_header Access-Control-Allow-Origin $dws_allowed_origin always;
          add_header Access-Control-Allow-Methods "POST, OPTIONS" always;
          add_header Access-Control-Allow-Headers "Authorization, Content-Type" always;
          add_header Access-Control-Allow-Credentials "true" always;
          add_header Access-Control-Max-Age 600 always;
        add_header Access-Control-Allow-Origin $dws_allowed_origin always;
        add_header Access-Control-Allow-Credentials "true" always;
        proxy_pass http://release-name-dws-admin.default.svc.cluster.local:3500/v1.0/invoke/release-name-dws-admin/method/workflows$is_args$args;
# Source: dws/templates/admin-gateway/service.yaml
  name: release-name-dws-admin-gateway
    app.kubernetes.io/component: admin-gateway
    app.kubernetes.io/component: admin-gateway
# Source: dws/templates/admin-gateway/deployment.yaml
  name: release-name-dws-admin-gateway
    app.kubernetes.io/component: admin-gateway
```

Confirms:
- All three gateway objects (`ConfigMap`, `Service`, `Deployment`) render.
- The nginx site config carries the exact `Access-Control-*` response headers required by
  the browser preflight, terminates OPTIONS with `204`, and `proxy_pass`es non-OPTIONS
  requests to the Dapr service-invocation URL (`/v1.0/invoke/<admin fullname>/method/workflows`).
- The `$is_args$args` suffix preserves the `dryRun` (and any other) query parameter
  verbatim to the controller.

### `helm template` — render-time validation

```
$ helm template . --set adminGateway.enabled=true
Error: execution error at (dws/templates/admin-gateway/service.yaml:1:4):
  adminGateway.enabled=true requires adminGateway.corsOrigins to be a non-empty list of
  explicit browser origins (e.g. https://console.example.com)

$ helm template . --set adminGateway.enabled=true --set 'adminGateway.corsOrigins={*}'
Error: execution error at (dws/templates/admin-gateway/service.yaml:1:4):
  adminGateway.corsOrigins may not contain "*": a wildcard Access-Control-Allow-Origin
  cannot combine with a credentialed cross-origin write path — list each console origin
  explicitly
```

Both failure modes surface with an explicit, actionable message at render time — matching
the specs' behaviour contract in `helm-admin-gateway`.

## Live acceptance (owed)

Not yet run. The following matrix mirrors Phase 2's `verify.md` and needs a live Docker
Desktop cluster (namespace `dws-phase4`) with Dex, Dapr, and the `dws-admin`/`dws-controller`
images available. Steps:

1. `helm install dws-phase4 charts/dws -n dws-phase4 --create-namespace`
   with `auth.enabled=true`, `auth.dex.enabled=true`, `dex.enabled=true`,
   `adminGateway.enabled=true`, and `adminGateway.corsOrigins={https://console.example.com}`.
2. `OPTIONS <gateway>/workflows` from an allowed origin → 204 with the CORS headers and
   no request in the `dws-admin` container's access log.
3. `POST <gateway>/workflows` with a valid Dex-issued JWT → 200/201 from `dws-controller`
   via gateway → admin sidecar → admin app → admin sidecar → controller sidecar →
   controller app.
4. Auth failure matrix: no-Auth / malformed / tampered-sig / wrong-aud / wrong-iss each
   return 401 from the admin sidecar, no request in the `dws-admin` container's access
   log.
5. Reads on the direct admin Service on `3000` (`GET /workflows`, `GET /instances`, SSE)
   still work unauthenticated — Phase 6 territory, verified unchanged here.
6. `helm uninstall` cleans up; a subsequent `helm install` with
   `adminGateway.enabled=false` renders zero gateway objects.

## Residuals (not blocking this change)

- **DAPR_CONTROLLER_APP_ID chart wiring for `dws-admin`.** The Phase 3 relay defaults its
  target app-id to `dws-controller`. This works only for a release named `dws`; every other
  release name breaks the relay. `templates/admin/deployment.yaml` should set
  `DAPR_CONTROLLER_APP_ID: {{ include "dws.controller.fullname" . }}` in the container
  `env`. Tracked as a follow-up; not this phase's scope.
- **Pod-IP:8080 direct-app-port bypass on `dws-controller`.** Carried over from Phase 2's
  `verify.md`. Unrelated to Phase 4 (the admin sidecar's app port is `3001`, unchanged),
  but the equivalent hole exists in principle — direct `POST <admin-pod-ip>:3000/workflows`
  from another pod on an NP-non-enforcing CNI reaches the app port and hits the Phase 3
  relay unauthenticated. Closing this needs either a CNI-aware NetworkPolicy or binding
  `dws-admin`'s Express listener to `127.0.0.1` (loses cluster-internal read-Service
  reachability, so requires a different Service topology). Out of scope for this
  chart-only phase.
