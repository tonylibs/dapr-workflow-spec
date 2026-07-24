package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.durabletask.Task;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.activity.CallRequest;
import io.dws.orchestrator.workflow.activity.CallServiceActivity;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Parses the fixture DSL 1.0 {@code order.yaml} with the Open Workflow Specification SDK and drives the
 * interpreter's program-counter loop against a mocked {@link WorkflowContext}, asserting the task
 * execution order for both switch branches:
 * checkInventory -> switch .inStock -> chargePayment | notifyOutOfStock -> end.
 */
class InterpreterWorkflowIntegrationTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final InterpreterWorkflow workflow = new InterpreterWorkflow();

  @BeforeEach
  void seedSupport() throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromClasspath("order.yaml");
    WorkflowSupport.init(definition, definition.getDocument().getName(),
        new JqEvaluator(mapper), mapper,
        /* daprClient (unused; activities are mocked) */ null,
        mock(WorkflowTaskOptions.class), "pubsub");
  }

  @Test
  @SuppressWarnings("unchecked")
  void inStockOrderExecutesCheckInventoryThenChargePayment() throws Exception {
    // Arrange
    WorkflowContext ctx = mock(WorkflowContext.class);
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
  }

  @Test
  @SuppressWarnings("unchecked")
  void outOfStockOrderExecutesCheckInventoryThenNotify() throws Exception {
    // Arrange
    WorkflowContext ctx = mock(WorkflowContext.class);
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
  }
}
