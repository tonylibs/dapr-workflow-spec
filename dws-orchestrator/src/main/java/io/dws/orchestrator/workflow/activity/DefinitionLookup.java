package io.dws.orchestrator.workflow.activity;

import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.ForkTask;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.TryTask;
import java.util.List;

/**
 * Resolves a task by name against the pod's one pinned definition. The in-process activities take a
 * task name rather than the task itself, so their inputs stay small and JSON-serializable.
 *
 * <p>The search descends into a try task's {@code try} and {@code catch.do} lists, a for task's
 * {@code do} list, and a fork task's {@code fork.branches} list, so a nested task is resolvable
 * exactly like a top-level one. That is sound because task names are unique across the whole
 * definition — {@code dws-controller} rejects duplicates at compile time, since a {@code
 * call}/{@code run} task's Dapr app-id is derived from its name alone.
 */
public final class DefinitionLookup {

  private DefinitionLookup() {}

  public static Task taskByName(String taskName) {
    Task found = search(WorkflowSupport.definition().getDo(), taskName);
    if (found == null) {
      throw new IllegalStateException("definition has no task named '" + taskName + "'");
    }
    return found;
  }

  /** Depth-first search; names are unique, so the first match is the only match. */
  private static Task search(List<TaskItem> items, String taskName) {
    if (items == null) {
      return null;
    }
    for (TaskItem item : items) {
      if (item.getName().equals(taskName)) {
        return item.getTask();
      }
      Task task = item.getTask();
      if (task == null) {
        continue;
      }
      TryTask tryTask = task.getTryTask();
      if (tryTask != null) {
        Task nested = search(tryTask.getTry(), taskName);
        if (nested == null && tryTask.getCatch() != null) {
          nested = search(tryTask.getCatch().getDo(), taskName);
        }
        if (nested != null) {
          return nested;
        }
      }
      ForTask forTask = task.getForTask();
      if (forTask != null) {
        Task nested = search(forTask.getDo(), taskName);
        if (nested != null) {
          return nested;
        }
      }
      ForkTask forkTask = task.getForkTask();
      if (forkTask != null) {
        Task nested = search(forkTask.getFork().getBranches(), taskName);
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
  }
}
