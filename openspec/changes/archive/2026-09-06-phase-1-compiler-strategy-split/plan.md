# Phase 1 Compiler Strategy Split — Implementation Plan

> **For agentic workers:** implement task-by-task; verify each with the stated command
> before committing. v1 logic is moved verbatim — never edit it while moving.

**Goal:** Build the ADR 0002 seam so a v2 compiler can be added without touching v1, with
byte-for-byte-identical v1 `DeploymentPlan` output.

**Architecture:** `WorkflowCompiler` becomes an interface with `V1OrchestratorCompiler` (current
body, moved verbatim) and an empty `V2StructuralCompiler`. `DeploymentPlan` gains an additive
`List<CompiledNode> flowStepGraph` (sealed `FlowNode`/`StepNode`, Jackson-polymorphic).
`CompilerProducer` picks the strategy from a scoped Dapr Configuration flag, default v1.

**Tech Stack:** Java 25, Quarkus 3.37, Jackson, Dapr SDK Configuration API, Helm, JUnit 5 + AssertJ.

---

## Task 1: Golden baseline

- [ ] **Step 1:** Add throwaway `GoldenCaptureTest` compiling every compilable fixture + inline
      OAuth/asyncapi/grpc definitions, serializing each `DeploymentPlan` to `-Dgolden.dir`.
- [ ] **Step 2:** Run `./mvnw -o test -Dtest=GoldenCaptureTest -Dgolden.dir=<pre>` against the current
      `WorkflowCompiler`; keep the output as the reference.
- [ ] **Step 3:** Confirm `./mvnw test` is green pre-refactor. Commit point: none (baseline only).

## Task 2: Extract WorkflowCompiler interface

- [ ] **Step 1:** Create `compile/WorkflowCompiler.java` as `public interface WorkflowCompiler {
      DeploymentPlan compile(String specText); }`.
- [ ] **Step 2:** Copy the current class into `compile/V1OrchestratorCompiler.java`, renaming only the
      class and constructor to `V1OrchestratorCompiler`, `implements WorkflowCompiler`, `@Override compile`.
      Do not touch any method body. Keep `public static String version(...)`.
- [ ] **Step 3:** `./mvnw -o test-compile` — callers (`WorkflowResource`, `CompilerProducer`) still
      reference `WorkflowCompiler`. Commit: `refactor(controller): extract WorkflowCompiler interface`.

## Task 3: Widen DeploymentPlan with the sealed graph

- [ ] **Step 1:** Add `model/CompiledNode.java` — sealed interface permitting `FlowNode`, `StepNode`;
      methods `nodeId/appId/definitionResource/specText/children`; default `key()` = substring after last
      `.`; `@JsonTypeInfo(use=NAME, include=PROPERTY, property="nodeType")` + `@JsonSubTypes`.
- [ ] **Step 2:** Add `model/FlowNode.java` (record with `children`) and `model/StepNode.java`
      (record, leaf `children() -> List.of()`, `Optional<String> functionAppId`), each `@JsonTypeName`.
- [ ] **Step 3:** Add `List<CompiledNode> flowStepGraph` as the last component of `DeploymentPlan`,
      copy-defensive in the canonical constructor; add a compatibility constructor delegating with
      `List.of()` so every existing `new DeploymentPlan(...)` compiles unchanged.
- [ ] **Step 4:** Unit test `key()` and polymorphic round-trip. `./mvnw -o test -Dtest=CompiledNodeTest`.
      Commit: `feat(controller): add flowStepGraph sealed node model to DeploymentPlan`.

## Task 4: V2 stub

- [ ] **Step 1:** Add `compile/V2StructuralCompiler.java` returning a `DeploymentPlan` with legacy
      fields empty and only `flowStepGraph` empty — no logic.
- [ ] **Step 2:** Unit test: v2 leaves `steps` empty. Commit folded into Task 5.

## Task 5: Producer + config Component

- [ ] **Step 1:** Rewrite `CompilerProducer` to inject `DaprClient`, read the compiler-version key from
      `dws-controller-config` via `getConfiguration`, default v1, produce the matching strategy; wrap in
      try/catch → v1 on any failure.
- [ ] **Step 2:** Add `charts/dws/templates/controller/config-component.yaml` — `configuration.redis`
      Component `dws-controller-config`, `scopes: [dws-controller]`, mirroring `definitions-component.yaml`.
- [ ] **Step 3:** Unit test `CompilerProducer` defaults to v1 with a null/throwing client.
      `./mvnw -o test`. Commit: `feat(controller): select compiler strategy from Dapr Configuration flag`.

## Task 6: Verify + clean up

- [ ] **Step 1:** Re-run `GoldenCaptureTest` through `V1OrchestratorCompiler` to `<post>`; diff `<pre>`
      vs `<post>` after removing `flowStepGraph` → zero diff.
- [ ] **Step 2:** `./mvnw test` full suite green; `./mvnw spotless:apply`; `helm lint`/`helm template`
      the chart to render the new Component.
- [ ] **Step 3:** Delete `GoldenCaptureTest`. Final commit: `test(controller): prove v1 output unchanged`.
