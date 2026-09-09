package io.dws.controller.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import lombok.experimental.UtilityClass;

/** Renders one compiled node's single-node definition (Phase 0 schema). */
@UtilityClass
public class SingleNodeDefinition {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** The envelope fields every node shares. */
  public record Envelope(String workflow, String version) {}

  public static String flow(
      Envelope envelope, String appId, FlowScope scope, Map<String, String> children) {
    ObjectNode node = envelope(envelope, appId, "flow");
    node.put("scope", scope.scope());
    ArrayNode taskArray = node.putArray("tasks");
    scope.tasks().forEach(taskArray::add);
    ObjectNode childObject = node.putObject("children");
    children.forEach(childObject::put);
    scope.catchAppId().ifPresent(catchAppId -> node.put("catch", catchAppId));
    scope
        .tryCatchConfig()
        .ifPresent(
            config -> {
              config.errors().ifPresent(errors -> node.set("errors", errors));
              config.retry().ifPresent(retry -> node.set("retry", retry));
              config.as().ifPresent(as -> node.put("as", as));
              config.when().ifPresent(when -> node.put("when", when));
              config.exceptWhen().ifPresent(exceptWhen -> node.put("exceptWhen", exceptWhen));
            });
    scope
        .forConfig()
        .ifPresent(
            config -> {
              config.each().ifPresent(each -> node.put("each", each));
              config.in().ifPresent(in -> node.put("in", in));
              config.at().ifPresent(at -> node.put("at", at));
              config.whileCondition().ifPresent(w -> node.put("while", w));
            });
    scope.forkMode().ifPresent(forkMode -> node.put("forkMode", forkMode));
    return write(node);
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
      return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node) + "\n";
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("single-node definition could not be rendered", e);
    }
  }
}
