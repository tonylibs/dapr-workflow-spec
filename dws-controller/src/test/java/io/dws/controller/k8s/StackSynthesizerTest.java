package io.dws.controller.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.dws.controller.compile.V1OrchestratorCompiler;
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
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;
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
  private static final String SERVICE_ACCOUNT = "dws-orchestrator";
  private static final ImageCatalog IMAGES =
      new ImageCatalog(
          "sw-call-http:1.0",
          "sw-call-openapi:1.0",
          "sw-call-grpc:1.0",
          "sw-call-asyncapi:1.0",
          "sw-call-a2a:1.0",
          "sw-run-shell:1.0",
          "sw-run-script-js:1.0",
          "sw-run-script-python:1.0",
          "sw-orchestrator:1.0");

  private final StackSynthesizer synthesizer = new StackSynthesizer();
  private final WorkflowCompiler compiler =
      new V1OrchestratorCompiler(IMAGES, ignored -> "openapi".getBytes(StandardCharsets.UTF_8));

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
  @EnumSource(TaskKind.class)
  @DisplayName("every Knative step moves daprd metrics off queue-proxy's 9090")
  void knativeStepMovesDaprMetricsPort(TaskKind kind) {
    Map<String, String> annotations = synthesizeStepAnnotations(kind);

    assertThat(annotations).containsEntry("dapr.io/metrics-port", "9095");
  }

  @Test
  @DisplayName("the step daprd metrics port clashes with no other listener in the pod")
  void daprMetricsPortIsFreeInAKnativePod() {
    // Knative Serving's queue-proxy (pkg/networking/constants.go, k8s_validation.go
    // reservedPorts): 8012 http, 8013 h2c, 8112 https, 8022 admin, 9090 autoscaler metrics,
    // 9091 request metrics, 8008 profiling. daprd's own injector defaults: 3500 http,
    // 3501 public/healthz, 40000 debug, 50001 api gRPC, 50002 internal gRPC. 8080 is the step
    // container's app port.
    List<Integer> taken =
        List.of(8012, 8013, 8112, 8022, 9090, 9091, 8008, 3500, 3501, 40000, 50001, 50002, 8080);

    assertThat(Integer.parseInt(StackSynthesizer.STEP_DAPR_METRICS_PORT)).isNotIn(taken);
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
    env.put("AUTH_TOKEN", new SecretKeyRef("apitoken", "value"));
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
                Map.of("secretKeyRef", Map.of("name", "apitoken", "key", "value"))));
  }

  @Test
  @DisplayName("the orchestrator renders literals and Kubernetes secret-key environment sources")
  void orchestratorRendersTypedEnvironmentValues() {
    Map<String, io.dws.controller.model.EnvValue> env = new LinkedHashMap<>();
    env.put("DEFINITION_KEY", new Literal("definition"));
    env.put("SECRET_apitoken", new SecretKeyRef("apitoken", "value"));
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
            .orchestratorDeployment(plan, NAMESPACE, SERVICE_ACCOUNT)
            .getSpec()
            .getTemplate()
            .getSpec()
            .getContainers()
            .getFirst()
            .getEnv();

    EnvVar literal = envVar(rendered, "DEFINITION_KEY");
    EnvVar secret = envVar(rendered, "SECRET_apitoken");
    assertThat(literal.getValue()).isEqualTo("definition");
    assertThat(literal.getValueFrom()).isNull();
    assertThat(secret.getValue()).isNull();
    assertThat(secret.getValueFrom().getSecretKeyRef().getName()).isEqualTo("apitoken");
    assertThat(secret.getValueFrom().getSecretKeyRef().getKey()).isEqualTo("value");
  }

  @Test
  @DisplayName("the orchestrator pod runs as the configured dedicated service account")
  void orchestratorRunsAsDedicatedServiceAccount() {
    DeploymentPlan plan = compiler.compile(noSecretDefinition());

    var podSpec =
        synthesizer
            .orchestratorDeployment(plan, NAMESPACE, "custom-orchestrator")
            .getSpec()
            .getTemplate()
            .getSpec();

    assertThat(podSpec.getServiceAccountName()).isEqualTo("custom-orchestrator");
  }

  @Test
  @DisplayName(
      "the definition store is handed over as DAPR_CONFIG_STORE, the name the orchestrator reads")
  void definitionStoreUsesTheOrchestratorsEnvVarName() {
    DeploymentPlan plan = compiler.compile(noSecretDefinition());

    List<EnvVar> rendered =
        synthesizer
            .orchestratorDeployment(plan, NAMESPACE, SERVICE_ACCOUNT)
            .getSpec()
            .getTemplate()
            .getSpec()
            .getContainers()
            .getFirst()
            .getEnv();

    assertThat(envVar(rendered, "DAPR_CONFIG_STORE").getValue())
        .isEqualTo(plan.definitionResource());
    assertThat(rendered).extracting(EnvVar::getName).doesNotContain("DEFINITION_STORE");
  }

  @Test
  @DisplayName("declared workflow secrets are projected to the orchestrator with SECRET_ names")
  void declaredSecretsAreProjectedToOrchestrator() {
    DeploymentPlan plan = compiler.compile(sharedOAuthDefinition());

    List<EnvVar> rendered =
        synthesizer
            .orchestratorDeployment(plan, NAMESPACE, SERVICE_ACCOUNT)
            .getSpec()
            .getTemplate()
            .getSpec()
            .getContainers()
            .getFirst()
            .getEnv();

    assertThat(rendered)
        .extracting(EnvVar::getName)
        .contains("SECRET_oauthclientid", "SECRET_oauthclientsecret");
    assertThat(envVar(rendered, "SECRET_oauthclientid").getValueFrom().getSecretKeyRef().getName())
        .isEqualTo("oauthclientid");
    assertThat(
            envVar(rendered, "SECRET_oauthclientsecret").getValueFrom().getSecretKeyRef().getKey())
        .isEqualTo("value");
  }

  @Test
  @DisplayName("a workflow without declared secrets keeps the literal orchestrator environment")
  void noSecretWorkflowKeepsLiteralOrchestratorEnvironment() {
    DeploymentPlan plan = compiler.compile(noSecretDefinition());

    List<EnvVar> rendered =
        synthesizer
            .orchestratorDeployment(plan, NAMESPACE, SERVICE_ACCOUNT)
            .getSpec()
            .getTemplate()
            .getSpec()
            .getContainers()
            .getFirst()
            .getEnv();

    assertThat(rendered)
        .extracting(EnvVar::getName)
        .containsExactlyInAnyOrder("DAPR_CONFIG_STORE", "DEFINITION_KEY");
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
  @DisplayName(
      "a2a 'with.server' oauth2 synthesizes the same scoped resources and app-id match as http")
  void a2aServerOauth2SharesScopedResources() {
    DeploymentPlan plan = compiler.compile(a2aServerOAuthDefinition());
    OAuthEndpoint descriptor = plan.oauthEndpoints().getFirst();

    List<GenericKubernetesResource> endpoints = synthesizer.oauthHttpEndpoints(plan, NAMESPACE);
    List<GenericKubernetesResource> middleware =
        synthesizer.oauthMiddlewareComponents(plan, NAMESPACE);
    List<GenericKubernetesResource> configurations =
        synthesizer.oauthConfigurations(plan, NAMESPACE);

    assertThat(endpoints).hasSize(1);
    assertThat(middleware).hasSize(1);
    assertThat(configurations).hasSize(1);
    assertThat(descriptor.appIds()).containsExactly("dispatch-agent");
    assertThat(endpoints.getFirst().getAdditionalProperties())
        .containsEntry("scopes", List.of("dispatch-agent"));
    assertThat(spec(endpoints.getFirst())).containsEntry("baseUrl", "https://agent.example.test");

    assertThat(synthesizer.knativeServices(plan, NAMESPACE))
        .allSatisfy(
            service ->
                assertThat(templateAnnotations(service))
                    .containsEntry("dapr.io/config", descriptor.name())
                    .containsEntry("dapr.io/app-id", "dispatch-agent"));
  }

  @Test
  @DisplayName("Dapr 1.18.1 OAuth middleware uses comma-delimited scopes and a narrow path filter")
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
                Map.of("name", "oauthclientid", "key", "value")),
            Map.of(
                "name",
                "clientSecret",
                "secretKeyRef",
                Map.of("name", "oauthclientsecret", "key", "value")),
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
    Pattern pathFilter =
        Pattern.compile((String) metadataEntry(metadata, "pathFilter").get("value"));
    assertThat(
            pathFilter
                .matcher("/v1.0/invoke/" + descriptor.name() + "/method/v1/account")
                .matches())
        .isTrue();
    assertThat(
            pathFilter
                .matcher("/v1.0/invoke/" + descriptor.name() + "/method/v1/unrelated")
                .matches())
        .isFalse();
    assertThat(pathFilter.matcher("/v1.0/invoke/other-endpoint/method/v1/account").matches())
        .isFalse();

    String serialized = Serialization.asJson(component);
    assertThat(serialized)
        .contains("oauthclientid", "oauthclientsecret")
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

  // ---- orchestrator tracing (observability-orchestrator-tracing) ----

  private static final ObservabilitySettings ON = new ObservabilitySettings(true, "true");

  private static DeploymentPlan orchestratorPlan(
      String workflow, String versionId, Map<String, io.dws.controller.model.EnvValue> env) {
    OrchestratorSpec orchestrator =
        new OrchestratorSpec(
            workflow + "-" + versionId, "sw-orchestrator:1.0", workflow, 8080, 1, env);
    return new DeploymentPlan(
        workflow,
        versionId,
        workflow + "@" + versionId,
        "dws-def-" + workflow + "-" + versionId,
        "spec: text",
        List.of(),
        List.of(),
        orchestrator);
  }

  private static DeploymentPlan orchestratorPlan() {
    // Ordered: the rendered env list follows the map's iteration order.
    Map<String, io.dws.controller.model.EnvValue> env = new LinkedHashMap<>();
    env.put("DAPR_CONFIG_STORE", new Literal("dws-def-order-fulfilment-v1a2b3c4d"));
    env.put("DEFINITION_KEY", new Literal("definition"));
    return orchestratorPlan("order-fulfilment", "v1a2b3c4d", env);
  }

  private static Map<String, String> podAnnotations(Deployment deployment) {
    return deployment.getSpec().getTemplate().getMetadata().getAnnotations();
  }

  private static List<EnvVar> orchestratorEnv(Deployment deployment) {
    return deployment.getSpec().getTemplate().getSpec().getContainers().getFirst().getEnv();
  }

  /**
   * The orchestrator Deployment exactly as rendered with tracing off, by hand: the
   * pre-observability shape plus the dedicated service account.
   */
  private static Deployment todaysOrchestratorDeployment() {
    return new DeploymentBuilder()
        .withNewMetadata()
        .withName("order-fulfilment-v1a2b3c4d")
        .withNamespace(NAMESPACE)
        .withLabels(
            Map.of(
                "dws.io/workflow", "order-fulfilment",
                "dws.io/version", "v1a2b3c4d",
                "dws.io/managed-by", "dws-controller"))
        .endMetadata()
        .withNewSpec()
        .withReplicas(1)
        .withNewSelector()
        .withMatchLabels(Map.of("app", "order-fulfilment-v1a2b3c4d"))
        .endSelector()
        .withNewTemplate()
        .withNewMetadata()
        .withLabels(
            Map.of(
                "dws.io/workflow", "order-fulfilment",
                "dws.io/version", "v1a2b3c4d",
                "dws.io/managed-by", "dws-controller",
                "app", "order-fulfilment-v1a2b3c4d"))
        .withAnnotations(
            Map.of(
                "dapr.io/enabled", "true",
                "dapr.io/app-id", "order-fulfilment",
                "dapr.io/app-port", "8080"))
        .endMetadata()
        .withNewSpec()
        .withServiceAccountName(SERVICE_ACCOUNT)
        .addNewContainer()
        .withName("orchestrator")
        .withImage("sw-orchestrator:1.0")
        .withEnv(
            new EnvVarBuilder()
                .withName("DAPR_CONFIG_STORE")
                .withValue("dws-def-order-fulfilment-v1a2b3c4d")
                .build(),
            new EnvVarBuilder().withName("DEFINITION_KEY").withValue("definition").build())
        .addNewPort()
        .withContainerPort(8080)
        .endPort()
        .endContainer()
        .endSpec()
        .endTemplate()
        .endSpec()
        .build();
  }

  @Test
  @DisplayName("observability off renders exactly the hand-built pre-observability Deployment")
  void offSettingsRenderTodaysDeployment() {
    DeploymentPlan plan = orchestratorPlan();

    Deployment off =
        synthesizer.orchestratorDeployment(
            plan, NAMESPACE, SERVICE_ACCOUNT, ObservabilitySettings.OFF);

    assertThat(off).isEqualTo(todaysOrchestratorDeployment());
    // Spelled out so a failure names the drifting part rather than a whole-object diff.
    assertThat(podAnnotations(off))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "dapr.io/enabled", "true",
                "dapr.io/app-id", "order-fulfilment",
                "dapr.io/app-port", "8080"));
    assertThat(orchestratorEnv(off))
        .extracting(EnvVar::getName, EnvVar::getValue)
        .containsExactly(
            tuple("DAPR_CONFIG_STORE", "dws-def-order-fulfilment-v1a2b3c4d"),
            tuple("DEFINITION_KEY", "definition"));
    assertThat(off.getSpec().getTemplate().getSpec().getContainers())
        .extracting(container -> container.getName())
        .containsExactly("orchestrator");
  }

  @Test
  @DisplayName("the three-argument overload renders the same Deployment as OFF settings")
  void threeArgumentOverloadMatchesOffSettings() {
    DeploymentPlan plan = orchestratorPlan();

    assertThat(synthesizer.orchestratorDeployment(plan, NAMESPACE, SERVICE_ACCOUNT))
        .isEqualTo(
            synthesizer.orchestratorDeployment(
                plan, NAMESPACE, SERVICE_ACCOUNT, ObservabilitySettings.OFF))
        .isEqualTo(todaysOrchestratorDeployment());
  }

  @Test
  @DisplayName("enabled stamps targeted injection and the tracing Configuration reference")
  void enabledStampsTargetedInjection() {
    Deployment deployment =
        synthesizer.orchestratorDeployment(orchestratorPlan(), NAMESPACE, SERVICE_ACCOUNT, ON);

    assertThat(podAnnotations(deployment))
        .containsEntry("instrumentation.opentelemetry.io/inject-java", "true")
        .containsEntry("instrumentation.opentelemetry.io/container-names", "orchestrator")
        .containsEntry("dapr.io/config", "dws-tracing")
        .containsEntry("dapr.io/enabled", "true")
        .containsEntry("dapr.io/app-id", "order-fulfilment")
        .containsEntry("dapr.io/app-port", "8080")
        .hasSize(6);
    assertThat(deployment.getSpec().getTemplate().getSpec().getContainers())
        .extracting(container -> container.getName())
        .containsExactly("orchestrator");
  }

  @Test
  @DisplayName("enabled carries a custom Instrumentation reference into inject-java")
  void enabledCarriesCustomInstrumentationReference() {
    Deployment deployment =
        synthesizer.orchestratorDeployment(
            orchestratorPlan(),
            NAMESPACE,
            SERVICE_ACCOUNT,
            new ObservabilitySettings(true, "dws-system/dws-instrumentation"));

    assertThat(podAnnotations(deployment))
        .containsEntry(
            "instrumentation.opentelemetry.io/inject-java", "dws-system/dws-instrumentation");
  }

  @Test
  @DisplayName("enabled asserts service name and workflow identity, with no node or sampling input")
  void enabledAssertsIdentityValues() {
    Deployment deployment =
        synthesizer.orchestratorDeployment(orchestratorPlan(), NAMESPACE, SERVICE_ACCOUNT, ON);

    List<EnvVar> env = orchestratorEnv(deployment);
    assertThat(envVar(env, "OTEL_SERVICE_NAME").getValue()).isEqualTo("order-fulfilment");
    assertThat(envVar(env, "OTEL_RESOURCE_ATTRIBUTES").getValue())
        .isEqualTo("dws.workflow.name=order-fulfilment,dws.workflow.version=v1a2b3c4d");
    assertThat(env)
        .extracting(EnvVar::getName)
        .noneMatch(name -> name.contains("dws.node") || name.contains("SAMPLER"));
    assertThat(podAnnotations(deployment).keySet())
        .noneMatch(key -> key.contains("dws.node") || key.toLowerCase().contains("sampl"));
    assertThat(envVar(env, "OTEL_RESOURCE_ATTRIBUTES").getValue())
        .doesNotContain("dws.node")
        .doesNotContain("service.name");
  }

  @Test
  @DisplayName("identity values are percent-encoded so ',' and '=' never split the list")
  void identityValuesArePercentEncoded() {
    DeploymentPlan plan = orchestratorPlan("a,b=c", "v1,2=3", Map.of());

    String attributes =
        envVar(
                orchestratorEnv(
                    synthesizer.orchestratorDeployment(plan, NAMESPACE, SERVICE_ACCOUNT, ON)),
                "OTEL_RESOURCE_ATTRIBUTES")
            .getValue();

    assertThat(attributes).isEqualTo("dws.workflow.name=a%2Cb%3Dc,dws.workflow.version=v1%2C2%3D3");
    assertThat(attributes.split(",")).hasSize(2);
  }

  @Test
  @DisplayName("a literal percent sign is encoded too, so decoding is lossless")
  void percentSignIsEncoded() {
    DeploymentPlan plan = orchestratorPlan("50%off", "v1", Map.of());

    String attributes =
        envVar(
                orchestratorEnv(
                    synthesizer.orchestratorDeployment(plan, NAMESPACE, SERVICE_ACCOUNT, ON)),
                "OTEL_RESOURCE_ATTRIBUTES")
            .getValue();

    assertThat(attributes).startsWith("dws.workflow.name=50%25off,");
  }

  @Test
  @DisplayName("existing resource attributes are appended to and an existing service name is kept")
  void appendsToExistingResourceAttributes() {
    Map<String, io.dws.controller.model.EnvValue> env = new LinkedHashMap<>();
    env.put("OTEL_RESOURCE_ATTRIBUTES", new Literal("team=x"));
    env.put("OTEL_SERVICE_NAME", new Literal("custom-name"));
    DeploymentPlan plan = orchestratorPlan("order-fulfilment", "v1a2b3c4d", env);

    List<EnvVar> rendered =
        orchestratorEnv(synthesizer.orchestratorDeployment(plan, NAMESPACE, SERVICE_ACCOUNT, ON));

    assertThat(envVar(rendered, "OTEL_RESOURCE_ATTRIBUTES").getValue())
        .isEqualTo("team=x,dws.workflow.name=order-fulfilment,dws.workflow.version=v1a2b3c4d");
    assertThat(envVar(rendered, "OTEL_SERVICE_NAME").getValue()).isEqualTo("custom-name");
    assertThat(rendered)
        .extracting(EnvVar::getName)
        .filteredOn(name -> name.startsWith("OTEL_"))
        .containsExactlyInAnyOrder("OTEL_RESOURCE_ATTRIBUTES", "OTEL_SERVICE_NAME");
  }

  @Test
  @DisplayName("a blank existing resource-attributes value gains no leading comma")
  void blankExistingResourceAttributesGainNoLeadingComma() {
    DeploymentPlan plan =
        orchestratorPlan("wf", "v1", Map.of("OTEL_RESOURCE_ATTRIBUTES", new Literal("  ")));

    assertThat(
            envVar(
                    orchestratorEnv(
                        synthesizer.orchestratorDeployment(plan, NAMESPACE, SERVICE_ACCOUNT, ON)),
                    "OTEL_RESOURCE_ATTRIBUTES")
                .getValue())
        .isEqualTo("dws.workflow.name=wf,dws.workflow.version=v1");
  }

  @Test
  @DisplayName("a secret-sourced resource-attributes value is left untouched")
  void secretSourcedResourceAttributesAreLeftUntouched() {
    DeploymentPlan plan =
        orchestratorPlan(
            "wf",
            "v1",
            Map.of("OTEL_RESOURCE_ATTRIBUTES", new SecretKeyRef("otel-attrs", "value")));

    List<EnvVar> rendered =
        orchestratorEnv(synthesizer.orchestratorDeployment(plan, NAMESPACE, SERVICE_ACCOUNT, ON));

    EnvVar attributes = envVar(rendered, "OTEL_RESOURCE_ATTRIBUTES");
    assertThat(attributes.getValue()).isNull();
    assertThat(attributes.getValueFrom().getSecretKeyRef().getName()).isEqualTo("otel-attrs");
    assertThat(rendered)
        .extracting(EnvVar::getName)
        .filteredOn(name -> name.equals("OTEL_RESOURCE_ATTRIBUTES"))
        .hasSize(1);
  }

  @Test
  @DisplayName("a secret-sourced resource-attributes value logs exactly one WARN per synthesis")
  void secretSourcedResourceAttributesLogOneWarning() {
    DeploymentPlan plan =
        orchestratorPlan(
            "wf",
            "v1",
            Map.of("OTEL_RESOURCE_ATTRIBUTES", new SecretKeyRef("otel-attrs", "value")));

    try (LogCapture logs = new LogCapture(StackSynthesizer.class)) {
      synthesizer.orchestratorDeployment(plan, NAMESPACE, SERVICE_ACCOUNT, ON);

      assertThat(logs.at(Level.WARNING)).hasSize(1);
      assertThat(LogCapture.message(logs.at(Level.WARNING).getFirst()))
          .contains("OTEL_RESOURCE_ATTRIBUTES", "Secret", "wf-v1");
    }
  }

  @Test
  @DisplayName("literal resource attributes and tracing off log no WARN")
  void literalResourceAttributesAndOffLogNoWarning() {
    DeploymentPlan plan =
        orchestratorPlan("wf", "v1", Map.of("OTEL_RESOURCE_ATTRIBUTES", new Literal("team=x")));
    DeploymentPlan secret =
        orchestratorPlan(
            "wf", "v1", Map.of("OTEL_RESOURCE_ATTRIBUTES", new SecretKeyRef("otel-attrs", "v")));

    try (LogCapture logs = new LogCapture(StackSynthesizer.class)) {
      synthesizer.orchestratorDeployment(plan, NAMESPACE, SERVICE_ACCOUNT, ON);
      synthesizer.orchestratorDeployment(
          secret, NAMESPACE, SERVICE_ACCOUNT, ObservabilitySettings.OFF);

      assertThat(logs.at(Level.WARNING)).isEmpty();
    }
  }

  @Test
  @DisplayName("an existing dapr.io/config is preserved and nothing is stamped")
  void existingDaprConfigIsPreserved() {
    Map<String, String> existing = new LinkedHashMap<>();
    existing.put("dapr.io/enabled", "true");
    existing.put("dapr.io/config", "other");

    Map<String, String> annotations = StackSynthesizer.orchestratorAnnotations(existing, ON);

    assertThat(annotations).isEqualTo(existing).containsEntry("dapr.io/config", "other");
    assertThat(annotations).doesNotContainKey("instrumentation.opentelemetry.io/inject-java");
    assertThat(StackSynthesizer.isInstrumented(annotations)).isFalse();
  }

  @Test
  @DisplayName(
      "signature-level isolation: only the orchestrator Deployment synthesis accepts settings")
  void onlyOrchestratorSynthesisMethodsAcceptSettings() {
    assertThat(StackSynthesizer.class.getDeclaredMethods())
        .filteredOn(method -> !method.isSynthetic())
        .filteredOn(
            method ->
                java.util.Arrays.asList(method.getParameterTypes())
                    .contains(ObservabilitySettings.class))
        .extracting(method -> method.getName())
        .containsOnly("orchestratorDeployment", "orchestratorAnnotations");
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
          secrets: [oauthclientid, oauthclientsecret]
          authentications:
            accounts:
              oauth2:
                authority: https://identity.example.test
                grant: client_credentials
                client:
                  id: ${ $secrets.oauthclientid }
                  secret: ${ $secrets.oauthclientsecret }
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

  private static String a2aServerOAuthDefinition() {
    return """
        document:
          dsl: '1.0.0'
          namespace: default
          name: a2a-oauth-resource-sharing
          version: '1.0.0'
        use:
          secrets: [oauthclientid, oauthclientsecret]
        do:
          - dispatchAgent:
              call: a2a
              with:
                server:
                  uri: https://agent.example.test/rpc
                  authentication:
                    oauth2:
                      authority: https://identity.example.test
                      grant: client_credentials
                      client:
                        id: ${ $secrets.oauthclientid }
                        secret: ${ $secrets.oauthclientsecret }
                      scopes: [agent.invoke]
                method: message/send
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
          secrets: [oauthclientid, oauthclientsecret]
          authentications:
            reader:
              oauth2:
                authority: https://identity.example.test
                grant: client_credentials
                client:
                  id: ${ $secrets.oauthclientid }
                  secret: ${ $secrets.oauthclientsecret }
                endpoints:
                  token: /oauth/token
                scopes: [accounts.read]
            writer:
              oauth2:
                authority: https://identity.example.test
                grant: client_credentials
                client:
                  id: ${ $secrets.oauthclientid }
                  secret: ${ $secrets.oauthclientsecret }
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
