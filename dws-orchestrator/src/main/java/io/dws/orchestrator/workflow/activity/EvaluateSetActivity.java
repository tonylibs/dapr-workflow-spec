package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.types.Set;
import io.serverlessworkflow.api.types.SetTask;
import io.serverlessworkflow.api.types.SetTaskConfiguration;
import io.serverlessworkflow.api.types.Task;
import java.util.Map;

/**
 * Applies a SET task, producing a new data document. Pure jq evaluation with no I/O — it runs in
 * the orchestrator's own JVM (no network hop, nothing extra deployed) and exists as an activity
 * purely so every task type dispatches through {@code ctx.callActivity(...)} uniformly, keeping
 * evaluation out of the workflow method's replay loop.
 */
public class EvaluateSetActivity implements WorkflowActivity {

  @Override
  public Object run(WorkflowActivityContext ctx) {
    return apply(ctx.getInput(EvaluateSetRequest.class));
  }

  /** Produces a new data document with each {@code set} entry evaluated over the original data. */
  public static JsonNode apply(EvaluateSetRequest request) {
    Task task = DefinitionLookup.taskByName(request.taskName());
    SetTask setTask = task.getSetTask();
    if (setTask == null) {
      throw new IllegalStateException("task '" + request.taskName() + "' is not a set task");
    }

    JqEvaluator jq = WorkflowSupport.jq();
    ObjectMapper mapper = WorkflowSupport.mapper();
    JsonNode data = request.data();

    ObjectNode result =
        (data != null && data.isObject()) ? data.deepCopy() : mapper.createObjectNode();
    Set set = setTask.getSet();
    if (set == null) {
      return result;
    }
    SetTaskConfiguration cfg = set.getSetTaskConfiguration();
    if (cfg != null && cfg.getAdditionalProperties() != null) {
      for (Map.Entry<String, Object> entry : cfg.getAdditionalProperties().entrySet()) {
        result.set(entry.getKey(), evalSetValue(entry.getValue(), data, jq, mapper));
      }
      return result;
    }
    if (set.getString() != null) {
      JsonNode whole = jq.evaluate(set.getString(), data);
      return (whole != null && whole.isObject()) ? whole : result;
    }
    return result;
  }

  private static JsonNode evalSetValue(
      Object value, JsonNode data, JqEvaluator jq, ObjectMapper mapper) {
    if (value instanceof String expr) {
      return jq.evaluate(expr, data);
    }
    return mapper.valueToTree(value);
  }
}
