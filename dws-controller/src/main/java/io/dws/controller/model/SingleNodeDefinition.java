package io.dws.controller.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;

/** Renders one compiled node's single-node definition (Phase 0 schema). */
@UtilityClass
public class SingleNodeDefinition {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** The envelope fields every node shares. */
  public record Envelope(String workflow, String version) {}

  /** A flow node's definition, with no data-flow companion. */
  public static String flow(
      Envelope envelope, String appId, FlowScope scope, Map<String, String> children) {
    return write(flowNode(envelope, appId, scope, children));
  }

  /** A flow node's definition, referencing its data-flow companion Step by app ID. */
  public static String flow(
      Envelope envelope,
      String appId,
      FlowScope scope,
      Map<String, String> children,
      String dataflowAppId) {
    ObjectNode node = flowNode(envelope, appId, scope, children);
    node.put("dataflow", Objects.requireNonNull(dataflowAppId, "dataflowAppId"));
    return write(node);
  }

  private static ObjectNode flowNode(
      Envelope envelope, String appId, FlowScope scope, Map<String, String> children) {
    ObjectNode node = envelope(envelope, appId, "flow");
    node.put("scope", scope.scope());
    ArrayNode taskArray = node.putArray("tasks");
    scope.tasks().forEach(taskArray::add);
    ObjectNode childObject = node.putObject("children");
    children.forEach(childObject::put);
    scope.config().ifPresent(config -> putConfig(node, config));
    return node;
  }

  /** A controller's own fields, flattened onto the flow object under their DSL names (ADR 0004). */
  private static void putConfig(ObjectNode node, FlowScope.ScopeConfig config) {
    switch (config) {
      case FlowScope.TryCatchConfig c -> {
        c.catchAppId().ifPresent(catchAppId -> node.put("catch", catchAppId));
        c.errors().ifPresent(errors -> node.set("errors", errors));
        c.retry().ifPresent(retry -> node.set("retry", retry));
        c.as().ifPresent(as -> node.put("as", as));
        c.when().ifPresent(when -> node.put("when", when));
        c.exceptWhen().ifPresent(exceptWhen -> node.put("exceptWhen", exceptWhen));
      }
      case FlowScope.ForConfig c -> {
        c.each().ifPresent(each -> node.put("each", each));
        c.in().ifPresent(in -> node.put("in", in));
        c.at().ifPresent(at -> node.put("at", at));
        c.whileCondition().ifPresent(w -> node.put("while", w));
      }
      case FlowScope.ForkConfig c -> node.put("forkMode", c.mode());
      case FlowScope.WaitConfig c -> node.set("wait", c.duration());
      case FlowScope.ListenConfig c -> {
        node.set("to", c.to());
        c.timeout().ifPresent(timeout -> node.set("timeout", timeout));
        c.read().ifPresent(read -> node.put("read", read));
      }
    }
  }

  /** A step node's definition, with no co-located function. */
  public static String step(Envelope envelope, String appId, JsonNode task) {
    return step(envelope, appId, task, null);
  }

  public static String step(Envelope envelope, String appId, JsonNode task, String functionAppId) {
    ObjectNode node = envelope(envelope, appId, "step");
    node.set("task", task);
    if (functionAppId != null) {
      node.put("functionAppId", functionAppId);
    }
    return write(node);
  }

  private static ObjectNode envelope(Envelope envelope, String appId, String kind) {
    ObjectNode node = JSON.createObjectNode();
    node.put("workflow", envelope.workflow());
    node.put("version", envelope.version());
    node.put("nodeId", appId);
    node.put("kind", kind);
    return node;
  }

  private static String write(ObjectNode node) {
    try {
      DefaultPrettyPrinter printer =
          new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n"));
      return JSON.writer(printer).writeValueAsString(node) + "\n";
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("single-node definition could not be rendered", e);
    }
  }
}
