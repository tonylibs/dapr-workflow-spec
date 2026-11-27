# ADR Review Manifest

- Status: completed
- Review date: 2026-11-27

## Review Summary

ADR review completed for this change against `docs/adr/` (sequence 0001–0014; next available number is 0015). This change directly implements the already-accepted architectural commitments in ADR 0006 (`wait` and `listen` as leaf Flow controllers) and ADR 0012 Decision 3 (companion `<task>.dataflow` Step nodes for structural tasks declaring data flow), and fixes the wire field names (`wait`, `to`, `timeout`, `read`, `dataflow`) in the single-node definition contract and schema as ADR 0006 explicitly delegated to the implementing schema change. No prior ADR is superseded or revisited, and no new repository-level ADR files were created.

## In-Force ADRs Reviewed

- `docs/adr/0001-workflow-runtime-v2-decisions.md` — Accepted (amended by ADR 0003 and ADR 0004): per-node Dapr app IDs and uniform Step layer.
- `docs/adr/0002-workflow-compiler-strategy-split.md` — Accepted: `WorkflowCompiler` two-strategy split and `CompiledNode` (`FlowNode`/`StepNode`) tree.
- `docs/adr/0003-fork-as-a-flow-node.md` — Accepted (generalized by ADR 0004): uniform parent-dispatch rule across Flow and Step children.
- `docs/adr/0004-scope-nodes-carry-their-own-configuration.md` — Accepted (supersedes ADR 0001 Decision 1's `forkBranch` enumeration): Sequencer (`main`/`do`) vs Controller (`for`/`try-catch`/`fork`) shapes; extended by ADR 0006.
- `docs/adr/0005-observability-instrumentation-decisions.md` — Accepted (unrelated to this change).
- `docs/adr/0006-wait-and-listen-as-flow-controllers.md` — Accepted (for placement, broadcast semantics, and per-node bridge): `wait` and `listen` compile as leaf Flow controllers carrying their duration / `to` and timeout verbatim, amending `docs/roadmaps/workflow-runtime-architecture.md`.
- `docs/adr/0007-chart-feature-layers.md` — Accepted (unrelated to this change).
- `docs/adr/0009-trace-context-across-workflow-replay.md` — Accepted (unrelated to this change).
- `docs/adr/0010-use-components-as-a-shared-store.md` — Accepted (unrelated to this change; `UseResolver` wiring for `listen` timeout belongs to 3f).
- `docs/adr/0011-call-a2a-runner-design.md` — Accepted (unrelated to this change).
- `docs/adr/0012-data-flow-in-v2-node-handlers.md` — Accepted (2026-11-02): Decision 3 emits one companion `<task>.dataflow` Step node per structural task declaring `input`/`output`/`export`, and none when absent.
- `docs/adr/0013-time-triggers-in-dws-schedule-time.md` — Accepted (unrelated to this change).
- `docs/adr/0014-dws-jq-expression-function.md` — Accepted (2026-11-24): leaves ADR 0012 Decision 3 unchanged.

*(Note: `docs/adr/0008-chart-bring-your-own-infra.md` is Proposed, not yet accepted, and unrelated to this change.)*

## New Durable ADRs Created

- None - no major durable architectural decisions were introduced and no new repository-level ADR files were created.
