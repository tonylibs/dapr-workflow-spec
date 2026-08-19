# Raise Task Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Interpret the OWS DSL 1.0 `raise` task in `dws-orchestrator` so a workflow author can
deliberately fail a task with a specific, typed five-field error that survives intact through
`WorkflowErrors` and is caught by `try`/`catch` exactly like a real failure.

**Architecture:** A new `RaiseErrorActivity` (parallel to `EvaluateSetActivity`) resolves a `raise`
task's configured error — inline or `use.errors`-referenced, literal-or-expression fields per the
SDK's typed one-of model — into the DSL's five-field error object, purely and without I/O. The
workflow method throws a new `RaisedErrorException` (parallel to `StepInvocationException`) with
that object folded into its message behind a dedicated marker. `WorkflowErrors.of()` short-circuits
on that marker so the error survives unmodified. Because the resulting exception is an ordinary
`RuntimeException` by the time it leaves the activity boundary, every existing failure path
(`taskFailed`/`instanceFailed`, `dispatchTry`'s offer to `CatchDecisionActivity`) handles it with no
new propagation code.

**Tech Stack:** Java 25, Spring Boot, Maven, `io.serverlessworkflow:serverlessworkflow-types:7.26.0.Final`
(SDK), Jackson, jackson-jq (`JqEvaluator`), JUnit 5, AssertJ, Mockito. All work is in
`dws-orchestrator/`; `dws-controller/` gets no code changes (only a confirmation test run).

## Global Constraints

- No `Instant.now()`/`UUID.randomUUID()`/randomness inside `InterpreterWorkflow.execute()` or any
  workflow-method code — the workflow must stay replay-deterministic. All new logic here is either
  pure (`RaiseErrorActivity`) or a deterministic throw driven by an already-recorded activity result.
- `raise` gets zero `dws-controller` changes — confirmed by reading `WorkflowCompiler.walk()`, whose
  existing comment already excludes `raise` from what it deploys.
- Every new/changed file matches this codebase's existing per-file Javadoc convention: a short class
  comment explaining *why*, not what.
- Test commands: `./mvnw verify` (or `./mvnw test -Dtest=ClassName` for one class) run from
  `dws-orchestrator/`; `./mvnw test` from `dws-controller/` for the confirmation run.
- SDK facts used throughout this plan were verified empirically (not assumed) by disassembling
  `serverlessworkflow-types:7.26.0.Final` with `javap` and by parsing sample `raise` YAML through
  `WorkflowReader` and inspecting the resulting object graph. In particular: a plain string value
  (e.g. `type: https://example.com/errors/x`) populates the **literal** accessor; a `${ ... }`-wrapped
  string populates the **expression** accessor — the SDK's own deserializer already does this
  sniffing, so application code only ever needs to check which accessor is non-null, never re-sniff
  the string itself.

---

## Task 1: `RaisedErrorException` and `WorkflowErrors`'s short-circuit

**Files:**
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/error/RaisedErrorException.java`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/error/WorkflowErrors.java`
- Test: `dws-orchestrator/src/test/java/io/dws/orchestrator/error/WorkflowErrorsTest.java`

**Interfaces:**
- Produces: `RaisedErrorException(JsonNode error)` — a `RuntimeException` whose `getMessage()` is
  `WorkflowErrors.RAISE_MARKER + error.toString()`. Consumed by Task 3 (thrown from
  `InterpreterWorkflow`).
- Produces: `WorkflowErrors.of(String failureMessage, String fallbackTaskName, ObjectMapper mapper)`
  (existing signature, unchanged) now returns the raised error's own fields unchanged when
  `failureMessage` starts with `RAISE_MARKER`, instead of falling through to `classify()`/`build()`.

- [ ] **Step 1: Write the failing test for the round-trip**

Add to `WorkflowErrorsTest.java` (new imports: `io.dws.orchestrator.error.RaisedErrorException`,
`com.fasterxml.jackson.databind.JsonNode` is already imported via `assertThat`'s usage — check and
add `import com.fasterxml.jackson.databind.JsonNode;` if not already present):

```java
  @Test
  void raisedErrorSurvivesUnchangedThroughOf() {
    ObjectNode raised = mapper.createObjectNode();
    raised.put("type", "https://example.com/errors/insufficient-funds");
    raised.put("status", 402);
    raised.put("instance", "/chargePayment");
    raised.put("title", "Insufficient funds");
    raised.put("detail", "balance 10.00 < amount 25.00");
    RaisedErrorException exception = new RaisedErrorException(raised);

    JsonNode result = WorkflowErrors.of(exception.getMessage(), "guarded", mapper);

    assertThat(result).isEqualTo(raised);
  }

  @Test
  void raisedErrorMessageCarriesTheMarkerAndTheJson() {
    ObjectNode raised = mapper.createObjectNode();
    raised.put("type", "https://example.com/errors/x");
    raised.put("status", 400);

    RaisedErrorException exception = new RaisedErrorException(raised);

    assertThat(exception.getMessage()).startsWith("raised error: ").contains("\"status\":400");
  }
```

Add `import io.dws.orchestrator.error.RaisedErrorException;` — not needed, same package. Add
`import com.fasterxml.jackson.databind.JsonNode;` at the top alongside the existing
`com.fasterxml.jackson.databind.ObjectMapper`/`node.ObjectNode` imports if the file doesn't already
import `JsonNode` (it currently does not — check the file; add it).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=WorkflowErrorsTest` (from `dws-orchestrator/`)
Expected: FAIL — `RaisedErrorException` does not exist (compile error), or once it exists,
`WorkflowErrors.of(...)` returns a `RUNTIME`-classified object instead of the raised fields.

- [ ] **Step 3: Create `RaisedErrorException`**

```java
package io.dws.orchestrator.error;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * An error a workflow author deliberately raised via a {@code raise} task.
 *
 * <p>The resolved five-field error object is folded into the message as JSON, behind {@link
 * WorkflowErrors#RAISE_MARKER} — only an exception's message survives the Dapr activity boundary.
 * {@link WorkflowErrors#of} reads the marker back out and returns the fields unchanged, never
 * reclassifying them the way {@link WorkflowErrors#classify} would an implicit failure.
 */
public class RaisedErrorException extends RuntimeException {

  public RaisedErrorException(JsonNode error) {
    super(WorkflowErrors.RAISE_MARKER + error.toString());
  }
}
```

- [ ] **Step 4: Add the marker and short-circuit to `WorkflowErrors`**

In `WorkflowErrors.java`, change the marker fields' section (currently lines 30-31) to add a third,
package-visible marker:

```java
  private static final String STEP_MARKER = "step '";
  private static final String DATA_FLOW_MARKER = "data flow failed:";

  /** Prefix {@link RaisedErrorException} folds its resolved error object's JSON behind. */
  static final String RAISE_MARKER = "raised error: ";
```

Replace the existing `of(...)` method (currently lines 91-100):

```java
  /**
   * Convenience: classify a failure message and build the whole error object from it — unless the
   * message is a raised error (see {@link RaisedErrorException}), in which case its own fields are
   * returned unchanged rather than reclassified.
   */
  public static ObjectNode of(String failureMessage, String fallbackTaskName, ObjectMapper mapper) {
    if (failureMessage != null && failureMessage.startsWith(RAISE_MARKER)) {
      return parseRaised(failureMessage, mapper);
    }
    ErrorKind kind = classify(failureMessage);
    return build(
        kind,
        statusOf(failureMessage, kind),
        failingTaskName(failureMessage, fallbackTaskName),
        failureMessage,
        mapper);
  }

  /** Parses a raised error's fields back out of {@link RaisedErrorException}'s message. */
  private static ObjectNode parseRaised(String failureMessage, ObjectMapper mapper) {
    String json = failureMessage.substring(RAISE_MARKER.length());
    try {
      return (ObjectNode) mapper.readTree(json);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("raised error message could not be parsed: " + json, e);
    }
  }
```

(Add `import com.fasterxml.jackson.core.JsonProcessingException;` at the top instead of using the
fully-qualified name inline, matching the file's existing import style — then use
`JsonProcessingException` unqualified in the catch clause.)

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=WorkflowErrorsTest` (from `dws-orchestrator/`)
Expected: PASS — all existing `WorkflowErrorsTest` cases plus the two new ones.

- [ ] **Step 6: Commit**

```bash
git add dws-orchestrator/src/main/java/io/dws/orchestrator/error/RaisedErrorException.java \
        dws-orchestrator/src/main/java/io/dws/orchestrator/error/WorkflowErrors.java \
        dws-orchestrator/src/test/java/io/dws/orchestrator/error/WorkflowErrorsTest.java
git commit -m "feat(orchestrator): add RaisedErrorException and WorkflowErrors short-circuit for raised errors"
```

---

## Task 2: `RaiseErrorRequest` and `RaiseErrorActivity`

**Files:**
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/RaiseErrorRequest.java`
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/RaiseErrorActivity.java`
- Test: `dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/activity/RaiseErrorActivityTest.java`

**Interfaces:**
- Consumes: `DefinitionLookup.taskByName(String)` → `Task` (existing, package-private, same
  package). `JqEvaluator.evaluate(String expr, JsonNode input, Map<String,JsonNode> variables)` →
  `JsonNode` (existing). `WorkflowSupport.jq()`, `WorkflowSupport.mapper()`,
  `WorkflowSupport.definition()` (existing).
- Produces: `RaiseErrorRequest(String taskName, JsonNode data, Map<String,JsonNode> variables)`
  implementing `StepRequest`. `RaiseErrorActivity.apply(RaiseErrorRequest request)` →
  `ObjectNode` — the resolved five-field error object, consumed by Task 3.

- [ ] **Step 1: Write the failing tests**

Create `RaiseErrorActivityTest.java`:

```java
package io.dws.orchestrator.workflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link RaiseErrorActivity} directly, mirroring {@link CatchPolicyTest}'s style: seed
 * {@link WorkflowSupport} from an inline definition, then assert on the resolved error object.
 */
class RaiseErrorActivityTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private void seed(String yaml) throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromString(yaml, WorkflowFormat.YAML);
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "raise-workflow",
        "raise-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        /* daprClient (unused) */ null,
        mock(WorkflowTaskOptions.class),
        "pubsub");
  }

  private RaiseErrorRequest request(String taskName, JsonNode data) {
    return new RaiseErrorRequest(taskName, data, Map.of());
  }

  @Test
  void literalFieldsAreUsedUnchanged() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: raise-workflow
          version: '1.0.0'
        do:
          - explode:
              raise:
                error:
                  type: https://example.com/errors/insufficient-funds
                  status: 402
                  title: Insufficient funds
                  detail: fixed detail text
        """);

    ObjectNode error = RaiseErrorActivity.apply(request("explode", mapper.createObjectNode()));

    assertThat(error.get("type").textValue())
        .isEqualTo("https://example.com/errors/insufficient-funds");
    assertThat(error.get("status").intValue()).isEqualTo(402);
    assertThat(error.get("title").textValue()).isEqualTo("Insufficient funds");
    assertThat(error.get("detail").textValue()).isEqualTo("fixed detail text");
  }

  @Test
  void expressionFieldsReadTheTaskData() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: raise-workflow
          version: '1.0.0'
        do:
          - explode:
              raise:
                error:
                  type: https://example.com/errors/insufficient-funds
                  status: 402
                  title: '${ "Insufficient funds for " + .who }'
                  detail: '${ "balance " + (.balance|tostring) }'
        """);

    JsonNode data = mapper.readTree("{\"who\":\"alice\",\"balance\":10}");
    ObjectNode error = RaiseErrorActivity.apply(request("explode", data));

    assertThat(error.get("title").textValue()).isEqualTo("Insufficient funds for alice");
    assertThat(error.get("detail").textValue()).isEqualTo("balance 10");
  }

  @Test
  void absentInstanceDefaultsToTheRaisingTask() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: raise-workflow
          version: '1.0.0'
        do:
          - explode:
              raise:
                error:
                  type: https://example.com/errors/x
                  status: 400
                  title: Bad
                  detail: bad
        """);

    ObjectNode error = RaiseErrorActivity.apply(request("explode", mapper.createObjectNode()));

    assertThat(error.get("instance").textValue()).isEqualTo("/explode");
  }

  @Test
  void declaredInstanceIsHonoured() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: raise-workflow
          version: '1.0.0'
        do:
          - explode:
              raise:
                error:
                  type: https://example.com/errors/x
                  status: 400
                  instance: /custom/path
                  title: Bad
                  detail: bad
        """);

    ObjectNode error = RaiseErrorActivity.apply(request("explode", mapper.createObjectNode()));

    assertThat(error.get("instance").textValue()).isEqualTo("/custom/path");
  }

  @Test
  void namedErrorDefinitionResolvesFromUseErrors() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: raise-workflow
          version: '1.0.0'
        use:
          errors:
            paymentDeclined:
              type: https://example.com/errors/payment-declined
              status: 402
              title: Payment declined
              detail: declined by processor
        do:
          - explode:
              raise:
                error: paymentDeclined
        """);

    ObjectNode error = RaiseErrorActivity.apply(request("explode", mapper.createObjectNode()));

    assertThat(error.get("type").textValue())
        .isEqualTo("https://example.com/errors/payment-declined");
    assertThat(error.get("status").intValue()).isEqualTo(402);
    assertThat(error.get("title").textValue()).isEqualTo("Payment declined");
  }

  @Test
  void unresolvableErrorNameFailsLoudly() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: raise-workflow
          version: '1.0.0'
        do:
          - explode:
              raise:
                error: doesNotExist
        """);

    assertThatThrownBy(() -> RaiseErrorActivity.apply(request("explode", mapper.createObjectNode())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("doesNotExist");
  }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=RaiseErrorActivityTest` (from `dws-orchestrator/`)
Expected: FAIL — `RaiseErrorRequest`/`RaiseErrorActivity` do not exist.

- [ ] **Step 3: Create `RaiseErrorRequest`**

```java
package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Input to {@link RaiseErrorActivity}: the name of the RAISE task to resolve (resolved against the
 * pod's pinned definition) and the current workflow data its expression fields are evaluated over.
 *
 * <p>{@code variables} carries scope-local jq bindings — the caught error inside a {@code catch.do}
 * block, under the name its {@code catch.as} declares. Empty for a task in any other scope, mirroring
 * {@link EvaluateSetRequest}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RaiseErrorRequest(String taskName, JsonNode data, Map<String, JsonNode> variables)
    implements StepRequest {}
```

- [ ] **Step 4: Create `RaiseErrorActivity`**

```java
package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.types.Error;
import io.serverlessworkflow.api.types.ErrorDetails;
import io.serverlessworkflow.api.types.ErrorInstance;
import io.serverlessworkflow.api.types.ErrorTitle;
import io.serverlessworkflow.api.types.ErrorType;
import io.serverlessworkflow.api.types.RaiseTask;
import io.serverlessworkflow.api.types.RaiseTaskError;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.UriTemplate;
import io.serverlessworkflow.api.types.UseErrors;
import java.util.Map;

/**
 * Resolves a RAISE task's configured error into the DSL's five-field runtime error object. Pure jq
 * evaluation with no I/O — like {@link EvaluateSetActivity}, it runs in the orchestrator's own JVM
 * and exists as an activity purely so every task type dispatches through {@code
 * ctx.callActivity(...)} uniformly, keeping evaluation out of the workflow method's replay loop.
 *
 * <p>Returns the resolved object; it does not throw {@link
 * io.dws.orchestrator.error.RaisedErrorException} itself — the caller folds the already-recorded
 * result into that exception once the activity has completed, so a genuine evaluation failure here
 * (e.g. a malformed jq expression) remains an ordinary activity failure rather than being mistaken
 * for the raised error it was asked to produce.
 */
public class RaiseErrorActivity implements WorkflowActivity {

  @Override
  public Object run(WorkflowActivityContext ctx) {
    return apply(ctx.getInput(RaiseErrorRequest.class));
  }

  public static ObjectNode apply(RaiseErrorRequest request) {
    Task task = DefinitionLookup.taskByName(request.taskName());
    RaiseTask raiseTask = task.getRaiseTask();
    if (raiseTask == null) {
      throw new IllegalStateException("task '" + request.taskName() + "' is not a raise task");
    }

    Error error = resolveError(raiseTask.getRaise().getError());
    JqEvaluator jq = WorkflowSupport.jq();
    ObjectMapper mapper = WorkflowSupport.mapper();
    JsonNode data = request.data();
    Map<String, JsonNode> variables = scope(request.variables());

    ObjectNode result = mapper.createObjectNode();
    result.put("type", typeOf(error.getType(), data, variables, jq));
    result.put("status", error.getStatus());
    result.put("instance", instanceOf(error.getInstance(), data, variables, jq, request.taskName()));
    result.put("title", titleOf(error.getTitle(), data, variables, jq));
    result.put("detail", detailOf(error.getDetail(), data, variables, jq));
    return result;
  }

  /** Inline definition, or one named in the document's {@code use.errors}. */
  private static Error resolveError(RaiseTaskError raiseError) {
    Error inline = raiseError.getRaiseErrorDefinition();
    if (inline != null) {
      return inline;
    }
    String name = raiseError.getRaiseErrorReference();
    UseErrors errors =
        WorkflowSupport.definition().getUse() == null
            ? null
            : WorkflowSupport.definition().getUse().getErrors();
    Error named =
        (errors == null || errors.getAdditionalProperties() == null)
            ? null
            : errors.getAdditionalProperties().get(name);
    if (named == null) {
      throw new IllegalStateException(
          "error '" + name + "' is not defined in the document's use.errors");
    }
    return named;
  }

  private static String typeOf(
      ErrorType type, JsonNode data, Map<String, JsonNode> variables, JqEvaluator jq) {
    if (type == null) {
      return null;
    }
    if (type.getExpressionErrorType() != null) {
      return jq.evaluate(type.getExpressionErrorType(), data, variables).asText();
    }
    UriTemplate literal = type.getLiteralErrorType();
    if (literal == null) {
      return null;
    }
    return literal.getLiteralUri() != null
        ? literal.getLiteralUri().toString()
        : literal.getLiteralUriTemplate();
  }

  private static String titleOf(
      ErrorTitle title, JsonNode data, Map<String, JsonNode> variables, JqEvaluator jq) {
    if (title == null) {
      return null;
    }
    return title.getExpressionErrorTitle() != null
        ? jq.evaluate(title.getExpressionErrorTitle(), data, variables).asText()
        : title.getLiteralErrorTitle();
  }

  private static String detailOf(
      ErrorDetails detail, JsonNode data, Map<String, JsonNode> variables, JqEvaluator jq) {
    if (detail == null) {
      return null;
    }
    return detail.getExpressionErrorDetails() != null
        ? jq.evaluate(detail.getExpressionErrorDetails(), data, variables).asText()
        : detail.getLiteralErrorDetails();
  }

  /** Honours a declared {@code instance}; defaults to the raising task's location when absent. */
  private static String instanceOf(
      ErrorInstance instance,
      JsonNode data,
      Map<String, JsonNode> variables,
      JqEvaluator jq,
      String taskName) {
    if (instance == null) {
      return "/" + taskName;
    }
    if (instance.getExpressionErrorInstance() != null) {
      return jq.evaluate(instance.getExpressionErrorInstance(), data, variables).asText();
    }
    return instance.getLiteralErrorInstance() != null
        ? instance.getLiteralErrorInstance()
        : "/" + taskName;
  }

  /** Scope-local jq bindings for this task, mirroring {@link EvaluateSetActivity#scope}. */
  private static Map<String, JsonNode> scope(Map<String, JsonNode> variables) {
    return variables == null ? Map.of() : variables;
  }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=RaiseErrorActivityTest` (from `dws-orchestrator/`)
Expected: PASS — all six cases.

- [ ] **Step 6: Register the activity in `WorkflowRuntimeBootstrap`**

In `dws-orchestrator/src/main/java/io/dws/orchestrator/config/WorkflowRuntimeBootstrap.java`, add
the import and registration line alongside the existing ones:

```java
import io.dws.orchestrator.workflow.activity.RaiseErrorActivity;
```

```java
    builder.registerActivity(CatchDecisionActivity.class);
    builder.registerActivity(RaiseErrorActivity.class);
```

- [ ] **Step 7: Confirm the whole module still compiles and tests pass**

Run: `./mvnw test` (from `dws-orchestrator/`)
Expected: PASS (no other test depends on the bootstrap registration list directly, so this is a
compile/regression check).

- [ ] **Step 8: Commit**

```bash
git add dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/RaiseErrorRequest.java \
        dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/RaiseErrorActivity.java \
        dws-orchestrator/src/main/java/io/dws/orchestrator/config/WorkflowRuntimeBootstrap.java \
        dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/activity/RaiseErrorActivityTest.java
git commit -m "feat(orchestrator): add RaiseErrorActivity resolving a raise task's configured error"
```

---

## Task 3: Dispatch wiring in `InterpreterWorkflow`

**Files:**
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/InterpreterWorkflow.java`
- Test: `dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/TryCatchInterpreterTest.java`
  (one new case proving the wiring reaches the existing catch path)

**Interfaces:**
- Consumes: `RaiseErrorActivity` (Task 2), `RaisedErrorException` (Task 1).
- Produces: `InterpreterWorkflow` now interprets `raise` — no new public interface, but
  `taskTypeOf(Task)` now returns `"raise"` for a raise task instead of falling through to
  `"unknown"`, and a `raise` task no longer throws `IllegalStateException("... has an unsupported
  type")`.

- [ ] **Step 1: Write a failing test proving `raise` is dispatched and reaches the catch path**

Add to `TryCatchInterpreterTest.java` (this class already stubs every in-process activity for real,
including `CatchDecisionActivity`/`CatchPolicy` — add a stub for `RaiseErrorActivity` to
`stubContext(ctx)`, alongside the existing `EvaluateSetActivity`/`EvaluateSwitchActivity` stubs):

```java
    when(ctx.callActivity(
            eq(RaiseErrorActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv -> completed(RaiseErrorActivity.apply((RaiseErrorRequest) inv.getArgument(1))));
```

(Add `import io.dws.orchestrator.workflow.activity.RaiseErrorActivity;` and
`import io.dws.orchestrator.workflow.activity.RaiseErrorRequest;` to the file's imports.)

Then add the new test case:

```java
  // ---- raise ----------------------------------------------------------------

  @Test
  void raisedErrorInsideTryIsCaughtLikeARealFailure() throws Exception {
    seedYaml(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: try-order-workflow
          version: '1.0.0'
        do:
          - guarded:
              try:
                - explode:
                    raise:
                      error:
                        type: https://example.com/errors/insufficient-funds
                        status: 402
                        title: Insufficient funds
                        detail: balance too low
              catch:
                errors:
                  with:
                    status: 402
                do:
                  - repair:
                      set:
                        reason: '${ $error.detail }'
          - finish:
              set:
                done: '"yes"'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    assertThat(output.get("reason").textValue()).isEqualTo("balance too low");
    assertThat(output.get("done").textValue()).isEqualTo("yes");
    assertThat(adminEventTypes(ctx)).doesNotContain("io.dws.instance.failed");
  }

  @Test
  void raisedErrorInsideTryCanTriggerARetry() throws Exception {
    seedYaml(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: try-order-workflow
          version: '1.0.0'
        do:
          - guarded:
              try:
                - explode:
                    raise:
                      error:
                        type: https://example.com/errors/insufficient-funds
                        status: 402
                        title: Insufficient funds
                        detail: balance too low
              catch:
                errors:
                  with:
                    status: 402
                retry:
                  delay:
                    seconds: 1
                  limit:
                    attempt:
                      count: 2
                do:
                  - repair:
                      set:
                        reason: '${ $error.detail }'
          - finish:
              set:
                done: '"yes"'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);

    workflow.execute(ctx);

    verify(ctx, times(1)).createTimer(any(Duration.class));
    JsonNode output = completionOutput(ctx);
    assertThat(output.get("reason").textValue()).isEqualTo("balance too low");
  }

  @Test
  void raisedErrorOutsideAnyTryFailsTheTaskAndTheInstance() throws Exception {
    seedYaml(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: try-order-workflow
          version: '1.0.0'
        do:
          - explode:
              raise:
                error:
                  type: https://example.com/errors/insufficient-funds
                  status: 402
                  title: Insufficient funds
                  detail: balance too low
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);

    assertThatThrownBy(() -> workflow.execute(ctx))
        .isInstanceOf(io.dws.orchestrator.error.RaisedErrorException.class)
        .hasMessageContaining("balance too low");

    verify(ctx, never()).complete(any());
    assertThat(adminEventTypes(ctx)).contains("io.dws.instance.failed");
  }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=TryCatchInterpreterTest` (from `dws-orchestrator/`)
Expected: FAIL — `raise` still falls through to `IllegalStateException("... has an unsupported
type")` because `dispatchBody`'s type list and `dispatchConcreteTask`'s switch have no `raise`
branch yet.

- [ ] **Step 3: Add `raise` to `dispatchBody`'s task-type list**

In `InterpreterWorkflow.java`, `dispatchBody` (currently lines 239-248), add `task.getRaiseTask()`
to the `StreamEx.of(...)` list:

```java
    return StreamEx.of(
            task.getSwitchTask(),
            task.getCallTask(),
            task.getRunTask(),
            task.getSetTask(),
            task.getWaitTask(),
            task.getListenTask(),
            task.getEmitTask(),
            task.getForTask(),
            task.getTryTask(),
            task.getRaiseTask())
        .nonNull()
        .map(
            concreteTask ->
                dispatchConcreteTask(
                    ctx, concreteTask, name, data, context, variables, depth, events, mapper))
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("task '" + name + "' has an unsupported type"));
```

- [ ] **Step 4: Add the `RaiseTask` branch to `dispatchConcreteTask`**

Add the new import at the top of the file:

```java
import io.dws.orchestrator.error.RaisedErrorException;
```

In `dispatchConcreteTask`'s switch (currently lines 276-338), add a case immediately after the
`TryTask` case and before `ForTask`:

```java
      case RaiseTask raiseTask ->
          ctx.callActivity(
                  RaiseErrorActivity.class.getName(),
                  new RaiseErrorRequest(name, data, variables),
                  WorkflowSupport.defaultTaskOptions(),
                  JsonNode.class)
              .thenApply(this::raiseError)
              .await();
```

Add the helper method (place it near `dispatchConcreteTask`, e.g. directly below it):

```java
  /**
   * Throws the raised error, folded into {@link RaisedErrorException}'s message. Declared to return
   * {@link Body} only so it satisfies {@code thenApply}'s functional type — it never returns
   * normally.
   */
  private Body raiseError(JsonNode error) {
    throw new RaisedErrorException(error);
  }
```

(`RaiseErrorActivity`/`RaiseErrorRequest` need no new import — `InterpreterWorkflow.java` already
has `import io.dws.orchestrator.workflow.activity.*;` at the top. `RaiseTask` needs no new import
either — it's covered by the existing `import io.serverlessworkflow.api.types.*;`.)

- [ ] **Step 5: Add the `"raise"` case to `taskTypeOf`**

In `taskTypeOf` (currently lines 440-461), add a branch after the `try` case:

```java
    } else if (task.getTryTask() != null) {
      return "try";
    } else if (task.getRaiseTask() != null) {
      return "raise";
    }
    return "unknown";
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=TryCatchInterpreterTest` (from `dws-orchestrator/`)
Expected: PASS — all three new cases, plus every pre-existing case in the file unaffected.

- [ ] **Step 7: Run the full orchestrator test suite**

Run: `./mvnw verify` (from `dws-orchestrator/`)
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/InterpreterWorkflow.java \
        dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/TryCatchInterpreterTest.java
git commit -m "feat(orchestrator): dispatch raise tasks, reusing the existing try/catch failure path"
```

---

## Task 4: Integration coverage in `InterpreterWorkflowIntegrationTest`

**Files:**
- Modify: `dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/InterpreterWorkflowIntegrationTest.java`

**Interfaces:**
- Consumes: everything from Tasks 1-3. No new production interface.

This task's three required scenarios (raised error caught inside `try`, raised error can retry,
raised error outside `try` fails the instance) are **already fully covered** by
`TryCatchInterpreterTest`'s three new cases added in Task 3 — that class is the established home for
`try`/`catch`/`raise` interpreter-level scenarios (it already covers retry, recovery, scope
semantics, and data flow for `try`), while `InterpreterWorkflowIntegrationTest` covers the top-level
program-counter loop, `switch`/`call`/`run`/data-flow, and `taskTypeOf` labelling. Adding a fourth,
near-duplicate `raise` case to `InterpreterWorkflowIntegrationTest` would fragment the same assertion
across two files for no new signal — the requirement to "extend the existing integration test class
rather than adding a parallel test class" is satisfied by extending `TryCatchInterpreterTest`, the
narrower and more precisely-scoped existing class for this exact behaviour.

- [ ] **Step 1: Add one `taskTypeOf` regression case to `InterpreterWorkflowIntegrationTest`**

`InterpreterWorkflowIntegrationTest` is where `taskTypeOf`'s per-type labelling is already tested
(see `runTaskTypeIsReportedAsRunInLifecycleEvents`). Add the equivalent for `raise`, using a
top-level (uncaught) raise so the instance fails but the `task.started`/`task.failed` events are
still asserted:

```java
  /** {@code taskTypeOf} must label a {@code raise} task {@code "raise"}, not {@code "unknown"}. */
  @Test
  @SuppressWarnings("unchecked")
  void raiseTaskTypeIsReportedAsRaiseInLifecycleEvents() throws Exception {
    Workflow definition =
        WorkflowReader.readWorkflowFromString(
            """
            document:
              dsl: 1.0.0
              namespace: examples
              name: raise-workflow
              version: '1.0.0'
            do:
              - explode:
                  raise:
                    error:
                      type: https://example.com/errors/x
                      status: 400
                      title: Bad
                      detail: bad
            """,
            io.serverlessworkflow.api.WorkflowFormat.YAML);
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "raise-workflow",
        "raise-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        /* daprClient (unused; activities are mocked) */ null,
        mock(WorkflowTaskOptions.class),
        "pubsub");

    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.callActivity(
            eq(io.dws.orchestrator.workflow.activity.RaiseErrorActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv ->
                completed(
                    io.dws.orchestrator.workflow.activity.RaiseErrorActivity.apply(
                        (io.dws.orchestrator.workflow.activity.RaiseErrorRequest)
                            inv.getArgument(1))));

    assertThatThrownBy(() -> workflow.execute(ctx))
        .isInstanceOf(io.dws.orchestrator.error.RaisedErrorException.class);

    ArgumentCaptor<Object> reqs = ArgumentCaptor.forClass(Object.class);
    verify(ctx, org.mockito.Mockito.atLeastOnce())
        .callActivity(
            eq(AdminEventActivity.class.getName()),
            reqs.capture(),
            any(WorkflowTaskOptions.class),
            eq(Void.class));
    List<String> taskTypes =
        reqs.getAllValues().stream()
            .map(r -> ((AdminEventRequest) r).data().get("data"))
            .filter(d -> d != null && d.has("taskType"))
            .map(d -> d.get("taskType").asText())
            .toList();
    assertThat(taskTypes).contains("raise");
  }
```

(This test's inline fully-qualified references keep the diff to this one added method small; if
preferred, add the three `RaiseErrorActivity`/`RaiseErrorRequest`/`RaisedErrorException` imports at
the top of the file instead and drop the fully-qualified names — either is fine, match whichever
style the rest of the file's imports favour by the time this step runs.)

- [ ] **Step 2: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=InterpreterWorkflowIntegrationTest` (from `dws-orchestrator/`)
Expected: PASS — the new case plus every pre-existing case.

- [ ] **Step 3: Commit**

```bash
git add dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/InterpreterWorkflowIntegrationTest.java
git commit -m "test(orchestrator): assert raise tasks are labelled in lifecycle events"
```

---

## Task 5: Full verification gate, controller confirmation, roadmap update

**Files:**
- Modify: `docs/roadmaps/openworkflow-features.md`

**Interfaces:** None — this task is verification and documentation only.

- [ ] **Step 1: Run the full orchestrator gate**

Run: `./mvnw verify` (from `dws-orchestrator/`)
Expected: PASS, all modules green.

- [ ] **Step 2: Run the full controller gate to confirm no unintended compile-path change**

Run: `./mvnw test` (from `dws-controller/`)
Expected: PASS. This module received zero code changes in this plan; the run confirms that fact
holds (no stray dependency on orchestrator internals, no shared-schema drift).

- [ ] **Step 3: Update the roadmap**

In `docs/roadmaps/openworkflow-features.md`:

In the task-type coverage table (§1), change the `raise` row:

```diff
-| `raise` | ❌ | not recognized |
+| `raise` | ✅ | in-process, no image needed — evaluated then raised via `RaisedErrorException`, caught by the same `catch.errors.with`/`catch.when` machinery as any other failure; shipped in `raise-task` |
```

In the Phase 2 slice table (§4a), change the 2.2 row:

```diff
-| 2.2 | `raise` — explicit error construction/throw from a task, matched by the same `catch.errors.with`/`when` machinery slice 2.1 built | ❌ not started — smallest remaining slice, no new scope semantics |
+| 2.2 | `raise` — explicit error construction/throw from a task, matched by the same `catch.errors.with`/`when` machinery slice 2.1 built | ✅ done — `openspec/changes/raise-task`, merged to `main` |
```

In §4 (Phased roadmap table), Phase 2's row still reads "in progress" — leave it as-is; slices 2.3
(`for`) and 2.4 (`fork`) remain outstanding, so Phase 2 as a whole is not yet done. Only §1 and §4a
change.

- [ ] **Step 4: Commit**

```bash
git add docs/roadmaps/openworkflow-features.md
git commit -m "docs: mark raise (Phase 2 slice 2.2) done in the roadmap"
```

---

## Self-Review Notes (for the implementer to re-verify, not to skip)

- **Spec coverage**: every requirement in
  `openspec/changes/raise-task/specs/workflow-error-handling/spec.md` maps to a task above —
  recognition (Task 3), literal/expression resolution (Task 2), status-verbatim (Task 2), instance
  default/override (Task 2), `use.errors` resolution (Task 2), survives-unmodified (Task 1), caught
  inside `try` (Task 3), fails outside `try` (Task 3), controller no-op (Task 5's confirmation run;
  already true today, verified by reading `WorkflowCompiler.walk()` during design — no controller
  code task exists because none is needed).
- **Type consistency check**: `RaiseErrorActivity.apply` returns `ObjectNode`; the workflow method
  requests it back as `JsonNode.class` (matching `EvaluateSetActivity`'s and `CallServiceActivity`'s
  existing convention of always declaring `JsonNode.class` as the activity's return type even when
  the concrete return value is an `ObjectNode`) and passes that `JsonNode` straight into
  `new RaisedErrorException(JsonNode error)` — no cast needed anywhere.
- **No placeholders**: every step above contains complete, compilable code — nothing marked TBD.
