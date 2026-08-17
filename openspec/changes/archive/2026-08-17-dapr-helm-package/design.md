## Context

`charts/dws` already wires one conditional subchart dependency this way: `postgresql`, gated on
`condition: postgresql.enabled` in `Chart.yaml`, with a chart-owned Secret
(`templates/postgres/secret.yaml`) that only renders when the toggle is on. Phase 4 replicates
that pattern for `dapr/dapr`. The admin Deployment
(`charts/dws/templates/admin/deployment.yaml`) already carries unconditional
`dapr.io/enabled`/`dapr.io/app-id` annotations and `DAPR_PUBSUB_NAME`/`DAPR_PUBSUB_TOPIC`/
`DAPR_APP_PORT` env vars, added ahead of schedule so CI's `integration` job (which does a real
`kubectl rollout status` wait, not just a dry-run) had a working sidecar. CI currently installs
Dapr out-of-band via `dapr init -k` before `helm install`; see specs/proposal for the target
end state.

## Goals / Non-Goals

**Goals:**
- Reuse the existing `postgresql.enabled` conditional-dependency pattern for `dapr.enabled`,
  rather than inventing a new mechanism.
- Keep the preflight check declarative (a template-evaluated `.Capabilities` check), consistent
  with how Helm itself expects cluster-capability gating to be expressed — no external script or
  hook Job.
- Prove both `dapr.enabled` states in CI without weakening the existing real-rollout assertions.

**Non-Goals:**
- Any Knative-side toggle, hook Job, or CRD bundling (Phase 11).
- Gating `templates/controller/deployment.yaml`'s (future, Phase 5) Dapr annotations — that
  template has no Dapr annotations yet and is not touched here.
- Redesigning how `dws-admin` or `dws-controller` talk to Dapr at the application layer — this
  change is chart/values plumbing only.

## Decisions

**Dependency wiring mirrors `postgresql`.** Add to `Chart.yaml`:
```yaml
dependencies:
  - name: postgresql
    version: 16.7.27
    repository: oci://registry-1.docker.io/bitnamicharts
    condition: postgresql.enabled
  - name: dapr
    version: "<pinned dapr/dapr chart version>"
    repository: https://dapr.github.io/helm-charts/
    condition: dapr.enabled
```
Alternative considered: install Dapr via a separate `helm install dapr dapr/dapr` step
documented in the README instead of a chart dependency. Rejected — the roadmap's explicit goal
is a single `helm install` bootstrap, and the `postgresql` precedent already establishes
subchart dependencies as the chart's pattern for optional infra.

**`dapr.enabled` defaults to `true`.** Matches `postgresql.enabled`'s default and keeps
`helm upgrade` with no overrides behaviorally identical to today's ad hoc `dapr init -k`-then-
install flow (Dapr present) — no silent regression for existing installs.

**Gating the admin annotations/env vars uses a single `{{- if .Values.dapr.enabled }}` block**
around the three annotation/env-var groups in `templates/admin/deployment.yaml`, rather than
wrapping the whole Deployment (the Deployment itself must still render — only its
Dapr-dependent fields are conditional). This is a straightforward `{{- if }}` addition to an
existing template, not a new template.

**Preflight check as a `helm.sh/hook: pre-install,pre-upgrade` no-op-render `fail`.** Helm
doesn't have a native "abort if condition" primitive outside of `{{ fail "..." }}` evaluated at
template time — `.Capabilities.APIVersions.Has` is available during normal template rendering
(no hook Job needed, unlike the Knative approach in Phase 11 which needs a real hook Job because
it also runs `kubectl apply`). Placed in a small dedicated template (e.g.
`templates/_preflight.tpl` or inlined in `_helpers.tpl`) that every install/upgrade renders:
```gotemplate
{{- if not .Values.dapr.enabled }}
{{- if not (.Capabilities.APIVersions.Has "dapr.io/v1alpha1") }}
{{- fail "dapr.enabled=false but Dapr CRDs (dapr.io/v1alpha1) were not found in the cluster. Either set dapr.enabled=true to let this chart install Dapr, or install Dapr separately before running helm install/upgrade." }}
{{- end }}
{{- end }}
```
Alternative considered: a `helm test` Job that checks post-install. Rejected — the requirement
is to fail *before* workloads are created (admin's Dapr sidecar-wait would otherwise hang/crash
loop), and `.Capabilities` is only accurate at install/upgrade evaluation time, which is exactly
what a plain template check gives for free.

**CI matrix instead of removing coverage.** The `integration` job's manual `dapr init -k` step
is replaced by letting the chart's own dependency install Dapr (the default-values path). A
second case is added that pre-installs Dapr independently (today's `dapr init -k` step, kept
only for that case) and installs the chart with `dapr.enabled=false`, asserting the release
still comes up correctly against externally-managed Dapr. This keeps the existing real-rollout
assertions (`kubectl rollout status`, `helm test`) exercised in both toggle states instead of
losing coverage when the manual step is dropped.

**Self-healing post-install hook for the sidecar-injection race.** A plain `helm install`
with `dapr.enabled=true` creates the `dapr` subchart and the admin Deployment in the same
atomic apply — Helm gives no ordering guarantee that the `dapr-sidecar-injector` webhook is
actually serving before the admin Pod is admitted, and a missed injection never recovers on
its own (admission only runs once, at Pod creation; a container restart doesn't re-trigger
it). This surfaced empirically in CI: even after the injector Deployment reported `Ready`
and its rollout completed, the admin Pod still came up without its `daprd` sidecar.
`templates/dapr-ready-hook.yaml` adds a `post-install,post-upgrade` hook Job (namespace-
scoped `ServiceAccount`/`Role`/`RoleBinding`, only rendered when `dapr.enabled=true`) that
waits for the injector rollout and then deletes the admin Pod if it's missing the sidecar,
letting the Deployment recreate it correctly. Alternatives considered:
- *Document a required two-phase install* (`helm install --set admin.enabled=false`, wait,
  then `helm upgrade`) — rejected as the default UX; still documented as always safe, but
  the chart shouldn't require operators to know this internal detail for a plain install to
  work.
- *`initContainer` on the admin Pod that waits for the injector* — doesn't help; injection
  is decided once, at admission time, before any container (including init containers) runs,
  so a Pod that missed injection stays sidecar-less regardless of what its containers do.
- *Give `dws-admin`'s application code sidecar-retry logic* — out of scope for a chart-only
  change; `dws-admin` is a separate component/repo.

## Risks / Trade-offs

- **Dapr subchart install time inside `helm install --timeout`** → the `integration` job's
  existing 5m timeout on `helm install` may need headroom now that Dapr's own control plane
  (operator, sentry, placement, sidecar injector) installs as part of the same release instead
  of being pre-warmed by a separate `dapr init -k --wait` step beforehand. Mitigate by measuring
  actual CI wall time after the change and bumping `--timeout` only if needed, rather than
  guessing upfront.
- **`.Capabilities.APIVersions.Has "dapr.io/v1alpha1"` false negative** on a cluster running a
  very old or non-standard Dapr install that doesn't register that exact API group → the
  preflight would incorrectly fail a valid `dapr.enabled=false` install. Mitigated by using
  `dapr.io/v1alpha1`, the long-stable CRD group Dapr has shipped since early releases; documented
  as the detection mechanism so it's easy to revisit if a real false negative surfaces.
- **Breaking change for any existing consumer relying on the always-on admin annotations while
  running `dapr.enabled=false`-equivalent (i.e., no chart-level toggle existed before, so
  behavior was always "annotations present")** → covered explicitly as **BREAKING** in the
  proposal; default stays `true` so the only affected path is an operator who opts in to
  `dapr.enabled=false`.

## Migration Plan

No data migration. Rollout is a normal chart version bump:
1. Land the `Chart.yaml`/`values.yaml`/template changes and run `helm dependency update` to
   regenerate `Chart.lock` with the new `dapr` entry.
2. CI's `verify` job (`helm lint`, `helm template` default/disabled/override, kind dry-run)
   catches rendering regressions before `integration`.
3. Existing installs upgrading with no value overrides pick up `dapr.enabled=true` by default —
   equivalent to today's externally-installed Dapr, now chart-managed. Operators who already run
   a separately-managed Dapr control plane and want to keep it that way must set
   `dapr.enabled=false` on upgrade (and will hit the new preflight check if Dapr isn't actually
   there, which is the intended fail-fast behavior).
4. Rollback: revert the chart version; `dapr.enabled` simply stops being a recognized value
   (ignored) on the prior chart version, and the admin annotations return to their previous
   always-on behavior.
