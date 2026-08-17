## Why

Phase 4 of the Helm chart packaging roadmap (`docs/roadmaps/helm-packaging.md`) makes Dapr an
optional, chart-managed prerequisite instead of assumed pre-existing cluster infra. Today
`charts/dws` has no `dapr` dependency, no `dapr.enabled` toggle, and no preflight check; the
admin Deployment's `dapr.io/enabled`/`dapr.io/app-id` annotations and `DAPR_PUBSUB_NAME`/
`DAPR_PUBSUB_TOPIC` env vars were added unconditionally, ahead of schedule, purely to get CI's
real integration test running before this toggle existed. This is the critical path for Phase 5
(event wiring), which needs a real in-chart Dapr control plane to finish cleanly.

## What Changes

- Add `dapr/dapr` as a conditional dependency in `charts/dws/Chart.yaml`, gated on
  `condition: dapr.enabled` (mirrors the existing `postgresql`/`postgresql.enabled` wiring).
- Add `dapr.enabled` (default `true`) to `charts/dws/values.yaml`.
- **BREAKING**: Wrap the admin Deployment's `dapr.io/enabled`/`dapr.io/app-id` pod annotations
  and `DAPR_PUBSUB_NAME`/`DAPR_PUBSUB_TOPIC` env vars behind `.Values.dapr.enabled` — previously
  unconditional, so `dapr.enabled=false` now removes them where it previously had no effect.
  `templates/controller/deployment.yaml` is untouched (its Dapr annotations are Phase 5 scope).
- Add a preflight check (`Capabilities.APIVersions.Has`, evaluated at `helm install`/`upgrade`)
  that fails fast when `dapr.enabled=false` but Dapr CRDs are not actually present in the
  cluster.
- CI: drop the hand-rolled `dapr init -k` step from `.github/workflows/helm.yml`'s `integration`
  job now that Dapr installs via the chart dependency; add a matrix/second case that validates
  `dapr.enabled=false` against a pre-installed Dapr control plane, so both toggle states are
  exercised.
- Update `docs/roadmaps/helm-packaging.md`: mark the Phase 4 row ✅ and note in the Phase 5 row
  that the `pubsub-component.yaml` / controller-annotation work is now unblocked.

**Non-goals**: Knative Serving support (`knative.enabled`, install-hook Job, CRD bundling) is
explicitly out of scope — tracked separately as Phase 11. `templates/controller/deployment.yaml`
Dapr annotations are Phase 5 scope and not touched here. This change has no effect on the DSL
1.0 workflow definition schema, on `dws-orchestrator`'s runtime interpretation of a definition,
or on any deployed per-workflow resource (`dws-orchestrator` Deployments, step Knative Services)
— it only changes the static `dws-controller`/`dws-admin` platform installer. Existing workflow
definitions already `POST`ed to a running `dws-controller` are unaffected; this change only
affects how the platform itself is installed/upgraded via `helm`.

**Compatibility**: `dapr.enabled` defaults to `true`, so a plain `helm upgrade` with no value
overrides keeps installing/using Dapr exactly as before. An operator who previously relied on
the always-on admin Dapr annotations while running with an implicit (non-chart-managed) Dapr
control plane and would now set `dapr.enabled=false` will see those annotations and env vars
disappear from the admin pod — this is the intended, documented behavior of the new toggle, not
an accidental regression, but it is a behavior change for that pod spec and is called out as
**BREAKING** above.

## Capabilities

### New Capabilities
- `helm-dapr-dependency`: Dapr as a conditional Helm chart dependency (`dapr.enabled`, default
  `true`) plus a preflight check that fails `helm install`/`upgrade` when Dapr is disabled but
  its CRDs aren't present in the cluster.

### Modified Capabilities
- `helm-admin-deployment`: the admin Deployment's Dapr annotations and pubsub env vars, which
  today render unconditionally, must render only when `dapr.enabled` is true.

## Impact

- `charts/dws/Chart.yaml` — new `dapr` dependency entry.
- `charts/dws/values.yaml` — new `dapr.enabled` value.
- `charts/dws/templates/admin/deployment.yaml` — gate existing annotations/env vars behind
  `.Values.dapr.enabled`.
- `charts/dws/templates/_helpers.tpl` and/or a new preflight template — Dapr CRD presence check.
- `.github/workflows/helm.yml` — `integration` job: remove manual `dapr init -k`, add a
  `dapr.enabled=false` validation path against a pre-installed Dapr.
- `docs/roadmaps/helm-packaging.md` — Phase 4 row to ✅, Phase 5 row note.
- No changes to `dws-controller`, `dws-orchestrator`, `dws-call-http`, `dws-call-openapi`,
  `dws-run`, or the DSL 1.0 schema.
