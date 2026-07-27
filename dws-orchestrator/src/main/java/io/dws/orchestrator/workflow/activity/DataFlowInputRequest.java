package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Input-phase request: transform {@code rawInput} through the task's {@code input.from} and
 * validate it against {@code input.schema}. {@code context} is the current workflow context,
 * exposed to the expression as {@code $context}.
 */
public record DataFlowInputRequest(String taskName, JsonNode rawInput, JsonNode context) {}
