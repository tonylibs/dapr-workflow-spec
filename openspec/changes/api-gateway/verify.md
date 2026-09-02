# Verification Report

> Produced by the verify step after apply, to confirm the implementation matches specs / design / tasks.
> Any failing check must be corrected in the corresponding artifact, then verify re-run.

**Change**: `api-gateway`
**Verified at**: `2026-09-02`
**Verifier**: Claude Code (orchestrator session + three scoped subagents: `nestjs-developer`,
`frontend-developer`, `platform-deployment-developer`)

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
| Missing Authorization header on read | 401, Nest never observes it | Pending live run |
| Valid bearer reaches read and write | Both forwarded to Nest:3000 | Pending live run |
| Invalid token does not reach SSE route | 401, no SSE subscription in Nest | Pending live run |
| Pubsub callback remains internal | Callback reaches app-port 3000 without a bearer | Pending live run |

---

## 5. Deferred Verification (explicitly incomplete, not falsely claimed)

`plan.md:23` states up front: "Treat live SSE-over-APISIX/Dapr as deferred verification; do not claim
live success without evidence." This report honors that constraint.

| Item | Status | Why deferred |
|---|---|---|
| **SSE over Gateway + Dapr (end to end)** | **NOT VERIFIED** | Requires a real cluster, APISIX data plane, and Dapr sidecar. Transport-layer behavior has unit coverage in `dws-console` (fetch-based SSE parsing, named events, cancellation, terminal-state closure, token reacquisition, reconnect/resync, 401 degradation — see task 2.4), but **actual streaming across APISIX and Dapr invoke is unproven**. Buffering, `Transfer-Encoding: chunked` passthrough, and idle timeout in particular cannot be verified at render time. |
| **Live bearer matrix (§4)** | **NOT EXECUTED** | Same constraint; needs a real IdP issuing tokens. |
| **Real APISIX traffic behavior** | **NOT VERIFIED** | This pass validated manifest render correctness only; no real data plane was started. |

**Stated plainly**: the guarantee this change currently carries stops at "manifests render correctly
and component unit/integration tests pass." The claim that a browser actually receives an SSE stream
through the Gateway is **not supported by any evidence yet**.

---

## Overall Decision

- [x] PASS (within the scope of local gates) — ready for code review and commit

**Qualification**: this PASS covers render, unit, and integration gates only. The live verification in
§4 and §5 remains outstanding. Before archiving:

1. Run the §4 bearer matrix and the SSE end-to-end check against a real cluster;
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
