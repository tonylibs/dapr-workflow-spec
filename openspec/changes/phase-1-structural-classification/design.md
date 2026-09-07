## Context

`dws-controller` compiles a DSL 1.0 definition into a `DeploymentPlan`. Since
`phase-1-compiler-strategy-split` that happens behind a `WorkflowCompiler` interface with two
implementations: `V1OrchestratorCompiler`, which produces the legacy flat step list plus one
orchestrator Deployment, and `V2StructuralCompiler`, which is a stub returning an empty plan.
`CompilerProducer` picks between them from a `compiler.version` key on the `dws-controller-config`
Dapr Configuration store, defaulting to v1.

The v2 target state (ADR 0001, amended by ADR 0003) makes every task in the classification table
its own deployed Dapr app: Flow scopes run on `dws-flow`, Step tasks on `dws-step`, and a
`call`/`run` step's existing Knative function sits one hop further out behind its `dws-step`. Each
node loads a pinned single-node definition whose shape is fixed by
`openspec/schemas/single-node-definition.schema.json`.

Constraints this change inherits rather than decides:

- Every consumer keeps a `WorkflowCompiler`-typed field; the seam is already built.
- v1 output must stay byte-for-byte identical, and v1 must stay the default.
- `CompiledNode.children()` is a plain `List`, never a keyed map; a child's key comes from
  `key()` on the child itself (ADR 0002).
- `docs/roadmaps/workflow-runtime-architecture.md`'s six worked examples are the fixtures, and its
  acceptance criteria #2 and #9 pin the derived-identifier formats and the rejection behavior.

Stakeholders: Phase 2 (`dws-step`) and Phase 3 (`dws-flow`) consume the `specText` this pass
renders; Phase 4 (`StackSynthesizer`) consumes the tree and `functionAppId`.

## Goals / Non-Goals

**Goals**

- A recursive classification pass producing a `CompiledNode` tree for any definition v1 accepts.
- Derived identifiers matching acceptance criterion #2, sanitized to DNS-1123 Dapr app IDs.
- The ADR 0001 `-fn` naming-collision rule recorded on each `call`/`run` `StepNode`.
- Compilation rejected, with a clear message, when two nodes resolve to one app ID.
- A per-node `specText` that validates against the single-node definition schema.
- Golden tests over all six worked examples asserting tree, app IDs, and `specText` exactly.
- `V1OrchestratorCompiler` behavior and tests completely unaffected; v1 stays the default.

**Non-Goals**

- Executing anything. `dws-step` and `dws-flow` runtime behavior is Phases 2 and 3.
- Rendering Kubernetes objects from the graph — Phase 4's `StackSynthesizer` split.
- Fork's `allOf`/`anyOf` execution semantics, and `anyOf`'s losing-branch cancellation. This pass
  classifies fork and records `forkMode`; it never runs one.
- Flipping the compiler default to v2 (Phase 5), or any side-by-side parity harness.
- Renaming any currently deployed Knative Service. `functionAppId` is recorded, not yet applied.
- `dws-console` changes. This is a backend runtime/deploy concern only.

## Decisions

### D1: Four collaborators rather than one recursive method

`V2StructuralCompiler` orchestrates; the work lives in four small classes in the `compile` package:

| Class | Responsibility | Depends on |
|---|---|---|
| `SpecParser` | Format detection and parse-or-throw, extracted verbatim from `V1OrchestratorCompiler` | serverlessworkflow API |
| `NodeClassifier` | Recursive descent over `TaskItem`s producing the `CompiledNode` tree | `NodeNaming`, `SingleNodeDefinition` |
| `NodeNaming` | `nodeId` derivation, DNS-1123 sanitization, `-fn` suffixing, `definitionResource`, collision detection | `Names` |
| `SingleNodeDefinition` | Renders one node's `specText` JSON per the Phase 0 schema | Jackson |

Alternative considered: one recursive method inside `V2StructuralCompiler`. Rejected — it merges
parsing, classification, naming, and serialization into a class that only end-to-end golden tests
can exercise, so a naming bug and a rendering bug fail the same assertion. A visitor with pluggable
per-kind emitters was also considered and rejected as indirection Phase 1 does not need; the
classification table is a fixed list of nine task kinds, not an extension point.

`SpecParser`'s extraction is the one place this change touches v1. It moves two private methods and
has `V1OrchestratorCompiler` delegate to them — behavior-preserving, with the existing v1 tests as
the guard. The alternative, duplicating format detection into v2, guarantees the two drift apart.

### D2: Classification table

`FlowNode` for `main`, each `for`, each `try`, each `catch`, each `fork`, and each fork branch.
`StepNode` for every other task kind — `set`, `switch`, `wait`, `listen`, `emit`, `raise`, `call`,
`run` — with no exceptions, per ADR 0001 Decision 1. `switch` is deliberately a Step: its `then`
values route to named entities but it owns no task list.

Fork follows ADR 0003 rather than the pre-amendment roadmap text: its own `FlowNode` with
`forkMode` of `all` (`compete: false`) or `any` (`compete: true`), an empty `tasks` list, and the
branch nodes as `children`. Alternative considered: keep fork inline in the parent, as the roadmap
doc still describes. Rejected — ADR 0003 supersedes that text, and the doc is corrected by this
change rather than followed.

### D3: Derived identifiers, flat for named scopes

A scope the DSL names keeps that name as its `nodeId`. Dotted derived ids appear only where the DSL
supplies none:

| Scope | `nodeId` | `appId` |
|---|---|---|
| top-level `do` | `<workflow>.main` | `order-fulfillment-main` |
| named `for`/`try` | the task's own name | `reserve-items` |
| `catch` | `<try-task>.catch` | `fulfill-order-catch` |
| fork branch | `<fork-task>.branch.<branch-root-task>` | `notify-channels-branch-notify-recipients` |
| structural task at a branch root | the task's own name | `notify-recipients` |

Alternatives considered. **Parent-qualified paths** (`order-fulfillment.main.fulfillOrder.reserveItems`)
are unique by construction but breach DNS-1123's 63-character cap at realistic nesting depth and
contradict acceptance criterion #2's own examples. **Flat with qualification only on collision**
makes a node's app ID depend on unrelated parts of the document — renaming a task elsewhere
silently re-addresses this node — and removes the rejection behavior criterion #9 requires.

The last row is not a special case: a structural task at a branch root is a named scope like any
other, so the flat rule applies unchanged. An earlier draft appended the scope kind
(`<branch-nodeId>.for`) to avoid repeating the name the branch id already consumed. That broke the
wire contract — the phase-0 `single-node-definition-contract` requires `children` to map *task name*
to app ID, and `key()` returns a nodeId's last dotted segment, so the appended form keyed the branch's
only child `for` while its `tasks` entry was keyed `notifyRecipients`. Phase 3 dispatch resolves
children through that map, so the two must agree. Dropping the suffix removes the special case and
restores the match.

Sanitization is `Names.kebab` applied to the whole dotted id: dots and other non-alphanumerics
collapse to single dashes, camelCase boundaries split. `fulfillOrder.catch` → `fulfill-order-catch`.

### D4: `-fn` only on `call`/`run`, recorded not applied

A `call`/`run` `StepNode` gets `functionAppId = appId + "-fn"`; every other `StepNode` leaves it
`Optional.empty()`. The `dws-step` node keeps the plain task-derived name because it is the node the
rest of the graph addresses; the pre-existing Knative Service takes the suffix. Nothing is renamed
by this change — `StackSynthesizer` still emits v1 names, and consuming `functionAppId` is Phase 4.

Alternative considered: suffix the `dws-step` node instead and leave the function's name alone.
Rejected by ADR 0001's corollary — the graph would then address every I/O step by a name that
differs in shape from every other node's.

### D5: `specText` rendered from `key()`, `children` never stored

Flow node:

```json
{ "workflow": "order-fulfillment", "version": "order-fulfillment@v1a2b3c4d",
  "nodeId": "fulfill-order", "kind": "flow", "scope": "try",
  "tasks": [ { "reserveItems": { "for": {}, "do": [] } } ],
  "children": { "reserveItems": "reserve-items", "catch": "fulfill-order-catch" },
  "catch": "fulfill-order-catch" }
```

Step node:

```json
{ "workflow": "order-fulfillment", "version": "order-fulfillment@v1a2b3c4d",
  "nodeId": "reserve-item", "kind": "step",
  "task": { "reserveItem": { "call": "http", "with": {} } },
  "functionAppId": "reserve-item-fn" }
```

The envelope's `nodeId` carries the sanitized app ID, because the schema defines that field as "a
DNS-1123 label and the Dapr app ID for this node"; the pre-sanitized dotted form stays on the Java
`CompiledNode.nodeId()` and never reaches the wire. `tasks` holds the scope's own DSL task objects
unmodified and in source order — name-keyed, so a runtime can match a task to its `children` entry.
A step's `task` uses the same name-keyed shape for consistency. `children` is built as
`children.stream().collect(toMap(CompiledNode::key, CompiledNode::appId))` per ADR 0002 — a
projection at render time, never a stored map. A catch child appears in both `children` and the
dedicated `catch` field: the map is what the runtime dispatches through, `catch` is what tells it
which entry is the recovery scope. A fork node renders `forkMode` and an empty `tasks`.

### D6: Collision detection over a pre-order walk

`CompiledNode` gains a `flatten()` default method — the pre-order walk ADR 0002 already earmarked
for `StackSynthesizer`. The compiler walks the finished tree once, accumulating `appId -> nodeId`;
the second binding for an app ID throws `CompilationException` naming both source `nodeId`s and the
shared app ID. Doing it as a post-pass over the tree rather than inside the recursion keeps
`NodeClassifier` free of accumulator state and gives the error message both colliding ids.

### D7: Fixtures validated against the real schema

Each of the six worked examples gets `definition.yaml`, an `expected-graph.json` (tree shape,
`nodeId`, `appId`, `definitionResource`, `functionAppId`), and one `nodes/<appId>.json` per node.
Tests assert byte-exact equality *and* validate every rendered `specText` against
`single-node-definition.schema.json` using `com.networknt:json-schema-validator`, which
`dws-controller` already resolves on its compile classpath — no new dependency.

Alternative considered: fixture equality alone. Rejected — a fixture is hand-written, so equality
alone happily passes against a fixture the schema would reject, and the schema is the contract
Phases 2 and 3 implement against.

## Risks / Trade-offs

- **`SpecParser` extraction touches v1** → the move is mechanical and
  `WorkflowCompilerTest` plus `CompilerStrategyTest` run unchanged as the guard; any behavior delta fails them.
- **Pod-count growth from D2's fork decision and the two-node branch rule** → bounded and already
  flagged for Phase 4's capacity check by ADR 0001 and ADR 0003; not re-litigated here.
- **The 63-character DNS-1123 cap** → D3 keeps ids short, but a deep fork-branch chain can still
  approach it. Mitigation: `NodeNaming` rejects an over-length app ID with the same clear
  `CompilationException` shape as a collision, rather than silently truncating into a collision.
- **The roadmap doc rewrite could collide with Phase 3 visualizer work** → the rewrite is confined
  to the fork rows, criteria #6/#7, and one mermaid block, all of which already contradict an
  accepted ADR; leaving them is the larger risk.
- **`single-node-definition-contract`'s base requirements are in an unarchived change** → this
  change adds to that capability rather than editing phase-0's artifacts, so the two archive in
  either order without conflict.
- **The schema validator arrives transitively** → `com.networknt:json-schema-validator:2.0.0` is
  already resolved on `dws-controller`'s compile classpath rather than declared in its `pom.xml`,
  so a future dependency change could remove it silently. Mitigation: declare it explicitly in
  `pom.xml` if `./mvnw verify` ever fails to resolve it; no new external dependency either way.

## Migration Plan

No migration. v2 is unreachable in any deployed environment unless `compiler.version` is explicitly
set to `v2` on the `dws-controller-config` store, and this change does not alter that default. v1's
compiled output, deployed resources, and tests are untouched, so rollback is `git revert` with no
data or resource implications. The `-fn` suffix is recorded on `StepNode` only; no Knative Service
is renamed until Phase 4 consumes it, so no running workload is affected.

## Open Questions

- Whether `NodeNaming` should reject or hash-truncate an app ID that exceeds 63 characters. This
  change rejects, on the grounds that a silent truncation can itself create a collision; revisit if
  a legitimate definition ever hits the cap.
- Whether `flatten()` belongs on `CompiledNode` as a default method or on a separate walker.
  Placed on `CompiledNode` per ADR 0002's own sketch; Phase 4 may move it if `StackSynthesizer`
  wants a different traversal order.
