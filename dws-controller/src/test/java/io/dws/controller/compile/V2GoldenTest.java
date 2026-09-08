package io.dws.controller.compile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.dws.controller.model.CompiledNode;
import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.StepNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class V2GoldenTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Path EXAMPLES = Path.of("src", "test", "resources", "v2");
  private static final Path SCHEMA =
      Path.of("..", "openspec", "schemas", "single-node-definition.schema.json");

  @ParameterizedTest
  @ValueSource(
      strings = {
        "nested-try-for-catch",
        "parallel-fork",
        "state-and-decision",
        "timing-and-event",
        "external-call-and-run",
        "raise-and-recovery"
      })
  void matchesItsGoldenFixture(String example) throws Exception {
    Path dir = EXAMPLES.resolve(example);
    String definition = Files.readString(dir.resolve("definition.yaml"));
    DeploymentPlan plan = new V2StructuralCompiler().compile(definition);
    CompiledNode root = plan.flowStepGraph().get(0);

    String expectedGraph =
        Files.readString(dir.resolve("expected-graph.json"))
            .replace("<versionId>", plan.versionId());
    assertThat(JSON.readTree(describe(root))).isEqualTo(JSON.readTree(expectedGraph));

    Schema schema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7)
            .getSchema(JSON.readTree(Files.readString(SCHEMA)));
    for (CompiledNode node : root.flatten()) {
      Path nodeFixture = dir.resolve("nodes").resolve(node.appId() + ".json");
      assertThat(node.specText())
          .as("specText for %s", node.appId())
          .isEqualTo(Files.readString(nodeFixture).replace("<versionId>", plan.versionId()));
      assertThat(schema.validate(JSON.readTree(node.specText())))
          .as("schema violations for %s", node.appId())
          .isEmpty();
    }
  }

  @Test
  void theSchemaActuallyRejectsAnInvalidNode() throws Exception {
    Schema schema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7)
            .getSchema(JSON.readTree(Files.readString(SCHEMA)));
    // kind: flow with no scope, no tasks, no children — required fields missing
    JsonNode invalid =
        JSON.readTree("{\"workflow\":\"w\",\"version\":\"v\",\"nodeId\":\"n\",\"kind\":\"flow\"}");
    assertThat(schema.validate(invalid)).isNotEmpty();
  }

  /** Projects a node into the fixture's shape: identity and structure, not specText. */
  private static String describe(CompiledNode node) throws Exception {
    return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(describeNode(node));
  }

  private static ObjectNode describeNode(CompiledNode node) {
    ObjectNode out = JSON.createObjectNode();
    out.put("nodeId", node.nodeId());
    out.put("appId", node.appId());
    out.put("definitionResource", node.definitionResource());
    out.put("kind", node instanceof StepNode ? "step" : "flow");
    out.put(
        "functionAppId",
        node instanceof StepNode stepNode ? stepNode.functionAppId().orElse(null) : null);
    ArrayNode children = out.putArray("children");
    for (CompiledNode child : node.children()) {
      children.add(describeNode(child));
    }
    return out;
  }
}
