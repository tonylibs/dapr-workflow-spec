package io.dws.orchestrator.api.dto;

/** Returned when a new workflow instance is started. */
public record StartInstanceResponse(String instanceId, String workflowName, String version) {
}
