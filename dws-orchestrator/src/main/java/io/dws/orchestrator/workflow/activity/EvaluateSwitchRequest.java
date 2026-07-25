package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Input to {@link EvaluateSwitchActivity}: the name of the SWITCH task to evaluate (resolved
 * against the pod's pinned definition) and the current workflow data its {@code when} expressions
 * are evaluated over.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluateSwitchRequest(String taskName, JsonNode data) {}
