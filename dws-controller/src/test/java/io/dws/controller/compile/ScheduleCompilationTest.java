package io.dws.controller.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.ImageCatalog;
import io.dws.controller.model.TriggerKind;
import io.dws.controller.model.TriggerSpec;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code workflow-schedule-compilation} (scheduling Phase 6a): the controller validates a
 * definition's {@code schedule} at submit and normalizes a valid time trigger into the plan's
 * {@link TriggerSpec}. Plain JUnit — the compiler is framework-free.
 */
class ScheduleCompilationTest {

  private static final ImageCatalog IMAGES =
      new ImageCatalog("a:1", "b:1", "c:1", "d:1", "e:1", "f:1", "g:1", "h:1", "orchestrator:1");

  private final WorkflowCompiler v1 =
      new V1OrchestratorCompiler(IMAGES, ignored -> "x".getBytes(StandardCharsets.UTF_8));
  private final WorkflowCompiler v2 = new V2StructuralCompiler();

  /** A minimal valid definition named {@code order-sync} with the given {@code schedule} block. */
  static String scheduled(String schedule) {
    return """
        document:
          dsl: '1.0.0'
          namespace: test
          name: order-sync
          version: '1.0.0'
        schedule: %s
        do:
          - noop:
              set:
                done: true
        """
        .formatted(schedule);
  }

  private TriggerSpec triggerOf(String schedule) {
    return v1.compile(scheduled(schedule)).trigger();
  }

  private void assertRejected(String schedule, String... messageParts) {
    assertThatThrownBy(() -> v1.compile(scheduled(schedule)))
        .isInstanceOf(CompilationException.class)
        .hasMessageContainingAll(messageParts);
  }

  // ---- accepted -------------------------------------------------------------

  @Test
  @DisplayName("a valid 5-field cron schedule is accepted and carried verbatim")
  void acceptsValidCron() {
    DeploymentPlan plan = v1.compile(scheduled("{ cron: \"0 0 * * *\" }"));

    assertThat(plan.trigger())
        .isEqualTo(
            new TriggerSpec(
                "order-sync",
                plan.versionId(),
                plan.orchestrator().appId(),
                TriggerKind.CRON,
                "0 0 * * *"));
  }

  @Test
  @DisplayName("cron whitespace is normalized to single spaces")
  void normalizesCronWhitespace() {
    assertThat(triggerOf("{ cron: \"  */5   9-17 * *  1-5 \" }").schedule())
        .isEqualTo("*/5 9-17 * * 1-5");
  }

  @Test
  @DisplayName("a duration object is accepted and normalized to canonical ISO-8601")
  void acceptsDurationObject() {
    TriggerSpec trigger = triggerOf("{ every: { minutes: 5 } }");

    assertThat(trigger.kind()).isEqualTo(TriggerKind.EVERY);
    assertThat(trigger.schedule()).isEqualTo("PT5M");
  }

  @Test
  @DisplayName("a multi-unit duration object normalizes to one canonical string")
  void normalizesMultiUnitDurationObject() {
    assertThat(triggerOf("{ every: { hours: 1, minutes: 30 } }").schedule()).isEqualTo("PT1H30M");
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "PT1H, PT1H",
    "PT90M, PT1H30M",
    "P1D, PT24H",
    "P1DT1H, PT25H",
    "PT1S, PT1S",
    "PT1.5S, PT1.5S"
  })
  @DisplayName("ISO-8601 duration strings normalize to their canonical form")
  void normalizesIsoDurations(String literal, String canonical) {
    assertThat(triggerOf("{ every: \"" + literal + "\" }").schedule()).isEqualTo(canonical);
  }

  @Test
  @DisplayName("after is accepted and normalized like every")
  void acceptsAfter() {
    TriggerSpec trigger = triggerOf("{ after: { days: 1, milliseconds: 500 } }");

    assertThat(trigger.kind()).isEqualTo(TriggerKind.AFTER);
    assertThat(trigger.schedule()).isEqualTo("PT24H0.5S");
  }

  @Test
  @DisplayName("a definition without schedule has no trigger")
  void unscheduledHasNoTrigger() {
    String unscheduled =
        """
        document:
          dsl: '1.0.0'
          namespace: test
          name: order-sync
          version: '1.0.0'
        do:
          - noop:
              set:
                done: true
        """;

    assertThat(v1.compile(unscheduled).trigger()).isNull();
  }

  // ---- rejected: shape ------------------------------------------------------

  @Test
  @DisplayName("more than one time trigger is rejected")
  void rejectsMoreThanOneTrigger() {
    assertRejected(
        "{ every: \"PT1H\", cron: \"0 0 * * *\" }",
        "schedule",
        "exactly one of 'every', 'cron' or 'after'");
  }

  @Test
  @DisplayName("all three time triggers together are rejected")
  void rejectsAllThreeTriggers() {
    assertRejected(
        "{ every: PT1H, cron: \"0 0 * * *\", after: PT1M }",
        "exactly one of 'every', 'cron' or 'after'");
  }

  @Test
  @DisplayName("an empty schedule block is rejected")
  void rejectsEmptySchedule() {
    assertRejected("{}", "schedule", "one of 'every', 'cron', 'after' or 'on' is required");
  }

  @Test
  @DisplayName("an event trigger is rejected until Phase 6b")
  void rejectsOn() {
    assertRejected(
        "{ on: { one: { with: { type: com.example.order } } } }",
        "schedule.on",
        "not yet supported");
  }

  @Test
  @DisplayName("read without on is rejected")
  void rejectsReadWithoutOn() {
    assertRejected(
        "{ every: \"PT1H\", read: envelope }", "schedule.read", "valid only with 'schedule.on'");
  }

  @Test
  @DisplayName("read with on is rejected only for on")
  void rejectsReadWithOnForOnOnly() {
    assertThatThrownBy(
            () ->
                v1.compile(
                    scheduled(
                        "{ on: { one: { with: { type: com.example.order } } }, read: envelope }")))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("schedule.on")
        .hasMessageNotContaining("schedule.read");
  }

  // ---- rejected: durations --------------------------------------------------

  @Test
  @DisplayName("an interval below the 1 second minimum is rejected")
  void rejectsSubSecondInterval() {
    assertRejected("{ every: \"PT0.5S\" }", "schedule.every", "at least 1 second");
  }

  @ParameterizedTest
  @ValueSource(strings = {"{ every: {} }", "{ every: { milliseconds: 999 } }", "{ after: PT0S }"})
  @DisplayName("zero and sub-second durations are rejected")
  void rejectsOtherBelowMinimumDurations(String schedule) {
    assertRejected(schedule, "at least 1 second");
  }

  @Test
  @DisplayName("a negative duration is rejected")
  void rejectsNegativeDuration() {
    assertRejected("{ every: \"-PT1H\" }", "schedule.every", "at least 1 second");
  }

  @Test
  @DisplayName("a runtime-expression duration is rejected")
  void rejectsRuntimeExpression() {
    assertRejected(
        "{ after: \"${ .delay }\" }",
        "schedule.after",
        "runtime-expression durations are not supported");
  }

  @ParameterizedTest
  @ValueSource(strings = {"1h", "P1W", "P1M", "PT"})
  @DisplayName("strings that are not fixed-length ISO-8601 durations are rejected")
  void rejectsNonIsoDurations(String literal) {
    assertRejected("{ every: \"" + literal + "\" }", "schedule.every", "ISO-8601");
  }

  @Test
  @DisplayName("a negative duration-object field is rejected")
  void rejectsNegativeDurationField() {
    assertRejected("{ every: { minutes: -5 } }", "schedule.every");
  }

  // ---- rejected: cron -------------------------------------------------------

  @Test
  @DisplayName("a 4-field cron is rejected")
  void rejectsFourFieldCron() {
    assertRejected("{ cron: \"0 0 * *\" }", "schedule.cron", "5-field cron expression is required");
  }

  @Test
  @DisplayName("a 6-field cron is rejected")
  void rejectsSixFieldCron() {
    assertRejected(
        "{ cron: \"0 0 0 * * *\" }", "schedule.cron", "5-field cron expression is required");
  }

  @ParameterizedTest
  @ValueSource(strings = {"61 * * * *", "* 25 * * *", "* * 32 * *", "* * * 13 *", "a b c d e"})
  @DisplayName("a malformed 5-field cron is rejected")
  void rejectsMalformedCron(String cron) {
    assertRejected("{ cron: \"" + cron + "\" }", "schedule.cron", "not a valid cron expression");
  }

  @Test
  @DisplayName("a cron macro is not a 5-field expression")
  void rejectsCronMacro() {
    assertRejected("{ cron: \"@daily\" }", "schedule.cron", "5-field cron expression is required");
  }

  // ---- v2 strategy ----------------------------------------------------------

  @Test
  @DisplayName("the v2 strategy rejects any schedule")
  void v2RejectsSchedule() {
    assertThatThrownBy(() -> v2.compile(scheduled("{ every: \"PT1H\" }")))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("'schedule' is not yet supported on runtime v2");
  }

  @Test
  @DisplayName("the v2 strategy rejects a schedule the SDK model cannot represent")
  void v2RejectsUnparseableSchedule() {
    assertThatThrownBy(() -> v2.compile(scheduled("{ every: \"PT1H\", read: envelope }")))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("'schedule' is not yet supported on runtime v2");
  }
}
