# ADR 0009: Trace Context Across Dapr Workflow Replay

- **Status:** Accepted, with the scope limits listed under [Limits of the evidence](#limits-of-the-evidence).
- **Date:** 2026-10-08
- **Context:** [`docs/roadmaps/observability.md`](../roadmaps/observability.md) design decision 1
  ("Trace context across Dapr Workflow replay"), deferred from Phase 0-A(c) to observation. The
  observation is [Phase 2a live evidence](../roadmaps/observability-phase2a-evidence.md).
- **Related:** [ADR 0005](0005-observability-instrumentation-decisions.md) (identity and the single
  sampling root this ADR relies on).

## Context

A Dapr Workflow function is re-executed from the start each time the engine replays it, so any code
in it that created a span would create it again on every replay. The roadmap flagged two ways this
could break the design: duplicated spans per replay, or a fresh trace per replay. It also asked what
the "workflow-level span" is and whether replayed activity work is suppressed.

The compiled orchestrator (Phase 2a) is the first thing in this repository that runs a Dapr
Workflow with the OTel Java agent attached, so it is where this could be observed.

## What was observed

One `tracedemo` workflow (call, 45 s wait, call), a Java agent on the orchestrator, a tracing-only
Dapr sidecar, and a restart of the orchestrator pod during the wait. Full numbers are in the
evidence file; the conclusions that matter here:

- The engine (daprd) emits the workflow-level spans: one root `create_orchestration||<wf>`, one
  `orchestration||<wf>`, one `timer` per timer, and one `activity||<name>` per activity execution.
  They are anchored to persisted workflow state, not to the replay: after a pod kill the
  replacement pod's spans joined the **same trace under the same root**, with the **same set of
  activity executions** (identical task IDs, none repeated) as a run with no restart.
- The Java SDK adds one `activity:<name>` span under each `activity||<name>`, and the agent traces
  the calls the activity code makes beneath it. Activity code therefore does run under a span
  context.
- Replay itself produced no spans: three runs, one of them across a restart, had the same 51-span
  structure.
- An incoming `traceparent` on the start request joined only the Dapr HTTP start span. The workflow
  spans form a separate trace; the caller's context is not inherited.
- A run with a *non-deterministic* workflow function did produce more spans (83 vs 51), a repeated
  `timer` span and a stalled instance. See "Consequences".

## Decision

1. **The engine owns the workflow-level spans. Do not create spans in workflow code.** No custom
   "workflow span", no manual `startSpan` in the interpreter's workflow functions. The engine's
   `create_orchestration`/`orchestration`/`timer`/`activity` spans are the workflow-level record,
   and they are replay-stable.
2. **Spans belong in activity code, which the agent traces automatically.** An activity runs once
   per execution, so a span there is not duplicated by replay. New instrumentation for compiled
   nodes goes inside activities (or in the step services), never in the workflow function.
3. **Replayed work is not suppressed by us, because it is not emitted.** There is nothing to filter:
   no `isReplaying` guard is needed for spans, and none should be added.
4. **A duplicated span or a restarted trace is a determinism bug, not a tracing bug.** If either
   appears, look first for workflow-function code that schedules different work on first execution
   than on replay. Do not respond by changing the span model.
5. **Cross-trace linkage from a caller to the workflow is not provided by this design.** The trace
   of a workflow instance starts at the engine. Joining it to the caller (the controller, an HTTP
   client) needs a link or an attribute carrying the caller's trace ID, which is a separate
   decision; it is not assumed to work through the Dapr start call.

## Consequences

- No change to the Phase 2a span model, the sidecar `Configuration`, or sampling is needed for
  replay. The roadmap's accepted risk — that replay might "reach back into Phase 2a's span model" —
  did not materialise.
- **The committed interpreter is not deterministic on replay.** `InterpreterWorkflow` publishes a
  lifecycle event from `catch (RuntimeException)` blocks around awaited tasks. The Dapr Java SDK
  signals "waiting for a result" with `OrchestratorBlockedException`, a `RuntimeException`, so the
  catch block schedules an extra activity during the blocked unwind and the task IDs differ between
  the first execution and replay. On the live cluster this produced spurious `task.failed` /
  `instance.failed` events, `Discarding a potentially duplicate TaskCompleted` warnings, extra
  spans, and an instance that never advanced past its first timer. This is a correctness defect
  independent of tracing, and decision 4 is why it surfaced as a span anomaly. It must be fixed in
  `dws-orchestrator` (rethrow `OrchestratorBlockedException` before any side effect in each catch).
  This ADR does not make that change.
- Step calls are **not** visible in the trace today: their remote activities run in apps whose
  sidecars and code are untraced. That is Track B (Phase 2b/3), unchanged by this ADR.
- Decision 5 leaves "controller → orchestrator in one trace" unresolved for the roadmap's Phase 2a
  deliverable. The controller does not currently start instances, so there is no concrete caller to
  design for yet.

## Limits of the evidence

One cluster, one replica, one workflow shape, Redis as the actor store, Dapr 1.18.1, Java SDK
1.18.0, OTel Java agent 2.31.1, a single pod kill during a timer. Not exercised: activity retries,
`for`/`fork`/scope child workflows (`ScopeRunnerWorkflow`, `ForkBranchWorkflow`), `ContinueAsNew`,
event waiting, multiple restarts, multiple replicas, and chart-default sampling (`0.1`). The probe
orchestrator carried a local, uncommitted guard for the defect above; see the evidence file's
"Deviations". Revisit this ADR if a later workflow shape shows duplicate or split traces *after*
determinism has been ruled out.

## Non-goals

- Fixing `InterpreterWorkflow`, the controller/chart defects listed in the evidence file, or
  introducing a workflow-level span or caller linkage.
