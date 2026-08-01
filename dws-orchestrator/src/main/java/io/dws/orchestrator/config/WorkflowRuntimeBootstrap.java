package io.dws.orchestrator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.client.DaprClient;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dapr.workflows.runtime.WorkflowRuntime;
import io.dapr.workflows.runtime.WorkflowRuntimeBuilder;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.InterpreterWorkflow;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.dws.orchestrator.workflow.activity.AdminEventActivity;
import io.dws.orchestrator.workflow.activity.CallServiceActivity;
import io.dws.orchestrator.workflow.activity.CatchDecisionActivity;
import io.dws.orchestrator.workflow.activity.DataFlowInputActivity;
import io.dws.orchestrator.workflow.activity.DataFlowOutputActivity;
import io.dws.orchestrator.workflow.activity.EmitEventActivity;
import io.dws.orchestrator.workflow.activity.EvaluateSetActivity;
import io.dws.orchestrator.workflow.activity.EvaluateSwitchActivity;
import io.serverlessworkflow.api.types.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registers the interpreter workflow (named from {@code document.name}) and its activities with the
 * Dapr workflow runtime, and starts the runtime once the context is ready. Seeds {@link
 * WorkflowSupport} so the reflectively-created workflow/activity instances reach their
 * collaborators. The definition itself is already loaded (fail-fast) as a bean.
 */
@Component
public class WorkflowRuntimeBootstrap implements DisposableBean {

  private static final Logger LOG = LoggerFactory.getLogger(WorkflowRuntimeBootstrap.class);

  private final Workflow definition;
  private final JqEvaluator jqEvaluator;
  private final ObjectMapper mapper;
  private final DaprClient daprClient;
  private final WorkflowTaskOptions defaultTaskOptions;
  private final OrchestratorProperties props;

  private volatile WorkflowRuntime runtime;
  private Thread runtimeThread;

  public WorkflowRuntimeBootstrap(
      Workflow definition,
      JqEvaluator jqEvaluator,
      @Qualifier("orchestratorObjectMapper") ObjectMapper mapper,
      DaprClient daprClient,
      WorkflowTaskOptions defaultTaskOptions,
      OrchestratorProperties props) {
    this.definition = definition;
    this.jqEvaluator = jqEvaluator;
    this.mapper = mapper;
    this.daprClient = daprClient;
    this.defaultTaskOptions = defaultTaskOptions;
    this.props = props;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void startRuntime() {
    String workflowName = definition.getDocument().getName();
    String appId =
        (props.getAppId() != null && !props.getAppId().isBlank()) ? props.getAppId() : workflowName;
    WorkflowSupport.init(
        definition,
        workflowName,
        appId,
        props.getDefinitionKey(),
        jqEvaluator,
        mapper,
        daprClient,
        defaultTaskOptions,
        props.getDefaultPubsub());

    WorkflowRuntimeBuilder builder =
        new WorkflowRuntimeBuilder().registerWorkflow(workflowName, InterpreterWorkflow.class);
    builder.registerActivity(CallServiceActivity.class);
    builder.registerActivity(EmitEventActivity.class);
    builder.registerActivity(AdminEventActivity.class);
    builder.registerActivity(EvaluateSwitchActivity.class);
    builder.registerActivity(EvaluateSetActivity.class);
    builder.registerActivity(DataFlowInputActivity.class);
    builder.registerActivity(DataFlowOutputActivity.class);
    builder.registerActivity(CatchDecisionActivity.class);

    this.runtime = builder.build();
    this.runtimeThread =
        new Thread(
            () -> {
              try {
                LOG.info("Starting Dapr workflow runtime for '{}'", workflowName);
                runtime.start();
              } catch (Exception e) {
                LOG.error("Workflow runtime terminated", e);
              }
            },
            "dws-workflow-runtime");
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
