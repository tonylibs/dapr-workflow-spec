## 1. Helm chart

- [x] 1.1 Add the `imagePullSecrets` array schema and document the existing namespace-local Secret prerequisite in `charts/dws/values.yaml` and `charts/dws/README.md`.
- [x] 1.2 Use a shared Helm name helper for the step ServiceAccount and `DWS_STEP_SERVICE_ACCOUNT` in the controller Deployment. Gate the account on `controller.enabled` and verify that the chart grants it no RBAC.
- [x] 1.3 Render the nonempty list on the controller, scheduler, orchestrator, step, and Dapr readiness hook ServiceAccounts and on every first-party Deployment pod template. Confirm an empty list omits the fields.
- [x] 1.4 Add `charts/dws/tests/image-pull-secrets-render-test.sh` and run `bash charts/dws/tests/image-pull-secrets-render-test.sh charts/dws`. Cover all enabled first-party resources, multiple configured references, omitted and explicitly empty values, malformed values, step identity under default and custom releases and effective namespaces, and the disabled controller case.
- [x] 1.5 Wire the render test into `.github/workflows/helm.yml` and the root `CLAUDE.md` chart gate. Regenerate the default observability render fixture with the Helm version pinned in CI and review that the diff adds the step account and controller environment variable. Run the full root `CLAUDE.md` chart gate.

## 2. Controller

- [x] 2.1 Add `dws.step.service-account` with default `dws-step` to `DwsConfig` and `application.yaml`. Thread the resolved value through `StackApplier` into the shared `StackSynthesizer` Knative template builder.
- [x] 2.2 Add `dws-step` without an RBAC grant to `dws-controller/k8s/controller-rbac.yaml`. Document the standalone install order, custom workload namespace, and matching `DWS_STEP_SERVICE_ACCOUNT` setting in `dws-controller/k8s/README.md`.
- [x] 2.3 Add `StepServiceAccountContractTest` to pin the chart environment key to controller configuration binding and standalone provisioning. Cover default and configured names, multiple deployable step kinds, and a mutable Knative Service reapplied at the same definition version. Run `./mvnw test -Dtest=StepServiceAccountContractTest,StackSynthesizerTest` and `./mvnw verify` from `dws-controller/`, using `mvnw.cmd` on Windows.

## 3. Acceptance

- [x] 3.1 On a fresh cluster namespace with Dapr and Knative, create `regcred`, use uncached private tags, and install the matching chart and controller image with `imagePullSecrets: [{name: regcred}]`. Confirm the controller, admin, and scheduler are ready. Submit and execute a workflow with a Knative step, then record orchestrator and step Pod readiness, the step's resolved image digest and dedicated ServiceAccount, and successful workflow completion without manual ServiceAccount patches or preloaded images.
- [x] 3.2 Run `openspec validate chart-image-pull-secrets --type change --strict` before archive.
