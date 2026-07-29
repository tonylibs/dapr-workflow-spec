# `try`/`catch`/`retry` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Interpret the OWS DSL 1.0 `try`/`catch`/`retry` task in `dws-orchestrator`, and compile the
tasks nested inside it in `dws-controller`, so a workflow can retry a failing block with backoff and
recover instead of failing the instance.

**Architecture:** The interpreter's program-counter loop is extracted into a scope-aware
`runTaskList` used by the top-level `do`, the `try` list, and the `catch.do` list — each scope gets
its own name index, so flow directives stay inside their scope. A `try` failure is handed to one
in-process `CatchDecisionActivity` that synthesises a five-field error object, applies the static and
dynamic filters, resolves the retry policy (inline or from `use.retries`), enforces limits and
computes the delay including jitter; the workflow method only branches on that verdict and awaits a
durable timer between attempts. `dws-controller` recurses into `try`/`catch.do` when walking tasks so
nested `call`/`run` get their step services, and rejects duplicate task names.

**Tech Stack:** Java 25 (Spring Boot 4.1 / Quarkus), Maven, Dapr Workflows SDK 1.18, Open Workflow
Specification SDK `serverlessworkflow-api` 7.26.0.Final, jackson-jq 1.2.0, JUnit 5 + AssertJ +
Mockito.

## Global Constraints

- Read `openspec/changes/try-catch-retry/design.md` before starting — every decision below is
  labelled with its `D<n>` there.
- `InterpreterWorkflow.execute()` and everything it calls directly must stay **replay-deterministic**:
  no `Instant.now()`, no `Math.random()`, no `UUID.randomUUID()`. Clock values come from
  `ctx.getCurrentInstant()`; randomness happens inside an activity.
- Only an exception's **message** survives the Dapr activity boundary. Any detail a later step needs
  must be in the message string.
- jq is the only expression language — reuse `io.dws.orchestrator.expr.JqEvaluator`.
- No new dependency in either component. No new deployed resource kind, no Dapr component change.
- A `call`/`run` task's Dapr app-id is `TaskNaming.toKebabCase(taskName)` — unchanged, and now
  applied to nested tasks too.
- Formatting is enforced by spotless (google-java-format). Run `./mvnw spotless:apply` before
  committing if a build fails on formatting.
- Only a JDK 21 is installed in this environment; run Maven with `-Djava.version=21`. CI still builds
  against the pinned Java 25 — do **not** commit a change to the `java.version` property.
- Commit after every task, using the message given in that task's final step.

---

## File Structure

**`dws-controller`**
- Modify: `src/main/java/io/dws/controller/compile/WorkflowCompiler.java` — recurse the task walk;
  add definition-wide name-uniqueness validation.
- Test: `src/test/java/io/dws/controller/compile/WorkflowCompilerTest.java`

**`dws-orchestrator`**
- Create: `src/main/java/io/dws/orchestrator/error/ErrorKind.java` — the three failure classes with
  their type URI and default status.
- Create: `src/main/java/io/dws/orchestrator/error/StepInvocationException.java` — a step-service
  failure carrying its app-id and HTTP status **in the message**, so the class survives as text.
- Create: `src/main/java/io/dws/orchestrator/error/WorkflowErrors.java` — classify a failure message
  into an `ErrorKind` + status, and build the five-field error `ObjectNode`.
- Create: `src/main/java/io/dws/orchestrator/workflow/ScopeEnd.java`,
  `src/main/java/io/dws/orchestrator/workflow/ScopeResult.java` — how a task scope ended.
- Create: `src/main/java/io/dws/orchestrator/workflow/activity/CatchDecisionRequest.java`,
  `CatchDecision.java`, `CatchPolicy.java`, `CatchDecisionActivity.java` — the whole catch verdict.
- Modify: `src/main/java/io/dws/orchestrator/workflow/InterpreterWorkflow.java` — scope-aware runner,
  `try` dispatch, retry loop.
- Modify: `src/main/java/io/dws/orchestrator/workflow/activity/DefinitionLookup.java` — recursive
  lookup.
- Modify: `DataFlowInputRequest`, `DataFlowOutputRequest`, `EvaluateSetRequest`,
  `EvaluateSwitchRequest`, `DataFlowPipeline`, `EvaluateSetActivity`, `EvaluateSwitchActivity` — carry
  scope variables so nested expressions can read `$error`.
- Modify: `src/main/java/io/dws/orchestrator/workflow/activity/CallServiceActivity.java` — wrap
  invocation failures in `StepInvocationException`.
- Modify: `src/main/java/io/dws/orchestrator/config/WorkflowRuntimeBootstrap.java` — register
  `CatchDecisionActivity`.
- Create: `src/test/resources/try-order.yaml` — a fixture definition exercising `try`/`catch`/`retry`.
- Create: `src/test/java/io/dws/orchestrator/error/WorkflowErrorsTest.java`,
  `src/test/java/io/dws/orchestrator/workflow/activity/CatchPolicyTest.java`.
- Modify: `src/test/java/io/dws/orchestrator/workflow/InterpreterWorkflowIntegrationTest.java`.

---

## Task 1: Controller — recurse into nested task lists, reject duplicate names

**Files:**
- Modify: `dws-controller/src/main/java/io/dws/controller/compile/WorkflowCompiler.java`
- Test: `dws-controller/src/test/java/io/dws/controller/compile/WorkflowCompilerTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: nothing consumed by later tasks — this task is independently shippable. It establishes
  the invariant Task 3 relies on at runtime (task names unique across the definition).

- [ ] **Step 1: Write the failing tests**

Add to `WorkflowCompilerTest`. Build the definitions the same way the existing tests in this class do
(read a YAML fixture string through the SDK reader the class already uses) — do not invent a new
fixture mechanism.

```java
@Test
void callTaskInsideTryCompilesToAStepService() {
  Workflow definition = read("""
      document: {dsl: '1.0.0', namespace: test, name: try-compile, version: '0.1.0'}
      do:
        - guarded:
            try:
              - fetchOrder:
                  call: http
                  with: {method: get, endpoint: https://example.test/orders}
            catch:
              errors: {with: {type: 'https://open-workflow-specification.org/dsl/errors/types/communication'}}
              do:
                - notifyFailure:
                    run:
                      shell: {command: 'echo failed'}
      """);

  CompiledWorkflow compiled = compiler.compile(definition);

  assertThat(compiled.steps()).extracting(StepService::name)
      .containsExactlyInAnyOrder("fetch-order", "notify-failure");
  assertThat(compiled.steps()).filteredOn(s -> s.name().equals("fetch-order"))
      .singleElement().extracting(StepService::kind).isEqualTo(TaskKind.CALL_HTTP);
  assertThat(compiled.steps()).filteredOn(s -> s.name().equals("notify-failure"))
      .singleElement().extracting(StepService::kind).isEqualTo(TaskKind.RUN_SHELL);
}

@Test
void duplicateTaskNamesAcrossDepthsAreRejected() {
  Workflow definition = read("""
      document: {dsl: '1.0.0', namespace: test, name: dup, version: '0.1.0'}
      do:
        - fetchOrder:
            call: http
            with: {method: get, endpoint: https://example.test/a}
        - guarded:
            try:
              - fetchOrder:
                  call: http
                  with: {method: get, endpoint: https://example.test/b}
            catch: {}
      """);

  assertThatThrownBy(() -> compiler.compile(definition))
      .isInstanceOf(CompilationException.class)
      .hasMessageContaining("fetchOrder");
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd dws-controller && ./mvnw test -Djava.version=21 -Dtest=WorkflowCompilerTest`
Expected: FAIL — `fetch-order`/`notify-failure` missing from `steps()`, and no exception for the
duplicate.

- [ ] **Step 3: Recurse the walk**

In `WorkflowCompiler.walk(...)`, add a `try` branch at the end of the existing chain and fix the
stale comment:

```java
      } else if (task.getListenTask() != null) {
        bindings.add(new TopicBinding(taskName, TopicBinding.Direction.LISTEN, taskName));
      } else if (task.getTryTask() != null) {
        TryTask tryTask = task.getTryTask();
        if (tryTask.getTry() != null) {
          walk(tryTask.getTry(), steps, bindings);
        }
        if (tryTask.getCatch() != null && tryTask.getCatch().getDo() != null) {
          walk(tryTask.getCatch().getDo(), steps, bindings);
        }
      }
      // switch/set/wait/raise (and for/fork task lists) deploy nothing and are not walked.
```

Import `io.serverlessworkflow.api.types.TryTask`.

- [ ] **Step 4: Add the uniqueness validation**

Add a collector and call it from the same place the existing structural validation runs (the method
that builds the `errors` list before `walk(workflow.getDo(), ...)`):

```java
  /**
   * Task names must be unique across the whole definition: a call/run task's Dapr app-id — and so
   * its deployed Knative Service name — is derived from the task name alone, and the orchestrator
   * resolves tasks by name at any depth.
   */
  private void collectDuplicateNames(List<TaskItem> tasks, Set<String> seen, Set<String> duplicates) {
    if (tasks == null) {
      return;
    }
    for (TaskItem item : tasks) {
      if (!seen.add(item.getName())) {
        duplicates.add(item.getName());
      }
      TryTask tryTask = item.getTask().getTryTask();
      if (tryTask != null) {
        collectDuplicateNames(tryTask.getTry(), seen, duplicates);
        if (tryTask.getCatch() != null) {
          collectDuplicateNames(tryTask.getCatch().getDo(), seen, duplicates);
        }
      }
    }
  }
```

In the validation method, after the existing `getDo()` empty check:

```java
    Set<String> duplicates = new LinkedHashSet<>();
    collectDuplicateNames(workflow.getDo(), new HashSet<>(), duplicates);
    for (String duplicate : duplicates) {
      errors.add("Duplicate task name '" + duplicate + "': task names must be unique across the "
          + "whole definition, including nested try/catch lists");
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd dws-controller && ./mvnw test -Djava.version=21 -Dtest=WorkflowCompilerTest`
Expected: PASS, including every pre-existing test in the class.

- [ ] **Step 6: Add the remaining coverage**

Add three more tests to the same class: an `emit` inside `try` produces a `TopicBinding` of direction
`EMIT`; a `listen` inside `catch.do` produces one of direction `LISTEN`; and a definition with no
`try` task compiles to exactly the same step/binding set as before (assert against an explicit
expected list, not a snapshot).

- [ ] **Step 7: Run the full controller gate**

Run: `cd dws-controller && ./mvnw test -Djava.version=21`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add dws-controller/src/main/java/io/dws/controller/compile/WorkflowCompiler.java \
        dws-controller/src/test/java/io/dws/controller/compile/WorkflowCompilerTest.java
git commit -m "feat(controller): compile tasks nested in try/catch and reject duplicate task names"
```

---

## Task 2: Orchestrator — scope-aware task-list runner

**Files:**
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/ScopeEnd.java`
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/ScopeResult.java`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/InterpreterWorkflow.java`
- Test: `dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/InterpreterWorkflowIntegrationTest.java`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces:
  - `enum ScopeEnd { FELL_THROUGH, EXIT, END }`
  - `record ScopeResult(JsonNode data, JsonNode context, ScopeEnd end)`
  - `private ScopeResult runTaskList(WorkflowContext ctx, List<TaskItem> items, JsonNode data,
    JsonNode context, Map<String, JsonNode> variables, int depth, AdminEventBuilder events)`
  - `private static final int MAX_DEPTH = 16;`

- [ ] **Step 1: Write the failing test**

This is a pure refactor, so the test asserts the *new* capability it unlocks. Add to
`InterpreterWorkflowIntegrationTest` — it will stay red until Task 5 wires `try`, so mark it
`@Disabled("enabled in Task 5")` **only** if you are running tasks strictly in order; otherwise
write it now and let it drive Task 5.

```java
@Test
void existingDefinitionRunsUnchangedThroughTheScopeRunner() {
  WorkflowContext ctx = mock(WorkflowContext.class);
  stubContext(ctx);
  when(ctx.getInput(JsonNode.class)).thenReturn(mapper.createObjectNode().put("inStock", true));
  stubCall(ctx);

  workflow.execute(ctx);

  ArgumentCaptor<JsonNode> completed = ArgumentCaptor.forClass(JsonNode.class);
  verify(ctx).complete(completed.capture());
  assertThat(completed.getValue()).isNotNull();
}
```

- [ ] **Step 2: Run it to confirm the existing behaviour is green before refactoring**

Run: `cd dws-orchestrator && ./mvnw test -Djava.version=21 -Dtest=InterpreterWorkflowIntegrationTest`
Expected: PASS. This is the regression baseline for the refactor — if it is red now, fix that first.

- [ ] **Step 3: Add the scope result types**

`ScopeEnd.java`:

```java
package io.dws.orchestrator.workflow;

/** How a task scope finished: ran off its end, exited itself, or ended the whole instance. */
public enum ScopeEnd {
  /** The list ran to completion with no terminating directive. */
  FELL_THROUGH,
  /** {@code exit}: complete this scope only; an enclosing task continues. */
  EXIT,
  /** {@code end}: complete the whole workflow instance from any depth. */
  END
}
```

`ScopeResult.java`:

```java
package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.JsonNode;

/** The data and context a task scope produced, plus how it finished. */
public record ScopeResult(JsonNode data, JsonNode context, ScopeEnd end) {}
```

- [ ] **Step 4: Extract the loop**

In `InterpreterWorkflow`, move the body of the `try { int pc = 0; … }` block into a new method,
parameterised by the list. Key changes from the old loop: `indexByName` is built from `items`
(the parameter), `COMPLETE` splits into `EXIT`/`END`, and the method returns rather than calling
`ctx.complete(...)`.

```java
  /** Guard against pathologically nested definitions blowing the call stack. */
  private static final int MAX_DEPTH = 16;

  /**
   * Runs one task list as its own scope. Flow-directive targets resolve only against {@code items},
   * which is the DSL's own rule that a directive "may only redirect to tasks declared within their
   * own scope". Called for the top-level {@code do} and for a try task's {@code try}/{@code catch.do}.
   */
  private ScopeResult runTaskList(
      WorkflowContext ctx,
      List<TaskItem> items,
      JsonNode data,
      JsonNode context,
      Map<String, JsonNode> variables,
      int depth,
      AdminEventBuilder events) {
    if (depth > MAX_DEPTH) {
      throw new IllegalStateException(
          "workflow exceeded the maximum task nesting depth of " + MAX_DEPTH);
    }

    Map<String, Integer> indexByName = new HashMap<>();
    for (int i = 0; i < items.size(); i++) {
      indexByName.put(items.get(i).getName(), i);
    }

    int pc = 0;
    for (int steps = 0; pc >= 0 && pc < items.size(); steps++) {
      if (steps > MAX_STEPS) {
        throw new IllegalStateException(
            "workflow exceeded " + MAX_STEPS + " steps; check for a definition loop");
      }

      TaskItem item = items.get(pc);
      String name = item.getName();
      Task task = item.getTask();
      String taskType = taskTypeOf(task);

      publish(ctx, events.taskStarted(name, taskType));
      FlowOutcome then;
      try {
        Dispatch result = dispatch(ctx, task, name, data, context, variables, depth, events);
        data = result.data();
        context = result.context();
        then = result.then();
      } catch (RuntimeException e) {
        publish(ctx, events.taskFailed(name, taskType, String.valueOf(e.getMessage())));
        throw e;
      }
      publish(ctx, events.taskCompleted(name, taskType));

      FlowDirectiveEnum keyword = then == null ? null : then.directive();
      if (keyword == FlowDirectiveEnum.END) {
        return new ScopeResult(data, context, ScopeEnd.END);
      }
      if (keyword == FlowDirectiveEnum.EXIT) {
        return new ScopeResult(data, context, ScopeEnd.EXIT);
      }
      pc = advance(then, pc, indexByName);
    }
    return new ScopeResult(data, context, ScopeEnd.FELL_THROUGH);
  }
```

Simplify `advance` accordingly — it no longer returns `COMPLETE`, so delete the `COMPLETE` constant
and the `END, EXIT -> COMPLETE` arm, leaving `CONTINUE -> pc + 1` plus the target lookup. Its
"references undefined task" `IllegalStateException` is now the scope-violation error and should read:

```java
        throw new IllegalStateException(
            "flow references task '" + target + "', which is not declared in this task scope");
```

- [ ] **Step 5: Rewrite `execute()` on top of the runner**

```java
    try {
      ScopeResult result =
          runTaskList(
              ctx,
              items,
              data,
              context,
              Map.of(),
              0,
              events);
      publish(ctx, events.instanceCompleted());
      ctx.complete(result.data());
    } catch (RuntimeException e) {
      publish(ctx, events.instanceFailed(String.valueOf(e.getMessage())));
      throw e;
    }
```

Both `END` and `FELL_THROUGH` complete the instance at top level; `EXIT` does too, because exiting
the outermost scope *is* completing. No branch on `end` is needed here.

- [ ] **Step 6: Thread `variables`, `depth`, and `events` through `dispatch`**

Widen `dispatch(...)` and `dispatchBody(...)` to take `Map<String, JsonNode> variables, int depth,
AdminEventBuilder events` and pass them along. For now `variables` is only forwarded (Task 4 gives it
content); `depth`/`events` are only needed by the `try` branch added in Task 5. Do not change any
existing branch's behaviour.

- [ ] **Step 7: Note the deferred depth test**

The depth guard cannot be exercised yet: building a nested definition requires `try` to be
interpreted, which lands in Task 5. Do **not** write a reflective or stubbed test for it here — it
is covered by Task 6 Step 6, which generates a definition nesting `try` past `MAX_DEPTH` and asserts
the message. Record the deferral in this task's commit message (Step 9 already does) so it is not
read as missing coverage.

- [ ] **Step 8: Run the orchestrator gate**

Run: `cd dws-orchestrator && ./mvnw verify -Djava.version=21`
Expected: BUILD SUCCESS, every existing test still passing — this step is a pure refactor.

- [ ] **Step 9: Commit**

```bash
git add dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/
git commit -m "refactor(orchestrator): run task lists as scopes with their own flow-directive index

exit now completes only the current scope and end completes the instance; at top
level both still complete the instance, so behaviour is unchanged. The nesting-depth
guard is covered by the nested integration cases added with try interpretation."
```

---

## Task 3: Orchestrator — recursive task lookup and the runtime error object

**Files:**
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/DefinitionLookup.java`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/CallServiceActivity.java`
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/error/ErrorKind.java`
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/error/StepInvocationException.java`
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/error/WorkflowErrors.java`
- Test: `dws-orchestrator/src/test/java/io/dws/orchestrator/error/WorkflowErrorsTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum ErrorKind { VALIDATION, COMMUNICATION, RUNTIME }` with `String typeUri()` and
    `int defaultStatus()`.
  - `class StepInvocationException extends RuntimeException` with `String appId()`, `int status()`.
  - `WorkflowErrors.classify(String failureMessage)` → `ErrorKind`
  - `WorkflowErrors.statusOf(String failureMessage, ErrorKind kind)` → `int`
  - `WorkflowErrors.build(ErrorKind kind, int status, String instance, String detail, ObjectMapper
    mapper)` → `ObjectNode` with fields `type`, `status`, `instance`, `title`, `detail`.
  - `DefinitionLookup.taskByName(String)` now searches nested `try`/`catch.do` lists.

- [ ] **Step 1: Write the failing tests**

`WorkflowErrorsTest.java`:

```java
package io.dws.orchestrator.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class WorkflowErrorsTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void dataFlowFailureIsAValidationError() {
    String message = "task 'chargePayment' output data flow failed: /total: must be a number";
    assertThat(WorkflowErrors.classify(message)).isEqualTo(ErrorKind.VALIDATION);
    assertThat(WorkflowErrors.statusOf(message, ErrorKind.VALIDATION)).isEqualTo(400);
  }

  @Test
  void stepFailureIsACommunicationErrorCarryingItsStatus() {
    String message = "step 'fetch-order' failed with status 503: upstream unavailable";
    assertThat(WorkflowErrors.classify(message)).isEqualTo(ErrorKind.COMMUNICATION);
    assertThat(WorkflowErrors.statusOf(message, ErrorKind.COMMUNICATION)).isEqualTo(503);
  }

  @Test
  void stepFailureWithoutARecoverableStatusDefaultsTo502() {
    String message = "step 'fetch-order' failed: connection reset";
    assertThat(WorkflowErrors.statusOf(message, ErrorKind.COMMUNICATION)).isEqualTo(502);
  }

  @Test
  void anythingElseIsARuntimeError() {
    String message = "task 'x' has an unsupported type";
    assertThat(WorkflowErrors.classify(message)).isEqualTo(ErrorKind.RUNTIME);
    assertThat(WorkflowErrors.statusOf(message, ErrorKind.RUNTIME)).isEqualTo(500);
  }

  @Test
  void buildProducesTheFiveDslFields() {
    ObjectNode error =
        WorkflowErrors.build(
            ErrorKind.COMMUNICATION, 503, "/do/0/guarded/try/0/fetchOrder", "upstream down", mapper);

    assertThat(error.get("type").asText())
        .isEqualTo("https://open-workflow-specification.org/dsl/errors/types/communication");
    assertThat(error.get("status").asInt()).isEqualTo(503);
    assertThat(error.get("instance").asText()).isEqualTo("/do/0/guarded/try/0/fetchOrder");
    assertThat(error.get("title").asText()).isEqualTo("Communication error");
    assertThat(error.get("detail").asText()).isEqualTo("upstream down");
  }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd dws-orchestrator && ./mvnw test -Djava.version=21 -Dtest=WorkflowErrorsTest`
Expected: FAIL — `io.dws.orchestrator.error` does not exist.

- [ ] **Step 3: Implement `ErrorKind`**

```java
package io.dws.orchestrator.error;

/**
 * The three failure classes this phase distinguishes, each with the Open Workflow Specification
 * error type URI it maps to and the status used when no upstream status is recoverable.
 *
 * <p>The full standard error-type catalogue and RFC 7807 Problem Details formatting are Phase 3;
 * these three are the minimum that makes {@code catch.errors.with} meaningful.
 */
public enum ErrorKind {
  VALIDATION("validation", 400, "Validation error"),
  COMMUNICATION("communication", 502, "Communication error"),
  RUNTIME("runtime", 500, "Runtime error");

  private static final String TYPE_PREFIX =
      "https://open-workflow-specification.org/dsl/errors/types/";

  private final String slug;
  private final int defaultStatus;
  private final String title;

  ErrorKind(String slug, int defaultStatus, String title) {
    this.slug = slug;
    this.defaultStatus = defaultStatus;
    this.title = title;
  }

  public String typeUri() {
    return TYPE_PREFIX + slug;
  }

  public int defaultStatus() {
    return defaultStatus;
  }

  public String title() {
    return title;
  }
}
```

- [ ] **Step 4: Implement `StepInvocationException`**

```java
package io.dws.orchestrator.error;

/**
 * A step-service invocation that failed. The app-id and HTTP status are folded into the message
 * because only an exception's message survives the Dapr activity boundary — {@link WorkflowErrors}
 * classifies the failure by reading that message back.
 */
public class StepInvocationException extends RuntimeException {

  private final String appId;
  private final int status;

  public StepInvocationException(String appId, int status, String detail, Throwable cause) {
    super(
        status > 0
            ? "step '" + appId + "' failed with status " + status + ": " + detail
            : "step '" + appId + "' failed: " + detail,
        cause);
    this.appId = appId;
    this.status = status;
  }

  public String appId() {
    return appId;
  }

  /** The upstream HTTP status, or 0 when none could be recovered. */
  public int status() {
    return status;
  }
}
```

- [ ] **Step 5: Implement `WorkflowErrors`**

```java
package io.dws.orchestrator.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the minimal runtime error object {@code {type, status, instance, title, detail}} that
 * {@code catch.errors.with} filters against.
 *
 * <p>Classification reads the failure <em>message</em> rather than the exception type: a failure
 * raised inside an activity reaches the workflow as an opaque activity failure whose message is the
 * only surviving detail. {@link StepInvocationException} and {@code DataFlowException} both write a
 * stable marker into their message for exactly this reason.
 */
public final class WorkflowErrors {

  private static final Pattern STEP_STATUS =
      Pattern.compile("step '[^']+' failed with status (\\d{3})");

  private WorkflowErrors() {}

  public static ErrorKind classify(String failureMessage) {
    String message = failureMessage == null ? "" : failureMessage;
    if (message.contains("data flow failed:")) {
      return ErrorKind.VALIDATION;
    }
    if (message.startsWith("step '")) {
      return ErrorKind.COMMUNICATION;
    }
    return ErrorKind.RUNTIME;
  }

  public static int statusOf(String failureMessage, ErrorKind kind) {
    if (kind == ErrorKind.COMMUNICATION && failureMessage != null) {
      Matcher matcher = STEP_STATUS.matcher(failureMessage);
      if (matcher.find()) {
        return Integer.parseInt(matcher.group(1));
      }
    }
    return kind.defaultStatus();
  }

  public static ObjectNode build(
      ErrorKind kind, int status, String instance, String detail, ObjectMapper mapper) {
    ObjectNode error = mapper.createObjectNode();
    error.put("type", kind.typeUri());
    error.put("status", status);
    error.put("instance", instance);
    error.put("title", kind.title());
    error.put("detail", detail == null ? "" : detail);
    return error;
  }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd dws-orchestrator && ./mvnw test -Djava.version=21 -Dtest=WorkflowErrorsTest`
Expected: PASS (5 tests).

- [ ] **Step 7: Wrap step-invocation failures**

In `CallServiceActivity.run(...)`, wrap the `invokeMethod(...).block()` call:

```java
    try {
      JsonNode response =
          client
              .invokeMethod(
                  request.appId(), request.path(), request.data(), HttpExtension.POST, JsonNode.class)
              .block();
      // A 204/empty response leaves the data document unchanged.
      return response == null ? request.data() : response;
    } catch (RuntimeException e) {
      throw new StepInvocationException(
          request.appId(), statusOf(e), String.valueOf(e.getMessage()), e);
    }
```

Add a small private `statusOf(RuntimeException)` that returns `((DaprException) e).getHttpStatusCode()`
when the throwable (or a cause in its chain) is a `DaprException` exposing one, and `0` otherwise.
Verify the accessor name against the Dapr SDK on the classpath before writing it — if no HTTP status
is exposed, return `0` and let `WorkflowErrors` default to 502.

- [ ] **Step 8: Make `DefinitionLookup` recursive**

```java
  static Task taskByName(String taskName) {
    Task found = search(WorkflowSupport.definition().getDo(), taskName);
    if (found == null) {
      throw new IllegalStateException("definition has no task named '" + taskName + "'");
    }
    return found;
  }

  /**
   * Depth-first search over the definition, descending into a try task's {@code try} and {@code
   * catch.do} lists. Task names are unique across the whole definition (enforced at compile time),
   * so the first match is the only match.
   */
  private static Task search(List<TaskItem> items, String taskName) {
    if (items == null) {
      return null;
    }
    for (TaskItem item : items) {
      if (item.getName().equals(taskName)) {
        return item.getTask();
      }
      TryTask tryTask = item.getTask().getTryTask();
      if (tryTask != null) {
        Task nested = search(tryTask.getTry(), taskName);
        if (nested == null && tryTask.getCatch() != null) {
          nested = search(tryTask.getCatch().getDo(), taskName);
        }
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
  }
```

- [ ] **Step 9: Run the gate**

Run: `cd dws-orchestrator && ./mvnw verify -Djava.version=21`
Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
git add dws-orchestrator/src/main/java/io/dws/orchestrator/error/ \
        dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/DefinitionLookup.java \
        dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/CallServiceActivity.java \
        dws-orchestrator/src/test/java/io/dws/orchestrator/error/
git commit -m "feat(orchestrator): add the runtime error object and resolve tasks at any depth"
```

---

## Task 4: Orchestrator — the catch decision (filter, policy, backoff, jitter)

**Files:**
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/CatchDecisionRequest.java`
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/CatchDecision.java`
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/CatchPolicy.java`
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/CatchDecisionActivity.java`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/config/WorkflowRuntimeBootstrap.java`
- Test: `dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/activity/CatchPolicyTest.java`
- Test fixture: `dws-orchestrator/src/test/resources/try-order.yaml`

**Interfaces:**
- Consumes: `WorkflowErrors`, `ErrorKind` (Task 3); `DefinitionLookup.taskByName` (Task 3).
- Produces:
  - `record CatchDecisionRequest(String tryTaskName, String failedTaskName, String failureMessage,
    int attempt, long firstFailureEpochMillis, long nowEpochMillis, JsonNode data, JsonNode context)`
  - `record CatchDecision(boolean caught, boolean retry, long delayMillis, JsonNode error,
    String errorVariable)`
  - `CatchPolicy.decide(CatchDecisionRequest)` → `CatchDecision`
  - `CatchDecisionActivity` (registered activity, returns `CatchDecision`)

- [ ] **Step 1: Write the fixture**

`dws-orchestrator/src/test/resources/try-order.yaml`:

```yaml
document:
  dsl: '1.0.0'
  namespace: test
  name: try-order
  version: '0.1.0'
use:
  retries:
    thrice:
      delay:
        seconds: 2
      backoff:
        exponential: {}
      limit:
        attempt:
          count: 3
do:
  - guarded:
      try:
        - fetchOrder:
            call: http
            with:
              method: get
              endpoint: https://example.test/orders
      catch:
        errors:
          with:
            type: https://open-workflow-specification.org/dsl/errors/types/communication
            status: 503
        as: failure
        retry: thrice
        do:
          - recordFailure:
              set:
                recovered: '"yes"'
  - finish:
      set:
        done: '"yes"'
```

- [ ] **Step 2: Write the failing tests**

`CatchPolicyTest.java` — seed `WorkflowSupport` from the fixture exactly as
`InterpreterWorkflowIntegrationTest.seedSupport()` does, then:

```java
  private CatchDecisionRequest request(String failureMessage, int attempt) {
    return new CatchDecisionRequest(
        "guarded", "fetchOrder", failureMessage, attempt, 0L, 1_000L,
        mapper.createObjectNode(), mapper.createObjectNode());
  }

  @Test
  void matchingTypeAndStatusIsCaught() {
    CatchDecision decision =
        CatchPolicy.decide(request("step 'fetch-order' failed with status 503: down", 1));
    assertThat(decision.caught()).isTrue();
    assertThat(decision.error().get("status").asInt()).isEqualTo(503);
    assertThat(decision.errorVariable()).isEqualTo("failure");
  }

  @Test
  void nonMatchingStatusIsNotCaught() {
    CatchDecision decision =
        CatchPolicy.decide(request("step 'fetch-order' failed with status 500: boom", 1));
    assertThat(decision.caught()).isFalse();
    assertThat(decision.retry()).isFalse();
  }

  @Test
  void exponentialBackoffDoublesTheDelay() {
    assertThat(CatchPolicy.decide(request(FAILURE_503, 1)).delayMillis()).isEqualTo(2_000L);
    assertThat(CatchPolicy.decide(request(FAILURE_503, 2)).delayMillis()).isEqualTo(4_000L);
  }

  @Test
  void attemptLimitStopsRetrying() {
    assertThat(CatchPolicy.decide(request(FAILURE_503, 3)).retry()).isTrue();
    CatchDecision exhausted = CatchPolicy.decide(request(FAILURE_503, 4));
    assertThat(exhausted.caught()).isTrue();
    assertThat(exhausted.retry()).isFalse();
  }
```

where `FAILURE_503 = "step 'fetch-order' failed with status 503: down"`. Add further tests, each
building its own inline definition through `WorkflowReader` so the fixture stays readable:

- an empty `catch: {}` catches a `RUNTIME` failure;
- `catch.when` referencing `$failure.status` gates the catch;
- `catch.exceptWhen` vetoes an otherwise-matching error;
- an inline `retry:` policy behaves identically to the named `thrice`;
- `retry: doesNotExist` fails with a message containing `doesNotExist`;
- `limit.duration: {seconds: 5}` with `nowEpochMillis - firstFailureEpochMillis = 6_000` stops
  retrying;
- `limit.attempt.count: 0` is treated as absent (retries continue);
- `errors.with.status: 0` is treated as absent (does not constrain);
- `limit.attempt.duration: {seconds: 1}` fails with a message containing
  `limit.attempt.duration`;
- `jitter: {from: {seconds: 1}, to: {seconds: 2}}` on a 2s constant delay yields a delay in
  `[3000, 4000]` over 50 invocations, and is not always the same value.

- [ ] **Step 3: Run to verify they fail**

Run: `cd dws-orchestrator && ./mvnw test -Djava.version=21 -Dtest=CatchPolicyTest`
Expected: FAIL — `CatchPolicy` does not exist.

- [ ] **Step 4: Add the request/result records**

```java
package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A failure inside a try task's body, offered to its catch clause. Clock values are supplied by the
 * caller (from the workflow context's replay-safe instant) rather than read inside the activity, so
 * {@code limit.duration} accounting is stable across replay.
 */
public record CatchDecisionRequest(
    String tryTaskName,
    String failedTaskName,
    String failureMessage,
    int attempt,
    long firstFailureEpochMillis,
    long nowEpochMillis,
    JsonNode data,
    JsonNode context) {}
```

```java
package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The catch clause's verdict on one failure: whether it is handled here, whether to attempt the try
 * body again after {@code delayMillis}, and the error object to bind under {@code errorVariable}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatchDecision(
    boolean caught, boolean retry, long delayMillis, JsonNode error, String errorVariable) {}
```

- [ ] **Step 5: Implement `CatchPolicy`**

Structure it as one public `decide` plus small private helpers — filter match, condition evaluation,
policy resolution, limit check, delay computation. The essential shape:

```java
  public static CatchDecision decide(CatchDecisionRequest request) {
    TryTask tryTask = DefinitionLookup.taskByName(request.tryTaskName()).getTryTask();
    TryTaskCatch clause = tryTask.getCatch();
    ObjectMapper mapper = WorkflowSupport.mapper();

    ErrorKind kind = WorkflowErrors.classify(request.failureMessage());
    int status = WorkflowErrors.statusOf(request.failureMessage(), kind);
    JsonNode error =
        WorkflowErrors.build(
            kind, status, "/" + request.failedTaskName(), request.failureMessage(), mapper);
    String variable = (clause.getAs() == null || clause.getAs().isBlank()) ? "error" : clause.getAs();

    if (!matches(clause, error) || !conditionsAllow(clause, request, error, variable)) {
      return new CatchDecision(false, false, 0L, error, variable);
    }
    long delay = retryDelay(clause, request, error, variable);   // -1 when no further retry applies
    return new CatchDecision(true, delay >= 0, Math.max(delay, 0L), error, variable);
  }
```

Rules to implement inside those helpers, each already pinned by a test:

- `matches`: for each of `type`, `instance`, `title` compare when non-null and non-blank; for
  `status` compare only when `filter.getStatus() != 0`; map the SDK's `getDetails()` against the
  error's `detail`. A null `clause.getErrors()` or null `getWith()` matches everything.
- `conditionsAllow`: evaluate `clause.getWhen()` truthy (when present) and `clause.getExceptWhen()`
  falsy (when present) via `WorkflowSupport.jq().evaluate(expr, request.data(), variables)` where
  `variables = Map.of("context", request.context(), variable, error)`, applying `JqEvaluator`'s
  truthiness rule.
- `retryDelay`: return `-1` when `clause.getRetry()` is null. Resolve the policy: inline via
  `getRetryPolicyDefinition()`, or by name via
  `WorkflowSupport.definition().getUse().getRetries().getAdditionalProperties().get(name)` — a null
  result throws `IllegalStateException("retry policy '" + name + "' is not defined in use.retries")`.
  Throw `IllegalStateException("retry policy limit.attempt.duration is not supported (per-attempt "
  + "timeouts are not implemented)")` when `limit.getAttempt().getDuration() != null`. Return `-1`
  when the policy's `when` is falsy, its `exceptWhen` truthy, `attempt > limit.attempt.count` (when
  count `!= 0`), or `now - firstFailure > limit.duration`. Otherwise compute:

```java
    Duration base = policy.getDelay() == null ? Duration.ofSeconds(1) : durationOf(policy.getDelay());
    long millis = base.toMillis();
    RetryBackoff backoff = policy.getBackoff();
    if (backoff != null && backoff.getLinearBackoff() != null) {
      millis = millis * request.attempt();
    } else if (backoff != null && backoff.getExponentialBackOff() != null) {
      millis = millis * (1L << (request.attempt() - 1));
    }
    RetryPolicyJitter jitter = policy.getJitter();
    if (jitter != null) {
      long from = durationOf(jitter.getFrom()).toMillis();
      long to = durationOf(jitter.getTo()).toMillis();
      millis += from + (long) (ThreadLocalRandom.current().nextDouble() * Math.max(to - from, 0));
    }
    return millis;
```

Copy the `TimeoutAfter` → `Duration` conversion from `InterpreterWorkflow.durationOf(...)` into a
shared helper rather than duplicating it — move that method to a small package-private utility both
classes use, and update `InterpreterWorkflow` to call it.

- [ ] **Step 6: Implement the activity and register it**

```java
package io.dws.orchestrator.workflow.activity;

import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;

/**
 * Decides what a try task's catch clause does with one failure. Pure in-process evaluation like
 * {@link EvaluateSwitchActivity} — but it also draws the retry jitter, which is why it must be an
 * activity: Dapr records the result in the instance history, so replay reuses the same delay
 * instead of re-drawing it.
 */
public class CatchDecisionActivity implements WorkflowActivity {

  @Override
  public Object run(WorkflowActivityContext ctx) {
    return CatchPolicy.decide(ctx.getInput(CatchDecisionRequest.class));
  }
}
```

In `WorkflowRuntimeBootstrap.startRuntime()`, add `builder.registerActivity(CatchDecisionActivity.class);`
after the two data-flow registrations, and add the import.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd dws-orchestrator && ./mvnw test -Djava.version=21 -Dtest=CatchPolicyTest`
Expected: PASS.

- [ ] **Step 8: Run the gate**

Run: `cd dws-orchestrator && ./mvnw verify -Djava.version=21`
Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
git add dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/Catch*.java \
        dws-orchestrator/src/main/java/io/dws/orchestrator/config/WorkflowRuntimeBootstrap.java \
        dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/activity/CatchPolicyTest.java \
        dws-orchestrator/src/test/resources/try-order.yaml
git commit -m "feat(orchestrator): decide catch handling and retry timing in one activity"
```

---

## Task 5: Orchestrator — `try` dispatch, retry loop, and the error variable

**Files:**
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/InterpreterWorkflow.java`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/DataFlowInputRequest.java`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/DataFlowOutputRequest.java`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/EvaluateSetRequest.java`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/EvaluateSwitchRequest.java`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/DataFlowPipeline.java`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/EvaluateSetActivity.java`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/EvaluateSwitchActivity.java`

**Interfaces:**
- Consumes: `runTaskList`, `ScopeResult`, `ScopeEnd` (Task 2); `CatchDecision`,
  `CatchDecisionRequest`, `CatchDecisionActivity` (Task 4).
- Produces: `try` is interpreted; `taskTypeOf` returns `"try"`; every request record above gains a
  trailing `Map<String, JsonNode> variables` component.

- [ ] **Step 1: Widen the request records**

Add `Map<String, JsonNode> variables` as the last component of `DataFlowInputRequest`,
`DataFlowOutputRequest`, `EvaluateSetRequest`, and `EvaluateSwitchRequest`. In `DataFlowPipeline`,
merge them into the jq variable map:

```java
  private static Map<String, JsonNode> scope(JsonNode context, Map<String, JsonNode> variables) {
    Map<String, JsonNode> merged = new HashMap<>();
    merged.put("context", context);
    if (variables != null) {
      merged.putAll(variables);
    }
    return merged;
  }
```

and use `scope(context, request.variables())` wherever `Map.of("context", context)` appears today. Do
the same in `EvaluateSetActivity.apply(...)` and `EvaluateSwitchActivity.evaluate(...)` so `set` and
`switch` inside `catch.do` can read the error too. Update every existing construction site (including
the integration test's stubs) to pass `Map.of()`.

- [ ] **Step 2: Run the build to fix all call sites**

Run: `cd dws-orchestrator && ./mvnw test -Djava.version=21`
Expected: PASS once every constructor call is updated. No behaviour change.

- [ ] **Step 3: Write the failing test**

In `InterpreterWorkflowIntegrationTest`, add a `seedTrySupport()` helper that seeds `WorkflowSupport`
from `try-order.yaml`, a `stubCatchDecision(ctx)` that runs the real `CatchPolicy.decide(...)` through
the mocked context (mirroring how `EvaluateSwitchActivity` is stubbed), and a `stubTimer(ctx)` that
returns an already-completed `Task<Void>` from `ctx.createTimer(any(Duration.class))`. Then:

```java
@Test
void failingCallIsRetriedThenRecovered() {
  WorkflowContext ctx = mock(WorkflowContext.class);
  stubContext(ctx);
  stubCatchDecision(ctx);
  stubTimer(ctx);
  when(ctx.getInput(JsonNode.class)).thenReturn(mapper.createObjectNode());
  // fetch-order fails with 503 on every attempt; the retry limit is 3.
  when(ctx.callActivity(eq(CallServiceActivity.class.getName()), any(),
          any(WorkflowTaskOptions.class), eq(JsonNode.class)))
      .thenThrow(new StepInvocationException("fetch-order", 503, "down", null));

  workflow.execute(ctx);

  verify(ctx, times(3)).createTimer(any(Duration.class));
  ArgumentCaptor<JsonNode> completed = ArgumentCaptor.forClass(JsonNode.class);
  verify(ctx).complete(completed.capture());
  assertThat(completed.getValue().get("recovered").asText()).isEqualTo("yes");
  assertThat(completed.getValue().get("done").asText()).isEqualTo("yes");
}
```

- [ ] **Step 4: Run to verify it fails**

Run: `cd dws-orchestrator && ./mvnw test -Djava.version=21 -Dtest=InterpreterWorkflowIntegrationTest#failingCallIsRetriedThenRecovered`
Expected: FAIL with "uses for/try, which is recognised but not yet interpreted".

- [ ] **Step 5: Implement the `try` branch**

In `dispatchBody`, replace the combined `for`/`try` rejection with a `try` branch plus a `for`-only
rejection:

```java
    } else if (task.getTryTask() != null) {
      return dispatchTry(ctx, task.getTryTask(), name, data, variables, depth, events);
    } else if (task.getForTask() != null) {
      throw new UnsupportedOperationException(
          "task '" + name + "' uses for, which is recognised but not yet interpreted");
    }
```

Then add the retry loop. Note that `attempt` counts try-body executions, and the durable timer is the
only wait:

```java
  /**
   * Runs a try task: attempt the body, and on failure ask the catch clause what to do. Retrying
   * re-runs the <em>whole</em> try list from the try task's original input, because the retry policy
   * belongs to the try task rather than to any inner task.
   */
  private Body dispatchTry(
      WorkflowContext ctx,
      TryTask tryTask,
      String name,
      JsonNode data,
      JsonNode context,
      Map<String, JsonNode> variables,
      int depth,
      AdminEventBuilder events) {
    long firstFailureMillis = 0L;

    for (int attempt = 1; ; attempt++) {
      try {
        ScopeResult body =
            runTaskList(ctx, tryTask.getTry(), data, context, variables, depth + 1, events);
        return new Body(body.data(), body.context(), FlowOutcome.of(tryTask.getThen()), body.end());
      } catch (RuntimeException failure) {
        long now = ctx.getCurrentInstant().toEpochMilli();
        if (attempt == 1) {
          firstFailureMillis = now;
        }
        CatchDecision decision =
            ctx.callActivity(
                    CatchDecisionActivity.class.getName(),
                    new CatchDecisionRequest(
                        name,
                        failedTaskNameOf(failure, name),
                        String.valueOf(failure.getMessage()),
                        attempt,
                        firstFailureMillis,
                        now,
                        data,
                        context),
                    WorkflowSupport.defaultTaskOptions(),
                    CatchDecision.class)
                .await();

        if (!decision.caught()) {
          throw failure;
        }
        if (decision.retry()) {
          ctx.createTimer(Duration.ofMillis(decision.delayMillis())).await();
          continue;
        }
        return recover(ctx, tryTask, name, data, decision, variables, depth, events);
      }
    }
  }
```

`recover(...)` runs `catch.do` when present, with the error added to the variable map:

```java
  private Body recover(...) {
    TryTaskCatch clause = tryTask.getCatch();
    if (clause.getDo() == null || clause.getDo().isEmpty()) {
      return new Body(data, context, FlowOutcome.of(tryTask.getThen()), ScopeEnd.FELL_THROUGH);
    }
    Map<String, JsonNode> scoped = new HashMap<>(variables);
    scoped.put(decision.errorVariable(), decision.error());
    ScopeResult recovered =
        runTaskList(ctx, clause.getDo(), data, context, scoped, depth + 1, events);
    return new Body(
        recovered.data(), recovered.context(), FlowOutcome.of(tryTask.getThen()), recovered.end());
  }
```

Two mechanical details to finish:

1. `Body` currently carries only `{data, then}`. A nested scope also produces a new context and can
   end the instance, so widen `Body` to `record Body(JsonNode data, JsonNode context, FlowOutcome
   then, ScopeEnd end)` and update every branch of `dispatchBody` to pass the incoming context and
   `ScopeEnd.FELL_THROUGH`. In `runTaskList`, propagate a nested `ScopeEnd.END` by returning
   immediately rather than continuing the loop.
2. `failedTaskNameOf(failure, fallback)` extracts the inner task name from the failure message when
   it starts with `task '` (the shape `DataFlowException` and the interpreter's own messages use),
   and falls back to the try task's name otherwise. Keep it a small private static helper with its
   own unit test.

- [ ] **Step 6: Report `try` in lifecycle events**

`taskTypeOf` already returns `"try"` — verify it does and leave it. Confirm that a handled error
publishes `taskCompleted` for the try task: this falls out of `dispatchTry` returning normally, since
`runTaskList`'s `catch (RuntimeException e)` only fires when `dispatch` throws.

- [ ] **Step 7: Run the test to verify it passes**

Run: `cd dws-orchestrator && ./mvnw test -Djava.version=21 -Dtest=InterpreterWorkflowIntegrationTest`
Expected: PASS, including the pre-existing cases.

- [ ] **Step 8: Run the gate**

Run: `cd dws-orchestrator && ./mvnw verify -Djava.version=21`
Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
git add dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/
git commit -m "feat(orchestrator): interpret try/catch with retry and a scoped error variable"
```

---

## Task 6: Integration coverage and the full gate

**Files:**
- Modify: `dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/InterpreterWorkflowIntegrationTest.java`
- Modify: `dws-orchestrator/src/test/resources/try-order.yaml` (or add sibling fixtures)

**Interfaces:**
- Consumes: everything from Tasks 2–5.
- Produces: no production code.

- [ ] **Step 1: Caught-and-recovered, with the error readable**

Add a fixture whose `catch.do` sets a field from the error, e.g. `set: {reason: '$failure.detail'}`,
and assert the completion output carries the upstream detail. This proves the D10 variable binding
reaches a nested `set`.

- [ ] **Step 2: Retry succeeds on the second attempt, from the original input**

Stub `CallServiceActivity` to throw once and then return `{"order": 1}`. Assert exactly one
`createTimer` call, that the instance completes, and — with a two-task `try` list whose first task
mutates the data — that the second attempt's first task saw the try task's original input rather than
the failed attempt's partial data.

- [ ] **Step 3: Filtered-out error propagates**

Stub the step to fail with status 500 against a `catch` filtering on 503. Assert
`assertThatThrownBy(() -> workflow.execute(ctx))`, that `ctx.complete(...)` was never called, and
that an `instanceFailed` admin event was published carrying the original message.

- [ ] **Step 4: Failure inside `catch.do` propagates**

Give `catch.do` a task that fails (an `output.schema` violation is the easiest deterministic
failure). Assert the instance fails and that the try task published `taskFailed`.

- [ ] **Step 5: Scope semantics**

Two cases: `then: exit` on a task inside `try` skips the rest of that list, completes the try task,
and lets `finish` run; `then: end` inside `try` completes the instance without running `finish`.

- [ ] **Step 6: Nesting depth guard**

Build a definition nesting `try` inside `try` past `MAX_DEPTH` (generate it in the test rather than
hand-writing 17 levels of YAML) and assert the failure message names the depth limit.

- [ ] **Step 7: Error does not leak**

Assert the completion output contains no `type`/`status`/`detail` fields from the error object, and
that a task after the `try` reading `$context` does not see it.

- [ ] **Step 8: Nested data flow**

Give a task inside `try` an `input.from`, an `output.as`, and an `export.as`; assert all three are
applied and that a task *after* the `try` task reads the exported value via `$context`.

- [ ] **Step 9: Run both component gates**

```bash
cd dws-orchestrator && ./mvnw verify -Djava.version=21
cd ../dws-controller && ./mvnw test -Djava.version=21
```
Expected: BUILD SUCCESS for both. Record the test counts in the commit message.

- [ ] **Step 10: Commit**

```bash
git add dws-orchestrator/src/test/
git commit -m "test(orchestrator): cover try/catch/retry end to end"
```

---

## Self-Review Notes

Checked against `specs/workflow-error-handling/spec.md` and `specs/nested-task-execution/spec.md`:

- Every `workflow-error-handling` requirement maps to a task — try body (T5.1), propagation (T5.2),
  error object (T3), static filter (T4.5), dynamic filter (T4.5), error variable (T5.1/T5.5),
  policy resolution (T4.5), retry loop (T5.5), backoff/jitter/limits (T4.5), recovery block (T5.5),
  shared pipeline (T5.1 + T6.8), catch-path directive (T5.5).
- Every `nested-task-execution` requirement maps to a task — scopes (T2), `exit`/`end` (T2 + T6.5),
  depth bound (T2 + T6.6), lookup at depth (T3.8), name uniqueness (T1.4), nested compilation (T1.3).
- Type consistency: `ScopeResult`/`ScopeEnd`/`CatchDecision`/`CatchDecisionRequest`/`Body` field
  names are used identically in Tasks 2, 4, and 5. `Body` gains its `context`/`end` components in
  Task 5 Step 5 — Task 2 must not treat `Body` as final.
- One knowingly deferred item: Task 2's own nesting-depth test is covered by Task 6 Step 6 rather
  than inline, because a nested definition cannot be built until `try` is interpreted. Called out in
  Task 2's commit message so it is not read as an oversight.
