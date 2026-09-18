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
 *
 * <p>{@code dispatchContext} carries the root workflow instance id and iteration path across this
 * child-instance boundary unchanged (see {@link DispatchContext}) — this type is also used to guard
 * a task-level {@code timeout}, and the branch/guarded task must not pick up this child instance's
 * own, freshly-derived {@code ctx.getInstanceId()} in place of it.
 */
public record ForkBranchInput(
    String taskName,
    JsonNode data,
    JsonNode context,
    Map<String, JsonNode> variables,
    int depth,
    DispatchContext dispatchContext) {}
