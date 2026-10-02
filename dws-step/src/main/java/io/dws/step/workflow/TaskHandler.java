package io.dws.step.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import io.dws.step.config.SingleNodeDefinition;

/** Executes one task kind. Later phases add a handler per kind without touching routing. */
public interface TaskHandler {

  /** The one task kind this handler serves. */
  TaskKind kind();

  /**
   * Runs the node's task.
   *
   * @return the new workflow data
   * @throws io.dws.step.failure.StepFailureException on any failure, so v1 can classify it
   */
  JsonNode handle(SingleNodeDefinition definition, StepInput input);
}
