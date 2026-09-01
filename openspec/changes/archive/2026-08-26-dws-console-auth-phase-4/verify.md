# dws-console-auth-phase-4 — verify

Change: `dws-console-auth-phase-4` (auth roadmap Phase 4).

## Status

Chart-side implementation and local gates are green. The Docker Desktop live matrix ran on
2026-09-01 and passed preflight, negative auth, unchanged reads, cleanup, and disabled-gateway
checks. The valid-token chain remains **blocked** by `dws-admin`'s dual-listener contract:
the sidecar can target either Nest's relay on 3000 or the Dapr subscription server on 3001,
but it has only one `app-port`. Phase 4 must resolve that multiplexing problem before completion.

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
- nginx's static `proxy_pass` preserves the incoming `dryRun` (and any other) query parameter
  verbatim. Keeping the upstream static is important: appending `$is_args$args` made nginx
  require a runtime DNS resolver and returned 502 in the first live run.

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

## Live acceptance (Docker Desktop, 2026-09-01)

Cluster: `docker-desktop`, Kubernetes v1.34.1, Dapr 1.18.2. Namespace/release:
`dws-phase4`. Dex used its local password connector only as a test fixture; valid and
wrong-audience tokens were minted by separate public clients, and the wrong-issuer token came
from the independently configured Phase 2 Dex release.

| Check | Actual | Result |
|---|---:|---|
| Allowed-origin OPTIONS | 204; explicit origin, `POST, OPTIONS`, requested headers, credentials | PASS |
| Disallowed-origin OPTIONS | 403 | PASS |
| No authorization | 401 | PASS |
| Malformed token | 401 | PASS |
| Tampered signature (middle signature character changed) | 401 | PASS |
| Wrong audience | 401 | PASS |
| Wrong issuer | 401 | PASS |
| Direct `GET /workflows` | 200 | PASS |
| Direct `GET /instances` | 200 | PASS |
| Direct `GET /instances/events` | 200, `text/event-stream` | PASS |
| Uninstall, then gateway-disabled reinstall | zero gateway objects; core Deployments Ready | PASS |
| Valid JWT with chart app-port 3001 | 404 `Cannot POST /workflows` | **BLOCKED** |

Two nginx defects surfaced and are fixed in the working tree with a containerized regression
test (`charts/dws/tests/admin-gateway-cors-test.sh`):

1. The upstream admin listener adds `Access-Control-Allow-Origin: *`; nginx also added the
   explicit credentialed origin, producing two browser-invalid values. The gateway now hides
   upstream CORS origin/credentials headers before adding its own.
2. `$is_args$args` made `proxy_pass` dynamic, so nginx returned 502 with `no resolver defined`.
   The static upstream form resolves through Kubernetes DNS and natively preserves the query.
   A live `dryRun=true` request under the temporary port-3000 fixture returned 200 and created
   zero workflow resources.

### Blocking dual-listener finding

`dws-admin` runs two HTTP servers:

- Nest on port 3000 owns `POST /workflows` and all read/SSE routes.
- `@dbc-tech/nest-dapr` on port 3001 owns Dapr subscription callbacks.

The chart sets `dapr.io/app-port: "3001"`, so a valid gateway invocation passes bearer
middleware and is delivered to the subscription server, which correctly reports that it has no
`POST /workflows`. Temporarily patching the annotation to 3000 makes the full chain return 200,
proving gateway → admin sidecar → relay → controller sidecar → controller, but that patch would
redirect pub/sub callbacks away from their server and is therefore not a valid implementation.
Phase 4 needs an explicit single-port multiplexer or an equivalent architecture decision.

## Residuals (not blocking this change)

- **DAPR_CONTROLLER_APP_ID is now wired.** The live non-default release rendered
  `DAPR_CONTROLLER_APP_ID=dws-phase4-controller`; this earlier residual is closed.
- **Pod-IP:8080 direct-app-port bypass on `dws-controller`.** Carried over from Phase 2's
  `verify.md`. Unrelated to Phase 4 (the admin sidecar's app port is `3001`, unchanged),
  but the equivalent hole exists in principle — direct `POST <admin-pod-ip>:3000/workflows`
  from another pod on an NP-non-enforcing CNI reaches the app port and hits the Phase 3
  relay unauthenticated. Closing this needs either a CNI-aware NetworkPolicy or binding
  `dws-admin`'s Express listener to `127.0.0.1` (loses cluster-internal read-Service
  reachability, so requires a different Service topology). Out of scope for this
  chart-only phase.
