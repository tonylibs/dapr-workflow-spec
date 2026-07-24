package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.workflows.WorkflowContext;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.activity.AdminEventRequest;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import io.dapr.workflows.WorkflowTaskOptions;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminEventBuilder}: the workflow/version split from the definition key and
 * the replay-safe (context-sourced) timestamps and monotonic ids.
 */
class AdminEventBuilderTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void seedSupport() throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromClasspath("order.yaml");
    WorkflowSupport.init(definition, definition.getDocument().getName(),
        "order-workflow", "order-workflow@v3",
        new JqEvaluator(mapper), mapper, null,
        mock(WorkflowTaskOptions.class), "pubsub");
  }

  @Test
  @DisplayName("versionFromKey splits the version off the definition key")
  void versionFromKey_splitsVersion() {
    assertThat(AdminEventBuilder.versionFromKey("order-workflow@v3")).isEqualTo("v3");
    assertThat(AdminEventBuilder.versionFromKey("no-version")).isEqualTo("no-version");
  }

  @Test
  @DisplayName("envelopes use context instant for time and a monotonic per-instance id")
  void deterministicTimeAndId() {
    WorkflowContext ctx = mock(WorkflowContext.class);
    when(ctx.getInstanceId()).thenReturn("inst-42");
    when(ctx.getCurrentInstant()).thenReturn(Instant.parse("2026-07-24T00:00:00Z"));

    AdminEventBuilder builder = AdminEventBuilder.forContext(ctx);
    AdminEventRequest started = builder.instanceStarted();
    AdminEventRequest task = builder.taskStarted("checkInventory", "call");

    assertThat(started.pubsub()).isEqualTo("pubsub");
    assertThat(started.topic()).isEqualTo("dws.events");
    assertThat(started.data().get("time").asText()).isEqualTo("2026-07-24T00:00:00Z");
    assertThat(started.data().get("id").asText()).isEqualTo("inst-42-1");
    assertThat(started.data().get("source").asText()).isEqualTo("dws-orchestrator/order-workflow");
    assertThat(started.data().get("data").get("version").asText()).isEqualTo("v3");
    assertThat(started.data().get("data").get("workflow").asText()).isEqualTo("order-workflow");

    // The id increments deterministically with each event.
    assertThat(task.data().get("id").asText()).isEqualTo("inst-42-2");
    assertThat(task.data().get("data").get("taskType").asText()).isEqualTo("call");
  }
}
