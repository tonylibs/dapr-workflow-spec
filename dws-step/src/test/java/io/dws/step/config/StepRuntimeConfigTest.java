package io.dws.step.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Boots the full context so wiring failures (e.g. a missing bean) fail the build, not startup. */
@SpringBootTest
class StepRuntimeConfigTest {

  @TempDir static Path tempDir;

  @DynamicPropertySource
  static void definitionPath(DynamicPropertyRegistry registry) throws IOException {
    Path file = tempDir.resolve("step-set.json");
    Files.writeString(
        file,
        """
        {
          "workflow": "order-fulfillment",
          "version": "order-fulfillment@v3f9a1c2b",
          "nodeId": "validate-order",
          "kind": "step",
          "task": { "set": { "validated": true } }
        }
        """);
    registry.add(SingleNodeDefinitionLoader.DEFINITION_PATH_ENV, file::toString);
  }

  @Autowired SingleNodeDefinition definition;

  @Test
  void contextStartsAndLoadsDefinition() {
    assertThat(definition.nodeId()).isEqualTo("validate-order");
    assertThat(definition.taskKind()).isEqualTo("set");
  }
}
