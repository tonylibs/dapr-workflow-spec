package io.dws.orchestrator.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.durabletask.Task;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dapr.workflows.WorkflowTaskRetryPolicy;
import io.dws.orchestrator.dataflow.DataFlowException;
import io.dws.orchestrator.error.RaisedErrorException;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.activity.AdminEventActivity;
import io.dws.orchestrator.workflow.activity.AdminEventRequest;
import io.dws.orchestrator.workflow.activity.CallRequest;
import io.dws.orchestrator.workflow.activity.CallServiceActivity;
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
import io.dws.orchestrator.workflow.activity.StepActivity;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Parses the fixture DSL 1.0 {@code order.yaml} with the Open Workflow Specification SDK and drives
 * the interpreter's program-counter loop against a mocked {@link WorkflowContext}, asserting the
 * task execution order for both switch branches (checkInventory -> switch .inStock -> chargePayment
 * | notifyOutOfStock -> end) and the lifecycle events scheduled through {@link AdminEventActivity}.
 */
class InterpreterWorkflowIntegrationTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final InterpreterWorkflow workflow = new InterpreterWorkflow();

  @BeforeEach
  void seedSupport() throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromClasspath("order.yaml");
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "order-workflow",
        "order-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        /* daprClient (unused; activities are mocked) */ null,
        mock(WorkflowTaskOptions.class),
        "pubsub");
  }

  /**
   * Stubs admin-event activity scheduling (Void), the two local evaluation activities, and the
   * replay-safe context values. SWITCH/SET dispatch through activities like CALL/EMIT do, so the
   * stubs run the real activity bodies — the interpreter never evaluates jq itself.
   */
  @SuppressWarnings("unchecked")
  private Task<Void> stubContext(WorkflowContext ctx) {
    when(ctx.getInstanceId()).thenReturn("inst-1");
    when(ctx.getCurrentInstant()).thenReturn(Instant.parse("2026-07-24T00:00:00Z"));
    Task<Void> adminTask = mock(Task.class);
    when(adminTask.await()).thenReturn(null);
    when(ctx.callActivity(
            eq(AdminEventActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(Void.class)))
        .thenReturn(adminTask);

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
            eq(EvaluateSetActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv -> completed(EvaluateSetActivity.apply((EvaluateSetRequest) inv.getArgument(1))));

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
    return adminTask;
  }

  private void seedInline(String yaml) throws Exception {
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

  private static JsonNode completionOutput(WorkflowContext ctx) {
    ArgumentCaptor<Object> output = ArgumentCaptor.forClass(Object.class);
    verify(ctx).complete(output.capture());
    return (JsonNode) output.getValue();
  }

  /** A already-resolved durable task yielding {@code value}. */
  @SuppressWarnings("unchecked")
  private static <T> Task<T> completed(T value) {
    Task<T> task = taskWithThenApply();
    when(task.await()).thenReturn(value);
    return task;
  }

  /** A mock durable task whose continuations map its eventual value, like Dapr's real task API. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <T> Task<T> taskWithThenApply() {
    Task<T> task = mock(Task.class);
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

  /**
   * Stubs the canonical multi-app step activity ({@link StepActivity#NAME}) — the path {@code call:
   * http} and {@code run:*} take — to yield {@code task}. The activity input is the raw
   * workflow-data JSON and the target app-id rides in the {@link WorkflowTaskOptions}, so tests
   * read those from the captured call rather than from a request wrapper.
   */
  @SuppressWarnings("unchecked")
  private static void stubStepActivity(WorkflowContext ctx, Task<JsonNode> task) {
    when(ctx.callActivity(
            eq(StepActivity.NAME), any(), any(WorkflowTaskOptions.class), eq(JsonNode.class)))
        .thenReturn(task);
  }

  /** The target app-ids of the step activities scheduled so far, in order. */
  private static List<String> stepActivityAppIds(WorkflowContext ctx, int count) {
    ArgumentCaptor<WorkflowTaskOptions> options =
        ArgumentCaptor.forClass(WorkflowTaskOptions.class);
    verify(ctx, times(count))
        .callActivity(eq(StepActivity.NAME), any(), options.capture(), eq(JsonNode.class));
    return options.getAllValues().stream().map(WorkflowTaskOptions::getAppId).toList();
  }

  /** The activity-input documents the step activities were scheduled with, in order. */
  private static List<JsonNode> stepActivityInputs(WorkflowContext ctx, int count) {
    ArgumentCaptor<Object> inputs = ArgumentCaptor.forClass(Object.class);
    verify(ctx, times(count))
        .callActivity(
            eq(StepActivity.NAME),
            inputs.capture(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class));
    return inputs.getAllValues().stream().map(JsonNode.class::cast).toList();
  }

  @Test
  @SuppressWarnings("unchecked")
  void inStockOrderExecutesCheckInventoryThenChargePayment() throws Exception {
    // Arrange
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"item\":\"widget\"}"));

    JsonNode afterInventory = mapper.readTree("{\"item\":\"widget\",\"inStock\":true}");
    JsonNode afterCharge =
        mapper.readTree("{\"item\":\"widget\",\"inStock\":true,\"charged\":true}");

    Task<JsonNode> callTask = taskWithThenApply();
    when(callTask.await()).thenReturn(afterInventory, afterCharge);
    stubStepActivity(ctx, callTask);

    // Act
    workflow.execute(ctx);

    // Assert: exactly two step activities, in order inventory -> payment (both call: http).
    assertThat(stepActivityAppIds(ctx, 2)).containsExactly("check-inventory", "charge-payment");

    ArgumentCaptor<Object> output = ArgumentCaptor.forClass(Object.class);
    verify(ctx).complete(output.capture());
    assertThat(((JsonNode) output.getValue()).get("charged").booleanValue()).isTrue();

    // Lifecycle events fire in order: instance.started, per-task started/completed,
    // instance.completed.
    assertThat(adminEventTypes(ctx))
        .containsExactly(
            "io.dws.instance.started",
            "io.dws.task.started",
            "io.dws.task.completed", // checkInventory (call)
            "io.dws.task.started",
            "io.dws.task.completed", // decide (switch)
            "io.dws.task.started",
            "io.dws.task.completed", // chargePayment (call)
            "io.dws.instance.completed");
  }

  @Test
  @SuppressWarnings("unchecked")
  void outOfStockOrderExecutesCheckInventoryThenNotify() throws Exception {
    // Arrange
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"item\":\"widget\"}"));

    JsonNode afterInventory = mapper.readTree("{\"item\":\"widget\",\"inStock\":false}");
    JsonNode afterNotify =
        mapper.readTree("{\"item\":\"widget\",\"inStock\":false,\"notified\":true}");

    Task<JsonNode> callTask = taskWithThenApply();
    when(callTask.await()).thenReturn(afterInventory, afterNotify);
    stubStepActivity(ctx, callTask);

    // Act
    workflow.execute(ctx);

    // Assert: routed through the default branch, inventory -> notification.
    assertThat(stepActivityAppIds(ctx, 2))
        .containsExactly("check-inventory", "notify-out-of-stock");

    ArgumentCaptor<Object> output = ArgumentCaptor.forClass(Object.class);
    verify(ctx).complete(output.capture());
    assertThat(((JsonNode) output.getValue()).get("notified").booleanValue()).isTrue();

    assertThat(adminEventTypes(ctx)).endsWith("io.dws.instance.completed");
  }

  @Test
  @SuppressWarnings("unchecked")
  void taskDispatchFailurePublishesTaskFailedAndInstanceFailed() throws Exception {
    // Arrange: the first call activity throws.
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"item\":\"widget\"}"));

    Task<JsonNode> callTask = taskWithThenApply();
    when(callTask.await()).thenThrow(new RuntimeException("inventory down"));
    stubStepActivity(ctx, callTask);

    // Act + Assert: the original error still propagates.
    assertThatThrownBy(() -> workflow.execute(ctx))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("inventory down");

    List<String> types = adminEventTypes(ctx);
    assertThat(types)
        .containsExactly(
            "io.dws.instance.started",
            "io.dws.task.started",
            "io.dws.task.failed",
            "io.dws.instance.failed");
    // The interpreter never completed the instance.
    verify(ctx, org.mockito.Mockito.never()).complete(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void switchDispatchesThroughTheEvaluateSwitchActivity() throws Exception {
    // Arrange
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"item\":\"widget\"}"));

    Task<JsonNode> callTask = taskWithThenApply();
    when(callTask.await())
        .thenReturn(
            mapper.readTree("{\"item\":\"widget\",\"inStock\":true}"),
            mapper.readTree("{\"charged\":true}"));
    stubStepActivity(ctx, callTask);

    // Act
    workflow.execute(ctx);

    // Assert: the one switch task was resolved by scheduling the activity, not inline.
    ArgumentCaptor<Object> requests = ArgumentCaptor.forClass(Object.class);
    verify(ctx)
        .callActivity(
            eq(EvaluateSwitchActivity.class.getName()),
            requests.capture(),
            any(WorkflowTaskOptions.class),
            eq(FlowOutcome.class));
    assertThat(((EvaluateSwitchRequest) requests.getValue()).taskName()).isEqualTo("decide");
  }

  @Test
  void flowOutcomeRoundTripsThroughJackson() throws Exception {
    for (FlowOutcome outcome :
        List.of(
            FlowOutcome.CONTINUE, new FlowOutcome("END", null), new FlowOutcome(null, "next"))) {
      FlowOutcome back = mapper.readValue(mapper.writeValueAsString(outcome), FlowOutcome.class);
      assertThat(back).isEqualTo(outcome);
    }
  }

  /**
   * A {@code run: shell} task must be dispatched as a multi-app step activity (not rejected as an
   * unsupported type), targeting the kebab-cased task name as its app-id and carrying the current
   * workflow data as the activity input.
   */
  @Test
  @SuppressWarnings("unchecked")
  void runTaskIsDispatchedAsAStepActivityAndCompletes() throws Exception {
    // Arrange: a separate fixture holding a single `run: shell` task.
    Workflow definition = WorkflowReader.readWorkflowFromClasspath("run.yaml");
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "run-workflow",
        "run-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        /* daprClient (unused; activities are mocked) */ null,
        mock(WorkflowTaskOptions.class),
        "pubsub");

    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"seed\":1}"));

    JsonNode afterRun = mapper.readTree("{\"synced\":true}");
    Task<JsonNode> callTask = taskWithThenApply();
    when(callTask.await()).thenReturn(afterRun);
    stubStepActivity(ctx, callTask);

    // Act
    workflow.execute(ctx);

    // Assert: dispatched through the canonical `Run` activity, routed by the kebab-cased task name,
    // with the raw workflow data as the activity input.
    assertThat(stepActivityAppIds(ctx, 1)).containsExactly("sync-inventory");
    assertThat(stepActivityInputs(ctx, 1).get(0)).isEqualTo(mapper.readTree("{\"seed\":1}"));

    ArgumentCaptor<Object> output = ArgumentCaptor.forClass(Object.class);
    verify(ctx).complete(output.capture());
    assertThat(((JsonNode) output.getValue()).get("synced").booleanValue()).isTrue();

    assertThat(adminEventTypes(ctx))
        .containsExactly(
            "io.dws.instance.started",
            "io.dws.task.started",
            "io.dws.task.completed",
            "io.dws.instance.completed");
  }

  /**
   * A migrated step activity must carry the default retry policy (the one the HTTP path uses) in
   * its {@link WorkflowTaskOptions}, so retry behaviour is unchanged by the move to multi-app
   * dispatch.
   */
  @Test
  @SuppressWarnings("unchecked")
  void stepActivityCarriesTheDefaultRetryPolicy() throws Exception {
    WorkflowTaskRetryPolicy policy =
        new WorkflowTaskRetryPolicy(
            3, java.time.Duration.ofSeconds(1), 2.0, java.time.Duration.ofSeconds(10), null);
    Workflow definition = WorkflowReader.readWorkflowFromClasspath("run.yaml");
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "run-workflow",
        "run-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        null,
        new WorkflowTaskOptions(policy),
        "pubsub");

    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{}"));

    Task<JsonNode> callTask = taskWithThenApply();
    when(callTask.await()).thenReturn(mapper.readTree("{\"synced\":true}"));
    stubStepActivity(ctx, callTask);

    workflow.execute(ctx);

    ArgumentCaptor<WorkflowTaskOptions> options =
        ArgumentCaptor.forClass(WorkflowTaskOptions.class);
    verify(ctx).callActivity(eq(StepActivity.NAME), any(), options.capture(), eq(JsonNode.class));
    assertThat(options.getValue().getRetryPolicy()).isSameAs(policy);
    assertThat(options.getValue().getAppId()).isEqualTo("sync-inventory");
  }

  /**
   * A {@code null} step-activity result must leave the data document unchanged (matching the HTTP
   * path, where a 204/empty response returns the request data).
   */
  @Test
  @SuppressWarnings("unchecked")
  void nullStepActivityResultLeavesDataUnchanged() throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromClasspath("run.yaml");
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "run-workflow",
        "run-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        null,
        mock(WorkflowTaskOptions.class),
        "pubsub");

    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"seed\":42}"));

    Task<JsonNode> callTask = taskWithThenApply();
    when(callTask.await()).thenReturn(null);
    stubStepActivity(ctx, callTask);

    workflow.execute(ctx);

    ArgumentCaptor<Object> output = ArgumentCaptor.forClass(Object.class);
    verify(ctx).complete(output.capture());
    assertThat((JsonNode) output.getValue()).isEqualTo(mapper.readTree("{\"seed\":42}"));
  }

  /**
   * {@code call: openapi} is deliberately left on the HTTP service-invocation path ({@link
   * CallServiceActivity}) — its Node image is not an activity worker — so it must schedule no
   * multi-app step activity.
   */
  @Test
  @SuppressWarnings("unchecked")
  void openApiCallStaysOnTheHttpServiceInvocationPath() throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromClasspath("openapi.yaml");
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "openapi-workflow",
        "openapi-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        null,
        mock(WorkflowTaskOptions.class),
        "pubsub");

    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"sku\":\"abc\"}"));

    Task<JsonNode> callTask = taskWithThenApply();
    when(callTask.await()).thenReturn(mapper.readTree("{\"price\":9.99}"));
    when(ctx.callActivity(
            eq(CallServiceActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenReturn(callTask);

    workflow.execute(ctx);

    // Routed via CallServiceActivity (app-id lookup-price, POST /run), and no `Run` activity fired.
    ArgumentCaptor<Object> requests = ArgumentCaptor.forClass(Object.class);
    verify(ctx)
        .callActivity(
            eq(CallServiceActivity.class.getName()),
            requests.capture(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class));
    CallRequest req = (CallRequest) requests.getValue();
    assertThat(req.appId()).isEqualTo("lookup-price");
    assertThat(req.path()).isEqualTo("run");
    verify(ctx, org.mockito.Mockito.never())
        .callActivity(eq(StepActivity.NAME), any(), any(WorkflowTaskOptions.class), any());
  }

  /**
   * Seeds {@link WorkflowSupport} with the {@code dataflow.yaml} fixture and stubs both data-flow
   * phases so the real pipeline logic runs, exactly as {@code stubContext} does for switch/set.
   */
  @SuppressWarnings("unchecked")
  private void stubDataFlow(WorkflowContext ctx) throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromClasspath("dataflow.yaml");
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "dataflow-workflow",
        "dataflow-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        /* daprClient (unused; activities are mocked) */ null,
        mock(WorkflowTaskOptions.class),
        "pubsub");

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

  /**
   * The full Phase 1 pipeline end to end: {@code input.from} narrows what the first step sees,
   * {@code output.as} reshapes what flows on, {@code export.as} writes the workflow context, a
   * later task reads it back through {@code $context}, and a task declaring no data flow passes its
   * document through untouched.
   */
  @Test
  @SuppressWarnings("unchecked")
  void dataFlowPipelineTransformsInputOutputAndCarriesTheExportedContext() throws Exception {
    // Arrange
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    stubDataFlow(ctx);
    when(ctx.getInput(JsonNode.class))
        .thenReturn(mapper.readTree("{\"orderId\":\"o-1\",\"price\":9.5,\"unrelated\":\"x\"}"));

    // chargePayment's step returns a receipt; passThrough's step echoes what it is given.
    Task<JsonNode> callTask = taskWithThenApply();
    when(callTask.await())
        .thenReturn(
            mapper.readTree("{\"receipt\":\"r-77\",\"noise\":1}"),
            mapper.readTree("{\"archived\":true}"));
    stubStepActivity(ctx, callTask);

    // Act
    workflow.execute(ctx);

    // Assert: input.from narrowed the document the first step activity was invoked with (both
    // chargePayment and passThrough are call: http, so they take the activity path).
    List<JsonNode> inputs = stepActivityInputs(ctx, 2);
    JsonNode sentToCharge = inputs.get(0);
    assertThat(sentToCharge.get("orderId").textValue()).isEqualTo("o-1");
    assertThat(sentToCharge.get("amount").doubleValue()).isEqualTo(9.5);
    assertThat(sentToCharge.has("unrelated")).isFalse();

    // recordAudit read the context chargePayment exported, and output.as reshaped it;
    // passThrough declares no data flow, so it received that document unchanged.
    JsonNode sentToPassThrough = inputs.get(1);
    assertThat(sentToPassThrough.get("audited").textValue()).isEqualTo("r-77");

    // The instance completes with the last step's own output — the context is not part of it.
    ArgumentCaptor<Object> output = ArgumentCaptor.forClass(Object.class);
    verify(ctx).complete(output.capture());
    JsonNode completed = (JsonNode) output.getValue();
    assertThat(completed.get("archived").booleanValue()).isTrue();
    assertThat(completed.has("charged")).isFalse();
  }

  /**
   * A schema-validation failure must fail the instance through the ordinary task-failure path, with
   * the offending field named in the message that crosses the activity boundary.
   */
  @Test
  @SuppressWarnings("unchecked")
  void outputSchemaViolationFailsTheInstanceNamingTheField() throws Exception {
    // Arrange: the step returns a numeric receipt, so output.as yields a non-string `reference`.
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    stubDataFlow(ctx);
    when(ctx.getInput(JsonNode.class))
        .thenReturn(mapper.readTree("{\"orderId\":\"o-1\",\"price\":9.5}"));

    Task<JsonNode> callTask = taskWithThenApply();
    when(callTask.await()).thenReturn(mapper.readTree("{\"receipt\":404}"));
    stubStepActivity(ctx, callTask);

    // Act + Assert
    assertThatThrownBy(() -> workflow.execute(ctx))
        .isInstanceOf(DataFlowException.class)
        .hasMessageContaining("chargePayment")
        .hasMessageContaining("output")
        .hasMessageContaining("reference");

    assertThat(adminEventTypes(ctx))
        .containsExactly(
            "io.dws.instance.started",
            "io.dws.task.started",
            "io.dws.task.failed",
            "io.dws.instance.failed");
    verify(ctx, org.mockito.Mockito.never()).complete(any());
  }

  /** {@code taskTypeOf} must label a {@code run} task {@code "run"}, not {@code "unknown"}. */
  @Test
  @SuppressWarnings("unchecked")
  void runTaskTypeIsReportedAsRunInLifecycleEvents() throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromClasspath("run.yaml");
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "run-workflow",
        "run-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        /* daprClient (unused; activities are mocked) */ null,
        mock(WorkflowTaskOptions.class),
        "pubsub");

    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{}"));

    Task<JsonNode> callTask = taskWithThenApply();
    when(callTask.await()).thenReturn(mapper.readTree("{}"));
    stubStepActivity(ctx, callTask);

    workflow.execute(ctx);

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
    assertThat(taskTypes).contains("run");
  }

  /**
   * {@code taskTypeOf} must label a {@code raise} task {@code "raise"}, not {@code "unknown"}. The
   * raise is top-level and therefore uncaught, so the instance fails — which is itself the
   * assertion that a raised error reaches the ordinary task-failure path.
   */
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
            WorkflowFormat.YAML);
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
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{}"));
    when(ctx.callActivity(
            eq(RaiseErrorActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv -> completed(RaiseErrorActivity.apply((RaiseErrorRequest) inv.getArgument(1))));

    assertThatThrownBy(() -> workflow.execute(ctx)).isInstanceOf(RaisedErrorException.class);

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

  // ---- for -----------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void forIteratesTheBodyOncePerElement() throws Exception {
    seedInline(
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
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"items\":[10,20,30]}"));

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    assertThat(output.get("seen")).isEqualTo(mapper.readTree("[10,20,30]"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void forBindsIndexVariableWithDefaultOrCustomName() throws Exception {
    seedInline(
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
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class))
        .thenReturn(mapper.readTree("{\"items\":[\"a\",\"b\",\"c\"]}"));

    workflow.execute(ctx);

    assertThat(completionOutput(ctx).get("idx")).isEqualTo(mapper.readTree("[0,1,2]"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void forStopsWhenWhileBecomesFalse() throws Exception {
    seedInline(
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
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"items\":[1,2,3,4,5]}"));

    workflow.execute(ctx);

    assertThat(completionOutput(ctx).get("seen")).isEqualTo(mapper.readTree("[1,2]"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void forOverEmptyCollectionRunsBodyZeroTimesAndPassesDataThrough() throws Exception {
    seedInline(
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
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"items\":[]}"));

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    assertThat(output.has("seen")).isFalse();
    assertThat(output.get("done").textValue()).isEqualTo("yes");
    assertThat(adminEventTypes(ctx))
        .contains("io.dws.task.started", "io.dws.task.completed", "io.dws.instance.completed");
  }

  @Test
  @SuppressWarnings("unchecked")
  void nonArrayForInFailsTheTaskAndInstance() throws Exception {
    seedInline(
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
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{\"count\":3}"));

    assertThatThrownBy(() -> workflow.execute(ctx))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("loop");
    verify(ctx, org.mockito.Mockito.never()).complete(any());
    assertThat(adminEventTypes(ctx)).contains("io.dws.instance.failed");
  }

  // ---- fork ----------------------------------------------------------------

  /**
   * Stubs {@code ctx.callChildWorkflow(ForkBranchWorkflow.NAME, ...)} so each branch resolves to
   * the {@link Task} the test hands in for that branch name, keyed off the {@link ForkBranchInput}
   * each call is made with. Does not re-run {@link ForkBranchWorkflow}'s real dispatch — that is
   * {@code ForkBranchWorkflowTest}'s job; this only tests {@code dispatchFork}'s own orchestration.
   */
  @SuppressWarnings("unchecked")
  private static void stubForkBranches(WorkflowContext ctx, Map<String, Task<JsonNode>> byBranch) {
    when(ctx.callChildWorkflow(
            org.mockito.ArgumentMatchers.eq(io.dws.orchestrator.workflow.ForkBranchWorkflow.NAME),
            any(),
            any(String.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv -> {
              io.dws.orchestrator.workflow.ForkBranchInput input = inv.getArgument(1);
              Task<JsonNode> task = byBranch.get(input.taskName());
              if (task == null) {
                throw new AssertionError("no stub for branch " + input.taskName());
              }
              return task;
            });
  }

  /** {@code ctx.allOf} resolving to each handle's {@code await()} result, in list order. */
  @SuppressWarnings("unchecked")
  private static void stubAllOf(WorkflowContext ctx) {
    when(ctx.allOf(any(List.class)))
        .thenAnswer(
            inv -> {
              List<Task<Object>> handles = inv.getArgument(0);
              Task<List<Object>> combined = mock(Task.class);
              when(combined.await())
                  .thenAnswer(ignored -> handles.stream().map(Task::await).toList());
              return combined;
            });
  }

  /** {@code ctx.anyOf} resolving to the handle at {@code winningIndex}, regardless of order. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void stubAnyOf(WorkflowContext ctx, int winningIndex) {
    when(ctx.anyOf(any(List.class)))
        .thenAnswer(
            inv -> {
              // Raw types here (rather than List<Task<?>>/Task<Task<?>>) sidestep a wildcard-
              // capture mismatch: each `Task<?>` read from the list captures a distinct, unrelated
              // wildcard, so it cannot be handed straight to a `Task<Task<?>>` stub.
              List<Task> handles = inv.getArgument(0);
              Task combined = mock(Task.class);
              Task winner = handles.get(winningIndex);
              when(combined.await()).thenReturn(winner);
              return combined;
            });
  }

  /**
   * Builds a real {@link io.dapr.durabletask.CompositeTaskFailedException} via reflection: every
   * constructor on that SDK class is package-private to {@code io.dapr.durabletask}, so this test
   * package cannot call {@code new} directly, but {@code ctx.allOf} throws exactly this type when
   * one joined branch fails — matching that exact contract (rather than a generic {@code
   * RuntimeException}) is the point of this test.
   */
  private static io.dapr.durabletask.CompositeTaskFailedException compositeTaskFailedException(
      String message, List<? extends Exception> causes) {
    try {
      java.lang.reflect.Constructor<io.dapr.durabletask.CompositeTaskFailedException> ctor =
          io.dapr.durabletask.CompositeTaskFailedException.class.getDeclaredConstructor(
              String.class, List.class);
      ctor.setAccessible(true);
      return ctor.newInstance(message, causes);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void forkJoinsAllBranchesIntoAnArrayInDeclaredOrder() throws Exception {
    seedInline(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: fork-workflow
          version: '1.0.0'
        do:
          - raiseAlarm:
              fork:
                compete: false
                branches:
                  - callNurse:
                      set:
                        paged: '"nurse"'
                  - callSecurity:
                      set:
                        paged: '"security"'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{}"));

    Task<JsonNode> nurse = taskWithThenApply();
    when(nurse.await()).thenReturn(mapper.readTree("{\"paged\":\"nurse\"}"));
    Task<JsonNode> security = taskWithThenApply();
    when(security.await()).thenReturn(mapper.readTree("{\"paged\":\"security\"}"));
    stubForkBranches(ctx, Map.of("callNurse", nurse, "callSecurity", security));
    stubAllOf(ctx);

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    assertThat(output.isArray()).isTrue();
    assertThat(output.get(0).get("paged").textValue()).isEqualTo("nurse");
    assertThat(output.get(1).get("paged").textValue()).isEqualTo("security");
  }

  @Test
  @SuppressWarnings("unchecked")
  void forkCompeteReturnsTheWinningBranchOnly() throws Exception {
    seedInline(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: fork-workflow
          version: '1.0.0'
        do:
          - raiseAlarm:
              fork:
                compete: true
                branches:
                  - callNurse:
                      set:
                        paged: '"nurse"'
                  - callSecurity:
                      set:
                        paged: '"security"'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{}"));

    Task<JsonNode> nurse = taskWithThenApply();
    when(nurse.await()).thenReturn(mapper.readTree("{\"paged\":\"nurse\"}"));
    Task<JsonNode> security = taskWithThenApply();
    when(security.await()).thenReturn(mapper.readTree("{\"paged\":\"security\"}"));
    stubForkBranches(ctx, Map.of("callNurse", nurse, "callSecurity", security));
    stubAnyOf(ctx, 0); // callNurse's handle is index 0 (declared first)

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    assertThat(output.isArray()).isFalse();
    assertThat(output.get("paged").textValue()).isEqualTo("nurse");
  }

  @Test
  @SuppressWarnings("unchecked")
  void forkJoinFailurePropagatesAndIsCaughtByEnclosingTry() throws Exception {
    seedInline(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: fork-workflow
          version: '1.0.0'
        do:
          - guarded:
              try:
                - raiseAlarm:
                    fork:
                      compete: false
                      branches:
                        - callNurse:
                            set:
                              paged: '"nurse"'
                        - callSecurity:
                            raise:
                              error:
                                type: https://example.com/errors/paging-down
                                status: 500
                                title: Down
                                detail: paging system down
              catch:
                do:
                  - recovered:
                      set:
                        paged: '"fallback"'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{}"));

    Task<JsonNode> nurse = taskWithThenApply();
    when(nurse.await()).thenReturn(mapper.readTree("{\"paged\":\"nurse\"}"));
    Task<JsonNode> security = taskWithThenApply();
    when(security.await())
        .thenThrow(
            compositeTaskFailedException(
                "one branch failed", List.of(new RuntimeException("paging system down"))));
    stubForkBranches(ctx, Map.of("callNurse", nurse, "callSecurity", security));
    // allOf itself throws the composite failure, matching the real SDK contract.
    when(ctx.allOf(any(List.class)))
        .thenAnswer(
            inv -> {
              throw compositeTaskFailedException(
                  "one branch failed", List.of(new RuntimeException("paging system down")));
            });
    when(ctx.callActivity(
            eq(io.dws.orchestrator.workflow.activity.CatchDecisionActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(io.dws.orchestrator.workflow.activity.CatchDecision.class)))
        .thenAnswer(
            inv ->
                completed(
                    new io.dws.orchestrator.workflow.activity.CatchDecision(
                        true, false, 0L, mapper.readTree("{\"status\":500}"), "error")));
    when(ctx.callActivity(
            eq(EvaluateSetActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv -> completed(EvaluateSetActivity.apply((EvaluateSetRequest) inv.getArgument(1))));

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    assertThat(output.get("paged").textValue()).isEqualTo("fallback");
  }
}
