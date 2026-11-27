## Why

ADR 0006 reclassifies `wait` and `listen` from Step activities to leaf Flow controller nodes because Dapr durable timers and external-event waits only exist on the workflow context, and ADR 0012 Decision 3 requires a companion `<task>.dataflow` Step node whenever a structural Flow task declares `input`, `output`, or `export`. Until `V2StructuralCompiler`, the single-node definition contract/schema, and `dws-flow`'s loader reflect both decisions, Phase 3 sub-phases 3e (`wait` controller), 3f (`listen` controller), and 3k (Flow data-flow handlers) remain blocked.

## What Changes

- **Reclassify `wait` and `listen` as leaf Flow controllers (`dws-controller`)** — **BREAKING** for v2 compiled output: `V2StructuralCompiler` (`NodeClassifier`) compiles `wait` and `listen` tasks into `FlowNode` instances with `scope: "wait"` / `scope: "listen"`, empty `tasks` (`[]`), and empty `children` (`{}`), instead of `StepNode` instances.
  - A `wait` node carries its duration verbatim under `wait` (ISO-8601 string or duration object).
  - A `listen` node carries its event selection verbatim under `to` (from `listen.to`), its optional task-level `timeout` verbatim under `timeout`, and its optional `listen.read` mode verbatim under `read`.
- **Emit companion data-flow Step nodes (`dws-controller`)**: for every `for`, `try-catch`, `fork`, `wait`, `listen`, or nested `do` task that declares any of `input`, `output`, or `export`, `V2StructuralCompiler` emits one companion `StepNode` with `nodeId` `<task>.dataflow`, DNS-1123 sanitized `appId`, and a `task` object `{ "<task>": { ... } }` carrying only those declared data-flow sections verbatim. Tasks without data flow emit no companion.
- **Reference companion nodes from structural Flow nodes (`dws-controller`, contract & schema)**: a structural `FlowNode` with a companion carries a top-level `"dataflow": "<companion-app-id>"` field on its single-node definition (leaving `children` strictly for execution children, so `wait`/`listen` keep `children: {}`, `fork` keeps only branches, and `do` keeps a 1:1 map with `tasks`). The companion participates in `CompiledNode.flatten()` and `AppIdRegistry.requireDistinct` collision checks.
- **Update single-node definition schema and contract (`dws-controller`, OpenSpec)**: extend `single-node-definition.schema.json` to accept `scope: "wait"` and `scope: "listen"` with their scope-exclusive fields and `maxProperties: 0` on `children`, and accept optional `dataflow` on non-`main` flow scopes while keeping existing scopes unchanged.
- **Accept `wait`, `listen`, and `dataflow` in `dws-flow`**: update `SingleNodeDefinition` and `SingleNodeDefinitionLoader` to load and validate `wait` and `listen` controller definitions (and optional `dataflow` on non-`main` scopes), routing both through `ScopeDispatch` to fail at runtime with the existing `config failure: scope '<scope>' is not implemented yet` error.
- **Preserve `wait`/`listen` rejection in `dws-step`**: keep `dws-step` rejecting `wait` and `listen` definitions at startup (naming ADR 0006) and verify its test suite passes unchanged.
- **Determinism and golden fixtures (`dws-controller`)**: verify byte-identical compilation across repeated compiles of the same document; update the `timing-and-event` golden fixture and add golden fixtures for every structural kind (`for`, `try-catch`, `fork`, `wait`, `listen`, nested `do`) with and without data flow.
- **Amend architecture documentation (`docs/roadmaps/workflow-runtime-architecture.md`)**: update the task-ownership table, terms table, classification rules table, and the "timing and event steps" example (prose and Mermaid diagram) per ADR 0006.

### Non-Goals and Compatibility

- **v1 runtime and compiler unaffected**: `V1OrchestratorCompiler`, `dws-orchestrator`, and all existing v1 workflow definitions and deployed resources are completely unchanged; `CompilerProducer` remains defaulted to v1.
- **No runtime execution for `wait`, `listen`, or companion data flow yet**: implementing the `wait` timer (3e), `listen` workflow/bridge (3f/3g), `dws-flow` companion calls and context threading (3k), or `dws-step` companion/data-flow handler execution (2h) is out of scope.
- **No Kubernetes deploy synthesis changes**: Phase 4 (`StackSynthesizer`/`StackApplier`) is untouched; no new deployed resource types are synthesized here.
- **No workflow-level `timeout` or document-level `input`/`output`**: `main` continues to disallow `timeout` (3j) and `dataflow` (ADR 0012).
- **Backward compatibility within v2**: existing v2 fixtures without `wait`/`listen` or structural data flow compile identically (no new fields emitted when absent). Existing v2 fixtures containing `wait` or `listen` (`timing-and-event`) intentionally change from `StepNode` to leaf `FlowNode` per ADR 0006.

## Capabilities

### New Capabilities

<!-- None: all behaviour updates extend existing v2 compiler, contract, and Flow host specifications. -->

### Modified Capabilities

- `single-node-definition-contract`: add leaf controller scopes `wait` and `listen` with their scope-exclusive configuration fields (`wait`; `to`, `timeout`, `read`), enforce empty `children` on leaf controllers, and add the optional `dataflow` companion app-ID reference on non-`main` flow scopes.
- `workflow-structural-classification`: reclassify `wait` and `listen` from `StepNode` to leaf controller `FlowNode`s; emit companion `<task>.dataflow` `StepNode`s for structural tasks declaring `input`/`output`/`export`; include companions in graph flattening, app-ID collision checks, and deterministic golden test coverage across all structural kinds with and without data flow.
- `dws-flow-sequencer`: accept `wait` and `listen` single-node definitions (and optional `dataflow` on non-`main` scopes) in `dws-flow`'s loader, and route `wait` and `listen` through `ScopeDispatch` to the not-implemented configuration failure when run.

## Impact

- **`dws-controller`** (Java 25, Quarkus, Maven — gate: `cd dws-controller && ./mvnw verify`): `compile.v2` (`NodeClassifier`, `NodeNaming`), `model` (`FlowNode`, `FlowScope`, `SingleNodeDefinition`), `src/test/resources/single-node-definition.schema.json`, `V2GoldenTest`, `V2StructuralCompilerTest`, `NodeClassifierTest`, `NodeNamingTest`, and golden fixtures under `src/test/resources/v2/`.
- **`dws-flow`** (.NET 10 — gate: `cd dws-flow && dotnet test test/dws-flow.Tests.csproj`): `SingleNodeDefinition`, `SingleNodeDefinitionLoader`, and unit tests (`SingleNodeDefinitionLoaderTests`, `ScopeDispatchTests`).
- **`dws-step`** (Java 25, Spring Boot, Maven — gate: `cd dws-step && ./mvnw verify`): no functional change expected; existing `wait`/`listen` startup rejection remains intact and verified by its build gate.
- **Documentation**: `docs/roadmaps/workflow-runtime-architecture.md` amended per ADR 0006; `docs/roadmaps/workflow-runtime-architecture-roadmap.md` untouched during planning (updated when implementation lands if desired).
- **Independent build boundaries preserved**: no shared build system or cross-component code dependencies are introduced; contract alignment is pinned via `single-node-definition.schema.json` and component-local contract tests.
