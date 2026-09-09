## Why

`V2StructuralCompiler`'s `for`, `try`, and `catch` scopes render a `specText` that omits their own
scope's configuration — `each`/`in` for a loop, `errors`/`retry` for a guarded try/catch — leaving
it stranded on the parent's verbatim `tasks` entry, where a Phase 2/3 runtime reading the scope's own
node can never see it. `forkMode` got this right during Phase 1; `for`/`catch` never did. ADR 0004
(Accepted) fixes this by making every Flow node one of exactly two shapes — sequencer (owns a task
list, no configuration) or controller (owns its scope's configuration, delegates to children) — and
is the gate Phase 2 (`dws-step`) and Phase 3 (`dws-flow`) are blocked on. This change implements it.

## What Changes

**`openspec/schemas/single-node-definition.schema.json`**
- From: `scope` enum is `main`, `do`, `for`, `try`, `catch`, `fork`, `forkBranch`; no scope-specific
  configuration fields; `tasks` unconstrained except on `fork`.
- To: `scope` enum is `main`, `do`, `for`, `try-catch`, `fork`. `each`/`in`/`at`/`while` valid only
  when `scope: for`. `errors`/`retry` valid only when `scope: try-catch`. `tasks` is `maxItems: 0`
  whenever `scope` is a controller (`for`, `try-catch`, `fork`).
- Reason: ADR 0004 Decision — a scope's configuration lives on the node that implements that scope.
- Impact: breaking to any `specText` consumer written against the current enum. None exists yet
  outside `dws-controller`'s own tests — Phase 2/3 have not started reading this shape.

**`NodeClassifier` (`dws-controller/src/main/java/io/dws/controller/compile/v2`)**
- From: a `for`/`try` node holds its own guarded/loop task list directly in `tasks`; a fork branch is
  wrapped in an intermediate `forkBranch` node.
- To: `for` and `try-catch` nodes are controllers — empty `tasks`, their configuration fields set,
  one synthesized `do`-sequencer child (`<task>.do` for `for`, `<task>.try` for `try-catch`) holding
  the task list that used to live on the node itself. Fork's children point directly at each branch
  root task's own compiled node — no wrapper.
- Reason: same ADR 0004 decision, at the point that actually produces the tree.
- Impact: non-breaking to `V1OrchestratorCompiler` and its callers — v2-only, and v2 is still not
  selected by default (`CompilerProducer` still resolves to v1 unless configured otherwise).

**`docs/roadmaps/workflow-runtime-architecture.md`**
- From: classification table and acceptance criteria describe `try`/`catch`/`forkBranch` as scopes
  and don't distinguish sequencer from controller.
- To: table and acceptance criteria updated to the two-shape model; the `forkBranch` row is dropped.
- Reason: keep the roadmap doc, which the golden fixtures are checked against, consistent with the
  schema and compiler it describes.
- Impact: docs-only.

**Golden fixtures (`dws-controller/src/test/resources/v2/*`, all 6 sets)**
- From: encode the current (defective) shape.
- To: regenerated against the new classifier output. Node counts move in both directions per ADR
  0004: `nested-try-for-catch` (`order-fulfillment`) 7 → 9 nodes; `parallel-fork` (`notify-order`)
  8 → 7 nodes; `raise-and-recovery` (`guarded-payment`) 5 → 6 nodes.
- Reason: these are the pinned contract the "worked examples compile to fixed graphs" requirement
  asserts byte-for-byte; they currently encode the defect this change fixes.
- Impact: test-only.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `workflow-structural-classification`: classification produces the sequencer/controller shape
  instead of `for`/`try`/`catch` sequencers and a `forkBranch` wrapper; derived identifiers gain
  `<for-task>.do`/`<try-task>.try` and retire `<fork-task>.branch.<branch-root-task>`; each node's
  rendered `specText` carries its own scope's configuration.

## Impact

- **Code**: `dws-controller` only — `compile/v2/NodeClassifier.java`, `compile/v2/NodeNaming.java`,
  `model/FlowScope.java`, `model/SingleNodeDefinition.java`.
- **Schema**: `openspec/schemas/single-node-definition.schema.json` (breaking to future consumers of
  the current enum; none exist yet).
- **Docs**: `docs/roadmaps/workflow-runtime-architecture.md`.
- **Tests**: all 6 golden fixture sets under `dws-controller/src/test/resources/v2/`.
- **Downstream**: unblocks Phase 2 (`dws-step`) and Phase 3 (`dws-flow`), which read this shape.
- **No API/deployment impact**: v2 is not selected by default; `V1OrchestratorCompiler` output is
  unchanged.
