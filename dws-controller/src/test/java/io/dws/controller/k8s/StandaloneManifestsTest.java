package io.dws.controller.k8s;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.kubernetes.client.utils.Serialization;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The standalone manifests in {@code k8s/} are applied by hand, so nothing but this test notices
 * when they stop matching what the controller does. Each assertion below was a live failure
 * (observability phase 2a evidence): a rendered/applied manifest the API server accepted, but that
 * granted too little at runtime.
 */
class StandaloneManifestsTest {

  private static final Path K8S = Path.of("k8s");
  private static final String ORCHESTRATOR_ACCOUNT = "dws-orchestrator";

  /**
   * Every verb the controller needs per managed kind. {@code deletecollection} is required because
   * label-selector deletes ({@code withLabels(...).delete()}) are authorized as collection deletes.
   */
  private static final Set<String> MANAGED_VERBS =
      Set.of("get", "list", "create", "update", "patch", "delete", "deletecollection");

  @Test
  @DisplayName("the controller Role covers every dapr.io / knative kind ResourceContexts manages")
  void controllerRoleCoversEveryDynamicKind() throws IOException {
    JsonNode role = document("controller-rbac.yaml", "Role", "dws-controller");

    List<ResourceDefinitionContext> contexts = resourceContexts();
    assertThat(contexts).as("ResourceContexts constants found by reflection").hasSizeGreaterThan(4);
    for (ResourceDefinitionContext context : contexts) {
      assertThat(verbsFor(role, context.getGroup(), context.getPlural()))
          .as("verbs for %s.%s", context.getPlural(), context.getGroup())
          .containsAll(MANAGED_VERBS);
    }
  }

  @Test
  @DisplayName("the controller Role covers the built-in kinds it creates, labels and deletes")
  void controllerRoleCoversBuiltInKinds() throws IOException {
    JsonNode role = document("controller-rbac.yaml", "Role", "dws-controller");

    assertThat(verbsFor(role, "", "configmaps"))
        .containsAll(Set.of("get", "list", "create", "delete", "deletecollection"));
    assertThat(verbsFor(role, "apps", "deployments")).containsAll(MANAGED_VERBS);
  }

  @Test
  @DisplayName(
      "orchestrator pods get a dedicated service account that can read definition ConfigMaps")
  void orchestratorServiceAccountCanReadConfigMaps() throws IOException {
    document("controller-rbac.yaml", "ServiceAccount", ORCHESTRATOR_ACCOUNT);
    JsonNode role = document("controller-rbac.yaml", "Role", ORCHESTRATOR_ACCOUNT);
    JsonNode binding = document("controller-rbac.yaml", "RoleBinding", ORCHESTRATOR_ACCOUNT);

    assertThat(verbsFor(role, "", "configmaps")).containsExactlyInAnyOrder("get", "list", "watch");
    assertThat(role.get("rules")).as("read-only on configmaps and nothing else").hasSize(1);
    assertThat(binding.at("/roleRef/name").asText()).isEqualTo(ORCHESTRATOR_ACCOUNT);
    assertThat(binding.at("/subjects/0/kind").asText()).isEqualTo("ServiceAccount");
    assertThat(binding.at("/subjects/0/name").asText()).isEqualTo(ORCHESTRATOR_ACCOUNT);
  }

  @Test
  @DisplayName("the controller Deployment tells the controller which service account to stamp")
  void controllerDeploymentNamesTheOrchestratorServiceAccount() throws IOException {
    JsonNode deployment = document("controller-deployment.yaml", "Deployment", "dws-controller");

    String value = null;
    for (JsonNode env : deployment.at("/spec/template/spec/containers/0/env")) {
      if ("DWS_ORCHESTRATOR_SERVICE_ACCOUNT".equals(env.get("name").asText())) {
        value = env.get("value").asText();
      }
    }
    assertThat(value).isEqualTo(ORCHESTRATOR_ACCOUNT);
  }

  private static JsonNode document(String file, String kind, String name) throws IOException {
    try (MappingIterator<JsonNode> docs =
        Serialization.yamlMapper()
            .readerFor(JsonNode.class)
            .readValues(K8S.resolve(file).toFile())) {
      while (docs.hasNext()) {
        JsonNode doc = docs.next();
        if (kind.equals(doc.path("kind").asText())
            && name.equals(doc.at("/metadata/name").asText())) {
          return doc;
        }
      }
    }
    throw new AssertionError("no " + kind + " named " + name + " in " + file);
  }

  /** Union of verbs across all rules naming {@code resource} in {@code group}. */
  private static Set<String> verbsFor(JsonNode role, String group, String resource) {
    Set<String> verbs = new java.util.HashSet<>();
    for (JsonNode rule : role.get("rules")) {
      if (contains(rule.get("apiGroups"), group) && contains(rule.get("resources"), resource)) {
        rule.get("verbs").forEach(verb -> verbs.add(verb.asText()));
      }
    }
    return verbs;
  }

  private static boolean contains(JsonNode array, String value) {
    for (JsonNode item : array) {
      if (value.equals(item.asText())) {
        return true;
      }
    }
    return false;
  }

  private static List<ResourceDefinitionContext> resourceContexts() {
    List<ResourceDefinitionContext> contexts = new ArrayList<>();
    for (Field field : ResourceContexts.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())
          && field.getType() == ResourceDefinitionContext.class) {
        try {
          contexts.add((ResourceDefinitionContext) field.get(null));
        } catch (IllegalAccessException e) {
          throw new AssertionError(e);
        }
      }
    }
    return contexts;
  }
}
