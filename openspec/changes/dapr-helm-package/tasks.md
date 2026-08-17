## 1. Chart dependency (`charts/dws/Chart.yaml`, `values.yaml`)

- [ ] 1.1 Add `dapr` to `dependencies:` in `charts/dws/Chart.yaml` — `repository: https://dapr.github.io/helm-charts/`, `condition: dapr.enabled`, pinned to a specific released `dapr/dapr` chart version (mirror the `postgresql` entry's shape).
- [ ] 1.2 Add `dapr.enabled: true` to `charts/dws/values.yaml`, documented with the same comment style as `postgresql.enabled`.
- [ ] 1.3 Run `helm dependency update charts/dws` to fetch the new subchart and regenerate `Chart.lock`.
- [ ] 1.4 Validate: `helm lint charts/dws` and `helm template dws charts/dws | grep -q 'app.kubernetes.io/name: dapr'` (Dapr resources present by default).
- [ ] 1.5 Validate: `helm template dws charts/dws --set dapr.enabled=false` renders no Dapr subchart resources.

## 2. Gate admin Deployment's Dapr annotations/env vars (`charts/dws/templates/admin/deployment.yaml`)

- [ ] 2.1 Wrap `dapr.io/enabled`/`dapr.io/app-id` pod annotations in `{{- if .Values.dapr.enabled }}...{{- end }}`.
- [ ] 2.2 Wrap `DAPR_PUBSUB_NAME`/`DAPR_PUBSUB_TOPIC`/`DAPR_APP_PORT` env vars in the same conditional.
- [ ] 2.3 Leave `templates/controller/deployment.yaml` untouched (Phase 5 scope).
- [ ] 2.4 Update `openspec/specs/helm-admin-deployment/spec.md`'s "Admin environment and health probes" requirement per this change's delta (handled at archive time — no action needed here beyond the delta already written in `specs/helm-admin-deployment/spec.md`).
- [ ] 2.5 Validate: `helm template dws charts/dws` (default) shows the admin Deployment with the Dapr annotations/env vars present; `helm template dws charts/dws --set dapr.enabled=false` shows the admin Deployment with none of them present.

## 3. Preflight check for Dapr presence

- [ ] 3.1 Add a preflight template (e.g. `charts/dws/templates/_preflight.tpl` or a block in `_helpers.tpl`) that, when `dapr.enabled=false`, checks `.Capabilities.APIVersions.Has "dapr.io/v1alpha1"` and calls `fail` with an actionable message if absent.
- [ ] 3.2 Wire the preflight template so it's evaluated unconditionally at render time (e.g. included from a resource every install/upgrade renders, such as `_helpers.tpl`'s common labels or a lightweight standalone template).
- [ ] 3.3 Validate: `helm template dws charts/dws --set dapr.enabled=false` against local Helm (no live cluster, so `.Capabilities.APIVersions` is the client's default empty/base set) fails with the expected error message — confirms the check fires when the API group truly isn't present.
- [ ] 3.4 Validate (kind, live cluster): `helm install`/`upgrade` with `dapr.enabled=false` succeeds when Dapr CRDs are pre-installed in the cluster, and fails fast (before any workload is created) when they are not.

## 4. CI (`.github/workflows/helm.yml`)

- [ ] 4.1 In the `integration` job, remove the manual "Install Dapr control plane" step (`dapr init -k --wait`) — Dapr now installs via the chart's own dependency on the default `dapr.enabled=true` path.
- [ ] 4.2 Add a second integration case (matrix entry or additional job step sequence) that keeps a `dapr init -k`-equivalent pre-install, then installs the chart with `--set dapr.enabled=false`, and asserts the release still rolls out successfully against the externally-managed Dapr.
- [ ] 4.3 Keep the existing `kubectl rollout status` / `helm test` assertions in both cases so real-rollout coverage isn't weakened.
- [ ] 4.4 Validate: push the branch and confirm both `verify` and `integration` (both cases) jobs pass in `helm-chart` Actions workflow.

## 5. Docs (`docs/roadmaps/helm-packaging.md`)

- [ ] 5.1 Update the Phase 4 row status to ✅ with a summary of what shipped.
- [ ] 5.2 Update the Phase 5 row / "Open items" note to record that the `pubsub-component.yaml` / controller-annotation work is now unblocked by Phase 4 landing.
