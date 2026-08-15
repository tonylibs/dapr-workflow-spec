package io.dws.orchestrator.workflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link EvaluateForActivity} directly. Seeds {@link WorkflowSupport} from an inline
 * definition and asserts on the resolved collection, mirroring {@link RaiseErrorActivityTest}'s
 * style.
 */
class EvaluateForActivityTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private void seed(String yaml) throws Exception {
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

  private EvaluateForRequest request(String taskName, JsonNode data) {
    return new EvaluateForRequest(taskName, data, Map.of());
  }

  @Test
  void bareJqExpressionYieldsTheArray() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: for-workflow
          version: '1.0.0'
        do:
          - loop:
              for:
                each: pet
                in: .pets
              do:
                - noop:
                    set:
                      done: '"yes"'
        """);

    JsonNode data = mapper.readTree("{\"pets\":[{\"id\":1},{\"id\":2}]}");
    JsonNode result = EvaluateForActivity.apply(request("loop", data));

    assertThat(result.isArray()).isTrue();
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get(0).get("id").intValue()).isEqualTo(1);
  }

  @Test
  void wrappedExpressionYieldsTheArray() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: for-workflow
          version: '1.0.0'
        do:
          - loop:
              for:
                each: pet
                in: '${ .pets }'
              do:
                - noop:
                    set:
                      done: '"yes"'
        """);

    JsonNode data = mapper.readTree("{\"pets\":[1,2,3]}");
    JsonNode result = EvaluateForActivity.apply(request("loop", data));

    assertThat(result.isArray()).isTrue();
    assertThat(result.size()).isEqualTo(3);
  }

  @Test
  void scopeVariableIsBoundIntoInExpression() throws Exception {
    seed(
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
                in: $sample
              do:
                - noop:
                    set:
                      done: '"yes"'
        """);

    JsonNode data = mapper.createObjectNode();
    JsonNode sample = mapper.readTree("[10,20,30]");
    EvaluateForRequest req = new EvaluateForRequest("loop", data, Map.of("sample", sample));

    JsonNode result = EvaluateForActivity.apply(req);

    assertThat(result.isArray()).isTrue();
    assertThat(result.size()).isEqualTo(3);
    assertThat(result.get(1).intValue()).isEqualTo(20);
  }

  @Test
  void nonArrayResultIsRejected() throws Exception {
    seed(
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

    JsonNode data = mapper.readTree("{\"count\":3}");

    assertThatThrownBy(() -> EvaluateForActivity.apply(request("loop", data)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("loop")
        .hasMessageContaining("array");
  }
}
