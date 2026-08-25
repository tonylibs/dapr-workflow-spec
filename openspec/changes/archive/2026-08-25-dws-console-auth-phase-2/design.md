## Context

The `dws-console` browser client already carries an OIDC/PKCE JWT (Phase 1, `oidc-client` spec).
`dws-controller` today accepts every request unauthenticated: its pod has `dapr.io/enabled` and
`dapr.io/app-id` but no `dapr.io/app-port`, no `dapr.io/config`, no Dapr `Configuration`, and no
`middleware.http.bearer` Component. Its Kubernetes Service exposes the app port directly, so any
in-cluster caller can `POST` a definition to `<controller-fullname>:<port>` and skip authentication
entirely. Roadmap [`docs/roadmaps/dws-auth.md`](../../../docs/roadmaps/dws-auth.md) constrains the
fix to Dapr-native JWT validation — no verification logic in `dws-controller` source.

The current `helm-controller-deployment` spec explicitly forbids `dapr.io/app-port` on the
controller pod. That rule was written when the controller only *published* outbound events; Phase 2
turns the controller into a Dapr *inbound* target, so the prohibition must be lifted and the
inverse — `app-port` MUST be present — asserted.

`charts/dws` already deploys Dex as a conditional dependency (`helm-dex-idp` spec), so an in-chart
OIDC issuer/JWKS is available when `dex.enabled=true`. External OIDC (any spec-compliant provider
with a discovery document) must also be supported for real deployments — see roadmap §4.

## Goals / Non-Goals

**Goals:**

- All inbound HTTP to `dws-controller` in a chart-installed release traverses a Dapr HTTP pipeline
  containing `middleware.http.bearer` when `auth.enabled=true`.
- No JWT verification code or dependency enters `dws-controller` (Java source unchanged).
- The direct application-port bypass (Service → container port `8080`) is closed by front-porting
  the Service on the Dapr sidecar port.
- Helm values support both bundled Dex (`auth.dex.enabled=true`) and any external OIDC provider
  (`auth.issuer` / `auth.audience` / `auth.jwksURL`).
- Health probes and existing chart tests keep working. Local Helm lint/template gates stay green.

**Non-Goals:**

- Phase 3 `dws-admin` write relay, Phase 4 `admin-gateway` nginx, Phase 5 console write UI, Phase 6
  read guarding, Phase 7 user management, Phase 8 bundled-IdP interoperability — none touched.
- Role/RBAC enforcement. Roadmap §4 notes no stable Dex role claim has been proven; this change
  deliberately does not invent one.
- Modifying `dws-controller` Java source. Any auth work landing in Quarkus code is out of scope.
- Changing DSL 1.0 workflow semantics or task-to-resource mapping.
- Cross-component contract changes for step services (`dws-call-http`, `dws-call-openapi`,
  `dws-run`, `dws-orchestrator`). Only the controller-facing inbound contract changes, and only for
  callers that use the Kubernetes Service directly (which today is nothing in production — Phase 3
  will introduce the first real caller, the `dws-admin` relay).

## Decisions

### D1 — Use `middleware.http.bearer`, not custom OPA/Rego middleware

Dapr ships `middleware.http.bearer` upstream; it validates a JWT against issuer + audience with
JWKS discovery. Alternatives considered: `middleware.http.opa` (would introduce a Rego dependency
and a role model we do not yet have per roadmap §4); custom Quarkus auth extension (rejected by
ground rule "JWT verification is Dapr-only, never hand-rolled in app code"). Bearer middleware is
the minimum viable primitive for Phase 2 and is what Phase 3–4 will layer role checks on top of
when a claim shape is proven.

### D2 — Front the Kubernetes Service on the sidecar port, keep app port pod-local

Two alternatives:

- **(a) Service → sidecar port 3500.** All external cluster traffic must invoke via Dapr service
  invocation (`/v1.0/invoke/<app-id>/method/...`). Middleware cannot be bypassed by naming the
  Service directly.
- **(b) NetworkPolicy blocking direct pod:8080 traffic.** Relies on cluster-side NetworkPolicy
  support and an enforced CNI. Fragile across cluster distributions.

Choose (a). It works on any Kubernetes distribution, matches how Phase 3's `dws-admin` relay will
call the controller (via its own local sidecar's invoke path), and does not require a second
runtime dependency. The app port stays as a `containerPort` (kubelet probes still hit it directly
via `podIP:8080`, which does not go through the Service).

### D3 — Preserve pre-Phase-2 topology when `auth.enabled=false`

Existing releases that upgrade this chart without opting in should render the same Service→
container-port topology and no Component/Configuration objects. Alternative — always render the
sidecar-fronted topology and rely on the middleware being "identity" when disabled — was rejected
because it silently changes the calling contract for existing operators and creates a subtle
"looks authenticated but isn't" state. Explicit opt-in via `auth.enabled=true` is louder and safer.

### D4 — Dex-mode derives issuer/audience/jwksURL from the in-chart Dex

Operators enabling `dex.enabled=true` already declare the issuer implicitly. Requiring them to
also fill `auth.issuer`/`auth.audience`/`auth.jwksURL` duplicates configuration and invites
drift (typo → discovery fails → requests silently start being rejected). `auth.dex.enabled=true`
picks up the derived values from the same helper templates the Dex subchart uses. External-OIDC
mode (`auth.dex.enabled=false`, explicit values) remains the primary contract for real
deployments — Dex mode is a dev/quickstart convenience.

### D5 — Component `scopes` restrict middleware to the controller app-id

Dapr Components without `scopes` are visible to every app-id in the namespace. Scoping the auth
Component to `[<controller-fullname>]` prevents future sidecars (Phase 3 `dws-admin`, orchestrator
pods) from accidentally picking up the controller's middleware.

### D6 — Fail render when `auth.enabled=true` and no issuer/audience resolved

Rendering a bearer Component with empty `issuer`/`audience` metadata would deploy silently and
reject every request at runtime with cryptic 401s. Better: `helm template` fails at render time
with a message pointing at the missing values. Implemented via `required` in the template.

### D7 — Keep kubelet probes on the app port (`8080`), not through the sidecar

The kubelet dials `podIP:containerPort` — bypassing the Service. Probe traffic never traverses
the sidecar, so it is not gated by the middleware. This is the standard Dapr pattern and matches
`dws-admin/deployment.yaml`'s existing probe topology.

## Risks / Trade-offs

- **[Risk] Breaking cluster callers that today hit `<controller-service>:80` directly** →
  Mitigation: `auth.enabled=false` default preserves the old topology. Operators must opt in.
  Communicated via values docs and roadmap update. The only expected in-cluster caller in Phase
  3+ is `dws-admin`, which is designed against Dapr service invocation from the start.
- **[Risk] Bearer middleware version drift across Dapr releases** → Mitigation: chart already
  pins Dapr via the `helm-dapr-dependency` capability; the `middleware.http.bearer` type is
  present in Dapr ≥1.11 and remains stable. Chart tests assert the rendered Component's `type`
  and `metadata` shape.
- **[Trade-off] No role/RBAC enforcement yet** → Roadmap §4 explicitly defers this; adding it
  without a proven claim risks either a Rego dependency for a claim that will not stabilize or a
  wrong lookup shape that later needs migration.
- **[Risk] Live-verification blocker — Dex on `localhost:5556` from Phase 1 cannot serve
  in-cluster workloads** → Mitigation: this change lands chart + specs + local gates; the live
  authorization and bypass tests are recorded as a separate acceptance gate that must run in a
  reachable Dapr+Dex environment before flipping the roadmap row to ✅. This is called out in
  `openspec/changes/dws-console-auth-phase-2/verify.md` (created during apply).
- **[Trade-off] Rendering two Dapr objects (Component + Configuration) per controller instance**
  → Small template surface, matches the pattern already used for other Components
  (`definitions-component.yaml`, `pubsub-component.yaml`, `actor-statestore-component.yaml`).

## Migration Plan

1. Land chart + specs + roadmap update behind `auth.enabled=false` default. Existing installs are
   unaffected on upgrade.
2. Operator enables Dex (already supported): `--set dex.enabled=true`.
3. Operator enables auth: `--set auth.enabled=true --set auth.dex.enabled=true` (bundled Dex) or
   `--set auth.enabled=true --set auth.issuer=... --set auth.audience=... [--set auth.jwksURL=...]`
   (external OIDC).
4. `helm upgrade` triggers a Dapr sidecar restart on the controller pod; the sidecar re-reads its
   `Configuration` on start-up. No controller image change is required.
5. Rollback: `--set auth.enabled=false` restores the pre-Phase-2 Service→app-port topology.

## Open Questions

- Which named port on the sidecar corresponds to daprd's HTTP entry in this chart's Dapr install?
  The Dapr sidecar container is injected by the operator, not by our templates, so the Service
  cannot reference a named port on our pod template. Two workable approaches: (a) target the
  numeric port `3500` (Dapr default), (b) rely on the `dapr.io/sidecar-listen-address-port`
  annotation to make the port configurable. Choose (a) for Phase 2 and revisit if any operator
  needs a non-default sidecar port. Track in `tasks.md`.
- Should we add a Dapr `AccessControlPolicy` on top of the bearer middleware to restrict *which
  app-ids* may invoke the controller? Deferred — Phase 3's `dws-admin` will be the only caller,
  and access control is an orthogonal Dapr feature we can bolt on without changing the
  middleware.
