package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Input to {@link EvaluateSwitchActivity}: the name of the SWITCH task to evaluate (resolved
 * against the pod's pinned definition) and the current workflow data its {@code when} expressions
 * are evaluated over.
 *
 * <p>{@code variables} carries scope-local jq bindings — the caught error inside a {@code catch.do}
 * block, under the name its {@code catch.as} declares. Empty for a task in any other scope. It is
 * threaded alongside (not merged into) the data document so the binding disappears with its scope.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluateSwitchRequest(
    String taskName, JsonNode data, Map<String, JsonNode> variables) {}
