## Why

Phase 3 (`dws-console-auth-phase-3`) put a stateless write-relay route at `POST /workflows`
on `dws-admin` that forwards through the pod's own Dapr sidecar to the Phase 2 bearer-gated
`dws-controller`. But there is no ingress into that route yet: the browser cannot deliver a
cross-origin `POST` with an `Authorization` header without a CORS preflight, and it cannot
reach `dws-admin`'s Dapr sidecar port (`3500`) at all today because the Kubernetes Service in
front of `dws-admin` only exposes the app port (`3000`) — nothing sidecar-bound. As a result
the write path terminates without a way to be called end-to-end, and the whole roadmap stays
stuck one phase short of the console UI in Phase 5.

## What Changes

- Add a new chart-bundled **admin gateway** — an nginx `Deployment`, `Service`, `ConfigMap`
  under `charts/dws/templates/admin-gateway/` (a brand-new directory) — that answers browser
  CORS preflight for the console origin locally and reverse-proxies the real request to
  `dws-admin`'s Dapr sidecar invoke path
  (`/v1.0/invoke/{{ dws-admin fullname }}/method/workflows`). The gateway ships inside
  `charts/dws`; it is *not* an assumed cluster `Ingress`.
- Extend `charts/dws/templates/admin/service.yaml` to add a second port that front-ports
  `dws-admin`'s Dapr sidecar HTTP port (`3500`) when `auth.enabled=true`. The existing app-port
  (`3000`) stays exposed unchanged so today's read-path callers are not disturbed — this is
  the only place the split matters (read routes are untouched by design; guarding them is
  Phase 6, deliberately deferred).
- Add `charts/dws/templates/admin/auth-component.yaml` (a `middleware.http.bearer` Component,
  scoped to `dws-admin`'s app-id) and `charts/dws/templates/admin/auth-configuration.yaml`
  (a `Configuration` whose `spec.appHttpPipeline.handlers` names the bearer Component), both
  rendered only when `.Values.auth.enabled` is true. Mirror the Phase 2 controller-side
  templates exactly — same JWT metadata contract, same values plumbing, same Dex-derived
  fallback path.
- Add `dapr.io/config: {{ dws-admin fullname }}-config` to
  `charts/dws/templates/admin/deployment.yaml`'s pod template when `auth.enabled=true` so
  the sidecar picks up the new Configuration. `dapr.io/enabled` + `dapr.io/app-id` +
  `dapr.io/app-port` are already there — no other pod-annotation changes needed.

## Capabilities

### New Capabilities
- `helm-admin-gateway`: chart-bundled nginx reverse proxy that terminates browser CORS
  preflight for the console origin and forwards real requests to `dws-admin`'s Dapr sidecar
  invoke path.
- `helm-admin-auth-middleware`: chart rendering of the Dapr bearer middleware Component and
  Configuration that gate the `dws-admin` sidecar's inbound pipeline for the Phase 3 write
  relay route.

### Modified Capabilities
- `helm-admin-deployment`: the `dws-admin` Kubernetes Service SHALL front-port the Dapr
  sidecar's HTTP port (`3500`) when `auth.enabled=true`, and the Deployment's pod template
  SHALL carry `dapr.io/config: <admin fullname>-config` in the same condition.

## Impact

- Affected chart paths: `charts/dws/templates/admin-gateway/` (new directory,
  Deployment/Service/ConfigMap); `charts/dws/templates/admin/service.yaml` (new
  conditional port); `charts/dws/templates/admin/deployment.yaml` (new conditional
  annotation); `charts/dws/templates/admin/auth-component.yaml` (new); and
  `charts/dws/templates/admin/auth-configuration.yaml` (new). `charts/dws/values.yaml`
  and `charts/dws/templates/_helpers.tpl` gain a small `adminGateway.*` block and one or
  two helpers (an admin `fullname`-based Configuration name, mirroring
  `dws.auth.componentName`).
- No changes to `dws-admin`'s TypeScript source, no new dependencies, no JWT parsing code
  in application code — Dapr remains the sole verifier. Existing read routes (`GET
  /workflows`, `GET /instances`, SSE streams) remain reachable via the direct Service path
  on port `3000` unchanged.
- No changes to `dws-controller`, `dws-orchestrator`, `dws-call-http`, `dws-call-openapi`,
  or `dws-run`. No DSL, deployed-resource, or runtime-interpretation behavior changes.
- New chart values: `adminGateway.enabled` (bool), `adminGateway.image.*`,
  `adminGateway.corsOrigins` (list — mirrors `dws-admin`'s existing `corsOrigins` shape),
  `adminGateway.service.*`. Defaults render nothing new when `adminGateway.enabled` is
  false (the field defaults to `false` so upgrade is a topological no-op unless the operator
  opts in).
- Non-goals: no console-side wiring of the write UI to the new gateway origin (Phase 5),
  no move of `dws-admin`'s read routes onto the gateway path (Phase 6), no CMS/versioning
  changes (Phase 9), no NetworkPolicy for the still-open pod-IP:8080 bypass on
  `dws-controller` (tracked separately). No backwards-incompatibility for existing
  releases: without `auth.enabled=true` and `adminGateway.enabled=true`, `helm upgrade`
  is a no-op.
