package io.dws.step.failure;

/**
 * Verbatim port of the classification rules in {@code dws-orchestrator}'s {@code
 * io.dws.orchestrator.error.WorkflowErrors#classify} (marker constants and check order), kept in
 * test sources only. It exists so the failure-contract tests prove that this service's messages
 * classify the way v1 reads them. If v1's rules change, update this copy and the failure contract.
 */
final class V1WorkflowErrors {

  enum ErrorKind {
    VALIDATION,
    COMMUNICATION,
    TIMEOUT,
    RUNTIME
  }

  private static final String STEP_MARKER = "step '";
  private static final String DATA_FLOW_MARKER = "data flow failed:";
  private static final String TIMEOUT_MARKER = "timed out after";
  private static final String UPSTREAM_MARKER = "upstream failure:";
  private static final String CONFIG_MARKER = "config failure:";
  private static final String VALIDATION_MARKER = "validation failed:";

  private V1WorkflowErrors() {}

  static ErrorKind classify(String failureMessage) {
    String message = failureMessage == null ? "" : failureMessage;
    if (message.contains(TIMEOUT_MARKER)) {
      return ErrorKind.TIMEOUT;
    }
    if (message.contains(DATA_FLOW_MARKER)) {
      return ErrorKind.VALIDATION;
    }
    if (message.contains(VALIDATION_MARKER)) {
      return ErrorKind.VALIDATION;
    }
    if (message.contains(CONFIG_MARKER)) {
      return ErrorKind.RUNTIME;
    }
    if (message.startsWith(STEP_MARKER) || message.contains(UPSTREAM_MARKER)) {
      return ErrorKind.COMMUNICATION;
    }
    return ErrorKind.RUNTIME;
  }
}
