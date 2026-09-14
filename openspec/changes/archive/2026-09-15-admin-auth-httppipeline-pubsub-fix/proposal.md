## Why

Turning on `auth.enabled=true` together with `apiGateway.enabled=true` currently breaks
`dws.events`. The `dws-admin` bearer middleware is wired into `spec.appHttpPipeline`, which Dapr
applies to **every** inbound sidecar→app call — including daprd's own internal
`GET /dapr/subscribe` discovery call. That call carries no browser bearer token, so the sidecar
401s it, no subscription is ever registered, and `dws.events` messages never reach `dws-admin`'s
read model or SSE feed. This is the top open item on the `dws-console` auth roadmap
(`docs/roadmaps/dws-auth.md` §4) and the one blocker keeping `auth.enabled=true` from being safe
with real pub/sub traffic. Dapr's built-in `middleware.http.bearer` has no path-exemption field,
so there is no values-only fix — the handler has to move pipelines.

The design is settled in `docs/roadmaps/dws-auth.md` "Decision (2026-09-13)"; this change
implements it, it does not re-open it.

## What Changes

- **Move the `dws-admin` bearer handler from `spec.appHttpPipeline` to `spec.httpPipeline`**
  (`charts/dws/templates/admin/auth-configuration.yaml`). `httpPipeline` gates the sidecar's own
  Dapr HTTP API — the surface APISIX and the browser actually call for reads/writes/SSE — while
  leaving daprd's internal app-channel calls (`/dapr/subscribe`, pub/sub delivery) un-gated, which
  is what unblocks `dws.events`.
- **Leave the `dws-controller` bearer handler on `spec.appHttpPipeline`**
  (`charts/dws/templates/controller/auth-configuration.yaml`). This asymmetry is deliberate:
  `dws-admin` reaches the controller by Dapr service invocation, which arrives over internal gRPC
  and never traverses `httpPipeline`; moving the controller handler would silently delete the
  Phase 2 gate — no render error, `helm lint` green, only a no-token request would reveal it.
- **Rewrite the header comments** of both `auth-configuration.yaml` files (and the admin
  `auth-component.yaml`) so they state why they now diverge, instead of claiming to mirror each
  other — otherwise a future tidy-up reopens the hole.
- **Keep the Phase 3 controller relay forwarding `Authorization` verbatim**
  (`dws-admin/src/controller-relay/controller-relay.service.ts`). Under `httpPipeline` the admin's
  own outbound sidecar-invoke leg is now bearer-checked too, so the forwarded token is
  load-bearing, not vestigial.
- **Add a `helm template` regression test** (`charts/dws/tests/auth-pipeline-placement-test.sh`,
  wired into `.github/workflows/helm.yml`) pinning admin→`httpPipeline` and
  controller→`appHttpPipeline` so the divergence cannot be "tidied up" later without a red CI.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `helm-admin-auth-middleware`: the admin bearer `Configuration` is wired into
  `spec.httpPipeline` rather than `spec.appHttpPipeline`, so Dapr's internal
  subscription-discovery and pub/sub delivery calls stay reachable without a browser bearer token
  while the browser-facing Dapr HTTP API surface remains gated.

## Impact

- Affected component: `charts/dws` (`templates/admin/auth-configuration.yaml`,
  `templates/admin/auth-component.yaml`, `templates/controller/auth-configuration.yaml`, new
  `tests/auth-pipeline-placement-test.sh`) and `.github/workflows/helm.yml`.
- `dws-admin` and `dws-controller` application code is unchanged; the Phase 3 relay already
  forwards the bearer token verbatim, which is exactly what the new placement requires.
- `dws-controller`'s event publishing is unaffected: `events/DaprClientProducer.java` builds the
  default gRPC `DaprClient`, and HTTP middleware does not gate gRPC (checked, not assumed).
- **Operational note (daprd reloads Configuration only at startup):** a bare `helm upgrade`
  leaves the sidecars in a half-state — the admin and controller pods must be restarted for the
  new pipeline placement to take effect.
- **Accepted risk, closed later by Phase 10, not here:** once the gate leaves the admin app
  channel, `POST /dapr/events/dws` on `dws-admin`'s app port is reachable unauthenticated by any
  pod-network peer — same class as the §4 pod-IP bypass. Dapr API token + App API token (roadmap
  Phase 10) close it.
- **Constraint this placement introduces:** every call the admin app makes to its own sidecar now
  needs a valid JWT. Today only the relay does, and it forwards the user's. A future
  publish/state/binding call added to `dws-admin` will 401 unless it carries a token.
- Existing `auth.enabled=false` releases are topologically unchanged — the whole `Configuration`
  is still gated behind `auth.enabled`.
