# Verification Report

> Produced by the verify step after apply, to confirm the implementation matches specs / design / tasks.
> Any failing check must be corrected in the corresponding artifact, then verify re-run.

**Change**: `api-gateway`
**Verified at**: `2026-09-02` (§1–§4, §6); **§5 live verification added** `2026-09-02` in a
follow-up session against a real `docker-desktop` cluster
**Verifier**: Claude Code (orchestrator session + three scoped subagents: `nestjs-developer`,
`frontend-developer`, `platform-deployment-developer`; §5 verified by a separate Claude Code
session)

---

## 1. Structural Validation

- [x] `api-gateway` change is valid under strict mode

**Note on the command**: task 7.5 specifies `openspec validate --change api-gateway --strict`, but the
local openspec CLI is `1.4.1`, which has no `--change` flag (`error: unknown option '--change'`).
The equivalent correct syntax was used instead:

```text
$ openspec validate api-gateway --type change --strict --no-interactive
Change 'api-gateway' is valid
EXIT=0
```

Repo-wide scan (`openspec validate --all --json`):

```text
total=44 invalid=6
```

All 6 invalid items are **pre-existing main specs, unrelated to this change**.
`git status --porcelain openspec/specs/` is empty, confirming this branch never touched them;
`openspec/specs/helm-admin-auth-middleware/` was last modified by the archived change
`8096364a openspec: sync + archive dws-console-auth-phase-4`.

| Item | Type | Issues | Introduced by this change? |
|---|---|---|---|
| `helm-admin-auth-middleware` | spec | `requirements.2.text` missing SHALL/MUST | No — pre-existing |
| `helm-postgres-deployment` | spec | `requirements.1.scenarios` has no scenario | No — pre-existing |
| `helm-pubsub-integration-test` | spec | `requirements.1.text` missing SHALL/MUST | No — pre-existing |
| `helm-redis-dependency` | spec | `requirements.2.text` missing SHALL/MUST | No — pre-existing |
| `ows-phase3-errors-timeouts` | change | two ADDED requirements missing SHALL/MUST | No — someone else's change |
| `run-step-execution` | spec | `requirements.8.text` missing SHALL/MUST | No — pre-existing |

**Archive-time risk (not blocking this pass)**: this change's delta syncs into
`openspec/specs/helm-admin-auth-middleware/`, and that main spec currently has a requirement #3
missing SHALL/MUST. The delta itself is compliant (`### Requirement: Bearer middleware verifies
tokens before the dws-admin app runs` contains both SHALL and MUST), but if the main spec's existing
defect is still present after archive, `validate --all` will remain invalid. Recommend a separate
spec-lint change to clear all six items before archiving.

---

## 2. Task Completion (`tasks.md`)

- [x] 32/32 complete

```text
$ grep -c '^\s*- \[x\]' openspec/changes/api-gateway/tasks.md   → 32
$ grep -c '^\s*- \[ \]' openspec/changes/api-gateway/tasks.md   → 0
```

Items closed in this pass:

| Task | Executor | Result |
|---|---|---|
| 1.3 / 1.4 | `nestjs-developer` | Found already implemented in the working tree on entry; this pass was verification plus two stale-comment cleanups |
| 1.5 | `nestjs-developer` | dws-admin fully green |
| 2.3 | `frontend-developer` | Added the missing hook-level tests, closing the gap left by the previous pass |
| 7.1–7.4 | `platform-deployment-developer` | All four chart gates run, all passing |

**Process note**: checkbox 7.5 was found already ticked, with a forward reference to `verify.md` §2,
before this file existed. The tick preceded the work. It is accurate as of this report.

---

## 3. Command Evidence

### 3.1 `dws-admin` (tasks 1.3–1.5)

```text
$ pnpm install          → Lockfile is up to date, resolution step is skipped
$ pnpm test -- dapr-subscription.controller.spec.ts event-envelope.spec.ts \
      idempotent-handler.spec.ts configuration.spec.ts
                        → PASS  4 suites / 24 tests
$ pnpm lint             → PASS (no output)
$ pnpm db:migrate       → applied cleanly (local dws-admin-postgres-1)
$ pnpm test             → PASS  12 suites / 77 tests
$ pnpm build            → PASS (nest build)
```

Removal confirmed by grep sweep across `dws-admin/` for `nest-dapr`, `DAPR_APP_PORT`, `3001`,
`DaprModule`, `@dbc-tech`, `corsOrigins`, `corsOptions`, `enableCors`, and `src/dapr`. The only
surviving hits are **deliberate negative assertions** in `src/config/configuration.spec.ts`
(`.not.toHaveProperty` / `.not.toMatch`). Neither `node_modules/@dbc-tech` nor `node_modules/@dapr`
exists. `.github/workflows/dws-admin.yml` and `docker-compose.yml` carry no stale references.

### 3.2 `dws-console` (task 2.3)

```text
$ pnpm lint       → Checked 43 files in 72ms. No fixes applied.
$ pnpm test       → Test Files 7 passed (7) / Tests 79 passed (79)
$ pnpm typecheck  → clean (no output)
$ pnpm build      → client + SSR bundles OK
```

Added `src/lib/admin-hooks.test.tsx` and `src/lib/admin-hooks-live.test.tsx`, using a per-file
`// @vitest-environment jsdom` docblock to contain the jsdom cost. Added devDependencies
`@testing-library/react@^16.3.3` and `jsdom@^30.0.1` (both confirmed absent transitively beforehand).

Auth states covered: signed-out and initializing issue no `fetch`; signed-in issues exactly one
`fetch` carrying `Authorization: Bearer <token>`; a token-renewal failure surfaces as
`AuthenticationError` with zero fetches and zero retries; a `401` surfaces as `ApiError` with exactly
one fetch and no retry; the same gating applies to the `useInstanceLiveUpdates` SSE effect, and
losing sign-in on a rerender closes the open subscription.

**Side effect to watch in code review**: `pnpm add` also applied patch bumps to
`@tanstack/react-query`, `react-query-devtools`, and `react-router-ssr-query`, which are pinned to
`latest`. Not a deliberate upgrade, but lint, test, typecheck, and build all pass on the new versions.

### 3.3 `charts/dws` (tasks 7.1–7.4)

Helm version `v4.2.4`.

**7.1 Default mode**

```text
$ cd charts/dws && helm lint .        → 1 chart(s) linted, 0 chart(s) failed
$ helm template dws .                 → exit 0, 2940 lines
```

Assertions: zero `GatewayClass` / `Gateway` / `HTTPRoute` / `Ingress` kinds; no `# Source:` lines for
`api-gateway/`, `admin-gateway/`, or `console/ingress.yaml`.

**7.2 Bundled mode**

```text
$ helm template dws . --set apiGateway.enabled=true --set apisix.enabled=true \
    --set auth.enabled=true --set auth.issuer=https://idp.example.test \
    --set auth.audience=dws-admin --set admin.enabled=true --set console.enabled=true \
    --set postgresql.enabled=false --set admin.database.url=postgres://...   → exit 0
```

Assertions: exactly 1 GatewayClass, 1 Gateway, 1 GatewayProxy, 2 HTTPRoutes; APISIX
Deployment/StatefulSet/Service sourced from `dws/charts/apisix/...`;
`--show-only templates/admin/service.yaml` shows the admin Service with exactly one port at
`targetPort: 3500` (sidecar-only), no `targetPort: 3000/http`, no `containerPort: 3001`, and no
`DAPR_APP_PORT`; no `admin-gateway` or `console/ingress` sources anywhere in the render.

**7.3 External mode**

```text
$ helm template dws . --set apiGateway.enabled=true --set apisix.enabled=false \
    --set apiGateway.external.gatewayProxyName=existing-gateway-proxy ... \
    --api-versions gateway.networking.k8s.io/v1 \
    --api-versions apisix.apache.org/v1alpha1                                 → exit 0
```

Assertions: 1 GatewayClass, 1 Gateway, 2 HTTPRoutes, 0 GatewayProxy, and no APISIX `# Source:` lines.

Negative preflight cases, all failing as expected with actionable messages:

| Case | Raised from |
|---|---|
| Gateway API CRDs missing entirely | `preflight.yaml:7` ("requires Kubernetes Gateway API v1 CRDs...") |
| Gateway API present, APISIX CRD missing | `preflight.yaml:7` ("requires the APISIX apisix.apache.org/v1alpha1 CRDs...") |
| `apiGateway.enabled=true` with `auth.enabled=false` | `preflight.yaml:8` |
| `auth.enabled=true` with missing issuer / missing audience | `controller/auth-component.yaml` |
| `createGatewayClass=false` without `gatewayClassName` | `preflight.yaml:8` |
| `apisix.enabled=false` without `external.gatewayProxyName` | `gateway.yaml:24` (shares the `dws.apiGateway.gatewayProxyName` helper with `preflight.yaml:8`; identical message, surfaced from whichever template Helm evaluates first alphabetically) |

**One corrected assumption (not a defect)**: a "conflicting bundled + external" case
(`apisix.enabled=true` while also setting `external.gatewayProxyName`) was initially expected to fail.
It does not, and that is **correct**: `values.yaml` documents the field as "Ignored when
apisix.enabled=true", and the negative matrix in `values-schema-test.sh` does not treat it as a
failure either. No change was made.

**7.4 Regression scripts and dependency consistency**

```text
$ bash tests/values-schema-test.sh .      → all checks passed
$ bash tests/api-gateway-render-test.sh . → all checks passed
$ helm dependency list .                  → 5 deps, all ok (incl. apisix 2.16.0)
$ helm dependency build .                 → re-fetched; charts/apisix-2.16.0.tgz sha256 unchanged
     9998326a7f72d41b3475aabfbcab0522b88e62698cb150ac28eea2245832c6c3
```

**One flake, not reproducible (recorded, non-blocking)**: on the very first re-run of
`values-schema-test.sh` immediately after `helm dependency build` had freshly downloaded all five
subcharts, the `createGatewayClass=false` negative case's `2>/tmp/dws-values-schema-test-err`
redirect target momentarily did not exist when read (`grep: No such file or directory`). Isolated
re-testing of that exact `helm template` invocation passed 5/5, and 6 subsequent full script runs
passed cleanly. Attributed to a one-off Windows filesystem / AV-scan timing artifact on
newly-downloaded archives, not a chart logic defect.

**7.4 scope note**: this was a charts-only pass; the `dws-admin` and `dws-console` component CI gates
are covered by §3.1 and §3.2 respectively.

---

## 4. Live Bearer Matrix Recipe (not yet executed; for post-deployment verification)

Below is the runnable recipe for the four scenarios in spec `helm-admin-auth-middleware`.
**Not executed in this pass** — it requires a real cluster, a real IdP, and a deployed APISIX plus
Dapr sidecar, all beyond the scope of local render/unit gates.

Prerequisites: bundled mode deployed, `auth.issuer` / `auth.audience` pointing at a real IdP, and
`GW=https://<gateway-host>`.

```bash
# 0) Obtain a valid token (adjust for the actual IdP)
TOKEN=$(curl -s -X POST "$ISSUER/oauth2/token" \
  -d grant_type=client_credentials -d client_id=... -d client_secret=... \
  -d audience=dws-admin | jq -r .access_token)

# 1) Missing Authorization header -> expect 401, and Nest must not observe the request
curl -si "$GW/instances" | head -1
kubectl logs deploy/dws-admin -c dws-admin --since=30s | grep -c 'GET /instances'   # expect 0

# 2) Valid token on read and write routes -> expect forwarding to Nest on port 3000
curl -si "$GW/instances"  -H "Authorization: Bearer $TOKEN" | head -1   # expect 200
curl -si "$GW/workflows" -X POST -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/yaml' --data-binary @sample-workflow.yaml | head -1

# 3) Four bad-token shapes -> all expect 401
BAD_MALFORMED="not-a-jwt"
BAD_TAMPERED="${TOKEN%.*}.AAAAinvalidsignature"
BAD_AUD=$(mint_token --audience wrong-audience)     # via the IdP or a local signing tool
BAD_ISS=$(mint_token --issuer https://evil.example)
for t in "$BAD_MALFORMED" "$BAD_TAMPERED" "$BAD_AUD" "$BAD_ISS"; do
  printf '%s -> ' "${t:0:12}"; curl -so /dev/null -w '%{http_code}\n' \
    "$GW/instances" -H "Authorization: Bearer $t"
done   # expect four 401 lines

# 4) A bad token must not open an SSE subscription
curl -sN "$GW/instances/<id>/events" -H "Authorization: Bearer $BAD_TAMPERED" -o /dev/null \
  -w '%{http_code}\n'                                       # expect 401
kubectl logs deploy/dws-admin -c dws-admin --since=30s | grep -c 'SSE subscribe'  # expect 0

# 5) Pubsub callback stays internally reachable with no browser token
kubectl logs deploy/dws-admin -c daprd --since=5m | grep -i 'dapr/subscribe'
kubectl exec deploy/dws-admin -c daprd -- \
  wget -qO- localhost:3000/dapr/subscribe       # expect the subscription list, no bearer needed
```

| Scenario (spec) | Expectation | Status |
|---|---|---|
| Missing Authorization header on read | 401, Nest never observes it | **Executed 2026-09-02 — see §5** (Dapr rejects it before Nest; proven via the direct-to-sidecar bypass) |
| Valid bearer reaches read and write | Both forwarded to Nest:3000 | **Executed 2026-09-02 — see §5** (read path proven live; write/relay path — `POST /workflows` to `dws-controller` — is unchanged by this change and out of its scope) |
| Invalid token does not reach SSE route | 401, no SSE subscription in Nest | **Executed 2026-09-02 — see §5** (401 proven on the SSE path's sibling read route; the SSE endpoint itself was proven reachable and non-buffering with a *valid* token — see §5) |
| Pubsub callback remains internal | Callback reaches app-port 3000 without a bearer | **Executed 2026-09-02 — FAILED, see §5.** Dapr's own internal `GET /dapr/subscribe` call is rejected 401 by the same bearer gate. This scenario is not currently met. |

---

## 5. Live Verification (executed 2026-09-02, superseding the prior deferral)

The prior version of this section deferred live SSE-over-APISIX/Dapr verification per `plan.md:23`
("Treat live SSE-over-APISIX/Dapr as deferred verification; do not claim live success without
evidence"). That verification has now been run against a real cluster. This section replaces the
deferral with the actual command, exit codes, and observed output — including one scenario that
**did not pass** and two chart defects the run found and fixed.

### 5.1 What was run

`scripts/verify-gateway-sse-path.sh` (new script, committed alongside this update). It provisions a
disposable `dws-gw-e2e*`-prefixed namespace, builds `dws-admin`/`dws-console` from this exact
working tree (not a published image), installs `charts/dws` in bundled gateway mode
(`apiGateway.enabled=true`, `apisix.enabled=true`, `auth.enabled=true`, `admin.enabled=true`,
`console.enabled=true`, `dapr.enabled=false` reusing the cluster's pre-installed Dapr control
plane), and asserts the live matrix through a port-forwarded APISIX gateway Service and, in
parallel, a port-forwarded admin Service (used to bypass APISIX entirely for one comparison).

```text
$ bash scripts/verify-gateway-sse-path.sh
...
PASS: Gateway API v1 CRDs and APISIX apisix.apache.org/v1alpha1 CRDs are Established cluster-wide
PASS: dws-admin:gw-e2e and dws-console:gw-e2e built from <repo>
PASS: dws-admin:gw-e2e and dws-console:gw-e2e loaded into every cluster node's containerd content store
PASS: minted RSA keypair, JWKS document, and valid/tampered test tokens (issuer=http://mock-idp.dws-gw-e2e.svc.cluster.local audience=dws-admin)
PASS: disposable Redis and mock JWKS IdP are Ready in dws-gw-e2e
PASS: helm install succeeded (bundled APISIX + Gateway API, auth/admin/console enabled)
PASS: admin Deployment carries dapr.io/sidecar-listen-addresses so daprd's sidecar port is actually Service-reachable
PASS: all Deployments/StatefulSets in dws-gw-e2e are Ready (admin, console, apisix, apisix-ingress-controller, etcd, postgres, redis, mock-idp)
PASS: GatewayClass/Gateway/GatewayProxy/HTTPRoutes exist in dws-gw-e2e
FINDING: Dapr's own internal 'GET /dapr/subscribe' discovery call was rejected with 401 ...  (see §5.4)
PASS: port-forwards ready: Gateway (http://127.0.0.1:18080) and admin Service direct (http://127.0.0.1:18081, bypasses APISIX)
PASS: APISIX has programmed the /dws-admin route (first successful proxied request observed)
PASS: GET /dws-admin/instances with a valid bearer returns HTTP 200 with the expected {items,nextCursor} JSON from Nest, via Gateway -> APISIX -> Dapr invoke -> Nest:3000
PASS: missing and tampered-signature bearers both get HTTP 401 through the Gateway
PASS: direct-to-sidecar requests (APISIX entirely bypassed via a second port-forward straight to the admin Service) reproduce the identical 401/200 behavior -- Dapr's bearer middleware is the enforcement point, APISIX adds none of its own
PASS: SSE on /dws-admin/instances/events delivers a named 'event: instance' frame 778ms after connect, observed while the connection was still open (closed 5000ms later by the client, not the server) -- NOT buffered/batched at close, through Gateway -> APISIX -> Dapr invoke -> Nest
PASS: GET / routes to the console Service (redirects same-app to its default view exactly as the console SPA does outside the gateway too) and ultimately returns HTTP 200 text/html
PASS: /dws-admin/instances still returns the admin JSON payload (not the console's HTML) -- the admin PathPrefix rule wins over the console's catch-all /

=== 17 assertions passed. verify-gateway-sse-path.sh: all checks passed ===
$ echo $?
0
```

Cleanup ran to completion in the same invocation (`helm uninstall`, namespace delete, cluster-scoped
GatewayClass delete); the only things left behind on the cluster afterward are the cluster-scoped
Gateway API v1 and `apisix.apache.org` CRDs, deliberately not removed (see the script header).
`kubectl get pods -n dapr-system`, `-n dws-phase2`, `-n dws-phase4`, `-n dws-phase5`, and
`-n kafka` were checked before and after every run in this pass; none of those pre-existing
releases were touched (same restart counts/ages throughout).

### 5.2 Result against the four required scenarios

| # | Scenario | Result |
|---|---|---|
| 1 | Valid bearer reaches Nest through APISIX -> Dapr invoke, returns real JSON | **PASS.** `GET /dws-admin/instances` with a valid bearer → HTTP 200, body `{"items":[...],"nextCursor":...}` (the actual `PaginatedInstanceSummaryDto` shape), via the real Gateway/APISIX/Dapr-invoke hop. |
| 2 | Invalid/absent bearer rejected by Dapr, not APISIX | **PASS.** Missing and tampered-signature bearers both return 401 through the Gateway. The decisive check: the *same* request sent **directly to the admin Service, entirely bypassing APISIX** via a second port-forward, reproduces the identical 401 (no bearer) / 200 (valid bearer) behavior — APISIX is not in that request's path at all, so Dapr's `middleware.http.bearer` is unambiguously the enforcement point. |
| 3 | SSE delivers a named event frame while the connection is open (not buffered/batched at close) | **PASS.** A Node HTTP client opened `GET /dws-admin/instances/events` with a valid bearer through the Gateway; 778ms after connecting, a properly-framed `event: instance` SSE message arrived; the client then deliberately held the connection open **5000ms longer** before closing it itself. Because the event arrived long before the client-initiated close, the response was not withheld until the stream ended — the specific failure mode ("buffered until close") this item was deferred over does not occur. |
| 4 | Console root routes to console; `/dws-admin/*` takes precedence | **PASS.** `GET /` returns the console's own same-app redirect (`307 -> /workflows`, identical to the console's behavior outside the gateway) and, followed once, HTTP 200 `text/html` with the console's SSR shell. `/dws-admin/instances` continues to return the admin JSON payload, not the console's HTML, confirming the admin `PathPrefix` rule wins over the console's catch-all `/`. |

### 5.3 Two chart defects found and fixed by this live run

Neither of these was ever caught by `helm lint`/`helm template` because both are runtime behaviors
a rendered-YAML-only check cannot see.

1. **`apisix.service.externalTrafficPolicy` invalid for `type: ClusterIP`.** The upstream `apisix`
   subchart's own default (`externalTrafficPolicy: Cluster`) combined with this chart's
   `apisix.service.type: ClusterIP` default produced a Service Kubernetes' API server rejects
   outright: `spec.externalTrafficPolicy: Invalid value: "Cluster": may only be set for
   externally-accessible services`. **Fixed** in `charts/dws/values.yaml` by clearing
   `apisix.service.externalTrafficPolicy` to `""` alongside `type: ClusterIP`. Without this fix, a
   plain `apisix.enabled=true` bundled install fails `helm install` on any real cluster, every time.
2. **`dapr.io/sidecar-listen-addresses` missing on the admin pod.** Dapr's app-facing HTTP API
   (port 3500) binds to loopback only (`[::1]`/`127.0.0.1`) by default — reachable from the admin
   container in the same pod, but not through any Kubernetes Service, because kube-proxy's DNAT
   still targets the pod's real interface IP, which a loopback-only daprd never listens on. Both
   admin Service shapes that front 3500 (`templates/admin/service.yaml`, migration-window
   `dapr-http` port and gateway-mode sidecar-only port) depend on this working, but nothing in the
   chart requested a wider bind address. Observed failure: APISIX's data-plane log showed
   `connect() failed (111: Connection refused) while connecting to upstream ... upstream:
   "http://<admin-pod-ip>:3500/v1.0/invoke/dws-admin/method/instances"` — a clean 502 on every
   request. **Fixed** in `charts/dws/templates/admin/deployment.yaml` by adding
   `dapr.io/sidecar-listen-addresses: "[::],0.0.0.0"` to the admin pod annotations whenever
   `auth.enabled=true` (the same condition that already renders `dapr.io/config`). This exact
   defect was already independently documented, for a different reason, in
   `.github/workflows/helm.yml`'s pubsub e2e job comment ("daprd's HTTP API (port 3500) binds to
   127.0.0.1 / [::1] only for security") — that job worked around it with `kubectl debug
   --target=admin` (shared network namespace) rather than a Service, so the underlying
   Service-unreachability was never surfaced as a chart defect until this run. **Open follow-up**
   (not fixed here, out of this change's scope): `templates/controller/deployment.yaml` has the
   identical `dapr.io/config`-when-`auth.enabled` pattern and the identical Service-front-porting
   shape (from the already-archived `dws-console-auth-phase-2` change) without this annotation —
   it is very likely affected the same way and should get the same fix in a follow-up change; see
   `docs/roadmaps/dws-auth.md` §4.
3. `helm lint`, `helm template` (default/bundled/external), and both `charts/dws/tests/*.sh`
   scripts were re-run after both fixes and still pass — see the fresh output in §3.3 above,
   unchanged by either fix (neither is visible to a render-only check).

### 5.4 One scenario that did NOT pass: Dapr's own subscription discovery is bearer-gated too

While bringing the admin pod up for the checks above, its `daprd` sidecar logged:

```text
level=error msg="app returned http status code 401 from subscription endpoint" scope=dapr.runtime.processor.subscription
```

Dapr's own internal `GET /dapr/subscribe` discovery call — made by daprd itself, with no
`Authorization` header, to discover the app's declared subscriptions — is rejected 401 by the same
`middleware.http.bearer` Component wired into `spec.appHttpPipeline`. That pipeline applies to
**every** inbound sidecar→app call, not just externally-originated ones, so it gates Dapr's own
housekeeping traffic identically to a browser request. This directly contradicts the requirement in
spec `helm-admin-auth-middleware`: *"Dapr's internal programmatic-subscription discovery and
pub/sub callback delivery SHALL continue to reach the app without requiring a browser bearer
token."* As currently wired, it does not — no subscription is ever registered once `auth.enabled`
is on, so `dws.events` messages published through the normal pubsub API would never reach Nest.

Dapr's built-in `middleware.http.bearer` has no path-exemption/allowlist field (only
`audience`/`issuer`/`jwksURL` — confirmed against the upstream component reference), so there is no
values-only workaround available in this chart today. **This is reported as a finding, not fixed in
this change** — fixing it needs a design decision (e.g. moving pubsub discovery/delivery onto a
separate, unauthenticated app-port/path Dapr's own ACL can scope independently of the bearer
pipeline, or a different Dapr-side mechanism entirely) that is out of scope for a live-verification
pass. It is recorded in `docs/roadmaps/dws-auth.md` §4 as an open item.

To still answer the actual deferred question — does SSE survive the Gateway/APISIX/Dapr-invoke hop
without buffering — assertion 3 above (§5.2) triggered the test event with a directly-authenticated
`POST /dws-admin/dapr/events/dws` carrying the identical transport-CloudEvent shape Dapr's own
delivery would have sent (`{"data": <envelope>}`), through the same Gateway/APISIX/Dapr-invoke path
already proven for reads, rather than through Dapr's own (currently broken) automatic delivery.
That isolates the streaming/buffering question this section exists to answer from the
pubsub-registration defect above, and is called out explicitly in the script's own comments and
output so the distinction is never silently blurred.

### 5.5 What this changes about the guarantee this change carries

The `helm-admin-auth-middleware` spec's four scenarios are: 3 of 4 proven live, 1 of 4 (pubsub
callback stays internal) proven **false** as currently implemented. SSE-over-Gateway/APISIX/Dapr
invoke — the specific item `design.md` and `plan.md` called out as unproven and deferred — **is now
proven to work**, with real measured timing evidence that it is not buffered. The bearer/pubsub
conflict in §5.4 is a **new, separate, real finding**, not a re-statement of the old deferral, and
is why this change is not being marked fully spec-compliant here — see the Overall Decision below.

---

## Overall Decision

- [x] PASS for the render/unit/integration gates in §1–§4 (unchanged from the prior pass).
- [x] PASS for 3 of the 4 live scenarios in §5 (valid-bearer reads, Dapr-not-APISIX bearer
  enforcement, and — the specific item this section existed to resolve — non-buffered SSE over
  Gateway/APISIX/Dapr invoke).
- [ ] **NOT PASS** for the 4th live scenario: Dapr's own subscription discovery/delivery is
  bearer-gated (§5.4), contradicting the `helm-admin-auth-middleware` spec's pubsub-stays-internal
  requirement. `auth.enabled=true` + `apiGateway.enabled=true` currently means no `dws.events`
  message ever reaches `dws-admin`'s read model or its SSE feed in a real deployment.

**Qualification**: two chart defects that blocked ANY live use of bundled gateway mode
(`apisix.service.externalTrafficPolicy`, `dapr.io/sidecar-listen-addresses`) were found and fixed
in this pass — see §5.3. The remaining open item is real and tracked, not a re-statement of the
prior deferral:

1. ~~Run the §4 bearer matrix and the SSE end-to-end check against a real cluster~~ **Done — see
   §5.** 3 of 4 scenarios pass live; the pubsub-callback scenario does not (§5.4), tracked as an
   open item in `docs/roadmaps/dws-auth.md` §4, not resolved by this pass;
2. ~~Clear the six pre-existing spec-lint items listed in §1.~~ **Done for five of six** — see §6.
   Only `ows-phase3-errors-timeouts` remains, deliberately untouched (another team's active change);
3. ~~Review the unintended TanStack patch bumps in the `dws-console` lockfile.~~ **Resolved — accepted
   as-is.** Reverting the lockfile would be pointless: `dws-console/package.json` pins all three
   packages at `"latest"` (`@tanstack/react-query`, `@tanstack/react-query-devtools`,
   `@tanstack/react-router-ssr-query`), so the next `pnpm install` re-resolves to newest and
   re-applies the same bump. The observed drift is patch-only — `5.102.3` → `5.102.8`,
   `1.167.1` → `1.167.2`, `router-core 1.171.15` → `1.171.27` — and is green through lint, test,
   typecheck, and build. The underlying issue is the `"latest"` specifiers themselves, which make
   every install non-reproducible; that is a repo-wide dependency-policy question predating this
   change, and pinning exact versions should be its own change rather than a drive-by edit here.

---

## 6. Follow-up: Pre-existing Spec-Lint Debt Cleared

Five of the six items from §1 are fixed; `openspec validate --all` now reports
`total=44 invalid=1`.

**Root cause**: the validator inspects only the **first line** of a requirement's text for a
`SHALL`/`MUST` keyword, not the whole paragraph. Every failing requirement already expressed a
normative rule — the keyword simply sat on line 2 or later after wrapping. Fixes moved the keyword
onto the first line without changing what any requirement means.

| Spec | Fix |
|---|---|
| `helm-admin-auth-middleware` | Reordered the opening clause of "Bearer middleware verifies tokens…" so `SHALL` leads |
| `helm-pubsub-integration-test` | Reordered both requirements' opening clauses |
| `helm-redis-dependency` | Reordered "Dapr Components resolve a Redis connection…" |
| `run-step-execution` | Reordered "Spawn failures are retryable" |
| `helm-postgres-deployment` | Added the missing scenario to "Bitnami configuration is values-driven", grounded in the actual `postgresql` values block (standalone, `bitnamilegacy/postgresql`, 1Gi persistence, `dws`/`dws`/`dws_admin`) |

**Deliberately not fixed**: `openspec/changes/ows-phase3-errors-timeouts/` is another team's active
in-flight change, not an archived spec. Editing someone else's unmerged change without their input
is out of scope; its two `ADDED` requirements missing SHALL/MUST are theirs to resolve.

**Review note**: an initial automated pass over these files also reflowed requirements that were not
failing, collapsed wrapped text into >110-character lines, and dropped two meaningful fragments —
"(or a comparable leg)" from the pubsub CI requirement, and "SHALL NOT **be required to run**" was
weakened to "SHALL NOT require". Those regressions were reverted; wording now matches the originals
except where the keyword had to move.

---

**Notes**:

- This pass dispatched three scoped subagents in parallel with mutually exclusive file scopes
  (`dws-admin/`, `dws-console/`, `charts/`); no overlapping edits occurred.
- `nestjs-developer` reported that tasks 1.3 and 1.4 were already complete in the working tree on
  entry — a previous session did the implementation without ticking the checkboxes. The earlier
  "incomplete" reading came from `tasks.md` rather than the actual code, so real progress was ahead
  of the ledger.
