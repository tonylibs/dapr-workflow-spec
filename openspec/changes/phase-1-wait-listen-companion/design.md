## Context

Workflow Runtime v2 compiles an Open Workflow DSL 1.0 document into a tree of `FlowNode` (hosted by `.NET` `dws-flow`) and `StepNode` (hosted by Java `dws-step`) definitions pinned to the single-node definition JSON contract (`dws-controller/src/test/resources/single-node-definition.schema.json`).

### In-force ADR baseline

Auditing `docs/adr/` (0001–0014):
- **In force and constraining this change**:
  - **ADR 0001** (per-node Dapr app IDs; uniform Step layer), amended by **ADR 0003** (`fork` as a Flow node; uniform parent-dispatch rule) and **ADR 0004** (two Flow node shapes: Sequencers `main`/`do` vs Controllers `for`/`try-catch`/`fork` carrying their own configuration and empty `tasks`; `forkBranch` retired).
  - **ADR 0002** (`WorkflowCompiler` two-strategy split; `CompiledNode` sealed hierarchy `FlowNode`/`StepNode`; `DeploymentPlan.flowStepGraph`; `v1` untouched).
  - **ADR 0006** (Accepted): `wait` and `listen` compile to **leaf Flow controllers** (`tasks: []`, `children: {}`) rather than Steps, carrying their own duration / `to`+`timeout` verbatim; amends `docs/roadmaps/workflow-runtime-architecture.md`.
  - **ADR 0012** (Accepted 2026-11-02): **Decision 3** specifies that whenever a structural task (`for`, `try-catch`, `fork`, `wait`, `listen`, nested `do`) declares `input`, `output`, or `export`, the compiler emits one companion Step node `<task>.dataflow` (sanitized to a DNS-1123 app ID), and no companion when no data-flow section is declared.
  - **ADR 0014** (Accepted 2026-11-24): leaves ADR 0012 Decision 3 unchanged.
- **Unrelated to this change** (in force, unaffected): ADR 0005, 0007, 0009, 0010, 0011, 0013. (ADR 0008 is Proposed.)
- **No in-force ADR is superseded or revisited** — this change directly implements ADR 0006 and ADR 0012 Decision 3.

### Current state in the three affected components

1. **`dws-controller`**:
   - `NodeClassifier` currently classifies only `for`, `try`, `fork`, and `do` as `FlowNode`; `wait` and `listen` fall through to `step(...)`.
   - `FlowScope` holds `scope`, `tasks`, `catchAppId`, `forkMode`, `forConfig`, `tryCatchConfig`.
   - `FlowNode` holds `children` (with `ChildIndex.of(children)` enforcing unique child keys and rendering `"children"`).
   - `single-node-definition.schema.json` admits only `scope` in `["main", "do", "for", "try-catch", "fork"]`.
   - `timing-and-event` golden fixtures currently emit `reminder-delay` and `await-approval` as `kind: "step"`.
2. **`dws-flow`**:
   - `SingleNodeDefinitionLoader.ValidScopes` contains only `["main", "do", "for", "try-catch", "fork"]`.
   - `ScopeDispatch` already resolves anything outside `["main", "do"]` to `ScopeKind.NotImplemented` (`"config failure: scope '<scope>' is not implemented yet"`).
   - `ChildClassifier` already classifies `wait` and `listen` as `ChildKind.Flow`.
3. **`dws-step`**:
   - `TaskKind` already excludes `wait` and `listen`; `SingleNodeDefinition.FLOW_ONLY_TASK_KINDS` already rejects them at startup naming ADR 0006.

## Goals / Non-Goals

**Goals:**
- Compile `wait` and `listen` tasks into leaf controller `FlowNode`s carrying their configuration verbatim (`wait`; `to`, optional `timeout`, optional `read`), with empty `tasks` and empty `children`.
- Emit one companion `StepNode` (`<task>.dataflow`) for every structural task (`for`, `try-catch`, `fork`, `wait`, `listen`, nested `do`) that declares any of `input`, `output`, or `export`, carrying only those sections verbatim, and link it from the structural `FlowNode` via top-level `"dataflow"`.
- Update `single-node-definition.schema.json` so new scopes and fields validate strictly where allowed and existing scopes remain unchanged.
- Update `dws-flow` to load `wait`, `listen`, and optional `dataflow`, failing `wait`/`listen` at execution time with 3a's `"config failure: scope '<scope>' is not implemented yet"`.
- Keep `dws-step` rejecting `wait`/`listen` at startup with zero regressions.
- Guarantee byte-identical compilation across repeated compiles and add golden tests covering every structural kind with and without data flow.
- Amend `docs/roadmaps/workflow-runtime-architecture.md` per ADR 0006.

**Non-Goals:**
- Implementing runtime timer/event logic for `wait` (3e) or `listen` (3f/3g).
- Implementing `dws-flow` companion calls or context threading (3k), or `dws-step` data-flow handler / companion execution (2h).
- Adding `timeout` to `main` (3j) or document-level `input`/`output`.
- Modifying `V1OrchestratorCompiler`, `dws-orchestrator`, `StackSynthesizer`, or `dws-step` behavior.
- Preserving content-addressed versioning is mandatory: `SpecDigest` and `Names.definitionResource` are untouched; only the v2 compiled graph and per-node definitions change.

## Decisions

### D1: Wire-format field names for `wait` and `listen` leaf controllers

**Decision:** Mirror the OWS DSL 1.0 property names directly on the `kind: "flow"` object, matching ADR 0004's convention (`each`/`in`/`at`/`while` for `for`, `errors`/`retry`/`as`/`when`/`exceptWhen` for `try-catch`):

- **`scope: "wait"`**:
  ```json
  {
    "workflow": "approval-reminder",
    "version": "approval-reminder@v1a2b3c4d",
    "nodeId": "reminder-delay",
    "kind": "flow",
    "scope": "wait",
    "tasks": [],
    "children": {},
    "wait": "PT5M"
  }
  ```
  - `wait`: required, `type: ["string", "object"]` (ISO-8601 duration string such as `"PT5M"` or inline duration object such as `{"seconds": 10}`, deep-copied verbatim from `rawBody.get("wait")`).
  - `tasks`: `maxItems: 0`; `children`: `maxProperties: 0`.
  - Forbidden on all scopes other than `wait`.

- **`scope: "listen"`**:
  ```json
  {
    "workflow": "approval-reminder",
    "version": "approval-reminder@v1a2b3c4d",
    "nodeId": "await-approval",
    "kind": "flow",
    "scope": "listen",
    "tasks": [],
    "children": {},
    "to": { "one": { "with": { "type": "com.example.request.approved" } } },
    "timeout": "PT1H",
    "read": "data"
  }
  ```
  - `to`: required, `type: "object"`, deep-copied verbatim from `rawBody.path("listen").get("to")`.
  - `timeout`: optional, `type: ["string", "object"]`, deep-copied verbatim from `rawBody.get("timeout")` (task-level timeout in OWS DSL 1.0, since `listen` has no nested `listen.timeout`; covers ISO-8601 strings, `use.timeouts` names, and inline timeout objects). Omitted when absent.
  - `read`: optional, `type: "string"`, `minLength: 1`, copied verbatim from `rawBody.path("listen").get("read")` when present (needed by 3g's bridge per ADR 0006). Omitted when absent.
  - `tasks`: `maxItems: 0`; `children`: `maxProperties: 0`.
  - `to`, `timeout`, and `read` are forbidden on all scopes other than `listen` (leaving `main`, `do`, `for`, `try-catch`, `fork` unchanged; 3j can later widen `timeout` to `main`).

**Alternatives considered:**
- *Nesting under `"listen": { "to": ..., "read": ... }` or renaming `"wait"` to `"duration"`*: Rejected because `for` and `try-catch` flatten their scope config (`each`/`in`, `errors`/`retry`) onto the top-level flow object rather than nesting under a task keyword, and `"wait"`/`"to"`/`"timeout"`/`"read"` match the DSL verbatim with no synonym translation.
- *Omitting `read` until 3g*: Carrying optional `read` now (omitted when absent) costs nothing, avoids breaking `listen` fixtures later, and aligns with ADR 0004's treatment of `try-catch` (`as`/`when`/`exceptWhen` carried verbatim ahead of 3c).

### D2: Top-level `"dataflow"` reference on structural Flow nodes (not inside `"children"`)

**Decision:** A structural `FlowNode` (`scope` in `do`, `for`, `try-catch`, `fork`, `wait`, `listen`) references its companion Step node via an optional top-level string field:

```json
"dataflow": "<companion-app-id>"
```

constrained in JSON Schema to DNS-1123 labels (`^[a-z0-9]([-a-z0-9]*[a-z0-9])?$`, `maxLength: 63`), allowed on non-`main` scopes, and explicitly forbidden on `scope: "main"`.

**Rationale:** Keeping `"dataflow"` separate from `"children"` preserves every existing invariant across all scopes without special-casing:
- `wait` and `listen` remain genuine leaf controllers (`"children": {}`) even when they declare `input`/`output`/`export`.
- `fork` keeps only branch root tasks in `"children"` so 3d fans out over `children` cleanly without filtering out a synthetic key.
- `for` keeps only `"do"` in `"children"`; `try-catch` keeps only `"try"` and optional `"catch"`.
- Nested `do` keeps a 1:1 mapping between `tasks` and `children`, avoiding any collision if a nested `do` contains a task literally named `dataflow`.
- In `dws-flow` (3k), the handler chain around *every* non-`main` scope checks one uniform field (`definition.Dataflow`) to decide whether to invoke the companion Step before/after the scope body.

**Alternatives considered:**
- *Placing `"dataflow": "<app-id>"` inside `"children"`*: Rejected because it breaks leaf controllers (`children: {}`), pollutes `fork`'s branch map, collides with a task named `dataflow` inside nested `do`, and breaks `V2StructuralCompilerTest.assertChildrenKeysMatchTaskNames`.
- *Placing an object `"dataflow": { "appId": "...", "phases": [...] }`*: Rejected because the companion Step node already carries the exact declared `input`/`output`/`export` sections; duplicating phase metadata on the Flow node risks drift and adds unnecessary schema surface (3k only needs the target app ID to call `CallActivityAsync`).

### D3: Companion Step node shape and `FlowNode` graph representation

**Decision:**
- **Identifier**: `NodeNaming.dataflowNodeId(taskName)` → `<taskName>.dataflow`, sanitized by `NodeNaming.appId(...)` to DNS-1123 (e.g., `reserveItems.dataflow` → `reserve-items-dataflow`). Subject to the same 63-char DNS-1123 limit and `AppIdRegistry.requireDistinct` collision check (against all other node app IDs and `-fn` function app IDs).
- **Companion `StepNode` `specText`**:
  ```json
  {
    "workflow": "order-fulfillment",
    "version": "order-fulfillment@v1a2b3c4d",
    "nodeId": "reserve-items-dataflow",
    "kind": "step",
    "task": {
      "reserveItems": {
        "input": { "from": "${ .order }" },
        "output": { "as": "${ . }" }
      }
    }
  }
  ```
  Constructed by filtering `rawBody`'s fields in original iteration order, deep-copying only `"input"`, `"output"`, and `"export"` (when present), and wrapping them under `{ "<taskName>": { ... } }`. Preserving `{ "<taskName>": ... }` matches every other Step node emitted by `NodeClassifier.step` and supplies the owning task name needed for ADR 0012 Decision 7 fault messages. `functionAppId` is `Optional.empty()`.
- **Java model (`FlowNode` & `FlowScope`)**:
  - `FlowScope` gains `Optional<JsonNode> waitDuration` (or `WaitConfig`), `Optional<ListenConfig> listenConfig` (`JsonNode to`, `Optional<JsonNode> timeout`, `Optional<String> read`), and `Optional<String> dataflowAppId`, with copy-on-write withers `.withWait(...)`, `.withListen(...)`, `.withDataflow(...)`.
  - `FlowNode` gains `Optional<StepNode> dataflow` (with existing constructors preserved, defaulting to `Optional.empty()`, plus a `.withDataflow(StepNode)` wither or constructor overload).
  - `FlowNode` overrides `CompiledNode.flatten()` so traversal yields: `this`, then `dataflow` (if present), then each child's `flatten()` in `children` order. This keeps `children()` strictly for execution children while guaranteeing `AppIdRegistry.requireDistinct(root)`, `V2GoldenTest`, and Phase 4 deployment synthesis visit every companion node in a single deterministic order.
  - Note on `FlowNode.withDataflow(StepNode)`: because `FlowNode.specText()` renders dynamically from `scope`, attaching the companion must keep `scope.dataflowAppId()` and `FlowNode.dataflow()` in sync (e.g., `FlowNode.withDataflow(StepNode companion)` sets both `dataflow = Optional.of(companion)` and `scope = scope.withDataflow(companion.appId())`, and the canonical constructor validates they agree — following `dws-controller/CLAUDE.md`'s "Validation lives in the type" rule!).
  - `V2GoldenTest.describeNode` includes `"dataflow": describeNode(companion)` on a `FlowNode` **only when** `flow.dataflow().isPresent()`, so existing golden `expected-graph.json` files without data flow require zero churn while fixtures with data flow explicitly pin the companion's `nodeId`, `appId`, `definitionResource`, and `kind: "step"`.

**Alternatives considered:**
- *Storing the companion in `FlowNode.children()` and filtering it out in `ChildIndex`*: Rejected because every consumer of `children()` (`V2GoldenTest`, `StackSynthesizer`, graph visualizers) would have to know that some `children()` aren't execution children.
- *Unwrapped `"task": { "input": ... }` without the task-name wrapper*: Rejected because all other `V2StructuralCompiler` Step nodes wrap `task` in `{ "<taskName>": { ... } }`, and ADR 0012 Decision 7 requires data-flow faults to name the task.

### D4: Cross-component `dws-flow` and `dws-step` boundary

- **`dws-flow`**:
  - `SingleNodeDefinition` remains one `sealed record` with nullable properties for the new fields (`JsonNode? Wait`, `JsonObject? To`, `JsonNode? Timeout`, `string? Read`, `string? Dataflow`, defaulted to `null` in positional/convenience usage so existing tests stay concise or updated cleanly).
  - `SingleNodeDefinitionLoader` widens `ValidScopes` to include `"wait"` and `"listen"`, validates the contract rules for `wait`, `listen`, and `dataflow` (including rejecting scope-foreign fields and non-empty `tasks`/`children` on `wait`/`listen`, and rejecting `dataflow` on `main` or invalid DNS-1123 labels).
  - `ScopeDispatch` needs zero logic change (it already maps any non-`main`/`do` scope to `ScopeKind.NotImplemented` → `"config failure: scope '<scope>' is not implemented yet"`). Tests pin `wait` and `listen` explicitly.
  - `UseReferences` is unchanged for now: `listen` has empty `tasks`, so no `use` store lookup occurs until 3f implements `listen` and adds `listen.Timeout` to `UseReferences.Collect`.
- **`dws-step`**:
  - Already rejects `wait` and `listen` at startup naming ADR 0006 (`SingleNodeDefinition.FLOW_ONLY_TASK_KINDS`). No code change required; `cd dws-step && ./mvnw verify` confirms the invariant holds.
  - Loading/executing companion `<task>.dataflow` Step nodes (which have `input`/`output`/`export` and no `TaskKind`) belongs to roadmap row **2h** (Step data-flow handlers), not this Phase 1 follow-up.

### D5: Determinism and golden test matrix

- `V2StructuralCompiler` uses only deterministic structures (`LinkedHashMap`, `List.copyOf`, raw Jackson `ObjectNode` preserving source key order) and `SingleNodeDefinition` emits keys in a fixed order with Jackson's `DefaultPrettyPrinter`. A dedicated test compiles a multi-scope document (with and without data flow) twice and asserts byte-for-byte identical `specText` across all flattened nodes.
- Golden fixtures under `dws-controller/src/test/resources/v2/`:
  - Existing `timing-and-event`: updated so `reminder-delay` (`scope: "wait"`) and `await-approval` (`scope: "listen"`) are leaf `FlowNode`s (`publish-completion` remains a `StepNode`).
  - Existing `nested-try-for-catch`, `parallel-fork`, `state-and-decision`, `external-call-and-run`, `raise-and-recovery`: unchanged (already cover `for`, `try-catch`, `fork` without data flow).
  - New golden fixtures covering:
    - Nested `do` without data flow (completing the without-data-flow matrix for all 6 structural kinds).
    - All 6 structural kinds (`for`, `try-catch`, `fork`, `wait`, `listen`, nested `do`) **with** data flow (`input`/`output`/`export`), including `wait` object duration and `listen` with `timeout`/`read`, verifying companion emission, `"dataflow"` wiring, `expected-graph.json`, and schema validation.

## Risks / Trade-offs

- **[Risk] `dws-step` cannot yet boot a companion `<task>.dataflow` node until 2h lands** → Mitigation: explicitly documented as a planned phase boundary; Phase 4 deploy synthesis does not exist yet and 2h/3k build directly on the companion contract fixed here.
- **[Risk] Schema and `dws-flow` loader validation could drift over time** → Mitigation: mirror the exact `single-node-definition.schema.json` rules for `wait`, `listen`, and `dataflow` in `SingleNodeDefinitionLoader` and pin every valid/invalid case with unit tests in both `dws-controller` (`V2GoldenTest`) and `dws-flow` (`SingleNodeDefinitionLoaderTests`).
- **[Risk] `FlowNode.dataflow` and `FlowScope.dataflowAppId` could get out of sync** → Mitigation: enforce consistency in `FlowNode`'s compact constructor (throwing `IllegalArgumentException` if one is present without the other or their app IDs differ) and expose a single `FlowNode.withDataflow(StepNode)` construction path.
- **[Risk] Future 3j (`main`-level `timeout`) will also use `"timeout"`** → Mitigation: choosing `"timeout"` now for `listen` aligns with the DSL name and avoids introducing an ad-hoc synonym; 3j simply relaxes the schema's `if/else` for `timeout` from `const: "listen"` to `enum: ["main", "listen"]`.

## Migration Plan

No production migration or rollout steps are required:
- `CompilerProducer` remains defaulted to `v1`, and `V1OrchestratorCompiler` is untouched.
- Existing v2 golden fixtures without `wait`/`listen` or structural data flow remain byte-for-byte identical.
- `timing-and-event` golden fixtures are regenerated in lockstep with the classifier and schema changes.
- Rollback is a single git revert with no persistent state impact.

## Open Questions

None. ADR 0006 and ADR 0012 Decision 3 are both Accepted, and all remaining runtime behaviors (`wait` timer execution in 3e, `listen` workflow/registry/bridge in 3f/3g, companion invocation in 3k, companion execution in 2h) are explicitly scoped to their respective Phase 2/3 sub-phases.
