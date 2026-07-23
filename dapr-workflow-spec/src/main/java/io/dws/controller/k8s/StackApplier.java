package io.dws.controller.k8s;

import io.dws.controller.config.DwsConfig;
import io.dws.controller.model.ApplyResult;
import io.dws.controller.model.DeploymentPlan;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * Apply pass: materializes a {@link DeploymentPlan} in the cluster, drives the rollout of a new
 * version over the previous one, and garbage-collects drained versions. All mutation is keyed on
 * the {@code dws.io/*} labels, so the cluster stays the single source of truth.
 */
@ApplicationScoped
public class StackApplier {

    private static final Logger LOG = Logger.getLogger(StackApplier.class);

    private final KubernetesClient client;
    private final StackSynthesizer synthesizer;
    private final String namespace;

    public StackApplier(KubernetesClient client, StackSynthesizer synthesizer, DwsConfig config) {
        this.client = client;
        this.synthesizer = synthesizer;
        this.namespace = config.namespace();
    }

    /**
     * Deploys the plan. The definition ConfigMap is immutable, so it is only created when absent —
     * re-posting identical content resolves to the same version and changes nothing.
     */
    public ApplyResult apply(DeploymentPlan plan) {
        boolean alreadyDeployed = client.configMaps()
                        .inNamespace(namespace)
                        .withName(plan.definitionResource())
                        .get()
                != null;

        if (!alreadyDeployed) {
            client.resource(synthesizer.definitionConfigMap(plan, namespace))
                    .inNamespace(namespace)
                    .create();
        }

        applyDynamic(ResourceContexts.DAPR_COMPONENT, synthesizer.configurationComponent(plan, namespace));
        for (GenericKubernetesResource service : synthesizer.knativeServices(plan, namespace)) {
            applyDynamic(ResourceContexts.KNATIVE_SERVICE, service);
        }
        client.resource(synthesizer.orchestratorDeployment(plan, namespace))
                .inNamespace(namespace)
                .createOr(NonDeletingOperation::update);

        rollOut(plan);

        LOG.infof("Applied workflow %s version %s (created=%s)", plan.workflow(), plan.version(), !alreadyDeployed);
        return new ApplyResult(plan.workflow(), plan.versionId(), plan.version(), !alreadyDeployed);
    }

    /** Marks superseded versions for drain, then collects any that already report zero replicas. */
    private void rollOut(DeploymentPlan plan) {
        for (Deployment previous : orchestrators(Labels.workflow(plan.workflow()))) {
            String versionId = versionOf(previous);
            if (versionId == null || versionId.equals(plan.versionId())) {
                continue;
            }
            markForDrain(previous);
            collectIfDrained(plan.workflow(), previous, versionId);
        }
    }

    /**
     * Periodic reconcile so drained versions are collected without waiting for the next POST.
     * Drain handling itself belongs to the orchestrator; the controller only observes the result.
     */
    public void reconcile() {
        for (Deployment deployment : orchestrators(Labels.managed())) {
            String workflow = deployment.getMetadata().getLabels().get(Labels.WORKFLOW);
            String versionId = versionOf(deployment);
            if (workflow != null && versionId != null && isDraining(deployment)) {
                collectIfDrained(workflow, deployment, versionId);
            }
        }
    }

    private void collectIfDrained(String workflow, Deployment deployment, String versionId) {
        if (!hasZeroReplicas(deployment)) {
            return;
        }
        LOG.infof("Garbage-collecting drained version %s of workflow %s", versionId, workflow);
        deleteByLabels(Labels.version(workflow, versionId));
    }

    private void markForDrain(Deployment deployment) {
        if (isDraining(deployment)) {
            return;
        }
        client.apps()
                .deployments()
                .inNamespace(namespace)
                .withName(deployment.getMetadata().getName())
                .edit(current -> new DeploymentBuilder(current)
                        .editMetadata()
                        .addToAnnotations(Labels.DRAIN, "true")
                        .endMetadata()
                        .build());
    }

    /** Removes every managed resource belonging to a workflow. */
    public boolean deleteWorkflow(String workflow) {
        boolean existed = !orchestrators(Labels.workflow(workflow)).isEmpty()
                || !client.configMaps()
                        .inNamespace(namespace)
                        .withLabels(Labels.workflow(workflow))
                        .list()
                        .getItems()
                        .isEmpty();
        deleteByLabels(Labels.workflow(workflow));
        return existed;
    }

    private void deleteByLabels(Map<String, String> selector) {
        client.configMaps().inNamespace(namespace).withLabels(selector).delete();
        client.apps().deployments().inNamespace(namespace).withLabels(selector).delete();
        deleteDynamic(ResourceContexts.KNATIVE_SERVICE, selector);
        deleteDynamic(ResourceContexts.DAPR_COMPONENT, selector);
    }

    private void deleteDynamic(ResourceDefinitionContext context, Map<String, String> selector) {
        client.genericKubernetesResources(context)
                .inNamespace(namespace)
                .withLabels(selector)
                .delete();
    }

    private void applyDynamic(ResourceDefinitionContext context, GenericKubernetesResource resource) {
        client.genericKubernetesResources(context)
                .inNamespace(namespace)
                .resource(resource)
                .createOr(NonDeletingOperation::update);
    }

    private List<Deployment> orchestrators(Map<String, String> selector) {
        return client.apps()
                .deployments()
                .inNamespace(namespace)
                .withLabels(selector)
                .list()
                .getItems();
    }

    static String versionOf(Deployment deployment) {
        Map<String, String> labels = deployment.getMetadata().getLabels();
        return labels == null ? null : labels.get(Labels.VERSION);
    }

    static boolean isDraining(Deployment deployment) {
        Map<String, String> annotations = deployment.getMetadata().getAnnotations();
        return annotations != null && "true".equals(annotations.get(Labels.DRAIN));
    }

    /** A Deployment that has not reported status yet has not drained — only an explicit zero counts. */
    private static boolean hasZeroReplicas(Deployment deployment) {
        return deployment.getStatus() != null
                && deployment.getStatus().getReplicas() != null
                && deployment.getStatus().getReplicas() == 0;
    }
}
