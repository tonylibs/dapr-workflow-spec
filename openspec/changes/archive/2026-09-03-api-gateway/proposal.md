## Why

The current auth front door is split and incomplete: a bundled nginx proxies only writes,
console reads still reach `dws-admin` directly, and the console has a separate plain Ingress.
Worse, a valid sidecar-routed request cannot reach the Nest API because `dws-admin` runs Nest on
3000 while `@dbc-tech/nest-dapr` owns Dapr callbacks on 3001, and one sidecar accepts only one
app-port. The redesigned Phase 4 must first consolidate that listener contract, then replace both
front doors with one same-origin Gateway API entry point whose admin backend always traverses
Dapr bearer validation.

## What Changes

**dws-admin listener and ingestion contract**
- From: Nest API/SSE/write relay on port 3000 plus `@dbc-tech/nest-dapr` callbacks on port 3001.
- To: one Nest listener on port 3000, with ordinary programmatic-subscription discovery and
  callback routes delegating to the existing idempotent event processor.
- Impact: removes the second server/package and makes one Dapr app-port valid for ingestion and
  service invocation.

**Cluster front door**
- From: console-only `Ingress` plus a bundled nginx admin write gateway; reads bypass the Dapr
  bearer gate.
- To: one Gateway API `Gateway`, implemented by APISIX, with HTTPRoutes for `/dws-admin` and the
  console root. The admin route rewrites to the admin sidecar invoke path and covers reads, writes,
  and SSE.
- Impact: breaking migration for installs using `console.ingress.enabled=true`; old templates are
  removed and the legacy value fails with migration guidance rather than being ignored.

**APISIX ownership**
- Add a pinned optional `apisix` dependency (`condition: apisix.enabled`) and support an external
  APISIX controller when the dependency is disabled. External mode receives a Gateway API/APISIX
  CRD preflight check analogous to the existing Dapr check.

**Authenticated console traffic**
- From: only definition submission carries a bearer token; JSON reads are anonymous and SSE uses
  native `EventSource`.
- To: every admin request acquires the in-memory OIDC token; reads and writes attach it, and a
  fetch-based SSE client attaches it without persisting it.
- Impact: admin queries and streams wait for sign-in and treat 401 as a session/auth outcome.

The implementation removes `templates/admin-gateway/`, `templates/console/ingress.yaml`, and the
now-redundant `dws-admin` CORS path after the Gateway route is covered by local chart tests. Live
SSE traversal through Dapr invoke is recorded for later verification and does not block this
change.

Non-goals: APISIX plugins for JWT verification, new DWS API routes, changes to workflow DSL or
runtime interpretation, multi-replica SSE fan-out, or editing the archived Phase 4 change.

## Capabilities

### New Capabilities

- `helm-apisix-dependency`: optional pinned APISIX chart dependency, bundled/external controller
  modes, dependency artifacts, and CRD preflight behavior.
- `helm-api-gateway`: DWS-owned GatewayClass/Gateway/HTTPRoute topology, APISIX binding, route
  rewrite, validation, and legacy Ingress migration contract.
- `console-admin-authentication`: in-memory OIDC token acquisition for every admin request,
  authenticated fetch-based SSE, and signed-out/session-expired behavior.

### Modified Capabilities

- `admin-event-ingestion`: replace `@DaprPubSub`/port 3001 with Nest-hosted programmatic
  subscription discovery and delivery on port 3000.
- `helm-admin-deployment`: use app-port 3000 and expose only the sidecar-backed Service port in
  secured gateway mode.
- `helm-admin-auth-middleware`: expand the bearer gate contract from the write relay to every
  browser-facing admin read, write, and stream route.
- `helm-admin-gateway`: remove the superseded nginx Deployment, Service, ConfigMap, CORS contract,
  and values surface.
- `console-read-wiring`: require bearer-authenticated JSON reads and auth-aware query startup.
- `console-live-instance-updates`: replace anonymous native EventSource connections with
  bearer-authenticated fetch streaming while preserving reconnect/resync behavior.

## Impact

- `dws-admin`: event/Dapr/config/bootstrap modules, dependencies and lockfile, tests, README,
  `.env.example`, and container port declaration.
- `dws-console`: centralized admin client, OIDC boundary, query/live hooks and tests, package
  dependency/lockfile if a maintained SSE parser is selected, dev proxy documentation.
- `charts/dws`: `Chart.yaml`, `Chart.lock`, vendored dependency, values/schema tests, preflight,
  helpers, Gateway API/APISIX templates, admin Service/Deployment, NOTES/migration docs, and
  removal of both legacy front-door template trees.
- Compatibility: default gateway/APISIX remains opt-in; enabling the new gateway requires auth,
  console, and admin. Existing workflow definitions, task/resource compilation, controller
  routing, content-addressed versions, and step-service contracts are unchanged.

