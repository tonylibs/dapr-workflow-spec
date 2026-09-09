## Context

`dws-controller`'s `V2StructuralCompiler` walks a parsed DSL 1.0 definition into a `CompiledNode`
tree (`NodeClassifier`), where each node renders a `specText` pinned by
`openspec/schemas/single-node-definition.schema.json`. Phase 1 (`phase-1-structural-classification`,
archived 2026-09-06) built this classification pass and got `fork` right — its own `FlowNode` with
`forkMode` and empty `tasks`. It never applied the same treatment to `for` or `try`/`catch`: those
scopes render their own configuration nowhere, because the node that *is* the scope holds the task
list directly in `tasks` instead of delegating to a child, leaving no room for `each`/`in` or
`errors`/`retry` on that node's own `specText`. ADR 0004 (Accepted,
`docs/adr/0004-scope-nodes-carry-their-own-configuration.md`) names this a defect, not a design
choice, and is the gate Phase 2 (`dws-step`) and Phase 3 (`dws-flow`) are blocked on — both read this
graph.

`NodeClassifier.tryFlow`'s own Javadoc already flags the gap as an open question blocking Phase 2;
this change closes it.

Stakeholders: Phase 2/3 implementers (consume the graph this change reshapes), anyone maintaining
`docs/roadmaps/workflow-runtime-architecture.md`'s worked examples (checked-in fixtures pinned
against that doc).

## Goals / Non-Goals

**Goals:**
- Every compiled Flow node is one of exactly two shapes — sequencer (`main`, `do`) or controller
  (`for`, `try-catch`, `fork`) — matching ADR 0004.
- A controller's own `specText` carries that scope's configuration; nothing needed to interpret a
  scope survives only on the parent's verbatim `tasks` entry.
- `forkBranch` retired; a fork's `children` point straight at each branch root task's own node.
- All 6 golden fixture sets regenerated and passing byte-for-byte against the new classifier output.
- `docs/roadmaps/workflow-runtime-architecture.md` kept in sync with the schema and classifier.

**Non-Goals:**
- No DSL semantic change — every definition that compiled before compiles after, to a differently
  shaped graph (ADR 0004 Non-goals, inherited here).
- No change to `V1OrchestratorCompiler` or `CompilerProducer`'s v1 default.
- No resolution of `anyOf`'s losing-branch cancellation (ADR 0003, still open).
- No semantic validation beyond what v2 already has — tracked separately, gates the Phase 5 cutover.
- No `dws-step`/`dws-flow` runtime work — this change only unblocks it.

## Decisions

### D1: Controller fields live on `FlowScope`, not a new node type

- **Choice**: add `each`/`in`/`at`/`while` (for) and `errors`/`retry` (try-catch) as new `Optional`
  fields on the existing `FlowScope` record, alongside the existing `catchAppId`/`forkMode`, rather
  than introducing separate `FlowNode` subtypes per scope kind.
- **Rationale**: `FlowNode` is already generic over `FlowScope`; `CompiledNode` stays a two-member
  sealed interface (`FlowNode`/`StepNode`) per ADR 0002, which downstream dispatch (Phase 2/3, and
  `StackSynthesizer` in Phase 4) already switches on. A per-scope-kind `FlowNode` subtype would leak
  that distinction into every consumer of the sealed type for no behavioral gain — the two-shape
  split lives entirely in what `FlowScope` carries and in `SingleNodeDefinition.flow`'s rendering,
  not in the Java type hierarchy.
- **Considered alternative**: a `ControllerNode`/`SequencerNode` split under `CompiledNode`. Rejected
  — it would ripple through every existing `FlowNode` pattern match (`StackSynthesizer` stub,
  Phase 2/3 designs already assume `FlowNode`/`StepNode`) for a distinction the wire schema already
  expresses via `scope` and empty `tasks`.

### D2: New `do`-sequencer children are synthesized nodes, not literal DSL tasks

- **Choice**: the `<task>.do` (for) and `<task>.try` (try-catch) children are compiler-synthesized —
  same pattern as the existing `<task>.catch` node, which already wraps `catch.do` in a sequencer
  with no DSL keyword of its own.
- **Rationale**: matches the existing precedent exactly (`NodeClassifier.catchFlow`/`doFlow` already
  produce this shape for `catch.do` and a bare `do` task). No new derivation mechanism needed —
  `NodeNaming` gains two more `<parent>.<role>` helpers alongside `catchNodeId`.
- **Considered alternative**: keep the guarded/loop list as a synthetic field on the controller node
  itself instead of a real child. Rejected — the controller's `children` map is how a runtime finds
  what to call (ADR 0003's rule: "call the child at that scope's app ID, await, continue"); a special
  non-child field would require Phase 2/3 to special-case `for`/`try-catch` dispatch instead of
  uniformly calling children, reinstating the per-kind special-casing ADR 0003 removed.

### D3: `forkBranch` retirement removes a Java method, not just a scope value

- **Choice**: delete `NodeClassifier.branchFlow` and `NodeNaming.branchNodeId` outright; `forkFlow`
  calls `classifyTask` directly on each branch and uses the result as-is.
- **Rationale**: ADR 0004's own test — "a node shape earns its place by carrying something its
  children cannot see" — `forkBranch` carried nothing. Keeping the method as a pass-through would
  leave dead abstraction for no reason; the branch root task's own node already has everything (its
  own `nodeId`, `appId`, `specText`).
- **Considered alternative**: keep `branchFlow` as a thin pass-through for symmetry with
  `catchFlow`/`doFlow`. Rejected — those wrap a DSL-supplied task *list* with no node of its own;
  `branchFlow` wrapped a single already-classified node, which is a no-op wrapper by construction.

### D4: Schema enforces `tasks: []` on every controller via a single `if`/`then` per scope value

- **Choice**: extend the existing `if scope == fork then require forkMode` pattern
  (`single-node-definition.schema.json`) with parallel `if`/`then` blocks per controller scope,
  rather than a blanket `oneOf` over scope-specific sub-schemas.
- **Rationale**: matches the file's existing idiom exactly (one `if`/`then`/`else` pair already
  present for `forkMode`); a `oneOf`-per-scope rewrite would touch every existing scope's schema
  fragment for a rule that's naturally additive.
- **Considered alternative**: a JSON Schema `oneOf` with one branch per scope, each declaring its
  full field set. Rejected as a larger diff with no behavioral difference — draft-07 `if`/`then`
  composability already expresses "field X required/forbidden exactly when scope is Y".

## Risks / Trade-offs

- [Risk] Regenerating all 6 golden fixture sets by hand risks a byte-for-byte mismatch with what
  `NodeClassifier` actually emits. → Mitigation: fixtures are generated by running the updated
  compiler against each `definition.yaml` and capturing its output, never hand-authored, per the
  existing fixture-generation convention (tasks.md task group 4 pins this).
- [Trade-off] Node counts increase for every `for`/`try-catch` scope (one extra `do`-sequencer pod
  per scope) — accepted in ADR 0004 as the cost of making the rule uniform; fork saves one node per
  branch, partially offsetting it.
- [Trade-off] Breaking schema change to `single-node-definition.schema.json`'s `scope` enum. →
  Mitigation: no consumer outside `dws-controller`'s own tests reads this shape yet (Phase 2/3 not
  started); this is the last chance to change it for free, as ADR 0004 itself notes ("must land
  before `dws-step` and `dws-flow` are built against the current shape").

## Migration Plan

No deployed-system migration — v2 is not selected by default (`CompilerProducer` resolves to v1
unless `compiler.version` is explicitly set to `v2`), so this change ships behind that existing flag
with no rollout of its own. Rollback is reverting the commit; nothing external depends on the v2
shape yet.

## Open Questions

None outstanding — ADR 0004 resolved the one open question
(`openspec/changes/archive/2026-09-08-phase-1-structural-classification/design.md`'s recorded Open
Question) that this change exists to close.
