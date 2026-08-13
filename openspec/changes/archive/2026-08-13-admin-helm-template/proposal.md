## Why

Phase 2 of the Helm packaging roadmap (`docs/roadmaps/helm-packaging.md`) shipped real templates
for `dws-controller`; `admin/` and `postgres/` are still `helm create` placeholders. Without them,
`charts/dws` cannot bring up `dws-admin` or its Postgres read model, so `helm install` cannot
stand up the full control plane described in the roadmap. This also closes a testing gap: CI
today only lints/templates/dry-run-installs the chart, so nothing proves a deployed `dws-admin`
can actually reach a deployed Postgres.

## What Changes

- Add `charts/dws/templates/admin/{deployment,service,secret}.yaml` rendering the `dws-admin`
  Deployment (env, probes, ports per its Dockerfile/`.env.example` contract), Service (port 3000
  only — 3001 is Dapr-sidecar-only, no Service needed), and a Secret carrying `DATABASE_URL`.
- Add `charts/dws/templates/postgres/{statefulset,service,secret}.yaml` rendering a single-replica
  Postgres StatefulSet, headless Service, and credentials Secret, gated by `postgresql.enabled`
  (default `true`), mirroring `dws-admin/docker-compose.yml`'s dev credentials.
- Extend `_helpers.tpl` with `dws.admin.fullname`, `dws.postgres.fullname`, and matching
  selector-label helpers, following the existing `dws.controller.*` pattern exactly.
- Extend `values.yaml` with `admin:` and `postgresql:` blocks (image, replicas, service port,
  database connection resolution, pubsub name/topic).
- Wire DB connection resolution: when `postgresql.enabled: true`, admin's `DATABASE_URL` resolves
  automatically from the in-chart Postgres Secret/Service; when `false`, it comes from
  `admin.database.url` or `admin.database.existingSecret` (external/managed DB), with the branch
  handled in the admin Deployment template rather than by duplicating Secrets.
- Add a Helm test hook (`templates/tests/admin-db-connection.yaml`) that curls the deployed
  admin's `/health` endpoint post-install and fails unless the response is `200` **and** the
  Terminus health payload's `database` indicator specifically reports `up`.
- Extend `.github/workflows/helm.yml` with a real (non-dry-run) kind install job — installs with
  `postgresql.enabled=true`, waits for both `admin` and `postgres` rollouts, then runs
  `helm test` — because the existing dry-run job never starts pods and so cannot catch a broken
  admin/Postgres integration.

Controller templates/values are untouched. `dws-console` remains a placeholder (blocked upstream,
per the roadmap).

## Capabilities

### New Capabilities
- `helm-admin-deployment`: Chart-rendered `dws-admin` Deployment/Service/Secret — image,
  replicas, ports, env, probes, and DB connection resolution (in-chart Postgres vs. external DSN),
  sourced from `admin:` values.
- `helm-postgres-deployment`: Chart-rendered, toggleable in-chart Postgres StatefulSet/Service/
  Secret backing `dws-admin`'s read model, sourced from `postgresql:` values.
- `helm-admin-db-integration-test`: A Helm test hook plus a CI job that perform a real
  (non-dry-run) install and assert `dws-admin` can reach Postgres via its `/health` DB indicator.

### Modified Capabilities
(none — `helm-controller-deployment` is untouched by this change)

## Impact

- **Affected code**: `charts/dws/templates/admin/*`, `charts/dws/templates/postgres/*`,
  `charts/dws/templates/tests/admin-db-connection.yaml` (new), `charts/dws/templates/_helpers.tpl`,
  `charts/dws/values.yaml`, `.github/workflows/helm.yml`.
- **Dependencies**: none new — Postgres image is the standard `postgres:16-alpine` already used in
  `dws-admin/docker-compose.yml`.
- **Runtime contract preserved**: no changes to `dws-admin` itself — the chart only wires the
  container contract (`DATABASE_URL`, `RUN_MIGRATIONS_ON_BOOT`, `DAPR_PUBSUB_NAME`,
  `DAPR_PUBSUB_TOPIC`, `DAPR_APP_PORT`, port 3000 `/health`) already defined in its Dockerfile and
  `.env.example`; nothing here requires an image or code change in `dws-admin/`.
- **CI**: adds a real-install job to `.github/workflows/helm.yml`, increasing the workflow's
  runtime (pulls a Postgres image, waits for two rollouts) but not changing the existing
  lint/template/dry-run job.
