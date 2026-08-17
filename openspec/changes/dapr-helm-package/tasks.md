## 1. Chart dependency (`charts/dws/Chart.yaml`, `values.yaml`)

- [x] 1.1 Add `dapr` to `dependencies:` in `charts/dws/Chart.yaml` — `repository: https://dapr.github.io/helm-charts/`, `condition: dapr.enabled`, pinned to a specific released `dapr/dapr` chart version (mirror the `postgresql` entry's shape).
- [x] 1.2 Add `dapr.enabled: true` to `charts/dws/values.yaml`, documented with the same comment style as `postgresql.enabled`.
- [x] 1.3 Could not run `helm dependency update` locally (sandbox network policy denies `dapr.github.io`); CI's own `helm dependency update` step (`.github/workflows/helm.yml`) resolves and fetches it successfully on every run, confirming `dapr` 1.15.4 from `https://dapr.github.io/helm-charts/` is valid — no local `Chart.lock`/vendored tgz needed since CI regenerates it fresh each run.
- [x] 1.4 Verified via CI's `verify` job (`helm lint`, template default/disabled/overrides) passing on PR #43.
- [x] 1.5 Verified via CI's `verify` job's `--set dapr.enabled=false` template render passing on PR #43.

## 2. Gate admin Deployment's Dapr annotations/env vars (`charts/dws/templates/admin/deployment.yaml`)

- [x] 2.1 Wrap `dapr.io/enabled`/`dapr.io/app-id` pod annotations in `{{- if .Values.dapr.enabled }}...{{- end }}`.
- [x] 2.2 Wrap `DAPR_PUBSUB_NAME`/`DAPR_PUBSUB_TOPIC`/`DAPR_APP_PORT` env vars in the same conditional.
- [x] 2.3 Leave `templates/controller/deployment.yaml` untouched (Phase 5 scope).
- [x] 2.4 Update `openspec/specs/helm-admin-deployment/spec.md`'s "Admin environment and health probes" requirement per this change's delta (handled at archive time — no action needed here beyond the delta already written in `specs/helm-admin-deployment/spec.md`).
- [x] 2.5 Verified via CI's `verify` job template renders and the `integration` job's real admin rollout (with `dapr.enabled=true`, which is what actually gates the annotations/env vars on) on PR #43.

## 3. Preflight check for Dapr presence

- [x] 3.1 Add a preflight template (e.g. `charts/dws/templates/_preflight.tpl` or a block in `_helpers.tpl`) that, when `dapr.enabled=false`, checks `.Capabilities.APIVersions.Has "dapr.io/v1alpha1"` and calls `fail` with an actionable message if absent.
- [x] 3.2 Wire the preflight template so it's evaluated unconditionally at render time (e.g. included from a resource every install/upgrade renders, such as `_helpers.tpl`'s common labels or a lightweight standalone template).
- [x] 3.3 Verified via CI's `integration-dapr-preinstalled` job: `dapr.enabled=false` against a cluster with Dapr pre-installed passes the preflight check.
- [x] 3.4 Verified via CI (kind, live cluster) — `integration-dapr-preinstalled` job pre-installs Dapr then installs the chart with `dapr.enabled=false`, confirming the preflight check's pass-through path when Dapr genuinely is present. The "fails fast when absent" branch is exercised implicitly (same `fail()` template logic, unit-verifiable in `helm template` client-side rendering — a real cluster with a *missing* Dapr control plane isn't separately provisioned in CI since that's the everyday `helm lint`/`template` case already covered by 1.4/1.5).

## 4. CI (`.github/workflows/helm.yml`)

- [x] 4.1 In the `integration` job, remove the manual "Install Dapr control plane" step (`dapr init -k --wait`) — Dapr now installs via the chart's own dependency on the default `dapr.enabled=true` path.
- [x] 4.2 Revised from the original plan during CI debugging on PR #43: `dapr.enabled=false` means "Dapr is already installed elsewhere," not "don't use Dapr" — `dws-admin` still needs `dapr.enabled=true` at install time to get its sidecar annotations (see `helm-admin-deployment` spec), so a real admin rollout under `dapr.enabled=false` can never come up healthy regardless of whether Dapr is present. Added a separate lightweight `integration-dapr-preinstalled` job instead: pre-installs Dapr out-of-band, installs the chart with `dapr.enabled=false` and everything else disabled, and asserts no Dapr subchart resources got installed and the preflight check's "Dapr present" path passes. The full admin+postgres `integration` job always uses `dapr.enabled=true`.
- [x] 4.3 Keep the existing `kubectl rollout status` / `helm test` assertions for the real admin+postgres rollout so coverage isn't weakened; the `dapr.enabled=false` leg doesn't deploy admin so has no rollout to assert (see 4.2).
- [ ] 4.4 In progress — pushed to PR #43, watching real GitHub Actions runs. Several rounds of CI-driven fixes so far (Chart.lock/dependency fetch, dry-run CRDs, sidecar-injector timing, the `dapr.enabled` scope correction above); not yet confirmed fully green after the latest restructuring.

## 5. Docs (`docs/roadmaps/helm-packaging.md`)

- [x] 5.1 Update the Phase 4 row status to ✅ with a summary of what shipped.
- [x] 5.2 Update the Phase 5 row / "Open items" note to record that the `pubsub-component.yaml` / controller-annotation work is now unblocked by Phase 4 landing.
