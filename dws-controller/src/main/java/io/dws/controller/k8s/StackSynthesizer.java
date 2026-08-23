package io.dws.controller.k8s;

import imports.dev.knative.serving.Service;
import imports.dev.knative.serving.ServiceProps;
import imports.dev.knative.serving.ServiceSpec;
import imports.dev.knative.serving.ServiceSpecTemplate;
import imports.dev.knative.serving.ServiceSpecTemplateMetadata;
import imports.dev.knative.serving.ServiceSpecTemplateSpec;
import imports.dev.knative.serving.ServiceSpecTemplateSpecContainers;
import imports.dev.knative.serving.ServiceSpecTemplateSpecContainersEnv;
import imports.dev.knative.serving.ServiceSpecTemplateSpecContainersEnvValueFrom;
import imports.dev.knative.serving.ServiceSpecTemplateSpecContainersEnvValueFromSecretKeyRef;
import imports.dev.knative.serving.ServiceSpecTemplateSpecContainersPorts;
import imports.io.dapr.Component;
import imports.io.dapr.ComponentProps;
import imports.io.dapr.ComponentSpec;
import imports.io.dapr.ComponentSpecMetadata;
import imports.io.dapr.ComponentSpecMetadataSecretKeyRef;
import imports.io.dapr.Configuration;
import imports.io.dapr.ConfigurationProps;
import imports.io.dapr.ConfigurationSpec;
import imports.io.dapr.ConfigurationSpecHttpPipeline;
import imports.io.dapr.ConfigurationSpecHttpPipelineHandlers;
import imports.io.dapr.HttpEndpoint;
import imports.io.dapr.HttpEndpointProps;
import imports.io.dapr.HttpEndpointSpec;
import imports.io.dapr.WorkflowAccessPolicy;
import imports.io.dapr.WorkflowAccessPolicyProps;
import imports.io.dapr.WorkflowAccessPolicySpec;
import imports.io.dapr.WorkflowAccessPolicySpecRules;
import imports.io.dapr.WorkflowAccessPolicySpecRulesActivities;
import imports.io.dapr.WorkflowAccessPolicySpecRulesCallers;
import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.EnvValue;
import io.dws.controller.model.OAuthEndpoint;
import io.dws.controller.model.OAuthMiddleware;
import io.dws.controller.model.OrchestratorSpec;
import io.dws.controller.model.StepService;
import io.dws.controller.model.TaskKind;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.cdk8s.ApiObjectMetadata;
import org.cdk8s.Chart;
import org.cdk8s.Testing;

/**
 * Renders a {@link DeploymentPlan} into concrete Kubernetes objects. Knative Services and Dapr
 * Components are synthesized with cdk8s (using the generated imports) and handed on as dynamic
 * resources; the ConfigMap and orchestrator Deployment use the built-in fabric8 models.
 */
@ApplicationScoped
public class StackSynthesizer {

  static final String DEFINITION_KEY = "definition";
  private static final String CONTAINER_PORT_VALUE = "8080";
  private static final int CONTAINER_PORT = 8080;
  private static final String OAUTH_MIDDLEWARE_TYPE = "middleware.http.oauth2clientcredentials";

  /**
   * Canonical activity name every migrated Go step image registers and the orchestrator schedules
   * (mirrors {@code io.dws.orchestrator.workflow.activity.StepActivity#NAME}); the access policy
   * allow-lists exactly this activity.
   */
  private static final String RUN_ACTIVITY = "Run";

  /** Immutable ConfigMap holding the definition text verbatim. */
  public ConfigMap definitionConfigMap(DeploymentPlan plan, String namespace) {
    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(plan.definitionResource())
        .withNamespace(namespace)
        .withLabels(Labels.forPlan(plan))
        .endMetadata()
        .withImmutable(Boolean.TRUE)
        .addToData(DEFINITION_KEY, plan.specText())
        .build();
  }

  /**
   * Dapr configuration-store component backed by the definition ConfigMap, scoped to the workflow
   * app-id.
   */
  public GenericKubernetesResource configurationComponent(DeploymentPlan plan, String namespace) {
    Chart chart = Testing.chart();
    new Component(
        chart,
        plan.definitionResource(),
        ComponentProps.builder()
            .metadata(
                ApiObjectMetadata.builder()
                    .name(plan.definitionResource())
                    .namespace(namespace)
                    .labels(Labels.forPlan(plan))
                    .build())
            .scopes(List.of(plan.workflow()))
            .spec(
                ComponentSpec.builder()
                    .type("configuration.kubernetes")
                    .version("v1")
                    .metadata(
                        List.of(
                            ComponentSpecMetadata.builder()
                                .name("configMapName")
                                .value(plan.definitionResource())
                                .build()))
                    .build())
            .build());
    return toDynamicResource(chart);
  }

  /** One scale-to-zero, Dapr-enabled Knative Service per deployable step. */
  public List<GenericKubernetesResource> knativeServices(DeploymentPlan plan, String namespace) {
    List<GenericKubernetesResource> services = new ArrayList<>(plan.steps().size());
    for (StepService step : plan.steps()) {
      services.add(knativeService(plan, step, namespace));
    }
    return services;
  }

  /** Version-scoped external endpoints, visible only to the steps that requested each policy. */
  public List<GenericKubernetesResource> oauthHttpEndpoints(DeploymentPlan plan, String namespace) {
    List<GenericKubernetesResource> resources = new ArrayList<>(plan.oauthEndpoints().size());
    for (OAuthEndpoint endpoint : plan.oauthEndpoints()) {
      Chart chart = Testing.chart();
      new HttpEndpoint(
          chart,
          endpoint.name(),
          HttpEndpointProps.builder()
              .metadata(oauthMetadata(plan, endpoint, namespace))
              .scopes(List.copyOf(endpoint.appIds()))
              .spec(HttpEndpointSpec.builder().baseUrl(endpoint.baseUrl()).build())
              .build());
      resources.add(toDynamicResource(chart));
    }
    return resources;
  }

  /** OAuth2 client-credentials middleware Components backed only by Kubernetes Secret refs. */
  public List<GenericKubernetesResource> oauthMiddlewareComponents(
      DeploymentPlan plan, String namespace) {
    List<GenericKubernetesResource> resources = new ArrayList<>(plan.oauthEndpoints().size());
    for (OAuthEndpoint endpoint : plan.oauthEndpoints()) {
      Chart chart = Testing.chart();
      new Component(
          chart,
          endpoint.name(),
          ComponentProps.builder()
              .metadata(oauthMetadata(plan, endpoint, namespace))
              .scopes(List.copyOf(endpoint.appIds()))
              .spec(
                  ComponentSpec.builder()
                      .type(OAUTH_MIDDLEWARE_TYPE)
                      .version("v1")
                      .metadata(oauthMetadata(endpoint))
                      .build())
              .build());
      resources.add(toDynamicResource(chart));
    }
    return resources;
  }

  /**
   * One sidecar Configuration per canonical endpoint policy. Kubernetes workloads opt in through
   * {@code dapr.io/config}; Dapr Configuration resources themselves have no scopes field.
   */
  public List<GenericKubernetesResource> oauthConfigurations(
      DeploymentPlan plan, String namespace) {
    List<GenericKubernetesResource> resources = new ArrayList<>(plan.oauthEndpoints().size());
    for (OAuthEndpoint endpoint : plan.oauthEndpoints()) {
      Chart chart = Testing.chart();
      new Configuration(
          chart,
          endpoint.name(),
          ConfigurationProps.builder()
              .metadata(oauthMetadata(plan, endpoint, namespace))
              .spec(
                  ConfigurationSpec.builder()
                      .httpPipeline(
                          ConfigurationSpecHttpPipeline.builder()
                              .handlers(
                                  List.of(
                                      ConfigurationSpecHttpPipelineHandlers.builder()
                                          .name(endpoint.name())
                                          .type(OAUTH_MIDDLEWARE_TYPE)
                                          .build()))
                              .build())
                      .build())
              .build());
      resources.add(toDynamicResource(chart));
    }
    return resources;
  }

  private static ApiObjectMetadata oauthMetadata(
      DeploymentPlan plan, OAuthEndpoint endpoint, String namespace) {
    return ApiObjectMetadata.builder()
        .name(endpoint.name())
        .namespace(namespace)
        .labels(Labels.forPlan(plan))
        .build();
  }

  private static List<ComponentSpecMetadata> oauthMetadata(OAuthEndpoint endpoint) {
    OAuthMiddleware middleware = endpoint.middleware();
    return List.of(
        secretMetadata("clientId", middleware.clientId()),
        secretMetadata("clientSecret", middleware.clientSecret()),
        valueMetadata("scopes", String.join(",", middleware.scopes())),
        valueMetadata("tokenURL", middleware.tokenUrl()),
        valueMetadata("headerName", "authorization"),
        valueMetadata("authStyle", authStyle(middleware.clientAuthentication())),
        valueMetadata("pathFilter", pathFilter(endpoint)));
  }

  private static ComponentSpecMetadata secretMetadata(String name, EnvValue.SecretKeyRef secret) {
    return ComponentSpecMetadata.builder()
        .name(name)
        .secretKeyRef(
            ComponentSpecMetadataSecretKeyRef.builder()
                .name(secret.name())
                .key(secret.key())
                .build())
        .build();
  }

  private static ComponentSpecMetadata valueMetadata(String name, String value) {
    return ComponentSpecMetadata.builder().name(name).value(value).build();
  }

  private static String authStyle(String clientAuthentication) {
    return switch (clientAuthentication) {
      case "client_secret_post" -> "1";
      case "client_secret_basic" -> "2";
      default ->
          throw new IllegalArgumentException(
              "Unsupported OAuth client authentication style: " + clientAuthentication);
    };
  }

  private static String pathFilter(OAuthEndpoint endpoint) {
    String paths =
        endpoint.paths().stream()
            .sorted()
            .map(StackSynthesizer::regexEscape)
            .reduce((a, b) -> a + "|" + b)
            .orElse("(?!)");
    return "^/v1\\.0/invoke/" + regexEscape(endpoint.name()) + "/method(?:" + paths + ")$";
  }

  private static String regexEscape(String value) {
    return Pattern.compile("([\\\\.\\[\\]{}()*+?^$|])").matcher(value).replaceAll("\\\\$1");
  }

  /**
   * One {@code WorkflowAccessPolicy} per activity-invoked step, scoped to the step's Dapr app-id
   * and allowing this workflow's orchestrator app-id to schedule the canonical {@code Run} activity
   * on it. This is the allow-list Dapr's multi-app workflow feature enforces on cross-app activity
   * scheduling. {@code call: openapi} steps are HTTP-invoked (not activity workers), so they get no
   * policy. Self-calls are always permitted, so only the cross-app caller — the orchestrator — is
   * listed.
   */
  public List<GenericKubernetesResource> workflowAccessPolicies(
      DeploymentPlan plan, String namespace) {
    List<GenericKubernetesResource> policies = new ArrayList<>();
    for (StepService step : plan.steps()) {
      if (isActivityInvoked(step.kind())) {
        policies.add(workflowAccessPolicy(plan, step, namespace));
      }
    }
    return policies;
  }

  private GenericKubernetesResource workflowAccessPolicy(
      DeploymentPlan plan, StepService step, String namespace) {
    Chart chart = Testing.chart();
    String name = step.name() + "-wap";
    new WorkflowAccessPolicy(
        chart,
        name,
        WorkflowAccessPolicyProps.builder()
            .metadata(
                ApiObjectMetadata.builder()
                    .name(name)
                    .namespace(namespace)
                    .labels(Labels.forPlan(plan))
                    .build())
            .scopes(List.of(step.name()))
            .spec(
                WorkflowAccessPolicySpec.builder()
                    .rules(
                        List.of(
                            WorkflowAccessPolicySpecRules.builder()
                                .callers(
                                    List.of(
                                        WorkflowAccessPolicySpecRulesCallers.builder()
                                            .appId(plan.orchestrator().appId())
                                            .build()))
                                .activities(
                                    List.of(
                                        WorkflowAccessPolicySpecRulesActivities.builder()
                                            .name(RUN_ACTIVITY)
                                            .build()))
                                .build()))
                    .build())
            .build());
    return toDynamicResource(chart);
  }

  private GenericKubernetesResource knativeService(
      DeploymentPlan plan, StepService step, String namespace) {
    Chart chart = Testing.chart();
    Map<String, String> stepLabels = new LinkedHashMap<>(Labels.forPlan(plan));
    stepLabels.put(Labels.STEP_TYPE, Labels.stepType(step.kind()));
    new Service(
        chart,
        step.name(),
        ServiceProps.builder()
            .metadata(
                ApiObjectMetadata.builder()
                    .name(step.name())
                    .namespace(namespace)
                    .labels(stepLabels)
                    .build())
            .spec(
                ServiceSpec.builder()
                    .template(
                        ServiceSpecTemplate.builder()
                            .metadata(
                                ServiceSpecTemplateMetadata.builder()
                                    .labels(stepLabels)
                                    .annotations(stepAnnotations(plan, step))
                                    .build())
                            .spec(
                                ServiceSpecTemplateSpec.builder()
                                    .containers(
                                        List.of(
                                            ServiceSpecTemplateSpecContainers.builder()
                                                .image(step.image())
                                                .env(knativeEnv(step.env()))
                                                .ports(
                                                    List.of(
                                                        ServiceSpecTemplateSpecContainersPorts
                                                            .builder()
                                                            .containerPort(CONTAINER_PORT)
                                                            .build()))
                                                .build()))
                                    .build())
                            .build())
                    .build())
            .build());
    return toDynamicResource(chart);
  }

  private static Map<String, String> stepAnnotations(DeploymentPlan plan, StepService step) {
    Map<String, String> annotations = new LinkedHashMap<>();
    annotations.put("autoscaling.knative.dev/min-scale", minScale(step.kind()));
    annotations.put("dapr.io/enabled", "true");
    annotations.put("dapr.io/app-id", step.name());
    annotations.put("dapr.io/app-port", CONTAINER_PORT_VALUE);
    plan.oauthEndpoints().stream()
        .filter(endpoint -> endpoint.appIds().contains(step.name()))
        .map(OAuthEndpoint::name)
        .findFirst()
        .ifPresent(configuration -> annotations.put("dapr.io/config", configuration));
    return annotations;
  }

  /**
   * Activity-invoked steps ({@code CALL_HTTP}/{@code RUN_*}) must stay live to receive dispatched
   * work; {@code CALL_OPENAPI} remains HTTP-triggered and may scale to zero.
   */
  private static String minScale(TaskKind kind) {
    return isActivityInvoked(kind) ? "1" : "0";
  }

  /**
   * True for steps the orchestrator invokes as a multi-app Dapr Workflow activity — every kind but
   * {@code CALL_OPENAPI}, whose Node image stays on HTTP service invocation. The single source of
   * truth for the activity-vs-HTTP split, shared by {@link #minScale} and the access-policy synth.
   */
  private static boolean isActivityInvoked(TaskKind kind) {
    return kind != TaskKind.CALL_OPENAPI;
  }

  private static List<ServiceSpecTemplateSpecContainersEnv> knativeEnv(Map<String, EnvValue> env) {
    List<ServiceSpecTemplateSpecContainersEnv> vars = new ArrayList<>(env.size());
    env.forEach((name, value) -> vars.add(knativeEnv(name, value)));
    return vars;
  }

  private static ServiceSpecTemplateSpecContainersEnv knativeEnv(String name, EnvValue value) {
    var builder = ServiceSpecTemplateSpecContainersEnv.builder().name(name);
    if (value instanceof EnvValue.Literal literal) {
      return builder.value(literal.value()).build();
    }
    EnvValue.SecretKeyRef secret = (EnvValue.SecretKeyRef) value;
    return builder
        .valueFrom(
            ServiceSpecTemplateSpecContainersEnvValueFrom.builder()
                .secretKeyRef(
                    ServiceSpecTemplateSpecContainersEnvValueFromSecretKeyRef.builder()
                        .name(secret.name())
                        .key(secret.key())
                        .build())
                .build())
        .build();
  }

  /** The dedicated orchestrator Deployment for this workflow version. */
  public Deployment orchestratorDeployment(DeploymentPlan plan, String namespace) {
    OrchestratorSpec orchestrator = plan.orchestrator();
    Map<String, String> labels = Labels.forPlan(plan);
    Map<String, String> selector = Map.of("app", orchestrator.name());
    Map<String, String> podLabels = new LinkedHashMap<>(labels);
    podLabels.putAll(selector);

    return new DeploymentBuilder()
        .withNewMetadata()
        .withName(orchestrator.name())
        .withNamespace(namespace)
        .withLabels(labels)
        .endMetadata()
        .withNewSpec()
        .withReplicas(orchestrator.replicas())
        .withNewSelector()
        .withMatchLabels(selector)
        .endSelector()
        .withNewTemplate()
        .withNewMetadata()
        .withLabels(podLabels)
        .withAnnotations(orchestratorAnnotations(orchestrator))
        .endMetadata()
        .withNewSpec()
        .addNewContainer()
        .withName("orchestrator")
        .withImage(orchestrator.image())
        .withEnv(envVars(orchestrator.env()))
        .addNewPort()
        .withContainerPort(orchestrator.appPort())
        .endPort()
        .endContainer()
        .endSpec()
        .endTemplate()
        .endSpec()
        .build();
  }

  private static Map<String, String> orchestratorAnnotations(OrchestratorSpec orchestrator) {
    Map<String, String> annotations = new LinkedHashMap<>();
    annotations.put("dapr.io/enabled", "true");
    annotations.put("dapr.io/app-id", orchestrator.appId());
    annotations.put("dapr.io/app-port", String.valueOf(orchestrator.appPort()));
    return annotations;
  }

  private static List<EnvVar> envVars(Map<String, EnvValue> env) {
    List<EnvVar> vars = new ArrayList<>(env.size());
    env.forEach((name, value) -> vars.add(envVar(name, value)));
    return vars;
  }

  private static EnvVar envVar(String name, EnvValue value) {
    if (value instanceof EnvValue.Literal literal) {
      return new EnvVarBuilder().withName(name).withValue(literal.value()).build();
    }
    EnvValue.SecretKeyRef secret = (EnvValue.SecretKeyRef) value;
    return new EnvVarBuilder()
        .withName(name)
        .withNewValueFrom()
        .withNewSecretKeyRef(secret.key(), secret.name(), null)
        .endValueFrom()
        .build();
  }

  private static GenericKubernetesResource toDynamicResource(Chart chart) {
    Object manifest = Testing.synth(chart).get(0);
    return Serialization.jsonMapper().convertValue(manifest, GenericKubernetesResource.class);
  }
}
