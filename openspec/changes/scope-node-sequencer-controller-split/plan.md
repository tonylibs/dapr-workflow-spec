# Scope Node Sequencer/Controller Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every compiled v2 Flow node either a sequencer (owns a task list) or a controller (owns its scope's own configuration and delegates to `do`-shaped children), so a `for` node carries `each`/`in` and a `try-catch` node carries `errors`/`retry` on their own single-node definitions.

**Architecture:** `NodeClassifier`'s recursive descent stops rendering a scope's task list on the scope node itself for `for` and `try`. Those nodes become controllers with an empty `tasks` array plus configuration read **verbatim from the raw JSON** (never the typed SDK model, which injects DSL defaults the author never wrote), and their task lists move to synthesized `do`-sequencer children (`<task>.do`, `<task>.try`, existing `<task>.catch`). `forkBranch` wrapper nodes are deleted; a fork's children are the branch root tasks' own nodes.

**Tech Stack:** Java 25, Quarkus 3.37.4, `io.serverlessworkflow:serverlessworkflow-api` 7.26.0.Final, JUnit 5 + AssertJ, `com.networknt` JSON-schema validator (draft-07), Jackson.

**Spec:** `openspec/changes/scope-node-sequencer-controller-split/` — `proposal.md`, `design.md`, `specs/workflow-structural-classification/spec.md`, `tasks.md`. Source decision: `docs/adr/0004-scope-nodes-carry-their-own-configuration.md` (read its three worked examples — they are the target node graphs, and fixture regeneration is checked against them).

## Global Constraints

- **Build/gate command (JDK 25 required, `JAVA_HOME` in this container defaults to 21):**
  `cd dws-controller && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw verify`
  Single class: `... ./mvnw test -Dtest=V2StructuralCompilerTest`
  Baseline before this change is green — a red build is your change, not the environment.
- **Branch:** all work stays on `claude/hello-im6ak2`. Never push another branch.
- **Formatting:** Spotless + google-java-format runs automatically at `process-sources`. Never hand-format; just build.
- **`@UtilityClass` (Lombok)** on static-utility classes — do not hand-write private constructors. Models are plain records; `@Data`/`@Builder`/`@Value` are banned.
- **`Optional` chaining over `if (x == null)`** in new code. Optional/absent constructor arguments go in a value object with copy-on-write withers, never trailing nulls (`FlowScope` is the established example).
- **`CompilationException` message strings are operator-facing and asserted by tests** — never reword one as a side effect of moving code.
- **No new compile-time validation.** ADR 0004 explicitly defers v2 semantic validation. A `for` missing `in` renders a node that fails schema validation; that is the accepted outcome, not a new `CompilationException`.
- **Verbatim rule:** controller configuration is read from the raw `JsonNode` walk (`rawBody`), never from the typed model.
- **Expected mid-plan red:** `V2GoldenTest` (fixture-based) goes red from Task 3 and only returns green in Task 7 after fixtures are regenerated. Every other test class stays green at every commit. Do not "fix" `V2GoldenTest` before Task 7.

---

### Task 1: `FlowScope` carries controller configuration

**Files:**
- Modify: `dws-controller/src/main/java/io/dws/controller/model/FlowScope.java`
- Modify: `dws-controller/src/main/java/io/dws/controller/model/SingleNodeDefinition.java:20-31`
- Test: `dws-controller/src/test/java/io/dws/controller/model/SingleNodeDefinitionTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `FlowScope.ForConfig(Optional<String> each, Optional<String> in, Optional<String> at, Optional<String> whileCondition)`; `FlowScope.TryCatchConfig(Optional<JsonNode> errors, Optional<JsonNode> retry)`; withers `FlowScope.withForConfig(ForConfig)` and `FlowScope.withTryCatchConfig(TryCatchConfig)`. Tasks 3 and 4 call these.

- [ ] **Step 1: Write the failing test** — append to `SingleNodeDefinitionTest`:

```java
  @Test
  void rendersAForControllersLoopConfiguration() throws Exception {
    FlowScope scope =
        FlowScope.of("for", List.of())
            .withForConfig(
                new FlowScope.ForConfig(
                    Optional.of("item"), Optional.of(".items"), Optional.empty(), Optional.empty()));

    String text =
        SingleNodeDefinition.flow(
            new SingleNodeDefinition.Envelope("order-fulfillment", "order-fulfillment@v1a2b3c4d"),
            "reserve-items",
            scope,
            Map.of("do", "reserve-items-do"));

    JsonNode node = new ObjectMapper().readTree(text);
    assertThat(node.get("scope").asText()).isEqualTo("for");
    assertThat(node.get("tasks")).isEmpty();
    assertThat(node.get("each").asText()).isEqualTo("item");
    assertThat(node.get("in").asText()).isEqualTo(".items");
    assertThat(node.has("at")).isFalse();
    assertThat(node.has("while")).isFalse();
    assertThat(node.get("children").get("do").asText()).isEqualTo("reserve-items-do");
  }

  @Test
  void rendersATryCatchControllersErrorFilterAndRetryPolicy() throws Exception {
    ObjectMapper json = new ObjectMapper();
    FlowScope scope =
        FlowScope.of("try-catch", List.of())
            .withTryCatchConfig(
                new FlowScope.TryCatchConfig(
                    Optional.of(json.readTree("{\"with\":{\"status\":402}}")),
                    Optional.of(json.readTree("\"myRetryPolicy\""))))
            .withCatch("process-payment-catch");

    String text =
        SingleNodeDefinition.flow(
            new SingleNodeDefinition.Envelope("guarded-payment", "guarded-payment@v1a2b3c4d"),
            "process-payment",
            scope,
            Map.of("try", "process-payment-try", "catch", "process-payment-catch"));

    JsonNode node = json.readTree(text);
    assertThat(node.get("scope").asText()).isEqualTo("try-catch");
    assertThat(node.get("tasks")).isEmpty();
    assertThat(node.get("catch").asText()).isEqualTo("process-payment-catch");
    assertThat(node.get("errors").get("with").get("status").asInt()).isEqualTo(402);
    assertThat(node.get("retry").asText()).isEqualTo("myRetryPolicy");
  }

  @Test
  void omitsControllerConfigurationOnASequencer() throws Exception {
    String text =
        SingleNodeDefinition.flow(
            new SingleNodeDefinition.Envelope("w", "w@v1"),
            "w-main",
            FlowScope.of("main", List.of()),
            Map.of());

    JsonNode node = new ObjectMapper().readTree(text);
    for (String field : List.of("each", "in", "at", "while", "errors", "retry", "catch", "forkMode")) {
      assertThat(node.has(field)).as(field).isFalse();
    }
  }
```

Add any missing imports at the top of the file: `com.fasterxml.jackson.databind.JsonNode`, `com.fasterxml.jackson.databind.ObjectMapper`, `io.dws.controller.model.FlowScope` is same-package (no import), `java.util.List`, `java.util.Map`, `java.util.Optional`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd dws-controller && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test -Dtest=SingleNodeDefinitionTest`
Expected: FAIL to compile — `cannot find symbol: method withForConfig(...)`.

- [ ] **Step 3: Write the implementation** — replace `FlowScope.java` body with:

```java
package io.dws.controller.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;

/**
 * The scope-specific half of a flow node's single-node definition: the DSL scope that produced the
 * node, the task list it owns, and the fields only some scopes carry.
 *
 * <p>ADR 0004 splits flow nodes into two shapes. A <em>sequencer</em> ({@code main}, {@code do})
 * carries a task list and no configuration. A <em>controller</em> ({@code for}, {@code try-catch},
 * {@code fork}) carries that scope's own configuration and an empty task list, delegating each list
 * it owns to a {@code do}-shaped child.
 *
 * @param scope the classification scope, e.g. {@code main}, {@code try-catch}, {@code fork}
 * @param tasks this scope's task list, verbatim from the submitted document; always empty on a
 *     controller
 * @param catchAppId a {@code try-catch} scope's sibling catch node, when the definition supplies one
 * @param forkMode a {@code fork} scope's {@code any}/{@code all} completion mode
 * @param forConfig a {@code for} scope's own loop configuration
 * @param tryCatchConfig a {@code try-catch} scope's own error filter and retry policy
 */
public record FlowScope(
    String scope,
    List<JsonNode> tasks,
    Optional<String> catchAppId,
    Optional<String> forkMode,
    Optional<ForConfig> forConfig,
    Optional<TryCatchConfig> tryCatchConfig) {

  /**
   * A {@code for} controller's loop configuration, read verbatim from the definition. Every field is
   * optional here rather than validated: ADR 0004 defers v2 semantic validation, and the
   * single-node schema is what requires {@code in} on a {@code for} node.
   */
  public record ForConfig(
      Optional<String> each,
      Optional<String> in,
      Optional<String> at,
      Optional<String> whileCondition) {}

  /** A {@code try-catch} controller's error filter and retry policy, read verbatim. */
  public record TryCatchConfig(Optional<JsonNode> errors, Optional<JsonNode> retry) {}

  public FlowScope {
    tasks = List.copyOf(tasks);
  }

  /** A scope carrying only a task list — every sequencer, and a controller before configuration. */
  public static FlowScope of(String scope, List<JsonNode> tasks) {
    return new FlowScope(
        scope, tasks, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  /** This scope with its dedicated catch node's app ID attached. */
  public FlowScope withCatch(String catchAppId) {
    return new FlowScope(
        scope, tasks, Optional.of(catchAppId), forkMode, forConfig, tryCatchConfig);
  }

  /** This scope with its fork completion mode attached. */
  public FlowScope withForkMode(String forkMode) {
    return new FlowScope(
        scope, tasks, catchAppId, Optional.of(forkMode), forConfig, tryCatchConfig);
  }

  /** This scope with its loop configuration attached. */
  public FlowScope withForConfig(ForConfig forConfig) {
    return new FlowScope(
        scope, tasks, catchAppId, forkMode, Optional.of(forConfig), tryCatchConfig);
  }

  /** This scope with its error filter and retry policy attached. */
  public FlowScope withTryCatchConfig(TryCatchConfig tryCatchConfig) {
    return new FlowScope(
        scope, tasks, catchAppId, forkMode, forConfig, Optional.of(tryCatchConfig));
  }
}
```

Then replace `SingleNodeDefinition.flow` with (note field order — it is the fixture byte order: `catch`, `errors`, `retry`, `each`, `in`, `at`, `while`, `forkMode`, matching ADR 0004's example JSON):

```java
  public static String flow(
      Envelope envelope, String appId, FlowScope scope, Map<String, String> children) {
    ObjectNode node = envelope(envelope, appId, "flow");
    node.put("scope", scope.scope());
    ArrayNode taskArray = node.putArray("tasks");
    scope.tasks().forEach(taskArray::add);
    ObjectNode childObject = node.putObject("children");
    children.forEach(childObject::put);
    scope.catchAppId().ifPresent(catchAppId -> node.put("catch", catchAppId));
    scope
        .tryCatchConfig()
        .ifPresent(
            config -> {
              config.errors().ifPresent(errors -> node.set("errors", errors));
              config.retry().ifPresent(retry -> node.set("retry", retry));
            });
    scope
        .forConfig()
        .ifPresent(
            config -> {
              config.each().ifPresent(each -> node.put("each", each));
              config.in().ifPresent(in -> node.put("in", in));
              config.at().ifPresent(at -> node.put("at", at));
              config.whileCondition().ifPresent(w -> node.put("while", w));
            });
    scope.forkMode().ifPresent(forkMode -> node.put("forkMode", forkMode));
    return write(node);
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd dws-controller && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test -Dtest='SingleNodeDefinitionTest,FlowNodeTest'`
Expected: PASS. `V2GoldenTest` and `V2StructuralCompilerTest` are untouched by this task and must still pass — run `./mvnw test` once to confirm the whole suite is still green.

- [ ] **Step 5: Commit**

```bash
git add dws-controller/src/main/java/io/dws/controller/model/FlowScope.java \
        dws-controller/src/main/java/io/dws/controller/model/SingleNodeDefinition.java \
        dws-controller/src/test/java/io/dws/controller/model/SingleNodeDefinitionTest.java
git commit -m "feat(controller): let FlowScope carry for and try-catch configuration"
```

---

### Task 2: `NodeNaming` derives the `do`-child identifiers

**Files:**
- Modify: `dws-controller/src/main/java/io/dws/controller/compile/v2/NodeNaming.java:41-51`
- Test: `dws-controller/src/test/java/io/dws/controller/compile/v2/NodeNamingTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `NodeNaming.forBodyNodeId(String forTaskName)` → `<name>.do`; `NodeNaming.tryBodyNodeId(String tryTaskName)` → `<name>.try`. Tasks 3 and 4 call these. `NodeNaming.branchNodeId` stays for now — Task 5 deletes it together with its only caller.

- [ ] **Step 1: Write the failing test** — append to `NodeNamingTest`:

```java
  @Test
  void derivesALoopBodyIdFromItsForTask() {
    assertThat(NodeNaming.forBodyNodeId("reserveItems")).isEqualTo("reserveItems.do");
    assertThat(NodeNaming.appId(NodeNaming.forBodyNodeId("reserveItems")))
        .isEqualTo("reserve-items-do");
  }

  @Test
  void derivesAGuardedBodyIdFromItsTryTask() {
    assertThat(NodeNaming.tryBodyNodeId("fulfillOrder")).isEqualTo("fulfillOrder.try");
    assertThat(NodeNaming.appId(NodeNaming.tryBodyNodeId("fulfillOrder")))
        .isEqualTo("fulfill-order-try");
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd dws-controller && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test -Dtest=NodeNamingTest`
Expected: FAIL to compile — `cannot find symbol: method forBodyNodeId`.

- [ ] **Step 3: Write the implementation** — add to `NodeNaming`, directly under `catchNodeId`:

```java
  /**
   * A {@code for} controller's loop-body child (ADR 0004). The controller carries the loop
   * configuration; this child carries the list it iterates.
   */
  static String forBodyNodeId(String forTaskName) {
    return forTaskName + ".do";
  }

  /**
   * A {@code try-catch} controller's guarded-body child (ADR 0004). Sibling of {@link
   * #catchNodeId}: the controller carries {@code errors}/{@code retry}, these two carry the lists.
   */
  static String tryBodyNodeId(String tryTaskName) {
    return tryTaskName + ".try";
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd dws-controller && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test -Dtest=NodeNamingTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add dws-controller/src/main/java/io/dws/controller/compile/v2/NodeNaming.java \
        dws-controller/src/test/java/io/dws/controller/compile/v2/NodeNamingTest.java
git commit -m "feat(controller): derive for and try body node ids"
```

---

### Task 3: `for` compiles to a controller with a `do` child

From here `V2GoldenTest` is red until Task 7. That is expected and documented in Global Constraints.

**Files:**
- Modify: `dws-controller/src/main/java/io/dws/controller/compile/v2/NodeClassifier.java:134-138` (`forFlow`)
- Test: `dws-controller/src/test/java/io/dws/controller/compile/V2StructuralCompilerTest.java`

**Interfaces:**
- Consumes: `NodeNaming.forBodyNodeId` (Task 2), `FlowScope.withForConfig` / `FlowScope.ForConfig` (Task 1).
- Produces: private helpers `NodeClassifier.forConfig(JsonNode rawBody)` returning `FlowScope.ForConfig`, and `NodeClassifier.text(JsonNode parent, String field)` returning `Optional<String>`. Task 4 reuses the same reading style for `errors`/`retry`.

- [ ] **Step 1: Write the failing test** — append to `V2StructuralCompilerTest`. It uses the existing `NESTED` constant and the existing `node(root, appId)` helper:

```java
  @Test
  void classifiesAForScopeAsAControllerOverADoChild() throws Exception {
    CompiledNode main = compiler.compile(NESTED).flowStepGraph().get(0);

    CompiledNode loop = node(main, "reserve-items");
    JsonNode spec = JSON.readTree(loop.specText());
    assertThat(spec.get("scope").asText()).isEqualTo("for");
    assertThat(spec.get("tasks")).isEmpty();
    assertThat(spec.get("each").asText()).isEqualTo("item");
    assertThat(spec.get("in").asText()).isEqualTo(".items");
    assertThat(spec.get("children").get("do").asText()).isEqualTo("reserve-items-do");

    CompiledNode body = node(main, "reserve-items-do");
    JsonNode bodySpec = JSON.readTree(body.specText());
    assertThat(bodySpec.get("scope").asText()).isEqualTo("do");
    assertThat(bodySpec.get("tasks")).hasSize(1);
    assertThat(bodySpec.get("children").get("reserveItem").asText()).isEqualTo("reserve-item");
    assertThat(body.children()).singleElement().isInstanceOf(StepNode.class);
  }

  @Test
  void omitsLoopFieldsTheDefinitionDoesNotWrite() throws Exception {
    CompiledNode main = compiler.compile(NESTED).flowStepGraph().get(0);

    JsonNode spec = JSON.readTree(node(main, "reserve-items").specText());
    assertThat(spec.has("at")).isFalse();
    assertThat(spec.has("while")).isFalse();
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd dws-controller && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test -Dtest=V2StructuralCompilerTest#classifiesAForScopeAsAControllerOverADoChild`
Expected: FAIL — `expected: "for" ... tasks` is not empty (the loop body is still rendered on the `for` node itself), and `children` has key `reserveItem`, not `do`.

- [ ] **Step 3: Write the implementation** — replace `forFlow` in `NodeClassifier`:

```java
  /**
   * A {@code for} scope (ADR 0004): a controller carrying the loop configuration with an empty task
   * list, whose single {@code do} child owns the list it iterates.
   */
  private static CompiledNode forFlow(
      String nodeId, ForTask forTask, JsonNode rawBody, Context context) {
    CompiledNode body =
        listFlow(
            NodeNaming.forBodyNodeId(nodeId), SCOPE_DO, forTask.getDo(), rawBody, "do", context);
    return flow(
        context,
        nodeId,
        FlowScope.of(SCOPE_FOR, List.of()).withForConfig(forConfig(rawBody)),
        List.of(body));
  }

  /**
   * A loop's configuration, read from the raw task body rather than the typed model: the SDK injects
   * DSL defaults ({@code each: item}, {@code at: index}) the author never wrote, and a node's
   * definition carries what the document said.
   */
  private static FlowScope.ForConfig forConfig(JsonNode rawBody) {
    JsonNode rawFor = rawBody.path("for");
    return new FlowScope.ForConfig(
        text(rawFor, "each"), text(rawFor, "in"), text(rawFor, "at"), text(rawBody, "while"));
  }

  /** One optional textual field of a raw object, absent when missing or not a string. */
  private static Optional<String> text(JsonNode parent, String field) {
    return Optional.ofNullable(parent.get(field)).filter(JsonNode::isTextual).map(JsonNode::asText);
  }
```

Add the import `io.dws.controller.model.FlowScope` if it is not already present (it is — `FlowScope.of` is already used).

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd dws-controller && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test -Dtest=V2StructuralCompilerTest`
Expected: the two new tests PASS. `classifiesNestedTryForAndCatch`, `keysEveryFlowNodesChildrenByItsOwnTaskNames` and `classifiesForkAsItsOwnFlowNodeWithBranchNodes` now assert the old shape — update them in place to the ADR 0004 graph before moving on:
- `classifiesNestedTryForAndCatch`: `reserveItems`' child is now `reserve-items-do`, whose child is `reserve-item`.
- `keysEveryFlowNodesChildrenByItsOwnTaskNames` uses the `assertChildrenKeysMatchTaskNames` helper, which already skips nodes with an empty `tasks` array — a `for` controller now has one, so it self-skips. No change expected; confirm by running it.
Re-run until every test in this class passes. `V2GoldenTest` stays red — leave it.

- [ ] **Step 5: Commit**

```bash
git add dws-controller/src/main/java/io/dws/controller/compile/v2/NodeClassifier.java \
        dws-controller/src/test/java/io/dws/controller/compile/V2StructuralCompilerTest.java
git commit -m "feat(controller): compile for scopes as controllers over a do child"
```

---

### Task 4: `try` compiles to a `try-catch` controller carrying `errors`/`retry`

**Files:**
- Modify: `dws-controller/src/main/java/io/dws/controller/compile/v2/NodeClassifier.java:46-52` (scope constants), `:140-176` (`tryFlow`, `catchFlow`)
- Test: `dws-controller/src/test/java/io/dws/controller/compile/V2StructuralCompilerTest.java`

**Interfaces:**
- Consumes: `NodeNaming.tryBodyNodeId` (Task 2), `FlowScope.withTryCatchConfig` / `FlowScope.TryCatchConfig` (Task 1), `NodeClassifier.text` (Task 3).
- Produces: `NodeClassifier.tryCatchConfig(JsonNode rawBody)` → `FlowScope.TryCatchConfig`; scope constant `SCOPE_TRY_CATCH = "try-catch"`.

- [ ] **Step 1: Write the failing test** — append to `V2StructuralCompilerTest`:

```java
  private static final String GUARDED =
      """
      document:
        dsl: '1.0.0'
        namespace: default
        name: guarded-payment
        version: '1.0.0'
      do:
        - processPayment:
            try:
              - rejectPayment:
                  raise:
                    error:
                      type: https://example.com/errors/payment-rejected
                      status: 402
                      title: Payment rejected
                      detail: Payment authorization failed
            catch:
              errors:
                with:
                  status: 402
              do:
                - recordFailure:
                    set:
                      status: failed
            then: end
      """;

  @Test
  void classifiesATryScopeAsAControllerCarryingItsOwnErrorFilter() throws Exception {
    CompiledNode main = compiler.compile(GUARDED).flowStepGraph().get(0);

    CompiledNode tryCatch = node(main, "process-payment");
    JsonNode spec = JSON.readTree(tryCatch.specText());
    assertThat(spec.get("scope").asText()).isEqualTo("try-catch");
    assertThat(spec.get("tasks")).isEmpty();
    assertThat(spec.get("errors").get("with").get("status").asInt()).isEqualTo(402);
    assertThat(spec.get("catch").asText()).isEqualTo("process-payment-catch");
    assertThat(spec.get("children").get("try").asText()).isEqualTo("process-payment-try");
    assertThat(spec.get("children").get("catch").asText()).isEqualTo("process-payment-catch");

    JsonNode guarded = JSON.readTree(node(main, "process-payment-try").specText());
    assertThat(guarded.get("scope").asText()).isEqualTo("do");
    assertThat(guarded.get("children").get("rejectPayment").asText()).isEqualTo("reject-payment");

    JsonNode recovery = JSON.readTree(node(main, "process-payment-catch").specText());
    assertThat(recovery.get("scope").asText()).isEqualTo("do");
    assertThat(recovery.get("children").get("recordFailure").asText()).isEqualTo("record-failure");
  }

  @Test
  void aTryWithNoErrorFilterOmitsTheErrorsField() throws Exception {
    CompiledNode main = compiler.compile(NESTED).flowStepGraph().get(0);

    JsonNode spec = JSON.readTree(node(main, "fulfill-order").specText());
    assertThat(spec.get("scope").asText()).isEqualTo("try-catch");
    assertThat(spec.has("errors")).isFalse();
    assertThat(spec.has("retry")).isFalse();
    assertThat(spec.get("children").get("try").asText()).isEqualTo("fulfill-order-try");
    assertThat(spec.get("children").get("catch").asText()).isEqualTo("fulfill-order-catch");
  }
```

Also **rewrite the two existing retry tests in place** — they currently assert that a retry-only catch loses its policy, which is exactly the defect ADR 0004 fixes:

```java
  /**
   * A retry-only {@code catch} — no {@code do} — still produces no catch node and no {@code catch}
   * field, but the retry policy now lands on the try-catch controller itself rather than surviving
   * only in the parent's verbatim task entry (ADR 0004).
   */
  @Test
  void aRetryOnlyCatchKeepsItsPolicyOnTheControllerWithNoCatchNode() throws Exception {
    String retryOnly =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: retry-only
          version: '1.0.0'
        do:
          - guarded:
              try:
                - callOut:
                    call: http
                    with:
                      method: get
                      endpoint: https://example.com/thing
              catch:
                retry: myRetryPolicy
        """;

    CompiledNode main = compiler.compile(retryOnly).flowStepGraph().get(0);
    JsonNode spec = JSON.readTree(node(main, "guarded").specText());

    assertThat(spec.get("scope").asText()).isEqualTo("try-catch");
    assertThat(spec.get("retry").asText()).isEqualTo("myRetryPolicy");
    assertThat(spec.has("catch")).isFalse();
    assertThat(spec.get("children").properties()).hasSize(1);
    assertThat(spec.get("children").get("try").asText()).isEqualTo("guarded-try");
  }
```

Keep `aTryWithNoCatchAtAllHasNoCatchNodeOrField` but update its expectations: scope `try-catch`, no `catch` field, no `errors`/`retry`, one child keyed `try`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd dws-controller && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test -Dtest=V2StructuralCompilerTest#classifiesATryScopeAsAControllerCarryingItsOwnErrorFilter`
Expected: FAIL — `expected: "try-catch" but was: "try"`.

- [ ] **Step 3: Write the implementation** — in `NodeClassifier`, replace the `SCOPE_TRY`/`SCOPE_CATCH` constants with a single `SCOPE_TRY_CATCH` (leave `SCOPE_FORK_BRANCH` for Task 5):

```java
  private static final String SCOPE_TRY_CATCH = "try-catch";
```

then replace `tryFlow` and `catchFlow`:

```java
  /**
   * A {@code try} scope (ADR 0004): a controller carrying {@code errors}/{@code retry} with an empty
   * task list, a {@code try} child owning the guarded list, and — when the definition supplies a
   * non-empty {@code catch.do} — a {@code catch} child owning the recovery list. Both children are
   * {@code do} sequencers. A {@code catch} with no {@code do} (retry-only recovery) gets no catch
   * node and no {@code catch} field, but its {@code retry} still lands on this controller.
   */
  private static CompiledNode tryCatchFlow(
      String nodeId, TryTask tryTask, JsonNode rawBody, Context context) {
    CompiledNode guarded =
        listFlow(
            NodeNaming.tryBodyNodeId(nodeId), SCOPE_DO, tryTask.getTry(), rawBody, "try", context);
    Optional<CompiledNode> catchNode =
        Optional.ofNullable(tryTask.getCatch())
            .filter(caught -> caught.getDo() != null && !caught.getDo().isEmpty())
            .map(caught -> catchFlow(nodeId, caught, rawBody.get("catch"), context));

    FlowScope scope =
        FlowScope.of(SCOPE_TRY_CATCH, List.of()).withTryCatchConfig(tryCatchConfig(rawBody));
    FlowScope scoped =
        catchNode.map(CompiledNode::appId).map(scope::withCatch).orElse(scope);

    List<CompiledNode> children = new ArrayList<>(2);
    children.add(guarded);
    catchNode.ifPresent(children::add);
    return flow(context, nodeId, scoped, children);
  }

  /** A try-catch controller's error filter and retry policy, read verbatim from {@code catch}. */
  private static FlowScope.TryCatchConfig tryCatchConfig(JsonNode rawBody) {
    JsonNode rawCatch = rawBody.path("catch");
    return new FlowScope.TryCatchConfig(copy(rawCatch, "errors"), copy(rawCatch, "retry"));
  }

  /** One optional field of a raw object, deep-copied so the node owns its own JSON. */
  private static Optional<JsonNode> copy(JsonNode parent, String field) {
    return Optional.ofNullable(parent.get(field)).map(JsonNode::deepCopy);
  }

  /** A recovery list: a {@code do} sequencer over {@code catch.do}. */
  private static CompiledNode catchFlow(
      String tryNodeId, TryTaskCatch caught, JsonNode rawCatch, Context context) {
    return listFlow(
        NodeNaming.catchNodeId(tryNodeId), SCOPE_DO, caught.getDo(), rawCatch, "do", context);
  }
```

Update the dispatch in `classifyTask` to call `tryCatchFlow`:

```java
        .or(() -> task.map(Task::getTryTask).map(t -> tryCatchFlow(nodeId, t, rawBody, context)))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd dws-controller && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test -Dtest=V2StructuralCompilerTest`
Expected: PASS for the whole class. `rejectsATaskNamedCatchCollidingWithTheDedicatedCatchNode` still passes — a task named `catch` inside the guarded list now lands on the `try` child, so verify what it actually asserts and adjust the test to the node where the collision now occurs, rather than deleting the case. `V2GoldenTest` stays red.

- [ ] **Step 5: Commit**

```bash
git add dws-controller/src/main/java/io/dws/controller/compile/v2/NodeClassifier.java \
        dws-controller/src/test/java/io/dws/controller/compile/V2StructuralCompilerTest.java
git commit -m "feat(controller): compile try scopes as try-catch controllers"
```

---

### Task 5: Fork branches lose the `forkBranch` wrapper

**Files:**
- Modify: `dws-controller/src/main/java/io/dws/controller/compile/v2/NodeClassifier.java:178-211` (`forkFlow`, delete `branchFlow`)
- Modify: `dws-controller/src/main/java/io/dws/controller/compile/v2/NodeNaming.java` (delete `branchNodeId`)
- Test: `dws-controller/src/test/java/io/dws/controller/compile/V2StructuralCompilerTest.java`, `dws-controller/src/test/java/io/dws/controller/compile/v2/NodeNamingTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing new. Removes `NodeNaming.branchNodeId` and `NodeClassifier.branchFlow`.

- [ ] **Step 1: Write the failing test** — rewrite the existing `classifiesForkAsItsOwnFlowNodeWithBranchNodes` in `V2StructuralCompilerTest` (it uses the existing `FORKED` constant):

```java
  @Test
  void classifiesForkChildrenAsTheBranchRootsThemselves() throws Exception {
    CompiledNode main = compiler.compile(FORKED).flowStepGraph().get(0);

    CompiledNode fork = node(main, "notify-channels");
    JsonNode spec = JSON.readTree(fork.specText());
    assertThat(spec.get("scope").asText()).isEqualTo("fork");
    assertThat(spec.get("forkMode").asText()).isEqualTo("all");
    assertThat(spec.get("tasks")).isEmpty();
    assertThat(spec.get("children").get("notifyRecipients").asText()).isEqualTo("notify-recipients");
    assertThat(spec.get("children").get("writeAudit").asText()).isEqualTo("write-audit");

    assertThat(fork.children()).hasSize(2);
    assertThat(fork.children().get(0)).isInstanceOf(FlowNode.class);
    assertThat(fork.children().get(1)).isInstanceOf(StepNode.class);
    assertThat(main.flatten().stream().map(CompiledNode::appId))
        .noneMatch(appId -> appId.contains("-branch-"));
    assertThat(main.flatten()).hasSize(7);
  }
```

and delete the two `branchNodeId` cases from `NodeNamingTest` (search for `branchNodeId` and remove those tests — the method they cover is being deleted).

- [ ] **Step 2: Run test to verify it fails**

Run: `cd dws-controller && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test -Dtest=V2StructuralCompilerTest#classifiesForkChildrenAsTheBranchRootsThemselves`
Expected: FAIL — `children` maps `notifyRecipients` to `notify-channels-branch-notify-recipients`, and `flatten()` has 8 nodes.

- [ ] **Step 3: Write the implementation** — in `NodeClassifier`, delete the `branchFlow` method and the `SCOPE_FORK_BRANCH` constant, and classify each branch directly:

```java
  /**
   * A {@code fork} scope (ADR 0003, amended by ADR 0004): its own flow node with an empty task list,
   * {@code forkMode} from {@code compete}, and one child per {@code fork.branches} entry — the
   * branch's root task's own node, classified exactly as it would be anywhere else. A branch rooted
   * at a leaf task is therefore a {@link StepNode}, reached with {@code CallActivityAsync}; the fork
   * does not choose, the sealed type does.
   */
  private static CompiledNode forkFlow(
      String nodeId, ForkTask forkTask, JsonNode rawBody, Context context) {
    Optional<ForkTaskConfiguration> configuration = Optional.ofNullable(forkTask.getFork());
    List<TaskItem> branches =
        configuration.map(ForkTaskConfiguration::getBranches).orElseGet(List::of);
    RawTaskList rawBranches = RawTaskList.in(rawBody.get("fork"), "branches", branches);

    List<CompiledNode> children = new ArrayList<>(branches.size());
    for (int i = 0; i < branches.size(); i++) {
      children.add(classifyTask(branches.get(i), rawBranches.get(i), context));
    }
    String forkMode =
        configuration.filter(ForkTaskConfiguration::isCompete).isPresent()
            ? FORK_MODE_ANY
            : FORK_MODE_ALL;
    return flow(
        context, nodeId, FlowScope.of(SCOPE_FORK, List.of()).withForkMode(forkMode), children);
  }
```

Then delete `branchNodeId` from `NodeNaming`. Update the `requireUndottedTaskName` Javadoc, which names `branchNodeId` as one of the derived ids it protects — replace that reference with `forBodyNodeId`/`tryBodyNodeId`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd dws-controller && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test -Dtest='V2StructuralCompilerTest,NodeNamingTest,NodeClassifierTest'`
Expected: PASS. Then grep for stragglers: `grep -rn "forkBranch\|branchNodeId\|branchFlow" dws-controller/src` must return nothing.

- [ ] **Step 5: Commit**

```bash
git add dws-controller/src/main/java/io/dws/controller/compile/v2/NodeClassifier.java \
        dws-controller/src/main/java/io/dws/controller/compile/v2/NodeNaming.java \
        dws-controller/src/test/java/io/dws/controller/compile/V2StructuralCompilerTest.java \
        dws-controller/src/test/java/io/dws/controller/compile/v2/NodeNamingTest.java
git commit -m "refactor(controller): retire forkBranch nodes and their derived ids"
```

---

### Task 6: Schema enforces the two shapes

**Files:**
- Modify: `openspec/schemas/single-node-definition.schema.json:30-48` (the `flow` definition)
- Test: `dws-controller/src/test/java/io/dws/controller/compile/V2GoldenTest.java`

**Interfaces:**
- Consumes: nothing from Java tasks — the schema is standalone.
- Produces: the contract Task 7's regenerated fixtures are validated against.

- [ ] **Step 1: Write the failing test** — append to `V2GoldenTest` (it already builds a `Schema` in `theSchemaActuallyRejectsAnInvalidNode`; factor that into a small private helper `schema()` and reuse it):

```java
  @Test
  void theSchemaRejectsRetiredScopeValues() throws Exception {
    for (String retired : java.util.List.of("try", "catch", "forkBranch")) {
      JsonNode node =
          JSON.readTree(
              """
              {"workflow":"w","version":"v","nodeId":"n","kind":"flow",
               "scope":"%s","tasks":[],"children":{}}
              """
                  .formatted(retired));
      assertThat(schema().validate(node)).as(retired).isNotEmpty();
    }
  }

  @Test
  void theSchemaRejectsANonEmptyTaskListOnAController() throws Exception {
    JsonNode node =
        JSON.readTree(
            """
            {"workflow":"w","version":"v","nodeId":"n","kind":"flow","scope":"for",
             "in":".items","tasks":[{"someTask":{"set":{"a":1}}}],"children":{"do":"n-do"}}
            """);
    assertThat(schema().validate(node)).isNotEmpty();
  }

  @Test
  void theSchemaRejectsLoopFieldsOnANonForScope() throws Exception {
    JsonNode node =
        JSON.readTree(
            """
            {"workflow":"w","version":"v","nodeId":"n","kind":"flow","scope":"do",
             "tasks":[],"children":{},"each":"item","in":".items"}
            """);
    assertThat(schema().validate(node)).isNotEmpty();
  }

  @Test
  void theSchemaRejectsErrorsOnANonTryCatchScope() throws Exception {
    JsonNode node =
        JSON.readTree(
            """
            {"workflow":"w","version":"v","nodeId":"n","kind":"flow","scope":"do",
             "tasks":[],"children":{},"errors":{"with":{"status":402}}}
            """);
    assertThat(schema().validate(node)).isNotEmpty();
  }

  @Test
  void theSchemaAcceptsBothShapes() throws Exception {
    JsonNode sequencer =
        JSON.readTree(
            """
            {"workflow":"w","version":"v","nodeId":"n","kind":"flow","scope":"do",
             "tasks":[{"a":{"set":{"x":1}}}],"children":{"a":"a"}}
            """);
    JsonNode controller =
        JSON.readTree(
            """
            {"workflow":"w","version":"v","nodeId":"n","kind":"flow","scope":"try-catch",
             "tasks":[],"children":{"try":"n-try","catch":"n-catch"},"catch":"n-catch",
             "errors":{"with":{"status":402}},"retry":"myRetryPolicy"}
            """);
    assertThat(schema().validate(sequencer)).isEmpty();
    assertThat(schema().validate(controller)).isEmpty();
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd dws-controller && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test -Dtest=V2GoldenTest#theSchemaRejectsRetiredScopeValues`
Expected: FAIL — the current enum still accepts `try`, `catch`, `forkBranch`.

- [ ] **Step 3: Write the implementation** — replace the `flow` definition in `openspec/schemas/single-node-definition.schema.json` with:

```json
    "flow": {
      "allOf": [
        { "$ref": "#/definitions/envelope" },
        {
          "type": "object",
          "properties": {
            "kind": { "const": "flow" },
            "scope": { "enum": ["main", "do", "for", "try-catch", "fork"] },
            "tasks": {
              "type": "array",
              "items": { "type": "object", "minProperties": 1 }
            },
            "children": {
              "type": "object",
              "additionalProperties": { "type": "string", "minLength": 1 }
            },
            "catch": { "type": "string", "minLength": 1 },
            "forkMode": { "enum": ["all", "any"] },
            "each": { "type": "string", "minLength": 1 },
            "in": { "type": "string", "minLength": 1 },
            "at": { "type": "string", "minLength": 1 },
            "while": { "type": "string", "minLength": 1 },
            "errors": { "type": "object" },
            "retry": { "type": ["object", "string"] }
          },
          "required": ["scope", "tasks", "children"],
          "allOf": [
            {
              "if": { "properties": { "scope": { "const": "fork" } }, "required": ["scope"] },
              "then": { "required": ["forkMode"] },
              "else": { "not": { "required": ["forkMode"] } }
            },
            {
              "if": { "properties": { "scope": { "const": "for" } }, "required": ["scope"] },
              "then": { "required": ["in"] },
              "else": {
                "not": {
                  "anyOf": [
                    { "required": ["each"] },
                    { "required": ["in"] },
                    { "required": ["at"] },
                    { "required": ["while"] }
                  ]
                }
              }
            },
            {
              "if": { "properties": { "scope": { "const": "try-catch" } }, "required": ["scope"] },
              "else": {
                "not": {
                  "anyOf": [
                    { "required": ["errors"] },
                    { "required": ["retry"] },
                    { "required": ["catch"] }
                  ]
                }
              }
            },
            {
              "if": {
                "properties": { "scope": { "enum": ["for", "try-catch", "fork"] } },
                "required": ["scope"]
              },
              "then": { "properties": { "tasks": { "maxItems": 0 } } }
            }
          ]
        }
      ]
    },
```

Update the schema's top-level `description` to name the two shapes: sequencers (`main`, `do`) carry a task list; controllers (`for`, `try-catch`, `fork`) carry their scope's configuration and an empty task list.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd dws-controller && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test -Dtest=V2GoldenTest`
Expected: the five schema tests PASS; `matchesItsGoldenFixture` still FAILS for every example (fixtures not yet regenerated). Also confirm the file is valid JSON: `python3 -c "import json;json.load(open('openspec/schemas/single-node-definition.schema.json'))"`.

- [ ] **Step 5: Commit**

```bash
git add openspec/schemas/single-node-definition.schema.json \
        dws-controller/src/test/java/io/dws/controller/compile/V2GoldenTest.java
git commit -m "feat(openspec): pin the sequencer/controller shapes in the node schema"
```

---

### Task 7: Regenerate all six golden fixture sets

**Files:**
- Modify: every file under `dws-controller/src/test/resources/v2/*/expected-graph.json` and `dws-controller/src/test/resources/v2/*/nodes/*.json`
- Temporary: `dws-controller/src/test/java/io/dws/controller/compile/V2GoldenRegenerateTest.java` (created, run, then **deleted** before commit)

**Interfaces:**
- Consumes: the finished classifier (Tasks 3-5) and schema (Task 6).
- Produces: green `V2GoldenTest`.

- [ ] **Step 1: Write the regeneration utility**

Create `dws-controller/src/test/java/io/dws/controller/compile/V2GoldenRegenerateTest.java`:

```java
package io.dws.controller.compile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dws.controller.model.CompiledNode;
import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.StepNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** TEMPORARY: rewrites the v2 golden fixtures from current compiler output. Delete after use. */
class V2GoldenRegenerateTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Path EXAMPLES = Path.of("src", "test", "resources", "v2");

  @Test
  void regenerate() throws Exception {
    for (String example :
        List.of(
            "nested-try-for-catch",
            "parallel-fork",
            "state-and-decision",
            "timing-and-event",
            "external-call-and-run",
            "raise-and-recovery")) {
      Path dir = EXAMPLES.resolve(example);
      DeploymentPlan plan =
          new V2StructuralCompiler().compile(Files.readString(dir.resolve("definition.yaml")));
      CompiledNode root = plan.flowStepGraph().get(0);

      Files.writeString(
          dir.resolve("expected-graph.json"),
          JSON.writerWithDefaultPrettyPrinter().writeValueAsString(describeNode(root))
              .replace(plan.versionId(), "<versionId>"));

      Path nodes = dir.resolve("nodes");
      try (Stream<Path> stale = Files.list(nodes)) {
        for (Path path : stale.toList()) {
          Files.delete(path);
        }
      }
      for (CompiledNode node : root.flatten()) {
        Files.writeString(
            nodes.resolve(node.appId() + ".json"),
            node.specText().replace(plan.versionId(), "<versionId>"));
      }
    }
  }

  private static ObjectNode describeNode(CompiledNode node) {
    ObjectNode out = JSON.createObjectNode();
    out.put("nodeId", node.nodeId());
    out.put("appId", node.appId());
    out.put("definitionResource", node.definitionResource());
    out.put("kind", node instanceof StepNode ? "step" : "flow");
    out.put(
        "functionAppId",
        node instanceof StepNode stepNode ? stepNode.functionAppId().orElse(null) : null);
    ArrayNode children = out.putArray("children");
    for (CompiledNode child : node.children()) {
      children.add(describeNode(child));
    }
    return out;
  }
}
```

- [ ] **Step 2: Run it**

Run: `cd dws-controller && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test -Dtest=V2GoldenRegenerateTest`
Expected: PASS, and `git status` shows modified/added/deleted fixture files.

- [ ] **Step 3: Review the diff against ADR 0004 by hand — do not skip**

Run: `git status --short dws-controller/src/test/resources/v2 && git diff --stat dws-controller/src/test/resources/v2`

Check against ADR 0004's three tables:
- `nested-try-for-catch`: 9 node files — `order-fulfillment-main`, `validate-order`, `fulfill-order` (`scope: try-catch`), `fulfill-order-try`, `reserve-items` (`each`/`in`), `reserve-items-do`, `reserve-item` (`functionAppId: reserve-item-fn`), `fulfill-order-catch`, `mark-order-failed`.
- `parallel-fork`: 7 node files, no `*-branch-*` file, `notify-channels` children keyed `notifyRecipients`/`writeAudit`.
- `raise-and-recovery`: 6 node files; `process-payment.json` carries `"errors": {"with": {"status": 402}}` — this is the decisive one, the filter that previously vanished.
- The other three sets have no `for`/`try`/`fork` scopes; their diffs should be empty or whitespace-free. A non-trivial diff there means something else changed — investigate before continuing.

- [ ] **Step 4: Delete the utility and run the real gate**

```bash
rm dws-controller/src/test/java/io/dws/controller/compile/V2GoldenRegenerateTest.java
cd dws-controller && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw verify
```
Expected: BUILD SUCCESS — `V2GoldenTest` green, every node validating against the new schema.

- [ ] **Step 5: Commit**

```bash
git add dws-controller/src/test/resources/v2
git commit -m "test(controller): regenerate v2 golden fixtures for the two-shape node model"
```

---

### Task 8: Sync the roadmap doc and close out the change

**Files:**
- Modify: `docs/roadmaps/workflow-runtime-architecture.md` (lines ~45, ~79-94 classification table, ~165-210 parallel-fork example, ~388-396 acceptance criteria)
- Modify: `openspec/changes/scope-node-sequencer-controller-split/tasks.md` (check the boxes)

**Interfaces:**
- Consumes: the finished implementation.
- Produces: docs consistent with the shipped schema and classifier.

- [ ] **Step 1: Update the classification table and node glossary**

In `docs/roadmaps/workflow-runtime-architecture.md`:
- Replace the "Fork branch flow" glossary row with a two-shape description: sequencer (`main`, `do`) owns a task list; controller (`for`, `try-catch`, `fork`) owns its scope's configuration, renders an empty task list, and delegates to `do` children.
- In "Classification rules", replace the `try`/`catch` rows with one `try-catch` row and delete the "Each `fork.branches` item → Fork branch flow" row, replacing it with: each branch's root task compiles as it would anywhere else, and the fork addresses it directly.
- Line ~45 ("Top-level `main` flow, `for`, `try`, `catch`, `fork`, and fork branch-flow lifecycle") becomes `main`, `do`, `for`, `try-catch`, `fork`.

- [ ] **Step 2: Redraw the worked-example mermaid diagrams**

Replace the parallel-fork example's `BranchNotify`/`BranchAudit` nodes (lines ~204-205) with direct edges to `notify-recipients` and `write-audit`, and add the `do` children to the nested try/for/catch example. ADR 0004's "Worked examples" section has both diagrams in final form — copy their structure.

- [ ] **Step 3: Update acceptance criteria**

Criterion #2's derived-identifier list becomes `<workflow>.main`, `<try-task>.try`, `<try-task>.catch`, `<for-task>.do`; delete `<fork-task>.branch.<branch-root-task>`. Criterion #6's fork description drops "starts one child Flow per branch" wording that implies branch flows, in favor of "calls each branch root's own node — a child workflow for a Flow, an activity for a Step".

- [ ] **Step 4: Verify nothing stale remains**

```bash
grep -rn "forkBranch\|branch\.<branch\|fork branch" docs/roadmaps/workflow-runtime-architecture.md
```
Expected: no hits describing a branch node as its own scope. Then re-run the full gate one last time:
`cd dws-controller && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw verify` → BUILD SUCCESS.
And `openspec validate --all --json` from the repo root → every item `"valid": true`.

- [ ] **Step 5: Check off tasks.md and commit**

Mark every `- [ ]` in `openspec/changes/scope-node-sequencer-controller-split/tasks.md` as `- [x]`, except any genuinely not done (document why inline).

```bash
git add docs/roadmaps/workflow-runtime-architecture.md \
        openspec/changes/scope-node-sequencer-controller-split/tasks.md
git commit -m "docs(controller): sync the runtime architecture doc with ADR 0004"
```

---

## After the plan

Produce the change's remaining bridge artifacts, in this order (see `openspec/schemas/superpowers-bridge/schema.yaml`):
1. `verify.md` via `openspec-verify-change` — needs commits on the branch and `- [x]` tasks.
2. `retrospective.md` — needs `verify.md` not marked FAIL.
3. `openspec archive -y`.
