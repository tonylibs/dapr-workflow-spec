package io.dws.step.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Loads and validates the Step half of the shared single-node definition contract. */
public class SingleNodeDefinitionLoader {

  static final String DEFINITION_PATH_ENV = "DWS_STEP_DEFINITION_PATH";
  private static final Pattern DNS_1123_LABEL = Pattern.compile("[a-z0-9]([-a-z0-9]*[a-z0-9])?");

  private final ObjectMapper mapper;
  private final String definitionPath;

  public SingleNodeDefinitionLoader(ObjectMapper mapper, String definitionPath) {
    this.mapper = mapper;
    this.definitionPath = definitionPath;
  }

  public SingleNodeDefinition load() {
    if (definitionPath == null || definitionPath.isBlank()) {
      throw new DefinitionLoadException(DEFINITION_PATH_ENV + " is required but was not set");
    }

    JsonNode definition = readDefinition();
    if (definition == null || !definition.isObject()) {
      throw new DefinitionLoadException("single-node definition must be a JSON object");
    }

    String workflow = requiredText(definition, "workflow");
    String version = requiredText(definition, "version");
    String nodeId = requiredText(definition, "nodeId");
    if (nodeId.length() > 63 || !DNS_1123_LABEL.matcher(nodeId).matches()) {
      throw new DefinitionLoadException("nodeId must be a DNS-1123 label: '" + nodeId + "'");
    }
    if (!"step".equals(requiredText(definition, "kind"))) {
      throw new DefinitionLoadException("definition kind must be 'step'");
    }

    JsonNode task = definition.path("task");
    if (!task.isObject() || task.size() == 0) {
      throw new DefinitionLoadException("step definition must contain a non-empty object 'task'");
    }

    boolean delegatesToFunction = task.has("call") || task.has("run");
    JsonNode functionAppId = definition.get("functionAppId");
    if (delegatesToFunction
        && (functionAppId == null
            || !functionAppId.isTextual()
            || functionAppId.textValue().isBlank())) {
      throw new DefinitionLoadException(
          "functionAppId is required when task.call or task.run is set");
    }
    if (!delegatesToFunction && functionAppId != null) {
      throw new DefinitionLoadException(
          "functionAppId must be absent unless task.call or task.run is set");
    }

    return new SingleNodeDefinition(
        workflow, version, nodeId, task, functionAppId == null ? null : functionAppId.textValue());
  }

  private JsonNode readDefinition() {
    try {
      return mapper.readTree(Files.readString(Path.of(definitionPath)));
    } catch (IOException | IllegalArgumentException e) {
      throw new DefinitionLoadException(
          "failed to load definition '" + definitionPath + "': " + e.getMessage(), e);
    }
  }

  private String requiredText(JsonNode definition, String field) {
    JsonNode value = definition.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw new DefinitionLoadException("definition must contain non-empty string '" + field + "'");
    }
    return value.textValue();
  }
}
