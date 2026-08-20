package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * The input a {@link ScopeRunnerWorkflow} child instance receives: which task list to run as a
 * scope, and the data/context/variables/depth to run it with.
 *
 * <p>Mirrors {@link ForkBranchInput}'s "resolve by name, not by carrying the object" pattern: the
 * task list itself does not cross the child-workflow boundary. {@code tryTaskName} identifies which
 * list — {@code null}/blank selects the definition's top-level {@code do} list, and any other value
 * names a {@code try} task whose {@code try} list is resolved via {@link
 * io.dws.orchestrator.workflow.activity.DefinitionLookup#taskByName} from the pod's own pinned
 * definition, identically to how any in-process activity resolves its target.
 */
public record ScopeRunnerInput(
    String tryTaskName,
    JsonNode data,
    JsonNode context,
    Map<String, JsonNode> variables,
    int depth) {}
