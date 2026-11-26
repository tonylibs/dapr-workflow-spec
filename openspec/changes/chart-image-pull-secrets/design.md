## Context

The chart supplies `imagePullSecrets` directly to the admin and console pod templates. It already creates dedicated ServiceAccounts for the controller, scheduler, and generated orchestrators, plus a ServiceAccount for the Dapr readiness hook when enabled. None of those accounts receive the setting. The controller currently synthesizes Knative step templates without a named ServiceAccount. See the [proposal](proposal.md) and [Phase 6 live evidence](../../../docs/roadmaps/observability-phase6-evidence.md).

## Goals / Non-Goals

**Goals**

- Use one chart value for namespace-local private image pulls by DWS Deployments and generated workflow workloads.
- Give every generated Knative step a release-scoped identity in Helm installs and a documented default identity in standalone installs.
- Keep the ServiceAccount handoff verifiable from both the chart and the controller.

**Non-Goals**

- Changes to registry visibility, per-component pull-secret settings, or dependency chart configuration.
- Changes to workflow syntax, content-addressed versions, task mapping, step routing, or the step-service HTTP contract.

## Decisions

### Attach references at the workload identity and Deployment

Keep the existing top-level `imagePullSecrets` list and validate each item as a reference with a required, nonempty string `name`. Operators create the registry Secret in the effective workload namespace before installing. That namespace follows `namespaceOverride` when set. Render nonempty lists on all first-party ServiceAccounts, including the readiness hook account, and directly on the controller, scheduler, admin, and console Deployment pod templates. The readiness Job gets the list from its account. The Kubernetes admission plugin copies ServiceAccount pull-secret references to new Pods with an empty list. Direct references on chart Deployments also cover Pod creation before an existing account update has taken effect. [Kubernetes ServiceAccount admission](https://kubernetes.io/docs/reference/access-authn-authz/service-accounts-admin/#serviceaccount-admission-controller)

Copying the list into generated templates would pass registry Secret references through controller configuration and could trigger workload rollouts when the list changes. ServiceAccount references keep registry configuration with the operator and cover both generated orchestrator Pods and Knative Revisions. Knative's digest resolver receives the Revision namespace and ServiceAccount name. [Knative Revision reconciler](https://github.com/knative/serving/blob/main/pkg/reconciler/revision/revision.go)

### Pass a dedicated account name to the shared Knative synthesizer

Follow the existing orchestrator pattern. Use `dws.task.serviceAccountName` as the shared Helm helper. It appends `-task` to the chart fullname and applies the chart's name length handling. It supplies both `metadata.name` and `DWS_TASK_SERVICE_ACCOUNT`, yielding `dws-task` for the default release and `myrel-dws-task` for a release named `myrel`. This name distinguishes the Kubernetes identity from the `dws-step` runtime component. Gate the account with `controller.enabled` and create it with no RoleBinding or ClusterRoleBinding. Using the namespace `default` account would require an operator patch that also grants unrelated Pods use of the registry credentials. Sharing the orchestrator account would give step pods its ConfigMap permissions.

Bind `dws.task.service-account` in `DwsConfig` and `application.yaml`, with `DWS_TASK_SERVICE_ACCOUNT` as its environment override and `dws-task` as its default. `StackApplier` passes the resolved name to `StackSynthesizer.knativeServices`, which uses its shared Knative template builder to set `spec.template.spec.serviceAccountName` for every deployable step. This keeps deployment identity outside the compiled definition and its digest. The standalone `k8s/controller-rbac.yaml` provisions `dws-task` in the same namespace as its reference workloads, and `k8s/controller-deployment.yaml` passes the matching name. The chart and controller tests pin the environment variable, custom release name, and synthesized field.

## Risks / Trade-offs

- Missing or misnamed account -> Use the same Helm helper for the account and controller environment variable. Standalone operators provision the account in `DWS_NAMESPACE` before rolling out the new controller.
- Existing policy or grants target `default` -> Review operator-managed policies that depend on the old identity before resubmitting workflows. The new step account has no chart-granted RBAC.
- A correct render can still fail on registry authentication -> Use a fresh cluster or uncached private tags and validate both Knative digest resolution and actual Pod readiness. Record evidence without credentials.

## Migration Plan

1. Create the registry Secret in the effective workload namespace before a private-registry install. Upgrade the chart and controller image together. For standalone installs, apply the updated account manifest before upgrading the controller.
2. Submit a workflow with a deployable step. To migrate an existing workflow, resubmit its existing definition through `POST /workflows`. `StackApplier` reapplies mutable Services even when the definition ConfigMap exists, so the new account goes into a new Knative Revision while the definition version and Service name are preserved. New step Pods use the account from their Revision template. Newly admitted orchestrator Pods inherit the updated pull-secret list from their account.
3. For rollback, preserve the step account and its pull secrets while active Revisions reference it. If restoring a release that predates the account, first migrate traffic and workloads to Revisions with a surviving, correctly configured account, or arrange to retain the dedicated account during rollback. Verify private pulls after rollback.

For deployments of the earlier feature-branch build, move any custom `dws.step.service-account` or `DWS_STEP_SERVICE_ACCOUNT` setting to the new key. Keep the old account and its pull-secret references available during the upgrade while active Revisions still name it. Resubmit the workflow with the renamed account provisioned, then retire the old account after the affected Revisions stop serving traffic.

## Open Questions

The live verification requires an authenticated test registry and a cluster with Dapr and Knative ready. The existing workload identity and deployment decisions in the ADRs are compatible with this design.
