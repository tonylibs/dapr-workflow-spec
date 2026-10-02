package io.dws.step.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.dapr.workflows.WorkflowActivityContext;
import io.dws.step.config.SingleNodeDefinition;
import io.dws.step.failure.StepConfigException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StepActivityTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @ParameterizedTest
  @EnumSource(TaskKind.class)
  void routesToTheHandlerRegisteredForTheNodesKindAndNoOther(TaskKind kind) throws Exception {
    List<TaskKind> invoked = new ArrayList<>();
    List<TaskHandler> handlers =
        Arrays.stream(TaskKind.values()).<TaskHandler>map(k -> recording(k, invoked)).toList();
    StepActivity activity =
        new StepActivity(definitionFor(kind), new TaskHandlerRegistry(handlers));

    JsonNode result = activity.run(context(input()));

    assertThat(invoked).containsExactly(kind);
    assertThat(result).isEqualTo(TextNode.valueOf("handled-" + kind.key()));
  }

  @ParameterizedTest
  @EnumSource(TaskKind.class)
  void everyDefaultHandlerFailsWithNonRetryableNotImplementedConfigFailure(TaskKind kind) {
    StepActivity activity = new StepActivity(definitionFor(kind), TaskHandlerRegistry.defaults());

    assertThatThrownBy(() -> activity.run(context(input())))
        .isInstanceOfSatisfying(StepConfigException.class, e -> assertThat(e.retryable()).isFalse())
        .hasMessage(
            "step 'node-1' config failure: task kind '" + kind.key() + "' is not implemented yet");
  }

  @Test
  void handlerReceivesTheEnvelopeUnchanged() throws Exception {
    StepInput input = input();
    List<StepInput> seen = new ArrayList<>();
    TaskHandler capture =
        new TaskHandler() {
          @Override
          public TaskKind kind() {
            return TaskKind.SET;
          }

          @Override
          public JsonNode handle(SingleNodeDefinition definition, StepInput in) {
            seen.add(in);
            return in.data();
          }
        };
    StepActivity activity =
        new StepActivity(definitionFor(TaskKind.SET), new TaskHandlerRegistry(List.of(capture)));

    JsonNode result = activity.run(context(input));

    assertThat(seen).containsExactly(input);
    assertThat(result).isEqualTo(input.data());
  }

  @Test
  void missingInputIsAConfigFailure() {
    StepActivity activity =
        new StepActivity(definitionFor(TaskKind.SET), TaskHandlerRegistry.defaults());

    assertThatThrownBy(() -> activity.run(context(null)))
        .isInstanceOf(StepConfigException.class)
        .hasMessage("step 'node-1' config failure: activity input is missing");
  }

  @Test
  void kindWithoutARegisteredHandlerIsAConfigFailure() {
    StepActivity activity =
        new StepActivity(definitionFor(TaskKind.RUN), new TaskHandlerRegistry(List.of()));

    assertThatThrownBy(() -> activity.run(context(input())))
        .isInstanceOf(StepConfigException.class)
        .hasMessage("step 'node-1' config failure: no handler registered for task kind 'run'");
  }

  @Test
  void defaultRegistryCoversEveryKind() {
    TaskHandlerRegistry registry = TaskHandlerRegistry.defaults();

    for (TaskKind kind : TaskKind.values()) {
      assertThat(registry.handlerFor(kind).kind()).isEqualTo(kind);
    }
  }

  @Test
  void duplicateHandlersAreRejected() {
    List<TaskHandler> twice =
        List.of(recording(TaskKind.SET, new ArrayList<>()), recording(TaskKind.SET, List.of()));

    assertThatThrownBy(() -> new TaskHandlerRegistry(twice))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private TaskHandler recording(TaskKind kind, List<TaskKind> invoked) {
    return new TaskHandler() {
      @Override
      public TaskKind kind() {
        return kind;
      }

      @Override
      public JsonNode handle(SingleNodeDefinition definition, StepInput input) {
        invoked.add(kind);
        return TextNode.valueOf("handled-" + kind.key());
      }
    };
  }

  private SingleNodeDefinition definitionFor(TaskKind kind) {
    return new SingleNodeDefinition(
        "order", "order@v1", "node-1", mapper.createObjectNode().put(kind.key(), "x"), null);
  }

  private StepInput input() throws Exception {
    return new StepInput(
        mapper.readTree("{\"a\":1}"),
        Map.of("error", mapper.readTree("{\"status\":502}")),
        "root-1",
        "3");
  }

  private WorkflowActivityContext context(StepInput input) {
    WorkflowActivityContext context = mock(WorkflowActivityContext.class);
    when(context.getInput(StepInput.class)).thenReturn(input);
    return context;
  }
}
