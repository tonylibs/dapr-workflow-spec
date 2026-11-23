# ADR Review Manifest

- Status: completed
- Review date: 2026-11-23

## Review Summary

ADR review completed for this change. The step account follows the existing chart-to-controller ServiceAccount pattern. ADR 0010's ConfigMap and Secret permissions apply to workflow `use`-store hosts. This account serves the generated Knative step workloads, so it needs no grant for those stores.

## In-Force ADRs Reviewed

- [ADR 0001](../../../docs/adr/0001-workflow-runtime-v2-decisions.md) governs per-node deployment and step transport, with scope-placement amendments in [ADR 0003](../../../docs/adr/0003-fork-as-a-flow-node.md) and [ADR 0004](../../../docs/adr/0004-scope-nodes-carry-their-own-configuration.md).
- [ADR 0002](../../../docs/adr/0002-workflow-compiler-strategy-split.md) keeps resource synthesis downstream of compiler strategy selection.
- [ADR 0005](../../../docs/adr/0005-observability-instrumentation-decisions.md) and [ADR 0009](../../../docs/adr/0009-trace-context-across-workflow-replay.md) govern workload telemetry identity and trace propagation.
- [ADR 0006](../../../docs/adr/0006-wait-and-listen-as-flow-controllers.md), [ADR 0011](../../../docs/adr/0011-call-a2a-runner-design.md), [ADR 0012](../../../docs/adr/0012-data-flow-in-v2-node-handlers.md), and [ADR 0013](../../../docs/adr/0013-time-triggers-in-dws-schedule-time.md) govern node execution and scheduler responsibilities.
- [ADR 0010](../../../docs/adr/0010-use-components-as-a-shared-store.md) governs workflow configuration and Secret stores.

## New Durable ADRs Created

None. This change applies the existing workload identity pattern and Kubernetes image pull behavior. No major durable architectural decisions were introduced and no new repository-level ADR files were created.
