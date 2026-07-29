## Context

`charts/dws` was scaffolded in Phase 1 with stock `helm create` output: a generic nginx
Deployment/Service and boilerplate `_helpers.tpl` and `values.yaml`. The controller templates
(`templates/controller/{serviceaccount,rbac,deployment,service}.yaml`) exist only as placeholder
comments. The behavior we must reproduce lives in two hand-maintained manifests:

- `dws-controller/k8s/controller-rbac.yaml` — ServiceAccount + namespaced Role (four rules) +
  RoleBinding, all named `dws-controller`, namespace `default`, label `app: dws-controller`.
- `dws-controller/k8s/controller-deployment.yaml` — a single-replica Deployment (container
  `controller`, port `http`/8080, health probes on `/q/health/{live,ready}`, five env vars, CPU/mem
  requests+limits) and a `ClusterIP`-style Service exposing port 80 → `http`.

The existing `_helpers.tpl` already defines `dws.name`, `dws.fullname`, `dws.chart`, `dws.labels`,
`dws.selectorLabels`, and `dws.serviceAccountName`. This phase touches only controller resources;
admin/postgres/console templates stay as-is.

## Goals / Non-Goals

**Goals:**
- Faithfully port the controller RBAC + Deployment + Service into Helm templates whose default
  render is functionally identical to the raw manifests.
- Parameterize name, namespace, image, replicas, service port, and the four `DWS_IMAGES_*` values.
- Gate the whole controller stack behind `controller.enabled` (default `true`).
- Preserve the RBAC scope byte-for-byte in intent — same api groups, resources, verbs.

**Non-Goals:**
- No changes to `dws-admin`, `postgres`, or `console` templates (Phases 3+).
- No Dapr/Knative dependency wiring or preflight checks (Phase 4).
- No removal of the source manifests in `dws-controller/k8s/` — they remain the reference.
- No new probes, resource limits, or security contexts beyond what the source manifest already has.

## Decisions

**1. Controller name via a dedicated `dws.controller.fullname` helper, not raw `dws.fullname`.**
The chart is multi-component (controller, admin, postgres). Deriving each component's object name
as `<fullname>-<component>` keeps names unique and predictable. `dws.controller.fullname` returns
`printf "%s-controller" (include "dws.fullname" .)`. Selector labels for the controller extend
`dws.selectorLabels` with `app.kubernetes.io/component: controller` so the controller Deployment's
selector cannot accidentally match admin pods. Alternative — reuse `dws.fullname` directly for the
controller — was rejected because later phases add sibling Deployments that would collide on name
and selector.

**2. Namespace via a `dws.namespace` helper that defaults to `.Release.Namespace`.**
The source manifests hardcode `default`. Helm's idiom is to let the release namespace drive object
namespace, but an explicit `namespace:` on each object (rather than omitting it) keeps the RoleBinding
subject namespace and Role namespace in lockstep and makes `helm template` output self-contained.
`dws.namespace` returns `default "namespace" .Release.Namespace` guarded by an optional
`namespaceOverride` value, so operators can pin a namespace without relying on `--namespace`.

**3. `controller:` values block, self-contained (does not reuse the top-level `image:`/`service:`).**
The stock top-level `image`/`service`/`replicaCount` keys describe the placeholder nginx workload
and will be repurposed or removed in later value-design work (Phase 6). Rather than overload them,
the controller reads its own `controller.image`, `controller.replicaCount`, `controller.service`,
and `controller.images.*`. This keeps the controller self-describing and avoids coupling to
boilerplate that other phases will churn. Image reference is composed as
`{{ .Values.controller.image.repository }}:{{ .Values.controller.image.tag | default .Chart.AppVersion }}`.

**4. `DWS_IMAGES_*` env sourced from `controller.images.{callHttp,callOpenapi,run,orchestrator}`.**
These are the step-image references the controller stamps into per-workflow resources — pure
configuration, ideal for values. `DWS_NAMESPACE` stays a downward-API `fieldRef` (not a value)
because it must reflect the pod's actual namespace at runtime.

**5. RBAC rendered verbatim from the source, only name/namespace/labels templatized.**
The four Role rules are copied exactly. The spec pins the scope so a future edit that broadens it
(adds `secrets`, a wildcard, cluster scope) is caught by the "RBAC scope preserved" scenario. No
attempt to DRY the verb lists — clarity and auditability beat brevity for security-relevant YAML.

**6. Single `controller.enabled` gate wrapping each file.**
Each of the four template files wraps its content in `{{- if .Values.controller.enabled }}`. The
default in `values.yaml` is `true`. This matches the roadmap's per-component toggle convention that
later phases (`postgresql.enabled`, `knative.enabled`) also follow.

## Risks / Trade-offs

- **[Placeholder top-level values remain in `values.yaml`]** → The stock nginx `image`/`service`
  keys are now unused by the controller but still referenced by the leftover
  `templates/deployment.yaml`/`service.yaml` boilerplate. Mitigation: out of scope here; Phase 6
  (values design) reconciles them. This phase neither deletes nor depends on them, so the two code
  paths don't interfere.
- **[Namespace behavior differs from source default]** → Source hardcodes `default`; the chart
  follows the release namespace. Mitigation: `dws.namespace` still resolves to `default` when the
  release is installed into the default namespace, and `namespaceOverride` lets an operator pin it
  explicitly. Documented in the spec's namespace scenario.
- **[Component selector labels change from `app: dws-controller` to k8s recommended labels]** → The
  rendered selector uses `app.kubernetes.io/{name,instance,component}` rather than the source's
  `app: dws-controller`. Mitigation: this is a fresh install path (new objects), not an in-place
  relabel of a running Deployment, so there's no immutable-selector migration hazard. The Service
  selector and Deployment selector are rendered from the same helper, so they stay consistent.

## Migration Plan

Not a live migration — this adds a new install path alongside the existing raw manifests. Deploy by
`helm install dws charts/dws`. Rollback is `helm uninstall` (or simply continue using
`kubectl apply -f dws-controller/k8s/` which is unchanged). Verification for this phase is
`helm lint charts/dws` + `helm template charts/dws`, inspecting the rendered controller
Deployment and RBAC.

## Open Questions

- Should the source `dws-controller/k8s/*.yaml` manifests be deleted once the chart reaches parity?
  Deferred — kept as reference until the chart is published (Phase 8).
