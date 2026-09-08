package io.dws.controller.compile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SingleNodeDefinitionTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final SingleNodeDefinition.Envelope ENVELOPE =
      new SingleNodeDefinition.Envelope("order-fulfillment", "order-fulfillment@v1a2b3c4d");

  // NOTE: the brief's schema() used com.networknt.schema.JsonSchema/JsonSchemaFactory/SpecVersion
  // (the 1.x API). The version resolved transitively on this classpath (2.0.0) is the rewritten
  // v2 API (SchemaRegistry/Schema/SpecificationVersion) — those 1.x classes no longer exist in
  // the jar. Adapted to the 2.0.0 API below; behavior (load the real schema file, validate the
  // rendered JSON, assert no errors) is unchanged.
  private static Schema schema() throws Exception {
    Path path = Path.of("..", "openspec", "schemas", "single-node-definition.schema.json");
    JsonNode schemaNode = JSON.readTree(Files.readString(path));
    return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(schemaNode);
  }

  private static void assertValid(String rendered) throws Exception {
    assertThat(schema().validate(JSON.readTree(rendered))).isEmpty();
  }

  @Test
  void rendersAFlowNodeWithChildrenAndCatch() throws Exception {
    JsonNode task = JSON.readTree("{\"reserveItems\":{\"for\":{\"each\":\"item\"}}}");
    String rendered =
        SingleNodeDefinition.flow(
            ENVELOPE,
            "fulfill-order",
            "try",
            List.of(task),
            Map.of("reserveItems", "reserve-items", "catch", "fulfill-order-catch"),
            "fulfill-order-catch",
            null);
    JsonNode node = JSON.readTree(rendered);
    assertThat(node.get("nodeId").asText()).isEqualTo("fulfill-order");
    assertThat(node.get("kind").asText()).isEqualTo("flow");
    assertThat(node.get("scope").asText()).isEqualTo("try");
    assertThat(node.get("catch").asText()).isEqualTo("fulfill-order-catch");
    assertThat(node.get("children").get("reserveItems").asText()).isEqualTo("reserve-items");
    assertThat(node.has("forkMode")).isFalse();
    assertValid(rendered);
  }

  @Test
  void rendersAForkNodeWithForkModeAndNoTasks() throws Exception {
    String rendered =
        SingleNodeDefinition.flow(
            ENVELOPE,
            "notify-channels",
            "fork",
            List.of(),
            Map.of("notifyRecipients", "notify-channels-branch-notify-recipients"),
            null,
            "all");
    JsonNode node = JSON.readTree(rendered);
    assertThat(node.get("forkMode").asText()).isEqualTo("all");
    assertThat(node.get("tasks")).isEmpty();
    assertThat(node.has("catch")).isFalse();
    assertValid(rendered);
  }

  @Test
  void rendersADoScopeFlowNode() throws Exception {
    JsonNode task = JSON.readTree("{\"stampOrder\":{\"set\":{\"stamped\":true}}}");
    String rendered =
        SingleNodeDefinition.flow(
            ENVELOPE,
            "prepare-order",
            "do",
            List.of(task),
            Map.of("stampOrder", "stamp-order"),
            null,
            null);
    assertThat(JSON.readTree(rendered).get("scope").asText()).isEqualTo("do");
    assertValid(rendered);
  }

  @Test
  void rendersAStepNodeWithAndWithoutFunctionAppId() throws Exception {
    JsonNode task = JSON.readTree("{\"reserveItem\":{\"call\":\"http\"}}");
    String withFunction =
        SingleNodeDefinition.step(ENVELOPE, "reserve-item", task, "reserve-item-fn");
    assertThat(JSON.readTree(withFunction).get("functionAppId").asText())
        .isEqualTo("reserve-item-fn");
    assertValid(withFunction);

    JsonNode setTask = JSON.readTree("{\"validateOrder\":{\"set\":{\"status\":\"validating\"}}}");
    String withoutFunction = SingleNodeDefinition.step(ENVELOPE, "validate-order", setTask, null);
    assertThat(JSON.readTree(withoutFunction).has("functionAppId")).isFalse();
    assertValid(withoutFunction);
  }
}
