package io.dws.step.failure;

/**
 * Non-retryable configuration or shaping fault: {@code step '<task>' config failure: <detail>}. v1
 * classifies it as a runtime error.
 */
public final class StepConfigException extends StepFailureException {

  static final String MARKER = "config failure:";

  public StepConfigException(String task, String detail) {
    this(task, detail, null);
  }

  public StepConfigException(String task, String detail, Throwable cause) {
    super("step '" + task + "' " + MARKER + " " + detail, cause);
  }

  @Override
  public boolean retryable() {
    return false;
  }
}
