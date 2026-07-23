package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Input to {@link CallServiceActivity}: which Dapr app-id to invoke, on which method
 * path, and the current workflow data to POST.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CallRequest(String appId, String path, JsonNode data) {
}
