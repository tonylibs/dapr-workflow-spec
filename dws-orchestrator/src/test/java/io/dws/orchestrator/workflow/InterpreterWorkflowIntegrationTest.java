package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.durabletask.Task;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.activity.AdminEventActivity;
import io.dws.orchestrator.workflow.activity.AdminEventRequest;
import io.dws.orchestrator.workflow.activity.CallRequest;
import io.dws.orchestrator.workflow.activity.CallServiceActivity;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Parses the fixture DSL 1.0 {@code order.yaml} with the Open Workflow Specification SDK and drives the
 * interpreter's program-counter loop against a mocked {@link WorkflowContext}, asserting the task
 * execution order for both switch branches
 * (checkInventory -> switch .inStock -> chargePayment | notifyOutOfStock -> end) and the
 * lifecycle events scheduled through {@link AdminEventActivity}.
 */
class InterpreterWorkflowIntegrationTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final InterpreterWorkflow workflow = new InterpreterWorkflow();

  @BeforeEach
  void seedSupport() throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromClasspath("order.yaml");
    WorkflowSupport.init(definition, definition.getDocument().getName(),
        "order-workflow", "order-workflow@v1",
        new JqEvaluator(mapper), mapper,
        /* daprClient (unused; activities are mocked) */ null,
        mock(WorkflowTaskOptions.class), "pubsub");
  }

  /** Stubs admin-event activity scheduling (Void) and the replay-safe context values. */
  @SuppressWarnings("unchecked")
  private Task<Void> stubContext(WorkflowContext ctx) {
    when(ctx.getInstanceId()).thenReturn("inst-1");
    when(ctx.getCurrentInstant()).thenReturn(Instant.parse("2026-07-24T00:00:00Z"));
    Task<Void> adminTask = mock(Task.class);
    when(adminTask.await()).thenReturn(null);
    when(ctx.callActivity(eq(AdminEventActivity.class.getName()), any(), any(WorkflowTaskOptions.class), eq(Void.class)))
        .thenReturn(adminTask);
    return adminTask;
  }

  private static List<String> adminEventTypes(WorkflowContext ctx) {
    ArgumentCaptor<Object> reqs = ArgumentCaptor.forClass(Object.class);
    verify(ctx, org.mockito.Mockito.atLeastOnce()).callActivity(
        eq(AdminEventActivity.class.getName()), reqs.capture(), any(WorkflowTaskOptions.class), eq(Void.class));
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
    JsonNode afterCharge = mapper.readTree("{\"item\":\"widget\",\"inStock\":true,\"charged\":true}");

    Task<JsonNode> callTask = mock(Task.class);
    when(callTask.await()).thenReturn(afterInventory, afterCharge);
    when(ctx.callActivity(eq(CallServiceActivity.class.getName()), any(), any(WorkflowTaskOptions.class), eq(JsonNode.class)))
        .thenReturn(callTask);

    // Act
    workflow.execute(ctx);

    // Assert: exactly two service invocations, in order inventory -> payment.
    ArgumentCaptor<Object> requests = ArgumentCaptor.forClass(Object.class);
    verify(ctx, times(2)).callActivity(eq(CallServiceActivity.class.getName()), requests.capture(), any(WorkflowTaskOptions.class), eq(JsonNode.class));
    List<String> appIds = requests.getAllValues().stream().map(r -> ((CallRequest) r).appId()).toList();
    assertThat(appIds).containsExactly("check-inventory", "charge-payment");

    ArgumentCaptor<Object> output = ArgumentCaptor.forClass(Object.class);
    verify(ctx).complete(output.capture());
    assertThat(((JsonNode) output.getValue()).get("charged").booleanValue()).isTrue();

    // Lifecycle events fire in order: instance.started, per-task started/completed, instance.completed.
    assertThat(adminEventTypes(ctx)).containsExactly(
        "io.dws.instance.started",
        "io.dws.task.started", "io.dws.task.completed",   // checkInventory (call)
        "io.dws.task.started", "io.dws.task.completed",   // decide (switch)
        "io.dws.task.started", "io.dws.task.completed",   // chargePayment (call)
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
    JsonNode afterNotify = mapper.readTree("{\"item\":\"widget\",\"inStock\":false,\"notified\":true}");

    Task<JsonNode> callTask = mock(Task.class);
    when(callTask.await()).thenReturn(afterInventory, afterNotify);
    when(ctx.callActivity(eq(CallServiceActivity.class.getName()), any(), any(WorkflowTaskOptions.class), eq(JsonNode.class)))
        .thenReturn(callTask);

    // Act
    workflow.execute(ctx);

    // Assert: routed through the default branch, inventory -> notification.
    ArgumentCaptor<Object> requests = ArgumentCaptor.forClass(Object.class);
    verify(ctx, times(2)).callActivity(eq(CallServiceActivity.class.getName()), requests.capture(), any(WorkflowTaskOptions.class), eq(JsonNode.class));
    List<String> appIds = requests.getAllValues().stream().map(r -> ((CallRequest) r).appId()).toList();
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

    Task<JsonNode> callTask = mock(Task.class);
    when(callTask.await()).thenThrow(new RuntimeException("inventory down"));
    when(ctx.callActivity(eq(CallServiceActivity.class.getName()), any(), any(WorkflowTaskOptions.class), eq(JsonNode.class)))
        .thenReturn(callTask);

    // Act + Assert: the original error still propagates.
    assertThatThrownBy(() -> workflow.execute(ctx))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("inventory down");

    List<String> types = adminEventTypes(ctx);
    assertThat(types).containsExactly(
        "io.dws.instance.started",
        "io.dws.task.started",
        "io.dws.task.failed",
        "io.dws.instance.failed");
    // The interpreter never completed the instance.
    verify(ctx, org.mockito.Mockito.never()).complete(any());
  }
}
