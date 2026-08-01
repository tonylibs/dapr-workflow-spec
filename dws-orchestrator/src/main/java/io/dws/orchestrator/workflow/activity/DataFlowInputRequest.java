package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Input-phase request: transform {@code rawInput} through the task's {@code input.from} and
 * validate it against {@code input.schema}. {@code context} is the current workflow context,
 * exposed to the expression as {@code $context}.
 *
 * <p>{@code variables} carries scope-local jq bindings — the caught error inside a {@code catch.do}
 * block, under the name its {@code catch.as} declares. Empty for a task in any other scope. It is
 * threaded alongside (not merged into) the data document so the binding disappears with its scope.
 */
public record DataFlowInputRequest(
    String taskName, JsonNode rawInput, JsonNode context, Map<String, JsonNode> variables) {}
