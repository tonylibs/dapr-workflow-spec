## Context

The DWS Helm chart (`charts/dws`) previously passed `imagePullSecrets` only to the admin and console pod templates. It creates dedicated least-privilege ServiceAccounts for RBAC-bearing workloads (`controller`, `scheduleTime`, generated `orchestrator` Deployments, and `daprReadyHook`), plus (in this change) a zero-RBAC `dws-task` ServiceAccount for generated Knative step workloads. `dws-admin` and `dws-console` require no Kubernetes API RBAC and previously omitted `serviceAccountName` (falling back to namespace `default`).

Because a Kubernetes `PodSpec` accepts only a single `serviceAccountName`, RBAC-bearing workloads (`controller`, `scheduleTime`, `orchestrator`, `daprReadyHook`) cannot share a zero-RBAC ServiceAccount without either breaking their API access or expanding their RBAC to other workloads. Operators want two capabilities with **zero RBAC expansion** and **zero disruption to already-deployed Knative Revisions**:

1. An opt-in **shared zero-RBAC ServiceAccount** (`imagePullServiceAccount.enabled=true`) that attaches `admin` and `console` Deployments to the same stable zero-RBAC `<fullname>-task` ServiceAccount used by un-overridden Knative steps — without renaming or deleting `<fullname>-task` when toggled.
2. **Per-image `imagePullSecrets` overrides** on each of the eight generated step/task images (`controller.images.callHttp`, `callOpenapi`, `callGrpc`, `callAsyncapi`, `callA2a`, `run.shell`, `run.scriptJs`, `run.scriptPython`) that strictly **replace** the global default pull-secret list for that specific image (including `[]` for a public registry) without mutating `<fullname>-task` or breaking already-running un-overridden Revisions.

See the [proposal](proposal.md) and [Phase 6 live evidence](../../../docs/roadmaps/observability-phase6-evidence.md). All in-force ADRs in `docs/adr/` (`0001`–`0013`) were reviewed; none are superseded by this design (`values.schema.json` remains deliberately partial per ADR 0007, and workflow `use`-store boundaries per ADR 0010 are preserved).

## Goals / Non-Goals

**Goals:**

- Use one top-level `imagePullSecrets` chart value for namespace-local private image pulls across first-party DWS Deployments and generated workflow workloads.
- Keep one stable zero-RBAC ServiceAccount name (`<fullname>-task` / standalone `dws-task`) that always carries top-level `imagePullSecrets` and can optionally be shared with `admin` and `console` via `imagePullServiceAccount.enabled=true` without renaming or deleting the account on toggle and without expanding RBAC on any workload.
- Support per-step-image `imagePullSecrets` replacement overrides (including `[]`) on all eight generated step images by isolating overridden steps onto a dedicated pull-secret-free zero-RBAC ServiceAccount (`<fullname>-task-override` / standalone `dws-task-override`), leaving `<fullname>-task` and existing un-overridden Knative Revisions completely untouched.
- Keep all chart-to-controller contracts verifiable from both chart render tests and controller tests without affecting DSL syntax or content-addressed workflow definition versions.

**Non-Goals:**

- Merging RBAC-bearing ServiceAccounts (`controller`, `scheduleTime`, `orchestrator`, `daprReadyHook`) with zero-RBAC workloads or expanding RBAC on any workload.
- Changes to registry package visibility, per-component pull-secret overrides on chart-level Deployments (`controller`, `scheduleTime`, `admin`, `console`, `dapr.readyHook`) or `controller.images.orchestrator`, or dependency subchart configuration.
- Changes to workflow DSL syntax, content-addressed definition versioning, task-to-resource mapping, step routing, or the step-service HTTP/activity contract.

## Decisions

### 1. Stable zero-RBAC ServiceAccount (`<fullname>-task`) shared across non-RBAC workloads on opt-in

Keep top-level `imagePullSecrets` validated via a shared `#/definitions/imagePullSecrets` schema definition (array of objects with required nonempty string `name` and `additionalProperties: false`), and add top-level `imagePullServiceAccount.enabled` (boolean, default `false`).

- **Stable ServiceAccount name (`dws.task.serviceAccountName` → `<fullname>-task`):**
  - Rendered by `templates/controller/task-serviceaccount.yaml` whenever `controller.enabled` is `true` OR (`imagePullServiceAccount.enabled` is `true` AND (`admin.enabled` OR `console.enabled`)).
  - Always has **zero RBAC** (no `Role`, `RoleBinding`, or `ClusterRoleBinding`) and **always** carries top-level `imagePullSecrets` when non-empty (never stripped when per-image overrides are configured).
  - When `imagePullServiceAccount.enabled=false` (default): `DWS_TASK_SERVICE_ACCOUNT` points to `<fullname>-task`; `admin` and `console` Deployments omit `serviceAccountName` and keep direct `spec.template.spec.imagePullSecrets`.
  - When `imagePullServiceAccount.enabled=true`: `admin` and `console` Deployments set `spec.template.spec.serviceAccountName` to `<fullname>-task` (while retaining direct `spec.template.spec.imagePullSecrets` when non-empty), and `DWS_TASK_SERVICE_ACCOUNT` stays `<fullname>-task`.
  - All RBAC-bearing ServiceAccounts (`controller`, `scheduleTime`, `orchestrator`, `daprReadyHook`) stay separate with their least-privilege RBAC and always carry top-level `imagePullSecrets` when non-empty.
- **Why one stable SA name instead of renaming to `<fullname>-image-pull` on toggle:** Switching between two different SA names when toggling `imagePullServiceAccount.enabled` would cause `helm upgrade` to delete the previous SA while active Knative Revisions still reference it. Keeping `<fullname>-task` stable in both modes completely eliminates toggle-time breakage for active Knative Revisions.
- **Alternative considered:** sharing `<fullname>-orchestrator` across orchestrators and Knative steps. Rejected because `dws-orchestrator` requires namespace-wide `configmaps` (`get`, `list`, `watch`) RBAC for `daprd`'s `configuration.kubernetes` store, which would expand RBAC to step pods (including `dws-run-shell` / `dws-run-script-*`).

### 2. Dedicated pull-secret-free override ServiceAccount (`<fullname>-task-override`) for strict replacement

Both Kubernetes `ServiceAccount` admission (`plugin/pkg/admission/serviceaccount/admission.go`) and Knative Serving's `k8schain` digest resolver **union** `sa.ImagePullSecrets` with `pod.Spec.ImagePullSecrets` rather than replacing them.

To achieve **strict replacement** without ever stripping `imagePullSecrets` from `<fullname>-task`:

- Always render a second zero-RBAC ServiceAccount `templates/controller/task-override-serviceaccount.yaml` named `{{ include "dws.task.overrideServiceAccountName" . }}` (`<fullname>-task-override`, standalone default `dws-task-override`) whenever `controller.enabled=true`. It **never** sets `imagePullSecrets` and has **no RBAC**.
- Pass `DWS_TASK_OVERRIDE_SERVICE_ACCOUNT` alongside `DWS_TASK_SERVICE_ACCOUNT` on the controller Deployment.
- For each of the eight generated step images under `controller.images` (`callHttp`, `callOpenapi`, `callGrpc`, `callAsyncapi`, `callA2a`, `run.shell`, `run.scriptJs`, `run.scriptPython`), accept an optional `imagePullSecrets` field (omitted or `null` = inherit global default via `<fullname>-task`; an array, including `[]` = replace global default for that step image).
- Only for step images whose `imagePullSecrets` is a slice (`and (hasKey $img "imagePullSecrets") (kindIs "slice" (index $img "imagePullSecrets"))`), render the corresponding controller env var as a JSON string array of secret names (e.g. `["http-cred"]` or `"[]"`):
  - `DWS_IMAGES_PULL_SECRETS_CALL_HTTP` → `dws.images.pull-secrets.call-http` (`TaskKind.CALL_HTTP`)
  - `DWS_IMAGES_PULL_SECRETS_CALL_OPENAPI` → `dws.images.pull-secrets.call-openapi` (`TaskKind.CALL_OPENAPI`)
  - `DWS_IMAGES_PULL_SECRETS_CALL_GRPC` → `dws.images.pull-secrets.call-grpc` (`TaskKind.CALL_GRPC`)
  - `DWS_IMAGES_PULL_SECRETS_CALL_ASYNCAPI` → `dws.images.pull-secrets.call-asyncapi` (`TaskKind.CALL_ASYNCAPI`)
  - `DWS_IMAGES_PULL_SECRETS_CALL_A2A` → `dws.images.pull-secrets.call-a2a` (`TaskKind.CALL_A2A`)
  - `DWS_IMAGES_PULL_SECRETS_RUN_SHELL` → `dws.images.pull-secrets.run-shell` (`TaskKind.RUN_SHELL`)
  - `DWS_IMAGES_PULL_SECRETS_RUN_SCRIPT_JS` → `dws.images.pull-secrets.run-script-js` (`TaskKind.RUN_SCRIPT_JS`)
  - `DWS_IMAGES_PULL_SECRETS_RUN_SCRIPT_PYTHON` → `dws.images.pull-secrets.run-script-python` (`TaskKind.RUN_SCRIPT_PYTHON`)
- **No `DWS_IMAGE_PULL_SECRETS` env var is needed:** un-overridden steps simply stay on `<fullname>-task` and inherit global `imagePullSecrets` from Kubernetes/Knative natively.

**Why this beats stripping `<fullname>-task` when overrides are active:**
- Adding or removing an override for one step image (e.g., `callHttp`) never modifies `<fullname>-task`, so already-deployed Knative Revisions for every other step image keep pulling cleanly on cold starts without needing mass resubmission.
- Global `imagePullSecrets` updates still propagate to all un-overridden step Revisions through `<fullname>-task` without workflow resubmission.
- Overridden steps run under `<fullname>-task-override` (zero SA pull secrets) + their exact `spec.template.spec.imagePullSecrets` (or none for `[]`), guaranteeing zero secret leakage from the global list.
- Because `<fullname>-task-override` is always rendered whenever `controller.enabled=true`, enabling or disabling overrides never creates or deletes ServiceAccounts referenced by active Revisions.

**Why JSON array strings over comma-separated strings:**
SmallRye Config treats an empty environment variable (`""`) as absent (`Optional.empty()`), which cannot distinguish "unset (inherit global via `<fullname>-task`)" from "explicitly empty `[]` (use `<fullname>-task-override` with no pull secrets)". JSON arrays (`"[]"`, `'["a","b"]'`) are always non-empty strings, parse cleanly with Jackson, and fail fast with a clear error on malformed JSON or blank names.

### 3. Controller synthesis contract (`DwsConfig` → `StackApplier` → `StackSynthesizer`)

- Bind `dws.task.override-service-account` (`DWS_TASK_OVERRIDE_SERVICE_ACCOUNT`, default `dws-task-override`) alongside `dws.task.service-account` (`DWS_TASK_SERVICE_ACCOUNT`, default `dws-task`) in `DwsConfig` and `application.yaml`.
- Bind optional `dws.images.pull-secrets.<kind>` for all eight `TaskKind`s in `DwsConfig`, parsed eagerly into an immutable `ImagePullSecretPolicy` (failing fast on malformed JSON or blank entries).
- Thread `overrideServiceAccount` and `ImagePullSecretPolicy` from `StackApplier` into `StackSynthesizer.knativeServices` (preserving existing overloads by delegating with an empty policy):
  - If `policy.overrideFor(step.kind())` is **absent**: set `serviceAccountName` to `defaultServiceAccount` (`dws-task`) and omit `spec.template.spec.imagePullSecrets`.
  - If `policy.overrideFor(step.kind())` is **present**: set `serviceAccountName` to `overrideServiceAccount` (`dws-task-override`), and set `ServiceSpecTemplateSpec.imagePullSecrets(...)` via `ServiceSpecTemplateSpecImagePullSecrets` if non-empty (or omit if `[]`).
- `StackSynthesizer.orchestratorDeployment`, `WorkflowCompiler`, `DeploymentPlan`, `StepService`, and content-addressed workflow definition digests remain untouched.
- Standalone `dws-controller/k8s/controller-rbac.yaml` provisions both `dws-task` and `dws-task-override` (both zero RBAC), and `dws-controller/k8s/controller-deployment.yaml` passes both `DWS_TASK_SERVICE_ACCOUNT` and `DWS_TASK_OVERRIDE_SERVICE_ACCOUNT`.

## Risks / Trade-offs

- **RBAC ServiceAccounts remain separate when `imagePullServiceAccount.enabled=true`** -> Because Kubernetes Pods accept only one `serviceAccountName`, `controller`, `scheduleTime`, `orchestrator`, and `daprReadyHook` still have their own ServiceAccounts alongside `<fullname>-task`. Mitigation: top-level `imagePullSecrets` is rendered onto all of them automatically from one Helm value, and chart docs explain the zero-RBAC vs RBAC boundary.
- **Two zero-RBAC step ServiceAccounts (`<fullname>-task` and `<fullname>-task-override`)** -> One extra empty ServiceAccount object exists in the namespace when `controller.enabled=true`. Mitigation: it has zero RBAC, zero secrets, and eliminates both upgrade-time breakage when enabling overrides and deletion hazards when toggling `imagePullServiceAccount.enabled`.
- **Malformed JSON in standalone env vars** -> Validate all `dws.images.pull-secrets.*` eagerly in `dws-controller` with a clear exception naming the offending property.

## Migration Plan

1. Create all referenced registry Secrets in the effective workload namespace before installing or upgrading. Upgrade `charts/dws` and the `dws-controller` image together. For standalone installs, apply the updated `controller-rbac.yaml` (which adds `dws-task-override`) before upgrading `dws-controller`.
2. **Toggling `imagePullServiceAccount.enabled`:** Upgrade the chart in place. `<fullname>-task` keeps the same name and pull secrets, so active Knative Revisions are unaffected while `admin` and `console` Deployments roll cleanly onto `<fullname>-task`.
3. **Adopting or changing per-step-image `imagePullSecrets` overrides:** Upgrade the chart and controller, then resubmit only the workflows containing the overridden `TaskKind` via `POST /workflows`. `StackApplier` reapplies mutable Knative Services even when the immutable definition ConfigMap already exists, moving those steps onto `<fullname>-task-override` with their replacement `imagePullSecrets` while preserving Service names, definition versions, and all un-overridden Revisions.
4. **Rollback:** Because `<fullname>-task` and `<fullname>-task-override` are both rendered whenever `controller.enabled=true`, removing overrides only requires resubmitting affected workflows to return them to `<fullname>-task`. If rolling back to a pre-change release that predates `dws-task` / `dws-task-override`, preserve those ServiceAccounts until active Revisions stop referencing them.

## Open Questions

- Live private-registry verification for `imagePullServiceAccount.enabled=true` and a per-step-image `imagePullSecrets` replacement override requires an authenticated cluster with Dapr, Knative, and two distinct pull Secrets (or one private registry + one public step image with `[]` override). No in-force ADRs need supersession.
