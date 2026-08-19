# For Task Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Interpret the OWS DSL 1.0 `for` task in `dws-orchestrator` so a workflow author can
iterate a task list over a collection, with `$<each>`/`$<at>` bound per iteration, optional
`while` early-exit, and data threaded forward between iterations — reusing the scope-aware
task-list runner slice 2.1 built rather than duplicating it.

**Architecture:** A new `EvaluateForActivity` (parallel to `EvaluateSetActivity`) evaluates
`for.in` once and returns the collection. A new `EvaluateWhileActivity` (parallel to
`EvaluateSwitchActivity`) evaluates `while` per iteration for truthiness. A new `dispatchFor`
helper in `InterpreterWorkflow` (parallel to `dispatchTry`) drives the loop: per iteration it
clones `variables` into a scope-local `HashMap`, binds `$<each>`/`$<at>`, evaluates `while` when
declared, and calls `runTaskList(...)` at `depth + 1`. `DefinitionLookup.search` gains one branch
recursing into `ForTask.getDo()` so tasks nested under `for.do` are resolvable by name. Zero
`dws-controller` code changes.

**Tech Stack:** Java 25, Spring Boot, Maven, `io.serverlessworkflow:serverlessworkflow-types:7.26
.0.Final` (SDK), Jackson, jackson-jq (`JqEvaluator`), JUnit 5, AssertJ, Mockito. All work is in
`dws-orchestrator/`; `dws-controller/` gets no code changes (only a confirmation test run).

## Global Constraints

- No `Instant.now()`/`UUID.randomUUID()`/randomness inside `InterpreterWorkflow.execute()` or any
  workflow-method code — the workflow must stay replay-deterministic. All new logic in
  `dispatchFor` is either a `ctx.callActivity(...)` (Dapr-recorded) or a pure computation over
  already-recorded values (iteration variables, indices, collection elements).
- `for` gets zero `dws-controller` changes — confirmed by reading `WorkflowCompiler.walk()`,
  whose existing comment already excludes both `for` itself and the task lists nested under
  `for.do`/`fork` from what it deploys.
- Every new/changed file matches this codebase's per-file Javadoc convention: a short class
  comment explaining *why*, not what.
- Test commands: `./mvnw verify` (or `./mvnw test -Dtest=ClassName` for one class) run from
  `dws-orchestrator/`; `./mvnw test` from `dws-controller/` for the confirmation run.
- SDK facts used throughout this plan were verified empirically (not assumed) by disassembling
  `serverlessworkflow-types:7.26.0.Final` with `javap`: `ForTask.getFor()` →
  `ForTaskConfiguration` (`getEach`/`getIn`/`getAt`, all `String`); `ForTask.getWhile()` →
  `String`; `ForTask.getDo()` → `List<TaskItem>`. `for.in`/`while` are plain jq expressions
  (`${...}`-wrapping optional per `JqEvaluator.unwrap`). Defaults `each="item"`, `at="index"`
  per DSL 1.0 spec.

---

## Task 1: `EvaluateForRequest` and `EvaluateForActivity`

**Files:**
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/EvaluateForRequest
  .java`
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/EvaluateForActivity
  .java`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/config/WorkflowRuntimeBootstrap.java`
- Test: `dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/activity/EvaluateForActivityTest
  .java`

**Interfaces:**
- Consumes: `DefinitionLookup.taskByName(String)` → `Task`; `JqEvaluator.evaluate(String,
  JsonNode, Map<String, JsonNode>)` → `JsonNode`; `WorkflowSupport.jq()`/`mapper()`.
- Produces: `EvaluateForRequest(String taskName, JsonNode data, Map<String, JsonNode>
  variables)` implementing `StepRequest`. `EvaluateForActivity.apply(EvaluateForRequest request)`
  → `JsonNode` — the collection (array) `for.in` evaluates to; consumed by Task 4.

- [ ] **Step 1: Write the failing tests**

Create `EvaluateForActivityTest.java` (imports and seeding mirror `RaiseErrorActivityTest`):

```java
package io.dws.orchestrator.workflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvaluateForActivityTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private void seed(String yaml) throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromString(yaml, WorkflowFormat.YAML);
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "for-workflow",
        "for-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        null,
        mock(WorkflowTaskOptions.class),
        "pubsub");
  }

  private EvaluateForRequest request(String taskName, JsonNode data) {
    return new EvaluateForRequest(taskName, data, Map.of());
  }

  @Test
  void bareJqExpressionYieldsTheArray() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: for-workflow
          version: '1.0.0'
        do:
          - loop:
              for:
                each: pet
                in: .pets
              do:
                - noop:
                    set:
                      done: '"yes"'
        """);

    JsonNode data = mapper.readTree("{\"pets\":[{\"id\":1},{\"id\":2}]}");
    JsonNode result = EvaluateForActivity.apply(request("loop", data));

    assertThat(result.isArray()).isTrue();
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get(0).get("id").intValue()).isEqualTo(1);
  }

  @Test
  void wrappedExpressionYieldsTheArray() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: for-workflow
          version: '1.0.0'
        do:
          - loop:
              for:
                each: pet
                in: '${ .pets }'
              do:
                - noop:
                    set:
                      done: '"yes"'
        """);

    JsonNode data = mapper.readTree("{\"pets\":[1,2,3]}");
    JsonNode result = EvaluateForActivity.apply(request("loop", data));

    assertThat(result.isArray()).isTrue();
    assertThat(result.size()).isEqualTo(3);
  }

  @Test
  void scopeVariableIsBoundIntoInExpression() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: for-workflow
          version: '1.0.0'
        do:
          - loop:
              for:
                each: n
                in: $sample
              do:
                - noop:
                    set:
                      done: '"yes"'
        """);

    JsonNode data = mapper.createObjectNode();
    JsonNode sample = mapper.readTree("[10,20,30]");
    EvaluateForRequest request = new EvaluateForRequest("loop", data, Map.of("sample", sample));

    JsonNode result = EvaluateForActivity.apply(request);

    assertThat(result.isArray()).isTrue();
    assertThat(result.size()).isEqualTo(3);
    assertThat(result.get(1).intValue()).isEqualTo(20);
  }

  @Test
  void nonArrayResultIsRejected() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: for-workflow
          version: '1.0.0'
        do:
          - loop:
              for:
                each: n
                in: .count
              do:
                - noop:
                    set:
                      done: '"yes"'
        """);

    JsonNode data = mapper.readTree("{\"count\":3}");

    assertThatThrownBy(() -> EvaluateForActivity.apply(request("loop", data)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("loop")
        .hasMessageContaining("array");
  }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=EvaluateForActivityTest` (from `dws-orchestrator/`)
Expected: FAIL — `EvaluateForRequest`/`EvaluateForActivity` do not exist.

- [ ] **Step 3: Create `EvaluateForRequest`**

```java
package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Input to {@link EvaluateForActivity}: the name of the FOR task to resolve (against the pod's
 * pinned definition) and the current workflow data its {@code for.in} expression is evaluated
 * over. {@code variables} carries scope-local jq bindings inherited from an enclosing scope (e.g.
 * a {@code catch.do}'s error variable when the {@code for} nests inside {@code try}); empty at
 * the top level, mirroring {@link EvaluateSetRequest}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluateForRequest(String taskName, JsonNode data, Map<String, JsonNode> variables)
    implements StepRequest {}
```

- [ ] **Step 4: Create `EvaluateForActivity`**

```java
package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.databind.JsonNode;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.Task;

/**
 * Evaluates a FOR task's {@code for.in} expression to the collection to iterate. Pure jq
 * evaluation with no I/O — parallel to {@link EvaluateSwitchActivity}, it runs in the
 * orchestrator's own JVM and exists as an activity purely so every task type dispatches through
 * {@code ctx.callActivity(...)} uniformly, keeping evaluation out of the workflow method's
 * replay loop. Rejects a non-array result with an {@link IllegalStateException} so the failure
 * flows through the standard task-failure path.
 */
public class EvaluateForActivity implements WorkflowActivity {

  @Override
  public Object run(WorkflowActivityContext ctx) {
    return apply(ctx.getInput(EvaluateForRequest.class));
  }

  public static JsonNode apply(EvaluateForRequest request) {
    Task task = DefinitionLookup.taskByName(request.taskName());
    ForTask forTask = task.getForTask();
    if (forTask == null) {
      throw new IllegalStateException("task '" + request.taskName() + "' is not a for task");
    }
    String expression = forTask.getFor() == null ? null : forTask.getFor().getIn();
    if (expression == null || expression.isBlank()) {
      throw new IllegalStateException(
          "for task '" + request.taskName() + "' declares no in expression");
    }
    JqEvaluator jq = WorkflowSupport.jq();
    JsonNode result = jq.evaluate(expression, request.data(), EvaluateSetActivity.scope(request.variables()));
    if (result == null || !result.isArray()) {
      throw new IllegalStateException(
          "for task '"
              + request.taskName()
              + "' expected in to evaluate to an array, got: "
              + (result == null ? "null" : result.getNodeType()));
    }
    return result;
  }
}
```

- [ ] **Step 5: Register the activity**

In `dws-orchestrator/src/main/java/io/dws/orchestrator/config/WorkflowRuntimeBootstrap.java`, add
the import and registration alongside the existing ones:

```java
import io.dws.orchestrator.workflow.activity.EvaluateForActivity;
```

```java
    builder.registerActivity(RaiseErrorActivity.class);
    builder.registerActivity(EvaluateForActivity.class);
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=EvaluateForActivityTest` (from `dws-orchestrator/`)
Expected: PASS — all four cases.

- [ ] **Step 7: Commit**

```bash
git add dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/EvaluateForRequest.java \
        dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/EvaluateForActivity.java \
        dws-orchestrator/src/main/java/io/dws/orchestrator/config/WorkflowRuntimeBootstrap.java \
        dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/activity/EvaluateForActivityTest.java
git commit -m "feat(orchestrator): add EvaluateForActivity resolving a for task's collection"
```

---

## Task 2: `EvaluateWhileRequest` and `EvaluateWhileActivity`

**Files:**
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/EvaluateWhileRequest
  .java`
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/EvaluateWhileActivity
  .java`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/config/WorkflowRuntimeBootstrap.java`
- Test: `dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/activity/EvaluateWhileActivityTest
  .java`

**Interfaces:**
- Consumes: `DefinitionLookup.taskByName`, `JqEvaluator.evaluateBoolean(String, JsonNode,
  Map<String, JsonNode>)`, `WorkflowSupport.jq()`.
- Produces: `EvaluateWhileRequest(String taskName, JsonNode data, Map<String, JsonNode>
  variables)` implementing `StepRequest`. `EvaluateWhileActivity.apply(EvaluateWhileRequest
  request)` → `boolean`; consumed by Task 4.

- [ ] **Step 1: Write the failing tests**

```java
package io.dws.orchestrator.workflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvaluateWhileActivityTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private void seed(String yaml) throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromString(yaml, WorkflowFormat.YAML);
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "for-workflow",
        "for-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        null,
        mock(WorkflowTaskOptions.class),
        "pubsub");
  }

  @Test
  void truthyResultIsTrue() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: for-workflow
          version: '1.0.0'
        do:
          - loop:
              for:
                each: n
                in: .items
              while: .count > 0
              do:
                - noop:
                    set:
                      done: '"yes"'
        """);
    JsonNode data = mapper.readTree("{\"count\":3}");

    assertThat(EvaluateWhileActivity.apply(new EvaluateWhileRequest("loop", data, Map.of())))
        .isTrue();
  }

  @Test
  void falsyResultsAreFalse() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: for-workflow
          version: '1.0.0'
        do:
          - loop:
              for:
                each: n
                in: .items
              while: .flag
              do:
                - noop:
                    set:
                      done: '"yes"'
        """);
    assertThat(
            EvaluateWhileActivity.apply(
                new EvaluateWhileRequest("loop", mapper.readTree("{\"flag\":false}"), Map.of())))
        .isFalse();
    assertThat(
            EvaluateWhileActivity.apply(
                new EvaluateWhileRequest("loop", mapper.readTree("{\"flag\":null}"), Map.of())))
        .isFalse();
    assertThat(
            EvaluateWhileActivity.apply(
                new EvaluateWhileRequest("loop", mapper.createObjectNode(), Map.of())))
        .isFalse();
  }

  @Test
  void variableIsBoundIntoWhile() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: for-workflow
          version: '1.0.0'
        do:
          - loop:
              for:
                each: item
                in: .items
              while: '$item < 3'
              do:
                - noop:
                    set:
                      done: '"yes"'
        """);
    JsonNode item = mapper.readTree("2");

    assertThat(
            EvaluateWhileActivity.apply(
                new EvaluateWhileRequest("loop", mapper.createObjectNode(), Map.of("item", item))))
        .isTrue();

    JsonNode bigItem = mapper.readTree("5");
    assertThat(
            EvaluateWhileActivity.apply(
                new EvaluateWhileRequest("loop", mapper.createObjectNode(), Map.of("item", bigItem))))
        .isFalse();
  }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=EvaluateWhileActivityTest` (from `dws-orchestrator/`)
Expected: FAIL — request/activity do not exist.

- [ ] **Step 3: Create the request record**

```java
package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Input to {@link EvaluateWhileActivity}: the name of the FOR task whose {@code while} to
 * evaluate, the current iteration's data, and the scope-local variables (including the iteration
 * variables bound by {@code dispatchFor}). Called once per iteration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluateWhileRequest(String taskName, JsonNode data, Map<String, JsonNode> variables)
    implements StepRequest {}
```

- [ ] **Step 4: Create the activity**

```java
package io.dws.orchestrator.workflow.activity;

import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.Task;

/**
 * Evaluates a FOR task's {@code while} expression for jq truthiness. Pure jq evaluation with no
 * I/O — parallel to {@link EvaluateSwitchActivity}, it exists as an activity so evaluation stays
 * out of the workflow method's replay loop. Called once per iteration by {@code dispatchFor}.
 */
public class EvaluateWhileActivity implements WorkflowActivity {

  @Override
  public Object run(WorkflowActivityContext ctx) {
    return apply(ctx.getInput(EvaluateWhileRequest.class));
  }

  public static boolean apply(EvaluateWhileRequest request) {
    Task task = DefinitionLookup.taskByName(request.taskName());
    ForTask forTask = task.getForTask();
    if (forTask == null) {
      throw new IllegalStateException("task '" + request.taskName() + "' is not a for task");
    }
    String expression = forTask.getWhile();
    if (expression == null || expression.isBlank()) {
      throw new IllegalStateException(
          "for task '" + request.taskName() + "' declares no while expression");
    }
    JqEvaluator jq = WorkflowSupport.jq();
    return jq.evaluateBoolean(
        expression, request.data(), EvaluateSetActivity.scope(request.variables()));
  }
}
```

- [ ] **Step 5: Register the activity**

In `WorkflowRuntimeBootstrap.java`:

```java
import io.dws.orchestrator.workflow.activity.EvaluateWhileActivity;
```

```java
    builder.registerActivity(EvaluateForActivity.class);
    builder.registerActivity(EvaluateWhileActivity.class);
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=EvaluateWhileActivityTest` (from `dws-orchestrator/`)
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/EvaluateWhileRequest.java \
        dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/EvaluateWhileActivity.java \
        dws-orchestrator/src/main/java/io/dws/orchestrator/config/WorkflowRuntimeBootstrap.java \
        dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/activity/EvaluateWhileActivityTest.java
git commit -m "feat(orchestrator): add EvaluateWhileActivity for per-iteration while evaluation"
```

---

## Task 3: `DefinitionLookup.search` recurses into `ForTask.getDo()`

**Files:**
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/DefinitionLookup
  .java`
- Test: `dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/activity/DefinitionLookupTest
  .java` (create if absent)

**Interfaces:**
- Modified: `DefinitionLookup.search(List<TaskItem>, String)` — now also descends into
  `ForTask.getDo()`.

- [ ] **Step 1: Write the failing test**

Create `DefinitionLookupTest.java` if absent:

```java
package io.dws.orchestrator.workflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.Workflow;
import org.junit.jupiter.api.Test;

class DefinitionLookupTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private void seed(String yaml) throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromString(yaml, WorkflowFormat.YAML);
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "lookup-workflow",
        "lookup-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        null,
        mock(WorkflowTaskOptions.class),
        "pubsub");
  }

  @Test
  void taskInsideForDoResolvesByName() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: lookup-workflow
          version: '1.0.0'
        do:
          - loop:
              for:
                each: n
                in: .items
              do:
                - nestedSet:
                    set:
                      done: '"yes"'
        """);
    Task task = DefinitionLookup.taskByName("nestedSet");
    assertThat(task.getSetTask()).isNotNull();
  }

  @Test
  void taskInsideForDoInsideTryResolvesByName() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: lookup-workflow
          version: '1.0.0'
        do:
          - guarded:
              try:
                - loop:
                    for:
                      each: n
                      in: .items
                    do:
                      - deeplyNested:
                          set:
                            done: '"yes"'
              catch:
                do: []
        """);
    Task task = DefinitionLookup.taskByName("deeplyNested");
    assertThat(task.getSetTask()).isNotNull();
  }

  @Test
  void unknownNameStillFailsLoudly() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: lookup-workflow
          version: '1.0.0'
        do:
          - loop:
              for:
                each: n
                in: .items
              do:
                - present:
                    set:
                      done: '"yes"'
        """);
    assertThatThrownBy(() -> DefinitionLookup.taskByName("absent"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("absent");
  }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=DefinitionLookupTest` (from `dws-orchestrator/`)
Expected: FAIL on the first two cases (the third already passes) — `for.do` isn't walked.

- [ ] **Step 3: Extend `DefinitionLookup.search`**

In `DefinitionLookup.java`, add `import io.serverlessworkflow.api.types.ForTask;` at the top,
and extend the loop body:

```java
    for (TaskItem item : items) {
      if (item.getName().equals(taskName)) {
        return item.getTask();
      }
      Task task = item.getTask();
      if (task == null) {
        continue;
      }
      TryTask tryTask = task.getTryTask();
      if (tryTask != null) {
        Task nested = search(tryTask.getTry(), taskName);
        if (nested == null && tryTask.getCatch() != null) {
          nested = search(tryTask.getCatch().getDo(), taskName);
        }
        if (nested != null) {
          return nested;
        }
      }
      ForTask forTask = task.getForTask();
      if (forTask != null) {
        Task nested = search(forTask.getDo(), taskName);
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
```

Update the Javadoc for `search` to mention `for.do`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=DefinitionLookupTest` (from `dws-orchestrator/`)
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/DefinitionLookup.java \
        dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/activity/DefinitionLookupTest.java
git commit -m "feat(orchestrator): DefinitionLookup recurses into for.do"
```

---

## Task 4: `InterpreterWorkflow.dispatchFor` and dispatch wiring

**Files:**
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/InterpreterWorkflow.java`

**Interfaces:**
- Consumes: `EvaluateForActivity`/`EvaluateForRequest` (Task 1), `EvaluateWhileActivity`/
  `EvaluateWhileRequest` (Task 2), `runTaskList` (existing).
- Produces: `InterpreterWorkflow` now interprets `for`. `dispatchConcreteTask`'s `case ForTask _`
  is replaced with `case ForTask forTask -> dispatchFor(...)`.

- [ ] **Step 1: Replace the stub and add `dispatchFor`**

In `InterpreterWorkflow.java`:

Replace the current `case ForTask _` (lines 343–345):

```java
      case ForTask forTask ->
          dispatchFor(ctx, forTask, name, data, context, variables, depth, events, mapper);
```

Add `dispatchFor` below `dispatchTry`:

```java
  /**
   * Runs a for task: evaluate the collection once, then run the body once per element with the
   * iteration variables bound as scope-local jq variables. When {@code while} is declared it is
   * re-evaluated at the top of each iteration and stops the loop when false; when absent no
   * activity crossing happens per iteration.
   *
   * <p>Iterations thread data forward — iteration N + 1's input data is iteration N's body
   * output. Each iteration's body scope is at {@code depth + 1}; iterations themselves are
   * siblings, so the loop does not consume {@link #MAX_DEPTH}.
   */
  private Body dispatchFor(
      WorkflowContext ctx,
      ForTask forTask,
      String name,
      JsonNode data,
      JsonNode context,
      Map<String, JsonNode> variables,
      int depth,
      AdminEventBuilder events,
      ObjectMapper mapper) {
    FlowOutcome then = FlowOutcome.of(forTask.getThen());

    JsonNode collection =
        ctx.callActivity(
                EvaluateForActivity.class.getName(),
                new EvaluateForRequest(name, data, variables),
                WorkflowSupport.defaultTaskOptions(),
                JsonNode.class)
            .await();

    if (collection == null || collection.isEmpty()) {
      return new Body(data, context, then, ScopeEnd.FELL_THROUGH);
    }

    ForTaskConfiguration config = forTask.getFor();
    String eachName = nameOr(config == null ? null : config.getEach(), "item");
    String atName = nameOr(config == null ? null : config.getAt(), "index");
    boolean hasWhile = forTask.getWhile() != null && !forTask.getWhile().isBlank();

    JsonNode iterationData = data;
    JsonNode iterationContext = context;
    for (int index = 0; index < collection.size(); index++) {
      Map<String, JsonNode> scoped = new HashMap<>(variables);
      scoped.put(eachName, collection.get(index));
      scoped.put(atName, mapper.getNodeFactory().numberNode(index));

      if (hasWhile) {
        boolean keepGoing =
            ctx.callActivity(
                    EvaluateWhileActivity.class.getName(),
                    new EvaluateWhileRequest(name, iterationData, scoped),
                    WorkflowSupport.defaultTaskOptions(),
                    Boolean.class)
                .await();
        if (!keepGoing) {
          return new Body(iterationData, iterationContext, then, ScopeEnd.FELL_THROUGH);
        }
      }

      ScopeResult result =
          runTaskList(
              ctx, forTask.getDo(), iterationData, iterationContext, scoped, depth + 1, events,
              mapper);
      iterationData = result.data();
      iterationContext = result.context();
      if (result.end() == ScopeEnd.END) {
        return new Body(iterationData, iterationContext, then, ScopeEnd.END);
      }
      // ScopeEnd.EXIT completes only this iteration's scope; the loop continues.
    }

    return new Body(iterationData, iterationContext, then, ScopeEnd.FELL_THROUGH);
  }

  /** Returns {@code name} when non-blank, otherwise the default. */
  private static String nameOr(String name, String fallback) {
    return (name == null || name.isBlank()) ? fallback : name;
  }
```

Add the `ForTaskConfiguration` import at the top if not already present (via the wildcard
`io.serverlessworkflow.api.types.*` it is already covered).

- [ ] **Step 2: Run the orchestrator's existing tests to verify no regression**

Run: `./mvnw test` (from `dws-orchestrator/`)
Expected: PASS — every existing case unaffected; nothing new here is exercised yet by the
existing suite, but the compile must succeed and every prior case must still pass.

- [ ] **Step 3: Commit**

```bash
git add dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/InterpreterWorkflow.java
git commit -m "feat(orchestrator): dispatch for tasks via dispatchFor over runTaskList"
```

---

## Task 5: Integration coverage in `InterpreterWorkflowIntegrationTest`

**Files:**
- Modify: `dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/InterpreterWorkflowIntegrationTest
  .java`

**Interfaces:** Consumes Tasks 1–4. No new production interface.

- [ ] **Step 1: Extend `stubContext` with stubs for the two new activities**

Add to the imports:

```java
import io.dws.orchestrator.workflow.activity.EvaluateForActivity;
import io.dws.orchestrator.workflow.activity.EvaluateForRequest;
import io.dws.orchestrator.workflow.activity.EvaluateWhileActivity;
import io.dws.orchestrator.workflow.activity.EvaluateWhileRequest;
```

Inside `stubContext(WorkflowContext ctx)`, alongside the existing stubs:

```java
    when(ctx.callActivity(
            eq(EvaluateForActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv -> completed(EvaluateForActivity.apply((EvaluateForRequest) inv.getArgument(1))));

    when(ctx.callActivity(
            eq(EvaluateWhileActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(Boolean.class)))
        .thenAnswer(
            inv ->
                completed(
                    EvaluateWhileActivity.apply((EvaluateWhileRequest) inv.getArgument(1))));
```

- [ ] **Step 2: Add the integration cases**

```java
  // ---- for -----------------------------------------------------------------

  @Test
  void forIteratesTheBodyOncePerElement() throws Exception {
    Workflow definition =
        WorkflowReader.readWorkflowFromString(
            """
            document:
              dsl: 1.0.0
              namespace: examples
              name: for-workflow
              version: '1.0.0'
            do:
              - loop:
                  for:
                    each: n
                    in: .items
                  do:
                    - accumulate:
                        set:
                          seen: '(.seen // []) + [ $n ]'
            """,
            WorkflowFormat.YAML);
    seedDefinition(definition);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"items\":[10,20,30]}"));

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    assertThat(output.get("seen"))
        .isEqualTo(mapper.readTree("[10,20,30]"));
  }

  @Test
  void forBindsIndexVariable() throws Exception {
    Workflow definition =
        WorkflowReader.readWorkflowFromString(
            """
            document:
              dsl: 1.0.0
              namespace: examples
              name: for-workflow
              version: '1.0.0'
            do:
              - loop:
                  for:
                    each: n
                    in: .items
                    at: i
                  do:
                    - accumulate:
                        set:
                          idx: '(.idx // []) + [ $i ]'
            """,
            WorkflowFormat.YAML);
    seedDefinition(definition);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"items\":[\"a\",\"b\",\"c\"]}"));

    workflow.execute(ctx);

    assertThat(completionOutput(ctx).get("idx"))
        .isEqualTo(mapper.readTree("[0,1,2]"));
  }

  @Test
  void forStopsWhenWhileBecomesFalse() throws Exception {
    Workflow definition =
        WorkflowReader.readWorkflowFromString(
            """
            document:
              dsl: 1.0.0
              namespace: examples
              name: for-workflow
              version: '1.0.0'
            do:
              - loop:
                  for:
                    each: n
                    in: .items
                    at: i
                  while: '$i < 2'
                  do:
                    - accumulate:
                        set:
                          seen: '(.seen // []) + [ $n ]'
            """,
            WorkflowFormat.YAML);
    seedDefinition(definition);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"items\":[1,2,3,4,5]}"));

    workflow.execute(ctx);

    assertThat(completionOutput(ctx).get("seen"))
        .isEqualTo(mapper.readTree("[1,2]"));
  }

  @Test
  void forOverEmptyCollectionRunsBodyZeroTimes() throws Exception {
    Workflow definition =
        WorkflowReader.readWorkflowFromString(
            """
            document:
              dsl: 1.0.0
              namespace: examples
              name: for-workflow
              version: '1.0.0'
            do:
              - loop:
                  for:
                    each: n
                    in: .items
                  do:
                    - accumulate:
                        set:
                          seen: '(.seen // []) + [ $n ]'
              - marker:
                  set:
                    done: '"yes"'
            """,
            WorkflowFormat.YAML);
    seedDefinition(definition);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"items\":[]}"));

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    assertThat(output.has("seen")).isFalse();
    assertThat(output.get("done").textValue()).isEqualTo("yes");
  }

  @Test
  void forInsideTryHasItsFailureCaught() throws Exception {
    Workflow definition =
        WorkflowReader.readWorkflowFromString(
            """
            document:
              dsl: 1.0.0
              namespace: examples
              name: for-workflow
              version: '1.0.0'
            do:
              - guarded:
                  try:
                    - loop:
                        for:
                          each: n
                          in: .items
                        do:
                          - explode:
                              raise:
                                error:
                                  type: https://example.com/errors/x
                                  status: 400
                                  title: Bad
                                  detail: 'per-element failure'
                  catch:
                    errors:
                      with:
                        status: 400
                    do:
                      - repair:
                          set:
                            reason: '$error.detail'
              - finish:
                  set:
                    done: '"yes"'
            """,
            WorkflowFormat.YAML);
    seedDefinition(definition);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"items\":[1,2,3]}"));

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    assertThat(output.get("reason").textValue()).isEqualTo("per-element failure");
    assertThat(output.get("done").textValue()).isEqualTo("yes");
  }

  @Test
  void nonArrayForInFailsTheTask() throws Exception {
    Workflow definition =
        WorkflowReader.readWorkflowFromString(
            """
            document:
              dsl: 1.0.0
              namespace: examples
              name: for-workflow
              version: '1.0.0'
            do:
              - loop:
                  for:
                    each: n
                    in: .count
                  do:
                    - noop:
                        set:
                          done: '"yes"'
            """,
            WorkflowFormat.YAML);
    seedDefinition(definition);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"count\":3}"));

    assertThatThrownBy(() -> workflow.execute(ctx))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("loop");
    verify(ctx, never()).complete(any());
  }
```

(`seedDefinition` and `completionOutput` reflect the file's existing test helpers; if the class
uses different names, substitute the equivalents already in use in that file. If the file has no
`seedDefinition` helper, inline `WorkflowSupport.init(...)` per the pattern
`RaiseErrorActivityTest.seed` already uses.)

- [ ] **Step 3: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=InterpreterWorkflowIntegrationTest` (from `dws-orchestrator/`)
Expected: PASS — every new case plus every pre-existing case unaffected.

- [ ] **Step 4: Commit**

```bash
git add dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/InterpreterWorkflowIntegrationTest.java
git commit -m "test(orchestrator): integration coverage for the for task"
```

---

## Task 6: Full verification gate, controller confirmation, roadmap update

**Files:**
- Modify: `docs/roadmaps/openworkflow-features.md`

**Interfaces:** None — verification and documentation only.

- [ ] **Step 1: Run the full orchestrator gate**

Run: `./mvnw verify` (from `dws-orchestrator/`)
Expected: PASS, all modules green.

- [ ] **Step 2: Run the full controller gate to confirm no unintended compile-path change**

Run: `./mvnw test` (from `dws-controller/`)
Expected: PASS. This module received zero code changes; the run confirms that fact holds (no
stray dependency on orchestrator internals, no shared-schema drift) and that a definition
containing a `for` task compiles to the same set of `StepService`/`TopicBinding` as the same
definition without the `for` — consistent with `WorkflowCompiler.walk()`'s existing no-op
treatment.

- [ ] **Step 3: Update the roadmap**

In `docs/roadmaps/openworkflow-features.md`:

In the task-type coverage table (§1), change the `for` row:

```diff
-| `for` | ⚠️ | recognized, throws `UnsupportedOperationException` |
+| `for` | ✅ | in-process, no image needed — collection resolved via `EvaluateForActivity`, body scoped through `runTaskList` per iteration with `$<each>`/`$<at>` bound, optional `while` early-exit via `EvaluateWhileActivity`; shipped in `for-task` (new capability `workflow-iteration`) |
```

In the Phase 2 slice table (§4a), change the 2.3 row:

```diff
-| 2.3 | `for` — currently recognized, throws `UnsupportedOperationException`; reuses `runTaskList` for the loop body the same way `try` does | ❌ not started |
+| 2.3 | `for` — currently recognized, throws `UnsupportedOperationException`; reuses `runTaskList` for the loop body the same way `try` does | ✅ done — `openspec/changes/for-task`; no controller change was needed, and `DefinitionLookup` gained the `for.do` recursion branch |
```

In the dependency graph (§4's Mermaid or its Phase 2 slice list), no structural edge changes —
2.3 is now done, 2.4 is next. If the graph flags "next up" against 2.3, move that flag to 2.4.

Do NOT touch `openwiki/architecture/roadmap.md` — stale generated mirror.

- [ ] **Step 4: Commit**

```bash
git add docs/roadmaps/openworkflow-features.md
git commit -m "docs: mark for (Phase 2 slice 2.3) done in the roadmap"
```

---

## Self-Review Notes (for the implementer to re-verify, not to skip)

- **Spec coverage**: every requirement in `openspec/changes/for-task/specs/workflow-iteration/spec
  .md` maps to a task above — recognition (Task 4), body-once-per-element (Tasks 1 + 4), empty
  collection (Task 4 short-circuit), each/at bindings incl. defaults (Task 4), no leak past scope
  (Task 4's scope-local `HashMap`), `while` re-evaluation and stop (Task 4 + Task 2), `while`
  sees iteration variables (Task 2 test + Task 4 wiring), absent-`while` skips activity crossing
  (Task 4), forward-threaded data (Task 4), non-array `for.in` rejection (Task 1 test + Task 4
  propagation), name resolution across nested scopes (Task 3), composition with try/catch (Task
  5), controller no-op (Task 6's confirmation run; already true today, verified by reading
  `WorkflowCompiler.walk()` during design — no controller code task exists because none is
  needed).
- **Type consistency check**: `EvaluateForActivity.apply` returns `JsonNode`; the workflow method
  requests it back as `JsonNode.class`. `EvaluateWhileActivity.apply` returns `boolean`; the
  workflow method requests it back as `Boolean.class` (Dapr's activity API supports primitive
  wrapper types).
- **No placeholders**: every step above contains complete, compilable code — nothing marked TBD.
