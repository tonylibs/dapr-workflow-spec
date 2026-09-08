## 1. Contract updates (schema + architecture doc)

- [x] 1.1 Add `fork` to the flow branch's `scope` enum in
  `openspec/schemas/single-node-definition.schema.json`
- [x] 1.2 Add `forkMode` (`enum: [all, any]`) to the flow branch, required exactly when `scope` is
  `fork` and forbidden otherwise
- [x] 1.3 Rewrite the `fork` row of the classification table in
  `docs/roadmaps/workflow-runtime-architecture.md` to match ADR 0003 (own Flow node, `forkMode`,
  empty task list) and drop the "Fork region" row's "not a separate node" treatment
- [x] 1.4 Rewrite acceptance criteria #6 and #7 in the same doc so they describe a standalone fork
  Flow node rather than an inline region
- [x] 1.5 Redraw the parallel-fork example's mermaid so the fork renders as its own node with the
  branch flows beneath it
- [x] 1.6 Validation: `openspec validate --all --json` passes, and the schema file parses as valid
  draft-07

## 2. dws-controller — shared parsing seam

- [x] 2.1 Extract `detectFormat` and `parseOrThrow` from `V1OrchestratorCompiler` into a new
  package-private `SpecParser` in `io.dws.controller.compile`, verbatim
- [x] 2.2 Have `V1OrchestratorCompiler` delegate to `SpecParser`, changing no behavior
- [x] 2.3 Validation: `cd dws-controller && ./mvnw verify` — `WorkflowCompilerTest` and
  `CompilerStrategyTest` pass unchanged

## 3. dws-controller — naming and identifiers

- [x] 3.1 Add `Names.nodeDefinitionResource(workflow, versionId, appId)`
- [x] 3.2 Add `NodeNaming` with `nodeId` derivation for `main`, `catch`, and fork branch, and
  sanitization of a dotted `nodeId` to a DNS-1123 `appId`
- [x] 3.3 Add the `-fn` suffix rule, applied to `call`/`run` steps only
- [x] 3.4 Reject an `appId` longer than 63 characters with a `CompilationException` naming the
  `nodeId` and the produced app ID
- [x] 3.5 Unit tests for every derivation form, sanitization of camelCase and dotted ids, the `-fn`
  rule, and the length rejection
- [x] 3.6 Validation: `cd dws-controller && ./mvnw verify`

## 4. dws-controller — single-node definition rendering

- [x] 4.1 Add `SingleNodeDefinition` rendering the flow shape: envelope, `scope`, source-ordered
  `tasks`, `children` projected via `CompiledNode.key()`, plus `catch` and `forkMode` where they
  apply
- [x] 4.2 Render the step shape: envelope, name-keyed `task`, and `functionAppId` on `call`/`run`
  only
- [x] 4.3 Set the envelope's `nodeId` to the sanitized `appId`, never the dotted form
- [x] 4.4 Unit tests asserting each rendered shape validates against
  `single-node-definition.schema.json`
- [x] 4.5 Validation: `cd dws-controller && ./mvnw verify`

## 5. dws-controller — classification pass

- [x] 5.1 Add `CompiledNode.flatten()` as a pre-order default method, with unit tests
- [x] 5.2 Add `NodeClassifier` walking `TaskItem`s into the `CompiledNode` tree — `FlowNode` for
  `main`/`for`/`try`/`catch`/`fork`/fork branch, `StepNode` for every other task kind
- [x] 5.3 Classify `fork` per ADR 0003: own `FlowNode`, `forkMode` from `compete`, empty `tasks`,
  branch children
- [x] 5.4 Emit a branch `FlowNode` plus a nested scope `FlowNode` when a branch's root task is
  structural
- [x] 5.5 Add the post-pass collision check over `flatten()`, raising `CompilationException` naming
  both colliding `nodeId`s and the shared app ID
- [x] 5.6 Wire `V2StructuralCompiler.compile` to parse, compute identity fields, classify, check
  collisions, and return the plan with only `flowStepGraph` and the identity fields populated
- [x] 5.7 Validation: `cd dws-controller && ./mvnw verify`

## 6. dws-controller — golden tests

- [x] 6.1 Confirm `com.networknt:json-schema-validator` resolves for tests without a `pom.xml`
  change; declare it explicitly only if it does not
- [x] 6.2 Add fixtures for the nested try/for/catch example: `definition.yaml`,
  `expected-graph.json`, and one `nodes/<appId>.json` per node
- [x] 6.3 Add fixtures for the parallel fork branches example
- [x] 6.4 Add fixtures for the state and decision steps example
- [x] 6.5 Add fixtures for the timing and event steps example
- [x] 6.6 Add fixtures for the external call and run steps example
- [x] 6.7 Add fixtures for the raise and recovery steps example
- [x] 6.8 Add the golden test asserting tree shape, `nodeId`, `appId`, `definitionResource`,
  `functionAppId`, and byte-exact `specText` for all six examples
- [x] 6.9 Add the contract test validating every rendered `specText` against
  `single-node-definition.schema.json`
- [x] 6.10 Validation: `cd dws-controller && ./mvnw verify` — full gate green, v1 tests untouched
