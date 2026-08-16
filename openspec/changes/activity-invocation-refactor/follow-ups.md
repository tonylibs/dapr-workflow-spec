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

## 3. Dapr runtime version for durable/deduplicated activity results

`charts/dws/Chart.yaml` pins Dapr `appVersion: "1.16.0"`. Durable, deduplicated
multi-app activity results require Dapr `1.17+`. This change does **not** rely on
dedup for correctness — step activities are re-invokable, matching the prior HTTP retry
semantics — so `1.16.0` is sufficient to run the migrated dispatch. Bumping to `1.17+`
(and any config needed to enable result deduplication) is a deliberate follow-up, out of
scope here per the change's stated non-goals.
