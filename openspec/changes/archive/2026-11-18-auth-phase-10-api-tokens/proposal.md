## Why

With `auth.enabled=true`, the only thing standing between a pod-network peer and the control plane
is the Dapr bearer middleware, and the 2026-09-13 pipeline move left two holes it cannot close.
`dws-admin`'s app port (`:3000`) is reachable at `<pod-ip>:3000` without going near the sidecar, so
`POST /dapr/events/dws` lets any peer inject events into the read model unauthenticated (accepted
risk recorded in `docs/roadmaps/dws-auth.md` "Decision (2026-09-13)"). And each sidecar's own HTTP
API accepts any caller that can reach it, with only the bearer JWT to stop them. This is auth
roadmap **Phase 10**: Dapr's two machine-auth tokens, `dapr-api-token` on the way into a sidecar
and the app API token on the way from a sidecar into its app. Both are unblocked now that Phase 4
(APISIX) and the `dws-admin` `httpPipeline` move (2026-09-15) are done.

## What Changes

All of it renders and runs only when `auth.enabled=true`; with auth off the chart and apps behave
exactly as today.

- **Dapr API token** on both control-plane sidecars (`dws-admin`, `dws-controller`): each sidecar's
  HTTP API accepts only calls that carry a valid `dapr-api-token` header, in addition to (not
  instead of) the existing `Authorization` bearer check. A missing or wrong token gets 401.
- **APISIX injects the admin Dapr API token** on the `/dws-admin` route, the only route that
  reaches a sidecar. The token never appears in a manifest in cleartext.
- **`dws-admin` forwards the Dapr API token** on its own calls to its sidecar (the controller
  relay), since those now need it too.
- **App API token for `dws-admin`**: a global check on the app port rejects every request that does
  not carry the token Dapr attaches, so only `dws-admin`'s own sidecar can reach it. This closes the
  `pod-IP:3000` bypass, including unauthenticated `POST /dapr/events/dws`. The kubelet-probed
  `GET /health` stays open.
- **Chart-managed token Secrets**: the chart generates each token as a Secret (stable across
  `helm upgrade`) or accepts a user-supplied Secret per token. Tokens are never printed — not in
  `NOTES.txt`, not in test output, not in the live-verification logs.
- **`dws-controller` app-token check: investigated and reported, not implemented here.** The
  finding is that it does need one, and urgently — see `design.md` D7. It is carved into a
  follow-up change so this one stays reviewable.
- Update the Phase 10 row, §4 open items, and the 2026-09-13 decision in
  `docs/roadmaps/dws-auth.md`, and update the live-verification scripts that deliberately hit the
  app port or sidecar without a token.

**Non-goals:** Istio / service mesh (roadmap Phase 11 — this phase complements it, it does not
replace it); orchestrator and step-service sidecars (data plane, created by `dws-controller`, not
this chart); token rotation automation; replacing the bearer JWT as user identity; the
`dws-controller` app-port check (follow-up).

## Capabilities

### New Capabilities

- `helm-dapr-api-tokens`: how `charts/dws` provisions the Dapr API token and app API token
  Secrets (generated or user-supplied, never printed), wires them into the sidecars and app
  containers, and has APISIX inject the Dapr API token on the admin route.
- `admin-app-api-token`: how `dws-admin` authenticates its callers on the app port and
  authenticates itself to its sidecar when `auth.enabled=true`.

### Modified Capabilities

None. Existing requirements in `helm-admin-auth-middleware`, `helm-controller-auth-middleware`
and `helm-api-gateway` (bearer placement, route shape) are unchanged; the token checks are
additive and every requirement there still holds.

## Impact

- **Components:** `charts/dws` (new `templates/auth/` token Secrets; edits to admin/controller
  Deployments, the admin HTTPRoute, `values.yaml`, `_helpers.tpl`, `NOTES.txt`, the render tests);
  `dws-admin` (new app-token middleware, relay change, config field, tests). Each keeps its own
  build gate: `dws-admin` via `pnpm lint && pnpm test && pnpm build`, the chart via the full chart
  gate in the root `CLAUDE.md`. `dws-controller` application code is **not** touched.
- **Behavior change for existing `auth.enabled=true` releases:** any caller that talks to a
  sidecar or to `dws-admin:3000` without the token now gets 401. In-repo that is
  `scripts/verify-gateway-sse-path.sh`, `scripts/verify-console-ingress-migration.sh` and the
  `controller-auth-negative` helm test; all are updated here. Out-of-repo scripts must add the
  header.
- **Operational:** daprd reads the token env at startup, so existing pods must be restarted after
  `helm upgrade`, as with the 2026-09-13 pipeline move. Rotating a token means rotating the Secret
  and restarting the sidecars **and** APISIX.
- **Cross-package contract:** adds a new chart ↔ `dws-admin` env contract (`DAPR_API_TOKEN`,
  `APP_API_TOKEN`). Like the orchestrator env contract in the root `CLAUDE.md`, a mismatch is a
  silent runtime failure, not a compile error, so `design.md` D6 pins it with a test.
- **Residual risk, stated plainly:** `dws-controller:8080` stays reachable from pod-network peers
  until the follow-up lands (its sidecar API is gated by this change; its app port is not).
