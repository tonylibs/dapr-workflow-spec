---
type: Deployment Guide
title: Helm chart packaging
description: How the charts/dws Helm application chart installs DWS control-plane services and optional infrastructure, including the disabled-by-default Dex identity provider, and how chart changes are verified and released.
tags: [dws, helm, kubernetes, controller, admin, postgresql, dapr, redis, dex, authentication]
---

# Helm chart packaging

`charts/dws` is the DWS application chart. It packages the persistent control-plane services—the `dws-controller` API and the `dws-admin` [administrative read model](../integrations/admin-read-model.md)—plus conditional infrastructure dependencies, rather than the per-workflow runtime. The controller continues to create the pinned orchestrator and step services for each submitted definition; that lifecycle is described in [deployed workflow lifecycle](deployed-workflow.md).

The chart defaults to one controller replica, one admin replica, an in-chart standalone PostgreSQL instance, Dapr and its Redis backing services. It installs into the Helm release namespace unless `namespaceOverride` is set. Dex is available as a disabled-by-default optional in-chart identity provider; it is groundwork for the future console-authentication flow, not an authentication integration for any DWS service today. The current chart metadata and values live in `charts/dws/Chart.yaml` and `charts/dws/values.yaml`.

## Installed components

| Values area | Default | What it controls |
|---|---:|---|
| `controller.enabled` | `true` | Controller Deployment, HTTP Service, ServiceAccount, Role, and RoleBinding. Its `DWS_IMAGES_*` environment variables select the images stamped into per-workflow resources. |
| `admin.enabled` | `true` | Admin Deployment and HTTP Service. The pod is Dapr sidecar-enabled and runs migrations on boot. |
| `postgresql.enabled` | `true` | Conditional Bitnami PostgreSQL chart dependency, configured as a single-node development/evaluation database for admin. |
| `dapr.enabled` | `true` | Conditional Dapr control plane and its Redis-backed components. Disable only when a Dapr installation already exists in the cluster; the chart preflight validates that prerequisite. |
| `dex.enabled` | `false` | Conditional upstream Dex dependency. It registers `dws-console` as a public PKCE client and seeds one bootstrap administrator, but no current DWS component consumes its tokens. |
| `imagePullSecrets` | `[]` | Pull credentials attached to component pods that use private registries. |

The controller Role permits only the resources it reconciles: definition ConfigMaps, orchestrator Deployments, Knative Services, and Dapr components. Knative Serving and Dapr CRDs/control planes are therefore cluster prerequisites for actual workflow deployment; they are **not** installed by this chart. A server-side Helm dry run can validate the controller's core/RBAC/app manifests without those CRDs, but a running controller requires them when it applies workflow stacks.

## Database choices

With `postgresql.enabled=true`, the admin Deployment receives its `DATABASE_URL` from the chart-managed Postgres secret. Set `postgresql.enabled=false` for an external or managed database, then provide either `admin.database.url` (which renders an admin-owned Secret) or `admin.database.existingSecret` and, optionally, `admin.database.existingSecretKey`. Do not place real connection strings in committed values files.

The bundled database is explicitly configured for dev/eval use: a standalone instance with a 1 GiB persistent volume and no NetworkPolicy. Treat production sizing, credentials, backup, and network policy as operator-owned configuration.

## Optional Dex identity provider

Set `dex.enabled=true` to install the chart's upstream Dex dependency. `dex.issuer` must be an issuer URL reachable by the browser console and eventual token validators. `dex.consoleRedirectURI` configures the public PKCE client's **root** redirect URI (default `http://localhost:3000/` for `pnpm dev`); the chart derives Dex's browser CORS allowlist from its origin. This deployed-provider agreement is implemented by [console OIDC login](console-auth.md). The defaults are suitable only for in-cluster/local testing. Dex uses in-memory storage and a static password database, so this is a bootstrap/development-oriented identity-provider configuration, not a production identity lifecycle.

On a first live install, the chart generates a 20-character bootstrap-admin password, stores the administrator email and plaintext password in a chart-managed Kubernetes Secret, and puts only its bcrypt hash in Dex's rendered configuration Secret. A Helm upgrade reuses the stored password. Instead, operators can set `dex.adminUser.existingSecret` and `dex.adminUser.existingSecretKey` to source the password from an existing Secret; neither path puts a password in `values.yaml`. When Dex is enabled, Helm `NOTES.txt` prints commands to retrieve the bootstrap login. Treat the password and rendered Secrets as sensitive operational data.

Dex now supplies the optional console's browser login, but it remains isolated from DWS request authorization: the console's existing reads stay unauthenticated, it does not yet attach a bearer token, and the controller and admin do not yet validate them. The planned ordering is Dapr-sidecar bearer enforcement, an admin write relay/gateway, then console submission; the repository records that dependency sequence in `docs/roadmaps/dws-auth.md`. Do not claim that enabling Dex protects reads or workflow writes.

## Verification and release

`.github/workflows/helm.yml` runs on changes to `charts/**` or the workflow itself. Its verification job runs `helm lint`, renders default and overridden values, confirms controller resources disappear when `controller.enabled=false`, and performs `helm install --dry-run=server` against Kind. The dry run does not start pods, so it cannot prove admin-to-Postgres connectivity.

A dependent integration job installs Dapr into Kind, installs admin plus Postgres with the controller disabled, waits for both workloads, and runs `helm test`. It supplies a registry pull secret because the admin image is private in the CI environment. On pushes to `main`, the release job packages the chart and pushes it to `oci://ghcr.io/<repository-owner>/charts`; pull requests verify but do not publish.

For a local chart change, start with `helm lint charts/dws` and `helm template dws charts/dws`. When changing deployment templates, keep the controller image and `controller.images` values aligned with the runtime image contract in [deployed workflow lifecycle](deployed-workflow.md), then run the Helm workflow-equivalent checks where available.
