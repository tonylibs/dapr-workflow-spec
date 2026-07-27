package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Output-phase request: transform the task body's {@code rawOutput} through {@code output.as},
 * validate it against {@code output.schema}, then compute the new workflow context from {@code
 * export.as}. {@code context} is the context as it stands before this task's export.
 */
public record DataFlowOutputRequest(String taskName, JsonNode rawOutput, JsonNode context) {}
