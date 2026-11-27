## 1. Single-Node Definition Schema (`dws-controller`)

- [x] 1.1 Update `dws-controller/src/test/resources/single-node-definition.schema.json` to add `wait` and `listen` to the `kind: "flow"` `scope` enum, require `tasks: { maxItems: 0 }` and `children: { maxProperties: 0 }` on leaf controllers (`wait`, `listen`), require `wait` (string or object) exclusively on `scope: "wait"`, require `to` (object) and allow optional `timeout` (string or object) and `read` (non-empty string) exclusively on `scope: "listen"`, and allow optional DNS-1123 `dataflow` on non-`main` `kind: "flow"` scopes while forbidding it on `scope: "main"` and keeping all existing scopes unchanged.
- [x] 1.2 Add schema validation unit tests in `V2GoldenTest` covering valid/invalid `wait` (ISO-8601 string and duration object, missing `wait`, non-empty `tasks`/`children`, `wait` on non-`wait` scopes), valid/invalid `listen` (`to` with optional `timeout` string/object and `read`, missing `to`, non-empty `tasks`/`children`, `to`/`timeout`/`read` on non-`listen` scopes), valid/invalid `dataflow` (allowed on all six structural scopes, forbidden on `main`, invalid DNS-1123 pattern/length rejected), and companion `kind: "step"` node validation.

## 2. `dws-controller` Compiler Model, Classification, and Companion Emission

- [x] 2.1 Add `dataflowNodeId(String taskName)` (`<taskName>.dataflow`) to `NodeNaming` and add unit tests in `NodeNamingTest` covering derivation, DNS-1123 sanitization (e.g. `reserveItems.dataflow` -> `reserve-items-dataflow`), and rejection of derived app IDs exceeding 63 characters.
- [x] 2.2 Extend `FlowScope` to carry `wait` duration (`JsonNode`), `listen` configuration (`to`, optional `timeout`, optional `read`), and optional `dataflowAppId` with copy-on-write withers; extend `FlowNode` with `Optional<StepNode> dataflow`, enforce consistency between `FlowNode.dataflow` and `FlowScope.dataflowAppId` in the compact constructor, add `FlowNode.withDataflow(StepNode)`, and override `FlowNode.flatten()` to emit `this`, optional `dataflow`, then `children()` in order; update `SingleNodeDefinition.flow(...)` to render `wait`, `to`, `timeout`, `read`, and `dataflow` in deterministic key order.
- [x] 2.3 Update `NodeClassifier` to classify `wait` and `listen` tasks as leaf `FlowNode`s (`tasks: []`, `children: []`) carrying their configuration verbatim (`wait` from task body; `to` and optional `read` from `listen`, optional `timeout` from task body) instead of `StepNode`s.
- [x] 2.4 Add companion data-flow emission in `NodeClassifier`: for every structural task (`for`, `try`, `fork`, `wait`, `listen`, and nested `do`) whose task body declares one or more of `input`, `output`, or `export`, emit one companion `StepNode` (`<task>.dataflow`, DNS-1123 `appId`, empty `functionAppId`, `task: { "<task>": { ... } }` containing only the declared `input`/`output`/`export` entries verbatim in source key order) and attach it via `FlowNode.withDataflow(...)`; emit no companion when none of those keys are declared and never emit companions for `main`, derived `do` wrappers (`.do`/`.try`/`.catch`), or leaf Step tasks.
- [x] 2.5 Add compiler unit tests in `V2StructuralCompilerTest` covering: `wait` (string and object durations), `listen` (`to`, optional `timeout` string/object, optional `read`), companion emission and omission for all six structural kinds, strict key filtering on companion `task` (no structural/control keys leaked), `AppIdRegistry` collision detection between `<task>.dataflow` and sibling task or `-fn` app IDs, and byte-identical `specText` + graph topology across two consecutive compilations of the same document.

## 3. Golden Fixtures and `dws-controller` Verification

- [x] 3.1 Update `V2GoldenTest.describeNode` to serialize `"dataflow": describeNode(companion)` on a `FlowNode` only when `dataflow` is present, keeping unchanged fixtures without data flow untouched.
- [x] 3.2 Update the checked-in `dws-controller/src/test/resources/v2/timing-and-event/` golden fixtures (`expected-graph.json`, `nodes/reminder-delay.json`, `nodes/await-approval.json`) so `reminderDelay` compiles as a `scope: "wait"` leaf Flow controller and `awaitApproval` compiles as a `scope: "listen"` leaf Flow controller.
- [x] 3.3 Add checked-in v2 golden fixtures under `dws-controller/src/test/resources/v2/` (and register them in `V2GoldenTest`) covering nested `do` without data flow and all six structural kinds (`for`, `try-catch`, `fork`, `wait`, `listen`, nested `do`) with data flow (`input`/`output`/`export`), verifying byte-exact output and zero schema violations across every node in `flatten()`.
- [x] 3.4 Run `cd dws-controller && ./mvnw verify` (or `mvnw.cmd verify` on Windows) and confirm all unit, golden, and integration tests pass.

## 4. `dws-flow` Loader and Controller Routing

- [x] 4.1 Update `dws-flow/src/SingleNodeDefinition.cs` to expose nullable `Wait`, `To`, `Timeout`, `Read`, and `Dataflow` properties on the definition record while preserving existing properties and defaults.
- [x] 4.2 Update `dws-flow/src/SingleNodeDefinitionLoader.cs` to accept `wait` and `listen` in `ValidScopes` and validate leaf-controller rules (`tasks` empty, `children` empty), scope-exclusive fields (`wait` on `wait`; `to` plus optional `timeout`/`read` on `listen`), and optional DNS-1123 `dataflow` on non-`main` scopes (rejected on `main` or invalid DNS-1123 labels).
- [x] 4.3 Add and update tests in `dws-flow/test/SingleNodeDefinitionLoaderTests.cs` and `dws-flow/test/ScopeDispatchTests.cs` verifying `wait` and `listen` definitions load cleanly (with and without `dataflow`, string and object `wait`/`timeout`, optional `read`), invalid shapes throw `DefinitionLoadException`, and running `wait` or `listen` through `ScopeDispatch` / `FlowWorkflow` fails with `config failure: scope 'wait' is not implemented yet` / `config failure: scope 'listen' is not implemented yet`.
- [x] 4.4 Run `cd dws-flow && dotnet test test/dws-flow.Tests.csproj` and confirm all tests pass.

## 5. `dws-step` Regression Check

- [x] 5.1 Run `cd dws-step && ./mvnw verify` (or `mvnw.cmd verify` on Windows) to verify `dws-step` continues to reject `wait` and `listen` at startup (naming ADR 0006) with zero regressions.

## 6. Documentation Amendments and OpenSpec Validation

- [x] 6.1 Amend `docs/roadmaps/workflow-runtime-architecture.md` per ADR 0006: update the `Workflow-task ownership` table, `Terms` table, `Classification rules` table (`wait` and `listen` classified as leaf Flow controllers), and `Example: timing and event steps` (prose and Mermaid diagram showing `reminderDelay` and `awaitApproval` as Flow nodes and `publishCompletion` as a Step node).
- [x] 6.2 Run `openspec validate phase-1-wait-listen-companion --type change --strict` and confirm the change passes strict validation before archiving.
