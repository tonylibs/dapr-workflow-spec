package io.dws.orchestrator.dataflow;

/**
 * Raised when a task's data-flow pipeline fails — an {@code input.from}/{@code output.as}/{@code
 * export.as} expression that cannot be evaluated, an {@code input.schema}/{@code output.schema}
 * validation that does not pass, or an unsupported schema form.
 *
 * <p>This is the <em>minimal</em> fault shape: it propagates as an ordinary {@link
 * RuntimeException} out of the data-flow activity, so it reaches the interpreter through the same
 * path as any other task failure and is reported as {@code io.dws.task.failed} / {@code
 * io.dws.instance.failed}. RFC 7807 Problem Details formatting and the standard Open Workflow
 * Specification error types are deliberately out of scope here.
 *
 * <p>Only an exception's <em>message</em> survives the Dapr activity boundary, so the task name,
 * phase, and failure detail are all folded into the message rather than kept solely in fields.
 */
public class DataFlowException extends RuntimeException {

  /** The pipeline stage that failed. */
  public enum Phase {
    INPUT,
    OUTPUT,
    EXPORT;

    /** Lowercase label used in fault messages. */
    public String label() {
      return name().toLowerCase();
    }
  }

  private final String taskName;
  private final Phase phase;

  public DataFlowException(String taskName, Phase phase, String detail) {
    this(taskName, phase, detail, null);
  }

  public DataFlowException(String taskName, Phase phase, String detail, Throwable cause) {
    super("task '" + taskName + "' " + phase.label() + " data flow failed: " + detail, cause);
    this.taskName = taskName;
    this.phase = phase;
  }

  public String taskName() {
    return taskName;
  }

  public Phase phase() {
    return phase;
  }
}
