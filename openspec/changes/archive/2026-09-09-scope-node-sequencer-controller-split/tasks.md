## 1. Contract updates (schema + architecture doc)

- [x] 1.1 Rewrite `scope` enum in `openspec/schemas/single-node-definition.schema.json` to
  `main`, `do`, `for`, `try-catch`, `fork` (drop `try`, `catch`, `forkBranch`)
- [x] 1.2 Add `each`/`in`/`at`/`while` to the flow branch's schema, valid only when `scope` is `for`
  (forbidden otherwise, via `if`/`then`/`else`, matching the existing `forkMode` pattern)
- [x] 1.3 Add `errors`/`retry` to the flow branch's schema, valid only when `scope` is `try-catch`
- [x] 1.4 Constrain `tasks` to `maxItems: 0` whenever `scope` is `for`, `try-catch`, or `fork`
  (controller shapes)
- [x] 1.5 Rewrite the classification table in `docs/roadmaps/workflow-runtime-architecture.md` to the
  sequencer/controller split; drop the `forkBranch` row and its "not a separate node" caveat
- [x] 1.6 Update the doc's acceptance criteria that reference `try`/`catch`/`forkBranch` scopes or
  fork-branch derived identifiers
- [x] 1.7 Redraw the nested try/for/catch and parallel-fork worked-example mermaid diagrams to match
  the new node shapes (see ADR 0004 §Worked examples for the target diagrams)
- [x] 1.8 Validation: `openspec validate scope-node-sequencer-controller-split --json` reports
  `"valid": true`, and the schema file parses as valid draft-07. (`--all` reports 50 passed / 3
  failed; the 3 — `helm-admin-gateway`, `ows-phase3-errors-timeouts`, `workflow-error-format` — are
  pre-existing failures unrelated to and untouched by this change.)

## 2. dws-controller — model changes

- [x] 2.1 Add `each`, `in`, `at`, `while` `Optional<String>` fields to `FlowScope`, with a
  `withForConfig(...)`-style wither, following the existing `withCatch`/`withForkMode` pattern
- [x] 2.2 Add `errors`, `retry` `Optional<JsonNode>` (or equivalent) fields to `FlowScope` with a
  matching wither
- [x] 2.3 Update `SingleNodeDefinition.flow` to render the new `FlowScope` fields when present
- [x] 2.4 Validation: `./mvnw test -Dtest=SingleNodeDefinitionTest` (or equivalent existing test) —
  add cases for a `for` and a `try-catch` rendering with their new fields

## 3. dws-controller — NodeNaming

- [x] 3.1 Add `NodeNaming.forBodyNodeId(String forTaskName)` deriving `<task>.do`
- [x] 3.2 Add `NodeNaming.tryBodyNodeId(String tryTaskName)` deriving `<task>.try`
- [x] 3.3 Delete `NodeNaming.branchNodeId` and its `<fork>.branch.<name>` derivation
- [x] 3.4 Validation: `./mvnw test -Dtest=NodeNamingTest` (or equivalent)

## 4. dws-controller — NodeClassifier rewrite

- [x] 4.1 Rewrite `forFlow`: build a controller `FlowNode` (`scope: for`, empty `tasks`, `each`/`in`/
  `at`/`while` from `ForTask`) with one child — a `do`-sequencer over `forTask.getDo()` at
  `NodeNaming.forBodyNodeId(nodeId)`
- [x] 4.2 Rewrite `tryFlow`: build a controller `FlowNode` (`scope: try-catch`, empty `tasks`,
  `errors`/`retry` from `TryTask`/`TryTaskCatch`) with a `try` child — a `do`-sequencer over
  `tryTask.getTry()` at `NodeNaming.tryBodyNodeId(nodeId)` — and, when `catch.do` is non-empty, the
  existing `catch` child (now rendering `scope: do` instead of `scope: catch`)
- [x] 4.3 Delete `NodeClassifier.branchFlow`; have `forkFlow` classify each branch via
  `classifyTask(branch, rawBranches.get(i), context)` directly and use the result as the child
- [x] 4.4 Update `catchFlow` to render `scope: do` instead of `scope: catch` (no other change — it
  already wraps `catch.do` in a sequencer)
- [x] 4.5 Remove the now-unused `SCOPE_TRY`/`SCOPE_CATCH`/`SCOPE_FORK_BRANCH` constants (or repoint
  `SCOPE_CATCH`'s single remaining use, if any, to `SCOPE_DO`); add `SCOPE_TRY_CATCH`
- [x] 4.6 Validation: `cd dws-controller && ./mvnw verify` — expect fixture-diff test failures at this
  point until fixtures are regenerated in group 6; unit tests exercising `NodeClassifier` in
  isolation (not against checked-in fixtures) SHALL pass

## 5. dws-controller — new unit test coverage

- [x] 5.1 Add/extend `NodeClassifierTest` scenarios: a `for` node renders `each`/`in` and delegates to
  a `do` child; a `try-catch` node renders `errors`/`retry` and has `try`/`catch` children; a fork's
  children are keyed directly by branch task name (no `forkBranch` node in the tree)
- [x] 5.2 Add a schema-conformance test asserting `try`, `catch`, `forkBranch` are rejected as `scope`
  values, and that a controller's `tasks` array must be empty
- [x] 5.3 Validation: `./mvnw test` — new tests pass

## 6. dws-controller — golden fixture regeneration

- [x] 6.1 Regenerate `nested-try-for-catch` fixtures (`expected-graph.json` + `nodes/*.json`) by
  running the updated compiler against its `definition.yaml` and capturing output; verify against ADR
  0004 worked example 1 (9 nodes)
- [x] 6.2 Regenerate `parallel-fork` fixtures; verify against ADR 0004 worked example 2 (7 nodes)
- [x] 6.3 Regenerate `raise-and-recovery` fixtures; verify against ADR 0004 worked example 3 (6 nodes,
  `errors.with.status: 402` present on `process-payment`)
- [x] 6.4 Regenerate the remaining 3 fixture sets (`timing-and-event`, `external-call-and-run`,
  `state-and-decision`) — expected unchanged in shape (no `for`/`try`/`fork` scopes), regenerate
  anyway to catch any incidental schema/rendering drift
- [x] 6.5 Validation: `cd dws-controller && ./mvnw verify` — full green, including fixture-diff tests

## 7. Spec sync check

- [x] 7.1 Confirm `openspec/changes/scope-node-sequencer-controller-split/specs/
  workflow-structural-classification/spec.md`'s MODIFIED requirement headers match
  `openspec/specs/workflow-structural-classification/spec.md`'s existing headers verbatim (trim,
  case-sensitive) — required for archive-time delta apply to find them
- [x] 7.2 Validation: `openspec validate scope-node-sequencer-controller-split --json` reports
  `"valid": true` with no delta-apply warnings for this change's own deltas
  (`workflow-structural-classification`, `single-node-definition-contract`). A repo-wide
  `openspec validate --all --json` still reports the same 3 pre-existing failures named in 1.8,
  which this change neither introduces nor fixes.
