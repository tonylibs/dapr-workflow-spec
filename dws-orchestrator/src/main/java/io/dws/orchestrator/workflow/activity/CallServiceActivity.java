package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.databind.JsonNode;
import io.dapr.client.DaprClient;
import io.dapr.client.DaprHttp;
import io.dapr.client.domain.HttpExtension;
import io.dapr.exceptions.DaprException;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.dws.orchestrator.error.StepInvocationException;
import io.dws.orchestrator.workflow.WorkflowSupport;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import one.util.streamex.StreamEx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single I/O activity. Invokes a target Knative service by its Dapr app-id via service
 * invocation ({@code POST /<path>}), passing the current workflow data as JSON and returning the
 * response JSON as the new data document.
 */
public class CallServiceActivity implements WorkflowActivity {

  private static final Logger LOG = LoggerFactory.getLogger(CallServiceActivity.class);

  /**
   * Carries {@link CallRequest#workflowInstanceId()} on every outbound call, matching {@code
   * dws-call-a2a}'s {@code message_id.WORKFLOW_INSTANCE_ID_HEADER} (ADR 0004 Decision 8's
   * deterministic {@code messageId} derivation). Sent for every call kind on this activity's path
   * (openapi/grpc/asyncapi/a2a), not only a2a — harmless for the others, and needs no further
   * orchestrator change if a future runner wants it too.
   */
  private static final String WORKFLOW_INSTANCE_ID_HEADER = "X-Dws-Workflow-Instance-Id";

  /**
   * Carries {@link CallRequest#iterationIndex()} verbatim — an opaque, stable string {@code
   * dws-call-a2a}'s {@code message_id.ITERATION_INDEX_HEADER} never parses — matching that runner's
   * header name. Omitted (not sent as an empty/blank header) when the call task is not nested
   * inside a {@code for} loop.
   */
  private static final String ITERATION_INDEX_HEADER = "X-Dws-Iteration-Index";

  @Override
  public Object run(WorkflowActivityContext ctx) {
    CallRequest request = ctx.getInput(CallRequest.class);
    DaprClient client = WorkflowSupport.daprClient();

    LOG.info("Invoking app-id '{}' method '{}'", request.appId(), request.path());
    try {
      JsonNode response =
          client
              .invokeMethod(
                  request.appId(),
                  request.path(),
                  request.data(),
                  httpExtensionOf(request),
                  JsonNode.class)
              .block();

      // A 204/empty response leaves the data document unchanged.
      return response == null ? request.data() : response;
    } catch (RuntimeException e) {
      // Rethrown in a shape a catch clause can filter on: the step contract reserves 502 for
      // upstream/transport failure, and only this message crosses the activity boundary.
      throw new StepInvocationException(
          request.appId(), httpStatusOf(e), String.valueOf(e.getMessage()), e);
    }
  }

  /**
   * A {@code POST} extension carrying {@link #WORKFLOW_INSTANCE_ID_HEADER} (always) and {@link
   * #ITERATION_INDEX_HEADER} (only when the request actually has one) — a header is omitted
   * entirely rather than sent blank when its value is absent.
   */
  private static HttpExtension httpExtensionOf(CallRequest request) {
    Map<String, String> headers = new HashMap<>();
    if (request.workflowInstanceId() != null && !request.workflowInstanceId().isBlank()) {
      headers.put(WORKFLOW_INSTANCE_ID_HEADER, request.workflowInstanceId());
    }
    if (request.iterationIndex() != null && !request.iterationIndex().isBlank()) {
      headers.put(ITERATION_INDEX_HEADER, request.iterationIndex());
    }
    return new HttpExtension(DaprHttp.HttpMethods.POST, Map.of(), headers);
  }

  /**
   * The upstream HTTP status if the failure chain carries one, else {@code 0}. The status may be
   * wrapped a few levels down (the reactive call sites rewrap), so the whole cause chain is walked.
   */
  private static int httpStatusOf(Throwable failure) {
    return StreamEx.iterate(failure, Objects::nonNull, CallServiceActivity::nextCause)
        .select(DaprException.class)
        .mapToInt(DaprException::getHttpStatusCode)
        .findFirst(status -> status > 0)
        .orElse(0);
  }

  /** Ends the walk at a self-referencing cause instead of looping on it forever. */
  private static Throwable nextCause(Throwable failure) {
    Throwable cause = failure.getCause();
    return cause == failure ? null : cause;
  }
}
