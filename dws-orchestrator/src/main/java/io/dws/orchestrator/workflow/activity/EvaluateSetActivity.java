package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.types.SetTask;
import io.serverlessworkflow.api.types.SetTaskConfiguration;
import io.serverlessworkflow.api.types.Task;
import java.util.Map;
import java.util.Optional;
import one.util.streamex.EntryStream;

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
    ObjectNode base =
        Optional.ofNullable(data)
            .filter(JsonNode::isObject)
            .map(node -> (ObjectNode) node.deepCopy())
            .orElseGet(mapper::createObjectNode);

    // The structured entry form takes precedence — only a `set` written as a bare string runs as a
    // single whole-document program, and that only counts when it actually yields an object.
    return Optional.ofNullable(setTask.getSet())
        .flatMap(
            set ->
                Optional.ofNullable(set.getSetTaskConfiguration())
                    .map(SetTaskConfiguration::getAdditionalProperties)
                    .<JsonNode>map(
                        props -> withEntries(base, props, data, request.variables(), jq, mapper))
                    .or(
                        () ->
                            Optional.ofNullable(set.getString())
                                .map(expr -> jq.evaluate(expr, data, scope(request.variables())))
                                .filter(JsonNode::isObject)))
        .orElse(base);
  }

  /** Evaluates every {@code set} entry over the original data, returning the populated document. */
  private static ObjectNode withEntries(
      ObjectNode target,
      Map<String, Object> properties,
      JsonNode data,
      Map<String, JsonNode> variables,
      JqEvaluator jq,
      ObjectMapper mapper) {
    EntryStream.of(properties)
        .mapValues(value -> evalSetValue(value, data, variables, jq, mapper))
        .forKeyValue(target::set);
    return target;
  }

  private static JsonNode evalSetValue(
      Object value,
      JsonNode data,
      Map<String, JsonNode> variables,
      JqEvaluator jq,
      ObjectMapper mapper) {
    if (value instanceof String expr) {
      return jq.evaluate(expr, data, scope(variables));
    }
    return mapper.valueToTree(value);
  }

  /**
   * Scope-local jq bindings for this task — today only the error caught by an enclosing {@code
   * catch}, so a recovery task can read it. Null-tolerant because a request serialized before this
   * component existed omits it.
   */
  static Map<String, JsonNode> scope(Map<String, JsonNode> variables) {
    return variables == null ? Map.of() : variables;
  }
}
