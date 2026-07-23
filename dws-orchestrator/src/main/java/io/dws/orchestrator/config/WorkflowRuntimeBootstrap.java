package io.dws.orchestrator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.client.DaprClient;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dapr.workflows.runtime.WorkflowRuntime;
import io.dapr.workflows.runtime.WorkflowRuntimeBuilder;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.registry.DefinitionRegistry;
import io.dws.orchestrator.workflow.InterpreterWorkflow;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.dws.orchestrator.workflow.activity.CallServiceActivity;
import io.dws.orchestrator.workflow.activity.EmitEventActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registers the interpreter workflow and its activities with the Dapr workflow runtime and
 * starts the runtime once the application context is ready. Also seeds {@link WorkflowSupport}
 * so the reflectively-created workflow/activity instances can reach their collaborators.
 */
@Component
public class WorkflowRuntimeBootstrap implements DisposableBean {

  private static final Logger LOG = LoggerFactory.getLogger(WorkflowRuntimeBootstrap.class);

  private final DefinitionRegistry registry;
  private final JqEvaluator jqEvaluator;
  private final ObjectMapper mapper;
  private final DaprClient daprClient;
  private final WorkflowTaskOptions defaultTaskOptions;
  private final OrchestratorProperties props;

  private volatile WorkflowRuntime runtime;
  private Thread runtimeThread;

  public WorkflowRuntimeBootstrap(DefinitionRegistry registry,
                                  JqEvaluator jqEvaluator,
                                  @Qualifier("orchestratorObjectMapper") ObjectMapper mapper,
                                  DaprClient daprClient,
                                  WorkflowTaskOptions defaultTaskOptions,
                                  OrchestratorProperties props) {
    this.registry = registry;
    this.jqEvaluator = jqEvaluator;
    this.mapper = mapper;
    this.daprClient = daprClient;
    this.defaultTaskOptions = defaultTaskOptions;
    this.props = props;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void startRuntime() {
    WorkflowSupport.init(registry, jqEvaluator, mapper, daprClient, defaultTaskOptions, props.getDefaultPubsub());

    WorkflowRuntimeBuilder builder = new WorkflowRuntimeBuilder()
        .registerWorkflow(InterpreterWorkflow.class);
    builder.registerActivity(CallServiceActivity.class);
    builder.registerActivity(EmitEventActivity.class);

    this.runtime = builder.build();
    this.runtimeThread = new Thread(() -> {
      try {
        LOG.info("Starting Dapr workflow runtime");
        runtime.start();
      } catch (Exception e) {
        LOG.error("Workflow runtime terminated", e);
      }
    }, "dws-workflow-runtime");
    runtimeThread.setDaemon(true);
    runtimeThread.start();
  }

  @Override
  public void destroy() {
    if (runtime != null) {
      try {
        runtime.close();
      } catch (Exception e) {
        LOG.warn("Error closing workflow runtime", e);
      }
    }
    if (runtimeThread != null) {
      runtimeThread.interrupt();
    }
  }
}
