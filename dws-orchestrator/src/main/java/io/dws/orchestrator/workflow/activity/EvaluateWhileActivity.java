package io.dws.orchestrator.workflow.activity;

import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.Task;

/**
 * Evaluates a FOR task's {@code while} expression for jq truthiness. Pure jq evaluation with no I/O
 * — parallel to {@link EvaluateSwitchActivity}, it exists as an activity so evaluation stays out of
 * the workflow method's replay loop. Called once per iteration by {@code dispatchFor}.
 */
public class EvaluateWhileActivity implements WorkflowActivity {

  @Override
  public Object run(WorkflowActivityContext ctx) {
    return apply(ctx.getInput(EvaluateWhileRequest.class));
  }

  public static boolean apply(EvaluateWhileRequest request) {
    Task task = DefinitionLookup.taskByName(request.taskName());
    ForTask forTask = task.getForTask();
    if (forTask == null) {
      throw new IllegalStateException("task '" + request.taskName() + "' is not a for task");
    }
    String expression = forTask.getWhile();
    if (expression == null || expression.isBlank()) {
      throw new IllegalStateException(
          "for task '" + request.taskName() + "' declares no while expression");
    }
    JqEvaluator jq = WorkflowSupport.jq();
    return jq.evaluateBoolean(
        expression, request.data(), EvaluateSetActivity.scope(request.variables()));
  }
}
