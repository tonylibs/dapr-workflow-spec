package io.dws.controller.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import io.dapr.client.DaprClient;
import io.dapr.client.domain.ConfigurationItem;
import io.dws.controller.compile.WorkflowCompiler;
import io.dws.controller.model.DeploymentPlan;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * The controller's {@code DWS_LOG_FORMAT} reaches a really applied orchestrator Deployment: with
 * {@code dws.log.format=json} (what the environment variable binds to) and observability on, the
 * stored Deployment carries {@code DWS_LOG_FORMAT=json}.
 */
@QuarkusTest
@WithKubernetesTestServer
@TestProfile(StackApplierJsonLogFormatTest.JsonLogs.class)
class StackApplierJsonLogFormatTest {

  public static class JsonLogs implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("dws.log.format", "json");
    }
  }

  private static final String NAMESPACE = "default";

  @Inject KubernetesClient client;

  @Inject WorkflowCompiler compiler;

  @Inject StackApplier applier;

  @Inject DaprClient daprClient;

  @BeforeEach
  void setUp() {
    reset(daprClient);
    when(daprClient.publishEvent(anyString(), anyString(), any())).thenReturn(Mono.empty());
    applier.deleteWorkflow("order");
  }

  @AfterEach
  void removeTracingConfiguration() {
    client
        .genericKubernetesResources(ResourceContexts.DAPR_CONFIGURATION)
        .inNamespace(NAMESPACE)
        .withName(ObservabilitySettings.TRACING_CONFIGURATION)
        .delete();
  }

  private void observabilityOn(boolean on) {
    when(daprClient.getConfiguration(
            ObservabilitySettings.STORE,
            ObservabilitySettings.ENABLED_KEY,
            ObservabilitySettings.INSTRUMENTATION_KEY))
        .thenReturn(
            Mono.just(
                Map.of(
                    ObservabilitySettings.ENABLED_KEY,
                    new ConfigurationItem(ObservabilitySettings.ENABLED_KEY, "" + on, "1"))));
    client
        .genericKubernetesResources(ResourceContexts.DAPR_CONFIGURATION)
        .inNamespace(NAMESPACE)
        .resource(
            new GenericKubernetesResourceBuilder()
                .withApiVersion("dapr.io/v1alpha1")
                .withKind("Configuration")
                .withNewMetadata()
                .withName(ObservabilitySettings.TRACING_CONFIGURATION)
                .withNamespace(NAMESPACE)
                .endMetadata()
                .addToAdditionalProperties("spec", Map.of("tracing", Map.of("samplingRate", "1")))
                .build())
        .createOr(NonDeletingOperation::update);
  }

  private Map<String, String> appliedOrchestratorEnv() throws Exception {
    DeploymentPlan plan =
        compiler.compile(
            new String(
                getClass().getResourceAsStream("/fixtures/order.yaml").readAllBytes(),
                StandardCharsets.UTF_8));
    applier.apply(plan);
    Deployment deployment =
        client
            .apps()
            .deployments()
            .inNamespace(NAMESPACE)
            .withName(plan.orchestrator().name())
            .get();
    Map<String, String> env = new java.util.LinkedHashMap<>();
    for (EnvVar var :
        deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv()) {
      env.put(var.getName(), var.getValue());
    }
    return env;
  }

  @Test
  @DisplayName("json controller config and observability on stamp DWS_LOG_FORMAT=json")
  void stampsJsonWhenInstrumented() throws Exception {
    observabilityOn(true);

    assertThat(appliedOrchestratorEnv()).containsEntry("DWS_LOG_FORMAT", "json");
  }

  @Test
  @DisplayName("json controller config with observability off leaves the orchestrator unstamped")
  void stampsNothingWhenNotInstrumented() throws Exception {
    observabilityOn(false);

    assertThat(appliedOrchestratorEnv()).doesNotContainKey("DWS_LOG_FORMAT");
  }
}
