## Why

Dapr control-plane wiring (Phase 4) is done, but the two things it unblocks — the `dws-controller`
Deployment's own Dapr sidecar annotations and the `pubsub` Component both `dws-controller` and
`dws-orchestrator` publish `dws.events` through — are still missing from `charts/dws`. Without
`pubsub-component.yaml`, event publishing (already shipped in both Java components per
`docs/events.md`) degrades silently to a no-op on any chart-managed install. Redis, which backs
that Component and also the `dws-definitions` Configuration store `dws-orchestrator` already
depends on today (via a hand-applied, unmanaged manifest pointing at
`redis-master.default.svc.cluster.local`), isn't chart-managed either. This closes out Helm
packaging roadmap Phase 5.

## What Changes

- Add `dapr.io/enabled`/`dapr.io/app-id` pod annotations to the controller Deployment
  (`templates/controller/deployment.yaml`), gated by `.Values.dapr.enabled`, mirroring the admin
  Deployment. No `dapr.io/app-port`/second container port — `dws-controller` only publishes
  outbound via `EventPublisher`/`DaprClientProducer`, it never receives Dapr-routed inbound
  traffic (service invocation target or pubsub subscription), so no app-port is needed.
- Add `redis` as a conditional Bitnami subchart dependency, gated by `condition: dapr.enabled` —
  not an independent `redis.enabled` toggle. Redis exists solely to back the three Dapr Components
  below, so its lifecycle simply follows Dapr's: `Chart.yaml` dependency entry, a `redis:` values
  block (standalone architecture, auth password, persistence size, `networkPolicy.enabled: false`
  for kind/dev), and the same `bitnamilegacy` image-repository workaround if the Bitnami Redis
  chart needs it. Support an external Redis via `redis.external.{host,existingSecret,existingSecretKey}`
  values: when `redis.external.host` is set, the Dapr Components resolve their connection to that
  external Redis instead of the in-chart Bitnami one (mirroring `admin.database.url`/
  `existingSecret`), independent of whether the built-in Redis subchart also installed.
- Add three Dapr `Component` templates, gated by `.Values.dapr.enabled` alone (Redis is always
  resolvable when Dapr is enabled — built-in or external), shaped like
  `dws-orchestrator/k8s/configuration-component.yaml`:
  - `templates/pubsub-component.yaml` — `pubsub.redis`, component name `pubsub` (the name
    `dws-orchestrator` already uses for `emit` and both components use for event publishing —
    not a new name).
  - `templates/definitions-component.yaml` — `configuration.redis`, component name
    `dws-definitions`. Chart-manages what `dws-orchestrator/k8s/configuration-component.yaml`
    does today by hand against an unmanaged Redis host.
  - `templates/actor-statestore-component.yaml` — `state.redis`, `actorStateStore: "true"`. Not
    consumed by anything yet (`dws-orchestrator` doesn't call the Dapr Workflow runtime), added
    ahead of that per the roadmap's stated Phase 5 scope.
- Decide and record the fate of `dws-orchestrator/k8s/configuration-component.yaml` now that the
  chart renders an equivalent Component (kept, for non-chart/local deployments — documented as
  legacy in the design).
- Extend `.github/workflows/helm.yml`'s `integration` job (or a comparable leg) with an assertion
  that the `pubsub` Component is Ready in-cluster and that a message published to `dws.events` is
  actually delivered — not just that the chart renders/installs.
- Update `docs/roadmaps/helm-packaging.md` (Phase 5 status, chart-layout tree, "Open items") and
  the Helm row in `docs/roadmaps/README.md` once implemented.

Out of scope: Phase 6 (console) and Phase 11 (Knative).

## Capabilities

### New Capabilities

- `helm-redis-dependency`: in-chart Bitnami Redis subchart backing the Dapr Redis Components,
  installed whenever `dapr.enabled` is true, with an external-Redis escape hatch — mirrors
  `helm-postgres-deployment`.
- `helm-pubsub-component`: the `pubsub` (`pubsub.redis`) Dapr Component template, gated by
  `dapr.enabled`.
- `helm-definitions-component`: the `dws-definitions` (`configuration.redis`) Dapr Component
  template, chart-managed replacement for the hand-applied
  `dws-orchestrator/k8s/configuration-component.yaml`.
- `helm-actor-statestore-component`: the `state.redis` actor/workflow state store Component
  template, provisioned ahead of `dws-orchestrator`'s Dapr Workflow runtime adoption.
- `helm-pubsub-integration-test`: CI assertion (in `.github/workflows/helm.yml`) that the deployed
  `pubsub` Component is Ready and that a published message on `dws.events` is delivered.

### Modified Capabilities

- `helm-controller-deployment`: add `dapr.io/enabled`/`dapr.io/app-id` pod annotations, gated by
  `.Values.dapr.enabled`, matching the pattern already established for the admin Deployment.

## Impact

- `charts/dws/Chart.yaml`, `charts/dws/values.yaml` — new `redis` dependency and values block.
- `charts/dws/templates/controller/deployment.yaml` — new Dapr annotations.
- `charts/dws/templates/redis/` (new), `templates/pubsub-component.yaml` (new),
  `templates/definitions-component.yaml` (new), `templates/actor-statestore-component.yaml` (new).
- `charts/dws/templates/_helpers.tpl` — new Redis host/connection helpers, following the existing
  `dws.postgres.*` helper pattern.
- `.github/workflows/helm.yml` — extended `integration` job (or new job) for the pub/sub
  assertion.
- `dws-orchestrator/k8s/configuration-component.yaml` — retained as-is for non-chart deployments;
  no code change, only doc/roadmap notes.
- `docs/roadmaps/helm-packaging.md`, `docs/roadmaps/README.md` — status/doc updates only, no
  capability of their own.
- No changes to `dws-controller`, `dws-orchestrator`, `dws-call-http`, or `dws-call-openapi`
  application code — this is chart/CI wiring only, consuming event publishing and Component
  contracts that already exist.
