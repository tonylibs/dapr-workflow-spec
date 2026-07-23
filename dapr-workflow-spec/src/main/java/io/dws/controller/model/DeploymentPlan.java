package io.dws.controller.model;

import java.util.List;

/**
 * The full, computed stack for one workflow definition version — the output of the
 * pure compile pass and the input to the apply pass. Serializable as-is for the
 * {@code /plan} dry-run endpoint.
 *
 * @param workflow            kebab-cased workflow name ({@code W}); used in every resource name/label
 * @param versionId           {@code v<sha256-8>} of the normalized spec; stable across reruns
 * @param version             public version string {@code <name>@v<sha256-8>}
 * @param definitionResource  name of the immutable definition ConfigMap and Dapr Component ({@code dws-def-W-vN})
 * @param specText            the definition text stored verbatim in the ConfigMap
 * @param steps               one entry per deployable I/O task
 * @param bindings            pub/sub topic bindings from emit/listen tasks
 * @param orchestrator        the dedicated orchestrator Deployment for this version
 */
public record DeploymentPlan(
        String workflow,
        String versionId,
        String version,
        String definitionResource,
        String specText,
        List<StepService> steps,
        List<TopicBinding> bindings,
        OrchestratorSpec orchestrator) {

    public DeploymentPlan {
        steps = List.copyOf(steps);
        bindings = List.copyOf(bindings);
    }
}
