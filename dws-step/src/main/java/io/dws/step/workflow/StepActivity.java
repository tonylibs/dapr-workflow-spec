package io.dws.step.workflow;

import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.dws.step.config.StepDefinitionHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Phase-zero no-op activity; task execution and function proxying land in a later phase. */
public class StepActivity implements WorkflowActivity {

  public static final String NAME = "Step";
  private static final Logger LOG = LoggerFactory.getLogger(StepActivity.class);

  @Override
  public Object run(WorkflowActivityContext context) {
    LOG.info(
        "Running no-op Step activity for task kind '{}'",
        StepDefinitionHolder.definition().taskKind());
    return null;
  }
}
