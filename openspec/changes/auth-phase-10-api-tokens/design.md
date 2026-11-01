## Context

Sources: `docs/roadmaps/dws-auth.md` §2 (Phase 10 row), §2d (SSE proof), §2e (Istio relationship),
§4 (pod-IP bypass), and "Decision (2026-09-13)". See `proposal.md` for why.

Facts this design rests on, each read from the repo rather than assumed:

- `dws-admin` bearer gate is on `spec.httpPipeline` (`templates/admin/configuration.yaml`);
  `dws-controller`'s stays on `spec.appHttpPipeline` and must (service invocation arrives over
  gRPC). Neither moves.
- The admin Service in gateway mode fronts only the sidecar (`targetPort: 3500`). The Nest app on
  `:3000` is container-only, but `<pod-ip>:3000` is reachable from any pod-network peer.
- The only code in `dws-admin` that calls its sidecar is the controller relay
  (`controller-relay.service.ts`); it builds its outbound headers explicitly (authorization,
  content-type), so nothing is forwarded by accident.
- `dws-controller` publishes events through `new DaprClientBuilder().build()` (gRPC). The Dapr API
  token gates gRPC as well as HTTP, and the Java SDK reads `DAPR_API_TOKEN` from the environment.
- Bundled APISIX has **no** active or passive upstream health checks
  (`scripts/verify-gateway-sse-path.sh` asserts `checks == null`), so there is no probe to
  authenticate. The script already fails if that changes.
- Dapr requires the Secret named by `dapr.io/api-token-secret` / `dapr.io/app-token-secret` to hold
  the token under the key `token`.

## Goals / Non-Goals

**Goals:**
- Both machine-auth tokens in place on `dws-admin`, the Dapr API token on `dws-controller`, all
  behind `auth.enabled`.
- Tokens never visible in rendered output meant for display, and not as cleartext in any manifest
  this chart authors, other than the Secrets themselves.
- A clear answer on `dws-controller`.

**Non-Goals:**
- Orchestrator/step sidecars; Istio; rotation tooling; any change to who may do what (roles).
- A new sub-toggle. Considered and rejected: a second switch under `auth.*` doubles the render
  matrix for a feature whose only sane value is "on" whenever auth is on. `auth.enabled=false` is
  the kill switch, as it is for every other auth feature.

## Decisions

### D1. One token per (component, kind); three Secrets in this change

`<release>-admin-dapr-api-token`, `<release>-admin-app-api-token`,
`<release>-controller-dapr-api-token`, each with key `token`. Not one shared token: a leak from
the controller pod (whose `DAPR_API_TOKEN` is readable by anything in its container) must not
unlock the admin sidecar.

Names derive from `.Release.Name`, not `dws.fullname`, deliberately. The bundled APISIX subchart
renders its `extraEnvVars` through `tpl` with its own context, where `.Release.Name` is the parent
release name but `dws.fullname` does not exist (D4). The same naming is used for all three so there
is one rule to document.

Operators can supply their own Secret per token (`auth.apiTokens.<component>.<kind>.existingSecret`);
it must carry key `token`, which is Dapr's contract and not ours to rename.

**Alternative rejected:** one Secret holding all tokens under different keys. Dapr's annotations
cannot select a key.

### D2. Generation: `randAlphaNum` guarded by `lookup`, same pattern as the Dex bootstrap password

48 characters, reused from the existing Secret on upgrade. Honest limits, stated in `NOTES.txt` and
the README:

- Helm stores the rendered manifest in its release Secret, so a **generated** token is visible to
  anyone who can read that release Secret. Supplying `existingSecret` keeps the token out of Helm
  entirely and is the production recommendation. This is the same trade-off the Dex and Postgres
  generated credentials already make.
- `helm template` / `--dry-run` have no `lookup`, so they print fresh random tokens on every run;
  render tests must not echo Secret data.
- "Never prints" means: not in `NOTES.txt`, Helm test output, script logs, or CI logs. `NOTES.txt`
  names the Secrets and gives a `kubectl rollout restart` command; it never gives a command that
  prints the value.

### D3. App-token enforcement in `dws-admin`: Express middleware registered before the body parsers

A function `configureAppTokenGuard(app, token)` called first in `bootstrap` and from the HTTP-level
tests, mirroring how `configureBodyParsers` is structured. Behavior: if `token` is empty, register
nothing. Otherwise 401 any request whose `dapr-api-token` header does not match, except exactly
`GET /health`. Compare SHA-256 digests with `timingSafeEqual` so length is not leaked.

Why middleware, not a Nest `APP_GUARD`: guards run after middleware, so an unauthenticated peer
could make the process buffer and parse up to `MAX_BODY_BYTES` of JSON before being told 401.
The check belongs ahead of parsing. Why a path match, not a `@Public()` decorator, for `/health`:
the exemption has to exist before routing, and an exact-match allowlist that fails closed on every
variant (`/health/`, `/Health`) is easier to audit than a decorator scattered across controllers.

**Rejected alternatives for closing the pod-IP bypass**
- *NetworkPolicy*: CNI-dependent; kindnet and the target dev cluster do not enforce it (§4).
- *Bind the app to `127.0.0.1`* (the §4 preference for the controller): it breaks the kubelet's
  `httpGet` probes, which dial the **pod IP**, not loopback, so the pod would never become Ready.
  The roadmap's claim that this has "no probe interaction" is wrong and is corrected there.
  Workable only with `exec` probes, which needs a shell/curl in each image.
- *Istio*: Phase 11, a different layer (§2e). Complementary.

### D4. APISIX injects the Dapr API token via `RequestHeaderModifier` with an `$env://` reference

The admin HTTPRoute gains a standard Gateway API `RequestHeaderModifier` filter,
`set: dapr-api-token = $env://DWS_ADMIN_DAPR_API_TOKEN`, next to the existing `URLRewrite`. `set`
(not `add`) overwrites any client-supplied header. The console route gets no such filter, so the
admin token is never sent to the console.

The data plane resolves the reference from its own environment:

- Bundled: `apisix.extraEnvVars` sources `DWS_ADMIN_DAPR_API_TOKEN` from the admin Dapr API token
  Secret, and `apisix.nginx.envs` lists it (the subchart's own `apisix.nginx.envs`, which from this
  chart's values is `apisix.apisix.nginx.envs`). **Both** are needed: the first puts it in the pod,
  the second emits the nginx `env` directive that lets worker processes read it. Defaults live in
  `values.yaml`, using `{{ .Release.Name }}` (see D1).
- External: the operator must expose the same variable the same way. Documented in the README; the
  chart cannot render into a workload it does not own.
- A `dws.apiGateway.validate` addition fails render when bundled APISIX is on and the variable is
  missing, not sourced from the right Secret, or absent from `apisix.nginx.envs`. This is what
  makes "supply your own Secret" safe: the guard forces the operator to re-point APISIX instead
  of letting the route inject a stale value that Dapr then 401s.

**Live spike passed (2026-10-31, docker-desktop; tasks 1.1-1.4).** The ingress controller places the merged proxy-rewrite configuration on the APISIX Service referenced by the Route. An echo app behind a token-protected sidecar returned only `match: true, length: 48`, including for a forged client header: APISIX resolves the environment reference and daprd replaces the sidecar API token with the app token. Retain the RequestHeaderModifier approach. See `verify.md` for evidence. The original assumptions were:
(a) the pinned ingress controller (apisix-ingress-controller 2.1.0) merges `URLRewrite` and
`RequestHeaderModifier` into a single `proxy-rewrite` configuration rather than one overwriting the
other; (b) APISIX 3.17 resolves `$env://` inside `proxy-rewrite`'s `headers.set`, i.e. secret
references work for arbitrary plugin string fields and not only fields marked as encrypted.
If (a) fails, move the header to a `PluginConfig` referenced by an `ExtensionRef` filter (same env
mechanism). If (b) fails, there is no way to inject a Secret-backed value from a route, and the
remaining option is a literal in the HTTPRoute. **That fallback is not taken silently**: it puts
the token in a resource readable by anyone who can `get httproutes`, and contradicts the
"no cleartext in manifests" goal, so it needs an explicit decision from the owner before use.

**Alternative rejected:** APISIX global rule / `GatewayProxy.spec.plugins`. It would add the admin
token to every route including the console.

### D5. Explicit token env vars on app containers, not reliance on the injector

The Deployments set `DAPR_API_TOKEN` (and, admin only, `APP_API_TOKEN`) with `secretKeyRef`
themselves. Whether the Dapr sidecar injector also populates these in the app container differs
across versions, and a duplicate or missing variable is a silent failure. Explicit wins; task 5.x
verifies on the live cluster that no duplicate results.

### D6. Chart ↔ `dws-admin` env contract is pinned by a test

Same hazard as the orchestrator env contract in the root `CLAUDE.md`: a name drift is a silent
runtime failure. A chart render test asserts the Deployment sets `DAPR_API_TOKEN` and
`APP_API_TOKEN`, and a `dws-admin` config test asserts the app reads exactly those names, so
renaming either side fails CI. Root `CLAUDE.md` gets an entry for the contract.

### D7. `dws-controller`: it needs the app-token check, and more urgently than `dws-admin` did

**Finding.** Yes. `POST /workflows` and `DELETE /workflows/{name}` on `:8080` create and delete
Deployments, ConfigMaps, Dapr resources and Knative Services in the cluster. That port is reachable
at `<pod-ip>:8080` by any pod-network peer without a token of any kind: Phase 2 closed only the
Service path (§4). The admin event-injection hole is a read-model integrity problem; this is
unauthenticated cluster write access. The bearer middleware and, after this change, the Dapr API
token both protect the sidecar, and neither covers the app port.

**What it would take** (sized, not done here): a Quarkus request filter ahead of resource matching
that compares `dapr-api-token` to `APP_API_TOKEN`, exempting `/q/health/live` and
`/q/health/ready` (kubelet probes); the `dapr.io/app-token-secret` annotation and `APP_API_TOKEN`
env from a fourth Secret, `<release>-controller-app-api-token`; tests mirroring D3. It has the
same shape as the admin work and no new design questions. It also touches `dws-controller`
application code, which has an independent build and release pipeline, and no controller code
changes in this PR keeps review and release scope clean.

**Why deferred, not skipped:** the Dapr API token (this change) already stops the sidecar-side
route to the same write path, and the app-port exposure is unchanged from today, so deferring does
not widen the surface. It is the next change and should land before Phase 11 discussions, not be
folded into them. The roadmap's §4 pod-IP:8080 item is re-pointed to it, and the
`quarkus.http.host=127.0.0.1` suggestion is withdrawn for the probe reason in D3.

## Risks / Trade-offs

- **[Existing `auth.enabled=true` releases break until pods restart]** daprd reads token env at
  startup; a bare `helm upgrade` leaves old sidecars without the requirement and APISIX with a
  header they ignore, while new pods require it. → `NOTES.txt` prints the restart command;
  rollout order in the migration plan below. Same operational shape as the 2026-09-13 move.
- **[`$env://` unsupported in `proxy-rewrite`]** → spike first (D4); explicit-decision fallback.
- **[Generated token readable in the Helm release Secret]** → documented; `existingSecret`
  recommended for production (D2).
- **[Token rotation is manual and touches three things]** the Secret, the sidecars/app pods, and
  APISIX. → documented as a runbook step in the README; automation is a non-goal.
- **[`/health` is unauthenticated]** it returns database liveness only. → accepted; the
  alternative is breaking kubelet probes. It is exact-match and fails closed on variants.
- **[Daprd health or app-health calls to the app might not carry the token]** If an enabled
  app-health-check or a Dapr internal call (`/dapr/config`) arrives without the header, it would
  401 and surface as sidecar errors. → live verification (task 5.x) checks daprd logs for any
  401 from the app channel; `dapr.io/enable-app-health-check` is not enabled today.
- **[Unverified: does daprd strip an inbound `dapr-api-token` before invoking the app, or does
  it overwrite it with the app token]** The design works only if the app sees the app token. →
  checked live (task 5.x); if daprd forwards the Dapr API token instead, D3 needs a second
  accepted value and this document is updated before merge.

## Migration Plan

1. Merge; `helm upgrade` with `auth.enabled=true`. Secrets are created; Deployments roll because
   their pod templates changed, so sidecars and app restart together. APISIX restarts because its
   env changed.
2. Roll order is not critical but expect a brief window where the old APISIX sends no token to new
   admin sidecars (401). Upgrade is not zero-downtime for the admin API; acceptable for a dev/eval
   chart and called out in the README.
3. Rollback: `helm rollback`, or `auth.enabled=false`. The Secrets are chart-owned and are removed
   on uninstall; operator-supplied Secrets are never touched.

## Open Questions

- None that change the specs or task breakdown. The two unverified runtime behaviors above are
  gated by explicit spike and live-verification tasks, with a stated fallback for each.
