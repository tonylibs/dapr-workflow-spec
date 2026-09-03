## Context

Roadmap §2b supersedes the archived nginx-based Phase 4. That implementation proved the negative
JWT matrix and CORS behavior, but exposed a blocking topology defect: Nest owns the admin API on
3000 while `@dbc-tech/nest-dapr` owns pub/sub callbacks on 3001, and the sole sidecar can deliver
to only one app-port. Pointing Dapr at 3001 preserves ingestion but makes API service invocation
404; pointing it at 3000 makes API calls work but silently stops ingestion.

The chart also has two unrelated public paths: `templates/console/ingress.yaml` exposes only the
console and `templates/admin-gateway/` exposes only the write relay. Reads and SSE remain directly
reachable on the admin Service. The desired state is one Gateway API origin, backed by APISIX,
where all admin traffic enters through Dapr's bearer-gated service-invocation path.

Constraints:

- APISIX must be an optional `charts/dws` dependency, not an assumed platform install.
- Operators must also be able to use an externally managed APISIX controller.
- Dapr remains the JWT verifier; APISIX only routes.
- The console's default admin prefix is `/dws-admin`.
- Programmatic Dapr subscriptions are ordinary `GET /dapr/subscribe` discovery plus `POST`
  delivery routes; a dedicated SDK-owned server is not required.
- Native browser `EventSource` cannot carry `Authorization`, so gating reads implies a console
  streaming-client change.
- Live SSE through Dapr invoke is not yet proven and is deferred without blocking implementation.

## Goals / Non-Goals

**Goals:**

- Consolidate `dws-admin` API, SSE, and Dapr subscription callbacks on Nest port 3000.
- Front console and the full admin API with one Gateway API Gateway and APISIX controller.
- Force every public admin request through the admin sidecar's bearer middleware.
- Support bundled APISIX and external APISIX without changing the DWS-owned route contract.
- Make all console admin reads, writes, and SSE requests bearer-authenticated without persisting
  access tokens.
- Remove the nginx admin gateway, console Ingress template, and same-origin-redundant admin CORS.
- Fail upgrades using the old Ingress value with actionable migration instructions.
- Keep Helm defaults opt-in and pass chart gates in bundled and external-controller modes.

**Non-Goals:**

- JWT verification or authorization policy inside APISIX or Nest.
- A new cookie/session architecture or storing OIDC tokens outside the OIDC client.
- New admin resource paths or response contracts.
- Multi-replica SSE fan-out; `dws-admin` remains single-replica.
- Changing controller/orchestrator/step-service behavior, DSL semantics, or content-addressed
  workflow versioning.
- Completing the live SSE-over-Dapr acceptance run in this change.
- Editing any artifact under `openspec/changes/archive/`.

## Decisions

### D1 — Merge Dapr pub/sub endpoints into Nest

- **Choice:** remove `@dbc-tech/nest-dapr` and its port-3001 server. Add a thin Nest controller
  that returns the configured subscription from `GET /dapr/subscribe` and accepts Dapr transport
  CloudEvents at one `POST` route. The controller unwraps transport `data` and calls the existing
  `DwsEventsSubscriber` domain service. Nest and the sidecar both use port 3000.
- **Rationale:** Dapr's programmatic subscription contract is HTTP, and the existing ingestion
  pipeline already owns validation, idempotency, database writes, and SSE publication. Keeping
  those concerns in a service while making HTTP a thin adapter preserves behavior and removes the
  conflicting runtime.
- **Alternatives:** splitting into two app-ids adds a Deployment/sidecar and breaks in-process SSE
  fan-out; an in-pod proxy retains two servers and adds another runtime; streaming subscriptions
  are not the documented Node SDK baseline used here.

The callback distinguishes outcomes deliberately: handled and intentionally discarded malformed
events return 2xx/`SUCCESS`; unexpected processing/database errors propagate as non-2xx so Dapr
retries. It preserves exactly one subscription for the configured `(pubsubName, topic)` pair.

### D2 — Separate route enablement from APISIX dependency ownership

- **Choice:** `apiGateway.enabled` renders DWS gateway resources; `apisix.enabled` controls the
  optional APISIX dependency. The parent chart pins the APISIX chart, updates `Chart.lock`, and
  vendors its archive just like Dapr/Dex/Postgres/Redis.
- **Rationale:** disabling a bundled dependency must not prevent operators from targeting an
  external controller. A single switch would conflate controller ownership with route ownership.
- **Alternatives:** assuming APISIX pre-exists violates the roadmap; tying all Gateway resources
  directly to `apisix.enabled` makes external mode impossible.

Bundled defaults enable the APISIX ingress-controller child and its Gateway API support. DWS owns
the GatewayClass/Gateway/HTTPRoutes and the APISIX `GatewayProxy` binding so release/namespace and
admin-Service references are deterministic under the parent chart. Secrets use the pinned
subchart's supported Secret-reference contract rather than duplicating credentials where
possible.

### D3 — Preflight only externally managed CRDs

- **Choice:** if `apiGateway.enabled=true` and `apisix.enabled=false`, render-time preflight checks
  require Gateway API v1 and the APISIX API version/resource needed by the GatewayProxy binding.
  The failure tells the operator either to enable the dependency or install compatible CRDs and
  APISIX externally. Bundled mode skips this lookup because Helm computes capabilities before
  dependency CRDs are installed.
- **Rationale:** this exactly follows the existing Dapr external-install pattern while respecting
  Helm's CRD ordering.
- **Alternatives:** unconditional preflight produces false failures on first bundled install;
  omitting preflight leaves unrecognized resources and an unreconciled Gateway after install.

### D4 — Route `/dws-admin` through Dapr, then fall back to console

- **Choice:** one Gateway listener has two attached HTTPRoutes. The admin route matches
  `PathPrefix /dws-admin`, rewrites it to
  `/v1.0/invoke/<admin-app-id>/method/`, and targets the admin Service's sidecar port 3500. The
  console route matches `/` and targets the console Service. Hostname and TLS certificate
  references are values-driven.
- **Rationale:** it matches the console image's existing `/dws-admin` default and makes browser
  requests same-origin. Gateway path specificity selects admin before console. Rewriting at the
  route is required because a Service fronting daprd expects Dapr's invoke URL, not `/workflows`.
- **Alternatives:** separate origins retain CORS and split policy; routing directly to port 3000
  bypasses bearer middleware; making APISIX verify JWT duplicates the Dapr contract.

The implementation must prove that the pinned APISIX controller supports the chosen Gateway API
v1 rule-level URLRewrite form. Query strings, request bodies, Authorization/content headers,
response status/body/headers, and streamed bodies remain transparent.

### D5 — The secured admin Service exposes the sidecar only

- **Choice:** with the API gateway enabled, Helm requires `auth.enabled`, `admin.enabled`, and
  `console.enabled`. The admin Service exposes a single logical HTTP port targeting daprd 3500;
  port 3000 remains a container/probe port but is not Service-addressable. The pod annotation is
  `dapr.io/app-port: "3000"`.
- **Rationale:** a parallel Service port to the app recreates the authentication bypass this
  design removes. Pod-local health probes do not require a Service port.
- **Alternatives:** retaining both ports preserves accidental direct-read compatibility at the
  cost of violating the security boundary.

When the new gateway is disabled, current default/off behavior is retained for one migration
window. This keeps a default Helm upgrade from unexpectedly cutting off existing internal users;
the strict sidecar-only topology begins when the operator opts into the replacement front door.

### D6 — Authenticate all console admin transports at the OIDC boundary

- **Choice:** centralize token acquisition in the admin client. Every JSON request awaits the
  current in-memory OIDC access token and attaches it. SSE uses a fetch-based streaming transport
  with the same header, named-event parsing, AbortController cancellation, retry/backoff, and
  reconnect callbacks. Query hooks do not start until auth is ready and signed in.
- **Rationale:** reads are now behind the same bearer gate as writes, and native EventSource has no
  request-header API. Centralization prevents missed endpoints and keeps tokens out of component
  state and cache keys.
- **Alternatives:** query-string tokens leak through URLs/logs; cookies introduce a new session and
  CSRF design; anonymous reads contradict the requirement; APISIX-injected credentials erase
  end-user identity.

### D7 — Remove CORS and both legacy front doors atomically

- **Choice:** once new route render/tests pass, delete `templates/admin-gateway/` and
  `templates/console/ingress.yaml`, remove `adminGateway.*`, and remove the Nest CORS module/env
  wiring. Retain the old `console.ingress.enabled` input only as a deprecation trap that fails with
  migration guidance.
- **Rationale:** parallel paths create bypasses and ambiguity. Same-origin routing eliminates the
  CORS requirement entirely. A hard migration error is safer than silently ignoring an active
  Ingress configuration.
- **Alternatives:** rendering both during transition permits security bypass; silently dropping
  the Ingress can make an upgrade unreachable; translating arbitrary Ingress annotations is not
  portable.

## Risks / Trade-offs

- **[Risk] APISIX support for the exact URLRewrite/GatewayProxy combination differs by pinned
  controller version.** → Mitigation: pin one verified chart/controller set, render its dependency
  artifacts in CI, and add an early controller compatibility test before deleting legacy paths.
- **[Risk] APISIX chart 2.x introduces its own etcd and controller dependencies and materially
  increases the default footprint when enabled.** → Mitigation: keep `apisix.enabled=false` by
  default, expose upstream values, document production external-etcd guidance, and test dependency
  lock/vendor consistency.
- **[Risk] GatewayClass is cluster-scoped while releases are namespaced.** → Mitigation: derive a
  release-and-namespace-qualified class name, label it for Helm ownership, and document that external mode may
  reference an operator-owned class instead of creating a conflicting one.
- **[Risk] Removing the direct app Service port can surprise in-cluster consumers.** → Mitigation:
  scope the change to gateway opt-in, document the Dapr invoke replacement, and reject ambiguous
  legacy values at render time.
- **[Risk] A plain callback route may accidentally acknowledge failures.** → Mitigation: request
  tests distinguish malformed/ignored messages from unexpected database errors and assert the
  Dapr `SUCCESS`/retry contract.
- **[Risk] SSE buffering or timeout behavior may differ across APISIX and Dapr.** → Mitigation:
  preserve streaming headers and cancellation in code, avoid response-buffering filters, record a
  live verification recipe, and do not claim live proof in this change.
- **[Trade-off] All admin screens now require login.** → Accepted because reads and writes are
  intentionally one protected surface; explicit signed-out UI replaces the previous anonymous
  fallback.
- **[Trade-off] The migration cannot translate arbitrary Ingress annotations.** → Accepted because
  Gateway API is the new portable contract; the error message maps host/TLS and calls out fields
  that require operator review.

## Migration Plan

1. Land the one-listener admin change first within the same release: Nest serves subscription
   discovery/delivery, tests prove ingestion, and chart app-port becomes 3000.
2. Add authenticated console JSON/SSE clients and auth-aware query startup while the existing
   path still renders; unit tests prove headers and token non-persistence.
3. Add the pinned optional APISIX dependency, lock/vendor output, values, preflight, helpers,
   GatewayClass/Gateway/GatewayProxy/HTTPRoutes, and chart assertions.
4. Switch secured admin Service routing to sidecar-only in gateway mode and prove all admin paths
   resolve through the rewrite.
5. Remove nginx admin-gateway, console Ingress, and admin CORS. Add the legacy Ingress validation
   error, NOTES/values migration instructions, and roadmap update.
6. Run component gates and Helm lint/template matrices for default, bundled, and external APISIX.

Upgrade for an existing Ingress install:

- install Gateway API/APISIX externally or set `apisix.enabled=true`;
- set `apiGateway.enabled=true` and configure the old host/TLS Secret on the Gateway listener;
- ensure `auth.enabled=true`, `admin.enabled=true`, and `console.enabled=true`;
- update Dex/OIDC redirect URI to the shared public console origin;
- remove `console.ingress.enabled=true` and obsolete class/annotation values;
- verify reads/writes through `/dws-admin` before removing any separately managed external route.

Rollback requires restoring the previous chart/application images together because the Service
topology, console auth behavior, and admin callback listener are coordinated. Disable the new
Gateway/APISIX, restore the old Ingress/admin-gateway values with the prior chart, and confirm
event ingestion before exposing the old paths. The immutable workflow definitions and read-model
schema are unchanged, so no data rollback is required.

## Open Questions

No blocking design questions remain. Implementation must record, without changing the chosen
architecture, the exact APISIX 2.16.0 child-resource names/Secret keys used by the GatewayProxy
binding and the maintained fetch-SSE implementation selected for the console. Live
SSE-over-Dapr/APISIX acceptance remains intentionally deferred and must be tracked in `verify.md`.
