package io.dws.controller.k8s;

import io.dws.controller.compile.Names;
import io.dws.controller.compile.WorkflowCompiler;
import io.dws.controller.config.DwsConfig;
import io.dws.controller.model.StackStatus;
import io.dws.controller.model.WorkflowDetail;
import io.dws.controller.model.WorkflowSummary;
import io.dws.controller.model.WorkflowVersionStatus;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read model for the GET endpoints. Every answer comes from live cluster state selected by the
 * {@code dws.io/*} labels — the controller keeps no database of its own.
 */
@ApplicationScoped
public class StackReader {

    private final KubernetesClient client;
    private final String namespace;

    public StackReader(KubernetesClient client, DwsConfig config) {
        this.client = client;
        this.namespace = config.namespace();
    }

    public List<WorkflowSummary> list() {
        List<WorkflowSummary> summaries = new ArrayList<>();
        byWorkflow(orchestrators(Labels.managed())).forEach((workflow, deployments) -> current(deployments)
                .ifPresent(currentDeployment -> summaries.add(new WorkflowSummary(
                        workflow,
                        WorkflowCompiler.version(workflow, StackApplier.versionOf(currentDeployment)),
                        statusOf(currentDeployment)))));
        summaries.sort(Comparator.comparing(WorkflowSummary::name));
        return summaries;
    }

    public Optional<WorkflowDetail> get(String workflow) {
        List<Deployment> deployments = orchestrators(Labels.workflow(workflow));
        if (deployments.isEmpty()) {
            return Optional.empty();
        }
        Deployment currentDeployment = current(deployments).orElse(deployments.get(0));
        String versionId = StackApplier.versionOf(currentDeployment);

        List<WorkflowVersionStatus> versions = new ArrayList<>();
        for (Deployment deployment : deployments) {
            versions.add(toVersionStatus(workflow, deployment));
        }
        versions.sort(Comparator.comparing(WorkflowVersionStatus::versionId));

        return Optional.of(new WorkflowDetail(
                workflow,
                WorkflowCompiler.version(workflow, versionId),
                statusOf(currentDeployment),
                Names.definitionResource(workflow, versionId),
                stepNames(workflow, versionId),
                versions));
    }

    /** The definition text of the workflow's current version, straight from its ConfigMap. */
    public Optional<String> definitionText(String workflow) {
        return get(workflow)
                .map(WorkflowDetail::definitionResource)
                .map(name -> client.configMaps()
                        .inNamespace(namespace)
                        .withName(name)
                        .get())
                .filter(configMap -> configMap.getData() != null)
                .map(configMap -> configMap.getData().get(StackSynthesizer.DEFINITION_KEY));
    }

    private List<String> stepNames(String workflow, String versionId) {
        List<String> names = new ArrayList<>();
        for (GenericKubernetesResource service : client.genericKubernetesResources(ResourceContexts.KNATIVE_SERVICE)
                .inNamespace(namespace)
                .withLabels(Labels.version(workflow, versionId))
                .list()
                .getItems()) {
            names.add(service.getMetadata().getName());
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }

    private WorkflowVersionStatus toVersionStatus(String workflow, Deployment deployment) {
        String versionId = StackApplier.versionOf(deployment);
        return new WorkflowVersionStatus(
                versionId,
                WorkflowCompiler.version(workflow, versionId),
                statusOf(deployment),
                replicas(deployment),
                readyReplicas(deployment),
                StackApplier.isDraining(deployment));
    }

    private static Optional<Deployment> current(List<Deployment> deployments) {
        return deployments.stream().filter(d -> !StackApplier.isDraining(d)).findFirst();
    }

    private static Map<String, List<Deployment>> byWorkflow(List<Deployment> deployments) {
        Map<String, List<Deployment>> grouped = new LinkedHashMap<>();
        for (Deployment deployment : deployments) {
            String workflow = deployment.getMetadata().getLabels().get(Labels.WORKFLOW);
            if (workflow != null) {
                grouped.computeIfAbsent(workflow, k -> new ArrayList<>()).add(deployment);
            }
        }
        return grouped;
    }

    private static StackStatus statusOf(Deployment deployment) {
        if (StackApplier.isDraining(deployment)) {
            return StackStatus.DRAINING;
        }
        return readyReplicas(deployment) >= 1 ? StackStatus.READY : StackStatus.PENDING;
    }

    private static int replicas(Deployment deployment) {
        if (deployment.getStatus() == null || deployment.getStatus().getReplicas() == null) {
            return 0;
        }
        return deployment.getStatus().getReplicas();
    }

    private static int readyReplicas(Deployment deployment) {
        if (deployment.getStatus() == null || deployment.getStatus().getReadyReplicas() == null) {
            return 0;
        }
        return deployment.getStatus().getReadyReplicas();
    }

    private List<Deployment> orchestrators(Map<String, String> selector) {
        return client.apps()
                .deployments()
                .inNamespace(namespace)
                .withLabels(selector)
                .list()
                .getItems();
    }
}
