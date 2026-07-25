package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Input to {@link EvaluateSetActivity}: the name of the SET task to apply (resolved against the
 * pod's pinned definition) and the current workflow data its expressions are evaluated over.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluateSetRequest(String taskName, JsonNode data) {}
