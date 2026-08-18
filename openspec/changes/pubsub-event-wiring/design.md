## Context

`charts/dws` today has one Bitnami subchart pattern already proven: `postgresql` (conditional on
`postgresql.enabled`, chart-owned `templates/postgres/secret.yaml` composing a `DATABASE_URL`
from `postgresql.auth` + the subchart's own Service host). Phase 5 needs the same conditional
shape for Redis, but the consumers are different: three Dapr `Component` manifests, not an app
container env var, and Dapr's `pubsub.redis`/`configuration.redis`/`state.redis` component types
take `redisHost` (`host:port`) and `redisPassword` (`secretKeyRef`) directly — no connection-string
Secret to compose. `dws-orchestrator/k8s/configuration-component.yaml` already encodes this exact
shape by hand: `redisHost: redis-master.default.svc.cluster.local:6379`,
`redisPassword` via `secretKeyRef: {name: redis, key: redis-password}`, `enableTLS: "false"`,
`auth.secretStore: kubernetes`. The Secret name/key (`redis`/`redis-password`) are Bitnami Redis's
own conventional defaults — that manifest was already written against a Bitnami-shaped Redis
before this chart existed to install one.

See `proposal.md` for motivation. See `specs/helm-redis-dependency`, `specs/helm-pubsub-component`,
`specs/helm-definitions-component`, `specs/helm-actor-statestore-component`,
`specs/helm-pubsub-integration-test`, and the `helm-controller-deployment` delta for the
requirements this design implements.

## Goals / Non-Goals

**Goals:**
- Reuse the Bitnami Redis subchart's own auto-created Service and Secret directly for all three
  Dapr Components — no chart-owned custom Redis Secret, since none of the three Components need a
  composed connection string (unlike Postgres/`DATABASE_URL`).
- Tie Redis's install lifecycle to `dapr.enabled` (its only consumer) rather than an independent
  toggle, while keeping the external-Redis escape hatch recognizable to chart consumers already
  familiar with `admin.database.url`+`existingSecret`.
- Land all three Component templates behind one shared connection-resolution helper so
  `pubsub`/`dws-definitions`/actor-statestore stay consistent by construction.

**Non-Goals:**
- Not migrating `dws-orchestrator/k8s/configuration-component.yaml` or deleting it — it stays as
  the reference/no-chart manifest; the chart's `definitions-component.yaml` is an independent,
  functionally-equivalent template for chart-managed installs.
- Not wiring `dws-orchestrator` to the actor/workflow state store — `actor-statestore-component.yaml`
  is provisioned only; nothing consumes it yet (tracked separately, out of Phase 5's stated scope).
- Not building a full controller→orchestrator→admin round-trip test — the pub/sub CI assertion
  proves transport (Component Ready + delivery), not application-level event correctness (already
  covered by each component's own unit/integration tests per `docs/events.md`).

## Decisions

### Controller Dapr annotations: unconditional, no app-port

`dws-controller` only calls out through its Dapr sidecar (`EventPublisher`/`DaprClientProducer`
publish `dws.events`); grepping `dws-controller/src` confirms no Dapr-routed inbound path (no
service-invocation target, no declarative subscription endpoint). `dapr.io/app-port` and a second
container port exist only to tell the sidecar where to forward *inbound* Dapr traffic, so adding
one here would be dead configuration. Decision: `dapr.io/enabled`/`dapr.io/app-id` only,
omitting `dapr.io/app-port`, `DAPR_APP_PORT`, and the second container port entirely.

The annotations are rendered **unconditionally** — NOT gated by `.Values.dapr.enabled` (unlike
the admin Deployment). Alternative considered: mirror admin's `if .Values.dapr.enabled` wrap.
Rejected — Dapr sidecar annotations are inert without the sidecar-injector webhook running, so a
`dapr.enabled=false` install still renders them harmlessly, and keeping them always-on means the
controller starts publishing the moment a Dapr control plane becomes available (whether the
chart later installs one, or the operator installs one externally) without a follow-up
`helm upgrade` to flip the annotation on. Admin stays gated because its container also depends
on `DAPR_PUBSUB_*` env vars whose absence changes application behavior; the controller has no
matching env-var coupling to the toggle.

### Redis installs whenever Dapr does — no independent `redis.enabled` toggle

Alternative considered: an independent `redis.enabled` value (default `true`), mirroring
`postgresql.enabled` exactly. Rejected — unlike Postgres (which backs admin regardless of Dapr),
Redis in this chart exists solely to back the three Dapr Components; an operator who disables
Dapr has no use for it, and a separate default-true toggle only recreates a redundant "is this
on" decision that `dapr.enabled` already makes. Decision: `charts/dws/Chart.yaml`'s `redis`
dependency uses `condition: dapr.enabled` — Redis's install lifecycle is 1:1 with Dapr's, not a
value of its own.

External Redis (the production escape hatch) is expressed differently as a result: instead of a
boolean toggle, `redis.external.host` acts as an override switch by its own presence. When it's
set (non-empty), the three Component templates resolve their `redisHost`/`redisPassword` to that
external host and `redis.external.existingSecret`/`existingSecretKey` instead of the in-chart
Bitnami Redis — independent of whether the Bitnami Redis subchart also installed (it still does,
whenever `dapr.enabled=true`, since Chart-dependency conditions can't express "AND redis.external
is unset"). Known limitation, not a correctness bug: an operator running Dapr with an external
production Redis gets an extra, unused in-chart Redis instance unless they separately trim its
footprint (e.g. `--set redis.master.resources...`) or accept it. This is called out under
Risks below rather than solved with a second toggle, to avoid re-adding the flag this decision
just removed.

### Redis Components reference the Bitnami subchart's own Secret/Service directly

Alternative considered: a chart-owned `templates/redis/secret.yaml` composing a Redis connection
string, mirroring `templates/postgres/secret.yaml`. Rejected — Dapr's Redis component types don't
take a connection string; they take `redisHost`/`redisPassword` fields directly, which the Bitnami
Redis subchart's own auto-created Secret/Service already provide with no chart-owned intermediary.
Adding one would only duplicate the Bitnami Secret's `redis-password` key into a second Secret,
buying nothing.

Decision: `templates/redis/` (if it exists as a directory at all) contains no Secret — only, if
needed, a NOTES/helper stub. `_helpers.tpl` gains:
- `dws.redis.host` → `{{ .Release.Name }}-redis-master.{{ include "dws.namespace" . }}.svc.cluster.local:6379`
  when `redis.external.host` is unset (Bitnami standalone-architecture default Service name), or
  `.Values.redis.external.host` when it is set.
- `dws.redis.secretName` / `dws.redis.secretKey` → the Bitnami subchart's own Secret name
  (`{{ .Release.Name }}-redis`) and key (`redis-password`) by default, or
  `.Values.redis.external.existingSecret` / `existingSecretKey` (default `redis-password`) when
  `redis.external.host` is set — mirroring `admin.database.existingSecret`/`existingSecretKey`.

All three Component templates (`pubsub-component.yaml`, `definitions-component.yaml`,
`actor-statestore-component.yaml`) call these same helpers, so the connection-resolution logic
lives in one place. Before finalizing the pinned Bitnami Redis chart version, `helm template` its
actual Secret name/key and standalone Service name to confirm they match the
`<release>-redis`/`redis-password`/`<release>-redis-master` defaults assumed here — Bitnami chart
versions have changed default naming schemes across major bumps before (see the `bitnamilegacy`
image-repository precedent already hit by `postgresql`).

### definitions-component.yaml is additive, not a migration

`dws-orchestrator/k8s/configuration-component.yaml` stays untouched. It's the manifest a non-chart
deployment applies by hand; `charts/dws/templates/definitions-component.yaml` is the equivalent
for chart-managed installs. They intentionally describe the same `dws-definitions` Component
shape against potentially different Redis hosts — no shared templating between the two repos.

### actor-statestore-component.yaml ships unused

Per the roadmap's stated Phase 5 scope, the actor/workflow state store Component renders whenever
Dapr+Redis are available, even though nothing references it yet. It's inert until
`dws-orchestrator` starts calling the Dapr Workflow runtime — rendering it now avoids a second
Helm change purely to add a Component with no app-side dependency.

### CI pub/sub assertion: minimal publisher/subscriber, not app-level

The `integration` job already runs with `dapr.enabled=true`, `controller.enabled=false` (no
published controller image tag in CI). Reusing the real controller/admin pods for the pub/sub
assertion isn't possible without also standing up a workable controller image. Decision: add a
lightweight assertion step — `dapr components list` (or `kubectl get component pubsub -o
jsonpath` for a Ready-equivalent condition) plus a `dapr publish`/subscriber round-trip using the
Dapr CLI or two throwaway `curl`+`daprd`-sidecar pods — scoped to proving the `pubsub` Component
is live and topic `dws.events` carries a message, not proving controller/orchestrator/admin
business logic.

## Risks / Trade-offs

[Bitnami Redis chart's default Secret/Service naming could differ from the
`<release>-redis`/`redis-password`/`<release>-redis-master` assumption, silently breaking the
Components' `secretKeyRef`] → Verify against `helm template` output for the actually-pinned chart
version as an implementation task, before writing the Component templates' final helper values;
`helm lint`/CI's server-side dry-run will also catch a Secret name that doesn't resolve.

[An operator running Dapr with `redis.external.host` set for production still gets an unused
in-chart Bitnami Redis instance, since `Chart.yaml`'s `condition: dapr.enabled` can't also express
"AND no external Redis configured"] → Documented, accepted trade-off (see the "no independent
`redis.enabled` toggle" decision above); the operator can trim or ignore the unused instance, or a
future change can revisit this if it proves painful in practice.

[Bitnami's relocated `bitnamilegacy` image workaround, applied to `postgresql.image.repository`,
may or may not be needed for the pinned Redis chart version] → Check at implementation time
(same-generation Bitnami charts typically need the same workaround); apply
`redis.image.repository: bitnamilegacy/redis` + rely on the existing
`global.security.allowInsecureImages` only if the default `docker.io/bitnami/redis` pull actually
fails.

[Minimal CI pub/sub assertion (Component Ready + one message) doesn't catch a controller- or
orchestrator-side publish regression] → Acceptable per proposal's stated scope ("a full
controller/orchestrator/admin round-trip isn't required for this phase"); each Java component's
own test suite already covers its publish path per `docs/events.md`.

## Migration Plan

No data migration. Redis and all three Components install automatically on a fresh `helm install`
since they follow `dapr.enabled`, which already defaults to `true`. An existing release upgrading into this chart version gains a new
Bitnami Redis subchart deployment and three new Components on `helm upgrade` — no destructive
change to already-rendered controller/admin/postgres resources. Rollback is a plain
`helm rollback` to the prior release revision, which removes the Redis subchart's resources and
the three Components along with it (Bitnami's own Secret and PVC lifecycle follow its subchart's
`--uninstall`/`--rollback` behavior, unchanged by this chart's templates).
