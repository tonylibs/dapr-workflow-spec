# ADR Review Manifest

- Status: completed
- Review date: 2026-11-21

## Review Summary

ADR review completed for this change. The design sets a default log level for one framework category and adds success log lines in two .NET packages. That is a tactical logging-configuration fix. It sets no long-term architectural pattern, technology, boundary or cross-component contract. The log-format contract stays in `docs/log-format.md` and the `structured-log-correlation` spec, and the change extends that spec instead of recording an ADR. No in-force ADR is diverged from, so nothing is superseded.

## In-Force ADRs Reviewed

- `docs/adr/0005-observability-instrumentation-decisions.md`: observability instrumentation. Makes no log-level or probe-logging decision, so this change is consistent with it.
- `docs/adr/0009-trace-context-across-workflow-replay.md`: trace context across replay. Not affected, because span and trace propagation are unchanged.
- `docs/adr/0013-time-triggers-in-dws-schedule-time.md`: `dws-schedule-time` architecture and start mechanism. Not affected, because start semantics, fire keys and the job callback contract are unchanged.
- `docs/adr/0001` through `0004`, `0006`, `0010`, `0011`, `0012`: runtime-v2, compiler, flow-node, `use` store, A2A and data-flow decisions. Not related to logging.
- `docs/adr/0007` (in progress) and `0008` (proposed): not in force, and only about chart layering.

## New Durable ADRs Created

- None. No major durable architectural decisions were introduced, and no new repository-level ADR files were created.
