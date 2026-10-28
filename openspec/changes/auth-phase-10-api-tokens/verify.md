# Verification: auth-phase-10-api-tokens

Live sections (spike 1.x, group 6) are **pending**: no cluster in the authoring session. They are
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

## Spike 1.x — pending

## Live matrix (group 6) — pending
