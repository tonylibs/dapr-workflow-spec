package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Output-phase result: the task's transformed output (which becomes the next task's raw input) and
 * the workflow context after this task's {@code export.as}.
 */
public record DataFlowResult(JsonNode data, JsonNode context) {}
