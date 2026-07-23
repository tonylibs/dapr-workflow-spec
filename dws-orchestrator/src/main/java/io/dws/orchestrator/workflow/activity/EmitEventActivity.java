package io.dws.orchestrator.workflow.activity;

import io.dapr.client.DaprClient;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.dws.orchestrator.workflow.WorkflowSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes the current workflow data to a Dapr pub/sub topic on behalf of an EMIT task.
 */
public class EmitEventActivity implements WorkflowActivity {

  private static final Logger LOG = LoggerFactory.getLogger(EmitEventActivity.class);

  @Override
  public Object run(WorkflowActivityContext ctx) {
    EmitRequest request = ctx.getInput(EmitRequest.class);
    DaprClient client = WorkflowSupport.daprClient();

    LOG.info("Publishing to pubsub '{}' topic '{}'", request.pubsub(), request.topic());
    client.publishEvent(request.pubsub(), request.topic(), request.data()).block();
    return Boolean.TRUE;
  }
}
