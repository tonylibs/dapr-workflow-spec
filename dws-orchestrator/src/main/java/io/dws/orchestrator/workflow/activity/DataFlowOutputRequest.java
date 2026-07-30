package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Output-phase request: transform the task body's {@code rawOutput} through {@code output.as},
 * validate it against {@code output.schema}, then compute the new workflow context from {@code
 * export.as}. {@code context} is the context as it stands before this task's export.
 *
 * <p>{@code variables} carries scope-local jq bindings — the caught error inside a {@code catch.do}
 * block, under the name its {@code catch.as} declares. Empty for a task in any other scope. It is
 * threaded alongside (not merged into) the data document so the binding disappears with its scope.
 */
public record DataFlowOutputRequest(
    String taskName, JsonNode rawOutput, JsonNode context, Map<String, JsonNode> variables) {}
