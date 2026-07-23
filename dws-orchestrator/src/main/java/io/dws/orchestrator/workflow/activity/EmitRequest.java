package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Input to {@link EmitEventActivity}: the Dapr pub/sub component, the topic, and the
 * current workflow data to publish.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmitRequest(String pubsub, String topic, JsonNode data) {
}
