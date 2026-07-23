package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Input handed to every interpreter instance. The definition {@code version} is pinned
 * at start time so that replays resolve the exact same {@code WorkflowDef} and stay
 * deterministic even if newer definition versions are loaded later.
 *
 * @param workflowName logical workflow name (matches {@code document.name})
 * @param version      pinned definition version (matches {@code document.version})
 * @param data         initial workflow data document
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowInput(String workflowName, String version, JsonNode data) {
}
