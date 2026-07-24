package io.dws.controller.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.dapr.client.DaprClient;
import io.dws.controller.compile.WorkflowCompiler;
import io.dws.controller.model.ApplyResult;
import io.dws.controller.model.DeploymentPlan;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

@QuarkusTest
@WithKubernetesTestServer
class StackApplierTest {

  private static final String NAMESPACE = "default";

  @Inject KubernetesClient client;

  @Inject WorkflowCompiler compiler;

  @Inject StackApplier applier;

  /** The Mockito-mocked Dapr client supplied by {@code MockDaprClientProducer}. */
  @Inject DaprClient daprClient;

  @BeforeEach
  void cleanCluster() {
    reset(daprClient);
    when(daprClient.publishEvent(anyString(), anyString(), any())).thenReturn(Mono.empty());
    applier.deleteWorkflow("order");
  }

  @SuppressWarnings("unchecked")
  private static boolean publishedType(ArgumentCaptor<Object> bodies, String type) {
    return bodies.getAllValues().stream()
        .map(b -> (Map<String, Object>) b)
        .anyMatch(m -> type.equals(m.get("type")));
  }

  @Test
  @DisplayName("apply creates the immutable definition ConfigMap holding the spec verbatim")
  void createsImmutableDefinitionConfigMap() {
    DeploymentPlan plan = compiler.compile(fixture("order.yaml"));

    applier.apply(plan);

    ConfigMap configMap =
        client.configMaps().inNamespace(NAMESPACE).withName(plan.definitionResource()).get();
    assertThat(configMap).isNotNull();
    assertThat(configMap.getImmutable()).isTrue();
    assertThat(configMap.getData()).containsEntry("definition", fixture("order.yaml"));
    assertThat(configMap.getMetadata().getLabels())
        .containsEntry(Labels.WORKFLOW, "order")
        .containsEntry(Labels.VERSION, plan.versionId())
        .containsEntry(Labels.MANAGED_BY, Labels.MANAGED_BY_VALUE);
  }

  @Test
  @DisplayName("apply creates the Dapr configuration component scoped to the workflow app-id")
  void createsDaprConfigurationComponent() {
    DeploymentPlan plan = compiler.compile(fixture("order.yaml"));

    applier.apply(plan);

    GenericKubernetesResource component =
        client
            .genericKubernetesResources(ResourceContexts.DAPR_COMPONENT)
            .inNamespace(NAMESPACE)
            .withName(plan.definitionResource())
            .get();
    assertThat(component).isNotNull();
    assertThat(component.getAdditionalProperties()).containsEntry("scopes", List.of("order"));
    assertThat(spec(component)).containsEntry("type", "configuration.kubernetes");
  }

  @Test
  @DisplayName("apply creates one Dapr-enabled Knative Service per step")
  void createsKnativeServicePerStep() {
    DeploymentPlan plan = compiler.compile(fixture("order.yaml"));

    applier.apply(plan);

    List<GenericKubernetesResource> services =
        client
            .genericKubernetesResources(ResourceContexts.KNATIVE_SERVICE)
            .inNamespace(NAMESPACE)
            .withLabels(Labels.version("order", plan.versionId()))
            .list()
            .getItems();
    assertThat(services)
        .extracting(s -> s.getMetadata().getName())
        .containsExactlyInAnyOrder("check-inventory", "charge-payment", "notify-out-of-stock");
  }

  @Test
  @DisplayName(
      "apply creates a single-replica orchestrator Deployment pointed at the definition store")
  void createsOrchestratorDeployment() {
    DeploymentPlan plan = compiler.compile(fixture("order.yaml"));

    applier.apply(plan);

    Deployment deployment =
        client
            .apps()
            .deployments()
            .inNamespace(NAMESPACE)
            .withName(plan.orchestrator().name())
            .get();
    assertThat(deployment).isNotNull();
    assertThat(deployment.getSpec().getReplicas()).isEqualTo(1);
    var container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
    assertThat(container.getImage()).isEqualTo("ghcr.io/tonylibs/dws-orchestrator:latest");
    assertThat(container.getEnv())
        .extracting(e -> Map.entry(e.getName(), e.getValue()))
        .contains(
            Map.entry("DEFINITION_STORE", plan.definitionResource()),
            Map.entry("DEFINITION_KEY", "definition"));
    assertThat(deployment.getSpec().getTemplate().getMetadata().getAnnotations())
        .containsEntry("dapr.io/enabled", "true")
        .containsEntry("dapr.io/app-id", "order")
        .containsEntry("dapr.io/app-port", "8080");
  }

  @Test
  @DisplayName("re-applying identical content is a no-op at the same version")
  void reapplyIsIdempotent() {
    DeploymentPlan plan = compiler.compile(fixture("order.yaml"));

    ApplyResult first = applier.apply(plan);
    ApplyResult second = applier.apply(compiler.compile(fixture("order.yaml")));

    assertThat(first.created()).isTrue();
    assertThat(second.created()).isFalse();
    assertThat(second.version()).isEqualTo(first.version());
    assertThat(
            client
                .configMaps()
                .inNamespace(NAMESPACE)
                .withLabels(Labels.workflow("order"))
                .list()
                .getItems())
        .hasSize(1);
  }

  @Test
  @DisplayName("delete removes the whole stack by label selector")
  void deleteRemovesStackByLabel() {
    applier.apply(compiler.compile(fixture("order.yaml")));

    boolean deleted = applier.deleteWorkflow("order");

    assertThat(deleted).isTrue();
    assertThat(
            client
                .configMaps()
                .inNamespace(NAMESPACE)
                .withLabels(Labels.workflow("order"))
                .list()
                .getItems())
        .isEmpty();
    assertThat(
            client
                .apps()
                .deployments()
                .inNamespace(NAMESPACE)
                .withLabels(Labels.workflow("order"))
                .list()
                .getItems())
        .isEmpty();
    assertThat(
            client
                .genericKubernetesResources(ResourceContexts.KNATIVE_SERVICE)
                .inNamespace(NAMESPACE)
                .withLabels(Labels.workflow("order"))
                .list()
                .getItems())
        .isEmpty();
    assertThat(
            client
                .genericKubernetesResources(ResourceContexts.DAPR_COMPONENT)
                .inNamespace(NAMESPACE)
                .withLabels(Labels.workflow("order"))
                .list()
                .getItems())
        .isEmpty();
  }

  @Test
  @DisplayName("rolling out a new version drains the previous orchestrator")
  void rolloutDrainsPreviousVersion() {
    DeploymentPlan v1 = compiler.compile(fixture("order.yaml"));
    applier.apply(v1);
    DeploymentPlan v2 =
        compiler.compile(fixture("order.yaml").replace("/api/charge", "/api/charge-v2"));

    applier.apply(v2);

    assertThat(v2.versionId()).isNotEqualTo(v1.versionId());
    Deployment previous =
        client.apps().deployments().inNamespace(NAMESPACE).withName(v1.orchestrator().name()).get();
    assertThat(previous.getMetadata().getAnnotations()).containsEntry(Labels.DRAIN, "true");
    Deployment current =
        client.apps().deployments().inNamespace(NAMESPACE).withName(v2.orchestrator().name()).get();
    assertThat(current.getMetadata().getAnnotations()).doesNotContainKey(Labels.DRAIN);
  }

  @Test
  @DisplayName("a successful apply publishes io.dws.deployment.applied to dws.events")
  void applyPublishesDeploymentApplied() {
    applier.apply(compiler.compile(fixture("order.yaml")));

    ArgumentCaptor<Object> bodies = ArgumentCaptor.forClass(Object.class);
    verify(daprClient, atLeastOnce())
        .publishEvent(eq("pubsub"), eq("dws.events"), bodies.capture());
    assertThat(publishedType(bodies, "io.dws.deployment.applied")).isTrue();
  }

  @Test
  @DisplayName("a publish failure does not break the apply pass")
  void publishFailureDoesNotBreakApply() {
    when(daprClient.publishEvent(anyString(), anyString(), any()))
        .thenThrow(new RuntimeException("pubsub unavailable"));

    ApplyResult result = applier.apply(compiler.compile(fixture("order.yaml")));

    assertThat(result.created()).isTrue();
    assertThat(
            client
                .apps()
                .deployments()
                .inNamespace(NAMESPACE)
                .withLabels(Labels.workflow("order"))
                .list()
                .getItems())
        .isNotEmpty();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> spec(GenericKubernetesResource resource) {
    return (Map<String, Object>) resource.getAdditionalProperties().get("spec");
  }

  private static String fixture(String name) {
    try (var in = StackApplierTest.class.getResourceAsStream("/fixtures/" + name)) {
      if (in == null) {
        throw new AssertionError("missing fixture " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
