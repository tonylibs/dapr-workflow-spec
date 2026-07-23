package org.tony.k8s;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.kubernetes.client.utils.Serialization;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Applies cdk8s-synthesized manifests to a cluster and waits on their status conditions
 * via fabric8's dynamic/generic client, so no per-CRD Java model is required (Knative, Dapr, ...).
 */
public class DynamicResourceClient {

    private final KubernetesClient client;

    public DynamicResourceClient(KubernetesClient client) {
        this.client = client;
    }

    public GenericKubernetesResource apply(ResourceDefinitionContext context, String namespace, Object cdk8sManifest) {
        GenericKubernetesResource resource = Serialization.jsonMapper().convertValue(cdk8sManifest, GenericKubernetesResource.class);
        return client.genericKubernetesResources(context)
                .inNamespace(namespace)
                .resource(resource)
                .create();
    }

    public GenericKubernetesResource get(ResourceDefinitionContext context, String namespace, String name) {
        return client.genericKubernetesResources(context)
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    public GenericKubernetesResource waitUntilConditionTrue(ResourceDefinitionContext context, String namespace, String name,
            String conditionType, long timeout, TimeUnit unit) {
        return client.genericKubernetesResources(context)
                .inNamespace(namespace)
                .withName(name)
                .waitUntilCondition(resource -> hasConditionTrue(resource, conditionType), timeout, unit);
    }

    public boolean delete(ResourceDefinitionContext context, String namespace, String name) {
        List<io.fabric8.kubernetes.api.model.StatusDetails> result = client.genericKubernetesResources(context)
                .inNamespace(namespace)
                .withName(name)
                .delete();
        return !result.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static boolean hasConditionTrue(GenericKubernetesResource resource, String conditionType) {
        if (resource == null) {
            return false;
        }
        Object status = resource.getAdditionalProperties().get("status");
        if (!(status instanceof Map)) {
            return false;
        }
        Object conditions = ((Map<String, Object>) status).get("conditions");
        if (!(conditions instanceof List)) {
            return false;
        }
        return ((List<Map<String, Object>>) conditions).stream()
                .anyMatch(c -> conditionType.equals(c.get("type")) && "True".equals(c.get("status")));
    }
}
