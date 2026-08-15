package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Input to {@link EvaluateForActivity}: the name of the FOR task to resolve (against the pod's
 * pinned definition) and the current workflow data its {@code for.in} expression is evaluated over.
 * {@code variables} carries scope-local jq bindings inherited from an enclosing scope (e.g. a
 * {@code catch.do}'s error variable when the {@code for} nests inside {@code try}); empty at the
 * top level, mirroring {@link EvaluateSetRequest}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluateForRequest(String taskName, JsonNode data, Map<String, JsonNode> variables)
    implements StepRequest {}
