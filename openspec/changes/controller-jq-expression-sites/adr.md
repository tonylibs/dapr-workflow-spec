# ADR Review Manifest

- Status: completed
- Review date: 2026-11-27

## Review Summary

ADR review completed for this change. The supersession graph is partial only: ADR 0003 and ADR 0004
each supersede part of ADR 0001 Decision 1, namely which scopes and fork branches get their own
`dws-flow`. No ADR is superseded as a whole, so ADRs 0001 to 0014 are in force, minus those
enumerations. This change refines ADR 0014. It does not reverse any decision there, so the new ADR
supersedes nothing and ADR 0014 is not edited.

## In-Force ADRs Reviewed

- [ADR 0001](../../../docs/adr/0001-workflow-runtime-v2-decisions.md): per-node app IDs and the
  DNS-1123 sanitization rule that sites reuse.
- [ADR 0002](../../../docs/adr/0002-workflow-compiler-strategy-split.md): the compiler strategy split.
  The finder sits beside both strategies, not inside one.
- [ADR 0004](../../../docs/adr/0004-scope-nodes-carry-their-own-configuration.md): the derived
  `<task>.catch`, `<task>.do` and `<task>.try` node IDs that site owners align with.
- [ADR 0006](../../../docs/adr/0006-wait-and-listen-as-flow-controllers.md): `listen` as a controller
  plus a bridge, and `schedule.on` as an event source.
- [ADR 0010](../../../docs/adr/0010-use-components-as-a-shared-store.md): `use` components. The
  finder resolves `use.retries` and `use.errors`.
- [ADR 0012](../../../docs/adr/0012-data-flow-in-v2-node-handlers.md): data flow in Step nodes and
  the builtin policy.
- [ADR 0013](../../../docs/adr/0013-time-triggers-in-dws-schedule-time.md): the trigger-path
  precedent for 6b, and the contract-pinning test pattern.
- [ADR 0014](../../../docs/adr/0014-dws-jq-expression-function.md): Decisions 3, 6, 7 and 9 (O-2,
  O-4, O-5), the inventory, and the 2j notes.
- ADRs 0003, 0005, 0007, 0008, 0009 and 0011 were read and are not affected (fork, observability,
  chart layers, trace context, A2A runner).

## New Durable ADRs Created

- [ADR 0015: Expression Sites Follow the Rule of Each Field's Current Evaluator](../../../docs/adr/0015-expression-sites-follow-todays-evaluators.md):
  - one detection rule per field, taken from the evaluator that runs it today;
  - the owner-path and site-name identity;
  - declared variables;
  - the finder kept out of `compile()` until 2m.2;
  - six corrections to ADR 0014 found while specifying 2m.1.
