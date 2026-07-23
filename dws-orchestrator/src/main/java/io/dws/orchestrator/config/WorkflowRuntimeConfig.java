package io.dws.orchestrator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dapr.workflows.WorkflowTaskRetryPolicy;
import io.dapr.workflows.client.DaprWorkflowClient;
import io.dws.orchestrator.expr.JqEvaluator;
import io.serverlessworkflow.api.types.Workflow;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the orchestrator's collaborators as Spring beans.
 *
 * <p>The single workflow definition is resolved eagerly via {@link #workflowDefinition}; if loading
 * fails the exception aborts context startup, giving the required fail-fast, non-zero exit. A
 * dedicated Jackson 2 {@link ObjectMapper} keeps the orchestrator independent of the web layer's
 * Jackson configuration.
 */
@Configuration
@EnableConfigurationProperties(OrchestratorProperties.class)
public class WorkflowRuntimeConfig {

  @Bean("orchestratorObjectMapper")
  public ObjectMapper orchestratorObjectMapper() {
    return new ObjectMapper();
  }

  @Bean
  public JqEvaluator jqEvaluator(ObjectMapper orchestratorObjectMapper) {
    return new JqEvaluator(orchestratorObjectMapper);
  }

  @Bean(destroyMethod = "close")
  public DaprClient daprClient() {
    return new DaprClientBuilder().build();
  }

  @Bean(destroyMethod = "close")
  public DaprWorkflowClient daprWorkflowClient() {
    return new DaprWorkflowClient();
  }

  @Bean
  public WorkflowDefinitionLoader workflowDefinitionLoader(DaprClient daprClient,
                                                           OrchestratorProperties props) {
    return new WorkflowDefinitionLoader(daprClient, props);
  }

  /** Loads the pod's one definition exactly once; a failure here fails application startup. */
  @Bean
  public Workflow workflowDefinition(WorkflowDefinitionLoader loader) {
    return loader.load();
  }

  @Bean
  public WorkflowTaskOptions defaultTaskOptions(OrchestratorProperties props) {
    OrchestratorProperties.Retry retry = props.getRetry();
    WorkflowTaskRetryPolicy policy = new WorkflowTaskRetryPolicy(
        retry.getMaxAttempts(),
        retry.getFirstRetryInterval(),
        retry.getBackoffCoefficient(),
        retry.getMaxRetryInterval(),
        /* retryTimeout */ null);
    return new WorkflowTaskOptions(policy);
  }
}
