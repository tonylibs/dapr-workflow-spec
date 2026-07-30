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
import io.dws.orchestrator.dataflow.DataFlowException;
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
import io.dws.orchestrator.workflow.activity.EvaluateSetActivity;
import io.dws.orchestrator.workflow.activity.EvaluateSetRequest;
import io.dws.orchestrator.workflow.activity.EvaluateSwitchActivity;
import io.dws.orchestrator.workflow.activity.EvaluateSwitchRequest;
import io.dws.orchestrator.workflow.activity.FlowOutcome;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import java.time.Instant;
import java.util.List;
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
    return adminTask;
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
    when(ctx.callActivity(
            eq(CallServiceActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenReturn(callTask);

    // Act
    workflow.execute(ctx);

    // Assert: exactly two service invocations, in order inventory -> payment.
    ArgumentCaptor<Object> requests = ArgumentCaptor.forClass(Object.class);
    verify(ctx, times(2))
        .callActivity(
            eq(CallServiceActivity.class.getName()),
            requests.capture(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class));
    List<String> appIds =
        requests.getAllValues().stream().map(r -> ((CallRequest) r).appId()).toList();
    assertThat(appIds).containsExactly("check-inventory", "charge-payment");

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
    when(ctx.callActivity(
            eq(CallServiceActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenReturn(callTask);

    // Act
    workflow.execute(ctx);

    // Assert: routed through the default branch, inventory -> notification.
    ArgumentCaptor<Object> requests = ArgumentCaptor.forClass(Object.class);
    verify(ctx, times(2))
        .callActivity(
            eq(CallServiceActivity.class.getName()),
            requests.capture(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class));
    List<String> appIds =
        requests.getAllValues().stream().map(r -> ((CallRequest) r).appId()).toList();
    assertThat(appIds).containsExactly("check-inventory", "notify-out-of-stock");

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
    when(ctx.callActivity(
            eq(CallServiceActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenReturn(callTask);

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
    when(ctx.callActivity(
            eq(CallServiceActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenReturn(callTask);

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
   * Regression guard for the {@code run} task fall-through: before the fix, {@code dispatch()} had
   * no branch for {@code Task.getRunTask()} and threw {@code IllegalStateException("task '...' has
   * an unsupported type")} instead of invoking the step service the controller deployed for it.
   */
  @Test
  @SuppressWarnings("unchecked")
  void runTaskIsDispatchedAsServiceInvocationAndCompletes() throws Exception {
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
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{}"));

    JsonNode afterRun = mapper.readTree("{\"synced\":true}");
    Task<JsonNode> callTask = taskWithThenApply();
    when(callTask.await()).thenReturn(afterRun);
    when(ctx.callActivity(
            eq(CallServiceActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenReturn(callTask);

    // Act
    workflow.execute(ctx);

    // Assert: the run task is dispatched through the same activity/contract as call, routed by
    // the kebab-cased task name.
    ArgumentCaptor<Object> requests = ArgumentCaptor.forClass(Object.class);
    verify(ctx, times(1))
        .callActivity(
            eq(CallServiceActivity.class.getName()),
            requests.capture(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class));
    CallRequest req = (CallRequest) requests.getValue();
    assertThat(req.appId()).isEqualTo("sync-inventory");
    assertThat(req.path()).isEqualTo("run");

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
    when(ctx.callActivity(
            eq(CallServiceActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenReturn(callTask);

    // Act
    workflow.execute(ctx);

    // Assert: input.from narrowed the document the first step service was invoked with.
    ArgumentCaptor<Object> requests = ArgumentCaptor.forClass(Object.class);
    verify(ctx, times(2))
        .callActivity(
            eq(CallServiceActivity.class.getName()),
            requests.capture(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class));
    JsonNode sentToCharge = ((CallRequest) requests.getAllValues().get(0)).data();
    assertThat(sentToCharge.get("orderId").textValue()).isEqualTo("o-1");
    assertThat(sentToCharge.get("amount").doubleValue()).isEqualTo(9.5);
    assertThat(sentToCharge.has("unrelated")).isFalse();

    // recordAudit read the context chargePayment exported, and output.as reshaped it;
    // passThrough declares no data flow, so it received that document unchanged.
    JsonNode sentToPassThrough = ((CallRequest) requests.getAllValues().get(1)).data();
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
    when(ctx.callActivity(
            eq(CallServiceActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenReturn(callTask);

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
    when(ctx.callActivity(
            eq(CallServiceActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenReturn(callTask);

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
}
