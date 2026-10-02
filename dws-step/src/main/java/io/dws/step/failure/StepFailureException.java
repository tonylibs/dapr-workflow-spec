package io.dws.step.failure;

/**
 * A Step activity failure. Only the exception <em>message</em> survives the Dapr activity boundary,
 * and {@code dws-orchestrator}'s {@code WorkflowErrors.classify} reads its wording, so each subtype
 * builds a message byte-identical to v1's. Retryability is carried by the exception type.
 */
public abstract sealed class StepFailureException extends RuntimeException
    permits StepUpstreamException, StepConfigException, StepValidationException {

  protected StepFailureException(String message, Throwable cause) {
    super(message, cause);
  }

  /** Whether the Flow may retry the activity after this failure. */
  public abstract boolean retryable();
}
