package io.dws.controller.k8s;

import io.dws.controller.config.DwsConfig;
import io.dws.controller.events.EventPublisher;
import io.dws.controller.model.ApplyResult;
import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.StepService;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
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

  /** Dapr app-id annotation on the orchestrator pod template; equals the workflow name. */
  private static final String DAPR_APP_ID = "dapr.io/app-id";

  private final KubernetesClient client;
  private final StackSynthesizer synthesizer;
  private final EventPublisher events;
  private final String namespace;

  public StackApplier(
      KubernetesClient client,
      StackSynthesizer synthesizer,
      EventPublisher events,
      DwsConfig config) {
    this.client = client;
    this.synthesizer = synthesizer;
    this.events = events;
    this.namespace = config.namespace();
  }

  /**
   * Deploys the plan. The definition ConfigMap is immutable, so it is only created when absent —
   * re-posting identical content resolves to the same version and changes nothing.
   */
  public ApplyResult apply(DeploymentPlan plan) {
    try {
      boolean alreadyDeployed =
          client.configMaps().inNamespace(namespace).withName(plan.definitionResource()).get()
              != null;

      if (!alreadyDeployed) {
        client
            .resource(synthesizer.definitionConfigMap(plan, namespace))
            .inNamespace(namespace)
            .create();
      }
      String createdAt = Instant.now().toString();
      if (alreadyDeployed) {
        events.definitionUpdated(plan.workflow(), plan.versionId(), createdAt);
      } else {
        events.definitionCreated(plan.workflow(), plan.versionId(), createdAt);
      }

      applyDynamic(
          ResourceContexts.DAPR_COMPONENT, synthesizer.configurationComponent(plan, namespace));
      for (GenericKubernetesResource endpoint : synthesizer.oauthHttpEndpoints(plan, namespace)) {
        applyDynamic(ResourceContexts.DAPR_HTTP_ENDPOINT, endpoint);
      }
      for (GenericKubernetesResource middleware :
          synthesizer.oauthMiddlewareComponents(plan, namespace)) {
        applyDynamic(ResourceContexts.DAPR_COMPONENT, middleware);
      }
      for (GenericKubernetesResource configuration :
          synthesizer.oauthConfigurations(plan, namespace)) {
        applyDynamic(ResourceContexts.DAPR_CONFIGURATION, configuration);
      }
      for (GenericKubernetesResource service : synthesizer.knativeServices(plan, namespace)) {
        applyDynamic(ResourceContexts.KNATIVE_SERVICE, service);
      }
      for (GenericKubernetesResource policy : synthesizer.workflowAccessPolicies(plan, namespace)) {
        applyDynamic(ResourceContexts.WORKFLOW_ACCESS_POLICY, policy);
      }
      client
          .resource(synthesizer.orchestratorDeployment(plan, namespace))
          .inNamespace(namespace)
          .createOr(NonDeletingOperation::update);

      rollOut(plan);

      LOG.infof(
          "Applied workflow %s version %s (created=%s)",
          plan.workflow(), plan.version(), !alreadyDeployed);
      events.deploymentApplied(
          plan.workflow(), plan.versionId(), stepNames(plan), plan.orchestrator().appId());
      return new ApplyResult(plan.workflow(), plan.versionId(), plan.version(), !alreadyDeployed);
    } catch (RuntimeException e) {
      events.deploymentFailed(
          plan.workflow(),
          plan.versionId(),
          stepNames(plan),
          plan.orchestrator().appId(),
          e.getMessage());
      throw e;
    }
  }

  private static List<String> stepNames(DeploymentPlan plan) {
    return plan.steps().stream().map(StepService::name).toList();
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
   * Periodic reconcile so drained versions are collected without waiting for the next POST. Drain
   * handling itself belongs to the orchestrator; the controller only observes the result.
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
    events.deploymentCollected(workflow, versionId, orchestratorAppId(deployment, workflow));
  }

  private void markForDrain(Deployment deployment) {
    if (isDraining(deployment)) {
      return;
    }
    client
        .apps()
        .deployments()
        .inNamespace(namespace)
        .withName(deployment.getMetadata().getName())
        .edit(
            current ->
                new DeploymentBuilder(current)
                    .editMetadata()
                    .addToAnnotations(Labels.DRAIN, "true")
                    .endMetadata()
                    .build());
    String workflow = deployment.getMetadata().getLabels().get(Labels.WORKFLOW);
    events.deploymentDrained(
        workflow, versionOf(deployment), orchestratorAppId(deployment, workflow));
  }

  /** Orchestrator app-id from the pod-template annotation, falling back to the workflow name. */
  private static String orchestratorAppId(Deployment deployment, String workflow) {
    var template = deployment.getSpec() == null ? null : deployment.getSpec().getTemplate();
    var meta = template == null ? null : template.getMetadata();
    Map<String, String> annotations = meta == null ? null : meta.getAnnotations();
    String appId = annotations == null ? null : annotations.get(DAPR_APP_ID);
    return appId != null ? appId : workflow;
  }

  /** Removes every managed resource belonging to a workflow. */
  public boolean deleteWorkflow(String workflow) {
    boolean existed =
        !orchestrators(Labels.workflow(workflow)).isEmpty()
            || !client
                .configMaps()
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
    deleteDynamic(ResourceContexts.WORKFLOW_ACCESS_POLICY, selector);
    deleteDynamic(ResourceContexts.DAPR_COMPONENT, selector);
    deleteDynamic(ResourceContexts.DAPR_HTTP_ENDPOINT, selector);
    deleteDynamic(ResourceContexts.DAPR_CONFIGURATION, selector);
  }

  private void deleteDynamic(ResourceDefinitionContext context, Map<String, String> selector) {
    client.genericKubernetesResources(context).inNamespace(namespace).withLabels(selector).delete();
  }

  private void applyDynamic(ResourceDefinitionContext context, GenericKubernetesResource resource) {
    client
        .genericKubernetesResources(context)
        .inNamespace(namespace)
        .resource(resource)
        .createOr(NonDeletingOperation::update);
  }

  private List<Deployment> orchestrators(Map<String, String> selector) {
    return client
        .apps()
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

  /**
   * A Deployment that has not reported status yet has not drained — only an explicit zero counts.
   */
  private static boolean hasZeroReplicas(Deployment deployment) {
    return deployment.getStatus() != null
        && deployment.getStatus().getReplicas() != null
        && deployment.getStatus().getReplicas() == 0;
  }
}
