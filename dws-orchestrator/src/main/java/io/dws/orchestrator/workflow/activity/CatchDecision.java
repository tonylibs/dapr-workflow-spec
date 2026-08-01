package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The catch clause's verdict on one failure.
 *
 * <p>{@code caught} false means the failure is not handled here and must propagate unchanged.
 * {@code retry} true means attempt the try body again after {@code delayMillis}; false with {@code
 * caught} true means run the recovery block. {@code error} is the runtime error object, bound for
 * the recovery block under {@code errorVariable}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatchDecision(
    boolean caught, boolean retry, long delayMillis, JsonNode error, String errorVariable) {}
