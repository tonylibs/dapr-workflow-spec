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
| Postgres (for `dws-admin`) | Yes, optional built-in | Minimal in-chart StatefulSet by default; swappable for an external DB |
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
    ├── postgres/             # statefulset, headless service, secret — toggle: postgresql.enabled
    ├── console/              # deployment, service, ingress — disabled by default
    ├── knative-install-job.yaml   # hook Job, toggle: knative.enabled
    └── _helpers.tpl
```

## Phased roadmap

| Phase | Goal | Key tasks |
|---|---|---|
| 0. Prep | Confirm scope | Decide bundling vs. docs-only for prerequisites; confirm dws-admin/console are in-chart |
| 1. Scaffold | `helm create charts/dws`, strip unused boilerplate | Chart.yaml, values.yaml skeleton |
| 2. Controller | Port `dws-controller/k8s/*.yaml` to templates | Templatize namespace, RBAC, image refs (`DWS_IMAGES_*`) |
| 3. Admin + DB | Deployment/Service/Secret for dws-admin | Built-in Postgres StatefulSet toggle, or external DSN via `admin.database.url`/`existingSecret` |
| 4. Prerequisites | Dapr as chart dependency; Knative via hook Job | `dapr.enabled`/`knative.enabled` toggles, preflight CRD check |
| 5. Console | Add Deployment/Service/Ingress once an image exists | Currently blocked — no Dockerfile in `dws-console/` yet |
| 6. Values design | Finalize `values.yaml` | Per-component image/tag/resources/replicas; global namespace; ingress host |
| 7. Testing | `helm lint`, `helm template`, `ct install` on kind | `.github/workflows/helm.yml`, `ci/values-test.yaml` |
| 8. Publish | OCI or GH Pages chart repo | `ghcr.io/tonylibs/charts/dws`, versioned with app releases |
| 9. Docs | Update README + CLAUDE.md | Install/upgrade/uninstall commands, values reference table |

## Open items

- `dws-console` has no Dockerfile yet — Phase 5 is blocked until that lands upstream
  (see [`dws-console` roadmap](dws-console.md), Phase 6).
- Built-in Postgres is dev/eval-grade (single replica, no backup) — production users should set `postgresql.enabled: false` and point `admin.database` at a managed instance.
- Knative install-via-Job needs a pinned release version (`knative.version`) kept in sync with the `serving-crds.yaml` bundle already checked into `dws-controller/k8s/`.
