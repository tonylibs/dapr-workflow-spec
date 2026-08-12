package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.databind.JsonNode;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.types.SwitchCase;
import io.serverlessworkflow.api.types.SwitchItem;
import io.serverlessworkflow.api.types.SwitchTask;
import io.serverlessworkflow.api.types.Task;
import java.util.List;
import java.util.Map;
import one.util.streamex.StreamEx;

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
    Map<String, JsonNode> scope = EvaluateSetActivity.scope(request.variables());
    List<SwitchCase> cases =
        StreamEx.of(switchTask.getSwitch()).map(SwitchItem::getSwitchCase).toList();

    // Two selections, not one: the first conditional case whose `when` is truthy wins outright, and
    // only when none matches does the default case apply. `or` keeps that fallback lazy, so a match
    // short-circuits before any later condition is evaluated. Where several cases declare no `when`
    // the last one still wins, as it did when a loop kept overwriting the remembered default.
    return StreamEx.of(cases)
        .filter(EvaluateSwitchActivity::isConditional)
        .findFirst(branch -> jq.evaluateBoolean(branch.getWhen(), request.data(), scope))
        .or(
            () ->
                StreamEx.of(cases)
                    .remove(EvaluateSwitchActivity::isConditional)
                    .reduce((_, later) -> later))
        .map(SwitchCase::getThen)
        .map(FlowOutcome::of)
        .orElse(FlowOutcome.CONTINUE);
  }

  /** A case with no {@code when} is the switch's default, not a condition to evaluate. */
  private static boolean isConditional(SwitchCase branch) {
    return branch.getWhen() != null && !branch.getWhen().isBlank();
  }
}
