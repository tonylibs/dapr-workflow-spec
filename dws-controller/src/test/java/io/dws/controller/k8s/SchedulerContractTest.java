package io.dws.controller.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.dws.controller.compile.V1OrchestratorCompiler;
import io.dws.controller.compile.WorkflowCompiler;
import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.ImageCatalog;
import io.dws.controller.model.TriggerKind;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Cross-package contract guard between the controller and {@code dws-schedule-time} (scheduling
 * Phase 6a, root CLAUDE.md "contract-change etiquette"). The scheduler finds trigger ConfigMaps by
 * label, decides the active version by the drain annotation, and parses {@code trigger.json} — none
 * of which is a compile-time dependency, so drift surfaces only as a schedule that silently never
 * fires. This pins the names and checks the controller's output against the scheduler's parser
 * fixtures.
 *
 * <p>Regenerate the fixtures after an intended {@code trigger.json} change with {@code ./mvnw test
 * -Dtest=SchedulerContractTest -Ddws.writeTriggerFixtures=true}, then update the scheduler's
 * parser.
 */
class SchedulerContractTest {

  private static final Path FIXTURES = Path.of("..", "dws-schedule-time", "test", "fixtures");

  private static final ImageCatalog IMAGES =
      new ImageCatalog("a:1", "b:1", "c:1", "d:1", "e:1", "f:1", "g:1", "h:1", "orchestrator:1");

  private final WorkflowCompiler compiler =
      new V1OrchestratorCompiler(IMAGES, ignored -> "x".getBytes(StandardCharsets.UTF_8));
  private final StackSynthesizer synthesizer = new StackSynthesizer();

  private static String definition(String name, String schedule) {
    return """
        document:
          dsl: '1.0.0'
          namespace: test
          name: %s
          version: '1.0.0'
        %s
        do:
          - noop:
              set:
                done: true
        """
        .formatted(name, schedule);
  }

  /** One sample definition per trigger kind; their rendered triggers are the shared fixtures. */
  private static String sample(TriggerKind kind) {
    return switch (kind) {
      case EVERY -> definition("order-sync", "schedule: { every: { hours: 1, minutes: 30 } }");
      case CRON -> definition("nightly-report", "schedule: { cron: \"0 2 * * 1-5\" }");
      case AFTER -> definition("queue-drainer", "schedule: { after: PT30S }");
    };
  }

  @Test
  @DisplayName("the label and annotation names the scheduler selects on are pinned")
  void pinsSchedulerFacingNames() {
    assertThat(Labels.TRIGGER).isEqualTo("dws.io/trigger");
    assertThat(Labels.TRIGGER_TIME).isEqualTo("time");
    assertThat(Labels.DRAIN).isEqualTo("dws.io/drain");
    assertThat(Labels.WORKFLOW).isEqualTo("dws.io/workflow");
    assertThat(Labels.VERSION).isEqualTo("dws.io/version");
    assertThat(StackSynthesizer.TRIGGER_KEY).isEqualTo("trigger.json");
  }

  @Test
  @DisplayName("a draining version is marked by the dws.io/drain annotation, not a label")
  void drainIsAnAnnotation() {
    Deployment annotated =
        new DeploymentBuilder()
            .withNewMetadata()
            .withName("o")
            .addToAnnotations("dws.io/drain", "true")
            .endMetadata()
            .build();
    Deployment labeled =
        new DeploymentBuilder()
            .withNewMetadata()
            .withName("o")
            .addToLabels("dws.io/drain", "true")
            .endMetadata()
            .build();

    assertThat(StackApplier.isDraining(annotated)).isTrue();
    assertThat(StackApplier.isDraining(labeled)).isFalse();
  }

  @Test
  @DisplayName("the version ID of an unscheduled definition is unchanged by scheduling support")
  void unscheduledVersionIdIsUnchanged() {
    DeploymentPlan plan = compiler.compile(definition("order-sync", ""));

    // Pinned from the controller before the trigger ConfigMap existed; the trigger must not feed
    // the content digest.
    assertThat(plan.versionId()).isEqualTo("v17227349");
    assertThat(plan.trigger()).isNull();
  }

  @ParameterizedTest
  @EnumSource(TriggerKind.class)
  @DisplayName("the scheduler's trigger.json fixtures match what the controller renders")
  void triggerFixturesMatchControllerOutput(TriggerKind kind) throws IOException {
    assumeTrue(
        Files.isDirectory(FIXTURES.getParent().getParent()),
        "dws-schedule-time sources not alongside dws-controller; contract not checkable here");
    DeploymentPlan plan = compiler.compile(sample(kind));
    String rendered =
        synthesizer
            .triggerConfigMap(plan, "default")
            .orElseThrow()
            .getData()
            .get(StackSynthesizer.TRIGGER_KEY);
    Path fixture = FIXTURES.resolve("trigger-" + kind.wireName() + ".json");

    if (Boolean.getBoolean("dws.writeTriggerFixtures")) {
      Files.createDirectories(FIXTURES);
      Files.writeString(fixture, rendered + "\n", StandardCharsets.UTF_8);
    }

    assertThat(fixture).exists();
    assertThat(Files.readString(fixture, StandardCharsets.UTF_8).strip()).isEqualTo(rendered);
  }
}
