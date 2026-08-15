package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Input to {@link EvaluateWhileActivity}: the name of the FOR task whose {@code while} to evaluate,
 * the current iteration's data, and the scope-local variables (including the iteration variables
 * bound by {@code dispatchFor}). Called once per iteration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluateWhileRequest(String taskName, JsonNode data, Map<String, JsonNode> variables)
    implements StepRequest {}
