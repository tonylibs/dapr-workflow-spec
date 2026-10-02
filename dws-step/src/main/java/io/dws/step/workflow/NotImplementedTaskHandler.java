package io.dws.step.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import io.dws.step.config.SingleNodeDefinition;
import io.dws.step.failure.StepConfigException;

/** Placeholder for a kind whose behavior has not landed: a non-retryable config failure. */
public record NotImplementedTaskHandler(TaskKind kind) implements TaskHandler {

  @Override
  public JsonNode handle(SingleNodeDefinition definition, StepInput input) {
    throw new StepConfigException(
        definition.nodeId(), "task kind '" + kind.key() + "' is not implemented yet");
  }
}
