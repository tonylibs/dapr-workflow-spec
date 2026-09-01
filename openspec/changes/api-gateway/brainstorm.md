# API gateway redesign — brainstorming decision record

## Background and fixed scope

The auth roadmap's redesigned Phase 4 replaces two existing front doors, not just one:

- `charts/dws/templates/admin-gateway/` (the bundled nginx proxy that only fronts
  `POST /workflows`), and
- `charts/dws/templates/console/ingress.yaml` (the console-only Kubernetes Ingress).

The replacement is one Kubernetes Gateway API front door, implemented by Apache APISIX,
serving both `dws-console` and the complete `dws-admin` HTTP surface on one origin. APISIX is
an optional dependency of `charts/dws`; the chart must also support an externally managed
APISIX controller. Browser traffic to admin routes must enter through `dws-admin`'s Dapr
sidecar so the existing bearer middleware validates reads, writes, and streams alike.

The archived `2026-08-26-dws-console-auth-phase-4` change is historical evidence only. Its
nginx design is superseded and nothing under `openspec/changes/archive/` may be edited.

## Decision chain

### Q1 — How should the single-app-port/dual-listener conflict be resolved?

Three approaches were considered.

1. **Merge Dapr's programmatic subscription endpoints into the Nest listener (chosen).**
   Replace `@dbc-tech/nest-dapr`'s port-3001 server and `@DaprPubSub` decorator with ordinary
   Nest routes on port 3000: `GET /dapr/subscribe` advertises the configured `pubsub` /
   `dws.events` subscription, and a dedicated `POST` callback unwraps Dapr's transport
   CloudEvent and delegates to the existing idempotent event-processing service. Dapr's
   published HTTP contract explicitly treats these as normal application routes and regards
   an empty 2xx response as `SUCCESS`. This keeps one app-id, one sidecar, one app port, one
   process, and one in-process SSE fan-out.
2. **Split API and ingestion into two app-ids.** This gives each listener a sidecar, but adds a
   second Deployment/Service/app-id, duplicates database and rollout concerns, and breaks the
   current guarantee that the process which commits an event immediately publishes it to the
   same process's SSE subscribers. Repairing that would require the cross-replica bus already
   documented as out of scope.
3. **Keep both listeners behind an in-pod multiplexer.** A proxy could dispatch Dapr callbacks
   to 3001 and API traffic to 3000, but it adds another runtime and still retains the unnecessary
   decorator/server abstraction. It solves routing mechanically without simplifying ownership.

The first option is the smallest coherent design. `DwsEventsSubscriber` remains the domain
service that validates, writes, and publishes live events; HTTP discovery and delivery move to
a thin Nest controller. `@dbc-tech/nest-dapr`, `DAPR_APP_PORT`, port 3001, and the dedicated
`src/dapr/` module are removed. The chart sets `dapr.io/app-port: "3000"`.

### Q2 — How are bundled and external APISIX installations separated?

Use two independent switches:

- `apiGateway.enabled` controls DWS-owned `GatewayClass`, `Gateway`, `HTTPRoute`, and the
  APISIX `GatewayProxy` reference/configuration needed to bind the resources to a data plane.
- `apisix.enabled` controls only the pinned `apisix` chart dependency in `Chart.yaml`.

This avoids making "use an external controller" equivalent to "render no routes." Bundled mode
enables the dependency's ingress-controller subchart and configures its APISIX data plane.
External mode renders the same DWS routing resources but requires the operator to provide the
external APISIX/GatewayProxy settings.

When `apiGateway.enabled=true` and `apisix.enabled=false`, a Helm preflight check fails before
workloads are created unless the cluster advertises both Gateway API v1 and the APISIX CRDs the
rendered resources reference. This mirrors the existing Dapr preflight pattern. When APISIX is
bundled, its dependency owns those CRDs; the preflight does not try to discover CRDs during the
same Helm install because Helm capabilities are computed before dependency CRDs are installed.

The dependency is pinned (initial implementation baseline: APISIX chart `2.16.0`, whose chart
contains APISIX `3.17.0` and an ingress-controller dependency), recorded in `Chart.lock`, and
vendored under `charts/dws/charts/`, matching the chart's existing dependency workflow.

### Q3 — What route topology preserves same-origin behavior and the Dapr gate?

The shared `Gateway` has one HTTP listener and an optional hostname/TLS listener configuration.
Two `HTTPRoute`s attach to it:

- an admin route matches `PathPrefix /dws-admin`, rewrites that prefix to
  `/v1.0/invoke/<admin-app-id>/method/`, and targets the admin Service port that front-ports the
  Dapr sidecar's HTTP port 3500;
- a console route matches `PathPrefix /` and targets the console Service.

Gateway API's more-specific path precedence sends `/dws-admin/*` to admin and all other browser
routes to the console. The admin rewrite preserves the remaining path and query string, so
`/dws-admin/workflows?dryRun=true` becomes Dapr's invoke path for `/workflows?dryRun=true`.
All methods, request bodies, `Authorization`, content types, streaming response bodies, and
status codes are passed through. APISIX does not validate JWTs; the receiving Dapr sidecar does.

With `apiGateway.enabled=true`, Helm validation requires `auth.enabled=true`, `admin.enabled=true`,
and `console.enabled=true`. The admin Service then exposes only its sidecar-backed port; it does
not expose port 3000. Probes remain pod-local against the container's named port. With the new
gateway disabled, the chart keeps the existing default/off topology for a controlled migration.

### Q4 — What application changes are implied by gating reads as well as writes?

Current console writes carry a bearer token, but JSON reads do not, and browser `EventSource`
cannot set an `Authorization` header. Leaving that unchanged would make the redesigned read path
consistently return 401. Therefore the gateway change necessarily includes console integration:

- centralize access-token acquisition at the admin-client boundary and attach
  `Authorization: Bearer <token>` to every JSON read and write;
- replace native `EventSource` with a fetch-based SSE client/reader that can attach the same
  header, preserve named-event parsing, cancellation, and reconnect/resync behavior;
- prevent admin queries/streams from starting while the OIDC client is unavailable or signed
  out, and render an explicit sign-in/session-expired state rather than retrying 401s.

The token remains in the OIDC client's in-memory store; it is requested per call and is never
copied into React state, query keys, logs, local storage, or session storage.

`dws-admin` no longer needs browser CORS configuration because browser traffic is same-origin.
Its `CORS_ORIGINS` parsing, global CORS enablement, tests, chart/env wiring, and documentation are
removed. Dapr's `/dapr/subscribe` and event callback remain internal app callbacks and are not
special-cased by CORS.

Live SSE traversal through Dapr service invocation and APISIX remains an explicit later live
verification item. The implementation must preserve streaming semantics and provide unit/request
coverage, but lack of a completed live SSE proof does not block this change.

### Q5 — How does an existing `console.ingress.enabled=true` installation migrate?

The old Ingress template is removed, but its values are retained temporarily as deprecated input
so an upgrade cannot silently discard an active front door. Rendering fails with an actionable
message when `console.ingress.enabled=true`, directing the operator to:

1. set `apiGateway.enabled=true`;
2. choose bundled APISIX (`apisix.enabled=true`) or configure an external APISIX controller;
3. move `console.ingress.host` to the gateway hostname and translate any TLS Secret to the
   Gateway listener certificate reference;
4. remove Ingress-class annotations and class selection, which have no Gateway API equivalent in
   this chart; and
5. verify the console's OIDC redirect URI now matches the shared public origin.

This is a deliberate, visible breaking migration. No old Ingress and new Gateway are rendered in
parallel, and no old nginx admin gateway remains as a fallback bypass.

### Q6 — What proves the design is implementation-ready?

Required local evidence covers both components and both dependency modes:

- `dws-admin`: request-level tests for subscription discovery, transport-envelope unwrapping,
  success/drop/retry behavior, one-listener bootstrap, and unchanged idempotent ingestion; then
  `pnpm lint`, `pnpm test`, and `pnpm build`;
- `dws-console`: token propagation for every JSON call, no token persistence, authenticated SSE
  parsing/reconnect/cancellation, signed-out behavior, then lint/test/typecheck/build gates;
- `charts/dws`: dependency lock/vendor consistency; preflight failure/success cases; values-schema
  coverage; `helm lint` and `helm template` for default, bundled APISIX, and external APISIX
  (`--api-versions` supplying the expected CRDs); exact route/rewrite/backend assertions; and
  negative assertions that no nginx admin-gateway or console Ingress renders;
- OpenSpec strict validation.

The later live matrix must include valid/invalid bearer cases for reads and writes, route
precedence, preserved YAML/JSON bodies and queries, event ingestion on port 3000, disabled-mode
cleanup, and eventually SSE across Gateway -> APISIX -> Dapr invoke -> Nest. The SSE item is
tracked but is not an apply blocker for this change.

## Design carried forward

The change is one coordinated migration: consolidate `dws-admin` on Nest port 3000 first, then
switch its secured Service to the sidecar, add the optional APISIX dependency and shared Gateway
API resources, make console reads/streams authenticated, remove both superseded front doors and
CORS code, document the breaking Ingress migration, and prove all non-live gates in bundled and
external-controller modes.
