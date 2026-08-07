## 1. Helpers and values

- [ ] 1.1 Add `dws.admin.fullname` helper to `charts/dws/templates/_helpers.tpl` returning
  `<dws.fullname>-admin`, and `dws.admin.selectorLabels` extending `dws.selectorLabels` with
  `app.kubernetes.io/component: admin` — same pattern as `dws.controller.*`
- [ ] 1.2 Add `dws.postgres.fullname` helper returning `<dws.fullname>-postgres`, and
  `dws.postgres.selectorLabels` extending `dws.selectorLabels` with
  `app.kubernetes.io/component: postgres`
- [ ] 1.3 Add an `admin:` block to `charts/dws/values.yaml`: `enabled: true`, `replicaCount`,
  `image.{repository,tag,pullPolicy}`, `service.port` (3000), `database.{url,existingSecret,
  existingSecretKey}` (all empty/unset by default), `pubsub.{name,topic}` defaulting to
  `pubsub`/`dws.events`
- [ ] 1.4 Add a `postgresql:` block to `values.yaml`: `enabled: true`, `image.{repository,tag}`
  (`postgres`/`16-alpine`), `storage.size` (e.g. `1Gi`), `auth.{username,password,database}`
  defaulting to `dws`/`dws`/`dws_admin` (docker-compose parity), `auth.existingSecret` /
  `auth.existingSecretKey` for pointing at operator-managed credentials

## 2. Postgres templates

- [ ] 2.1 Write `templates/postgres/secret.yaml` — gated by `postgresql.enabled`; keys
  `username`, `password`, `database` from `postgresql.auth.*` (skip rendering if
  `postgresql.auth.existingSecret` is set); also compute and store a `database-url` key holding
  the full DSN (`postgres://<user>:<password>@<dws.postgres.fullname>.<namespace>.svc.cluster.local:5432/<database>`)
  so admin can reference it directly
- [ ] 2.2 Write `templates/postgres/statefulset.yaml` — gated by `postgresql.enabled`; single
  replica; container image from `postgresql.image.*`; env `POSTGRES_USER`/`POSTGRES_PASSWORD`/
  `POSTGRES_DB` via `secretKeyRef` to the postgres Secret (existing-secret name when set);
  `volumeClaimTemplates` sized from `postgresql.storage.size`; `serviceName` set to the postgres
  headless Service name
- [ ] 2.3 Write `templates/postgres/service.yaml` — gated by `postgresql.enabled`; headless
  (`clusterIP: None`); port 5432; selector from `dws.postgres.selectorLabels`

## 3. Admin templates

- [ ] 3.1 Write `templates/admin/secret.yaml` — gated by `admin.enabled` AND `postgresql.enabled`
  is `false` AND `admin.database.existingSecret` is empty (i.e., only renders when admin needs to
  own the literal `admin.database.url` DSN); single key `database-url`
- [ ] 3.2 Write `templates/admin/deployment.yaml` — gated by `admin.enabled`; `replicas` from
  `admin.replicaCount`; selector/pod labels from `dws.admin.selectorLabels` + `dws.labels`;
  container image from `admin.image.*`; container ports `3000` (http) and `3001`
  (`dapr-app-port`, no Service); env `DATABASE_URL` via `secretKeyRef` branching per design.md
  (`postgresql.enabled` → postgres Secret's `database-url` key; else
  `admin.database.existingSecret` set → that Secret/key; else → admin's own Secret's
  `database-url` key), `RUN_MIGRATIONS_ON_BOOT` (default `"true"`), `DAPR_PUBSUB_NAME`/
  `DAPR_PUBSUB_TOPIC` from `admin.pubsub.*`, `DAPR_APP_PORT=3001`; liveness/readiness probes
  `httpGet` `/health` on port `http` (3000)
- [ ] 3.3 Write `templates/admin/service.yaml` — gated by `admin.enabled`; port from
  `admin.service.port` targeting container port `http` (3000) only — no port for 3001

## 4. Helm test hook

- [ ] 4.1 Write `templates/tests/admin-db-connection.yaml` — gated by `admin.enabled`; annotated
  `helm.sh/hook: test`; a Job/Pod using a small curl-capable image that `curl`s the admin
  Service's `/health` endpoint and greps the JSON body for the `database` indicator reporting
  `up` (e.g. `grep -q '"database":{"status":"up"'`), exiting non-zero otherwise

## 5. CI

- [ ] 5.1 Add a comment above the existing dry-run step in `.github/workflows/helm.yml` explaining
  why it cannot catch a DB integration failure (no pods/DB actually start under
  `--dry-run=server`)
- [ ] 5.2 Add a new job (`integration`, `needs: verify`) to `.github/workflows/helm.yml`: spin up
  a kind cluster, `helm install` (no `--dry-run`) with `postgresql.enabled=true`, `kubectl rollout
  status` for both the admin Deployment and the postgres StatefulSet, then `helm test`, failing
  the job (and therefore the workflow) if the test hook fails

## 6. Verify

- [ ] 6.1 Run `helm lint charts/dws` — expect no errors
- [ ] 6.2 Run `helm template charts/dws` — confirm exactly one admin Deployment/Service and one
  postgres StatefulSet/Service/Secret render, and confirm admin's rendered `DATABASE_URL`
  `secretKeyRef` points at the postgres Secret's `database-url` key (no admin Secret rendered)
- [ ] 6.3 Run `helm template charts/dws --set admin.enabled=false` — confirm no admin resources
  (including the test hook) render
- [ ] 6.4 Run `helm template charts/dws --set postgresql.enabled=false` — confirm no postgres
  resources render
- [ ] 6.5 Run `helm template charts/dws --set postgresql.enabled=false --set admin.database.url=postgres://user:pass@managed-db.example.com:5432/dws_admin`
  — confirm admin's own Secret renders with that DSN, and admin's `DATABASE_URL` references it
- [ ] 6.6 Run `helm template charts/dws --set postgresql.enabled=false --set admin.database.existingSecret=my-db-secret --set admin.database.existingSecretKey=dsn`
  — confirm admin's `DATABASE_URL` references `my-db-secret`/`dsn` and no admin-owned Secret
  renders
- [ ] 6.7 On a real kind cluster: `helm install` with default values, wait for admin and postgres
  rollouts, run `helm test`, and confirm it passes with the DB indicator reporting up
- [ ] 6.8 Show the rendered admin Deployment, postgres StatefulSet, and test hook to the user for
  Phase 3 sign-off
