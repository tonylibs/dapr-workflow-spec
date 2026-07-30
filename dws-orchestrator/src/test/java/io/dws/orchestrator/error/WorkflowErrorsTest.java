package io.dws.orchestrator.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dws.orchestrator.dataflow.DataFlowException;
import io.dws.orchestrator.dataflow.DataFlowException.Phase;
import org.junit.jupiter.api.Test;

/**
 * Classification reads the failure <em>message</em>, not the exception type, because a failure
 * raised inside a Dapr activity reaches the workflow as an opaque activity failure whose message is
 * the only surviving detail. These tests pin the markers that classification depends on.
 */
class WorkflowErrorsTest {

  private static final String STEP_503 = "step 'fetch-order' failed with status 503: upstream down";

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void dataFlowFailureIsAValidationError() {
    String message =
        new DataFlowException("chargePayment", Phase.OUTPUT, "/total: must be a number")
            .getMessage();

    assertThat(WorkflowErrors.classify(message)).isEqualTo(ErrorKind.VALIDATION);
    assertThat(WorkflowErrors.statusOf(message, ErrorKind.VALIDATION)).isEqualTo(400);
  }

  @Test
  void stepFailureIsACommunicationErrorCarryingItsStatus() {
    assertThat(WorkflowErrors.classify(STEP_503)).isEqualTo(ErrorKind.COMMUNICATION);
    assertThat(WorkflowErrors.statusOf(STEP_503, ErrorKind.COMMUNICATION)).isEqualTo(503);
  }

  @Test
  void stepFailureWithoutARecoverableStatusDefaultsTo502() {
    String message =
        new StepInvocationException("fetch-order", 0, "connection reset", null).getMessage();

    assertThat(WorkflowErrors.classify(message)).isEqualTo(ErrorKind.COMMUNICATION);
    assertThat(WorkflowErrors.statusOf(message, ErrorKind.COMMUNICATION)).isEqualTo(502);
  }

  @Test
  void anythingElseIsARuntimeError() {
    String message = "task 'x' has an unsupported type";

    assertThat(WorkflowErrors.classify(message)).isEqualTo(ErrorKind.RUNTIME);
    assertThat(WorkflowErrors.statusOf(message, ErrorKind.RUNTIME)).isEqualTo(500);
  }

  @Test
  void nullMessageIsARuntimeError() {
    assertThat(WorkflowErrors.classify(null)).isEqualTo(ErrorKind.RUNTIME);
    assertThat(WorkflowErrors.statusOf(null, ErrorKind.RUNTIME)).isEqualTo(500);
  }

  @Test
  void buildProducesTheFiveDslFields() {
    ObjectNode error =
        WorkflowErrors.build(ErrorKind.COMMUNICATION, 503, "fetchOrder", STEP_503, mapper);

    assertThat(error.get("type").textValue())
        .isEqualTo("https://open-workflow-specification.org/dsl/errors/types/communication");
    assertThat(error.get("status").intValue()).isEqualTo(503);
    assertThat(error.get("instance").textValue()).isEqualTo("/fetchOrder");
    assertThat(error.get("title").textValue()).isEqualTo("Communication error");
    assertThat(error.get("detail").textValue()).isEqualTo(STEP_503);
  }

  @Test
  void instanceNamesTheFailingTaskNotTheEnclosingTryTask() {
    // The message shape the interpreter and DataFlowException both use: "task '<name>' ...".
    assertThat(
            WorkflowErrors.failingTaskName(
                "task 'fetchOrder' output data flow failed: x", "guarded"))
        .isEqualTo("fetchOrder");
    assertThat(
            WorkflowErrors.failingTaskName(
                "step 'fetch-order' failed with status 503: down", "guarded"))
        .isEqualTo("guarded");
    assertThat(WorkflowErrors.failingTaskName(null, "guarded")).isEqualTo("guarded");
  }

  @Test
  void stepInvocationExceptionFoldsAppIdAndStatusIntoItsMessage() {
    // Only the message survives the activity boundary, so both must be in it.
    StepInvocationException withStatus =
        new StepInvocationException("fetch-order", 503, "upstream down", null);
    StepInvocationException withoutStatus =
        new StepInvocationException("fetch-order", 0, "connection reset", null);

    assertThat(withStatus.getMessage())
        .contains("fetch-order")
        .contains("503")
        .contains("upstream down");
    assertThat(withStatus.appId()).isEqualTo("fetch-order");
    assertThat(withStatus.status()).isEqualTo(503);
    assertThat(withoutStatus.getMessage()).contains("fetch-order").doesNotContain("status");
  }
}
