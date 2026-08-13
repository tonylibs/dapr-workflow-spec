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

- Dapr installs via the official `dapr/dapr` chart as a conditional dependency (`condition: dapr.enabled` in `Chart.yaml`).
- Knative Serving has no comparable official Helm chart, so it installs via a Helm hook Job that `kubectl apply`s the pinned CRD + core manifests (mirrors the existing pinned bundles in `dws-controller/k8s/{dapr-crds,serving-crds}.yaml`).
- A preflight check (`Capabilities.APIVersions.Has`, evaluated against the real cluster at `helm install`/`upgrade` time) fails the install fast if a prerequisite is toggled off but its CRDs aren't actually present — instead of deploying workloads against a missing control plane.

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
    ├── knative-install-job.yaml   # hook Job, toggle: knative.enabled
    └── _helpers.tpl
```

Controller and admin Deployments also gain `dapr.io/enabled`/`dapr.io/app-id` pod annotations in
Phase 5, once a Dapr control plane is actually present in the cluster (Phase 4) — they carry no
sidecar annotations before that.

## Phased roadmap

| Phase | Goal | Key tasks |
|---|---|---|
| 0. Prep | Confirm scope | Decide bundling vs. docs-only for prerequisites; confirm dws-admin/console are in-chart |
| 1. Scaffold | `helm create charts/dws`, strip unused boilerplate | Chart.yaml, values.yaml skeleton |
| 2. Controller | Port `dws-controller/k8s/*.yaml` to templates | Templatize namespace, RBAC, image refs (`DWS_IMAGES_*`) |
| 3. Admin + DB | Deployment/Service/Secret for dws-admin | Complete: Bitnami PostgreSQL subchart toggle, or external DSN via `admin.database.url`/`existingSecret` |
| 4. Prerequisites | Dapr as chart dependency; Knative via hook Job | `dapr.enabled`/`knative.enabled` toggles, preflight CRD check |
| 5. Event wiring | Wire the controller→admin pub/sub path chart-side | `pubsub-component.yaml` (Dapr Component, topic `dws.events`); `dapr.io/enabled`/`dapr.io/app-id` annotations on controller + admin Deployments; end-to-end test (apply a definition → assert it lands in admin's read model). Depends on Phase 4 (needs a real Dapr control plane in-cluster) |
| 6. Console | Add Deployment/Service/Ingress once an image exists | Currently blocked — no Dockerfile in `dws-console/` yet |
| 7. Values design | Finalize `values.yaml` | Per-component image/tag/resources/replicas; global namespace; ingress host |
| 8. Testing | `helm lint`, `helm template`, `ct install` on kind | `.github/workflows/helm.yml`, `ci/values-test.yaml` |
| 9. Publish | OCI or GH Pages chart repo | `ghcr.io/tonylibs/charts/dws`, versioned with app releases |
| 10. Docs | Update README + CLAUDE.md | Install/upgrade/uninstall commands, values reference table |

## Open items

- `dws-console` has no Dockerfile yet — Phase 6 is blocked until that lands upstream
  (see [`dws-console` roadmap](dws-console.md), Phase 6 — unrelated numbering, that doc's own phases).
- Built-in Postgres is dev/eval-grade (single replica, no backup) — production users should set `postgresql.enabled: false` and point `admin.database` at a managed instance.
- Knative install-via-Job needs a pinned release version (`knative.version`) kept in sync with the `serving-crds.yaml` bundle already checked into `dws-controller/k8s/`.
- Phase 5 (event wiring) can't be meaningfully tested until Phase 4 lands — no Dapr control plane
  means no sidecar, no pub/sub Component, nothing to assert against. Event *publishing* itself
  (`dws-controller`/`dws-orchestrator` → topic `dws.events`) already shipped outside this roadmap
  (see `docs/events.md`); Phase 5 is purely the chart-side wiring + a deployed-integration test for
  the already-existing capability.
