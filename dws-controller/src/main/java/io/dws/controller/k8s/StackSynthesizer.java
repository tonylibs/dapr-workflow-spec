package io.dws.controller.k8s;

import imports.dev.knative.serving.Service;
import imports.dev.knative.serving.ServiceProps;
import imports.dev.knative.serving.ServiceSpec;
import imports.dev.knative.serving.ServiceSpecTemplate;
import imports.dev.knative.serving.ServiceSpecTemplateMetadata;
import imports.dev.knative.serving.ServiceSpecTemplateSpec;
import imports.dev.knative.serving.ServiceSpecTemplateSpecContainers;
import imports.dev.knative.serving.ServiceSpecTemplateSpecContainersEnv;
import imports.dev.knative.serving.ServiceSpecTemplateSpecContainersPorts;
import imports.io.dapr.Component;
import imports.io.dapr.ComponentProps;
import imports.io.dapr.ComponentSpec;
import imports.io.dapr.ComponentSpecMetadata;
import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.OrchestratorSpec;
import io.dws.controller.model.StepService;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cdk8s.ApiObjectMetadata;
import org.cdk8s.Chart;
import org.cdk8s.Testing;

/**
 * Renders a {@link DeploymentPlan} into concrete Kubernetes objects. Knative Services and
 * Dapr Components are synthesized with cdk8s (using the generated imports) and handed on as
 * dynamic resources; the ConfigMap and orchestrator Deployment use the built-in fabric8 models.
 */
@ApplicationScoped
public class StackSynthesizer {

    static final String DEFINITION_KEY = "definition";
    private static final String CONTAINER_PORT_VALUE = "8080";
    private static final int CONTAINER_PORT = 8080;

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

    /** Dapr configuration-store component backed by the definition ConfigMap, scoped to the workflow app-id. */
    public GenericKubernetesResource configurationComponent(DeploymentPlan plan, String namespace) {
        Chart chart = Testing.chart();
        new Component(
                chart,
                plan.definitionResource(),
                ComponentProps.builder()
                        .metadata(ApiObjectMetadata.builder()
                                .name(plan.definitionResource())
                                .namespace(namespace)
                                .labels(Labels.forPlan(plan))
                                .build())
                        .scopes(List.of(plan.workflow()))
                        .spec(ComponentSpec.builder()
                                .type("configuration.kubernetes")
                                .version("v1")
                                .metadata(List.of(ComponentSpecMetadata.builder()
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

    private GenericKubernetesResource knativeService(DeploymentPlan plan, StepService step, String namespace) {
        Chart chart = Testing.chart();
        new Service(
                chart,
                step.name(),
                ServiceProps.builder()
                        .metadata(ApiObjectMetadata.builder()
                                .name(step.name())
                                .namespace(namespace)
                                .labels(Labels.forPlan(plan))
                                .build())
                        .spec(ServiceSpec.builder()
                                .template(ServiceSpecTemplate.builder()
                                        .metadata(ServiceSpecTemplateMetadata.builder()
                                                .labels(Labels.forPlan(plan))
                                                .annotations(stepAnnotations(step))
                                                .build())
                                        .spec(ServiceSpecTemplateSpec.builder()
                                                .containers(List.of(ServiceSpecTemplateSpecContainers.builder()
                                                        .image(step.image())
                                                        .env(knativeEnv(step.env()))
                                                        .ports(List.of(
                                                                ServiceSpecTemplateSpecContainersPorts.builder()
                                                                        .containerPort(CONTAINER_PORT)
                                                                        .build()))
                                                        .build()))
                                                .build())
                                        .build())
                                .build())
                        .build());
        return toDynamicResource(chart);
    }

    private static Map<String, String> stepAnnotations(StepService step) {
        Map<String, String> annotations = new LinkedHashMap<>();
        annotations.put("autoscaling.knative.dev/min-scale", "0");
        annotations.put("dapr.io/enabled", "true");
        annotations.put("dapr.io/app-id", step.name());
        annotations.put("dapr.io/app-port", CONTAINER_PORT_VALUE);
        return annotations;
    }

    private static List<ServiceSpecTemplateSpecContainersEnv> knativeEnv(Map<String, String> env) {
        List<ServiceSpecTemplateSpecContainersEnv> vars = new ArrayList<>(env.size());
        env.forEach((name, value) -> vars.add(ServiceSpecTemplateSpecContainersEnv.builder()
                .name(name)
                .value(value)
                .build()));
        return vars;
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

    private static List<EnvVar> envVars(Map<String, String> env) {
        List<EnvVar> vars = new ArrayList<>(env.size());
        env.forEach((name, value) -> vars.add(new EnvVar(name, value, null)));
        return vars;
    }

    private static GenericKubernetesResource toDynamicResource(Chart chart) {
        Object manifest = Testing.synth(chart).get(0);
        return Serialization.jsonMapper().convertValue(manifest, GenericKubernetesResource.class);
    }
}
