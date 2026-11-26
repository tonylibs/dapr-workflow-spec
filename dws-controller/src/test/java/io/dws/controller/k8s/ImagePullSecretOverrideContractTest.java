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
import io.dws.controller.config.ImagePullSecretPolicy;
import io.dws.controller.model.ApplyResult;
import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.TaskKind;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import io.smallrye.config.WithDefault;
import jakarta.inject.Inject;
import java.io.IOException;
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
 * Cross-package contract guard for per-step-image pull-secret overrides: the chart, the standalone
 * manifests and the controller agree on the override account and the {@code
 * DWS_IMAGES_PULL_SECRETS_<KIND>} keys, and the apply pass isolates only overridden kinds. None of
 * these names are compiler-checked across the boundary; drift shows up only as a failed image pull.
 */
@QuarkusTest
@WithKubernetesTestServer
@TestProfile(ImagePullSecretOverrideContractTest.HttpOverride.class)
class ImagePullSecretOverrideContractTest {

  private static final String WORKFLOW = "pull-secret-contract";
  private static final String DEFAULT_OVERRIDE_ACCOUNT = "dws-task-override";
  private static final String CUSTOM_TASK_ACCOUNT = "custom-task";
  private static final String CUSTOM_OVERRIDE_ACCOUNT = "custom-task-override";
  private static final Path APPLICATION_CONFIG =
      Path.of("src", "main", "resources", "application.yaml");
  private static final Path STANDALONE_RBAC = Path.of("k8s", "controller-rbac.yaml");
  private static final Path STANDALONE_DEPLOYMENT = Path.of("k8s", "controller-deployment.yaml");
  private static final Path CHART_TEMPLATES = Path.of("..", "charts", "dws", "templates");

  private static final String DEFINITION =
      """
      document:
        dsl: '1.0.0'
        namespace: test
        name: pull-secret-contract
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
  @DisplayName("chart, application.yaml and DwsConfig agree on the override account key")
  void overrideAccountKeyStaysAligned() throws Exception {
    assertThat(
            DwsConfig.Task.class
                .getMethod("overrideServiceAccount")
                .getAnnotation(WithDefault.class)
                .value())
        .isEqualTo(DEFAULT_OVERRIDE_ACCOUNT);
    assertThat(config.task().overrideServiceAccount()).isEqualTo(CUSTOM_OVERRIDE_ACCOUNT);
    assertThat(read(APPLICATION_CONFIG))
        .contains(
            "override-service-account: ${DWS_TASK_OVERRIDE_SERVICE_ACCOUNT:"
                + DEFAULT_OVERRIDE_ACCOUNT
                + "}");
    assertThat(read(CHART_TEMPLATES.resolve(Path.of("controller", "deployment.yaml"))))
        .contains("- name: DWS_TASK_OVERRIDE_SERVICE_ACCOUNT");
  }

  @Test
  @DisplayName("the chart renders one DWS_IMAGES_PULL_SECRETS_<KIND> key per deployable kind")
  void chartPullSecretKeysCoverEveryKind() throws Exception {
    String helpers = read(CHART_TEMPLATES.resolve("_helpers.tpl"));

    assertThat(helpers).contains("- name: DWS_IMAGES_PULL_SECRETS_{{ $kind }}");
    for (TaskKind kind : TaskKind.values()) {
      assertThat(ImagePullSecretPolicy.environmentName(kind))
          .isEqualTo("DWS_IMAGES_PULL_SECRETS_" + kind.name());
      assertThat(helpers).contains("(list \"" + kind.name() + "\" ");
      assertThat(DwsConfig.PullSecrets.class.getMethod(camelCase(kind)).getReturnType())
          .isEqualTo(java.util.Optional.class);
    }
  }

  @Test
  @DisplayName("standalone manifests provision a grant-free override account with no secrets")
  void standaloneOverrideAccountHasNoGrantOrSecrets() throws IOException {
    JsonNode account = document(STANDALONE_RBAC, "ServiceAccount", DEFAULT_OVERRIDE_ACCOUNT);
    JsonNode deployment = document(STANDALONE_DEPLOYMENT, "Deployment", "dws-controller");

    assertThat(account.has("imagePullSecrets")).isFalse();
    assertThat(environmentValue(deployment, "DWS_TASK_OVERRIDE_SERVICE_ACCOUNT"))
        .isEqualTo(DEFAULT_OVERRIDE_ACCOUNT);
    List<String> boundAccounts = new ArrayList<>();
    for (JsonNode binding : documents(STANDALONE_RBAC)) {
      String kind = binding.path("kind").asText();
      if ("RoleBinding".equals(kind) || "ClusterRoleBinding".equals(kind)) {
        binding
            .path("subjects")
            .forEach(subject -> boundAccounts.add(subject.path("name").asText()));
      }
    }
    assertThat(boundAccounts).doesNotContain(DEFAULT_OVERRIDE_ACCOUNT);
  }

  @Test
  @DisplayName("only the overridden kind runs as the override account with its replacement list")
  void overriddenKindIsIsolated() {
    DeploymentPlan plan = compiler.compile(DEFINITION);

    applier.apply(plan);

    assertThat(templateSpec(stepService("fetch-inventory")))
        .containsEntry("serviceAccountName", CUSTOM_OVERRIDE_ACCOUNT)
        .containsEntry("imagePullSecrets", List.of(Map.of("name", "http-cred")));
    assertThat(templateSpec(stepService("sync-inventory")))
        .containsEntry("serviceAccountName", CUSTOM_TASK_ACCOUNT)
        .doesNotContainKey("imagePullSecrets");

    PodSpec orchestrator = orchestrator(plan).getSpec().getTemplate().getSpec();
    assertThat(orchestrator.getServiceAccountName())
        .isEqualTo(config.orchestrator().serviceAccount());
    assertThat(orchestrator.getImagePullSecrets()).isNullOrEmpty();
  }

  @Test
  @DisplayName("resubmitting the same definition repairs identity and secrets at the same version")
  void resubmissionRepairsOverrideAtSameVersion() {
    DeploymentPlan first = compiler.compile(DEFINITION);
    ApplyResult created = applier.apply(first);
    editTemplate(
        "fetch-inventory",
        spec -> {
          spec.put("serviceAccountName", "default");
          spec.remove("imagePullSecrets");
        });
    editTemplate(
        "sync-inventory", spec -> spec.put("imagePullSecrets", List.of(Map.of("name", "stale"))));

    DeploymentPlan same = compiler.compile(DEFINITION);
    ApplyResult updated = applier.apply(same);

    assertThat(updated.created()).isFalse();
    assertThat(updated.versionId()).isEqualTo(created.versionId());
    assertThat(same.versionId()).isEqualTo(first.versionId());
    assertThat(templateSpec(stepService("fetch-inventory")))
        .containsEntry("serviceAccountName", CUSTOM_OVERRIDE_ACCOUNT)
        .containsEntry("imagePullSecrets", List.of(Map.of("name", "http-cred")));
    assertThat(templateSpec(stepService("sync-inventory")))
        .containsEntry("serviceAccountName", CUSTOM_TASK_ACCOUNT)
        .doesNotContainKey("imagePullSecrets");
  }

  private void editTemplate(
      String name, java.util.function.Consumer<Map<String, Object>> mutation) {
    client
        .genericKubernetesResources(ResourceContexts.KNATIVE_SERVICE)
        .inNamespace(config.namespace())
        .withName(name)
        .edit(
            service -> {
              mutation.accept(templateSpec(service));
              return service;
            });
  }

  private Deployment orchestrator(DeploymentPlan plan) {
    return client
        .apps()
        .deployments()
        .inNamespace(config.namespace())
        .withLabels(Labels.version(WORKFLOW, plan.versionId()))
        .list()
        .getItems()
        .getFirst();
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

  private static String camelCase(TaskKind kind) {
    String[] parts = kind.name().toLowerCase(java.util.Locale.ROOT).split("_");
    StringBuilder name = new StringBuilder(parts[0]);
    for (int i = 1; i < parts.length; i++) {
      name.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
    }
    return name.toString();
  }

  private static String read(Path file) throws IOException {
    return Files.readString(file, StandardCharsets.UTF_8);
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

  public static class HttpOverride implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "dws.task.service-account",
          CUSTOM_TASK_ACCOUNT,
          "dws.task.override-service-account",
          CUSTOM_OVERRIDE_ACCOUNT,
          ImagePullSecretPolicy.propertyName(TaskKind.CALL_HTTP),
          "[\"http-cred\"]");
    }
  }
}
