package io.dws.step.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.dws.step.config.SingleNodeDefinition;
import io.dws.step.config.StepDefinitionHolder;
import io.dws.step.failure.StepConfigException;

/**
 * The constant-named {@code Step} activity: routes by the pinned node's task kind to that kind's
 * {@link TaskHandler}. Input is a {@link StepInput}; output is the new workflow data.
 */
public class StepActivity implements WorkflowActivity {

  public static final String NAME = "Step";

  private final SingleNodeDefinition definition;
  private final TaskHandlerRegistry registry;

  /** Used by Dapr, which creates activities reflectively. */
  public StepActivity() {
    this(StepDefinitionHolder.definition(), TaskHandlerRegistry.defaults());
  }

  public StepActivity(SingleNodeDefinition definition, TaskHandlerRegistry registry) {
    this.definition = definition;
    this.registry = registry;
  }

  @Override
  public JsonNode run(WorkflowActivityContext context) {
    TaskKind kind = definition.kind();
    TaskHandler handler = registry.handlerFor(kind);
    if (handler == null) {
      throw new StepConfigException(
          definition.nodeId(), "no handler registered for task kind '" + kind.key() + "'");
    }
    StepInput input = context.getInput(StepInput.class);
    if (input == null) {
      throw new StepConfigException(definition.nodeId(), "activity input is missing");
    }
    return handler.handle(definition, input);
  }
}
