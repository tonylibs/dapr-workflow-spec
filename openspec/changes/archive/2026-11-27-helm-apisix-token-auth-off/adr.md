# ADR Review Manifest

- Status: completed
- Review date: 2026-11-26

## Review Summary

ADR review completed for this change. All ADRs in `docs/adr/` (0001–0014) were read, and none has a
`Supersedes` link. The design makes one tactical chart-configuration fix: the default bundled-APISIX
`secretKeyRef` becomes `optional: true`, and the drift guard and tests stay as they are. It sets no
long-term architectural commitment, so no ADR is needed. The Phase 10 machine-auth design (D4,
archived change `2026-11-18-auth-phase-10-api-tokens`) stays in force unchanged.

## In-Force ADRs Reviewed

- ADR 0001 – Workflow Runtime v2 (Accepted): not affected, chart-only change.
- ADR 0002 – WorkflowCompiler strategy split (Accepted): not affected.
- ADR 0003 – Fork as a flow node (Accepted): not affected.
- ADR 0004 – Scope nodes carry their own configuration (Accepted): not affected.
- ADR 0005 – Observability instrumentation (Accepted): not affected.
- ADR 0006 – `wait`/`listen` as flow controllers (Accepted in part): not affected.
- ADR 0009 – Trace context across replay (Accepted): not affected.
- ADR 0010 – `use` components as a shared store (Accepted): not affected.
- ADR 0011 – `call: a2a` runner design (Accepted): not affected.
- ADR 0012 – Data flow in v2 node handlers (Accepted): not affected.
- ADR 0013 – Time triggers in `dws-schedule-time` (Accepted): not affected. Its token wiring is untouched.
- ADR 0014 – `dws-jq` expression function (Accepted): not affected.
- Not in force, reviewed as context: ADR 0007 (chart feature layers, under discussion) and ADR 0008
  (bring your own infra, Proposed). The change is consistent with both. Design "Open Questions"
  notes that if ADR 0007 is accepted, it may later make APISIX without auth a render error.

## New Durable ADRs Created

- None. No major durable architectural decisions were introduced.
