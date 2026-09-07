package io.dws.controller.compile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;

/** Renders one compiled node's single-node definition (Phase 0 schema). */
final class SingleNodeDefinition {

  private static final ObjectMapper JSON = new ObjectMapper();

  private SingleNodeDefinition() {}

  /** The envelope fields every node shares. */
  record Envelope(String workflow, String version) {}

  static String flow(
      Envelope envelope,
      String appId,
      String scope,
      List<JsonNode> tasks,
      Map<String, String> children,
      String catchAppId,
      String forkMode) {
    ObjectNode node = envelope(envelope, appId, "flow");
    node.put("scope", scope);
    ArrayNode taskArray = node.putArray("tasks");
    tasks.forEach(taskArray::add);
    ObjectNode childObject = node.putObject("children");
    children.forEach(childObject::put);
    if (catchAppId != null) {
      node.put("catch", catchAppId);
    }
    if (forkMode != null) {
      node.put("forkMode", forkMode);
    }
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
