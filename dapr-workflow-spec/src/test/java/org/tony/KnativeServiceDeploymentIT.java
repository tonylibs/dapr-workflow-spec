package org.tony;

import imports.dev.knative.serving.Service;
import imports.dev.knative.serving.ServiceProps;
import imports.dev.knative.serving.ServiceSpec;
import imports.dev.knative.serving.ServiceSpecTemplate;
import imports.dev.knative.serving.ServiceSpecTemplateMetadata;
import imports.dev.knative.serving.ServiceSpecTemplateSpec;
import imports.dev.knative.serving.ServiceSpecTemplateSpecContainers;
import imports.dev.knative.serving.ServiceSpecTemplateSpecContainersPorts;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.kubernetes.client.readiness.Readiness;
import org.cdk8s.ApiObjectMetadata;
import org.cdk8s.App;
import org.cdk8s.Chart;
import org.cdk8s.Testing;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.tony.k8s.DynamicResourceClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Applies a Knative Service built with cdk8s to a real cluster and verifies the
 * revision actually reconciles: Ready condition true and a ready pod backing it.
 * Requires a cluster with Knative Serving installed on the current kube context.
 * Not run by {@code mvn test}; opt in with {@code mvn verify -DskipITs=false}.
 */
class KnativeServiceDeploymentIT {

    private static final String NAMESPACE = "cdk8s-it-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String SERVICE_NAME = "hello-cdk8s";
    private static final ResourceDefinitionContext KNATIVE_SERVICE_CONTEXT = new ResourceDefinitionContext.Builder()
            .withGroup("serving.knative.dev")
            .withVersion("v1")
            .withKind("Service")
            .withNamespaced(true)
            .build();

    private static KubernetesClient client;
    private static DynamicResourceClient resources;

    @BeforeAll
    static void setUp() {
        client = new KubernetesClientBuilder().build();
        resources = new DynamicResourceClient(client);
        client.namespaces()
                .resource(new NamespaceBuilder().withNewMetadata().withName(NAMESPACE).endMetadata().build())
                .create();
    }

    @AfterAll
    static void tearDown() {
        if (client == null) {
            return;
        }
        client.namespaces().withName(NAMESPACE).delete();
        client.close();
    }

    @Test
    void appliedKnativeServiceBecomesReadyWithARunningPod() {
        Object manifest = synthesizeKnativeServiceManifest();

        resources.apply(KNATIVE_SERVICE_CONTEXT, NAMESPACE, manifest);

        GenericKubernetesResource deployed = resources.waitUntilConditionTrue(
                KNATIVE_SERVICE_CONTEXT, NAMESPACE, SERVICE_NAME, "Ready", 3, TimeUnit.MINUTES);

        assertNotNull(deployed, "Knative Service " + SERVICE_NAME + " never reported Ready within timeout");

        List<Pod> pods = client.pods()
                .inNamespace(NAMESPACE)
                .withLabel("serving.knative.dev/service", SERVICE_NAME)
                .list()
                .getItems();

        assertFalse(pods.isEmpty(), "expected at least one pod backing the deployed revision");
        assertTrue(pods.stream().anyMatch(Readiness::isPodReady),
                "expected a ready pod backing the deployed Knative Service, found: " + pods);
    }

    private static Object synthesizeKnativeServiceManifest() {
        App app = Testing.app();
        Chart chart = new Chart(app, "cdk8s-it");

        new Service(chart, SERVICE_NAME, ServiceProps.builder()
                .metadata(ApiObjectMetadata.builder()
                        .name(SERVICE_NAME)
                        .build())
                .spec(ServiceSpec.builder()
                        .template(ServiceSpecTemplate.builder()
                                .metadata(ServiceSpecTemplateMetadata.builder()
                                        // force at least one pod so the test doesn't race scale-to-zero
                                        .annotations(Map.of("autoscaling.knative.dev/min-scale", "1"))
                                        .build())
                                .spec(ServiceSpecTemplateSpec.builder()
                                        .containers(List.of(ServiceSpecTemplateSpecContainers.builder()
                                                .image("docker.io/hashicorp/http-echo:latest")
                                                .args(List.of("-text=hello-from-cdk8s", "-listen=:8080"))
                                                .ports(List.of(ServiceSpecTemplateSpecContainersPorts.builder()
                                                        .containerPort(8080)
                                                        .build()))
                                                .build()))
                                        .build())
                                .build())
                        .build())
                .build());

        return Testing.synth(chart).get(0);
    }
}
