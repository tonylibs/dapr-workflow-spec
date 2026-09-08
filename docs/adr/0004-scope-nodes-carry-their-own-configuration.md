# ADR 0004: Scope Nodes Carry Their Own Configuration

- **Status:** Accepted
- **Date:** 2026-09-08
- **Context:** [`docs/roadmaps/workflow-runtime-architecture-roadmap.md`](../roadmaps/workflow-runtime-architecture-roadmap.md)
  Phase 2 (`dws-step`) and Phase 3 (`dws-flow`) — this decision is the gate both were blocked on.
- **Related:** [ADR 0001](0001-workflow-runtime-v2-decisions.md) (per-node app IDs),
  [ADR 0002](0002-workflow-compiler-strategy-split.md) (the `CompiledNode` shape and type-directed
  dispatch), [ADR 0003](0003-fork-as-a-flow-node.md) (fork as its own Flow node — this decision
  generalizes its shape)
- **Supersedes:** ADR 0001 Decision 1's enumeration of fork *branches* as their own `dws-flow`
  instances, and the `forkBranch` scope it implies. Resolves the Open Question recorded in
  `openspec/changes/archive/2026-09-08-phase-1-structural-classification/design.md`.

## Context

Phase 1 compiled every scope into its own deployed node, but a scope node's pinned definition does
not carry that scope's own configuration. The configuration survives only in the *parent's* verbatim
`tasks` entry, because the parent renders the whole task object.

Observed on the shipped fixtures. `nested-try-for-catch` puts the loop configuration on the parent:

```json
// dws-controller/src/test/resources/v2/nested-try-for-catch/nodes/fulfill-order.json
"tasks": [ { "reserveItems": { "for": { "each": "item", "in": ".items" }, "do": [ … ] } } ]
```

while the node that *is* the loop has the body and nothing to iterate with:

```json
// …/nodes/reserve-items.json
{ "nodeId": "reserve-items", "scope": "for",
  "tasks":    [ { "reserveItem": { "call": "http", … } } ],
  "children": { "reserveItem": "reserve-item" } }
```

`raise-and-recovery` is the same shape for error handling: `process-payment.json` carries
`"catch": "process-payment-catch"` — which node recovers — but not the `errors.with.status: 402`
filter that decides *whether* to recover. That filter is on `guarded-payment-main.json`.

This contradicts ADR 0003, which states:

> The scope's own behavior (loop over items, try/catch/retry) lives entirely inside the child
> instance; the parent never special-cases *how* a child scope works, only that it's a child to call.

Under that decision a `for` Flow must iterate its own items and a `try` Flow must apply its own
filter, and from its own definition neither can. The only alternative — the parent reads the config
out of its own `tasks` entry and drives the child — reinstates exactly the per-kind special-casing
ADR 0003 was written to remove.

The tell that this was an oversight rather than a division of labour: `forkMode` *was* added to the
single-node schema during Phase 1, for precisely this reason (a fork node cannot join without
knowing whether it races or waits). The same reasoning was never applied to `for` or to `catch`.

Left unresolved, the concrete failure is silent: a Phase 3 implementer reads a `try` node's
definition, builds a Flow that catches every error, and ignores the `status: 402` the author wrote.
Nothing in the definition it loaded lets it detect the omission.

## Decision

**Every compiled Flow node is one of exactly two shapes, and a scope's configuration always lives on
the node that implements that scope.**

| Shape | Scopes | `tasks` | `children` keyed by | Carries |
|---|---|---|---|---|
| **Sequencer** | `main`, `do` | its own task list, in source order | task name | nothing but its list |
| **Controller** | `for`, `try-catch`, `fork` | empty | role (`do`, `try`, `catch`) or branch task name | that scope's configuration |

A controller holds only configuration and delegates each task list it owns to a `do` child. A
sequencer holds a task list and no configuration. `main` is the one sequencer that is not derived:
it *is* the document's top-level `do`.

Concretely:

- **`for`** carries `each` / `in` / `at` / `while`, and one child under the key `do`. It calls that
  child once per item, deciding the iteration itself.
- **`try-catch`** replaces the separate `try` and `catch` scopes with a single node carrying
  `errors` and `retry`, with children under `try` and `catch`. Both children are `do` sequencers.
  The existing `catch` field continues to name the recovery child's app ID.
- **`fork`** is unchanged from ADR 0003 — `forkMode`, empty `tasks` — except that its children are
  now the branch root tasks' own nodes.
- **`forkBranch` is retired.** A DSL branch is a `taskList` item, which is exactly one task, so a
  branch scope only ever wrapped a single-element list. Fork's `children` point straight at each
  branch root task's compiled node.

**Rationale:** it makes ADR 0003's rule true rather than aspirational. Every caller performs the
same operation — call the child at that scope's app ID, await, continue — because every scope node
is now self-sufficient. It also generalizes the shape ADR 0003 already chose for fork (configuration
on the node, empty `tasks`, children to call) to every structural scope, instead of leaving fork as
the only scope built that way.

Retiring `forkBranch` follows from the same test applied honestly: a node shape earns its place by
carrying something its children cannot see. `forkBranch` carried nothing.

## Consequences

- **`openspec/schemas/single-node-definition.schema.json` changes.** The `scope` enum becomes
  `main`, `do`, `for`, `try-catch`, `fork` — `try`, `catch` and `forkBranch` are removed. Flow nodes
  gain the loop configuration (valid only when `scope: for`) and `errors`/`retry` (valid only when
  `scope: try-catch`). `tasks` is constrained to empty on every controller. This is the third change
  to a schema shipped in Phase 0; it must land before `dws-step` and `dws-flow` are built against
  the current shape.
- **Node counts move in both directions.** `order-fulfillment` goes 7 → 9 (a `do` node for the try
  body, another for the loop body); `notify-order` goes 8 → 7 (two `forkBranch` nodes removed, one
  `do` added). Loops and try/catch cost a pod; forks save one per branch.
- **A fork's branches are no longer uniformly Flows.** A branch rooted at a `set` compiles to a
  `StepNode`, so the fork reaches it with `CallActivityAsync` rather than `CallChildWorkflowAsync`.
  This amends ADR 0001 Decision 1's "each fork branch flow", and is consistent with ADR 0002, which
  already fixed dispatch as a function of the child's sealed type — `StepNode` means activity,
  `FlowNode` means child workflow. Fork stops being special here too.
- **The `<fork-task>.branch.<branch-root-task>` derived identifier is retired**, along with
  `forkBranch`. A branch root keeps its own task name, matching the rule already adopted for a
  structural task at a branch root.
- **New derived identifiers** for the `do` children, following the existing `<try-task>.catch`
  convention: `<for-task>.do`, `<try-task>.try`, `<try-task>.catch`. Sanitized as ever —
  `reserveItems.do` → `reserve-items-do`.
- **The "children keys match task names" requirement is scoped to sequencers.** Controllers key
  `children` by role, and have no `tasks` to match against. The delta spec's scenario is narrowed
  rather than weakened.
- **All six golden fixture sets are regenerated.** They are the pinned contract Phases 2-4 build
  against, and they currently encode the defect.
- **Phase 2 is unblocked.** This was the gate recorded in the archived Phase 1 design.

## Non-goals

Does not change DSL semantics — every definition that compiled before compiles after, to a
differently shaped graph. Does not change `V1OrchestratorCompiler`, and does not flip
`CompilerProducer`'s default away from v1. Does not resolve `anyOf`'s losing-branch cancellation,
which ADR 0003 left open and which remains open. Does not add the semantic validation v2 still
lacks — that is tracked separately and gates the Phase 5 cutover, not Phase 2.
