# Follow-ups — activity-invocation-refactor

Findings and deferred work surfaced while implementing this change. None block the
code changes in this change; they are deployment/platform prerequisites and future
enhancements.

## 1. Cross-app dispatch prerequisite: shared namespace (verified — satisfied)

Multi-app activity dispatch requires the orchestrator Deployment and every step
Knative Service to run in the same namespace.

**Verified satisfied by construction.** `StackApplier` (`dws-controller`) applies every
synthesized resource — definition ConfigMap, Dapr Configuration component, all step
Knative Services, and the orchestrator Deployment — with a single `namespace =
config.namespace()` (`StackApplier.java`). `StackSynthesizer` takes that one `namespace`
argument for `definitionConfigMap`, `configurationComponent`, `knativeService`, and
`orchestratorDeployment` alike. There is no code path that splits a workflow's resources
across namespaces, so no synthesis change is required.

## 2. Cross-app dispatch prerequisite: shared workflow/actor state store (deployment gap)

Dapr brokers multi-app workflow activities through the workflow engine's actor/state
backend. The orchestrator and the step apps must resolve the **same** Dapr
workflow/actor state-store component in their namespace.

**Not currently provisioned by `charts/dws`.** The chart ships the controller, admin,
and postgres resources but no Dapr `Component` of `type: state.*` with
`actorStateStore: "true"`. The controller does **not** synthesize a state store either —
and it should not: a workflow/actor state store is namespace/platform-scoped (one per
namespace, shared by all Dapr apps), not a per-workflow resource, so it does not belong
in `StackSynthesizer`.

**Action (follow-up, deployment scope):** add a namespace-level Dapr state-store
`Component` (with `actorStateStore: "true"`) to `charts/dws` — or document it as a
required platform prerequisite the operator installs — before enabling activity-invoked
steps in a real cluster. Tracked here rather than fixed in this change because it is
platform/ops configuration, adjacent to but outside the code migration.

## 3. BLOCKING: cross-app dispatch is version-coupled and unvalidated at runtime

**Status: blocking for real-cluster use.** Local e2e (2026-08-16) showed the migrated
dispatch does not route across apps on the runtime versions tested:

- Dapr **1.15.5**: `required metadata dapr-app-id not found` (multi-app predates support —
  the feature requires runtime **≥1.16.0**).
- Dapr **1.16.0**: `required metadata dapr-callee-app-id or dapr-app-id not found`. The
  target worker (`Run` registered, `/healthz` 200, shared redis with `actorStateStore:
  "true"`) is never invoked; redis holds only the caller's workflow state.

**Root cause (evidence):** the failure is in Dapr's runtime cross-app *proxying* — the
`dapr-callee-app-id` gRPC metadata is not attached when the workflow engine dispatches the
activity to the callee app. This is a runtime/SDK version-compatibility problem, not a
logic defect in this branch: scheduling via `WorkflowTaskOptions(retryPolicy, appId)` /
`WithActivityAppID` and registering the `Run` activity both follow the documented API, and
the unit tests for that logic pass. Dapr's own releases have regressed this metadata
propagation (see [dapr/dapr#10039](https://github.com/dapr/dapr/issues/10039): runtime
1.17.7 + durabletask-go v0.11.5 worked; 1.17.8 + v0.12.1 broke). Our Go step images link
durabletask-go **v0.12.4** (via dapr go-sdk v1.15.0); the Java orchestrator uses dapr-sdk
1.18.0. The app-side durabletask-go/kit version and the daprd runtime version must be a
**compatible pair** for the callee-app-id metadata to propagate.

**Correction to earlier claims:** the proposal/design/verify previously asserted "Dapr
1.16.0 is sufficient to run the migrated dispatch." That is **not** substantiated — 1.16.0
did not dispatch in practice. The Dapr version is therefore a **blocking prerequisite**, not
the low-priority follow-up it was first recorded as.

**Action (required before merge/enablement):**
1. Identify a validated (daprd runtime, dapr go-sdk/durabletask-go, dapr Java SDK) version
   combination on which cross-app activity dispatch actually routes end-to-end.
2. Pin that runtime in `charts/dws/Chart.yaml` (currently `appVersion: "1.16.0"`) and pin the
   matching SDK/durabletask-go versions in the Go modules and the orchestrator pom.
3. Re-run the e2e (`e2e-checklist.md`) on that combination and record the passing evidence.

Durable/deduplicated activity results (a separate concern) still want `1.17+`; that remains
a non-goal here beyond the dispatch-enabling version bump.

## 4. WorkflowAccessPolicy for cross-app steps (production security, follow-up)

Dapr's multi-app workflows use a `WorkflowAccessPolicy` resource to allow-list which caller
app-ids may schedule which activities on a target app (self-calls are always permitted; the
policy only restricts). This change deploys none. It is **not** the cause of the metadata
failure above (that fails before any access check), and a pure allow-list is not required for
the dispatch to *function*, but for production each step app should carry a policy allowing
its workflow's orchestrator app-id to schedule the `Run` activity. Synthesizing a
`WorkflowAccessPolicy` per workflow in `dws-controller` is a follow-up, deferred until the
version/dispatch prerequisite (item 3) is validated.
