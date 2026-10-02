package io.dws.step.failure;

import static org.assertj.core.api.Assertions.assertThat;

import io.dws.step.failure.V1WorkflowErrors.ErrorKind;
import org.junit.jupiter.api.Test;

/**
 * Only a failure's message crosses the Dapr activity boundary, and v1's {@code WorkflowErrors}
 * classifies on its wording. Fixtures below are taken verbatim from {@code dws-orchestrator}'s
 * {@code WorkflowErrorsTest} (and {@code dws-run}'s worker test for the {@code 't'} prefix).
 */
class StepFailureContractTest {

  // v1 WorkflowErrorsTest.activityUpstreamFailureIsACommunicationErrorLikeThe502HttpPath
  private static final String V1_UPSTREAM = "step 'fetch-order' upstream failure: connection reset";
  // v1 WorkflowErrorsTest.activityConfigFailureIsARuntimeError
  private static final String V1_CONFIG = "step 'fetch-order' config failure: missing COMMAND";
  // v1 WorkflowErrorsTest.stepPayloadValidationFailureIsAValidationError (the step-service body)
  private static final String V1_VALIDATION_DETAIL = "message payload failed schema validation";

  @Test
  void upstreamFailureMatchesV1FixtureAndClassifiesAsCommunication() {
    StepUpstreamException failure = new StepUpstreamException("fetch-order", "connection reset");

    assertThat(failure.getMessage()).isEqualTo(V1_UPSTREAM);
    assertThat(failure.retryable()).isTrue();
    assertThat(V1WorkflowErrors.classify(failure.getMessage())).isEqualTo(ErrorKind.COMMUNICATION);
  }

  @Test
  void upstreamFailureMatchesDwsRunWorkerWording() {
    assertThat(new StepUpstreamException("t", "boom").getMessage())
        .startsWith("step 't' upstream failure:");
  }

  @Test
  void configFailureMatchesV1FixtureAndClassifiesAsRuntime() {
    StepConfigException failure = new StepConfigException("fetch-order", "missing COMMAND");

    assertThat(failure.getMessage()).isEqualTo(V1_CONFIG);
    assertThat(failure.retryable()).isFalse();
    assertThat(V1WorkflowErrors.classify(failure.getMessage())).isEqualTo(ErrorKind.RUNTIME);
  }

  @Test
  void validationFailureCarriesV1MarkerAndClassifiesAsValidation() {
    StepValidationException failure = new StepValidationException(V1_VALIDATION_DETAIL);

    assertThat(failure.getMessage())
        .isEqualTo("validation failed: message payload failed schema validation");
    assertThat(failure.retryable()).isFalse();
    assertThat(V1WorkflowErrors.classify(failure.getMessage())).isEqualTo(ErrorKind.VALIDATION);
  }

  @Test
  void validationFailureWrappedByAStepServiceStillClassifiesAsValidation() {
    // v1 fixture: the wrapped HTTP-path form, to prove the marker is the one v1 keys on.
    String wrapped =
        "step 'publish-order' failed with status 400: validation failed: message payload failed"
            + " schema validation";

    assertThat(V1WorkflowErrors.classify(wrapped)).isEqualTo(ErrorKind.VALIDATION);
    assertThat(wrapped).contains(new StepValidationException(V1_VALIDATION_DETAIL).getMessage());
  }

  @Test
  void causeIsPreservedButNotPartOfTheMessage() {
    RuntimeException cause = new RuntimeException("socket closed");

    assertThat(new StepUpstreamException("t", "x", cause).getCause()).isSameAs(cause);
    assertThat(new StepConfigException("t", "x", cause).getCause()).isSameAs(cause);
    assertThat(new StepUpstreamException("t", "x", cause).getMessage()).doesNotContain("socket");
  }

  @Test
  void portedV1RulesLeaveUnrelatedMessagesAsRuntime() {
    assertThat(V1WorkflowErrors.classify(null)).isEqualTo(ErrorKind.RUNTIME);
    assertThat(V1WorkflowErrors.classify("task 'x' has an unsupported type"))
        .isEqualTo(ErrorKind.RUNTIME);
  }
}
