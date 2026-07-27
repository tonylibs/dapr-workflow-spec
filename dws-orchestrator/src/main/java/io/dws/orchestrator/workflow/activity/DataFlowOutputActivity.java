package io.dws.orchestrator.workflow.activity;

import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;

/**
 * Runs the output half of a task's data-flow pipeline after the task body: {@code output.as},
 * {@code output.schema}, then {@code export.as}/{@code export.schema}. Returns the transformed
 * output together with the workflow context this task exported — see {@link DataFlowPipeline}.
 */
public class DataFlowOutputActivity implements WorkflowActivity {

  @Override
  public Object run(WorkflowActivityContext ctx) {
    return DataFlowPipeline.applyOutput(ctx.getInput(DataFlowOutputRequest.class));
  }
}
