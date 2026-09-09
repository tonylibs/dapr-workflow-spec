<!--
Raw capture of superpowers:brainstorming output.
-->

## Classification

**Architectural** — this change reshapes `CompiledNode`/single-node-definition schema, an interface
Phase 2 (`dws-step`) and Phase 3 (`dws-flow`) already depend on per the roadmap. However, the
architecture itself is not being decided here: ADR 0004
(`docs/adr/0004-scope-nodes-carry-their-own-configuration.md`) is already Accepted, with full
rationale, three worked examples, and a consequences list. This session's brainstorming scopes the
OpenSpec change and its task breakdown against that decided architecture — it does not re-litigate
the design.

## Context gathered

Read before the design pass:

- `docs/adr/0004-scope-nodes-carry-their-own-configuration.md` — the decision. Every compiled Flow
  node is one of two shapes: **sequencer** (`main`, `do` — owns a task list, no configuration) or
  **controller** (`for`, `try-catch`, `fork` — owns that scope's configuration, empty `tasks`,
  delegates to `do`-shaped children keyed by role). `forkBranch` is retired; a fork's children point
  straight at each branch root task's own node.
- `dws-controller/src/main/java/io/dws/controller/compile/v2/NodeClassifier.java` — the current
  recursive descent. Its own Javadoc on `tryFlow` already names the gap: "Whether that is correct is
  the unresolved scope-configuration question in the change's design (Open Questions), which blocks
  Phase 2." Confirms this is exactly the defect ADR 0004 fixes, not a new finding.
- `NodeNaming.java` — existing derived-id helpers (`catchNodeId`, `branchNodeId`) show the pattern
  new ones (`<for-task>.do`, `<try-task>.try`) follow.
- `FlowScope.java` / `FlowNode.java` / `SingleNodeDefinition.java` — the record carrying a scope's
  optional fields (`catchAppId`, `forkMode`) needs new optional fields for `for` (`each`/`in`/`at`/
  `while`) and `try-catch` (`errors`/`retry`).
- `openspec/schemas/single-node-definition.schema.json` — current `scope` enum still has
  `try`/`catch`/`forkBranch`; `tasks` isn't yet constrained empty for `for`/`try-catch`.
- `openspec/specs/workflow-structural-classification/spec.md` — the capability this change modifies.
  Archived from `phase-1-structural-classification` (2026-09-06). Requirements affected: "Recursive
  classification into Flow and Step nodes", "Fork compiles to its own Flow node", "Derived
  identifiers and DNS-1123 app IDs", "Each node renders a valid single-node definition".
- `dws-controller/src/test/resources/v2/*` — 6 golden fixture sets. `nested-try-for-catch`,
  `parallel-fork`, `raise-and-recovery` are the same definitions as ADR 0004's three worked examples
  (`order-fulfillment`, `notify-order`, `guarded-payment`), confirmed by diffing fixture node lists
  against the ADR's tables.

## Decision log

**Q1 — one change or split fixture regen into a follow-up?**
Options: (a) one change, full ADR 0004 scope (schema + compiler + docs + fixtures) — matches how
`phase-1-structural-classification` shipped its own schema change in the same change as the compiler
pass it enabled; (b) split fixtures into a trailing change.
**Decision: (a) — one change, full scope.** Fixtures are the pinned contract the requirement "The
worked examples compile to fixed graphs" already asserts byte-for-byte; landing the compiler change
without them would leave that requirement failing on `main`.

**Q2 — change directory name?**
Options: `phase-2-scope-node-configuration` (names the ADR's fix) vs
`scope-node-sequencer-controller-split` (names the mechanism — the two-shape model).
**Decision: `scope-node-sequencer-controller-split`.**

## Design presented (chat) and approved

**Components touched:**
- `openspec/schemas/single-node-definition.schema.json` — `scope` enum → `main|do|for|try-catch|
  fork` (drop `try`, `catch`, `forkBranch`). `each`/`in`/`at`/`while` valid only when `scope: for`.
  `errors`/`retry` valid only when `scope: try-catch`. `tasks` forced `maxItems: 0` when scope is a
  controller (`for`/`try-catch`/`fork`). `forkMode` rule unchanged.
- `NodeClassifier.tryFlow` → rewritten: the try node becomes a controller (empty `tasks`, carries
  `errors`/`retry` read off `TryTask`, scope `try-catch`). The guarded list moves off the node
  itself into a NEW synthesized `try` child (`<task>.try`, `do` sequencer) — today the guarded list
  sits directly on the try node's own `tasks`. The catch child is already shaped like a sequencer;
  only its wire `scope` changes from `catch` to `do`.
- `NodeClassifier.forFlow` → rewritten: the for node becomes a controller (empty `tasks`, carries
  `each`/`in`/`at`/`while`). The loop body moves into a NEW synthesized `do` child (`<task>.do`).
- `NodeClassifier.forkFlow`/`branchFlow` → `branchFlow` wrapper deleted. Fork's children point
  straight at `classifyTask(branch, ...)` — no intermediate `forkBranch` node. `NodeNaming
  .branchNodeId` and the `<fork>.branch.<name>` derived id retired.
- `FlowScope` — new fields for the two controller kinds (`each`/`in`/`at`/`while` for `for`;
  `errors`/`retry` for `try-catch`), alongside the existing `catchAppId`/`forkMode`.
  `SingleNodeDefinition.flow` renders them.
- `NodeNaming` — new derived-id helpers for `<for-task>.do` and `<try-task>.try`; `branchNodeId`
  deleted.
- `docs/roadmaps/workflow-runtime-architecture.md` — classification table + acceptance criteria
  updated to the sequencer/controller split; drop the `forkBranch` row.
- All 6 `dws-controller/src/test/resources/v2/*` fixture sets regenerated.

**Data flow / error handling:** no new error paths — `DuplicateChildKeyException`/
`CompilationException` machinery unchanged, just fed a differently-shaped tree. `errors.with.status`
now travels on the node that decides recovery instead of vanishing (the defect ADR 0004 fixes).

**Testing:** fixture-diff tests re-run against regenerated goldens; new assertions for `try-catch`
node carrying `errors`, `for` node carrying `each`/`in`, fork's children keyed by branch task name
directly (no wrapper). Schema-level test: `try`/`catch`/`forkBranch` now rejected as `scope` values.

User approved this design verbatim ("approve").
