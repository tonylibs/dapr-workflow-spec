## Why

Phase 1's first half (`phase-1-compiler-strategy-split`, archived 2026-09-06) built the seam:
a `WorkflowCompiler` interface, `V1OrchestratorCompiler` renamed verbatim from the old class, the
sealed `CompiledNode`/`FlowNode`/`StepNode` model, and a `V2StructuralCompiler` stub selected by a
Dapr Configuration flag that defaults to v1. The stub compiles nothing — it returns a
`DeploymentPlan` whose every field, `flowStepGraph` included, is empty.

This change fills that stub in: the recursive classification pass that turns a parsed DSL 1.0
definition into the Flow/Step graph, with derived identifiers, DNS-1123 app IDs, the `-fn`
naming-collision rule, duplicate-identifier rejection, and a valid per-node `specText`. It is the
first real content `flowStepGraph` ever carries, and everything after it in the roadmap
(`dws-step`/`dws-flow` execution, `StackSynthesizer` synthesis) reads that graph — so it has to be
right before Phase 2 can start against it.

Two blockers get cleared on the way. ADR 0003 recorded its schema impact as "documented, not yet
implemented", so a fork node's `specText` cannot validate against
`openspec/schemas/single-node-definition.schema.json` at all today. And
`docs/roadmaps/workflow-runtime-architecture.md` — the source for the golden fixtures — still says
fork is an inline region with no standalone node, directly contradicting ADR 0003.

## What Changes

**`V2StructuralCompiler` compile pass**
- From: returns a `DeploymentPlan` of empty strings and empty lists.
- To: parses the definition, computes workflow identity, and builds a `CompiledNode` tree rooted at
  the `main` `FlowNode`, with each node carrying its `nodeId`, sanitized `appId`,
  `definitionResource`, and rendered `specText`.
- Reason: the classification pass is Phase 1's actual deliverable; the stub was only the seam.
- Impact: non-breaking. v2 is still not selected by default — `CompilerProducer` still resolves to
  v1 unless the `compiler.version` flag explicitly says `v2`.

**Classification rules**
- To: `main`, each `for`, `try`, `catch`, `fork`, and fork branch become `FlowNode`s; every other
  task kind (`set`, `switch`, `wait`, `listen`, `emit`, `raise`, `call`, `run`) becomes a
  `StepNode`, with no exceptions. Fork follows ADR 0003 — its own `FlowNode`, `forkMode: all|any`
  derived from `compete`, an empty `tasks` list, children = the branch nodes.
- Reason: ADR 0001 Decision 1 as amended by ADR 0003.

**Derived identifiers**
- To: `<workflow>.main`, `<try-task>.catch`, `<fork-task>.branch.<branch-root-task>` for scopes the
  DSL does not name; a named scope keeps its own task name. A structural task at a fork-branch root
  appends its kind (`.for`, `.try`) because the branch id already consumed its name. Every `nodeId`
  is then sanitized to a DNS-1123 label as its `appId` — dots to dashes, camelCase to kebab
  (`fulfillOrder.catch` → `fulfill-order-catch`).
- Reason: acceptance criterion #2 plus ADR 0001's sanitization corollary.

**Naming-collision rule (`call`/`run` steps only)**
- From: a `call`/`run` task's kebab-case name addresses its Knative Service directly.
- To: the `dws-step` node keeps the plain task-derived app ID; the underlying function's Knative
  Service app ID takes an explicit `-fn` suffix, recorded as the `StepNode`'s `functionAppId`
  (`reserve-item` for the step, `reserve-item-fn` for the function).
- Reason: ADR 0001 Decision 1's naming-collision corollary. `dws-step` becomes the only direct
  caller of the function.
- Impact: no deployed resource is renamed by this change — `StackSynthesizer` still renders v1's
  names, and consuming `functionAppId` is Phase 4's work.

**Duplicate-identifier rejection**
- To: two nodes resolving to the same `appId` fail compilation with a `CompilationException` naming
  both source `nodeId`s and the app ID they share.
- Reason: acceptance criterion #9.

**Per-node `specText`**
- To: each node renders a single-node definition satisfying
  `openspec/schemas/single-node-definition.schema.json` — the shared envelope plus, for a flow, its
  `scope`, source-ordered `tasks`, a `children` map, and `catch`/`forkMode` where they apply; for a
  step, its `task` and, on `call`/`run`, its `functionAppId`. The `children` map is projected at
  render time from `CompiledNode.key()`, never from a stored map.
- Reason: item 5 of the requirement; ADR 0002 fixed `key()` as the mechanism.

**`DeploymentPlan` identity fields under v2**
- From: empty strings.
- To: `workflow`, `versionId`, `version`, `definitionResource`, and `specText` computed exactly as
  v1 computes them.
- Reason: every node's envelope needs `workflow` and `version`; identity is shared between the
  strategies rather than v1 legacy. The compatibility contract names only `steps` and `orchestrator`
  as the fields v2 leaves empty, and both stay empty.

**Single-node definition schema**
- From: flow `scope` enum is `main|for|try|catch|forkBranch`; no `forkMode`.
- To: `fork` added to the enum, `forkMode: all|any` present exactly when `scope` is `fork`.
- Reason: applies the delta ADR 0003 documented but deferred; without it no fork node validates.

**Architecture doc**
- From: the classification table calls `fork` an inline parallel region and says not to render a
  separate fork node; acceptance criteria #6 and #7 say no standalone fork invocation is created;
  the fork example's mermaid draws branches hanging off the parent.
- To: all three rewritten to match ADR 0003.
- Reason: the doc is the fixture source, and it currently states the opposite of what is built.

## Capabilities

### New Capabilities
- `workflow-structural-classification`: the v2 compiler's recursive classification pass — which DSL
  construct becomes a Flow versus a Step, how derived identifiers are formed and sanitized into
  Dapr app IDs, how the `call`/`run` naming collision resolves, when compilation is rejected for
  ambiguous identifiers, and what each node's single-node `specText` contains.

### Modified Capabilities
- `workflow-compiler-strategy`: `V2StructuralCompiler` now populates `flowStepGraph` and the plan's
  identity fields rather than returning an empty plan, and `CompiledNode` gains a `flatten()`
  pre-order walk. The strategy-selection default and the v1 side are unchanged.
- `single-node-definition-contract`: additive requirements for the `fork` scope and `forkMode`.
  Note this capability's base requirements are still owned by the unarchived
  `workflow-runtime-v2-phase0-scaffolding` change; this change adds to it rather than editing that
  change's artifacts.

## Impact

- Code: `dws-controller` `compile` package — `V2StructuralCompiler` plus new `SpecParser`,
  `NodeClassifier`, `NodeNaming`, `SingleNodeDefinition`; `Names` gains
  `nodeDefinitionResource`; `V1OrchestratorCompiler` delegates its two parse helpers to
  `SpecParser` (behavior-preserving). `model` package — `CompiledNode` gains `flatten()`.
- Tests: six golden fixtures under `dws-controller/src/test/resources`, one per worked example, plus
  unit tests for naming, sanitization, `-fn`, and collision rejection. Fixtures are validated against the real
  schema using `com.networknt:json-schema-validator`, already on the controller's classpath — no
  new dependency.
- Docs/schema: `openspec/schemas/single-node-definition.schema.json`,
  `docs/roadmaps/workflow-runtime-architecture.md`.
- Unchanged: `V1OrchestratorCompiler`'s behavior and tests, `CompilerProducer`'s v1 default,
  `WorkflowResource`, `StackSynthesizer`, `StackApplier`, every chart template, and every other
  package in the monorepo.
- Out of scope: `dws-step`/`dws-flow` runtime execution (Phases 2 and 3), `StackSynthesizer` deploy
  synthesis (Phase 4), fork's `allOf`/`anyOf` execution semantics and `anyOf` losing-branch
  cancellation (Phase 3), and flipping the compiler default to v2 (Phase 5).
