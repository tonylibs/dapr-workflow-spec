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

## 3. RESOLVED: cross-app dispatch validated on Dapr 1.18.0; chart bumped

**Status: resolved.** The blocker was a runtime-version mismatch, now fixed.

**What we saw first (2026-08-16):** on the runtime versions initially tested, dispatch did
not route across apps — Dapr **1.15.5** → `required metadata dapr-app-id not found` (multi-app
predates support; needs ≥1.16.0); Dapr **1.16.0** → `required metadata dapr-callee-app-id or
dapr-app-id not found`. The `dapr-callee-app-id` gRPC metadata was not propagated during the
workflow engine's cross-app proxying. This was never a logic defect in this branch —
scheduling via `WorkflowTaskOptions(retryPolicy, appId)` / `WithActivityAppID` and registering
the `Run` activity follow the documented API — but a runtime/client version-compatibility
problem (cf. [dapr/dapr#10039](https://github.com/dapr/dapr/issues/10039)).

**Root cause:** the runtime was *older* than the client libraries. The Go step images link
`dapr/go-sdk v1.15.0` → `durabletask-go v0.12.4`, `dapr/kit v0.18.1`, transitively
`dapr/dapr v1.18.0`; the Java orchestrator uses `dapr-sdk 1.18.0`. Running that 1.18-era
client stack against a **1.16.0** runtime is the mismatch that dropped the callee-app-id
metadata. `charts/dws/Chart.yaml` pinned `appVersion: "1.16.0"` — the actual defect.

**Validated fix (local e2e on Dapr 1.18.0):** the real `dws-call-http` worker (app-id
`check-inventory`, `Run` registered) and a minimal workflow host that schedules
`CallActivity("Run", WithActivityAppID("check-inventory"))`, wired to a shared redis
`statestore` (`actorStateStore: "true"`) + placement + scheduler, produced:

```
RESULT status=ORCHESTRATION_STATUS_COMPLETED output={"hello":"world","stock":42}
0 dispatch failures
```

i.e. the activity dispatched cross-app, executed the runner (upstream returned `stock:42`),
applied `OUTPUT=merge` onto the input `{"hello":"world"}`, and the workflow completed. The
`dapr-callee-app-id` error is gone on 1.18.0. (Getting there in self-hosted slim also required
`--override-broadcast-host-port` on the scheduler and `DAPR_HOST_IP=127.0.0.1` for mdns — those
are local-harness plumbing, not cluster concerns.)

**Applied:** `charts/dws/Chart.yaml` `appVersion` bumped **1.16.0 → 1.18.0**. The Go modules
(`go-sdk v1.15.0` / `durabletask-go v0.12.4`) and orchestrator (`dapr-sdk 1.18.0`) already
match this runtime. The retraction stands: the earlier "Dapr 1.16.0 is sufficient" claim was
wrong; **1.18.0 is the validated minimum** for this feature here.

## 4. WorkflowAccessPolicy for cross-app steps (IMPLEMENTED)

Dapr's multi-app workflows use a `WorkflowAccessPolicy` resource to allow-list which caller
app-ids may schedule which activities on a target app (self-calls are always permitted; the
policy only restricts).

**Now implemented in this change.** `dws-controller` synthesizes one `WorkflowAccessPolicy` per
activity-invoked step (`CALL_HTTP`/`RUN_SHELL`/`RUN_SCRIPT_*`), scoped to the step's app-id, whose
rule allows the workflow's orchestrator app-id to schedule the canonical `Run` activity;
`CALL_OPENAPI` steps get none (`StackSynthesizer.workflowAccessPolicies`, applied and label-GC'd via
`StackApplier`). This is **not** what unblocks item 3 — the metadata failure occurs before any
access check — but it is the correct production security posture for cross-app dispatch, so it is no
longer deferred. Once item 3's version prerequisite is resolved, this policy is already in place.
