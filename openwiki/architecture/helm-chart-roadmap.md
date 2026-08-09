---
type: Deployment Guide
title: Helm chart packaging
description: How the charts/dws Helm application chart installs the DWS controller and administrative read model, how to configure its database, and how chart changes are verified and released.
tags: [dws, helm, kubernetes, controller, admin, postgresql]
---

# Helm chart packaging

`charts/dws` is the DWS application chart. It packages the persistent control-plane services—the `dws-controller` API and the `dws-admin` [administrative read model](../integrations/admin-read-model.md)—rather than the per-workflow runtime. The controller continues to create the pinned orchestrator and step services for each submitted definition; that lifecycle is described in [deployed workflow lifecycle](deployed-workflow.md).

The chart defaults to one controller replica, one admin replica, and an in-chart standalone PostgreSQL instance. It installs into the Helm release namespace unless `namespaceOverride` is set. The current chart metadata and values live in `charts/dws/Chart.yaml` and `charts/dws/values.yaml`.

## Installed components

| Values area | Default | What it controls |
|---|---:|---|
| `controller.enabled` | `true` | Controller Deployment, HTTP Service, ServiceAccount, Role, and RoleBinding. Its `DWS_IMAGES_*` environment variables select the images stamped into per-workflow resources. |
| `admin.enabled` | `true` | Admin Deployment and HTTP Service. The pod is Dapr sidecar-enabled and runs migrations on boot. |
| `postgresql.enabled` | `true` | Conditional Bitnami PostgreSQL chart dependency, configured as a single-node development/evaluation database for admin. |
| `imagePullSecrets` | `[]` | Pull credentials attached to component pods that use private registries. |

The controller Role permits only the resources it reconciles: definition ConfigMaps, orchestrator Deployments, Knative Services, and Dapr components. Knative Serving and Dapr CRDs/control planes are therefore cluster prerequisites for actual workflow deployment; they are **not** installed by this chart. A server-side Helm dry run can validate the controller's core/RBAC/app manifests without those CRDs, but a running controller requires them when it applies workflow stacks.

## Database choices

With `postgresql.enabled=true`, the admin Deployment receives its `DATABASE_URL` from the chart-managed Postgres secret. Set `postgresql.enabled=false` for an external or managed database, then provide either `admin.database.url` (which renders an admin-owned Secret) or `admin.database.existingSecret` and, optionally, `admin.database.existingSecretKey`. Do not place real connection strings in committed values files.

The bundled database is explicitly configured for dev/eval use: a standalone instance with a 1 GiB persistent volume and no NetworkPolicy. Treat production sizing, credentials, backup, and network policy as operator-owned configuration.

## Verification and release

`.github/workflows/helm.yml` runs on changes to `charts/**` or the workflow itself. Its verification job runs `helm lint`, renders default and overridden values, confirms controller resources disappear when `controller.enabled=false`, and performs `helm install --dry-run=server` against Kind. The dry run does not start pods, so it cannot prove admin-to-Postgres connectivity.

A dependent integration job installs Dapr into Kind, installs admin plus Postgres with the controller disabled, waits for both workloads, and runs `helm test`. It supplies a registry pull secret because the admin image is private in the CI environment. On pushes to `main`, the release job packages the chart and pushes it to `oci://ghcr.io/<repository-owner>/charts`; pull requests verify but do not publish.

For a local chart change, start with `helm lint charts/dws` and `helm template dws charts/dws`. When changing deployment templates, keep the controller image and `controller.images` values aligned with the runtime image contract in [deployed workflow lifecycle](deployed-workflow.md), then run the Helm workflow-equivalent checks where available.
