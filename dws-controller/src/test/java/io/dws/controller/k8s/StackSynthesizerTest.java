package io.dws.controller.k8s;

import static org.assertj.core.api.Assertions.assertThat;

import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.OrchestratorSpec;
import io.dws.controller.model.StepService;
import io.dws.controller.model.TaskKind;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
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

  private final StackSynthesizer synthesizer = new StackSynthesizer();

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
}
