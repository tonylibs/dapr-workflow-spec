package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Input to {@link RaiseErrorActivity}: the name of the RAISE task to resolve (resolved against the
 * pod's pinned definition) and the current workflow data its expression fields are evaluated over.
 *
 * <p>{@code variables} carries scope-local jq bindings — the caught error inside a {@code catch.do}
 * block, under the name its {@code catch.as} declares. Empty for a task in any other scope, exactly
 * as for {@link EvaluateSetRequest}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RaiseErrorRequest(String taskName, JsonNode data, Map<String, JsonNode> variables)
    implements StepRequest {}
