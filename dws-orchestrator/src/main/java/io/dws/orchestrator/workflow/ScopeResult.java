package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * What running one task scope produced: the data and workflow-context documents as they stand when
 * the scope finished, plus how it finished.
 */
public record ScopeResult(JsonNode data, JsonNode context, ScopeEnd end) {}
