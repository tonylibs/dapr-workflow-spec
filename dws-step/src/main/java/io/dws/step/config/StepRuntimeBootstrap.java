package io.dws.step.config;

import io.dapr.workflows.runtime.WorkflowRuntime;
import io.dapr.workflows.runtime.WorkflowRuntimeBuilder;
import io.dws.step.workflow.StepActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Registers the one constant-named Step activity and starts the Dapr workflow worker. */
@Component
public class StepRuntimeBootstrap implements DisposableBean {

  private static final Logger LOG = LoggerFactory.getLogger(StepRuntimeBootstrap.class);

  private final SingleNodeDefinition definition;
  private volatile WorkflowRuntime runtime;
  private Thread runtimeThread;

  public StepRuntimeBootstrap(SingleNodeDefinition definition) {
    this.definition = definition;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void startRuntime() {
    StepDefinitionHolder.initialize(definition);
    runtime =
        new WorkflowRuntimeBuilder()
            .registerActivity(StepActivity.NAME, StepActivity.class)
            .build();
    runtimeThread =
        new Thread(
            () -> {
              try {
                LOG.info("Starting Step activity worker for node '{}'", definition.nodeId());
                runtime.start();
              } catch (Exception e) {
                LOG.error("Step activity worker terminated", e);
              }
            },
            "dws-step-workflow-runtime");
    runtimeThread.setDaemon(true);
    runtimeThread.start();
  }

  @Override
  public void destroy() {
    if (runtime != null) {
      try {
        runtime.close();
      } catch (Exception e) {
        LOG.warn("Error closing Step activity worker", e);
      }
    }
    if (runtimeThread != null) {
      runtimeThread.interrupt();
    }
  }
}
