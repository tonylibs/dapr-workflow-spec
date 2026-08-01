package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.databind.JsonNode;
import io.dapr.client.DaprClient;
import io.dapr.client.domain.HttpExtension;
import io.dapr.exceptions.DaprException;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.dws.orchestrator.error.StepInvocationException;
import io.dws.orchestrator.workflow.WorkflowSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single I/O activity. Invokes a target Knative service by its Dapr app-id via service
 * invocation ({@code POST /<path>}), passing the current workflow data as JSON and returning the
 * response JSON as the new data document.
 */
public class CallServiceActivity implements WorkflowActivity {

  private static final Logger LOG = LoggerFactory.getLogger(CallServiceActivity.class);

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
                  HttpExtension.POST,
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
   * The upstream HTTP status if the failure chain carries one, else {@code 0}. The status may be
   * wrapped a few levels down (the reactive call sites rewrap), so the whole cause chain is walked.
   */
  private static int httpStatusOf(Throwable failure) {
    for (Throwable t = failure; t != null && t != t.getCause(); t = t.getCause()) {
      if (t instanceof DaprException dapr && dapr.getHttpStatusCode() > 0) {
        return dapr.getHttpStatusCode();
      }
    }
    return 0;
  }
}
