package io.dws.step.failure;

/**
 * Retryable transport or upstream failure: {@code step '<task>' upstream failure: <detail>}. v1
 * classifies it as communication (the activity-path equivalent of the HTTP path's 502).
 */
public final class StepUpstreamException extends StepFailureException {

  static final String MARKER = "upstream failure:";

  public StepUpstreamException(String task, String detail) {
    this(task, detail, null);
  }

  public StepUpstreamException(String task, String detail, Throwable cause) {
    super("step '" + task + "' " + MARKER + " " + detail, cause);
  }

  @Override
  public boolean retryable() {
    return true;
  }
}
