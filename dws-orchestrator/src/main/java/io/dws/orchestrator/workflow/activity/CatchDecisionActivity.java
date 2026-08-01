package io.dws.orchestrator.workflow.activity;

import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;

/**
 * Decides what a try task's catch clause does with one failure.
 *
 * <p>Pure decision logic like {@link EvaluateSwitchActivity} — but here being an activity is load
 * bearing, not just conventional: the retry jitter draws a random value, and Dapr records an
 * activity's result in the instance history, so replay reuses the recorded delay instead of drawing
 * a different one. Real randomness per run, still deterministic on replay.
 */
public class CatchDecisionActivity implements WorkflowActivity {

  @Override
  public Object run(WorkflowActivityContext ctx) {
    return CatchPolicy.decide(ctx.getInput(CatchDecisionRequest.class));
  }
}
