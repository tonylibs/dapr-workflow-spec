## 1. Golden baseline (dws-controller)

- [x] 1.1 Capture pre-refactor `DeploymentPlan` golden JSON for every compilable fixture plus inline
      OAuth/asyncapi/grpc cases, using the current `WorkflowCompiler`.
- [x] 1.2 Record the baseline as the reference for the zero-diff check; `./mvnw test` green before edits.

## 2. Extract the WorkflowCompiler interface (dws-controller)

- [x] 2.1 Add `interface WorkflowCompiler { DeploymentPlan compile(String specText); }` in the `compile` package.
- [x] 2.2 Copy the existing class body verbatim into `V1OrchestratorCompiler implements WorkflowCompiler`
      (rename class + constructor only; no logic edits).
- [x] 2.3 Add `@Override` on `compile`; keep the public static `version(...)` helper reachable by callers.
- [x] 2.4 Confirm `WorkflowResource` and all callers still reference the `WorkflowCompiler` type unchanged.

## 3. Widen DeploymentPlan with the sealed graph (dws-controller)

- [x] 3.1 Add sealed `CompiledNode` interface (`nodeId`, `appId`, `definitionResource`, `specText`,
      `children`, default `key()`) with Jackson `@JsonTypeInfo`/`@JsonSubTypes`.
- [x] 3.2 Add record `FlowNode` (with children) and record `StepNode` (leaf, `children() -> List.of()`,
      `Optional<String> functionAppId`).
- [x] 3.3 Add `List<CompiledNode> flowStepGraph` as the last component of `DeploymentPlan` with a
      compatibility constructor defaulting it to empty, so existing call sites keep compiling.

## 4. V2 stub (dws-controller)

- [x] 4.1 Add `V2StructuralCompiler implements WorkflowCompiler` returning a `DeploymentPlan` with legacy
      fields empty and only `flowStepGraph` set (empty) — no classification logic.

## 5. Strategy selection producer + config Component

- [x] 5.1 Update `CompilerProducer` to inject `DaprClient`, read the compiler-version flag via
      `getConfiguration` from `dws-controller-config`, default v1, produce the matching strategy; any
      error resolves to v1.
- [x] 5.2 Add `charts/dws/templates/controller/config-component.yaml`: `configuration.redis` Component
      `dws-controller-config`, scoped to `dws-controller` only (mirror `definitions-component.yaml`).

## 6. Tests + verification (dws-controller)

- [x] 6.1 Add unit tests: `CompiledNode.key()` derivation, polymorphic serialize/deserialize of the graph,
      v1 leaves `flowStepGraph` empty, v2 stub populates only `flowStepGraph`, `CompilerProducer` defaults v1.
- [x] 6.2 Re-run golden capture through `V1OrchestratorCompiler`; diff legacy fields against the baseline →
      zero diff.
- [x] 6.3 Run the full suite `./mvnw test` green; run `./mvnw spotless:apply` and helm lint/template for the chart.
- [x] 6.4 Remove the throwaway golden-capture test before finalizing.
