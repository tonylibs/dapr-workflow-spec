## Why

`dws-controller` currently accepts every inbound request unauthenticated. `dws-console` Phase 1
established an OIDC/PKCE browser client that carries a JWT, but nothing on the write path verifies
it: the `dws-controller` Kubernetes Service exposes the app container port directly, and the
existing pod annotations (`dapr.io/enabled`, `dapr.io/app-id`) do not gate inbound HTTP because
there is no `dapr.io/app-port`, no `dapr.io/config`, and no Dapr `Configuration`/middleware wired
to the inbound pipeline. Any client (or `dws-admin` relay in Phase 3) with cluster network reach
can `POST` a definition and drive the controller. Phase 2 closes that gap using Dapr-native
`middleware.http.bearer` — no JWT verification code enters `dws-controller` itself.

Ground rules from [`docs/roadmaps/dws-auth.md`](../../../docs/roadmaps/dws-auth.md) apply: JWT and
role verification live only in Dapr middleware, never in application code; role/RBAC enforcement
stays optional until a stable Dex role claim is proven.

## What Changes

- **BREAKING** (cluster-callers only, not DSL): `dws-controller`'s Kubernetes Service SHALL front
  the Dapr sidecar's HTTP port (`3500`), not the controller's app port (`8080`). The app port
  becomes pod-local — direct Service traffic cannot bypass middleware. Callers now invoke via
  Dapr service invocation (`/v1.0/invoke/<app-id>/method/...`), which is the calling shape Phase
  3's `dws-admin` relay is designed against.
- Add `dapr.io/app-port: "8080"` and `dapr.io/config: <configuration-name>` annotations to the
  controller Deployment pod template so the sidecar knows which port to proxy and which
  `Configuration` (and therefore which HTTP pipeline) to apply. Existing `dapr.io/enabled` and
  `dapr.io/app-id` annotations are preserved.
- Add a Dapr `Component` of type `middleware.http.bearer`, scoped to the controller app-id,
  configured with `issuer`, `audience`, and optional `jwksURL` metadata sourced from chart values.
- Add a Dapr `Configuration` whose `spec.appHttpPipeline.handlers` reference the bearer Component,
  applied to inbound HTTP calls to the controller sidecar.
- Extend `charts/dws` `values.yaml` with an `auth:` block: `auth.enabled`, `auth.issuer`,
  `auth.audience`, `auth.jwksURL`, `auth.dex.enabled` (mode toggle that auto-derives issuer /
  audience / JWKS URL from the in-chart Dex when true). Off by default so existing releases stay
  functional until an operator opts in.
- Sidecar liveness/readiness probes for `daprd` and health-probe paths for the controller SHALL
  remain reachable. The bearer middleware is applied through `Configuration.spec.appHttpPipeline`
  and does not intercept the sidecar's own `/v1.0/healthz`; controller health endpoints
  (`/q/health/live`, `/q/health/ready`) stay on the pod-local app port for kubelet probes.
- Role/RBAC enforcement stays unimplemented pending a proven Dex claim (see roadmap §4). No Rego
  Component, no group-based scoping added in this phase.
- Update the existing `helm-controller-deployment` spec (currently forbids `dapr.io/app-port` on
  the controller) and its chart tests to match the new behavior.
- Add a new `helm-controller-auth-middleware` spec capturing the bearer `Component`,
  `Configuration`, Service front-port change, and values contract.
- Update `docs/roadmaps/dws-auth.md` Phase 2 row to reflect actual current state, and mark Phase 2
  complete only after live authorization and bypass evidence.

## Capabilities

### New Capabilities

- `helm-controller-auth-middleware`: `charts/dws` renders a Dapr `middleware.http.bearer`
  `Component`, a `Configuration` wiring it to the controller sidecar's inbound HTTP pipeline, and
  fronts the controller Kubernetes Service on the sidecar port so no traffic reaches the app port
  without traversing the middleware. Covers the `auth.*` values contract and the
  bundled-Dex-vs-external-OIDC selection.

### Modified Capabilities

- `helm-controller-deployment`: the "Controller pod carries Dapr sidecar annotations
  unconditionally" requirement is amended — `dapr.io/app-port: "8080"` and (when
  `auth.enabled=true`) `dapr.io/config: <configuration-name>` SHALL now be present on the
  controller pod template. The prior explicit prohibition on `dapr.io/app-port` is REMOVED. Service
  behavior is also amended: when `auth.enabled=true` the Service targets the sidecar's HTTP port,
  not the controller container's `http` port.

## Impact

- **Affected components**: `charts/dws` only (templates, values, tests). `dws-controller` source
  is not changed — no JWT verification code enters the controller.
- **Cluster contract change**: callers that today `POST` directly to the controller Service now
  need to invoke via Dapr service invocation (`/v1.0/invoke/<app-id>/method/...`) and carry an
  `Authorization: Bearer <jwt>` header. This is the same shape Phase 3's `dws-admin` relay will
  use, so it does not add a second contract.
- **Backward compatibility**: `auth.enabled` defaults to `false`; existing releases render the same
  Service→app-port topology and no middleware. Turning it on is a deliberate opt-in that requires
  configuring an issuer/audience (or enabling in-chart Dex).
- **Non-goals**: Phase 3's `dws-admin` write relay, Phase 4's `admin-gateway` nginx, Phase 6's
  read guarding, Phase 7's user management, and Phase 8's bundled-IdP interoperability are all
  explicitly out of scope. No role/RBAC enforcement in this phase.
- **Verification dependency**: acceptance requires a reachable Dapr + Dex environment (roadmap
  §Current progress explicitly notes the localhost port-forward from Phase 1 cannot serve
  cluster-side workloads). Local Helm lint/template gates pass without a cluster; live
  authorization and application-port bypass tests do not.
