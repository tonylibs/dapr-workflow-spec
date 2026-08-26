# helm-admin-gateway

## Purpose

Chart-bundled nginx reverse proxy that lets `dws-console` reach `dws-admin`'s Phase 3 write
relay from a browser: it terminates the CORS preflight for the console origin locally and
forwards the actual request onto `dws-admin`'s Dapr sidecar invoke path, so the sidecar's
bearer middleware (introduced by `helm-admin-auth-middleware`) sees the same token the browser
sent. Ships inside `charts/dws`; not an assumed cluster `Ingress`.

## Requirements

### Requirement: Admin gateway Deployment/Service/ConfigMap render when enabled

When `.Values.adminGateway.enabled` is `true`, `charts/dws` SHALL render exactly one nginx
`Deployment`, one `Service`, and one `ConfigMap` under a distinct component label
(`app.kubernetes.io/component: admin-gateway`) in the release namespace. The Deployment SHALL
mount the ConfigMap as the nginx site configuration and the Service SHALL expose the nginx
listen port. When `.Values.adminGateway.enabled` is `false` (the default), none of those
objects SHALL be rendered.

Owning component: `charts/dws` (`templates/admin-gateway/deployment.yaml`,
`templates/admin-gateway/service.yaml`, `templates/admin-gateway/configmap.yaml`).

#### Scenario: Disabled by default renders nothing gateway-shaped

- **WHEN** `helm template charts/dws` is run with default values
- **THEN** no object with `app.kubernetes.io/component: admin-gateway` appears in the output

#### Scenario: Enabled renders the trio

- **WHEN** `helm template charts/dws --set adminGateway.enabled=true` is run (with any other
  values required by the gateway's own required-values contract satisfied)
- **THEN** the output contains exactly one Deployment, one Service, and one ConfigMap all
  labeled `app.kubernetes.io/component: admin-gateway`
- **AND** the Deployment's pod template mounts the rendered ConfigMap as the nginx site config
- **AND** the Service's port targets the nginx container's listen port

### Requirement: nginx answers CORS preflight for the console origin without proxying

The rendered nginx configuration SHALL match `OPTIONS` requests to the write-relay path and
return `204 No Content` with `Access-Control-Allow-Origin` echoing the request's `Origin`
header when that origin is in `.Values.adminGateway.corsOrigins`, plus
`Access-Control-Allow-Methods` covering at minimum `POST, OPTIONS`,
`Access-Control-Allow-Headers` covering at minimum `Authorization, Content-Type`, and a
non-zero `Access-Control-Max-Age`. The preflight response SHALL NOT be proxied to any
upstream. Requests from origins not in the list SHALL be rejected with `403` (never proxied).

Owning component: `charts/dws` (`templates/admin-gateway/configmap.yaml`).

#### Scenario: Preflight from an allowed origin

- **WHEN** an `OPTIONS` request arrives at the gateway with `Origin:
  https://console.example.com` and that origin is in `adminGateway.corsOrigins`
- **THEN** the gateway responds `204` with `Access-Control-Allow-Origin:
  https://console.example.com`, `Access-Control-Allow-Methods` containing `POST` and
  `OPTIONS`, `Access-Control-Allow-Headers` containing `Authorization` and `Content-Type`,
  and a numeric `Access-Control-Max-Age`
- **AND** no request reaches `dws-admin`'s Service or its sidecar

#### Scenario: Preflight from an origin not in the allow-list

- **WHEN** an `OPTIONS` request arrives with an origin not in `adminGateway.corsOrigins`
- **THEN** the gateway responds `403`
- **AND** no request is proxied upstream

### Requirement: nginx proxies real requests to dws-admin's sidecar invoke path

For non-`OPTIONS` requests to the write-relay path, the rendered nginx configuration SHALL
`proxy_pass` to `http://<dws-admin fullname>.<release namespace>.svc.cluster.local:3500/v1.0/invoke/<dws-admin fullname>/method/workflows`
(the Dapr sidecar's HTTP port), preserving the request method, request body, the
`Authorization` header, the `Content-Type` header, and the `dryRun` query parameter if
present. The gateway SHALL NOT decode or otherwise inspect the `Authorization` header — token
verification is the sidecar's job (per `helm-admin-auth-middleware`).

Owning component: `charts/dws` (`templates/admin-gateway/configmap.yaml`).

#### Scenario: Real POST is proxied to sidecar invoke path

- **WHEN** a `POST /workflows` request arrives at the gateway from an allowed origin, with
  `Authorization: Bearer <token>` and a YAML or JSON body
- **THEN** the gateway forwards the request to
  `http://<dws-admin fullname>.<ns>.svc:3500/v1.0/invoke/<dws-admin fullname>/method/workflows`
  with the same method, body, `Authorization`, and `Content-Type` headers
- **AND** the response's status, body, and `Content-Type` are returned to the caller
  unchanged

#### Scenario: dryRun query parameter is preserved

- **WHEN** the incoming URL is `POST /workflows?dryRun=true`
- **THEN** the upstream URL carries `?dryRun=true` verbatim

### Requirement: Gateway requires explicit CORS allow-list; render fails on empty

When `.Values.adminGateway.enabled` is `true`, `.Values.adminGateway.corsOrigins` SHALL be a
non-empty list of exact origin strings. If it is empty or unset, `helm template` / `helm install`
SHALL fail with an explicit error message naming the missing value. Wildcard origins (`"*"`)
SHALL be rejected the same way — the gateway is a bearer-token-carrying write path and
credentialed cross-origin requests cannot combine with a wildcard origin.

Owning component: `charts/dws` (`templates/admin-gateway/*.yaml`).

#### Scenario: Missing corsOrigins fails render

- **WHEN** `helm template charts/dws --set adminGateway.enabled=true` is run without
  `adminGateway.corsOrigins`
- **THEN** rendering fails with an error naming `adminGateway.corsOrigins`

#### Scenario: Wildcard origin is rejected

- **WHEN** `adminGateway.corsOrigins` contains `"*"`
- **THEN** rendering fails with an error explaining that a wildcard cannot combine with a
  credentialed cross-origin write path
