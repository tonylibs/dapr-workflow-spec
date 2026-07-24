package io.dws.orchestrator.workflow.activity;

import io.dapr.client.DaprClient;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.dws.orchestrator.workflow.WorkflowSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes a DWS lifecycle event envelope (instance/task) to the shared {@code dws.events} topic.
 * Mirrors {@link EmitEventActivity}, but tolerates publish failure: a failed admin publish is logged
 * and swallowed so it can never wedge the workflow instance (admin events are advisory).
 */
public class AdminEventActivity implements WorkflowActivity {

  private static final Logger LOG = LoggerFactory.getLogger(AdminEventActivity.class);

  @Override
  public Object run(WorkflowActivityContext ctx) {
    AdminEventRequest request = ctx.getInput(AdminEventRequest.class);
    try {
      DaprClient client = WorkflowSupport.daprClient();
      client.publishEvent(request.pubsub(), request.topic(), request.data()).block();
    } catch (Exception e) {
      LOG.warn("Failed to publish admin event to pubsub '{}' topic '{}' (swallowed)",
          request.pubsub(), request.topic(), e);
    }
    return Boolean.TRUE;
  }
}
