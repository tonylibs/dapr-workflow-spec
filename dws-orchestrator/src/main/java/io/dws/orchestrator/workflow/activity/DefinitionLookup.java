package io.dws.orchestrator.workflow.activity;

import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;

/**
 * Resolves a task by name against the pod's one pinned definition. The in-process activities take a
 * task name rather than the task itself, so their inputs stay small and JSON-serializable.
 */
final class DefinitionLookup {

  private DefinitionLookup() {}

  static Task taskByName(String taskName) {
    for (TaskItem item : WorkflowSupport.definition().getDo()) {
      if (item.getName().equals(taskName)) {
        return item.getTask();
      }
    }
    throw new IllegalStateException("definition has no task named '" + taskName + "'");
  }
}
