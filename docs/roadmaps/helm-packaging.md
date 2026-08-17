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
| `dws-console` | Not yet | No Dockerfile/image exists upstream yet — placeholder toggle only, disabled by default. See [`dws-console` roadmap](dws-console.md) |
| `dws-orchestrator` | No | Deployed dynamically, per-workflow, by the controller at runtime — not a static install target |
| `dws-call-http` / `dws-call-openapi` / `dws-run-*` | No | Same as above — controller stamps these out per workflow |
| Dapr | Optional dependency | User-toggleable — see below |
| Knative Serving | Optional dependency | User-toggleable — see below |

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
├── Chart.yaml                # dependency: dapr (conditional on dapr.enabled)
├── values.yaml
└── templates/
    ├── controller/           # serviceaccount, rbac (role+rolebinding), deployment, service
    ├── admin/                # deployment, service, secret (db conn resolution)
    ├── postgres/             # chart-owned admin DSN Secret — toggle: postgresql.enabled
    ├── pubsub-component.yaml # Dapr Component "pubsub"/topic dws.events — toggle: dapr.enabled (Phase 5)
    ├── console/              # deployment, service, ingress — disabled by default
    ├── knative-install-job.yaml   # hook Job, toggle: knative.enabled (Phase 11)
    └── _helpers.tpl
```

The admin Deployment carries `dapr.io/enabled`/`dapr.io/app-id` pod annotations, now gated
behind `dapr.enabled` (Phase 4, done). The controller Deployment does not have them yet, and
there's still no `pubsub-component.yaml` Dapr Component template — both are Phase 5 scope.

## Phased roadmap

Status legend: ✅ done · ⚠️ partial/stubbed · ❌ not started. Updated 2026-08-17.

| Phase | Status | Goal | Key tasks |
|---|---|---|---|
| 0. Prep | ✅ | Confirm scope | Bundling vs. docs-only for prerequisites decided; dws-admin/console-placeholder confirmed in-chart |
| 1. Scaffold | ✅ | `helm create charts/dws`, strip unused boilerplate | Chart.yaml, values.yaml skeleton; leftover `helm create` boilerplate removed |
| 2. Controller | ✅ | Port `dws-controller/k8s/*.yaml` to templates | serviceaccount, rbac, deployment, service — namespace/RBAC/`DWS_IMAGES_*` templatized |
| 3. Admin + DB | ✅ | Deployment/Service/Secret for dws-admin | Bitnami PostgreSQL subchart toggle (`postgresql.enabled`) done; external DSN via `admin.database.url`/`existingSecret` done |
| 4. Dapr prerequisite | ✅ | Dapr as chart dependency | `Chart.yaml` declares `dapr/dapr` as a conditional dependency (`condition: dapr.enabled`); `values.yaml` exposes `dapr.enabled` (default `true`). The admin Deployment's `dapr.io/enabled`/`dapr.io/app-id` annotations and `DAPR_PUBSUB_NAME`/`DAPR_PUBSUB_TOPIC`/`DAPR_APP_PORT` env vars are now gated behind `.Values.dapr.enabled`. A preflight check (`templates/_preflight.tpl`, `Capabilities.APIVersions.Has "dapr.io/v1alpha1"`) fails `helm install`/`upgrade` fast when `dapr.enabled=false` but Dapr CRDs aren't present. CI's `integration` job now installs Dapr via the chart on the default `dapr.enabled=true` leg, and runs a second matrix leg with `dapr.enabled=false` against a pre-installed Dapr to prove both toggle states. **Knative stays split out — see Phase 11** |
| 5. Event wiring | ⚠️ | Wire the controller→admin pub/sub path chart-side | Admin Deployment's `dapr.io/enabled`/`dapr.io/app-id` annotations + `DAPR_PUBSUB_NAME`/`DAPR_PUBSUB_TOPIC` env vars are now gated behind `dapr.enabled` (Phase 4, done). Still missing: controller-side dapr annotations, a `pubsub-component.yaml` Dapr Component template, and an end-to-end test that asserts a submitted definition actually lands in admin's read model via pub/sub (today's `helm test` only checks DB connectivity). Phase 4 landing unblocks this work — no remaining dependency |
| 6. Console | ❌ | Add Deployment/Service/Ingress once an image exists | Still blocked — `templates/console/` is empty, no Dockerfile in `dws-console/` yet |
| 7. Values design | ⚠️ | Finalize `values.yaml` | Controller/admin/postgresql image/tag/resources/replicas + global namespace done; no ingress or console values yet (blocked on Phase 6) |
| 8. Testing | ✅ | `helm lint`, `helm template`, install test on kind | `.github/workflows/helm.yml`: lint + template (default/disabled/overridden) + kind server-dry-run in `verify`; a real kind install of admin+postgres+Dapr with `helm test` in `integration` (a hand-rolled kind pipeline instead of the `ct` tool, but covers the same ground) |
| 9. Publish | ✅ | OCI chart repo | `release` job in `helm.yml` packages and pushes to `oci://ghcr.io/tonylibs/charts` on merge to `main` |
| 10. Docs | ❌ | Update README + CLAUDE.md | Not started — no helm install/upgrade/uninstall commands or values reference table in either file yet |
| 11. Knative prerequisite | ❌ | Knative Serving via hook Job | Not started — split out of the original combined "Phase 4: Prerequisites" so Dapr (needed by Phase 5) isn't blocked on Knative design work. `knative-install-job.yaml` (post-install/post-upgrade hook Job), `knative.enabled`/`knative.version` values, preflight CRD check for Knative. Independent of every other phase — can land whenever, in parallel with anything above |

## Open items

- `dws-console` has no Dockerfile yet — Phase 6 (and the ingress part of Phase 7) is blocked until
  that lands upstream (see [`dws-console` roadmap](dws-console.md), Phase 6 — unrelated numbering,
  that doc's own phases).
- Built-in Postgres is dev/eval-grade (single replica, no backup) — production users should set `postgresql.enabled: false` and point `admin.database` at a managed instance.
- Knative install-via-Job (Phase 11) needs a pinned release version (`knative.version`) kept in sync with the `serving-crds.yaml` bundle already checked into `dws-controller/k8s/`. Not started; deliberately deprioritized behind Dapr since nothing else in the roadmap currently depends on it.
- Phase 4 landed a real in-chart Dapr control plane (`dapr.enabled` toggle + preflight check), so
  Phase 5's remaining piece (the `pubsub-component.yaml` Component + a real end-to-end assertion)
  is now unblocked — no outstanding dependency. Event *publishing* itself
  (`dws-controller`/`dws-orchestrator` → topic `dws.events`) already shipped outside this roadmap
  (see `docs/events.md`).
- **Next up:** Phase 5 (controller-side dapr annotations, `pubsub-component.yaml` Dapr Component
  template, end-to-end pub/sub assertion) — now unblocked by Phase 4. Phase 11 (Knative) has no
  dependents and can be picked up independently, on its own timeline.
