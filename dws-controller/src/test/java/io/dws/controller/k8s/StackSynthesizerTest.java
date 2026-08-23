package io.dws.controller.k8s;

import static org.assertj.core.api.Assertions.assertThat;

import io.dws.controller.compile.WorkflowCompiler;
import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.EnvValue.Literal;
import io.dws.controller.model.EnvValue.SecretKeyRef;
import io.dws.controller.model.ImageCatalog;
import io.dws.controller.model.OAuthEndpoint;
import io.dws.controller.model.OrchestratorSpec;
import io.dws.controller.model.StepService;
import io.dws.controller.model.TaskKind;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.utils.Serialization;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Direct unit tests for the pure cdk8s synthesis in {@link StackSynthesizer} — no cluster involved.
 * Focuses on the per-step Knative Service annotations, in particular the task-kind-conditional
 * {@code autoscaling.knative.dev/min-scale}.
 */
class StackSynthesizerTest {

  private static final String NAMESPACE = "default";
  private static final ImageCatalog IMAGES =
      new ImageCatalog(
          "sw-call-http:1.0",
          "sw-call-openapi:1.0",
          "sw-run-shell:1.0",
          "sw-run-script-js:1.0",
          "sw-run-script-python:1.0",
          "sw-orchestrator:1.0");

  private final StackSynthesizer synthesizer = new StackSynthesizer();
  private final WorkflowCompiler compiler =
      new WorkflowCompiler(IMAGES, ignored -> "openapi".getBytes(StandardCharsets.UTF_8));

  @ParameterizedTest
  @EnumSource(
      value = TaskKind.class,
      names = {"CALL_HTTP", "RUN_SHELL", "RUN_SCRIPT_JS", "RUN_SCRIPT_PYTHON"})
  @DisplayName("an activity-invoked step stays live with min-scale 1")
  void activityStepStaysLive(TaskKind kind) {
    Map<String, String> annotations = synthesizeStepAnnotations(kind);

    assertThat(annotations).containsEntry("autoscaling.knative.dev/min-scale", "1");
  }

  @Test
  @DisplayName("a call:openapi step keeps scale-to-zero with min-scale 0")
  void openApiStepScalesToZero() {
    Map<String, String> annotations = synthesizeStepAnnotations(TaskKind.CALL_OPENAPI);

    assertThat(annotations).containsEntry("autoscaling.knative.dev/min-scale", "0");
  }

  @ParameterizedTest
  @EnumSource(TaskKind.class)
  @DisplayName("the dapr annotations are unchanged regardless of task kind")
  void daprAnnotationsUnchanged(TaskKind kind) {
    Map<String, String> annotations = synthesizeStepAnnotations(kind);

    assertThat(annotations)
        .containsEntry("dapr.io/enabled", "true")
        .containsEntry("dapr.io/app-id", "sync-inventory")
        .containsEntry("dapr.io/app-port", "8080");
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskKind.class,
      names = {"CALL_HTTP", "RUN_SHELL", "RUN_SCRIPT_JS", "RUN_SCRIPT_PYTHON"})
  @DisplayName("an activity-invoked step gets a WorkflowAccessPolicy allowing the orchestrator")
  void activityStepGetsAccessPolicy(TaskKind kind) {
    StepService step =
        new StepService("sync-inventory", kind, "ghcr.io/tonylibs/step:latest", Map.of());

    List<GenericKubernetesResource> policies =
        synthesizer.workflowAccessPolicies(planWith(step), NAMESPACE);

    assertThat(policies).hasSize(1);
    GenericKubernetesResource policy = policies.get(0);
    assertThat(policy.getKind()).isEqualTo("WorkflowAccessPolicy");
    assertThat(scopesOf(policy)).containsExactly("sync-inventory");

    Map<String, Object> rule = firstRuleOf(policy);
    assertThat(callerAppIds(rule)).containsExactly("order");
    assertThat(activityNames(rule)).containsExactly("Run");
  }

  @Test
  @DisplayName("a call:openapi step gets no WorkflowAccessPolicy")
  void openApiStepGetsNoAccessPolicy() {
    StepService step =
        new StepService(
            "lookup-price", TaskKind.CALL_OPENAPI, "ghcr.io/tonylibs/step:latest", Map.of());

    assertThat(synthesizer.workflowAccessPolicies(planWith(step), NAMESPACE)).isEmpty();
  }

  @Test
  @DisplayName("a Knative step renders literals and Kubernetes secret-key environment sources")
  void knativeStepRendersTypedEnvironmentValues() {
    Map<String, io.dws.controller.model.EnvValue> env = new LinkedHashMap<>();
    env.put("AUTH_SCHEME", new Literal("bearer"));
    env.put("AUTH_TOKEN", new SecretKeyRef("API_TOKEN", "value"));
    StepService step =
        new StepService("call-api", TaskKind.CALL_HTTP, "ghcr.io/tonylibs/step:latest", env);

    List<Map<String, Object>> rendered =
        containerEnv(synthesizer.knativeServices(planWith(step), NAMESPACE).getFirst());

    assertThat(rendered)
        .containsExactlyInAnyOrder(
            Map.of("name", "AUTH_SCHEME", "value", "bearer"),
            Map.of(
                "name",
                "AUTH_TOKEN",
                "valueFrom",
                Map.of("secretKeyRef", Map.of("name", "API_TOKEN", "key", "value"))));
  }

  @Test
  @DisplayName("the orchestrator renders literals and Kubernetes secret-key environment sources")
  void orchestratorRendersTypedEnvironmentValues() {
    Map<String, io.dws.controller.model.EnvValue> env = new LinkedHashMap<>();
    env.put("DEFINITION_KEY", new Literal("definition"));
    env.put("SECRET_API_TOKEN", new SecretKeyRef("API_TOKEN", "value"));
    OrchestratorSpec orchestrator =
        new OrchestratorSpec(
            "order-orchestrator",
            "ghcr.io/tonylibs/dws-orchestrator:latest",
            "order",
            8080,
            1,
            env);
    DeploymentPlan plan =
        new DeploymentPlan(
            "order",
            "vabc12345",
            "order@vabc12345",
            "dws-def-order-vabc12345",
            "spec: text",
            List.of(),
            List.of(),
            orchestrator);

    List<EnvVar> rendered =
        synthesizer
            .orchestratorDeployment(plan, NAMESPACE)
            .getSpec()
            .getTemplate()
            .getSpec()
            .getContainers()
            .getFirst()
            .getEnv();

    EnvVar literal = envVar(rendered, "DEFINITION_KEY");
    EnvVar secret = envVar(rendered, "SECRET_API_TOKEN");
    assertThat(literal.getValue()).isEqualTo("definition");
    assertThat(literal.getValueFrom()).isNull();
    assertThat(secret.getValue()).isNull();
    assertThat(secret.getValueFrom().getSecretKeyRef().getName()).isEqualTo("API_TOKEN");
    assertThat(secret.getValueFrom().getSecretKeyRef().getKey()).isEqualTo("value");
  }

  @Test
  @DisplayName("declared workflow secrets are projected to the orchestrator with SECRET_ names")
  void declaredSecretsAreProjectedToOrchestrator() {
    DeploymentPlan plan = compiler.compile(sharedOAuthDefinition());

    List<EnvVar> rendered =
        synthesizer
            .orchestratorDeployment(plan, NAMESPACE)
            .getSpec()
            .getTemplate()
            .getSpec()
            .getContainers()
            .getFirst()
            .getEnv();

    assertThat(rendered)
        .extracting(EnvVar::getName)
        .contains("SECRET_OAUTH_CLIENT_ID", "SECRET_OAUTH_CLIENT_SECRET");
    assertThat(
            envVar(rendered, "SECRET_OAUTH_CLIENT_ID").getValueFrom().getSecretKeyRef().getName())
        .isEqualTo("OAUTH_CLIENT_ID");
    assertThat(
            envVar(rendered, "SECRET_OAUTH_CLIENT_SECRET")
                .getValueFrom()
                .getSecretKeyRef()
                .getKey())
        .isEqualTo("value");
  }

  @Test
  @DisplayName("a workflow without declared secrets keeps the literal orchestrator environment")
  void noSecretWorkflowKeepsLiteralOrchestratorEnvironment() {
    DeploymentPlan plan = compiler.compile(noSecretDefinition());

    List<EnvVar> rendered =
        synthesizer
            .orchestratorDeployment(plan, NAMESPACE)
            .getSpec()
            .getTemplate()
            .getSpec()
            .getContainers()
            .getFirst()
            .getEnv();

    assertThat(rendered)
        .extracting(EnvVar::getName)
        .containsExactlyInAnyOrder("DEFINITION_STORE", "DEFINITION_KEY");
    assertThat(rendered).allSatisfy(value -> assertThat(value.getValueFrom()).isNull());
  }

  @Test
  @DisplayName("equivalent OAuth calls share one scoped endpoint, middleware, and configuration")
  void equivalentOAuthCallsShareScopedResources() {
    DeploymentPlan plan = compiler.compile(sharedOAuthDefinition());
    OAuthEndpoint descriptor = plan.oauthEndpoints().getFirst();

    List<GenericKubernetesResource> endpoints = synthesizer.oauthHttpEndpoints(plan, NAMESPACE);
    List<GenericKubernetesResource> middleware =
        synthesizer.oauthMiddlewareComponents(plan, NAMESPACE);
    List<GenericKubernetesResource> configurations =
        synthesizer.oauthConfigurations(plan, NAMESPACE);

    assertThat(endpoints).hasSize(1);
    assertThat(middleware).hasSize(1);
    assertThat(configurations).hasSize(1);
    assertThat(endpoints.getFirst().getMetadata().getName()).isEqualTo(descriptor.name());
    assertThat(endpoints.getFirst().getAdditionalProperties())
        .containsEntry("scopes", List.of("get-account", "list-accounts"));
    assertThat(spec(endpoints.getFirst())).containsEntry("baseUrl", "https://api.example.test");
    assertThat(middleware.getFirst().getAdditionalProperties())
        .containsEntry("scopes", List.of("get-account", "list-accounts"));
    assertThat(endpoints.getFirst().getMetadata().getLabels())
        .containsEntry(Labels.WORKFLOW, "oauth-resource-sharing")
        .containsEntry(Labels.VERSION, plan.versionId());
    assertThat(middleware.getFirst().getMetadata().getLabels())
        .containsEntry(Labels.WORKFLOW, "oauth-resource-sharing")
        .containsEntry(Labels.VERSION, plan.versionId());
    assertThat(configurations.getFirst().getMetadata().getLabels())
        .containsEntry(Labels.WORKFLOW, "oauth-resource-sharing")
        .containsEntry(Labels.VERSION, plan.versionId());

    Map<String, Object> handler = firstHttpHandler(configurations.getFirst());
    assertThat(handler)
        .containsEntry("name", descriptor.name())
        .containsEntry("type", "middleware.http.oauth2clientcredentials");

    assertThat(synthesizer.knativeServices(plan, NAMESPACE))
        .allSatisfy(
            service ->
                assertThat(templateAnnotations(service))
                    .containsEntry("dapr.io/config", descriptor.name()));
  }

  @Test
  @DisplayName("OAuth middleware uses secret metadata, canonical scopes, and a narrow path filter")
  void oauthMiddlewareUsesSecretMetadataAndNarrowPathFilter() {
    DeploymentPlan plan = compiler.compile(sharedOAuthDefinition());
    OAuthEndpoint descriptor = plan.oauthEndpoints().getFirst();
    GenericKubernetesResource component =
        synthesizer.oauthMiddlewareComponents(plan, NAMESPACE).getFirst();
    List<Map<String, Object>> metadata = componentMetadata(component);

    assertThat(metadata)
        .contains(
            Map.of(
                "name",
                "clientId",
                "secretKeyRef",
                Map.of("name", "OAUTH_CLIENT_ID", "key", "value")),
            Map.of(
                "name",
                "clientSecret",
                "secretKeyRef",
                Map.of("name", "OAUTH_CLIENT_SECRET", "key", "value")),
            Map.of("name", "scopes", "value", "accounts.read,accounts.write"),
            Map.of("name", "tokenURL", "value", "https://identity.example.test/oauth/token"),
            Map.of("name", "headerName", "value", "authorization"),
            Map.of("name", "authStyle", "value", "1"),
            Map.of(
                "name",
                "pathFilter",
                "value",
                "^/v1\\.0/invoke/" + descriptor.name() + "/method(?:/v1/account|/v1/accounts)$"));
    assertThat(metadataEntry(metadata, "clientId")).doesNotContainKey("value");
    assertThat(metadataEntry(metadata, "clientSecret")).doesNotContainKey("value");

    String serialized = Serialization.asJson(component);
    assertThat(serialized)
        .contains("OAUTH_CLIENT_ID", "OAUTH_CLIENT_SECRET")
        .doesNotContain("correct-horse-battery-staple");
  }

  @Test
  @DisplayName("different OAuth policy content produces distinct version-scoped resource sets")
  void differentOAuthPoliciesProduceDistinctResourceSets() {
    DeploymentPlan plan = compiler.compile(differentOAuthPoliciesDefinition());

    assertThat(plan.oauthEndpoints()).hasSize(2);
    assertThat(synthesizer.oauthHttpEndpoints(plan, NAMESPACE)).hasSize(2);
    assertThat(synthesizer.oauthMiddlewareComponents(plan, NAMESPACE)).hasSize(2);
    assertThat(synthesizer.oauthConfigurations(plan, NAMESPACE)).hasSize(2);
    assertThat(synthesizer.oauthMiddlewareComponents(plan, NAMESPACE))
        .extracting(StackSynthesizerTest::componentMetadata)
        .extracting(metadata -> metadataEntry(metadata, "authStyle").get("value"))
        .containsExactlyInAnyOrder("1", "2");
    assertThat(plan.oauthEndpoints())
        .extracting(OAuthEndpoint::name)
        .doesNotHaveDuplicates()
        .allSatisfy(name -> assertThat(name).startsWith("oauth-policy-split-" + plan.versionId()));
  }

  private Map<String, String> synthesizeStepAnnotations(TaskKind kind) {
    StepService step =
        new StepService("sync-inventory", kind, "ghcr.io/tonylibs/step:latest", Map.of());
    GenericKubernetesResource service =
        synthesizer.knativeServices(planWith(step), NAMESPACE).get(0);
    return templateAnnotations(service);
  }

  private static DeploymentPlan planWith(StepService step) {
    OrchestratorSpec orchestrator =
        new OrchestratorSpec(
            "order-orchestrator",
            "ghcr.io/tonylibs/dws-orchestrator:latest",
            "order",
            8080,
            1,
            Map.of());
    return new DeploymentPlan(
        "order",
        "vabc12345",
        "order@vabc12345",
        "dws-def-order-vabc12345",
        "spec: text",
        List.of(step),
        List.of(),
        orchestrator);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> templateAnnotations(GenericKubernetesResource service) {
    Map<String, Object> spec = (Map<String, Object>) service.getAdditionalProperties().get("spec");
    Map<String, Object> template = (Map<String, Object>) spec.get("template");
    Map<String, Object> metadata = (Map<String, Object>) template.get("metadata");
    return (Map<String, String>) metadata.get("annotations");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> containerEnv(GenericKubernetesResource service) {
    Map<String, Object> spec = (Map<String, Object>) service.getAdditionalProperties().get("spec");
    Map<String, Object> template = (Map<String, Object>) spec.get("template");
    Map<String, Object> templateSpec = (Map<String, Object>) template.get("spec");
    Map<String, Object> container =
        ((List<Map<String, Object>>) templateSpec.get("containers")).getFirst();
    return (List<Map<String, Object>>) container.get("env");
  }

  private static EnvVar envVar(List<EnvVar> env, String name) {
    return env.stream().filter(value -> name.equals(value.getName())).findFirst().orElseThrow();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> spec(GenericKubernetesResource resource) {
    return (Map<String, Object>) resource.getAdditionalProperties().get("spec");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> componentMetadata(GenericKubernetesResource component) {
    return (List<Map<String, Object>>) spec(component).get("metadata");
  }

  private static Map<String, Object> metadataEntry(
      List<Map<String, Object>> metadata, String name) {
    return metadata.stream()
        .filter(entry -> name.equals(entry.get("name")))
        .findFirst()
        .orElseThrow();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> firstHttpHandler(GenericKubernetesResource configuration) {
    Map<String, Object> pipeline = (Map<String, Object>) spec(configuration).get("httpPipeline");
    return ((List<Map<String, Object>>) pipeline.get("handlers")).getFirst();
  }

  private static String sharedOAuthDefinition() {
    return """
        document:
          dsl: '1.0.0'
          namespace: default
          name: oauth-resource-sharing
          version: '1.0.0'
        use:
          secrets: [OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET]
          authentications:
            accounts:
              oauth2:
                authority: https://identity.example.test
                grant: client_credentials
                client:
                  id: ${ $secrets.OAUTH_CLIENT_ID }
                  secret: ${ $secrets.OAUTH_CLIENT_SECRET }
                endpoints:
                  token: /oauth/token
                scopes: [accounts.write, accounts.read, accounts.read]
        do:
          - getAccount:
              call: http
              with:
                method: get
                endpoint:
                  uri: https://api.example.test/v1/account
                  authentication:
                    use: accounts
          - listAccounts:
              call: http
              with:
                method: get
                endpoint:
                  uri: https://api.example.test/v1/accounts
                  authentication:
                    use: accounts
        """;
  }

  private static String differentOAuthPoliciesDefinition() {
    return """
        document:
          dsl: '1.0.0'
          namespace: default
          name: oauth-policy-split
          version: '1.0.0'
        use:
          secrets: [OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET]
          authentications:
            reader:
              oauth2:
                authority: https://identity.example.test
                grant: client_credentials
                client:
                  id: ${ $secrets.OAUTH_CLIENT_ID }
                  secret: ${ $secrets.OAUTH_CLIENT_SECRET }
                endpoints:
                  token: /oauth/token
                scopes: [accounts.read]
            writer:
              oauth2:
                authority: https://identity.example.test
                grant: client_credentials
                client:
                  id: ${ $secrets.OAUTH_CLIENT_ID }
                  secret: ${ $secrets.OAUTH_CLIENT_SECRET }
                  authentication: client_secret_basic
                endpoints:
                  token: /oauth/token
                scopes: [accounts.write]
        do:
          - getAccount:
              call: http
              with:
                method: get
                endpoint:
                  uri: https://api.example.test/v1/account
                  authentication:
                    use: reader
          - updateAccount:
              call: http
              with:
                method: post
                endpoint:
                  uri: https://api.example.test/v1/account
                  authentication:
                    use: writer
        """;
  }

  private static String noSecretDefinition() {
    return """
        document:
          dsl: '1.0.0'
          namespace: default
          name: no-secret
          version: '1.0.0'
        do:
          - getAccount:
              call: http
              with:
                method: get
                endpoint: https://api.example.test/v1/account
        """;
  }

  @SuppressWarnings("unchecked")
  private static List<String> scopesOf(GenericKubernetesResource policy) {
    return (List<String>) policy.getAdditionalProperties().get("scopes");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> firstRuleOf(GenericKubernetesResource policy) {
    Map<String, Object> spec = (Map<String, Object>) policy.getAdditionalProperties().get("spec");
    return ((List<Map<String, Object>>) spec.get("rules")).get(0);
  }

  @SuppressWarnings("unchecked")
  private static List<String> callerAppIds(Map<String, Object> rule) {
    // Serialized CRD key is appID; read whichever single value each caller entry carries so the
    // assertion is robust to the exact key casing the generated model emits.
    return ((List<Map<String, Object>>) rule.get("callers"))
        .stream().map(caller -> String.valueOf(caller.values().iterator().next())).toList();
  }

  @SuppressWarnings("unchecked")
  private static List<String> activityNames(Map<String, Object> rule) {
    return ((List<Map<String, Object>>) rule.get("activities"))
        .stream().map(activity -> String.valueOf(activity.get("name"))).toList();
  }
}
