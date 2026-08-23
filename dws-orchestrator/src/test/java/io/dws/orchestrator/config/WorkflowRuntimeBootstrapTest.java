package io.dws.orchestrator.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import org.junit.jupiter.api.Test;

class WorkflowRuntimeBootstrapTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void resolvesOnlyDeclaredProjectedSecretsAndIgnoresAbsentValues() throws Exception {
    Workflow definition =
        WorkflowReader.readWorkflowFromString(
            """
            document:
              dsl: 1.0.0
              namespace: examples
              name: secret-workflow
              version: '1.0.0'
            use:
              secrets: [API_TOKEN, OPTIONAL]
            do:
              - done:
                  set:
                    result: '"ok"'
            """,
            WorkflowFormat.YAML);

    var secrets =
        WorkflowRuntimeBootstrap.secretScope(
            definition,
            mapper,
            name ->
                switch (name) {
                  case "SECRET_API_TOKEN" -> "token";
                  case "SECRET_UNDECLARED" -> "must-not-be-read";
                  default -> null;
                });

    assertThat(secrets).containsOnlyKeys("API_TOKEN");
    assertThat(secrets.get("API_TOKEN").textValue()).isEqualTo("token");
  }
}
