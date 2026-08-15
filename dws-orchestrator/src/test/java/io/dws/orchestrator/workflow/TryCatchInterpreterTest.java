package io.dws.orchestrator.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.durabletask.Task;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.error.RaisedErrorException;
import io.dws.orchestrator.error.StepInvocationException;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.activity.AdminEventActivity;
import io.dws.orchestrator.workflow.activity.AdminEventRequest;
import io.dws.orchestrator.workflow.activity.CallServiceActivity;
import io.dws.orchestrator.workflow.activity.CatchDecisionActivity;
import io.dws.orchestrator.workflow.activity.CatchDecisionRequest;
import io.dws.orchestrator.workflow.activity.CatchPolicy;
import io.dws.orchestrator.workflow.activity.DataFlowInputActivity;
import io.dws.orchestrator.workflow.activity.DataFlowInputRequest;
import io.dws.orchestrator.workflow.activity.DataFlowOutputActivity;
import io.dws.orchestrator.workflow.activity.DataFlowOutputRequest;
import io.dws.orchestrator.workflow.activity.DataFlowPipeline;
import io.dws.orchestrator.workflow.activity.DataFlowResult;
import io.dws.orchestrator.workflow.activity.EvaluateForActivity;
import io.dws.orchestrator.workflow.activity.EvaluateForRequest;
import io.dws.orchestrator.workflow.activity.EvaluateSetActivity;
import io.dws.orchestrator.workflow.activity.EvaluateSetRequest;
import io.dws.orchestrator.workflow.activity.EvaluateSwitchActivity;
import io.dws.orchestrator.workflow.activity.EvaluateSwitchRequest;
import io.dws.orchestrator.workflow.activity.EvaluateWhileActivity;
import io.dws.orchestrator.workflow.activity.EvaluateWhileRequest;
import io.dws.orchestrator.workflow.activity.FlowOutcome;
import io.dws.orchestrator.workflow.activity.RaiseErrorActivity;
import io.dws.orchestrator.workflow.activity.RaiseErrorRequest;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Drives the interpreter's {@code try}/{@code catch}/{@code retry} handling end to end against a
 * mocked {@link WorkflowContext}. The in-process activities run their real bodies through the stubs
 * — only the network-facing call activity and the durable timer are faked — so these assert real
 * catch semantics rather than a mock's arrangement.
 */
class TryCatchInterpreterTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final InterpreterWorkflow workflow = new InterpreterWorkflow();

  // ---- fixtures ------------------------------------------------------------

  private void seed(Workflow definition) {
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "try-order-workflow",
        "try-order-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        /* daprClient (unused; activities are stubbed) */ null,
        mock(WorkflowTaskOptions.class),
        "pubsub");
  }

  private void seedClasspath(String resource) throws Exception {
    seed(WorkflowReader.readWorkflowFromClasspath(resource));
  }

  private void seedYaml(String yaml) throws Exception {
    seed(WorkflowReader.readWorkflowFromString(yaml, WorkflowFormat.YAML));
  }

  // ---- context stubs -------------------------------------------------------

  /** Runs every in-process activity for real; only I/O and the timer are faked. */
  @SuppressWarnings("unchecked")
  private void stubContext(WorkflowContext ctx) {
    when(ctx.getInstanceId()).thenReturn("inst-try-1");
    when(ctx.getCurrentInstant()).thenReturn(Instant.parse("2026-07-30T00:00:00Z"));
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.createObjectNode());

    Task<Void> voidTask = completed(null);
    when(ctx.callActivity(
            eq(AdminEventActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(Void.class)))
        .thenReturn(voidTask);
    when(ctx.createTimer(any(Duration.class))).thenReturn(voidTask);

    when(ctx.callActivity(
            eq(EvaluateSetActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv -> completed(EvaluateSetActivity.apply((EvaluateSetRequest) inv.getArgument(1))));
    when(ctx.callActivity(
            eq(EvaluateSwitchActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(FlowOutcome.class)))
        .thenAnswer(
            inv ->
                completed(
                    EvaluateSwitchActivity.evaluate((EvaluateSwitchRequest) inv.getArgument(1))));
    when(ctx.callActivity(
            eq(CatchDecisionActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(io.dws.orchestrator.workflow.activity.CatchDecision.class)))
        .thenAnswer(
            inv -> completed(CatchPolicy.decide((CatchDecisionRequest) inv.getArgument(1))));
    when(ctx.callActivity(
            eq(RaiseErrorActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv -> completed(RaiseErrorActivity.apply((RaiseErrorRequest) inv.getArgument(1))));
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
                completed(EvaluateWhileActivity.apply((EvaluateWhileRequest) inv.getArgument(1))));
    when(ctx.callActivity(
            eq(DataFlowInputActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv ->
                completed(DataFlowPipeline.applyInput((DataFlowInputRequest) inv.getArgument(1))));
    when(ctx.callActivity(
            eq(DataFlowOutputActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(DataFlowResult.class)))
        .thenAnswer(
            inv ->
                completed(
                    DataFlowPipeline.applyOutput((DataFlowOutputRequest) inv.getArgument(1))));
  }

  /** The step service always fails with {@code status}. */
  private void stubCallAlwaysFailing(WorkflowContext ctx, String appId, int status) {
    when(ctx.callActivity(
            eq(CallServiceActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenThrow(new StepInvocationException(appId, status, "upstream down", null));
  }

  /**
   * A mock durable task that also honours {@code thenApply}, matching Dapr's real task API — the
   * interpreter maps continuations rather than awaiting and then constructing.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <T> Task<T> completed(T value) {
    Task<T> task = mock(Task.class);
    when(task.await()).thenReturn(value);
    when(task.thenApply(any()))
        .thenAnswer(
            invocation -> {
              Function<T, Object> transform = invocation.getArgument(0);
              Task<Object> mapped = mock(Task.class);
              when(mapped.await()).thenAnswer(ignored -> transform.apply(task.await()));
              return mapped;
            });
    return task;
  }

  private static JsonNode completionOutput(WorkflowContext ctx) {
    ArgumentCaptor<Object> output = ArgumentCaptor.forClass(Object.class);
    verify(ctx).complete(output.capture());
    return (JsonNode) output.getValue();
  }

  private static List<String> adminEventTypes(WorkflowContext ctx) {
    ArgumentCaptor<Object> reqs = ArgumentCaptor.forClass(Object.class);
    verify(ctx, org.mockito.Mockito.atLeastOnce())
        .callActivity(
            eq(AdminEventActivity.class.getName()),
            reqs.capture(),
            any(WorkflowTaskOptions.class),
            eq(Void.class));
    return reqs.getAllValues().stream()
        .map(r -> ((AdminEventRequest) r).data().get("type").asText())
        .toList();
  }

  // ---- caught, retried, recovered -----------------------------------------

  @Test
  void retriesToItsLimitThenRunsTheRecoveryBlock() throws Exception {
    seedClasspath("try-order.yaml");
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    stubCallAlwaysFailing(ctx, "fetch-order", 503);

    workflow.execute(ctx);

    // The fixture allows three body executions, so exactly two waits happen between them.
    verify(ctx, times(2)).createTimer(any(Duration.class));
    verify(ctx, times(3))
        .callActivity(
            eq(CallServiceActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class));

    JsonNode output = completionOutput(ctx);
    assertThat(output.get("recovered").textValue()).isEqualTo("yes");
    assertThat(output.get("done").textValue()).isEqualTo("yes");
  }

  @Test
  void recoveryBlockReadsTheErrorUnderItsDeclaredName() throws Exception {
    seedClasspath("try-order.yaml");
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    stubCallAlwaysFailing(ctx, "fetch-order", 503);

    workflow.execute(ctx);

    // recordFailure sets `reason` from $failure.detail — the fixture renames the error variable.
    assertThat(completionOutput(ctx).get("reason").textValue())
        .contains("fetch-order")
        .contains("503");
  }

  @Test
  void handledErrorReportsTheTryTaskCompletedNotFailed() throws Exception {
    seedClasspath("try-order.yaml");
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    stubCallAlwaysFailing(ctx, "fetch-order", 503);

    workflow.execute(ctx);

    List<String> events = adminEventTypes(ctx);
    // The inner task fails on every attempt and says so; the try task itself completes.
    assertThat(events).filteredOn("io.dws.task.failed"::equals).hasSize(3);
    assertThat(events).doesNotContain("io.dws.instance.failed");
    assertThat(events).endsWith("io.dws.instance.completed");
  }

  @Test
  void errorNeverReachesTheCompletionOutput() throws Exception {
    seedClasspath("try-order.yaml");
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    stubCallAlwaysFailing(ctx, "fetch-order", 503);

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    assertThat(output.has("type")).isFalse();
    assertThat(output.has("status")).isFalse();
    assertThat(output.has("instance")).isFalse();
    assertThat(output.has("title")).isFalse();
  }

  @Test
  void succeedingBodyNeitherRetriesNorRecovers() throws Exception {
    seedClasspath("try-order.yaml");
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    Task<JsonNode> success = completed(mapper.readTree("{\"order\":1}"));
    when(ctx.callActivity(
            eq(CallServiceActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenReturn(success);

    workflow.execute(ctx);

    verify(ctx, never()).createTimer(any(Duration.class));
    JsonNode output = completionOutput(ctx);
    assertThat(output.get("order").intValue()).isEqualTo(1);
    assertThat(output.has("recovered")).isFalse();
  }

  // ---- not caught ----------------------------------------------------------

  @Test
  void filteredOutErrorPropagatesAndFailsTheInstance() throws Exception {
    seedClasspath("try-order.yaml");
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    // The fixture catches 503 only.
    stubCallAlwaysFailing(ctx, "fetch-order", 500);

    assertThatThrownBy(() -> workflow.execute(ctx))
        .isInstanceOf(StepInvocationException.class)
        .hasMessageContaining("500");

    verify(ctx, never()).complete(any());
    verify(ctx, never()).createTimer(any(Duration.class));
    assertThat(adminEventTypes(ctx)).contains("io.dws.instance.failed");
  }

  @Test
  void failureInsideTheRecoveryBlockPropagates() throws Exception {
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
                - fetchOrder:
                    call: http
                    with:
                      method: get
                      endpoint: http://order-service/run
              catch:
                do:
                  - repair:
                      set:
                        repaired: '"yes"'
                      output:
                        schema:
                          document:
                            type: object
                            required: [mustExist]
          - finish:
              set:
                done: '"yes"'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    stubCallAlwaysFailing(ctx, "fetch-order", 503);

    // The recovery task's own output schema fails: nothing catches a catch.
    assertThatThrownBy(() -> workflow.execute(ctx)).hasMessageContaining("repair");

    verify(ctx, never()).complete(any());
    assertThat(adminEventTypes(ctx)).contains("io.dws.instance.failed");
  }

  // ---- scope semantics -----------------------------------------------------

  @Test
  void exitInsideTryReturnsToTheEnclosingTask() throws Exception {
    seedYaml(scopeYaml("exit"));
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    assertThat(output.get("first").textValue()).isEqualTo("yes");
    // `exit` skipped the rest of the try list …
    assertThat(output.has("second")).isFalse();
    // … but the task after the try task still ran.
    assertThat(output.get("done").textValue()).isEqualTo("yes");
  }

  @Test
  void endInsideTryCompletesTheInstance() throws Exception {
    seedYaml(scopeYaml("end"));
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    assertThat(output.get("first").textValue()).isEqualTo("yes");
    assertThat(output.has("second")).isFalse();
    // `end` terminated the instance, so the task after the try task never ran.
    assertThat(output.has("done")).isFalse();
  }

  @Test
  void directiveCannotTargetATaskInAnotherScope() throws Exception {
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
                - first:
                    set:
                      first: '"yes"'
                    then: finish
              catch:
                errors:
                  with:
                    status: 503
          - finish:
              set:
                done: '"yes"'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);

    assertThatThrownBy(() -> workflow.execute(ctx))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("finish")
        .hasMessageContaining("scope");
  }

  @Test
  void nestingBeyondTheDepthLimitFailsWithAClearMessage() throws Exception {
    seedYaml(deeplyNestedYaml(InterpreterWorkflow.MAX_DEPTH + 2));
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);

    assertThatThrownBy(() -> workflow.execute(ctx))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nesting depth");
  }

  // ---- data flow inside a try ---------------------------------------------

  @Test
  void nestedTaskRunsTheStandardDataFlowPipeline() throws Exception {
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
                - shape:
                    set:
                      raw: '"value"'
                    output:
                      as: '${ { shaped: .raw } }'
                    export:
                      as: '${ { exported: .shaped } }'
              catch: {}
          - readContext:
              set:
                placeholder: '"x"'
              input:
                from: '${ { shaped: .shaped, seen: $context.exported } }'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    // output.as applied inside the try …
    assertThat(output.get("shaped").textValue()).isEqualTo("value");
    // … and export.as written there is visible to a task outside the try task.
    assertThat(output.get("seen").textValue()).isEqualTo("value");
  }

  // ---- raise ---------------------------------------------------------------

  @Test
  void raisedErrorInsideTryIsCaughtLikeARealFailure() throws Exception {
    seedYaml(raiseYaml("", 402));
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    // The author's own fields reached catch.do intact — not reclassified to runtime/500.
    assertThat(output.get("reason").textValue()).isEqualTo("balance too low");
    assertThat(output.get("caughtStatus").intValue()).isEqualTo(402);
    assertThat(output.get("caughtType").textValue())
        .isEqualTo("https://example.com/errors/insufficient-funds");
    assertThat(output.get("done").textValue()).isEqualTo("yes");
    assertThat(adminEventTypes(ctx)).doesNotContain("io.dws.instance.failed");
  }

  @Test
  void raisedErrorIsFilteredByCatchErrorsWithLikeARealFailure() throws Exception {
    // The catch filters on 404; the task raises 402, so nothing catches it.
    seedYaml(raiseYaml("", 404));
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);

    assertThatThrownBy(() -> workflow.execute(ctx))
        .isInstanceOf(RaisedErrorException.class)
        .hasMessageContaining("balance too low");

    verify(ctx, never()).complete(any());
    assertThat(adminEventTypes(ctx)).contains("io.dws.instance.failed");
  }

  @Test
  void raisedErrorInsideTryCanTriggerARetry() throws Exception {
    seedYaml(
        raiseYaml(
            """
            retry:
              delay:
                seconds: 1
              limit:
                attempt:
                  count: 2
            """,
            402));
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);

    workflow.execute(ctx);

    // Two body executions allowed, so exactly one wait between them.
    verify(ctx, times(1)).createTimer(any(Duration.class));
    assertThat(completionOutput(ctx).get("reason").textValue()).isEqualTo("balance too low");
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
        .isInstanceOf(RaisedErrorException.class)
        .hasMessageContaining("balance too low");

    verify(ctx, never()).complete(any());
    assertThat(adminEventTypes(ctx))
        .containsExactly(
            "io.dws.instance.started",
            "io.dws.task.started",
            "io.dws.task.failed",
            "io.dws.instance.failed");
  }

  @Test
  void raisedErrorReadsTheTaskDataThroughItsExpressionFields() throws Exception {
    seedYaml(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: try-order-workflow
          version: '1.0.0'
        do:
          - seed:
              set:
                who: '"alice"'
          - guarded:
              try:
                - explode:
                    raise:
                      error:
                        type: https://example.com/errors/insufficient-funds
                        status: 402
                        title: Insufficient funds
                        detail: '${ "no funds for " + .who }'
              catch:
                errors:
                  with:
                    status: 402
                do:
                  - repair:
                      set:
                        reason: '${ $error.detail }'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);

    workflow.execute(ctx);

    assertThat(completionOutput(ctx).get("reason").textValue()).isEqualTo("no funds for alice");
  }

  // ---- fixture builders ----------------------------------------------------

  /**
   * A try task whose body is a single {@code raise} of a 402, caught by a filter on {@code
   * catchStatus} and recovered by a block that copies the error's own fields into the data.
   *
   * @param catchExtras extra catch clauses (e.g. a retry policy), written unindented and
   *     re-indented here to the clause's own level, alongside {@code errors:} and {@code do:}
   */
  private static String raiseYaml(String catchExtras, int catchStatus) {
    String yaml =
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
                    status: %d
                #EXTRAS#
                do:
                  - repair:
                      set:
                        reason: '${ $error.detail }'
                        caughtStatus: '${ $error.status }'
                        caughtType: '${ $error.type }'
          - finish:
              set:
                done: '"yes"'
        """
            .formatted(catchStatus);
    // The marker occupies a whole line at the catch clause's own indentation, so the extras drop
    // in already aligned with `errors:` and `do:` — no text-block indentation arithmetic.
    return yaml.replace(
        "        #EXTRAS#\n", catchExtras.isBlank() ? "" : catchExtras.stripTrailing().indent(8));
  }

  /** A two-task try list whose first task carries the given directive. */
  private static String scopeYaml(String directive) {
    return """
        document:
          dsl: 1.0.0
          namespace: examples
          name: try-order-workflow
          version: '1.0.0'
        do:
          - guarded:
              try:
                - first:
                    set:
                      first: '"yes"'
                    then: %s
                - second:
                    set:
                      second: '"yes"'
              catch: {}
          - finish:
              set:
                done: '"yes"'
        """
        .formatted(directive);
  }

  // ---- for -----------------------------------------------------------------

  @Test
  void raisedErrorInsideForInsideTryIsCaughtLikeAnyOtherFailure() throws Exception {
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
                - loop:
                    for:
                      each: pet
                      in: .pets
                    do:
                      - explode:
                          raise:
                            error:
                              type: https://example.com/errors/x
                              status: 402
                              title: Bad
                              detail: 'per-element failure'
              catch:
                errors:
                  with:
                    status: 402
                do:
                  - repair:
                      set:
                        reason: '$error.detail'
          - finish:
              set:
                done: '"yes"'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"pets\":[1,2,3]}"));

    workflow.execute(ctx);

    ArgumentCaptor<Object> output = ArgumentCaptor.forClass(Object.class);
    verify(ctx).complete(output.capture());
    JsonNode completed = (JsonNode) output.getValue();
    assertThat(completed.get("reason").textValue()).isEqualTo("per-element failure");
    assertThat(completed.get("done").textValue()).isEqualTo("yes");
  }

  /** {@code depth} nested try tasks, generated rather than hand-written. */
  private static String deeplyNestedYaml(int depth) {
    StringBuilder yaml =
        new StringBuilder(
            """
            document:
              dsl: 1.0.0
              namespace: examples
              name: try-order-workflow
              version: '1.0.0'
            do:
            """);
    String indent = "  ";
    for (int level = 0; level < depth; level++) {
      yaml.append(indent).append("- level").append(level).append(":\n");
      yaml.append(indent).append("    try:\n");
      indent += "      ";
    }
    yaml.append(indent).append("- leaf:\n");
    yaml.append(indent).append("    set:\n");
    yaml.append(indent).append("      reached: '\"yes\"'\n");
    // Close each try with its required catch, outermost last.
    for (int level = depth - 1; level >= 0; level--) {
      indent = indent.substring(0, indent.length() - 6);
      yaml.append(indent).append("  catch: {}\n");
    }
    return yaml.toString();
  }
}
