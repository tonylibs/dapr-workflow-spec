package io.dws.controller.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import io.dapr.client.DaprClient;
import io.dws.controller.compile.WorkflowCompiler;
import io.dws.controller.config.DwsConfig;
import io.dws.controller.model.ApplyResult;
import io.dws.controller.model.DeploymentPlan;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import io.smallrye.config.WithDefault;
import jakarta.inject.Inject;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Cross-package contract guard for the chart/standalone controller configuration and the Knative
 * Revision identity the apply pass stores. None of these names are compiler-checked across the
 * boundary, so drift otherwise appears only when Knative tries to admit or pull a step Pod.
 */
@QuarkusTest
@WithKubernetesTestServer
@TestProfile(StepServiceAccountContractTest.CustomTaskAccount.class)
class StepServiceAccountContractTest {

  private static final String WORKFLOW = "identity-contract";
  private static final String DEFAULT_ACCOUNT = "dws-task";
  private static final String CUSTOM_ACCOUNT = "contract-task";
  private static final Path APPLICATION_CONFIG =
      Path.of("src", "main", "resources", "application.yaml");
  private static final Path STANDALONE_RBAC = Path.of("k8s", "controller-rbac.yaml");
  private static final Path STANDALONE_DEPLOYMENT = Path.of("k8s", "controller-deployment.yaml");
  private static final Path CHART_CONTROLLER_DEPLOYMENT =
      Path.of("..", "charts", "dws", "templates", "controller", "deployment.yaml");

  private static final String DEFINITION =
      """
      document:
        dsl: '1.0.0'
        namespace: test
        name: identity-contract
        version: '1.0.0'
      do:
        - fetchInventory:
            call: http
            with:
              method: get
              endpoint: https://inventory.example.test/items
        - syncInventory:
            run:
              shell:
                command: ./sync.sh
      """;

  @Inject KubernetesClient client;
  @Inject WorkflowCompiler compiler;
  @Inject StackApplier applier;
  @Inject DwsConfig config;
  @Inject DaprClient daprClient;

  @BeforeEach
  void prepare() {
    reset(daprClient);
    when(daprClient.publishEvent(anyString(), anyString(), any())).thenReturn(Mono.empty());
    when(daprClient.getConfiguration(
            ObservabilitySettings.STORE,
            ObservabilitySettings.ENABLED_KEY,
            ObservabilitySettings.INSTRUMENTATION_KEY))
        .thenReturn(Mono.just(Map.of()));
    applier.deleteWorkflow(WORKFLOW);
  }

  @AfterEach
  void cleanUp() {
    applier.deleteWorkflow(WORKFLOW);
  }

  @Test
  @DisplayName("the chart env key binds the configured account and the controller defaults it")
  void chartEnvironmentKeyAndControllerConfigurationStayAligned() throws Exception {
    Method taskMethod = DwsConfig.class.getMethod("task");
    Method serviceAccountMethod = taskMethod.getReturnType().getMethod("serviceAccount");

    assertThat(serviceAccountMethod.getAnnotation(WithDefault.class).value())
        .isEqualTo(DEFAULT_ACCOUNT);
    assertThat(resolvedTaskAccount()).isEqualTo(CUSTOM_ACCOUNT);

    assertThat(Files.readString(APPLICATION_CONFIG, StandardCharsets.UTF_8))
        .contains("service-account: ${DWS_TASK_SERVICE_ACCOUNT:" + DEFAULT_ACCOUNT + "}");
    assertThat(Files.readString(CHART_CONTROLLER_DEPLOYMENT, StandardCharsets.UTF_8))
        .contains("- name: DWS_TASK_SERVICE_ACCOUNT");
  }

  @Test
  @DisplayName("standalone manifests provision the default step account without an RBAC grant")
  void standaloneProvisioningHasNoGrant() throws IOException {
    document(STANDALONE_RBAC, "ServiceAccount", DEFAULT_ACCOUNT);
    JsonNode deployment = document(STANDALONE_DEPLOYMENT, "Deployment", "dws-controller");

    assertThat(environmentValue(deployment, "DWS_TASK_SERVICE_ACCOUNT")).isEqualTo(DEFAULT_ACCOUNT);
    List<String> boundAccounts = new ArrayList<>();
    for (JsonNode binding : documents(STANDALONE_RBAC)) {
      if ("RoleBinding".equals(binding.path("kind").asText())
          || "ClusterRoleBinding".equals(binding.path("kind").asText())) {
        binding
            .path("subjects")
            .forEach(subject -> boundAccounts.add(subject.path("name").asText()));
      }
    }
    assertThat(boundAccounts).doesNotContain(DEFAULT_ACCOUNT);
  }

  @Test
  @DisplayName("configured identity reaches call and run steps and repairs a same-version Service")
  void configuredIdentityReachesEveryMutableStepService() {
    DeploymentPlan first = compiler.compile(DEFINITION);

    ApplyResult created = applier.apply(first);

    assertThat(created.created()).isTrue();
    assertThat(stepServices(first))
        .extracting(service -> service.getMetadata().getName())
        .containsExactlyInAnyOrder("fetch-inventory", "sync-inventory");
    assertThat(stepServices(first))
        .allSatisfy(service -> assertThat(serviceAccountName(service)).isEqualTo(CUSTOM_ACCOUNT));

    client
        .genericKubernetesResources(ResourceContexts.KNATIVE_SERVICE)
        .inNamespace(config.namespace())
        .withName("fetch-inventory")
        .edit(
            service -> {
              templateSpec(service).put("serviceAccountName", "default");
              return service;
            });
    assertThat(serviceAccountName(stepService("fetch-inventory"))).isEqualTo("default");

    DeploymentPlan sameDefinition = compiler.compile(DEFINITION);
    ApplyResult updated = applier.apply(sameDefinition);

    assertThat(updated.created()).isFalse();
    assertThat(updated.versionId()).isEqualTo(created.versionId());
    assertThat(sameDefinition.versionId()).isEqualTo(first.versionId());
    assertThat(stepService("fetch-inventory").getMetadata().getName()).isEqualTo("fetch-inventory");
    assertThat(stepServices(sameDefinition))
        .allSatisfy(service -> assertThat(serviceAccountName(service)).isEqualTo(CUSTOM_ACCOUNT));
  }

  private String resolvedTaskAccount() throws ReflectiveOperationException {
    Method taskMethod = DwsConfig.class.getMethod("task");
    Object task = taskMethod.invoke(config);
    return (String) taskMethod.getReturnType().getMethod("serviceAccount").invoke(task);
  }

  private List<GenericKubernetesResource> stepServices(DeploymentPlan plan) {
    return client
        .genericKubernetesResources(ResourceContexts.KNATIVE_SERVICE)
        .inNamespace(config.namespace())
        .withLabels(Labels.version(WORKFLOW, plan.versionId()))
        .list()
        .getItems();
  }

  private GenericKubernetesResource stepService(String name) {
    return client
        .genericKubernetesResources(ResourceContexts.KNATIVE_SERVICE)
        .inNamespace(config.namespace())
        .withName(name)
        .get();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> templateSpec(GenericKubernetesResource service) {
    Map<String, Object> spec = (Map<String, Object>) service.getAdditionalProperties().get("spec");
    Map<String, Object> template = (Map<String, Object>) spec.get("template");
    return (Map<String, Object>) template.get("spec");
  }

  private static String serviceAccountName(GenericKubernetesResource service) {
    return String.valueOf(templateSpec(service).get("serviceAccountName"));
  }

  private static List<JsonNode> documents(Path file) throws IOException {
    List<JsonNode> result = new ArrayList<>();
    try (MappingIterator<JsonNode> docs =
        Serialization.yamlMapper().readerFor(JsonNode.class).readValues(file.toFile())) {
      while (docs.hasNext()) {
        result.add(docs.next());
      }
    }
    return result;
  }

  private static JsonNode document(Path file, String kind, String name) throws IOException {
    return documents(file).stream()
        .filter(
            doc ->
                kind.equals(doc.path("kind").asText())
                    && name.equals(doc.at("/metadata/name").asText()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no " + kind + " named " + name + " in " + file));
  }

  private static String environmentValue(JsonNode deployment, String name) {
    for (JsonNode env : deployment.at("/spec/template/spec/containers/0/env")) {
      if (name.equals(env.path("name").asText())) {
        return env.path("value").asText();
      }
    }
    throw new AssertionError("no environment variable " + name);
  }

  public static class CustomTaskAccount implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("dws.task.service-account", CUSTOM_ACCOUNT);
    }
  }
}
