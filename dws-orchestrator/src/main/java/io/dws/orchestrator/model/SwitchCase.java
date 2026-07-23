package io.dws.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single branch of a {@code switch} task: when {@link #when} evaluates truthy,
 * flow transfers to {@link #then}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwitchCase(String when, String then) {
}
