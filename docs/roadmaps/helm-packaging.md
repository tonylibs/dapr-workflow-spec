# Helm Chart Packaging Roadmap

Roadmap for packaging DWS as an installable Helm chart (`charts/dws`), so a cluster operator
can bring up the platform's control plane — `dws-controller`, `dws-admin` (+ its Postgres read
model) — with one `helm install`, optionally including Dapr and/or Knative Serving.

## Scope

Only the long-running platform components are chart-managed:

| Component | In chart? | Why |
|---|---|---|
| `dws-controller` | Yes | Persistent control-plane Deployment |
| `dws-admin` | Yes | Persistent read-model service, needs Postgres |
| Postgres (for `dws-admin`) | Yes, optional built-in | Bitnami PostgreSQL subchart by default; swappable for an external DB |
| Redis (backs Dapr Components: `dws-definitions` Configuration store today, actor/workflow state store once `dws-orchestrator` calls the Dapr Workflow runtime) | Yes, optional built-in — **not yet added** | Same pattern as Postgres: Bitnami Redis subchart by default (`redis.enabled`), swappable for external Redis. See Phase 5 |
| `dws-console` | Not yet | Image now exists upstream (`ghcr.io/tonylibs/dws-console`, since 2026-08-17) — chart templates not started yet; placeholder toggle only, disabled by default. See [`dws-console` roadmap](dws-console.md) |
| `dws-orchestrator` | No | Deployed dynamically, per-workflow, by the controller at runtime — not a static install target |
| `dws-call-http` / `dws-call-openapi` / `dws-run-*` | No | Same as above — controller stamps these out per workflow |
| Dapr | Optional dependency | User-toggleable — see below |
| Knative Serving | Optional dependency | User-toggleable — see below |
| Dex (in-chart IdP for `dws-console` login) | Yes, optional — **done**, toggle `dex.enabled` (default `false`) | Landed via the separate [`dws-auth` roadmap](dws-auth.md) (Phase 0), not tracked as a phase here — noted because it now ships inside this chart. No DWS service consumes its tokens yet |

## User-facing install options

Both cluster-wide prerequisites are offered, not assumed:

| `dapr.enabled` | `knative.enabled` | Result |
|---|---|---|
| true | true | Chart installs both control planes + DWS platform — single-command bootstrap |
| false | false | Chart installs only the DWS platform, assumes both pre-exist |
| true | false | Mixed — org already runs shared Knative |
| false | true | Mixed — org already runs shared Dapr |

- Dapr installs via the official `dapr/dapr` chart as a conditional dependency (`condition: dapr.enabled` in `Chart.yaml`). Ships in **Phase 4**.
- Knative Serving has no comparable official Helm chart, so it installs via a Helm hook Job that `kubectl apply`s the pinned CRD + core manifests (mirrors the existing pinned bundles in `dws-controller/k8s/{dapr-crds,serving-crds}.yaml`). Split out of Phase 4 and deferred to **Phase 11** (see below) — not on the critical path for Phase 5's event wiring, which only needs Dapr.
- A preflight check (`Capabilities.APIVersions.Has`, evaluated against the real cluster at `helm install`/`upgrade` time) fails the install fast if a prerequisite is toggled off but its CRDs aren't actually present — instead of deploying workloads against a missing control plane. Ships per-prerequisite: the Dapr check lands with Phase 4, the Knative check with Phase 11.

## Chart layout

```
charts/dws/
├── Chart.yaml                # dependencies: postgresql, dapr, redis, dex (all conditional;
│                             # redis follows dapr.enabled — no independent toggle)
├── values.yaml
└── templates/
    ├── controller/           # serviceaccount, rbac (role+rolebinding), deployment, service
    ├── admin/                # deployment, service, secret (db conn resolution)
    ├── postgres/             # chart-owned admin DSN Secret — toggle: postgresql.enabled
    ├── pubsub-component.yaml # done, Phase 5 — Dapr Component "pubsub" (pubsub.redis),
    │                         # topic dws.events, toggle: dapr.enabled
    ├── definitions-component.yaml  # done, Phase 5 — Dapr Component "dws-definitions"
    │                               # (configuration.redis); chart-managed replacement for the
    │                               # hand-applied dws-orchestrator/k8s/configuration-component.yaml
    │                               # (kept in that repo for non-chart deployments), toggle: dapr.enabled
    ├── actor-statestore-component.yaml  # done, Phase 5 — Dapr Component (state.redis,
    │                                    # actorStateStore: "true"); provisioned ahead of
    │                                    # dws-orchestrator adopting the Dapr Workflow runtime,
    │                                    # toggle: dapr.enabled
    ├── console/              # empty — disabled by default; dws-console image now exists
    │                         # (unblocked), Deployment/Service/Ingress templates not started
    ├── dex/secrets.yaml      # done, dws-auth Phase 0 — chart-managed bootstrap-admin Secret
    │                         # (generated password + bcrypt hash for Dex's staticPasswords),
    │                         # toggle: dex.enabled (default false); see dws-auth.md
    ├── knative-install-job.yaml   # hook Job, toggle: knative.enabled (Phase 11)
    ├── dapr-ready-hook.yaml  # done, Phase 4 — post-install/upgrade Job, self-heals a missed
    │                         # Dapr sidecar injection on the admin Pod, toggle: dapr.enabled
    ├── preflight.yaml + _preflight.tpl  # done, Phase 4 — fails install/upgrade fast if
    │                                    # dapr.enabled=false but Dapr CRDs aren't present
    ├── tests/admin-db-connection.yaml   # done, Phase 8 — `helm test` DB connectivity check
    └── _helpers.tpl          # includes dws.redis.host / secretName / secretKey — resolve to
                              # in-chart Bitnami Redis by default, or redis.external.* when set
```

Both the admin and controller Deployments carry `dapr.io/enabled`/`dapr.io/app-id` pod
annotations. Admin's are gated behind `dapr.enabled` (Phase 4) because it also renders
`DAPR_PUBSUB_*` env vars tied to the same toggle. Controller's are rendered unconditionally
(Phase 5) — the annotations are inert without a sidecar-injector, and always-on keeps the
controller ready to publish the moment a Dapr control plane appears (chart-installed or
external). The controller intentionally has no `dapr.io/app-port` — it only publishes outbound
via its Dapr sidecar and never receives Dapr-routed inbound traffic.

## Phased roadmap

Status legend: ✅ done · ⚠️ partial/stubbed · ❌ not started. Updated 2026-08-24 — phases 0–11
unchanged since 2026-08-19; this pass only reconciles Dex, which landed in the chart via the
separate `dws-auth` roadmap and wasn't reflected here before.

| Phase | Status | Goal | Key tasks |
|---|---|---|---|
| 0. Prep | ✅ | Confirm scope | Bundling vs. docs-only for prerequisites decided; dws-admin/console-placeholder confirmed in-chart |
| 1. Scaffold | ✅ | `helm create charts/dws`, strip unused boilerplate | Chart.yaml, values.yaml skeleton; leftover `helm create` boilerplate removed |
| 2. Controller | ✅ | Port `dws-controller/k8s/*.yaml` to templates | serviceaccount, rbac, deployment, service — namespace/RBAC/`DWS_IMAGES_*` templatized |
| 3. Admin + DB | ✅ | Deployment/Service/Secret for dws-admin | Bitnami PostgreSQL subchart toggle (`postgresql.enabled`) done; external DSN via `admin.database.url`/`existingSecret` done |
| 4. Dapr prerequisite | ✅ | Dapr as chart dependency | `Chart.yaml` declares `dapr/dapr` as a conditional dependency (`condition: dapr.enabled`); `values.yaml` exposes `dapr.enabled` (default `true`). The admin Deployment's `dapr.io/enabled`/`dapr.io/app-id` annotations and `DAPR_PUBSUB_NAME`/`DAPR_PUBSUB_TOPIC`/`DAPR_APP_PORT` env vars are now gated behind `.Values.dapr.enabled`. A preflight check (`templates/_preflight.tpl`, `Capabilities.APIVersions.Has "dapr.io/v1alpha1"`) fails `helm install`/`upgrade` fast when `dapr.enabled=false` but Dapr CRDs aren't present. CI's `integration` job now installs Dapr via the chart on the default `dapr.enabled=true` leg, and runs a second matrix leg with `dapr.enabled=false` against a pre-installed Dapr to prove both toggle states. **Knative stays split out — see Phase 11** |
| 5. Event wiring | ✅ | Wire Dapr Components chart-side: pub/sub (controller→admin) **and Redis-backed Components** | Controller Deployment now carries `dapr.io/enabled`/`dapr.io/app-id` unconditionally (no `dapr.enabled` gate — annotations are inert without a Dapr sidecar-injector, and always-on keeps the controller ready when Dapr later appears; no `dapr.io/app-port` — controller only publishes outbound). Chart.yaml pulls in the Bitnami Redis subchart under `condition: dapr.enabled` (Redis has no independent enable toggle — it exists solely to back the three Dapr Redis Components, so its lifecycle follows Dapr's; external Redis is expressed via `redis.external.host`, which overrides the Components' `redisHost`/`redisPassword` while the built-in subchart still installs alongside — a documented trade-off). Three chart-managed Dapr Component templates render whenever `dapr.enabled`: `pubsub-component.yaml` (`pubsub.redis`, topic `dws.events`), `definitions-component.yaml` (`configuration.redis`, replacing the hand-applied `dws-orchestrator/k8s/configuration-component.yaml` which stays in that repo for non-chart deployments), and `actor-statestore-component.yaml` (`state.redis`, `actorStateStore: "true"`, ahead of `dws-orchestrator` adopting the Dapr Workflow runtime). CI's `integration` job now waits for the Redis rollout, asserts all three Components are present, and runs an end-to-end pub/sub delivery check (a Dapr-enabled publisher/subscriber Pod pair with a `Subscription` CRD on topic `dws.events`) — transport-only, no controller/orchestrator/admin round-trip. Depends only on Phase 4 (Dapr, done) — not on Phase 11 (Knative) |
| 6. Console | ❌ | Add Deployment/Service/Ingress once an image exists | Unblocked as of 2026-08-17 — `dws-console` shipped a Dockerfile + CI image build (`ghcr.io/tonylibs/dws-console`); `templates/console/` itself is still empty, not started |
| 7. Values design | ⚠️ | Finalize `values.yaml` | Controller/admin/postgresql image/tag/resources/replicas + global namespace done; no ingress or console values yet (blocked on Phase 6) |
| 8. Testing | ✅ | `helm lint`, `helm template`, install test on kind | `.github/workflows/helm.yml`: lint + template (default/disabled/overridden) + kind server-dry-run in `verify`; a real kind install of admin+postgres+Dapr with `helm test` in `integration` (a hand-rolled kind pipeline instead of the `ct` tool, but covers the same ground) |
| 9. Publish | ✅ | OCI chart repo | `release` job in `helm.yml` packages and pushes to `oci://ghcr.io/tonylibs/charts` on merge to `main` |
| 10. Docs | ❌ | Update README + CLAUDE.md | Not started — no helm install/upgrade/uninstall commands or values reference table in either file yet |
| 11. Knative prerequisite | ❌ | Knative Serving via hook Job | Not started — split out of the original combined "Phase 4: Prerequisites" so Dapr (needed by Phase 5) isn't blocked on Knative design work. `knative-install-job.yaml` (post-install/post-upgrade hook Job), `knative.enabled`/`knative.version` values, preflight CRD check for Knative. Independent of every other phase — can land whenever, in parallel with anything above |

## Open items

- **Dex landed but isn't a phase here.** `dex.enabled` (default `false`), the upstream `dexidp`
  chart dependency, and the chart-managed bootstrap-admin Secret (`templates/dex/secrets.yaml`)
  are done — tracked as Phase 0 of [`dws-auth.md`](dws-auth.md), which also covers the console
  OIDC login (Phase 1, done) and the still-unstarted Dapr bearer-auth phases (2–7) that would let
  any DWS service actually consume Dex's tokens. This roadmap only notes Dex's presence in the
  chart layout/scope above; its own progress lives in `dws-auth.md`.
- `dws-console` now has a Dockerfile and a CI-built image (`ghcr.io/tonylibs/dws-console`, merged
  2026-08-17) — Phase 6 (and the ingress part of Phase 7) is unblocked; the `templates/console/`
  manifests and console `values.yaml` entries just haven't been written yet (see
  [`dws-console` roadmap](dws-console.md), Phase 6 — unrelated numbering, that doc's own phases).
- Built-in Postgres is dev/eval-grade (single replica, no backup) — production users should set `postgresql.enabled: false` and point `admin.database` at a managed instance. Same caveat will apply to the built-in Redis once it exists (`redis.enabled: false` for production, point at a managed instance).
- `dws-orchestrator/k8s/configuration-component.yaml` — the hand-applied Redis-backed `dws-definitions` Component from before this chart existed — is intentionally kept in that repo for non-chart / local deployments. The chart's `templates/definitions-component.yaml` is the equivalent for chart-managed installs; the two describe the same Component shape against potentially different Redis hosts, with no shared templating.
- **External Redis when Dapr is enabled**: setting `redis.external.host` retargets the three Dapr Component templates but does not disable the in-chart Bitnami Redis subchart (Helm dependency `condition:` fields can't AND two values). An operator running production against a managed Redis therefore still gets an unused in-chart Redis instance unless they separately trim its footprint; documented, accepted trade-off.
- Knative install-via-Job (Phase 11) needs a pinned release version (`knative.version`) kept in sync with the `serving-crds.yaml` bundle already checked into `dws-controller/k8s/`. Not started; deliberately deprioritized behind Dapr since nothing else in the roadmap currently depends on it.
- **Next up:** Phase 6 (console — now unblocked, image exists; Deployment/Service/Ingress templates and values not started), Phase 10 (docs — README/CLAUDE.md `helm install` reference), or Phase 11 (Knative — independent, on its own timeline). Phase 5 completed; the actor-statestore Component ships ready but unused until `dws-orchestrator` adopts the Dapr Workflow runtime (tracked separately, not on this roadmap).
