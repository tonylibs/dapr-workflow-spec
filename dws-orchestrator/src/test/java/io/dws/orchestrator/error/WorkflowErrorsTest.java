package io.dws.orchestrator.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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
  void activityUpstreamFailureIsACommunicationErrorLikeThe502HttpPath() {
    // The activity worker's upstream marker and the HTTP path's 502 are the same fault, so a catch
    // clause must see identical type/status/title regardless of which path produced the failure.
    String activity = "step 'fetch-order' upstream failure: connection reset";
    String http = "step 'fetch-order' failed with status 502: bad gateway";

    assertThat(WorkflowErrors.classify(activity)).isEqualTo(ErrorKind.COMMUNICATION);
    assertThat(WorkflowErrors.statusOf(activity, ErrorKind.COMMUNICATION)).isEqualTo(502);

    JsonNode fromActivity = WorkflowErrors.of(activity, "fetchOrder", mapper);
    JsonNode fromHttp = WorkflowErrors.of(http, "fetchOrder", mapper);
    assertThat(fromActivity.get("type")).isEqualTo(fromHttp.get("type"));
    assertThat(fromActivity.get("status")).isEqualTo(fromHttp.get("status"));
    assertThat(fromActivity.get("title")).isEqualTo(fromHttp.get("title"));
  }

  @Test
  void activityConfigFailureIsARuntimeError() {
    // A config/shaping fault is non-retryable and classifies distinctly from a communication error,
    // even though its message opens with the same `step '<name>'` prefix.
    String message = "step 'fetch-order' config failure: missing COMMAND";

    assertThat(WorkflowErrors.classify(message)).isEqualTo(ErrorKind.RUNTIME);
    assertThat(WorkflowErrors.statusOf(message, ErrorKind.RUNTIME)).isEqualTo(500);
    assertThat(WorkflowErrors.of(message, "fetchOrder", mapper).get("title").textValue())
        .isEqualTo("Runtime error");
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
        .isEqualTo("https://serverlessworkflow.io/spec/1.0.0/errors/communication");
    assertThat(error.get("status").intValue()).isEqualTo(503);
    assertThat(error.get("instance").textValue()).isEqualTo("/fetchOrder");
    assertThat(error.get("title").textValue()).isEqualTo("Communication error");
    assertThat(error.get("detail").textValue()).isEqualTo(STEP_503);
  }

  @Test
  void everyKindsTypeUriIsUnderTheStandardNamespace() {
    for (ErrorKind kind : ErrorKind.values()) {
      assertThat(kind.typeUri())
          .startsWith("https://serverlessworkflow.io/spec/1.0.0/errors/")
          .endsWith(
              switch (kind) {
                case VALIDATION -> "validation";
                case COMMUNICATION -> "communication";
                case AUTHORIZATION -> "authorization";
                case EXPRESSION -> "expression";
                case TIMEOUT -> "timeout";
                case RUNTIME -> "runtime";
              });
    }
  }

  @Test
  void taskTimeoutMessageIsATimeoutError() {
    String message = "task 'chargePayment' timed out after PT30S";

    assertThat(WorkflowErrors.classify(message)).isEqualTo(ErrorKind.TIMEOUT);
    assertThat(WorkflowErrors.statusOf(message, ErrorKind.TIMEOUT)).isEqualTo(408);
    assertThat(WorkflowErrors.failingTaskName(message, "guarded")).isEqualTo("chargePayment");
    assertThat(WorkflowErrors.of(message, "guarded", mapper).get("title").textValue())
        .isEqualTo("Timeout error");
  }

  @Test
  void workflowTimeoutMessageIsATimeoutErrorWithNoTaskName() {
    String message = "workflow timed out after PT1H";

    assertThat(WorkflowErrors.classify(message)).isEqualTo(ErrorKind.TIMEOUT);
    assertThat(WorkflowErrors.statusOf(message, ErrorKind.TIMEOUT)).isEqualTo(408);
    assertThat(WorkflowErrors.failingTaskName(message, "guarded")).isEqualTo("guarded");
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
  void raisedErrorSurvivesUnchangedThroughOf() {
    // A raised error is author-authoritative: classification must not touch any of its five fields.
    ObjectNode raised = mapper.createObjectNode();
    raised.put("type", "https://example.com/errors/insufficient-funds");
    raised.put("status", 402);
    raised.put("instance", "/chargePayment");
    raised.put("title", "Insufficient funds");
    raised.put("detail", "balance 10.00 < amount 25.00");
    RaisedErrorException exception = new RaisedErrorException(raised);

    JsonNode result = WorkflowErrors.of(exception.getMessage(), "guarded", mapper);

    assertThat(result).isEqualTo(raised);
  }

  @Test
  void raisedErrorMessageCarriesTheMarkerAndTheJson() {
    // Only the message survives the activity boundary, so the whole object must live in it.
    ObjectNode raised = mapper.createObjectNode();
    raised.put("type", "https://example.com/errors/x");
    raised.put("status", 400);

    RaisedErrorException exception = new RaisedErrorException(raised);

    assertThat(exception.getMessage()).startsWith("raised error: ").contains("\"status\":400");
  }

  @Test
  void raisedErrorDetailContainingAnotherMarkerIsStillNotReclassified() {
    // The short-circuit is a prefix check, so a `detail` quoting a step failure stays a raised
    // error rather than being re-read as a communication failure.
    ObjectNode raised = mapper.createObjectNode();
    raised.put("type", "https://example.com/errors/wrapped");
    raised.put("status", 402);
    raised.put("instance", "/explode");
    raised.put("title", "Wrapped");
    raised.put("detail", STEP_503);

    JsonNode result = WorkflowErrors.of(new RaisedErrorException(raised).getMessage(), "x", mapper);

    assertThat(result.get("type").textValue()).isEqualTo("https://example.com/errors/wrapped");
    assertThat(result.get("status").intValue()).isEqualTo(402);
    assertThat(result.get("title").textValue()).isEqualTo("Wrapped");
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
