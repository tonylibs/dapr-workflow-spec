## Why

The DWS chart's global `imagePullSecrets` previously reached only admin and console, requiring manual ServiceAccount patches in the Phase 6 live check to run the controller, scheduler, and generated workflow workloads from a private registry ([Issue #207](https://github.com/tonylibs/dapr-workflow-spec/issues/207)). Operators also need an option to share one zero-RBAC ServiceAccount across non-RBAC workloads (`dws-admin`, `dws-console`, and generated step/task pods) without expanding RBAC or disrupting active Knative Revisions on toggle, and to override `imagePullSecrets` per generated step/task image (replacing the global default for that image without stripping global pull secrets from un-overridden Revisions).

## What Changes

- Apply the global top-level `imagePullSecrets` list to every first-party ServiceAccount owned by `charts/dws` (except the dedicated override-only step ServiceAccount below) and every Deployment pod template rendered by its first-party templates. Document and validate the list as references to existing Secrets in the effective workload namespace.
- Provision a stable zero-RBAC `dws-task` ServiceAccount (`<fullname>-task` in Helm, `dws-task` in standalone manifests) that always carries the global `imagePullSecrets` list when non-empty, passed via `DWS_TASK_SERVICE_ACCOUNT` (`dws.task.service-account`), and stamped onto un-overridden generated Knative step Services.
- Add an opt-in `imagePullServiceAccount.enabled` option (default `false`) in `charts/dws`. When `true`, `admin` and `console` Deployments attach to that same stable zero-RBAC `<fullname>-task` ServiceAccount (rendered whenever any of `controller`, `admin`, or `console` is enabled). Toggling `imagePullServiceAccount.enabled` never renames or deletes `<fullname>-task`, and RBAC-bearing workloads (`controller`, `scheduleTime`, `orchestrator`, and `daprReadyHook`) retain their own separate least-privilege ServiceAccounts with zero RBAC expansion.
- Provision a second zero-RBAC ServiceAccount (`<fullname>-task-override` in Helm, `dws-task-override` in standalone manifests) whenever `controller.enabled=true`, passed via `DWS_TASK_OVERRIDE_SERVICE_ACCOUNT` (`dws.task.override-service-account`), that never carries `imagePullSecrets`.
- Add optional per-image `imagePullSecrets` overrides on each of the eight generated step/task images under `controller.images` (`callHttp`, `callOpenapi`, `callGrpc`, `callAsyncapi`, `callA2a`, `run.shell`, `run.scriptJs`, `run.scriptPython`). Setting an array (including `[]`) passes `DWS_IMAGES_PULL_SECRETS_<KIND>` to `dws-controller`, which routes only that overridden `TaskKind` to `<fullname>-task-override` and stamps its exact replacement `spec.template.spec.imagePullSecrets` (or omits it for `[]`). Un-overridden steps continue using `<fullname>-task` and inheriting global pull secrets cleanly without mutation.
- Pin the chart-to-controller contracts (default, shared zero-RBAC ServiceAccount toggle, and per-image pull-secret overrides) in chart render tests and controller tests, and verify private-registry execution live.
- **Non-goals**: merging RBAC-bearing ServiceAccounts (`controller`, `scheduleTime`, `orchestrator`, `daprReadyHook`), registry package visibility, per-component `imagePullSecrets` overrides for chart-level Deployments (`controller`, `scheduleTime`, `admin`, `console`, `dapr.readyHook`) or `controller.images.orchestrator`, and dependency subchart configuration (`dapr`, `postgresql`, `redis`, `dex`, `apisix`, `opentelemetry-collector`).

## Capabilities

### New Capabilities

- `helm-image-pull-secrets`: defines top-level `imagePullSecrets`, the opt-in shared zero-RBAC ServiceAccount attachment (`imagePullServiceAccount.enabled`) using the stable `<fullname>-task` account, optional per-image `imagePullSecrets` replacement overrides on the eight generated step/task images via `<fullname>-task-override`, schema validation, and coverage across chart-owned resources and controller-generated workloads.

### Modified Capabilities

- `helm-controller-deployment`: adds the stable zero-RBAC `<fullname>-task` (`dws-task`) and override-only `<fullname>-task-override` (`dws-task-override`) ServiceAccounts and controller wiring alongside the existing orchestrator identity contract.

## Impact

- **Affected components** (preserving independent build boundaries):
  - `charts/dws`: values schema validation, stable `<fullname>-task` and `<fullname>-task-override` ServiceAccount rendering, `imagePullServiceAccount.enabled` wiring on `admin` and `console`, per-image `DWS_IMAGES_PULL_SECRETS_<KIND>` env-var handoff, default observability render fixture update, and Helm render test gate.
  - `dws-controller`: SmallRye config binding for `dws.task.service-account`, `dws.task.override-service-account`, and `dws.images.pull-secrets.*`, `StackApplier` / `StackSynthesizer.knativeServices` synthesis, standalone `k8s/` manifests, and Maven test/verify gate.
- **DSL & runtime behavior**: No changes to OWS DSL syntax, content-addressed definition versioning, task-to-resource mapping, step routing, or step-service HTTP/activity runtime behavior. Pull-secret and override-account resolution happen strictly at Kubernetes apply/synthesis time outside the definition digest.
- **Compatibility**: Existing workflow definitions keep their versions and runtime behavior. Toggling `imagePullServiceAccount.enabled` or adding/removing a per-step-image override never mutates or deletes `<fullname>-task`, so existing un-overridden Knative Revisions continue pulling images uninterrupted. Resubmitting a workflow via `POST /workflows` rolls overridden steps onto `<fullname>-task-override` with their exact `imagePullSecrets`. Standalone deployments must provision `dws-task` and `dws-task-override` (or configure matching env vars) before using per-image overrides.
