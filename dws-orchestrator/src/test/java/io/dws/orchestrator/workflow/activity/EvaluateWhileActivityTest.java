package io.dws.orchestrator.workflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
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

/** Drives {@link EvaluateWhileActivity} directly, mirroring {@link EvaluateForActivityTest}. */
class EvaluateWhileActivityTest {

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

  @Test
  void truthyResultIsTrue() throws Exception {
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
                in: .items
              while: .count > 0
              do:
                - noop:
                    set:
                      done: '"yes"'
        """);
    JsonNode data = mapper.readTree("{\"count\":3}");

    assertThat(EvaluateWhileActivity.apply(new EvaluateWhileRequest("loop", data, Map.of())))
        .isTrue();
  }

  @Test
  void falsyResultsAreFalse() throws Exception {
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
                in: .items
              while: .flag
              do:
                - noop:
                    set:
                      done: '"yes"'
        """);
    assertThat(
            EvaluateWhileActivity.apply(
                new EvaluateWhileRequest("loop", mapper.readTree("{\"flag\":false}"), Map.of())))
        .isFalse();
    assertThat(
            EvaluateWhileActivity.apply(
                new EvaluateWhileRequest("loop", mapper.readTree("{\"flag\":null}"), Map.of())))
        .isFalse();
    assertThat(
            EvaluateWhileActivity.apply(
                new EvaluateWhileRequest("loop", mapper.createObjectNode(), Map.of())))
        .isFalse();
  }

  @Test
  void variableIsBoundIntoWhile() throws Exception {
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
                each: item
                in: .items
              while: '$item < 3'
              do:
                - noop:
                    set:
                      done: '"yes"'
        """);
    JsonNode item = mapper.readTree("2");

    assertThat(
            EvaluateWhileActivity.apply(
                new EvaluateWhileRequest("loop", mapper.createObjectNode(), Map.of("item", item))))
        .isTrue();

    JsonNode bigItem = mapper.readTree("5");
    assertThat(
            EvaluateWhileActivity.apply(
                new EvaluateWhileRequest(
                    "loop", mapper.createObjectNode(), Map.of("item", bigItem))))
        .isFalse();
  }
}
