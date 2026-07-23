package io.dws.orchestrator.workflow.adapter;

/**
 * Thin adapter mapping a Serverless Workflow task name to the Dapr resource it targets.
 *
 * <p>By convention a {@code call} task named {@code checkInventory} invokes the Dapr app-id
 * {@code check-inventory}; the same kebab-case mapping is used for {@code emit} topics. This is
 * the only orchestrator-specific interpretation layered on top of the SDK model.
 */
public final class TaskNaming {

  private TaskNaming() {
  }

  /** Converts a camelCase / PascalCase / snake_case task name to kebab-case. */
  public static String toKebabCase(String taskName) {
    if (taskName == null || taskName.isBlank()) {
      throw new IllegalArgumentException("task name must not be blank");
    }
    String withHyphens = taskName
        .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
        .replace('_', '-')
        .replace(' ', '-');
    return withHyphens.toLowerCase().replaceAll("-+", "-").replaceAll("^-|-$", "");
  }
}
