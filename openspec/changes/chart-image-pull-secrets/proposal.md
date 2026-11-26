## Why

The DWS chart's global `imagePullSecrets` currently reaches only admin and console. The Phase 6 live check required manual ServiceAccount patches to run the controller, scheduler, and generated workflow workloads from a private registry. [Issue #207](https://github.com/tonylibs/dapr-workflow-spec/issues/207)

## What Changes

- Apply the global pull-secret list to every ServiceAccount owned by the DWS chart and every Deployment pod template rendered by its first-party templates. Document and validate the list as references to existing Secrets in the workload namespace.
- Create a dedicated `dws-task` ServiceAccount with no chart-granted RBAC. Derive its name from the release in Helm and use `dws-task` as the standalone default. The name distinguishes this Kubernetes identity from the `dws-step` runtime component. Pass the account name from the chart to the controller and use it on every generated Knative step Service, including when the list is empty.
- Use `dws.task.service-account` and `DWS_TASK_SERVICE_ACCOUNT` for the controller setting that selects the account.
- Pin the chart-to-controller contract in render and controller tests. Verify a fresh installation and workflow execution against a private registry.
- Out of scope are registry package visibility, per-component pull-secret overrides, and dependency chart configuration.

## Capabilities

### New Capabilities

- `helm-image-pull-secrets` defines the global value and its coverage across chart-owned resources and generated workflow workloads.

### Modified Capabilities

- `helm-controller-deployment` adds the dedicated step identity alongside the existing orchestrator identity contract.

## Impact

- `charts/dws` owns values validation, ServiceAccount creation, and chart wiring. `dws-controller` owns runtime configuration and Knative Service synthesis. The chart render gate and controller Maven gate run independently.
- Existing DSL definitions retain their content-derived versions and runtime behavior. Existing Knative Services adopt the new identity when their workflow is submitted again. Standalone deployments must provision the step account before using the updated controller. Custom grants or policies attached to `default` need review before switching existing steps to the dedicated identity.
