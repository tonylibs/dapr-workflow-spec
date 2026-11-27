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
import io.dws.controller.model.FlowNode;
import io.dws.controller.model.StepNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class V2GoldenTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Path EXAMPLES = Path.of("src", "test", "resources", "v2");
  private static final Path SCHEMA =
      Path.of("src", "test", "resources", "single-node-definition.schema.json");

  @ParameterizedTest
  @ValueSource(
      strings = {
        "nested-try-for-catch",
        "parallel-fork",
        "state-and-decision",
        "timing-and-event",
        "external-call-and-run",
        "raise-and-recovery",
        "nested-do",
        "structural-dataflow"
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

    Schema schema = schema();
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
    // kind: flow with no scope, no tasks, no children — required fields missing
    JsonNode invalid =
        JSON.readTree("{\"workflow\":\"w\",\"version\":\"v\",\"nodeId\":\"n\",\"kind\":\"flow\"}");
    assertThat(schema().validate(invalid)).isNotEmpty();
  }

  @Test
  void theSchemaRejectsRetiredScopeValues() throws Exception {
    for (String retired : java.util.List.of("try", "catch", "forkBranch")) {
      JsonNode node =
          JSON.readTree(
              """
              {"workflow":"w","version":"v","nodeId":"n","kind":"flow",
               "scope":"%s","tasks":[],"children":{}}
              """
                  .formatted(retired));
      assertThat(schema().validate(node)).as(retired).isNotEmpty();
    }
  }

  @Test
  void theSchemaRejectsANonEmptyTaskListOnAController() throws Exception {
    JsonNode node =
        JSON.readTree(
            """
            {"workflow":"w","version":"v","nodeId":"n","kind":"flow","scope":"for",
             "in":".items","tasks":[{"someTask":{"set":{"a":1}}}],"children":{"do":"n-do"}}
            """);
    assertThat(schema().validate(node)).isNotEmpty();
  }

  @Test
  void theSchemaRejectsLoopFieldsOnANonForScope() throws Exception {
    JsonNode node =
        JSON.readTree(
            """
            {"workflow":"w","version":"v","nodeId":"n","kind":"flow","scope":"do",
             "tasks":[],"children":{},"each":"item","in":".items"}
            """);
    assertThat(schema().validate(node)).isNotEmpty();
  }

  @Test
  void theSchemaRejectsErrorsOnANonTryCatchScope() throws Exception {
    JsonNode node =
        JSON.readTree(
            """
            {"workflow":"w","version":"v","nodeId":"n","kind":"flow","scope":"do",
             "tasks":[],"children":{},"errors":{"with":{"status":402}}}
            """);
    assertThat(schema().validate(node)).isNotEmpty();
  }

  @Test
  void theSchemaAcceptsBothShapes() throws Exception {
    JsonNode sequencer =
        JSON.readTree(
            """
            {"workflow":"w","version":"v","nodeId":"n","kind":"flow","scope":"do",
             "tasks":[{"a":{"set":{"x":1}}}],"children":{"a":"a"}}
            """);
    JsonNode controller =
        JSON.readTree(
            """
            {"workflow":"w","version":"v","nodeId":"n","kind":"flow","scope":"try-catch",
             "tasks":[],"children":{"try":"n-try","catch":"n-catch"},"catch":"n-catch",
             "errors":{"with":{"status":402}},"retry":"myRetryPolicy"}
            """);
    assertThat(schema().validate(sequencer)).isEmpty();
    assertThat(schema().validate(controller)).isEmpty();
  }

  @Test
  void theSchemaValidatesWaitLeafControllers() throws Exception {
    Schema schema = schema();
    ObjectNode wait = flowDefinition("wait");
    wait.remove("wait");
    assertThat(schema.validate(wait)).isNotEmpty();
    assertThat(schema.validate(wait.put("wait", "PT5M"))).isEmpty();
    wait.putObject("wait").put("seconds", 10);
    assertThat(schema.validate(wait)).isEmpty();
    assertThat(schema.validate(wait.deepCopy().put("wait", 10))).isNotEmpty();
    assertThat(schema.validate(flowDefinition("do").put("wait", "PT5M"))).isNotEmpty();
  }

  @Test
  void theSchemaValidatesListenLeafControllers() throws Exception {
    Schema schema = schema();
    ObjectNode listen = flowDefinition("listen");
    listen.remove("to");
    assertThat(schema.validate(listen)).isNotEmpty();
    listen.putObject("to").putObject("one").putObject("with").put("type", "example.approval");
    assertThat(schema.validate(listen)).isEmpty();
    assertThat(schema.validate(listen.put("timeout", "PT1H").put("read", "data"))).isEmpty();
    listen.putObject("timeout").putObject("after").put("minutes", 5);
    assertThat(schema.validate(listen)).isEmpty();
    assertThat(schema.validate(listen.deepCopy().put("to", "event"))).isNotEmpty();
    assertThat(schema.validate(listen.deepCopy().put("timeout", 10))).isNotEmpty();
    assertThat(schema.validate(listen.deepCopy().put("read", ""))).isNotEmpty();
    for (String field : List.of("to", "timeout", "read")) {
      ObjectNode foreignScope = flowDefinition("do");
      foreignScope.set(field, listen.get(field));
      assertThat(schema.validate(foreignScope)).as(field).isNotEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"wait", "listen"})
  void theSchemaRejectsExecutionChildrenAndTasksOnLeafControllers(String scope) throws Exception {
    Schema schema = schema();
    ObjectNode leaf = flowDefinition(scope);
    ObjectNode withTasks = leaf.deepCopy();
    withTasks.withArray("tasks").addObject().putObject("nested").putObject("set").put("x", 1);
    assertThat(schema.validate(withTasks)).isNotEmpty();
    ObjectNode withChildren = leaf.deepCopy();
    withChildren.withObjectProperty("children").put("nested", "nested");
    assertThat(schema.validate(withChildren)).isNotEmpty();
  }

  @Test
  void theSchemaAllowsOnlyDns1123DataflowReferencesOnStructuralFlows() throws Exception {
    Schema schema = schema();
    for (String scope : List.of("do", "for", "try-catch", "fork", "wait", "listen")) {
      assertThat(schema.validate(flowDefinition(scope).put("dataflow", "reserve-items-dataflow")))
          .as(scope)
          .isEmpty();
    }
    assertThat(schema.validate(flowDefinition("main").put("dataflow", "reserve-items-dataflow")))
        .isNotEmpty();
    for (String invalid :
        List.of("", "ReserveItems", "-bad", "with.dot", "bad\n", "a".repeat(64))) {
      assertThat(schema.validate(flowDefinition("do").put("dataflow", invalid))).isNotEmpty();
    }
  }

  @Test
  void theSchemaAcceptsACompanionStep() throws Exception {
    JsonNode node =
        JSON.readTree(
            """
            {"workflow":"w","version":"v","nodeId":"reserve-items-dataflow","kind":"step",
             "task":{"reserveItems":{"input":{"from":"${ .payload }"},"output":{"as":"${ . }"}}}}
            """);
    assertThat(schema().validate(node)).isEmpty();
  }

  private static ObjectNode flowDefinition(String scope) throws Exception {
    ObjectNode flow =
        (ObjectNode)
            JSON.readTree(
                """
                {"workflow":"w","version":"v","nodeId":"n","kind":"flow","tasks":[],"children":{}}
                """);
    flow.put("scope", scope);
    switch (scope) {
      case "for" -> flow.put("in", ".items");
      case "fork" -> flow.put("forkMode", "all");
      case "wait" -> flow.put("wait", "PT5M");
      case "listen" -> flow.putObject("to");
      default -> {}
    }
    return flow;
  }

  private static Schema schema() throws Exception {
    return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7)
        .getSchema(JSON.readTree(Files.readString(SCHEMA)));
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
    if (node instanceof FlowNode flow) {
      flow.dataflow().ifPresent(companion -> out.set("dataflow", describeNode(companion)));
    }
    ArrayNode children = out.putArray("children");
    for (CompiledNode child : node.children()) {
      children.add(describeNode(child));
    }
    return out;
  }
}
