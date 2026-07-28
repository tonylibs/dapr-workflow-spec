---
type: Roadmap
title: Helm chart packaging roadmap
description: Roadmap for packaging DWS as an installable Helm chart (charts/dws), with Dapr and Knative Serving offered as optional, user-toggleable dependencies.
tags: [dws, dapr, knative, kubernetes, helm, roadmap]
---

# Helm chart packaging roadmap

The canonical source for this roadmap is `docs/helm-chart-roadmap.md`; this page summarizes it for OpenWiki navigation. Update `docs/helm-chart-roadmap.md` first, then this page.

Today only `dws-controller` is installed statically (`dws-controller/k8s/*.yaml`, applied by hand). This roadmap packages the platform's control plane — `dws-controller` and `dws-admin` (+ Postgres) — into one chart, `charts/dws`, that a cluster operator installs with `helm install`.

## Scope

`dws-orchestrator` and the `dws-call-*`/`dws-run-*` step images are **not** chart-managed — the controller deploys those dynamically, per workflow, at runtime (see [deployed workflow lifecycle](deployed-workflow.md)). The chart only covers the persistent platform services:

| Component | In chart? |
|---|---|
| `dws-controller` | Yes |
| `dws-admin` + Postgres | Yes (Postgres built-in by default, swappable for external) |
| `dws-console` | Not yet — no Dockerfile upstream; disabled placeholder toggle |
| Dapr | Optional dependency, `dapr.enabled` |
| Knative Serving | Optional dependency, `knative.enabled` |

## Prerequisite toggles

Both cluster-wide control planes are offered as part of the install, not assumed present:

| `dapr.enabled` | `knative.enabled` | Result |
|---|---|---|
| true | true | Chart installs both control planes + DWS platform |
| false | false | Chart installs only the DWS platform |
| true | false | Mixed — Knative already shared cluster-wide |
| false | true | Mixed — Dapr already shared cluster-wide |

Dapr installs via the official `dapr/dapr` chart as a conditional `Chart.yaml` dependency. Knative has no comparable official chart, so it installs via a Helm hook Job that applies pinned CRD + core manifests. A preflight check fails the install fast if a toggle is off but the corresponding CRDs aren't actually in the cluster.

## Phased roadmap

| Phase | Scope |
|---|---|
| 0 | Confirm scope: bundling vs. docs-only prerequisites; confirm dws-admin/console are in-chart |
| 1 | Scaffold chart (`helm create`), strip unused boilerplate |
| 2 | Port `dws-controller/k8s/*.yaml` to templates |
| 3 | Admin Deployment/Service/Secret + built-in Postgres StatefulSet |
| 4 | Dapr as chart dependency; Knative via hook Job; preflight CRD check |
| 5 | Console templates — blocked on an upstream Dockerfile |
| 6 | Finalize `values.yaml` |
| 7 | `helm lint`/`helm template`/`ct install` CI |
| 8 | Publish chart (OCI/GH Pages) |
| 9 | Docs update |

Full detail, chart layout, and open items: `docs/helm-chart-roadmap.md`.
