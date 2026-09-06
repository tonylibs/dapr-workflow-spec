# ADR 0003: Fork as a Standalone Flow Node

- **Status:** Accepted
- **Date:** 2026-09-05
- **Context:** [`docs/roadmaps/workflow-runtime-architecture-roadmap.md`](../roadmaps/workflow-runtime-architecture-roadmap.md)
  Phase 3 (`dws-flow` runtime)
- **Related:** [ADR 0001](0001-workflow-runtime-v2-decisions.md) Decision 1 (this ADR amends its
  fork corollary); [ADR 0002](0002-workflow-compiler-strategy-split.md) (defines the `FlowNode`/
  `StepNode` Composite shape this decision reuses, unchanged)
- **Supersedes:** ADR 0001 Decision 1's enumeration of which scopes get their own `dws-flow`
  instance (`main`, each `for`, `try`, `catch`, fork *branch*) implicitly excluded the fork task
  itself; the roadmap's Phase 3 entry made that explicit — "parent Flow performs `allOf`/`anyOf`
  directly for `fork` (no standalone fork node)." Both are amended by this decision.

## Context

Under the current design, every *structural* task a Flow node's task list can contain —
`for`, `try`/`catch` — is handled by the parent identically: reach the task, call that scope's own
deployed `dws-flow` app-id via `CallChildWorkflowAsync`, await the result, continue. The scope's
own behavior (loop over items, try/catch/retry) lives entirely inside the child instance; the
parent never special-cases *how* a child scope works, only that it's a child to call.

`fork` was the one exception: each fork *branch* already gets its own `dws-flow` instance (ADR
0001 Decision 1), but the fork point itself — the `allOf`/`anyOf` fan-out and combine — was
decided to run in-process inside the parent Flow node that contains the fork task, not as its own
deployed node. That makes the parent's task-dispatch code aware of one structural task kind it
must handle differently from the others.

## Decision

Fork gets its own `FlowNode` (per [ADR 0002](0002-workflow-compiler-strategy-split.md)'s
`CompiledNode` shape — no new node type), deployed like any other Flow scope. Its `children` are
the branch app-ids; it carries a new field, `forkMode: all|any`; its own `tasks` list is empty,
since it doesn't sequence anything of its own — it fans out to `children` via
`CallChildWorkflowAsync`, then `Task.WhenAll`/`WhenAny` per `forkMode`, then returns.

This restores the uniform rule for every structural task kind: from any caller's perspective,
hitting `for`/`try`/`catch`/`fork` is the same operation — call the child app at that scope's
app-id, await, continue. No caller needs to know which kind of structural scope it just called.

**Rationale:** the branches were *already* separate cross-app `dws-flow` instances before this
decision (ADR 0001 Decision 1) — fork's branches never ran in-process. This decision only moves
*where* the fan-out/combine code executes (a dedicated child instance vs. inline in the parent),
not whether the branches are remote. Given that, keeping fork as the one special-cased structural
task in the parent's dispatch code bought no real isolation — the cross-app coordination
complexity already existed either way — while costing the uniformity the rest of the classification
rules already have.

## Consequences

- **+1 pod per fork occurrence, +1 hop per fork execution** (parent → fork node → branches, instead
  of parent → branches directly) — an additional, bounded cost on top of the pod-count growth ADR
  0001 Decision 1 already flagged for Phase 4's capacity check.
- **No new failure-semantics category.** The branches are already independent child-workflow
  instances in separate apps under the current design; this only relocates the `allOf`/`anyOf`
  await, it doesn't introduce cross-app coordination that wasn't already there.
- **`anyOf` cancellation of losing branches is unchanged** — already awkward under the current
  design (cross-app child-workflow instances aren't auto-cancelled by `Task.WhenAny` today), and
  this decision doesn't make it better or worse. Still an open implementation question for
  whoever builds the fork `FlowNode`'s `WhenAny` path in Phase 3.
- **Schema impact, on an already-merged file:** `openspec/schemas/single-node-definition.schema.json`
  (shipped as part of the completed `workflow-runtime-v2-phase0-scaffolding` change) needs a
  `fork` value added to the flow branch's `scope` enum (currently `main`/`for`/`try`/`catch`/
  `forkBranch` — note `forkBranch` already exists for the branches themselves and is unaffected),
  plus a new `forkMode: all|any` field on flow nodes where `scope: fork`. Not applied by this ADR —
  a tracked follow-up, since Phase 0's schema already shipped.
- Amends ADR 0001 Decision 1: read its scope enumeration as `main`, each `for`, `try`, `catch`,
  `fork`, and fork branch — `fork` added alongside the branches it already listed.

## Non-goals

Doesn't change the fork branch node model (`forkBranch` `FlowNode`s are unaffected). Doesn't
resolve `anyOf`'s losing-branch cancellation mechanics — still open, unchanged by this decision.
Doesn't touch the schema file itself — the delta above is documented, not yet implemented.
