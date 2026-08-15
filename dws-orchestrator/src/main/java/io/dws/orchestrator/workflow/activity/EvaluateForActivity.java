package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.databind.JsonNode;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.ForTaskConfiguration;
import io.serverlessworkflow.api.types.Task;
import java.util.Optional;

/**
 * Evaluates a FOR task's {@code for.in} expression to the collection to iterate. Pure jq evaluation
 * with no I/O — parallel to {@link EvaluateSwitchActivity}, it runs in the orchestrator's own JVM
 * and exists as an activity purely so every task type dispatches through {@code
 * ctx.callActivity(...)} uniformly, keeping evaluation out of the workflow method's replay loop.
 * Rejects a non-array result with an {@link IllegalStateException} so the failure flows through the
 * standard task-failure path.
 */
public class EvaluateForActivity implements WorkflowActivity {

  @Override
  public Object run(WorkflowActivityContext ctx) {
    return apply(ctx.getInput(EvaluateForRequest.class));
  }

  public static JsonNode apply(EvaluateForRequest request) {
    Task task = DefinitionLookup.taskByName(request.taskName());
    ForTask forTask = task.getForTask();
    if (forTask == null) {
      throw new IllegalStateException("task '" + request.taskName() + "' is not a for task");
    }

    String expression =
        Optional.ofNullable(forTask.getFor())
            .map(ForTaskConfiguration::getIn)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "task '" + request.taskName() + "' has no for.in expression"));

    JqEvaluator jq = WorkflowSupport.jq();
    return Optional.ofNullable(
            jq.evaluate(expression, request.data(), EvaluateSetActivity.scope(request.variables())))
        .filter(JsonNode::isArray)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "for task '" + request.taskName() + "' expected in to evaluate to an array"));
  }
}
