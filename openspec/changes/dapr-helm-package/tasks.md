## 1. Chart dependency (`charts/dws/Chart.yaml`, `values.yaml`)

- [x] 1.1 Add `dapr` to `dependencies:` in `charts/dws/Chart.yaml` — `repository: https://dapr.github.io/helm-charts/`, `condition: dapr.enabled`, pinned to a specific released `dapr/dapr` chart version (mirror the `postgresql` entry's shape).
- [x] 1.2 Add `dapr.enabled: true` to `charts/dws/values.yaml`, documented with the same comment style as `postgresql.enabled`.
- [ ] 1.3 BLOCKED (sandbox network policy denies `dapr.github.io`): run `helm dependency update charts/dws` to fetch the new subchart and regenerate `Chart.lock`/vendor `charts/dws/charts/dapr-1.15.4.tgz` (this repo checks in dependency tarballs, see `postgresql-16.7.27.tgz`).
- [ ] 1.4 BLOCKED on 1.3 — same network restriction (`helm lint`/`helm template` both refuse to run: "found in Chart.yaml, but missing in charts/ directory: dapr").
- [ ] 1.5 BLOCKED on 1.3 — same network restriction.

## 2. Gate admin Deployment's Dapr annotations/env vars (`charts/dws/templates/admin/deployment.yaml`)

- [x] 2.1 Wrap `dapr.io/enabled`/`dapr.io/app-id` pod annotations in `{{- if .Values.dapr.enabled }}...{{- end }}`.
- [x] 2.2 Wrap `DAPR_PUBSUB_NAME`/`DAPR_PUBSUB_TOPIC`/`DAPR_APP_PORT` env vars in the same conditional.
- [x] 2.3 Leave `templates/controller/deployment.yaml` untouched (Phase 5 scope).
- [x] 2.4 Update `openspec/specs/helm-admin-deployment/spec.md`'s "Admin environment and health probes" requirement per this change's delta (handled at archive time — no action needed here beyond the delta already written in `specs/helm-admin-deployment/spec.md`).
- [ ] 2.5 BLOCKED on 1.3 — same network restriction; needs a renderable chart to verify.

## 3. Preflight check for Dapr presence

- [x] 3.1 Add a preflight template (e.g. `charts/dws/templates/_preflight.tpl` or a block in `_helpers.tpl`) that, when `dapr.enabled=false`, checks `.Capabilities.APIVersions.Has "dapr.io/v1alpha1"` and calls `fail` with an actionable message if absent.
- [x] 3.2 Wire the preflight template so it's evaluated unconditionally at render time (e.g. included from a resource every install/upgrade renders, such as `_helpers.tpl`'s common labels or a lightweight standalone template).
- [ ] 3.3 BLOCKED on 1.3 — same network restriction; needs a renderable chart to verify.
- [ ] 3.4 BLOCKED — needs a live kind cluster + real Dapr install; not runnable in this sandbox (no outbound access to fetch Dapr/kind images either). Exercised instead via CI's `integration` job (task 4.2).

## 4. CI (`.github/workflows/helm.yml`)

- [x] 4.1 In the `integration` job, remove the manual "Install Dapr control plane" step (`dapr init -k --wait`) — Dapr now installs via the chart's own dependency on the default `dapr.enabled=true` path.
- [x] 4.2 Add a second integration case (matrix entry or additional job step sequence) that keeps a `dapr init -k`-equivalent pre-install, then installs the chart with `--set dapr.enabled=false`, and asserts the release still rolls out successfully against the externally-managed Dapr.
- [x] 4.3 Keep the existing `kubectl rollout status` / `helm test` assertions in both cases so real-rollout coverage isn't weakened.
- [ ] 4.4 BLOCKED — requires pushing and watching real GitHub Actions runs; not verifiable from this sandbox. Also gated on 1.3 (CI's own `helm dependency build` step will only succeed once the pinned `dapr` version/repo in `Chart.yaml` is confirmed reachable — untested here for the same network reason).

## 5. Docs (`docs/roadmaps/helm-packaging.md`)

- [x] 5.1 Update the Phase 4 row status to ✅ with a summary of what shipped.
- [x] 5.2 Update the Phase 5 row / "Open items" note to record that the `pubsub-component.yaml` / controller-annotation work is now unblocked by Phase 4 landing.
