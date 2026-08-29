package io.dws.step.config;

import com.fasterxml.jackson.databind.JsonNode;

/** Validated, immutable representation of this process's one Step node. */
public record SingleNodeDefinition(
    String workflow, String version, String nodeId, JsonNode task, String functionAppId) {

  public String taskKind() {
    return task.fieldNames().next();
  }
}
