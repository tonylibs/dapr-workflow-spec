## 1. Controller Dapr annotations

- [ ] 1.1 Add `dapr.io/enabled`/`dapr.io/app-id` pod annotations to
      `charts/dws/templates/controller/deployment.yaml`, gated by `.Values.dapr.enabled`,
      mirroring `templates/admin/deployment.yaml`. No `dapr.io/app-port` and no second
      container port.
- [ ] 1.2 `helm template charts/dws` with default values and with `--set dapr.enabled=false`;
      confirm the controller pod template carries the annotations only in the first case, and
      never carries `dapr.io/app-port`.

## 2. Bitnami Redis dependency

- [ ] 2.1 Confirm the pinned Bitnami Redis chart version's actual auto-created Secret name/key
      and standalone Service hostname via `helm show values`/`helm template` against a temp
      release, so the helpers in 2.4 assume the right names (see design.md's Redis-naming risk).
- [ ] 2.2 Add `redis` as a conditional Bitnami subchart dependency (`condition: dapr.enabled` —
      not an independent `redis.enabled` toggle; Redis follows Dapr's lifecycle) to
      `charts/dws/Chart.yaml`, pinning a version consistent with the other Bitnami dependency
      (`postgresql`); run `helm dependency update charts/dws` to refresh `Chart.lock`.
- [ ] 2.3 Add a `redis:` block to `charts/dws/values.yaml`: `architecture: standalone`,
      `auth.password`, `master.persistence.size` (or the subchart's equivalent key),
      `networkPolicy.enabled: false`, and — if 2.1 shows it's needed —
      `image.repository: bitnamilegacy/redis` next to the existing
      `global.security.allowInsecureImages` comment. Add
      `redis.external.{host,existingSecret,existingSecretKey}` values for the external-Redis path
      (no `redis.enabled` field — presence of `redis.external.host` is the override signal).
- [ ] 2.4 Add `dws.redis.host`, `dws.redis.secretName`, and `dws.redis.secretKey` helpers to
      `charts/dws/templates/_helpers.tpl`, resolving to the in-chart Bitnami Redis Service/Secret
      when `redis.external.host` is unset and to `redis.external.*` when it is set (per
      design.md).
- [ ] 2.5 `helm lint charts/dws` and `helm template charts/dws` (default values, then
      `--set dapr.enabled=false`, then `--set redis.external.host=... --set
      redis.external.existingSecret=...` with `dapr.enabled=true`); confirm the Bitnami Redis
      subchart resources render only when `dapr.enabled=true`, and that the external-Redis case
      still installs the (now unused) built-in Redis alongside pointing Components at the
      external host — the documented trade-off in design.md.

## 3. Dapr Component templates

- [ ] 3.1 Add `charts/dws/templates/pubsub-component.yaml` — `dapr.io/v1alpha1` `Component`,
      type `pubsub.redis`, name `pubsub`, gated by `.Values.dapr.enabled` alone, using the
      `dws.redis.*` helpers for `redisHost`/`redisPassword` (`secretKeyRef`),
      `enableTLS: "false"`, `auth.secretStore: kubernetes` — shaped like
      `dws-orchestrator/k8s/configuration-component.yaml`.
- [ ] 3.2 Add `charts/dws/templates/definitions-component.yaml` — same shape, type
      `configuration.redis`, name `dws-definitions`.
- [ ] 3.3 Add `charts/dws/templates/actor-statestore-component.yaml` — same shape, type
      `state.redis`, with `actorStateStore: "true"` in `spec.metadata`.
- [ ] 3.4 `helm template charts/dws` with default values; confirm all three Components render
      with the expected `type`/`metadata.name`/`spec.metadata` entries. Re-run with
      `--set dapr.enabled=false`; confirm none of the three render.
- [ ] 3.5 `helm lint charts/dws`.

## 4. CI pub/sub assertion

- [ ] 4.1 Extend `.github/workflows/helm.yml`'s `integration` job (after the existing
      `helm test` step) with a step that confirms the `pubsub` Component reports Ready
      in-cluster (e.g. via `kubectl get component pubsub -n dws-system` /
      `dapr components -k`).
- [ ] 4.2 Add a minimal publish/subscribe check proving a message on topic `dws.events` is
      delivered through the `pubsub` Component (per design.md's "minimal publisher/subscriber"
      decision) — a throwaway Dapr-enabled pod pair or `dapr publish`/subscriber sidecar,
      scoped to transport only.
- [ ] 4.3 Update the failure-debug step to also dump the `pubsub` Component status and the new
      assertion's logs on failure, matching the existing debug-step pattern.
- [ ] 4.4 Confirm the `integration-dapr-preinstalled` job is unchanged (per
      `specs/helm-pubsub-integration-test`, it doesn't need the pub/sub assertion).

## 5. Verification

- [ ] 5.1 `helm dependency update charts/dws && helm lint charts/dws`.
- [ ] 5.2 `helm template charts/dws` — default values, `dapr.enabled=false`, `redis.external.host`
      set (with `dapr.enabled=true`), and `controller.enabled=false` — confirm no unexpected
      resources render or disappear outside what each toggle should affect.
- [ ] 5.3 Run (or trigger) the `.github/workflows/helm.yml` `verify` and `integration` jobs end
      to end against a kind cluster; confirm the new pub/sub assertion passes.

## 6. Docs

- [ ] 6.1 Update `docs/roadmaps/helm-packaging.md`: flip Phase 5's status (✅, or the accurate
      partial state if something was deliberately deferred), fill in the chart-layout tree
      entries for `redis/` (if a directory exists), `pubsub-component.yaml`,
      `definitions-component.yaml`, and `actor-statestore-component.yaml`, and update the "Open
      items" section (the Redis-replaces-the-hardcoded-Component note becomes stale).
- [ ] 6.2 Update the Helm row in `docs/roadmaps/README.md` to match.
