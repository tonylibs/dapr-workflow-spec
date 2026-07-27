package io.dws.orchestrator.workflow.activity;

import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;

/**
 * Runs the input half of a task's data-flow pipeline ({@code input.from} then {@code input.schema})
 * before the task body. Pure in-process evaluation — see {@link DataFlowPipeline}.
 */
public class DataFlowInputActivity implements WorkflowActivity {

  @Override
  public Object run(WorkflowActivityContext ctx) {
    return DataFlowPipeline.applyInput(ctx.getInput(DataFlowInputRequest.class));
  }
}
