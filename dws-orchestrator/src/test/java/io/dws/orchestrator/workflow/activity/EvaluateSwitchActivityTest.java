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

class EvaluateSwitchActivityTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void secretsBindingSelectsTheMatchingSwitchCase() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: secret-workflow
          version: '1.0.0'
        use:
          secrets: [FLAG]
        do:
          - choose:
              switch:
                - enabled:
                    when: '${ $secrets.FLAG == "enabled" }'
                    then: enabled-target
                - fallback:
                    then: fallback-target
        """,
        Map.of("FLAG", mapper.getNodeFactory().textNode("enabled")));

    assertThat(
            EvaluateSwitchActivity.evaluate(
                new EvaluateSwitchRequest("choose", mapper.createObjectNode(), Map.of())))
        .isEqualTo(new FlowOutcome(null, "enabled-target"));
  }

  @Test
  void noSecretWorkflowStillEvaluatesDataScopedConditions() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: plain-workflow
          version: '1.0.0'
        do:
          - choose:
              switch:
                - matching:
                    when: '${ .enabled }'
                    then: enabled-target
                - fallback:
                    then: fallback-target
        """,
        Map.of());

    assertThat(
            EvaluateSwitchActivity.evaluate(
                new EvaluateSwitchRequest(
                    "choose", mapper.readTree("{\"enabled\":true}"), Map.of())))
        .isEqualTo(new FlowOutcome(null, "enabled-target"));
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
