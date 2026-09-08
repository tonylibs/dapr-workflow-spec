package io.dws.controller.compile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import lombok.experimental.UtilityClass;

/** Renders one compiled node's single-node definition (Phase 0 schema). */
@UtilityClass
class SingleNodeDefinition {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** The envelope fields every node shares. */
  record Envelope(String workflow, String version) {}

  static String flow(
      Envelope envelope, String appId, FlowScope scope, Map<String, String> children) {
    ObjectNode node = envelope(envelope, appId, "flow");
    node.put("scope", scope.scope());
    ArrayNode taskArray = node.putArray("tasks");
    scope.tasks().forEach(taskArray::add);
    ObjectNode childObject = node.putObject("children");
    children.forEach(childObject::put);
    scope.catchAppId().ifPresent(catchAppId -> node.put("catch", catchAppId));
    scope.forkMode().ifPresent(forkMode -> node.put("forkMode", forkMode));
    return write(node);
  }

  static String step(Envelope envelope, String appId, JsonNode task, String functionAppId) {
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
