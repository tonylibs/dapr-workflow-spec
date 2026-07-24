package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Input to {@link AdminEventActivity}: the Dapr pub/sub component, the topic, and the fully-built
 * CloudEvents-style envelope to publish (see {@code docs/events.md}). Mirrors {@link EmitRequest};
 * {@code data} here is the whole envelope, not just workflow data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdminEventRequest(String pubsub, String topic, JsonNode data) {
}
