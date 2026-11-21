# ADR Review Manifest

- Status: completed
- Review date: 2026-11-21

## Review Summary

ADR review completed for this change. The shared parser fits ADR 0010's version-scoped store and
boot-time resolver. Validation at the sequencer's existing dispatch boundary preserves the runtime
and node boundaries described in ADRs 0001, 0004 and 0012.

## In-Force ADRs Reviewed

- [0001 — Per-node deployment and step transport](../../../docs/adr/0001-workflow-runtime-v2-decisions.md), as partially superseded by 0003 and 0004
- [0002 — Workflow compiler strategies](../../../docs/adr/0002-workflow-compiler-strategy-split.md)
- [0003 — Fork as a Flow node](../../../docs/adr/0003-fork-as-a-flow-node.md)
- [0004 — Scope node configuration](../../../docs/adr/0004-scope-nodes-carry-their-own-configuration.md)
- [0005 — Observability instrumentation](../../../docs/adr/0005-observability-instrumentation-decisions.md)
- [0006 — Wait and listen Flow nodes](../../../docs/adr/0006-wait-and-listen-as-flow-controllers.md), accepted portions
- [0009 — Trace context across replay](../../../docs/adr/0009-trace-context-across-workflow-replay.md)
- [0010 — Version-scoped use store](../../../docs/adr/0010-use-components-as-a-shared-store.md)
- [0011 — A2A runner design](../../../docs/adr/0011-call-a2a-runner-design.md)
- [0012 — Data flow in v2 node handlers](../../../docs/adr/0012-data-flow-in-v2-node-handlers.md)
- [0013 — Time triggers](../../../docs/adr/0013-time-triggers-in-dws-schedule-time.md)

## New Durable ADRs Created

The change introduces no major durable architectural decisions. The existing ADRs cover the affected
boundaries, so the review required no new repository-level ADR files.
