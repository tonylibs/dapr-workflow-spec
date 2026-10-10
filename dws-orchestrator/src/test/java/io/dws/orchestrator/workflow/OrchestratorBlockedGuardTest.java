package io.dws.orchestrator.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.durabletask.Task;
import io.dapr.durabletask.interruption.ContinueAsNewInterruption;
import io.dapr.durabletask.interruption.OrchestratorBlockedException;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.activity.AdminEventActivity;
import io.dws.orchestrator.workflow.activity.AdminEventRequest;
import io.dws.orchestrator.workflow.activity.CatchDecisionActivity;
import io.dws.orchestrator.workflow.activity.EvaluateSetActivity;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The Dapr SDK parks a workflow on an unfinished task by throwing {@link
 * OrchestratorBlockedException} out of {@code Task.await()} (and {@link ContinueAsNewInterruption}
 * to restart it). Both are {@link RuntimeException}s, but they are control flow, not failures: the
 * orchestrator catches them to end the current replay pass. Any workflow code that catches {@code
 * RuntimeException} and then schedules an activity (a lifecycle-event publish, a catch decision)
 * would add a command during that unwind, so the task-ID sequence differs between the first
 * execution and the replay — a non-deterministic workflow.
 *
 * <p>Each test makes a mocked {@link WorkflowContext}'s {@code await()} throw the interruption and
 * asserts that nothing is published or scheduled after the blocking call, and that the interruption
 * reaches the caller untouched.
 */
class OrchestratorBlockedGuardTest {

  private static final String START_EVENT = "io.dws.instance.started";
  private static final String TASK_STARTED_EVENT = "io.dws.task.started";

  private static final String SET_YAML =
      """
      document:
        dsl: 1.0.0
        namespace: examples
        name: blocked-workflow
        version: '1.0.0'
      do:
        - work:
            set:
              done: '"yes"'
      """;

  private static final String TRY_YAML =
      """
      document:
        dsl: 1.0.0
        namespace: examples
        name: blocked-workflow
        version: '1.0.0'
      do:
        - wrapper:
            try:
              - work:
                  set:
                    done: '"yes"'
            catch:
              errors:
                with:
                  type: https://serverlessworkflow.io/spec/1.0.0/errors/runtime
              do:
                - repair:
                    set:
                      repaired: '"yes"'
      """;

  private final ObjectMapper mapper = new ObjectMapper();

  /** Every activity name scheduled on the context, in order. */
  private final List<String> scheduledActivities = new ArrayList<>();

  /** The CloudEvent {@code type} of every lifecycle event published, in order. */
  private final List<String> publishedEvents = new ArrayList<>();

  @AfterEach
  void clear() {
    scheduledActivities.clear();
    publishedEvents.clear();
  }

  /** Both control-flow interruptions the SDK uses, each as a fresh instance. */
  static Stream<Supplier<RuntimeException>> interruptions() {
    return Stream.of(
        () -> new OrchestratorBlockedException("The orchestrator is blocked"),
        () -> new ContinueAsNewInterruption("continue as new"));
  }

  private void seed(String yaml) throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromString(yaml, WorkflowFormat.YAML);
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "blocked-workflow",
        "blocked-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        /* daprClient (unused; activities are stubbed) */ null,
        mock(WorkflowTaskOptions.class),
        "pubsub");
  }

  /**
   * A context whose lifecycle-event publishes complete normally and whose first {@code set}
   * evaluation blocks — the spot where a real context throws on an unfinished task. Every scheduled
   * activity is recorded.
   */
  @SuppressWarnings("unchecked")
  private WorkflowContext blockingContext(Supplier<RuntimeException> interruption) {
    WorkflowContext ctx = mock(WorkflowContext.class);
    when(ctx.getInstanceId()).thenReturn("inst-blocked-1");
    when(ctx.getCurrentInstant()).thenReturn(Instant.parse("2026-08-20T00:00:00Z"));
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.createObjectNode());

    when(ctx.callActivity(
            any(String.class), any(), any(WorkflowTaskOptions.class), any(Class.class)))
        .thenAnswer(
            inv -> {
              String activity = inv.getArgument(0);
              scheduledActivities.add(activity);
              Task<Object> task = mock(Task.class);
              if (AdminEventActivity.class.getName().equals(activity)) {
                AdminEventRequest request = inv.getArgument(1);
                publishedEvents.add(request.data().get("type").asText());
                when(task.await()).thenReturn(null);
              } else {
                when(task.await()).thenThrow(interruption.get());
                // Dispatch maps results with thenApply(...).await(); the mapped task blocks too.
                when(task.thenApply(any())).thenReturn(task);
              }
              return task;
            });
    return ctx;
  }

  @ParameterizedTest
  @MethodSource("interruptions")
  void topLevelExecuteRethrowsInterruptionWithoutPublishingFailure(
      Supplier<RuntimeException> interruption) throws Exception {
    seed(SET_YAML);
    WorkflowContext ctx = blockingContext(interruption);

    assertThatThrownBy(() -> new InterpreterWorkflow().execute(ctx))
        .isInstanceOf(interruption.get().getClass());

    // Only what ran before the block: the start events and the one activity that parked.
    assertThat(publishedEvents).containsExactly(START_EVENT, TASK_STARTED_EVENT);
    assertThat(scheduledActivities)
        .containsExactly(
            AdminEventActivity.class.getName(),
            AdminEventActivity.class.getName(),
            EvaluateSetActivity.class.getName());
    verify(ctx, never()).complete(any());
  }

  @ParameterizedTest
  @MethodSource("interruptions")
  void tryTaskRethrowsInterruptionWithoutAskingCatchDecision(
      Supplier<RuntimeException> interruption) throws Exception {
    seed(TRY_YAML);
    WorkflowContext ctx = blockingContext(interruption);

    assertThatThrownBy(() -> new InterpreterWorkflow().execute(ctx))
        .isInstanceOf(interruption.get().getClass());

    assertThat(scheduledActivities).doesNotContain(CatchDecisionActivity.class.getName());
    assertThat(publishedEvents)
        .containsExactly(START_EVENT, TASK_STARTED_EVENT, TASK_STARTED_EVENT);
    verify(ctx, never()).createTimer(any(java.time.Duration.class));
  }

  @ParameterizedTest
  @MethodSource("interruptions")
  void scopeRunnerChildRethrowsInterruptionWithoutPublishingFailure(
      Supplier<RuntimeException> interruption) throws Exception {
    seed(SET_YAML);
    WorkflowContext ctx = blockingContext(interruption);
    when(ctx.getInput(ScopeRunnerInput.class))
        .thenReturn(
            new ScopeRunnerInput(
                null,
                mapper.createObjectNode(),
                mapper.createObjectNode(),
                Map.of(),
                0,
                DispatchContext.root("inst-blocked-1")));

    assertThatThrownBy(() -> new ScopeRunnerWorkflow().execute(ctx))
        .isInstanceOf(interruption.get().getClass());

    assertThat(publishedEvents).containsExactly(TASK_STARTED_EVENT);
    verify(ctx, never()).complete(any());
  }

  @ParameterizedTest
  @MethodSource("interruptions")
  void forkBranchChildRethrowsInterruptionWithoutPublishing(Supplier<RuntimeException> interruption)
      throws Exception {
    seed(SET_YAML);
    WorkflowContext ctx = blockingContext(interruption);
    when(ctx.getInput(ForkBranchInput.class))
        .thenReturn(
            new ForkBranchInput(
                "work",
                mapper.createObjectNode(),
                mapper.createObjectNode(),
                Map.of(),
                0,
                DispatchContext.root("inst-blocked-1")));

    assertThatThrownBy(() -> new ForkBranchWorkflow().execute(ctx))
        .isInstanceOf(interruption.get().getClass());

    assertThat(publishedEvents).isEmpty();
    verify(ctx, never()).complete(any());
  }
}
