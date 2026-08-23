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

class EvaluateSetActivityTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void reservedSecretsBindingIsAvailableToSetAndCannotBeOverriddenByTaskVariables()
      throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: secret-workflow
          version: '1.0.0'
        use:
          secrets: [API_TOKEN]
        do:
          - reveal:
              set:
                value: '${ $secrets.API_TOKEN }'
        """,
        Map.of("API_TOKEN", mapper.getNodeFactory().textNode("token")));

    JsonNode result =
        EvaluateSetActivity.apply(
            new EvaluateSetRequest(
                "reveal",
                mapper.createObjectNode(),
                Map.of("secrets", mapper.getNodeFactory().textNode("override"))));

    assertThat(result.path("value").textValue()).isEqualTo("token");
  }

  @Test
  void noSecretWorkflowRetainsItsExistingTaskVariableScope() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: plain-workflow
          version: '1.0.0'
        do:
          - copy:
              set:
                value: '$caught.message'
        """,
        Map.of());

    JsonNode result =
        EvaluateSetActivity.apply(
            new EvaluateSetRequest(
                "copy",
                mapper.createObjectNode(),
                Map.of("caught", mapper.readTree("{\"message\":\"kept\"}"))));

    assertThat(result.path("value").textValue()).isEqualTo("kept");
  }

  private void seed(String yaml, Map<String, JsonNode> secrets) throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromString(yaml, WorkflowFormat.YAML);
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "secret-workflow",
        "secret-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        null,
        mock(WorkflowTaskOptions.class),
        "pubsub",
        secrets);
  }
}
