package io.dws.orchestrator.api.dto;

/** Snapshot of a workflow instance's runtime status and (optional) output. */
public record InstanceStatusResponse(String instanceId, String runtimeStatus, Object output) {
}
