package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * The input a {@link ForkBranchWorkflow} child instance receives: which branch task to dispatch, by
 * name, and the data/context/variables/depth to dispatch it with.
 *
 * <p>The branch's {@code Task} object itself is not carried across the child-workflow boundary —
 * {@code taskName} is unique across the whole definition (the same invariant every other name-based
 * lookup in this codebase relies on), so {@link
 * io.dws.orchestrator.workflow.activity.DefinitionLookup#taskByName} resolves it from the pod's own
 * pinned definition, identically to how any in-process activity resolves its target.
 */
public record ForkBranchInput(
    String taskName, JsonNode data, JsonNode context, Map<String, JsonNode> variables, int depth) {}
