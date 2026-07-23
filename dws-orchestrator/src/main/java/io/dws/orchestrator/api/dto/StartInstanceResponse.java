package io.dws.orchestrator.api.dto;

/** Returned when a new workflow instance is started. The pod pins the definition version. */
public record StartInstanceResponse(String instanceId, String workflowName) {
}
