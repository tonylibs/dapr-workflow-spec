package io.dws.controller.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.dapr.client.DaprClient;
import io.dapr.client.domain.ConfigurationItem;
import io.dws.controller.config.DwsConfig;
import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.ImageCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class CompilerStrategyTest {

  private static final ImageCatalog IMAGES =
      new ImageCatalog(
          "sw-call-http:1.0",
          "sw-call-openapi:1.0",
          "sw-call-grpc:1.0",
          "sw-call-asyncapi:1.0",
          "sw-run-shell:1.0",
          "sw-run-script-js:1.0",
          "sw-run-script-python:1.0",
          "sw-orchestrator:1.0");

  private static final String MINIMAL =
      """
      document:
        dsl: '1.0.0'
        namespace: default
        name: minimal
        version: '1.0.0'
      do:
        - finish:
            set:
              done: true
      """;

  private final DwsConfig config = mock(DwsConfig.class);
  private final OpenApiDocumentFetcher fetcher = url -> new byte[0];

  CompilerStrategyTest() {
    when(config.catalog()).thenReturn(IMAGES);
  }

  @Test
  @DisplayName("v1 compiler leaves flowStepGraph empty and populates legacy fields")
  void v1LeavesGraphEmpty() {
    DeploymentPlan plan = new V1OrchestratorCompiler(IMAGES, fetcher).compile(MINIMAL);
    assertThat(plan.flowStepGraph()).isEmpty();
    assertThat(plan.workflow()).isEqualTo("minimal");
    assertThat(plan.orchestrator()).isNotNull();
  }

  @Test
  @DisplayName("v2 compiler populates flowStepGraph and leaves legacy steps empty")
  void v2LeavesLegacyEmpty() {
    DeploymentPlan plan = new V2StructuralCompiler().compile(MINIMAL);
    assertThat(plan.steps()).isEmpty();
    assertThat(plan.orchestrator()).isNull();
    assertThat(plan.flowStepGraph()).hasSize(1);
    assertThat(plan.flowStepGraph().get(0).appId()).isEqualTo("minimal-main");
    assertThat(plan.workflow()).isEqualTo("minimal");
  }

  @Test
  @DisplayName("producer defaults to v1 when the config flag is absent")
  void producerDefaultsToV1WhenFlagAbsent() {
    DaprClient client = mock(DaprClient.class);
    when(client.getConfiguration(CompilerProducer.CONFIG_STORE, CompilerProducer.VERSION_KEY))
        .thenReturn(Mono.empty());

    WorkflowCompiler compiler = new CompilerProducer().workflowCompiler(config, fetcher, client);

    assertThat(compiler).isInstanceOf(V1OrchestratorCompiler.class);
  }

  @Test
  @DisplayName("producer defaults to v1 when the config fetch fails")
  void producerDefaultsToV1OnError() {
    DaprClient client = mock(DaprClient.class);
    when(client.getConfiguration(CompilerProducer.CONFIG_STORE, CompilerProducer.VERSION_KEY))
        .thenThrow(new RuntimeException("no sidecar"));

    WorkflowCompiler compiler = new CompilerProducer().workflowCompiler(config, fetcher, client);

    assertThat(compiler).isInstanceOf(V1OrchestratorCompiler.class);
  }

  @Test
  @DisplayName("producer selects v2 when the config flag resolves to v2")
  void producerSelectsV2() {
    DaprClient client = mock(DaprClient.class);
    when(client.getConfiguration(CompilerProducer.CONFIG_STORE, CompilerProducer.VERSION_KEY))
        .thenReturn(Mono.just(new ConfigurationItem(CompilerProducer.VERSION_KEY, "v2", "")));

    WorkflowCompiler compiler = new CompilerProducer().workflowCompiler(config, fetcher, client);

    assertThat(compiler).isInstanceOf(V2StructuralCompiler.class);
  }
}
