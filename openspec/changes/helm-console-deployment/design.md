## Context

See `proposal.md` — Why/What Changes. Phases 2–4 of the roadmap (`helm-controller-deployment`,
`helm-admin-deployment`, `helm-dapr-dependency`, all archived) established the pattern this change
extends: a `<component>.enabled` toggle, a `dws.<component>.fullname`/
`dws.<component>.selectorLabels` helper pair in `_helpers.tpl`, and a `<component>:` block in
`values.yaml`. This is the chart's first Ingress resource — no existing template to copy
structurally.

Constraints from the container contract (`dws-console/Dockerfile`, `dws-console/server.js`), not
decisions to make, just facts to build against:
- Listens on `$PORT` (default 3000), `$HOST` (default `0.0.0.0`).
- `/healthz` is a plain liveness/readiness endpoint that answers even if SSR fails — not a route,
  handled before the SSR handler.
- Runs as the image's built-in non-root `node` user; no `securityContext` needed beyond what
  `templates/admin/deployment.yaml` already omits.
- `VITE_DWS_ADMIN_URL` is a Vite build-time arg baked into the client bundle, defaulting to the
  same-origin path `/dws-admin`. It cannot be set as a Deployment env var — doing so would have no
  effect on the already-built client bundle and would misleadingly suggest it does something.

Confirmed by reading `dws-admin/src/main.ts` and `app.module.ts`: the Nest app calls no
`app.setGlobalPrefix(...)`, and no controller in `app.module.ts`'s imports declares a route-level
prefix — every admin route (`/workflows`, `/instances`, `/health`, `/docs`) is mounted at the
application root. So the console's default same-origin `/dws-admin` path only works if something
in front of admin strips that prefix before the request reaches it; admin itself does not expect
or strip it.

## Goals / Non-Goals

**Goals:**
- Real `console/` templates (Deployment, Service, Ingress) following the existing helper/gating
  pattern exactly.
- A default Ingress topology that makes the console's own build-time default
  (`VITE_DWS_ADMIN_URL=/dws-admin`) actually work out of the box: one host, `/` to console,
  `/dws-admin/*` to admin with the prefix stripped.
- CI coverage proving both `console.enabled` states render correctly, plus (if feasible) a real
  reachability check through the rendered Ingress.

**Non-Goals:**
- `dws-console` Phase 4 (definition submission) or Phase 5 (auth) — the image is read-only today,
  so this Ingress has no write path to secure.
- TLS certificate issuance (cert-manager, ACME) — `console.ingress.tls` accepts operator-supplied
  Secret references only, the same shape Kubernetes' native `Ingress.spec.tls` uses.
  Split-origin console deployments (custom-built image with a different
  `VITE_DWS_ADMIN_URL` plus admin `CORS_ORIGINS`) — supported by the container contract already,
  but this chart's default Ingress only wires the single-origin case; split-origin operators
  build their own image and skip `console.ingress` (or set `console.ingress.enabled=false` and
  front it themselves).
- General-purpose Ingress controller installation — the chart does not install an Ingress
  controller (unlike Dapr, which is a proper chart dependency); `console.ingress` assumes one
  already exists in the cluster, same as any application chart's Ingress.

## Decisions

**`console.enabled` defaults to `false`.**
The roadmap's stated end goal is "one `helm install`" bootstrapping the whole control plane, which
argues for `true` (controller/admin/postgresql/dapr are all `true` by default today). Weighed
against that: every other default-`true` component in this chart is either the platform itself
(controller, admin) or a control-plane dependency the chart can *also install itself*
conditionally (`postgresql.enabled`, `dapr.enabled`, each a real subchart dependency with its own
preflight check). An Ingress controller is different in kind — it's a cluster-level prerequisite
this chart makes no attempt to install or verify (no preflight check, unlike Dapr's
`Capabilities.APIVersions.Has` gate), and a large fraction of real clusters don't have one, or use
one the operator must point `console.ingress.className`/`annotations` at explicitly. Defaulting
`console.enabled=true` would mean a plain `helm install` with zero `--set` flags renders a Service
with no traffic path (Ingress defaults to disabled too, per the spec's "console disabled implies no
Ingress" and "no e2e ingress test yet" — see proposal), which is a worse first-run experience than
"nothing rendered, and the values file explains how to turn it on" — so `false` until there's a
real integration test proving the Ingress path end-to-end (this change adds one; a follow-up change
can flip the default once that's had a release cycle to prove out). Alternative considered: `true`
matching the "one helm install" narrative — rejected because it optimizes for a document's framing
over the concrete first-run outcome.

**Path-prefix stripping via `nginx.ingress.kubernetes.io/rewrite-target`, not a second Service or app-level prefix.**
`dws-admin` doesn't expect a `/dws-admin` prefix (confirmed above), and changing that is out of
scope (no `dws-admin` code changes in this proposal). Rewriting the path at the Ingress layer is
the standard fix and needs no new admin capability. Implementation: the admin rule's path is
`{{ .Values.console.ingress.adminPath }}(/|$)(.*)` with `pathType: ImplementationSpecific`, paired
with the annotation `nginx.ingress.kubernetes.io/rewrite-target: /$2` — the conventional
ingress-nginx two-capture-group pattern (`$1` is the optional trailing slash, `$2` is everything
after it), so `/dws-admin/workflows` forwards to admin as `/workflows` and bare `/dws-admin`
forwards as `/`. This annotation is ingress-nginx-specific; `console.ingress.annotations` is a free
map so an operator using a different controller (Traefik, GKE, ALB) supplies that controller's own
rewrite annotation instead — documented in `values.yaml` comments, not hardcoded into the template
beyond a sensible ingress-nginx default. Alternative considered: two separate Ingress hosts (avoids
rewrite entirely) — rejected because it breaks the container image's actual default
(`VITE_DWS_ADMIN_URL=/dws-admin`, a *path*, not a subdomain), forcing every default install into
the split-origin case the image contract treats as the exception.

**Console Service is not Ingress-routable without the chart's Ingress — no NodePort/LoadBalancer default.**
`console.service` stays `ClusterIP` (the field isn't even exposed as a type override in this
phase), matching `admin.service`. An operator who wants console reachable without the chart's
Ingress can already do so via `kubectl port-forward` or their own Ingress/Service — no new chart
surface needed for that.

**CI: extend `verify`'s template matrix; add integration reachability as a new job, not folded into the existing `integration` job.**
The existing `integration` job asserts `controller.enabled=false` (no published `1.0` controller
tag) and doesn't install an Ingress controller. Console's integration leg needs a different kind
cluster shape — `extraPortMappings` for 80/443 at cluster-create time, plus an ingress-nginx
controller install — which the existing job's cluster (created via the plain `helm/kind-action`
default config) doesn't have and other legs don't need. A dedicated `integration-console` job
(`needs: verify`, independent of `integration`) keeps the existing job's runtime and failure
surface unchanged and makes a console-specific failure distinguishable at a glance, matching the
precedent set by `integration-dapr-preinstalled`. It installs the chart with `controller.enabled=
false` (same reason as `integration`: no published stable tag), `admin.enabled=true` (needed as
the Ingress's second backend), `console.enabled=true`, `console.ingress.enabled=true`, waits for
the ingress-nginx admission webhook and both Deployments' rollouts, then `curl`s the mapped host
port for `/` (expect the console's HTML shell) and `console.ingress.adminPath + "/health"` (expect
admin's health JSON) — proving the rewrite actually reaches admin, not just that the console
serves. Full pub/sub-style e2e (the console consuming live SSE data) is out of scope for this
phase, per the proposal.

## Risks / Trade-offs

- **[Risk]** `console.enabled=false` by default means Phase 6 "ships" a capability most fresh
  installs won't see, undercutting the roadmap's "one helm install" framing until someone flips the
  toggle. → Mitigation: this is a values default, not a capability limitation — `--set
  console.enabled=true --set console.ingress.enabled=true` gets the full stack in one command
  today; the roadmap already tracks the default flip as a documented open item once the new
  integration job has run clean on `main` for a release cycle.
- **[Risk]** The default `nginx.ingress.kubernetes.io/rewrite-target` annotation is a no-op (or an
  error) on non-nginx controllers, so a first-time user on GKE/ALB/Traefik who only sets
  `console.ingress.host` gets a broken `/dws-admin` path with no obvious signal why. → Mitigation:
  `values.yaml` comments next to `console.ingress.annotations` state the default targets
  ingress-nginx explicitly and point at the rewrite needed for other controllers; a
  controller-detection preflight is out of scope (the chart has no reliable, generic way to
  identify "which Ingress controller is this annotation set for").
- **[Risk]** `console.ingress.adminPath`'s regex-based path in the Ingress spec is
  `pathType: ImplementationSpecific`, which some controllers (validated against `Prefix`/`Exact`
  only) may reject. → Mitigation: this is the same trade-off the ingress-nginx ecosystem itself
  accepts for prefix-strip rewrites; documented as an nginx-first default, same as the rewrite
  annotation above.
- **[Trade-off]** Adding a second kind cluster (`integration-console`) with different networking
  setup grows CI runtime and surface area for flakiness (ingress-nginx admission webhook readiness
  is a known source of race conditions in kind). → Accepted: mirrors the existing precedent
  (`integration-dapr-preinstalled` already runs a second, differently-configured cluster for the
  same reason — isolating a config dimension that doesn't fit the primary job's shape) and Phase
  6's scope explicitly calls for "at minimum reachability through the Ingress."
