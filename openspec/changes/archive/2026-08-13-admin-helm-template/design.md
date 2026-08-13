## Context

See `proposal.md` - Why. Phase 2 (`helm-controller-templates`, now archived) established the
pattern this change mirrors: a `component.enabled` toggle, a `dws.<component>.fullname` /
`dws.<component>.selectorLabels` helper pair in `_helpers.tpl`, and a `<component>:` block in
`values.yaml`. This change extends that same pattern to `admin/` and `postgres/`.

Constraints from the container contracts (not decisions to make, just facts to build against):
- `dws-admin` listens on `3000` (Nest/HTTP) and `3001` (`DAPR_APP_PORT`, sidecar-only). Only
  `3000` needs a Service.
- `dws-admin`'s env contract is `DATABASE_URL`, `RUN_MIGRATIONS_ON_BOOT`, `DAPR_PUBSUB_NAME`,
  `DAPR_PUBSUB_TOPIC`, `DAPR_APP_PORT` (`.env.example`).
- Health is `GET /health` on `3000`, a Terminus `HealthCheckService` payload; the `database`
  indicator (`DbHealthIndicator`) is the one that actually proves DB connectivity — a `200` alone
  doesn't, since Terminus returns `200` only when *all* indicators pass, but a future indicator
  added to the same check could still mask which one failed if we only assert on status code.
- `docker-compose.yml`'s Postgres credentials (`dws`/`dws`/`dws_admin`) are the chart's dev-parity
  defaults; the conditional Bitnami PostgreSQL subchart supplies the database runtime.

## Goals / Non-Goals

**Goals:**
- Real `admin/` and `postgres/` templates, following the Phase 2 helper/gating pattern exactly.
- One `DATABASE_URL` resolution path that branches cleanly between in-chart Postgres and an
  external DSN, without duplicating credential Secrets.
- A test that proves DB connectivity post-apply, not just "the process started."

**Non-Goals:**
- Postgres HA, backups, or production hardening (roadmap already flags built-in Postgres as
  dev/eval-grade; out of scope here).
- `dws-admin`'s read API (`WorkflowsModule`/`InstancesModule`) — not built yet upstream, nothing
  to expose via Service/Ingress beyond what already exists.
- Changing `dws-admin` itself (image, code, env var names) — the chart only wires the existing
  contract.
- `dws-console` — still blocked upstream, untouched.

## Decisions

**DATABASE_URL composition: template-time string, not a runtime `secretKeyRef` join.**
A Postgres DSN packs five pieces (user, password, host, port, dbname) into one string; Kubernetes
has no way to compose a Secret value from *other* Secret keys at pod-start time without an init
container or controller. Since the in-chart Postgres credentials (`postgresql.auth.*`) are already
known at `helm template` time, the DSN is composed directly in the template with `printf`, and
stored as a `database-url` key inside the chart-owned connection Secret
(`templates/postgres/secret.yaml`). The Bitnami subchart independently owns its StatefulSet,
Services, and credentials Secret. The admin Deployment's `DATABASE_URL` env then does a
`secretKeyRef` to `dws.postgres.fullname` / key `database-url` when `postgresql.enabled: true`,
while the DSN host is derived from the Bitnami primary Service. Admin never renders its own
database Secret in that branch.

**External DB branch: admin owns its Secret, only when needed.**
When `postgresql.enabled: false`, `templates/admin/secret.yaml` renders (gated additionally on
`admin.database.url` being set — i.e., not set when `admin.database.existingSecret` is used
instead) holding `DATABASE_URL` from `admin.database.url`. When `admin.database.existingSecret` is
set, the admin Deployment's `DATABASE_URL` env does a `secretKeyRef` straight to that
operator-provided Secret/key, and `templates/admin/secret.yaml` renders nothing. Net effect:
exactly one place holds the DSN in every configuration — in-chart postgres Secret, admin's own
Secret, or an operator's existing Secret — never two.

**Branch logic lives in the admin Deployment template, gated with `if/else if/else`:**
```
{{- if .Values.postgresql.enabled }}
  secretKeyRef: { name: {{ include "dws.postgres.fullname" . }}, key: database-url }
{{- else if .Values.admin.database.existingSecret }}
  secretKeyRef: { name: {{ .Values.admin.database.existingSecret }}, key: {{ .Values.admin.database.existingSecretKey | default "url" }} }
{{- else }}
  secretKeyRef: { name: {{ include "dws.admin.fullname" . }}, key: database-url }
{{- end }}
```
`templates/admin/secret.yaml` mirrors the same `if/else if/else` so it only renders the third
branch's Secret object.

**Bitnami subchart owns PostgreSQL Services.**
The conditional upstream subchart renders the PostgreSQL StatefulSet and its Services. The chart's
`dws.postgres.host` helper follows the subchart's primary Service naming convention
(`&lt;release&gt;-postgresql`, or `postgresql.fullnameOverride`) so the composed admin DSN targets the
deployed database without duplicating workload templates.

**Helm test hook checks the parsed `database` indicator, not just HTTP status.**
Terminus's health payload shape is `{"status":"ok","info":{...},"details":{"database":{"status":
"up"}}}` on success and returns HTTP `200` only when every indicator passes — but since `dws-admin`
today has exactly one indicator, a bare `200` check would already be correct. It's asserted via
JSON parsing (not just status code) anyway, so the test keeps catching the right failure mode if a
second indicator (e.g., pub/sub connectivity) is added later without the test needing to change
its meaning. The hook is a `curl | jq` one-liner in a small Job — no new image/dependency, `curlimages/curl` (already minimal, used commonly in Helm test hooks) is sufficient since it also ships nothing needed for JSON parsing beyond grep-based matching (`grep -q '"database":{"status":"up"'`), avoiding a `jq` dependency in the test image entirely.

**CI: extend `.github/workflows/helm.yml` with a second job, not a modified `verify` job.**
The existing `verify` job's dry-run install and the new real-install are different concerns
(static validation vs. live behavior) with different failure modes and runtimes; keeping them as
separate jobs means a dry-run failure and a live-integration failure are distinguishable at a
glance in the Actions UI, and the real-install job can be skipped/retried independently. The new
job runs after `verify` (`needs: verify`) since there's no reason to spin up a live Postgres if
static validation already failed.

## Risks / Trade-offs

- **[Risk]** Composing a DSN with `printf` embeds the Postgres password as plain template output
  inside a Secret's (base64-encoded, not encrypted) data — no different in practice from any
  Helm-templated Secret, but worth naming. → Mitigation: this is already how the existing
  `postgresql.auth.password` default is handled (a values-driven dev credential); the roadmap doc
  already calls out that built-in Postgres is dev/eval-grade and production users should point at
  a managed instance instead (`postgresql.enabled: false`).
- **[Risk]** The real-install CI job adds meaningful runtime (pulling the Bitnami PostgreSQL image
  and `ghcr.io/tonylibs/dws-admin`, waiting for two rollouts) to every chart-touching PR. → Mitigation:
  scoped to the same path filters as the rest of `helm.yml` (`charts/**`), so it only runs when
  chart changes are actually being validated.
- **[Risk]** `grep`-based JSON matching in the test hook is brittle to Terminus payload formatting
  changes (key ordering, whitespace). → Mitigation: `@nestjs/terminus`'s JSON output is stable
  and machine-generated (not hand-formatted), and the match pattern only needs to survive that
  one library's serialization, not arbitrary JSON.

## Migration Plan

No migration — this only adds new templates and values keys with backward-compatible defaults
(`admin.enabled` and `postgresql.enabled` both default `true`, matching the chart's existing
all-on-by-default posture from Phase 2). No existing release is affected until someone runs
`helm upgrade` with this chart version, at which point the new admin resources and the Bitnami
PostgreSQL subchart resources are created fresh (nothing pre-existing to migrate). Rollback is a
plain `helm rollback` to the prior chart version, which simply removes the newly-created resources.
