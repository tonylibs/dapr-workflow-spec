package io.dws.controller.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.ImageCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cross-package contract guard (root CLAUDE.md "contract-change etiquette"): the controller stamps
 * environment variables on the orchestrator Deployment, and {@code dws-orchestrator} reads them
 * through {@code application.yaml}. A name that drifts on either side is not a compile error — it
 * surfaces at runtime as the orchestrator looking in the wrong place ("configuration store
 * dws-definitions not found"). This reads the sibling module's file so drift fails here.
 */
class OrchestratorEnvContractTest {

  private static final Path ORCHESTRATOR_CONFIG =
      Path.of("..", "dws-orchestrator", "src", "main", "resources", "application.yaml");

  private static final ImageCatalog IMAGES =
      new ImageCatalog("a:1", "b:1", "c:1", "d:1", "e:1", "f:1", "g:1", "h:1", "orchestrator:1");

  private static final String WORKFLOW =
      """
      document:
        dsl: '1.0.0'
        namespace: test
        name: order
        version: '1.0.0'
      do:
        - noop:
            set:
              done: true
      """;

  @Test
  @DisplayName("every non-secret env var the controller stamps is one the orchestrator reads")
  void stampedEnvVarsAreReadByTheOrchestrator() throws IOException {
    assumeTrue(
        Files.isRegularFile(ORCHESTRATOR_CONFIG),
        "dws-orchestrator sources not alongside dws-controller; contract not checkable here");
    String orchestratorConfig = Files.readString(ORCHESTRATOR_CONFIG, StandardCharsets.UTF_8);
    DeploymentPlan plan =
        new V1OrchestratorCompiler(IMAGES, ignored -> "x".getBytes(StandardCharsets.UTF_8))
            .compile(WORKFLOW);

    List<String> stamped =
        plan.orchestrator().env().keySet().stream()
            .filter(name -> !name.startsWith("SECRET_"))
            .toList();

    assertThat(stamped).isNotEmpty();
    for (String name : stamped) {
      assertThat(orchestratorConfig)
          .as("dws-orchestrator application.yaml must read ${%s}", name)
          .contains("${" + name + ":");
    }
  }
}
