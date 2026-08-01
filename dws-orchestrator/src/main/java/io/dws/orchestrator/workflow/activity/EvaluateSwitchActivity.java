package io.dws.orchestrator.workflow.activity;

import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.types.SwitchCase;
import io.serverlessworkflow.api.types.SwitchItem;
import io.serverlessworkflow.api.types.SwitchTask;
import io.serverlessworkflow.api.types.Task;

/**
 * Resolves a SWITCH task's branch. Pure jq evaluation with no I/O — it runs in the orchestrator's
 * own JVM (no network hop, nothing extra deployed) and exists as an activity purely so every task
 * type dispatches through {@code ctx.callActivity(...)} uniformly, keeping evaluation out of the
 * workflow method's replay loop.
 */
public class EvaluateSwitchActivity implements WorkflowActivity {

  @Override
  public Object run(WorkflowActivityContext ctx) {
    return evaluate(ctx.getInput(EvaluateSwitchRequest.class));
  }

  /**
   * Returns the target of the first case whose {@code when} is truthy, else the default case (the
   * one with no {@code when}); {@link FlowOutcome#CONTINUE} when neither matches.
   */
  public static FlowOutcome evaluate(EvaluateSwitchRequest request) {
    Task task = DefinitionLookup.taskByName(request.taskName());
    SwitchTask switchTask = task.getSwitchTask();
    if (switchTask == null) {
      throw new IllegalStateException("task '" + request.taskName() + "' is not a switch task");
    }

    JqEvaluator jq = WorkflowSupport.jq();
    FlowOutcome defaultThen = FlowOutcome.CONTINUE;
    for (SwitchItem item : switchTask.getSwitch()) {
      SwitchCase branch = item.getSwitchCase();
      String when = branch.getWhen();
      if (when == null || when.isBlank()) {
        defaultThen = FlowOutcome.of(branch.getThen());
      } else if (jq.evaluateBoolean(
          when, request.data(), EvaluateSetActivity.scope(request.variables()))) {
        return FlowOutcome.of(branch.getThen());
      }
    }
    return defaultThen;
  }
}
