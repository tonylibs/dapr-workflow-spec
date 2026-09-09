package io.dws.controller.model;

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
import java.util.Optional;
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
    String rendered =
        SingleNodeDefinition.flow(
            ENVELOPE,
            "fulfill-order",
            FlowScope.of("try-catch", List.of()).withCatch("fulfill-order-catch"),
            Map.of("try", "fulfill-order-try", "catch", "fulfill-order-catch"));
    JsonNode node = JSON.readTree(rendered);
    assertThat(node.get("nodeId").asText()).isEqualTo("fulfill-order");
    assertThat(node.get("kind").asText()).isEqualTo("flow");
    assertThat(node.get("scope").asText()).isEqualTo("try-catch");
    assertThat(node.get("catch").asText()).isEqualTo("fulfill-order-catch");
    assertThat(node.get("children").get("try").asText()).isEqualTo("fulfill-order-try");
    assertThat(node.has("forkMode")).isFalse();
    assertValid(rendered);
  }

  @Test
  void rendersAForkNodeWithForkModeAndNoTasks() throws Exception {
    String rendered =
        SingleNodeDefinition.flow(
            ENVELOPE,
            "notify-channels",
            FlowScope.of("fork", List.of()).withForkMode("all"),
            Map.of("notifyRecipients", "notify-recipients"));
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
            FlowScope.of("do", List.of(task)),
            Map.of("stampOrder", "stamp-order"));
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

  @Test
  void rendersAForControllersLoopConfiguration() throws Exception {
    FlowScope scope =
        FlowScope.of("for", List.of())
            .withForConfig(
                new FlowScope.ForConfig(
                    Optional.of("item"),
                    Optional.of(".items"),
                    Optional.empty(),
                    Optional.empty()));

    String text =
        SingleNodeDefinition.flow(
            new SingleNodeDefinition.Envelope("order-fulfillment", "order-fulfillment@v1a2b3c4d"),
            "reserve-items",
            scope,
            Map.of("do", "reserve-items-do"));

    JsonNode node = new ObjectMapper().readTree(text);
    assertThat(node.get("scope").asText()).isEqualTo("for");
    assertThat(node.get("tasks")).isEmpty();
    assertThat(node.get("each").asText()).isEqualTo("item");
    assertThat(node.get("in").asText()).isEqualTo(".items");
    assertThat(node.has("at")).isFalse();
    assertThat(node.has("while")).isFalse();
    assertThat(node.get("children").get("do").asText()).isEqualTo("reserve-items-do");
    assertValid(text);
  }

  @Test
  void rendersATryCatchControllersErrorFilterAndRetryPolicy() throws Exception {
    ObjectMapper json = new ObjectMapper();
    FlowScope scope =
        FlowScope.of("try-catch", List.of())
            .withTryCatchConfig(
                new FlowScope.TryCatchConfig(
                    Optional.of(json.readTree("{\"with\":{\"status\":402}}")),
                    Optional.of(json.readTree("\"myRetryPolicy\"")),
                    Optional.of("paymentError"),
                    Optional.of(".paymentError.status == 402"),
                    Optional.of(".retryBudget == 0")))
            .withCatch("process-payment-catch");

    String text =
        SingleNodeDefinition.flow(
            new SingleNodeDefinition.Envelope("guarded-payment", "guarded-payment@v1a2b3c4d"),
            "process-payment",
            scope,
            Map.of("try", "process-payment-try", "catch", "process-payment-catch"));

    JsonNode node = json.readTree(text);
    assertThat(node.get("scope").asText()).isEqualTo("try-catch");
    assertThat(node.get("tasks")).isEmpty();
    assertThat(node.get("catch").asText()).isEqualTo("process-payment-catch");
    assertThat(node.get("errors").get("with").get("status").asInt()).isEqualTo(402);
    assertThat(node.get("retry").asText()).isEqualTo("myRetryPolicy");
    assertThat(node.get("as").asText()).isEqualTo("paymentError");
    assertThat(node.get("when").asText()).isEqualTo(".paymentError.status == 402");
    assertThat(node.get("exceptWhen").asText()).isEqualTo(".retryBudget == 0");
    assertValid(text);
  }

  @Test
  void omitsControllerConfigurationOnASequencer() throws Exception {
    String text =
        SingleNodeDefinition.flow(
            new SingleNodeDefinition.Envelope("w", "w@v1"),
            "w-main",
            FlowScope.of("main", List.of()),
            Map.of());

    JsonNode node = new ObjectMapper().readTree(text);
    for (String field :
        List.of(
            "each",
            "in",
            "at",
            "while",
            "errors",
            "retry",
            "as",
            "when",
            "exceptWhen",
            "catch",
            "forkMode")) {
      assertThat(node.has(field)).as(field).isFalse();
    }
  }
}
