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
import io.dapr.client.domain.ConfigurationItem;
import io.dws.controller.compile.Names;
import io.dws.controller.compile.WorkflowCompiler;
import io.dws.controller.model.ApplyResult;
import io.dws.controller.model.DeploymentPlan;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
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
  @DisplayName("apply creates the scoped OAuth endpoint, middleware, and the step's configuration")
  void createsOAuthResources() {
    DeploymentPlan plan = compiler.compile(fixture("oauth.yaml"));
    String resourceName = plan.oauthEndpoints().getFirst().name();

    applier.apply(plan);

    GenericKubernetesResource endpoint =
        client
            .genericKubernetesResources(ResourceContexts.DAPR_HTTP_ENDPOINT)
            .inNamespace(NAMESPACE)
            .withName(resourceName)
            .get();
    GenericKubernetesResource component =
        client
            .genericKubernetesResources(ResourceContexts.DAPR_COMPONENT)
            .inNamespace(NAMESPACE)
            .withName(resourceName)
            .get();
    GenericKubernetesResource configuration =
        daprConfigurationOf(Names.daprConfiguration("get-account", plan.versionId()));

    assertThat(endpoint).isNotNull();
    assertThat(component).isNotNull();
    assertThat(configuration).isNotNull();
    assertThat(configuration.getMetadata().getLabels())
        .containsEntry(Labels.WORKFLOW, "oauth-apply")
        .containsEntry(Labels.VERSION, plan.versionId());
    assertThat(daprConfigurationOf(resourceName))
        .as("the per-endpoint Configuration no longer exists")
        .isNull();
    assertThat(endpoint.getMetadata().getLabels())
        .containsEntry(Labels.WORKFLOW, "oauth-apply")
        .containsEntry(Labels.VERSION, plan.versionId());
    assertThat(component.getAdditionalProperties()).containsEntry("scopes", List.of("get-account"));
    assertThat(
            client
                .genericKubernetesResources(ResourceContexts.KNATIVE_SERVICE)
                .inNamespace(NAMESPACE)
                .withName("get-account")
                .get()
                .getAdditionalProperties())
        .isNotEmpty();
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
  @DisplayName("a synthesized Knative Service carries dws.io/step-type distinguishing its kind")
  void knativeServiceCarriesStepTypeLabel() {
    DeploymentPlan callPlan = compiler.compile(fixture("order.yaml"));
    applier.apply(callPlan);

    GenericKubernetesResource callService =
        client
            .genericKubernetesResources(ResourceContexts.KNATIVE_SERVICE)
            .inNamespace(NAMESPACE)
            .withName("check-inventory")
            .get();
    assertThat(callService.getMetadata().getLabels()).containsEntry(Labels.STEP_TYPE, "call-http");

    applier.deleteWorkflow("order");
    DeploymentPlan runPlan = compiler.compile(fixture("run-shell.yaml"));
    applier.apply(runPlan);

    GenericKubernetesResource runService =
        client
            .genericKubernetesResources(ResourceContexts.KNATIVE_SERVICE)
            .inNamespace(NAMESPACE)
            .withName("sync-inventory")
            .get();
    assertThat(runService.getMetadata().getLabels()).containsEntry(Labels.STEP_TYPE, "run-shell");

    applier.deleteWorkflow("shellflow");
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
            Map.entry("DAPR_CONFIG_STORE", plan.definitionResource()),
            Map.entry("DEFINITION_KEY", "definition"));
    // Not `default`: the definition Component reads its ConfigMap as the pod's service account.
    assertThat(deployment.getSpec().getTemplate().getSpec().getServiceAccountName())
        .isEqualTo("dws-orchestrator");
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

  // ---- orchestrator tracing (observability-orchestrator-tracing) ----

  private void storeSays(Map<String, String> values) {
    Map<String, ConfigurationItem> items = new HashMap<>();
    values.forEach((key, value) -> items.put(key, new ConfigurationItem(key, value, "1")));
    when(daprClient.getConfiguration(
            ObservabilitySettings.STORE,
            ObservabilitySettings.ENABLED_KEY,
            ObservabilitySettings.INSTRUMENTATION_KEY))
        .thenReturn(Mono.just(items));
  }

  /** The block the chart's dws-tracing carries; it must reach the merged Configuration intact. */
  private static final Map<String, Object> TRACING =
      Map.of(
          "samplingRate",
          "1",
          "otel",
          Map.of("endpointAddress", "collector:4318", "isSecure", false, "protocol", "http"));

  private GenericKubernetesResource daprConfigurationOf(String name) {
    return client
        .genericKubernetesResources(ResourceContexts.DAPR_CONFIGURATION)
        .inNamespace(NAMESPACE)
        .withName(name)
        .get();
  }

  private void tracingConfigurationInCluster() {
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
                .addToAdditionalProperties("spec", Map.of("tracing", TRACING))
                .build())
        .createOr(NonDeletingOperation::update);
  }

  @AfterEach
  void removeTracingConfiguration() {
    client
        .genericKubernetesResources(ResourceContexts.DAPR_CONFIGURATION)
        .inNamespace(NAMESPACE)
        .withName(ObservabilitySettings.TRACING_CONFIGURATION)
        .delete();
  }

  private Deployment orchestratorOf(DeploymentPlan plan) {
    return client
        .apps()
        .deployments()
        .inNamespace(NAMESPACE)
        .withName(plan.orchestrator().name())
        .get();
  }

  private void assertTodaysOrchestrator(Deployment deployment) {
    assertThat(deployment.getSpec().getTemplate().getMetadata().getAnnotations())
        .containsOnlyKeys("dapr.io/enabled", "dapr.io/app-id", "dapr.io/app-port");
    assertThat(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv())
        .extracting(EnvVar::getName)
        .noneMatch(name -> name.startsWith("OTEL_"));
  }

  @Test
  @DisplayName("flag on with dws-tracing present stores an instrumented orchestrator Deployment")
  void instrumentsOrchestratorWhenFlagOnAndConfigurationPresent() {
    storeSays(Map.of(ObservabilitySettings.ENABLED_KEY, "true"));
    tracingConfigurationInCluster();
    DeploymentPlan plan = compiler.compile(fixture("order.yaml"));

    applier.apply(plan);

    Deployment deployment = orchestratorOf(plan);
    assertThat(deployment.getSpec().getTemplate().getMetadata().getAnnotations())
        .containsEntry("dapr.io/config", Names.daprConfiguration(plan.workflow(), plan.versionId()))
        .containsEntry("instrumentation.opentelemetry.io/container-names", "orchestrator")
        .containsEntry("instrumentation.opentelemetry.io/inject-java", "true")
        .containsEntry("dapr.io/app-id", "order");
    assertThat(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv())
        .extracting(e -> Map.entry(e.getName(), e.getValue()))
        .contains(
            Map.entry("OTEL_SERVICE_NAME", "order"),
            Map.entry(
                "OTEL_RESOURCE_ATTRIBUTES",
                "dws.workflow.name=order,dws.workflow.version=" + plan.versionId()));
  }

  @Test
  @DisplayName("flag on but dws-tracing absent stores today's orchestrator and still succeeds")
  void flagOnWithoutConfigurationKeepsTodaysOrchestrator() {
    storeSays(Map.of(ObservabilitySettings.ENABLED_KEY, "true"));
    DeploymentPlan plan = compiler.compile(fixture("order.yaml"));

    ApplyResult result = applier.apply(plan);

    assertThat(result.created()).isTrue();
    assertTodaysOrchestrator(orchestratorOf(plan));
  }

  @Test
  @DisplayName("an unreadable store stores today's orchestrator and still succeeds")
  void unreadableStoreKeepsTodaysOrchestrator() {
    when(daprClient.getConfiguration(
            ObservabilitySettings.STORE,
            ObservabilitySettings.ENABLED_KEY,
            ObservabilitySettings.INSTRUMENTATION_KEY))
        .thenReturn(Mono.error(new RuntimeException("store down")));
    tracingConfigurationInCluster();
    DeploymentPlan plan = compiler.compile(fixture("order.yaml"));

    ApplyResult result = applier.apply(plan);

    assertThat(result.created()).isTrue();
    assertTodaysOrchestrator(orchestratorOf(plan));
  }

  @Test
  @DisplayName("flag absent stores today's orchestrator even when dws-tracing exists")
  void absentFlagKeepsTodaysOrchestrator() {
    storeSays(Map.of());
    tracingConfigurationInCluster();
    DeploymentPlan plan = compiler.compile(fixture("order.yaml"));

    applier.apply(plan);

    assertTodaysOrchestrator(orchestratorOf(plan));
  }

  @Test
  @DisplayName("a flag change applies on the next deploy only; the stored Deployment is untouched")
  void flagChangeAppliesOnNextDeployOnly() {
    tracingConfigurationInCluster();
    DeploymentPlan first = compiler.compile(fixture("order.yaml"));
    applier.apply(first);
    assertTodaysOrchestrator(orchestratorOf(first));

    storeSays(Map.of(ObservabilitySettings.ENABLED_KEY, "true"));
    DeploymentPlan next =
        compiler.compile(fixture("order.yaml").replace("/api/charge", "/api/charge-v2"));
    applier.apply(next);

    assertThat(orchestratorOf(next).getSpec().getTemplate().getMetadata().getAnnotations())
        .containsEntry(
            "dapr.io/config", Names.daprConfiguration(next.workflow(), next.versionId()));
    // Re-read from the server after the second apply: the first version's Deployment (now
    // draining) was not re-rendered and is still un-instrumented.
    Deployment previous = orchestratorOf(first);
    assertThat(previous.getMetadata().getAnnotations()).containsEntry(Labels.DRAIN, "true");
    assertTodaysOrchestrator(previous);
  }

  /**
   * Everything the apply pass stored for {@code workflow} except the orchestrator Deployment, keyed
   * by kind/name and reduced to the parts the controller renders (labels, annotations, spec/data)
   * so server-assigned metadata cannot cause false differences.
   */
  private Map<String, Object> storedNonOrchestratorResources(String workflow) {
    Map<String, Object> stored = new TreeMap<>();
    client
        .configMaps()
        .inNamespace(NAMESPACE)
        .withLabels(Labels.workflow(workflow))
        .list()
        .getItems()
        .forEach(
            configMap ->
                stored.put(
                    "ConfigMap/" + configMap.getMetadata().getName(),
                    List.of(
                        configMap.getMetadata().getLabels(),
                        configMap.getData(),
                        String.valueOf(configMap.getImmutable()))));
    Map<String, ResourceDefinitionContext> dynamic = new LinkedHashMap<>();
    dynamic.put("KnativeService", ResourceContexts.KNATIVE_SERVICE);
    dynamic.put("DaprComponent", ResourceContexts.DAPR_COMPONENT);
    dynamic.put("DaprHttpEndpoint", ResourceContexts.DAPR_HTTP_ENDPOINT);
    dynamic.put("DaprConfiguration", ResourceContexts.DAPR_CONFIGURATION);
    dynamic.put("WorkflowAccessPolicy", ResourceContexts.WORKFLOW_ACCESS_POLICY);
    dynamic.forEach(
        (kind, context) ->
            client
                .genericKubernetesResources(context)
                .inNamespace(NAMESPACE)
                .withLabels(Labels.workflow(workflow))
                .list()
                .getItems()
                .forEach(
                    resource ->
                        stored.put(
                            kind + "/" + resource.getMetadata().getName(),
                            List.of(
                                resource.getMetadata().getLabels(),
                                String.valueOf(resource.getMetadata().getAnnotations()),
                                resource.getAdditionalProperties()))));
    return stored;
  }

  @Test
  @DisplayName(
      "the flag changes only the orchestrator and its Configuration: everything else is identical")
  void otherStoredResourcesAreIdenticalWithFlagOnAndOff() {
    for (String[] fixtureAndWorkflow :
        new String[][] {{"order.yaml", "order"}, {"oauth.yaml", "oauth-apply"}}) {
      String fixture = fixtureAndWorkflow[0];
      String workflow = fixtureAndWorkflow[1];
      try {
        reset(daprClient);
        when(daprClient.publishEvent(anyString(), anyString(), any())).thenReturn(Mono.empty());
        storeSays(Map.of(ObservabilitySettings.ENABLED_KEY, "false"));
        applier.apply(compiler.compile(fixture(fixture)));
        Map<String, Object> flagOff = storedNonOrchestratorResources(workflow);
        applier.deleteWorkflow(workflow);

        storeSays(Map.of(ObservabilitySettings.ENABLED_KEY, "true"));
        tracingConfigurationInCluster();
        DeploymentPlan plan = compiler.compile(fixture(fixture));
        applier.apply(plan);
        Map<String, Object> flagOn = storedNonOrchestratorResources(workflow);

        // The flag really took effect, so the comparison below is not vacuous: the orchestrator
        // names its own merged Configuration, which carries the tracing block and is the only
        // resource the flag adds.
        String orchestratorConfiguration =
            Names.daprConfiguration(plan.workflow(), plan.versionId());
        assertThat(orchestratorOf(plan).getSpec().getTemplate().getMetadata().getAnnotations())
            .as(fixture)
            .containsEntry("dapr.io/config", orchestratorConfiguration);
        assertThat(flagOn)
            .as(fixture)
            .containsKey("DaprConfiguration/" + orchestratorConfiguration);
        flagOn.remove("DaprConfiguration/" + orchestratorConfiguration);
        assertThat(flagOff).as(fixture).hasSizeGreaterThan(2);
        assertThat(flagOn).as(fixture).isEqualTo(flagOff);
      } finally {
        applier.deleteWorkflow(workflow);
      }
    }
  }

  @Test
  @DisplayName("OAuth steps and a traced orchestrator each own one Configuration, none dropped")
  void tracedWorkflowWithOAuthKeepsEveryConfiguration() {
    storeSays(Map.of(ObservabilitySettings.ENABLED_KEY, "true"));
    tracingConfigurationInCluster();
    DeploymentPlan plan = compiler.compile(fixture("oauth.yaml"));

    applier.apply(plan);

    GenericKubernetesResource step =
        daprConfigurationOf(Names.daprConfiguration("get-account", plan.versionId()));
    GenericKubernetesResource orchestrator =
        daprConfigurationOf(Names.daprConfiguration(plan.workflow(), plan.versionId()));
    assertThat(spec(step)).containsOnlyKeys("httpPipeline");
    assertThat(spec(orchestrator)).containsOnlyKeys("tracing").containsEntry("tracing", TRACING);
    assertThat(orchestratorOf(plan).getSpec().getTemplate().getMetadata().getAnnotations())
        .containsEntry("dapr.io/config", orchestrator.getMetadata().getName());
  }

  @Test
  @DisplayName("merged Configurations follow the version: GC'd with a drained version")
  void mergedConfigurationsAreCollectedWithTheirVersion() {
    storeSays(Map.of(ObservabilitySettings.ENABLED_KEY, "true"));
    tracingConfigurationInCluster();
    DeploymentPlan first = compiler.compile(fixture("oauth.yaml"));
    applier.apply(first);
    String stepConfiguration = Names.daprConfiguration("get-account", first.versionId());
    String orchestratorConfiguration = Names.daprConfiguration(first.workflow(), first.versionId());
    assertThat(daprConfigurationOf(stepConfiguration)).isNotNull();
    assertThat(daprConfigurationOf(orchestratorConfiguration)).isNotNull();

    applier.deleteWorkflow(first.workflow());

    assertThat(daprConfigurationOf(stepConfiguration)).isNull();
    assertThat(daprConfigurationOf(orchestratorConfiguration)).isNull();
    assertThat(daprConfigurationOf(ObservabilitySettings.TRACING_CONFIGURATION))
        .as("the chart-owned source Configuration is not a managed resource")
        .isNotNull();
  }

  @Test
  @DisplayName("workloads that need nothing get no Configuration and no dapr.io/config")
  void workloadsNeedingNothingStoreNoConfiguration() {
    DeploymentPlan plan = compiler.compile(fixture("order.yaml"));

    applier.apply(plan);

    assertThat(
            client
                .genericKubernetesResources(ResourceContexts.DAPR_CONFIGURATION)
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
        .isNotEmpty()
        .allSatisfy(
            service ->
                assertThat(service.getAdditionalProperties().toString())
                    .doesNotContain("dapr.io/config"));
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
