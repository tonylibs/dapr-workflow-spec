# ADR Review Manifest

- Status: completed
- Review date: 2026-11-05

## Review Summary

ADR review completed for this change. Repository ADRs are in `docs/adr/` (per `openspec/config.yaml`).
No in-force ADR is superseded. One new durable ADR records the trigger-component split, the Jobs
API choice, the start contract and the timing rules.

## In-Force ADRs Reviewed

- `docs/adr/0001-workflow-runtime-v2-decisions.md` (accepted, partly superseded by 0003/0004 on
  scope enumeration). v1 and v2 coexist, and 6a targets v1 only.
- `docs/adr/0002-workflow-compiler-strategy-split.md` (accepted). Trigger config travels in
  `DeploymentPlan`, and the v2 strategy rejects `schedule`.
- `docs/adr/0003-fork-as-a-flow-node.md` (accepted). Not affected.
- `docs/adr/0004-scope-nodes-carry-their-own-configuration.md` (accepted). Not affected.
- `docs/adr/0005-observability-instrumentation-decisions.md` (accepted). The scheduler follows the
  `DWS_LOG_FORMAT` contract.
- `docs/adr/0006-wait-and-listen-as-flow-controllers.md` (accepted). `listen` stays in the runtime,
  and `schedule.on` is left to 6b.
- `docs/adr/0009-trace-context-across-workflow-replay.md` (accepted). Not affected.
- `docs/adr/0010-use-components-as-a-shared-store.md` (accepted). Per-host env stamped by the
  controller. The scheduler is not a workflow host and resolves no `use` entries.
- `docs/adr/0011-call-a2a-runner-design.md` (accepted). Not affected.
- `docs/adr/0012-data-flow-in-v2-node-handlers.md` (accepted). Not affected, because 6a starts only
  v1 instances.
- Not in force, read for direction only:
  - `docs/adr/0007-chart-feature-layers.md` (in progress): "triggers are Core";
  - `docs/adr/0008-chart-bring-your-own-infra.md` (proposed): state store name.

## New Durable ADRs Created

- `docs/adr/0013-time-triggers-in-dws-schedule-time.md`. Covers:
  - triggers outside the controller, one component per trigger family;
  - Dapr Jobs API over cron binding;
  - the runtime-neutral start contract with deterministic instance IDs;
  - timing and failure rules.
