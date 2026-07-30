package io.dws.orchestrator.error;

/**
 * A step-service invocation that failed.
 *
 * <p>The app-id and the upstream HTTP status are folded into the <em>message</em>, not just kept in
 * fields, because only an exception's message survives the Dapr activity boundary — the workflow
 * side sees an opaque activity failure carrying that text and nothing else. {@link WorkflowErrors}
 * reads the message back to classify the failure and recover its status.
 */
public class StepInvocationException extends RuntimeException {

  private final String appId;
  private final int status;

  /**
   * @param status the upstream HTTP status, or {@code 0} when none could be recovered — the message
   *     then omits the status clause entirely rather than claiming a misleading zero
   */
  public StepInvocationException(String appId, int status, String detail, Throwable cause) {
    super(
        status > 0
            ? "step '" + appId + "' failed with status " + status + ": " + detail
            : "step '" + appId + "' failed: " + detail,
        cause);
    this.appId = appId;
    this.status = status;
  }

  public String appId() {
    return appId;
  }

  /** The upstream HTTP status, or {@code 0} when none could be recovered. */
  public int status() {
    return status;
  }
}
