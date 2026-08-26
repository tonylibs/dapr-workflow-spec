## Context

See `proposal.md` for motivation. Phase 2 already put a bearer-middleware pattern in
`charts/dws/templates/controller/` (`auth-component.yaml`, `auth-configuration.yaml`), routed
the controller Service through the Dapr sidecar port (`3500`), and defined the
`_helpers.tpl` blocks that resolve `issuer`/`audience`/`jwksURL` from either explicit values or
derived-from-Dex mode. Phase 3 added a `POST /workflows` write-relay in `dws-admin`
(`src/controller-relay/`) that forwards through `dws-admin`'s own local sidecar. The pieces
this phase must add are the CORS-terminating ingress in front of `dws-admin`'s sidecar and
the sidecar-side bearer gate; no application-code change on either side.

Constraints from the repo shape the approach: `charts/dws` uses Bitnami-style values +
`_helpers.tpl` conventions; the chart already exposes `auth.*` values (Phase 2); `dws-admin`
already has `dapr.io/enabled` + `dapr.io/app-id` + `dapr.io/app-port` annotations; the
existing `dws-admin` Service must keep exposing port `3000` so today's read-path callers are
undisturbed (Phase 6 is deliberately deferred).

## Goals / Non-Goals

**Goals:**
- One code path in the chart, mirroring Phase 2 exactly, that turns on bearer gating for
  `dws-admin`'s sidecar the moment `.Values.auth.enabled=true` — no new values shape, no new
  helpers beyond an `dws.admin.*` mirror of `dws.controller.*`.
- A minimal, chart-bundled nginx that terminates the browser preflight for the console
  origin and reverse-proxies real requests to `dws-admin`'s sidecar invoke path.
- Full off-by-default: `adminGateway.enabled=false` by default, so `helm upgrade` is a
  topological no-op unless the operator opts in.
- The live acceptance matrix from `proposal.md` (no-auth / valid / malformed / tampered-sig /
  wrong-aud / wrong-iss / wrong-role) reproduces exactly, hitting the sidecar first, before
  the `dws-admin` container ever observes the request.

**Non-Goals:**
- Any change to `dws-admin`'s TypeScript. No JWT libraries, no session cookies, no CORS
  handling in Nest (that already exists for reads and stays as-is; the write path's CORS is
  the gateway's concern).
- Guarding of the existing read routes (`GET /workflows`, `GET /instances`, SSE) — those
  keep the direct Service-on-`3000` path unchanged; Phase 6 owns moving them.
- Role/Rego middleware on the admin sidecar. Phase 2 flagged the same open item; if it lands
  it lands on both sidecars at once, in a separate change.
- Closing the pod-IP:8080 direct-app-port bypass on `dws-controller` (still tracked from
  Phase 2 verify).

## Decisions

- **Reuse the Phase 2 values contract exactly.** The bearer Component + Configuration for
  `dws-admin` reuse `.Values.auth.enabled`, `.Values.auth.issuer|audience|jwksURL`, and the
  `auth.dex.enabled` derivation. Rationale: a Phase 2 valid token minted for the console must
  already be accepted by the controller after the relay; if the admin sidecar used a
  different issuer/audience shape the token would fail one of the two hops. Alternative
  considered — a separate `.Values.admin.auth.*` block — rejected because it splits the
  values surface, doubles the risk of an operator mis-pairing them, and buys nothing (the
  scoping is already done at the Component's `scopes` field per sidecar).
- **New helpers `dws.admin.auth.componentName` / `dws.admin.auth.configurationName`** in
  `_helpers.tpl`, mirroring the existing `dws.auth.componentName` /
  `dws.auth.configurationName` (which are already controller-scoped despite the plain
  names). Rationale: the plain names are already reserved for controller — renaming them
  would ripple through Phase 2 unnecessarily. Alternative — reuse the plain names —
  rejected: two Components with the same `metadata.name` in one namespace do not co-exist
  cleanly and the plain names are Phase 2's history.
- **Chart-bundled nginx over a cluster `Ingress`.** Rationale: consistency with the rest of
  `charts/dws` (nothing else assumes an external ingress controller); makes the
  preflight-vs-proxy split live in this chart's ConfigMap rather than in per-cluster ingress
  annotations that vary by nginx/traefik/gce/aws-alb.
- **`proxy_pass` target is the fully qualified in-cluster DNS of `dws-admin`'s Service on
  port `3500`.** `http://<admin fullname>.<ns>.svc.cluster.local:3500/v1.0/invoke/<admin fullname>/method/workflows`.
  Rationale: forces the traffic through the Service's sidecar-front-port, which is what makes
  the bearer middleware actually run. Alternative — proxy to `<admin>:3000/workflows`
  directly (skipping Dapr) — rejected: it silently bypasses the gate this phase exists to
  install.
- **Preflight is answered inside nginx, never proxied.** Rationale: fewer sidecar
  round-trips per browser click; keeps the `Access-Control-*` response headers under
  chart-values control (`adminGateway.corsOrigins`) rather than under Dapr-middleware
  control (Dapr does not synthesise a CORS response for `OPTIONS`). Alternative — let the
  preflight hit `dws-admin`'s existing CORS module in `src/config/cors.ts` — rejected: that
  module's read-path allow-list is intentionally permissive (`*` acceptable), which cannot
  combine with a credentialed write path; a Nest-side CORS split by route is more code and
  more surface for less clarity than a chart-values allow-list evaluated in nginx.
- **`adminGateway.corsOrigins` is required and must be an explicit exact-origin list;
  wildcards are rejected at render time.** Rationale: browsers reject
  `Access-Control-Allow-Origin: *` on a credentialed request (the write path always carries
  `Authorization`), so a wildcard-only config would appear to work in `helm template` and
  break at runtime. Failing the render is the earlier, louder failure mode.
- **Namespaced `_config` and `_auth` name suffixes reuse the Phase 2 shape** — the admin
  Component is `<admin fullname>-auth`, the Configuration is `<admin fullname>-config`.
  Rationale: symmetric with `helm-controller-auth-middleware`; easy to reason about at
  `kubectl get components,configurations -n <ns>`.

## Risks / Trade-offs

- The gateway becomes a second CORS surface next to `dws-admin`'s existing
  `src/config/cors.ts` (which stays wide-open for read routes). **Mitigation**: proposal
  notes it, Phase 6's follow-up captures the eventual trim; until then, they gate different
  hostnames so no runtime collision is possible.
- Depending on the sidecar port for the gate means the admin Service now serves two ports
  when auth is on, and only one when auth is off — an operator flipping `auth.enabled` toggles
  a Service shape. **Mitigation**: existing port `3000` is unconditional; the toggle only
  *adds* a port. Read-path callers see no change either way.
- `_helpers.tpl` gains two new helper names very close to Phase 2's. Reader-confusion risk if
  someone edits the plain-named ones expecting them to affect admin. **Mitigation**: the
  admin helpers name themselves `dws.admin.auth.*` (fully qualified), and the plain names
  keep their Phase-2-only doc comment.
- The nginx image is a new supply-chain dependency for the chart. **Mitigation**: reuse the
  same registry/tagging convention `charts/dws` already uses for its other images
  (`values.yaml` `adminGateway.image.repository` + `.tag`, defaulted to a specific pinned
  version, not `latest`).
- Live acceptance depends on a Dex-issued JWT with a specific audience. Phase 2's
  `verify.md` already established the exact command; this phase reuses it verbatim, so the
  risk is only that Dex 2.44.0's quirks (Phase 8) resurface. **Mitigation**: the same
  `curl`-with-token matrix runs against the gateway URL instead of the Service URL — the
  Dex flow is identical.

## Migration Plan

- Ship in one release with `adminGateway.enabled=false` default. Operators opt in per
  environment.
- Rollback: `helm upgrade` with `adminGateway.enabled=false` (or the previous release) drops
  all Phase 4 objects; the admin Service reverts to its Phase-3 shape (`3000` only); the
  `dapr.io/config` annotation disappears; the sidecar stops enforcing the bearer gate. Read
  routes are unaffected in every direction.
- Ordering with Phase 5 (console write UI wiring): Phase 5 lands after this, targeting the
  new gateway origin. Rolling back Phase 4 while Phase 5 is live breaks the console write
  flow, but reads keep working — so the rollback plan is "roll back Phase 5 first".

## Open Questions

- Whether the gateway should also expose `/healthz` or `/readyz` for its own probes, or
  reuse nginx's status stub. Design assumes a small `stub_status`-backed
  `/adminGateway/health` on the same listen port. Deferrable — does not change the specs, the
  approach, or the task breakdown.
