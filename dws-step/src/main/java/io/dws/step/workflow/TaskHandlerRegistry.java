package io.dws.step.workflow;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Maps each task kind to its handler. */
public final class TaskHandlerRegistry {

  private final Map<TaskKind, TaskHandler> handlers = new EnumMap<>(TaskKind.class);

  public TaskHandlerRegistry(List<TaskHandler> registered) {
    for (TaskHandler handler : registered) {
      if (handlers.putIfAbsent(handler.kind(), handler) != null) {
        throw new IllegalArgumentException(
            "duplicate handler for task kind '" + handler.kind().key() + "'");
      }
    }
  }

  /**
   * The production registry. A later phase replaces a kind's {@link NotImplementedTaskHandler} here
   * with its real handler.
   */
  public static TaskHandlerRegistry defaults() {
    return new TaskHandlerRegistry(
        List.of(
            new NotImplementedTaskHandler(TaskKind.SET),
            new NotImplementedTaskHandler(TaskKind.SWITCH),
            new NotImplementedTaskHandler(TaskKind.EMIT),
            new NotImplementedTaskHandler(TaskKind.RAISE),
            new NotImplementedTaskHandler(TaskKind.CALL),
            new NotImplementedTaskHandler(TaskKind.RUN)));
  }

  /** The handler for {@code kind}, or {@code null} when none is registered. */
  public TaskHandler handlerFor(TaskKind kind) {
    return handlers.get(kind);
  }
}
