# ADR Review Manifest

- Status: completed
- Review date: 2026-11-26

## Review Summary

ADR review completed for this change, including the opt-in `imagePullServiceAccount.enabled` sharing of the stable zero-RBAC `<fullname>-task` ServiceAccount across non-RBAC workloads (`admin`, `console`, and un-overridden Knative steps) and per-step-image `imagePullSecrets` replacement overrides isolated onto a pull-secret-free `<fullname>-task-override` ServiceAccount. All RBAC-bearing ServiceAccounts (`controller`, `scheduleTime`, `orchestrator`, `daprReadyHook`) remain separate with least-privilege permissions, preserving workflow `use`-store boundaries (ADR 0010) and partial `values.schema.json` validation (ADR 0007). Per-image pull-secret resolution lives downstream of compiler strategy selection (ADR 0002) at Kubernetes synthesis time.

## In-Force ADRs Reviewed

- [ADR 0001](../../../docs/adr/0001-workflow-runtime-v2-decisions.md) governs per-node deployment and step transport, with scope-placement amendments in [ADR 0003](../../../docs/adr/0003-fork-as-a-flow-node.md) and [ADR 0004](../../../docs/adr/0004-scope-nodes-carry-their-own-configuration.md).
- [ADR 0002](../../../docs/adr/0002-workflow-compiler-strategy-split.md) keeps resource synthesis downstream of compiler strategy selection.
- [ADR 0005](../../../docs/adr/0005-observability-instrumentation-decisions.md) and [ADR 0009](../../../docs/adr/0009-trace-context-across-workflow-replay.md) govern workload telemetry identity and trace propagation.
- [ADR 0006](../../../docs/adr/0006-wait-and-listen-as-flow-controllers.md), [ADR 0011](../../../docs/adr/0011-call-a2a-runner-design.md), [ADR 0012](../../../docs/adr/0012-data-flow-in-v2-node-handlers.md), and [ADR 0013](../../../docs/adr/0013-time-triggers-in-dws-schedule-time.md) govern node execution and scheduler responsibilities.
- [ADR 0007](../../../docs/adr/0007-chart-feature-layers.md) (in progress) and [ADR 0008](../../../docs/adr/0008-chart-bring-your-own-infra.md) govern chart schema partialness and operator-managed infrastructure boundaries.
- [ADR 0010](../../../docs/adr/0010-use-components-as-a-shared-store.md) governs workflow configuration and Secret stores.

## New Durable ADRs Created

None. This change extends the chart's existing workload ServiceAccount and `imagePullSecrets` configuration model without introducing a new durable cross-project architectural commitment or superseding any in-force ADR.
