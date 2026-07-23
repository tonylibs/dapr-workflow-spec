package io.dws.orchestrator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dapr.workflows.WorkflowTaskRetryPolicy;
import io.dapr.workflows.client.DaprWorkflowClient;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.loader.DefinitionLoader;
import io.dws.orchestrator.registry.DefinitionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Wires the orchestrator's collaborators as Spring beans.
 *
 * <p>A dedicated Jackson 2 {@link ObjectMapper} ({@code orchestratorObjectMapper}) is used for
 * definition parsing, jq evaluation and Dapr payloads, keeping the orchestrator independent of
 * whichever Jackson the web layer is configured with.
 */
@Configuration
@EnableConfigurationProperties(OrchestratorProperties.class)
public class WorkflowRuntimeConfig {

  private static final Logger LOG = LoggerFactory.getLogger(WorkflowRuntimeConfig.class);

  @Bean("orchestratorObjectMapper")
  public ObjectMapper orchestratorObjectMapper() {
    return new ObjectMapper();
  }

  @Bean
  public JqEvaluator jqEvaluator(ObjectMapper orchestratorObjectMapper) {
    return new JqEvaluator(orchestratorObjectMapper);
  }

  @Bean
  public DefinitionRegistry definitionRegistry(OrchestratorProperties props,
                                               ObjectMapper orchestratorObjectMapper) {
    DefinitionRegistry registry =
        new DefinitionLoader(orchestratorObjectMapper).loadDirectory(Path.of(props.getDefinitionsPath()));
    LOG.info("Loaded {} workflow definition version(s) from {}", registry.size(), props.getDefinitionsPath());
    return registry;
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
