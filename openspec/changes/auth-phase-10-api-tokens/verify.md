# Verification: auth-phase-10-api-tokens

Historical note (2026-10-28): live sections were pending in the authoring session. They were
to be filled in on the owner's local cluster per `tasks.md`. No token or JWT value is recorded here.

## Local gates (2026-10-28)

| Package | Command | Result |
|---|---|---|
| `dws-admin` | `pnpm lint && pnpm test && pnpm build` (Postgres 16 via `TEST_DATABASE_URL`) | 0; 20 suites / 159 tests |
| `charts/dws` | `helm lint charts/dws` (Helm 4.3.0) | 0 |
| `charts/dws` | `helm template dws charts/dws` | 0 |
| `charts/dws` | `bash charts/dws/tests/<each>.sh charts/dws` (all six, incl. new `api-tokens-render-test.sh`) | 0 |

`api-tokens-render-test.sh` was mutation-checked: reverting any one of 19 wirings (annotations,
env, route filter, `set`→`add`, literal value, console-route header, token length, existingSecret,
shared Secret, APISIX defaults, guard, NOTES leak, auth-off Secrets) makes it fail.

## 5.3 — `.github/workflows/helm.yml` jobs and auth

- `integration` (pubsub e2e) installs with auth off (`controller.enabled=false`,
  `postgresql.enabled=true`, `dapr.enabled=true`): its direct `:3000` port-forward and `:3500`
  `kubectl debug` calls are unaffected, and its `helm test` renders neither auth test Job.
- `verify` (lint, template, `--dry-run=server`) and `integration-dapr-preinstalled` also run with
  auth off; `integration-oauth-path-filter` does not use the chart.
- The only auth-on job is `integration-gateway-sse`, which runs `scripts/verify-gateway-sse-path.sh`
  (updated in 5.1) and builds `dws-admin` from the same tree, so it needs the `dws-admin` app-token
  change in the same PR. It sets `GW_E2E_INCLUDE_CONTROLLER=0`, so controller token wiring is not
  covered live in CI.

## Spike 1.x — completed; see 2026-10-31 evidence below

## Live matrix (group 6) — see 2026-10-31 evidence below

## Live verification — 2026-10-31

Context: `docker-desktop`, nodes desktop-control-plane/desktop-worker/desktop-worker2,
Dapr 1.18.2, bundled APISIX 3.17.0 and ingress-controller 2.1.0. Disposable namespace
`dws-gw-e2e`; current-tree admin/console images built locally and imported into every node.
The chart's default controller image could not be pulled; the locally available
`ghcr.io/tonylibs/dws-controller:phase4-live` was imported and used for controller checks.
No controller application code changes are part of this change.

### Commands and results

| Command/check | Result |
|---|---|
| `pnpm install --frozen-lockfile` in dws-admin | Exit 0; restored missing local dependencies |
| `pnpm lint; pnpm test; pnpm build` in dws-admin (stop on failure) | Exit 0; 20 suites, 159 tests passed |
| Helm 4.3.0 lint, default template, all six chart test scripts | Exit 0; token render test 30 groups |
| First `scripts/verify-gateway-sse-path.sh` run | Exit 0; 36 assertions passed |
| Second run with prebuilt images and controller tag override | 35 assertions passed; namespace retained for extra checks |
| `helm upgrade dws charts/dws -n dws-gw-e2e --reuse-values` | Exit 0; all three token hashes identical before/after |
| `helm test dws -n dws-gw-e2e --timeout 2m` after health-test fix | Exit 0; admin DB test and both controller tests Succeeded |
| `bash -n scripts/verify-gateway-sse-path.sh` | Exit 0 |
| ShellCheck via `koalaman/shellcheck:stable`, read-only source mount | Exit 0 |

The original admin DB Helm test failed with 401 because its Service targets the sidecar.
The auth-on test now runs as an injected Job and invokes `/health` via its own sidecar;
Dapr's internal service invocation supplies the app token. The auth-off test is unchanged.

### Spike 1.1–1.4

The APISIX Route references a Service; the merged plugin is on that Service, not directly
on the Route. Admin API inspection of both resources found:

```json
{"proxy-rewrite":{"regex_uri":["^(/dws-admin)(/.*)?$","/v1.0/invoke/dws-admin/method$2"],"headers":{"set":{"dapr-api-token":"$env://DWS_ADMIN_DAPR_API_TOKEN"}}}}
```

An additional `token-spike` HTTPRoute used the same two filters and Secret-backed variable,
with its own echo Deployment/Service and token-protected Dapr sidecar. The echo app compared
its incoming header to the app-token Secret internally and returned only
`{"match":true,"length":48}`. Both a normal request and one carrying a forged client token
returned that result. This proves environment resolution and filter merging at APISIX,
and replacement of the sidecar API token with the distinct app token by daprd. D4 retains
the RequestHeaderModifier approach; no literal-token fallback is used.

### Live matrix

| Path / credentials | Observed |
|---|---|
| Admin sidecar: valid API token; missing/malformed/tampered/wrong-audience/wrong-issuer bearer | 401 for each |
| Admin sidecar: valid API token + valid bearer | 200 |
| Controller service invocation: missing/malformed/tampered/wrong-audience/wrong-issuer bearer | 401 for each |
| Controller service invocation: valid bearer | 200 |
| Admin sidecar: valid bearer + missing/wrong API token | 401 for each |
| Admin sidecar: valid API token + no bearer | 401 |
| Gateway: valid bearer + forged API token | 200; client header replaced |
| Gateway: no bearer + forged API token | 401 |
| Admin pod-IP app port: GET instances with missing/wrong token | 401 for each |
| Admin pod-IP app port: forged event POST with missing/wrong token | 401 for each; instance/processed-event/task row counts unchanged |
| Admin pod-IP app port: GET health | 200; probes remain Ready |
| Real controller public API, via ephemeral curl container in controller pod network namespace | 401 without token (`/v1.0/metadata`) |
| Controller pod-IP app port: GET workflows | 200; expected deferred D7 exposure |
| Pubsub publish through caller sidecar | 204; subscription registered for dws.events; event reached SSE/read model |
| SSE through Gateway | Named instance frame in 2744 ms; connection remained open another 5019 ms |
| APISIX admin upstream checks | No active/passive checks; ten authenticated requests stayed 200 after negative cases |
| Generated tokens across Helm upgrade | All three preserved; hash equality only, no token output |

The app subscription registered while APP_API_TOKEN was enabled; no app-channel 401 was
observed in the inspected subscription logs. Pod deletion/recovery and the separate ingress
migration rehearsal are recorded below when complete.
### Individual pod deletion (task 6.7)

The extended run deleted admin, APISIX and controller pods in that order. Each replacement
became Ready and authenticated requests through the in-cluster Gateway returned HTTP 200.
The subscription grep matched after admin replacement, but Windows kubectl reported a closed
stdout pipe because `grep -q` exited early under `pipefail`; the extended harness exited 1 at
that final assertion. The script now captures daprd output to a private file before searching,
which avoids that pipe closure. This harness failure is recorded separately from the observed
successful pod recovery. Admin app containers had exactly one APP_API_TOKEN and one
DAPR_API_TOKEN entry (no injector duplication).

### Migration rehearsal and final result

`PATH=<Helm-4.3.0-dir>:$PATH bash scripts/verify-console-ingress-migration.sh`
passed all 19 assertions, including clean legacy-value rejection, external APISIX adoption,
Gateway reconciliation and a real rollback restoring the serving legacy admin-gateway.
An earlier overlapping run failed due to IngressClass ownership collision; the final run was
sequenced after gateway cleanup and passed. The retained gateway namespace and the migration
namespace were cleaned by their scripts; shared CRDs remain as documented by those scripts.

Task 6.7's three individual pod-recovery checks passed; its final subscription assertion exposed
and led to correction of the Windows pipe issue described above. The standard gateway script
had already exited 0 with 36 assertions. The final migration script also exited 0. No token or
JWT values are recorded. One chart warning remains for the standalone auth-off/APISIX-only
configuration; see `verification-report.md` and the chart README workaround.
