package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.durabletask.Task;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.loader.DefinitionLoader;
import io.dws.orchestrator.registry.DefinitionRegistry;
import io.dws.orchestrator.workflow.activity.CallServiceActivity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives the interpreter's program-counter loop directly against a mocked
 * {@link WorkflowContext}, exercising the order-workflow example end to end:
 * checkInventory (CALL) -> decide (SWITCH on .inStock) -> chargePayment | notifyOutOfStock -> END.
 */
class InterpreterWorkflowIntegrationTest {

  private static final String ORDER_WORKFLOW = """
      {
        "document": { "name": "order-workflow", "version": "1.0.0" },
        "start": "checkInventory",
        "tasks": {
          "checkInventory":  { "type": "call", "service": "inventory-service", "then": "decide" },
          "decide": {
            "type": "switch",
            "cases": [ { "when": "${ .inStock }", "then": "chargePayment" } ],
            "default": "notifyOutOfStock"
          },
          "chargePayment":    { "type": "call", "service": "payment-service", "then": "done" },
          "notifyOutOfStock": { "type": "call", "service": "notification-service", "then": "failed" },
          "done":   { "type": "end" },
          "failed": { "type": "end" }
        }
      }
      """;

  private final ObjectMapper mapper = new ObjectMapper();
  private final InterpreterWorkflow workflow = new InterpreterWorkflow();

  @BeforeEach
  void seedSupport() throws Exception {
    DefinitionRegistry registry = new DefinitionRegistry();
    registry.register(new DefinitionLoader(mapper).parse(ORDER_WORKFLOW));
    WorkflowSupport.init(registry, new JqEvaluator(mapper), mapper,
        /* daprClient (unused; activities are mocked) */ null,
        mock(WorkflowTaskOptions.class), "pubsub");
  }

  @Test
  @SuppressWarnings("unchecked")
  void inStockOrderFollowsChargePaymentBranch() throws Exception {
    // Arrange
    WorkflowContext ctx = mock(WorkflowContext.class);
    WorkflowInput input = new WorkflowInput("order-workflow", "1.0.0", mapper.readTree("{\"item\":\"widget\"}"));
    when(ctx.getInput(WorkflowInput.class)).thenReturn(input);

    JsonNode afterInventory = mapper.readTree("{\"item\":\"widget\",\"inStock\":true}");
    JsonNode afterCharge = mapper.readTree("{\"item\":\"widget\",\"inStock\":true,\"charged\":true}");

    Task<JsonNode> callTask = mock(Task.class);
    when(callTask.await()).thenReturn(afterInventory, afterCharge);
    when(ctx.callActivity(eq(CallServiceActivity.class.getName()), any(), any(WorkflowTaskOptions.class), eq(JsonNode.class)))
        .thenReturn(callTask);

    // Act
    workflow.execute(ctx);

    // Assert: two service calls (inventory, payment) and completion with the charged document.
    verify(ctx, times(2)).callActivity(eq(CallServiceActivity.class.getName()), any(), any(WorkflowTaskOptions.class), eq(JsonNode.class));

    ArgumentCaptor<Object> output = ArgumentCaptor.forClass(Object.class);
    verify(ctx).complete(output.capture());
    assertThat(((JsonNode) output.getValue()).get("charged").booleanValue()).isTrue();
  }

  @Test
  @SuppressWarnings("unchecked")
  void outOfStockOrderFollowsDefaultBranch() throws Exception {
    // Arrange
    WorkflowContext ctx = mock(WorkflowContext.class);
    WorkflowInput input = new WorkflowInput("order-workflow", "1.0.0", mapper.readTree("{\"item\":\"widget\"}"));
    when(ctx.getInput(WorkflowInput.class)).thenReturn(input);

    JsonNode afterInventory = mapper.readTree("{\"item\":\"widget\",\"inStock\":false}");
    JsonNode afterNotify = mapper.readTree("{\"item\":\"widget\",\"inStock\":false,\"notified\":true}");

    Task<JsonNode> callTask = mock(Task.class);
    when(callTask.await()).thenReturn(afterInventory, afterNotify);
    when(ctx.callActivity(eq(CallServiceActivity.class.getName()), any(), any(WorkflowTaskOptions.class), eq(JsonNode.class)))
        .thenReturn(callTask);

    // Act
    workflow.execute(ctx);

    // Assert: routed through notifyOutOfStock and completed with the notified document.
    ArgumentCaptor<Object> output = ArgumentCaptor.forClass(Object.class);
    verify(ctx).complete(output.capture());
    assertThat(((JsonNode) output.getValue()).get("notified").booleanValue()).isTrue();
  }
}
