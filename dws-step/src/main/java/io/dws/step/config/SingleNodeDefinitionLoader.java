package io.dws.step.config;

import static org.apache.commons.lang3.BooleanUtils.negate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import one.util.streamex.StreamEx;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

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
    String path =
        Optional.ofNullable(definitionPath)
            .filter(StringUtils::isNotBlank)
            .orElseThrow(
                () ->
                    new DefinitionLoadException(
                        DEFINITION_PATH_ENV + " is required but was not set"));

    JsonNode definition =
        Optional.ofNullable(readDefinition(path))
            .filter(JsonNode::isObject)
            .orElseThrow(
                () -> new DefinitionLoadException("single-node definition must be a JSON object"));

    String workflow = requiredText(definition, "workflow");
    String version = requiredText(definition, "version");
    String nodeId = requiredText(definition, "nodeId");
    if (nodeId.length() > 63 || !DNS_1123_LABEL.matcher(nodeId).matches()) {
      throw new DefinitionLoadException("nodeId must be a DNS-1123 label: '" + nodeId + "'");
    }
    if (!"step".equals(requiredText(definition, "kind"))) {
      throw new DefinitionLoadException("definition kind must be 'step'");
    }

    JsonNode task =
        Optional.of(definition.path("task"))
            .filter(JsonNode::isObject)
            .filter(node -> negate(node.isEmpty()))
            .orElseThrow(
                () ->
                    new DefinitionLoadException(
                        "step definition must contain a non-empty object 'task'"));

    validateTaskKind(task);

    String functionAppId =
        StreamEx.of("call", "run").anyMatch(task::has)
            ? nonBlankText(definition, "functionAppId")
                .orElseThrow(
                    () ->
                        new DefinitionLoadException(
                            "functionAppId is required when task.call or task.run is set"))
            : null;

    return new SingleNodeDefinition(workflow, version, nodeId, task, functionAppId);
  }

  private void validateTaskKind(JsonNode task) {
    List<String> flowOnly =
        StreamEx.of(SingleNodeDefinition.FLOW_ONLY_TASK_KINDS).filter(task::has).toList();
    if (CollectionUtils.isNotEmpty(flowOnly)) {
      throw new DefinitionLoadException(
          "task kind(s) "
              + flowOnly
              + " cannot run in dws-step: wait and listen run as dws-flow controller nodes"
              + " (see ADR 0006)");
    }
    try {
      new SingleNodeDefinition(null, null, null, task, null).kind();
    } catch (IllegalStateException e) {
      throw new DefinitionLoadException(e.getMessage(), e);
    }
  }

  private JsonNode readDefinition(String path) {
    try {
      return mapper.readTree(Files.readString(Path.of(path)));
    } catch (IOException | IllegalArgumentException e) {
      throw new DefinitionLoadException(
          "failed to load definition '" + path + "': " + e.getMessage(), e);
    }
  }

  private String requiredText(JsonNode definition, String field) {
    return nonBlankText(definition, field)
        .orElseThrow(
            () ->
                new DefinitionLoadException(
                    "definition must contain non-empty string '" + field + "'"));
  }

  private Optional<String> nonBlankText(JsonNode definition, String field) {
    return Optional.ofNullable(definition.get(field))
        .filter(JsonNode::isTextual)
        .map(JsonNode::textValue)
        .filter(StringUtils::isNotBlank);
  }
}
