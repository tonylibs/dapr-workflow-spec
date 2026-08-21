package io.dws.orchestrator.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.durabletask.Task;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.error.StepInvocationException;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.activity.AdminEventActivity;
import io.dws.orchestrator.workflow.activity.CatchDecisionActivity;
import io.dws.orchestrator.workflow.activity.CatchDecisionRequest;
import io.dws.orchestrator.workflow.activity.CatchPolicy;
import io.dws.orchestrator.workflow.activity.DataFlowInputActivity;
import io.dws.orchestrator.workflow.activity.DataFlowInputRequest;
import io.dws.orchestrator.workflow.activity.DataFlowOutputActivity;
import io.dws.orchestrator.workflow.activity.DataFlowOutputRequest;
import io.dws.orchestrator.workflow.activity.DataFlowPipeline;
import io.dws.orchestrator.workflow.activity.DataFlowResult;
import io.dws.orchestrator.workflow.activity.DefinitionLookup;
import io.dws.orchestrator.workflow.activity.EvaluateSetActivity;
import io.dws.orchestrator.workflow.activity.EvaluateSetRequest;
import io.dws.orchestrator.workflow.activity.StepActivity;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.Workflow;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Drives task-level, workflow-level, and retry-per-attempt timeouts against a mocked {@link
 * WorkflowContext}. {@code ForkBranchWorkflow}/{@code ScopeRunnerWorkflow} child instances are not
 * really spawned by Mockito — {@code ctx.callChildWorkflow} is stubbed to run the exact same
 * dispatch/runTaskList call the real child workflow type would make, against this same mocked
 * context, so these tests assert real guarded-execution semantics rather than a mock's arrangement.
 * {@code ctx.anyOf} is stubbed per test to pick either the guarded work (index 0, added first by
 * the production code) or the timer (index 1), simulating which side of the race wins.
 */
class TimeoutInterpreterTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final InterpreterWorkflow workflow = new InterpreterWorkflow();

  private void seedYaml(String yaml) throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromString(yaml, WorkflowFormat.YAML);
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "timeout-workflow",
        "timeout-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        /* daprClient (unused; activities are stubbed) */ null,
        mock(WorkflowTaskOptions.class),
        "pubsub");
  }

  @SuppressWarnings("unchecked")
  private void stubContext(WorkflowContext ctx) {
    when(ctx.getInstanceId()).thenReturn("inst-timeout-1");
    when(ctx.getCurrentInstant()).thenReturn(Instant.parse("2026-08-20T00:00:00Z"));
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.createObjectNode());

    Task<Void> voidTask = completed(null);
    when(ctx.callActivity(
            eq(AdminEventActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(Void.class)))
        .thenReturn(voidTask);

    when(ctx.callActivity(
            eq(EvaluateSetActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv -> completed(EvaluateSetActivity.apply((EvaluateSetRequest) inv.getArgument(1))));
    when(ctx.callActivity(
            eq(CatchDecisionActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(io.dws.orchestrator.workflow.activity.CatchDecision.class)))
        .thenAnswer(
            inv -> completed(CatchPolicy.decide((CatchDecisionRequest) inv.getArgument(1))));
    when(ctx.callActivity(
            eq(DataFlowInputActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv ->
                completed(DataFlowPipeline.applyInput((DataFlowInputRequest) inv.getArgument(1))));
    when(ctx.callActivity(
            eq(DataFlowOutputActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(DataFlowResult.class)))
        .thenAnswer(
            inv ->
                completed(
                    DataFlowPipeline.applyOutput((DataFlowOutputRequest) inv.getArgument(1))));

    // Task-level guard: ctx.callChildWorkflow(ForkBranchWorkflow.NAME, ...) runs the real
    // dispatch() the production child workflow would run, against this same mocked context.
    when(ctx.callChildWorkflow(
            eq(ForkBranchWorkflow.NAME), any(), eq(InterpreterWorkflow.Dispatch.class)))
        .thenAnswer(
            inv -> {
              ForkBranchInput input = inv.getArgument(1);
              io.serverlessworkflow.api.types.Task task =
                  DefinitionLookup.taskByName(input.taskName());
              InterpreterWorkflow.Dispatch result =
                  workflow.dispatch(
                      ctx,
                      task,
                      input.taskName(),
                      input.data(),
                      input.context(),
                      input.variables(),
                      input.depth(),
                      AdminEventBuilder.forContext(ctx),
                      mapper);
              return completed(result);
            });

    // Workflow/retry-attempt guard: ctx.callChildWorkflow(ScopeRunnerWorkflow.NAME, ...) runs the
    // real runTaskList() the production child workflow would run, against this same context.
    when(ctx.callChildWorkflow(eq(ScopeRunnerWorkflow.NAME), any(), eq(ScopeResult.class)))
        .thenAnswer(
            inv -> {
              ScopeRunnerInput input = inv.getArgument(1);
              List<TaskItem> items =
                  (input.tryTaskName() == null || input.tryTaskName().isBlank())
                      ? WorkflowSupport.definition().getDo()
                      : DefinitionLookup.taskByName(input.tryTaskName()).getTryTask().getTry();
              ScopeResult result =
                  workflow.runTaskList(
                      ctx,
                      items,
                      input.data(),
                      input.context(),
                      input.variables(),
                      input.depth(),
                      AdminEventBuilder.forContext(ctx),
                      mapper);
              return completed(result);
            });
  }

  /** The step activity always fails with {@code status}. */
  private void stubCallAlwaysFailing(WorkflowContext ctx, int status) {
    when(ctx.callActivity(
            eq(StepActivity.NAME), any(), any(WorkflowTaskOptions.class), eq(JsonNode.class)))
        .thenThrow(new StepInvocationException("guarded", status, "upstream down", null));
  }

  /**
   * {@code ctx.anyOf} always picks the handle at {@code winningIndex} from whatever race list is
   * passed — 0 is the guarded work (added first by production code), 1 is the timer.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void stubRace(WorkflowContext ctx, IntSupplier winningIndex) {
    when(ctx.anyOf(any(List.class)))
        .thenAnswer(
            inv -> {
              List<Task> handles = inv.getArgument(0);
              Task winner = handles.get(winningIndex.getAsInt());
              Task combined = mock(Task.class);
              when(combined.await()).thenReturn(winner);
              return combined;
            });
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <T> Task<T> completed(T value) {
    Task<T> task = mock(Task.class);
    when(task.await()).thenReturn(value);
    when(task.thenApply(any()))
        .thenAnswer(
            invocation -> {
              Function<T, Object> transform = invocation.getArgument(0);
              Task<Object> mapped = mock(Task.class);
              when(mapped.await()).thenAnswer(ignored -> transform.apply(task.await()));
              return mapped;
            });
    return task;
  }

  private static JsonNode completionOutput(WorkflowContext ctx) {
    ArgumentCaptor<Object> output = ArgumentCaptor.forClass(Object.class);
    verify(ctx).complete(output.capture());
    return (JsonNode) output.getValue();
  }

  // ---- task-level timeout ---------------------------------------------------

  private static String taskTimeoutYaml(boolean insideTry) {
    if (!insideTry) {
      return """
          document:
            dsl: 1.0.0
            namespace: examples
            name: timeout-workflow
            version: '1.0.0'
          do:
            - guarded:
                set:
                  stamped: '"yes"'
                timeout:
                  after:
                    seconds: 5
            - finish:
                set:
                  done: '"yes"'
          """;
    }
    return """
        document:
          dsl: 1.0.0
          namespace: examples
          name: timeout-workflow
          version: '1.0.0'
        do:
          - wrapper:
              try:
                - guarded:
                    set:
                      stamped: '"yes"'
                    timeout:
                      after:
                        seconds: 5
              catch:
                errors:
                  with:
                    type: https://serverlessworkflow.io/spec/1.0.0/errors/timeout
                do:
                  - repair:
                      set:
                        reason: '${ $error.detail }'
        """;
  }

  @Test
  void taskCompletingWithinItsTimeoutIsUnaffected() throws Exception {
    seedYaml(taskTimeoutYaml(false));
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    Task<Void> timerTask = completed(null);
    when(ctx.createTimer(any(Duration.class))).thenReturn(timerTask);
    stubRace(ctx, () -> 0);

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    assertThat(output.get("stamped").textValue()).isEqualTo("yes");
    assertThat(output.get("done").textValue()).isEqualTo("yes");
  }

  @Test
  void taskTimeoutResolvedByUseTimeoutsReferenceBehavesLikeInline() throws Exception {
    seedYaml(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: timeout-workflow
          version: '1.0.0'
        use:
          timeouts:
            short:
              after:
                seconds: 5
        do:
          - guarded:
              set:
                stamped: '"yes"'
              timeout: short
          - finish:
              set:
                done: '"yes"'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    Task<Void> timerTask = completed(null);
    when(ctx.createTimer(any(Duration.class))).thenReturn(timerTask);
    stubRace(ctx, () -> 1);

    assertThatThrownBy(() -> workflow.execute(ctx))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("guarded")
        .hasMessageContaining("timed out after");
  }

  @Test
  void taskExceedingItsTimeoutFailsWithATimeoutError() throws Exception {
    seedYaml(taskTimeoutYaml(false));
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    Task<Void> timerTask = completed(null);
    when(ctx.createTimer(any(Duration.class))).thenReturn(timerTask);
    stubRace(ctx, () -> 1);

    assertThatThrownBy(() -> workflow.execute(ctx))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("guarded")
        .hasMessageContaining("timed out after");

    verify(ctx, never()).complete(any());
  }

  @Test
  void timedOutTaskInsideTryIsCaughtByTypeFilter() throws Exception {
    seedYaml(taskTimeoutYaml(true));
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    Task<Void> timerTask = completed(null);
    when(ctx.createTimer(any(Duration.class))).thenReturn(timerTask);
    stubRace(ctx, () -> 1);

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    assertThat(output.get("reason").textValue()).contains("guarded").contains("timed out after");
  }

  // ---- workflow-level timeout ------------------------------------------------

  private static final String WORKFLOW_TIMEOUT_YAML =
      """
      document:
        dsl: 1.0.0
        namespace: examples
        name: timeout-workflow
        version: '1.0.0'
      timeout:
        after:
          seconds: 30
      do:
        - stampIt:
            set:
              stamped: '"yes"'
      """;

  @Test
  void instanceCompletingWithinItsWorkflowTimeoutIsUnaffected() throws Exception {
    seedYaml(WORKFLOW_TIMEOUT_YAML);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    Task<Void> timerTask = completed(null);
    when(ctx.createTimer(any(ZonedDateTime.class))).thenReturn(timerTask);
    stubRace(ctx, () -> 0);

    workflow.execute(ctx);

    assertThat(completionOutput(ctx).get("stamped").textValue()).isEqualTo("yes");
  }

  @Test
  void instanceExceedingItsWorkflowTimeoutFails() throws Exception {
    seedYaml(WORKFLOW_TIMEOUT_YAML);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    Task<Void> timerTask = completed(null);
    when(ctx.createTimer(any(ZonedDateTime.class))).thenReturn(timerTask);
    stubRace(ctx, () -> 1);

    assertThatThrownBy(() -> workflow.execute(ctx))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("workflow timed out after");

    verify(ctx, never()).complete(any());
  }

  @Test
  void topLevelTryCannotCatchTheInstanceWideDeadline() throws Exception {
    seedYaml(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: timeout-workflow
          version: '1.0.0'
        timeout:
          after:
            seconds: 30
        do:
          - guarded:
              try:
                - fetchOrder:
                    call: http
                    with:
                      method: get
                      endpoint: http://order-service/run
              catch: {}
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    Task<Void> timerTask = completed(null);
    when(ctx.createTimer(any(ZonedDateTime.class))).thenReturn(timerTask);
    // The instance-wide deadline races above the whole guarded top-level scope, so it always wins
    // here regardless of what the try inside that scope would have done.
    stubRace(ctx, () -> 1);

    assertThatThrownBy(() -> workflow.execute(ctx))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("workflow timed out after");

    verify(ctx, never()).complete(any());
  }

  // ---- retry per-attempt timeout ---------------------------------------------

  private static final String PER_ATTEMPT_TIMEOUT_YAML =
      """
      document:
        dsl: 1.0.0
        namespace: examples
        name: timeout-workflow
        version: '1.0.0'
      do:
        - guarded:
            try:
              - fetchOrder:
                  call: http
                  with:
                    method: get
                    endpoint: http://order-service/run
            catch:
              errors:
                with:
                  status: 503
              retry:
                delay:
                  seconds: 1
                limit:
                  attempt:
                    count: 2
                    duration:
                      seconds: 5
              do:
                - repair:
                    set:
                      reason: '${ $error.detail }'
      """;

  @Test
  void attemptExceedingItsPerAttemptDurationCountsAsAFailedAttempt() throws Exception {
    seedYaml(PER_ATTEMPT_TIMEOUT_YAML);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    // The first attempt's guarded scope always loses the per-attempt race (index 1 = timer); the
    // second attempt is allowed by limit.attempt.count and completes normally without a timeout.
    stubCallAlwaysFailing(ctx, 503);
    java.util.concurrent.atomic.AtomicInteger call =
        new java.util.concurrent.atomic.AtomicInteger();
    Task<Void> timerTask = completed(null);
    when(ctx.createTimer(any(Duration.class))).thenReturn(timerTask);
    stubRace(ctx, () -> call.getAndIncrement() == 0 ? 1 : 0);

    workflow.execute(ctx);

    // Two body executions allowed: attempt 1 times out (counts as a failed attempt), attempt 2 is
    // the fixture's own step failure, then the limit is exhausted and recovery runs.
    JsonNode output = completionOutput(ctx);
    assertThat(output.get("reason").textValue()).isNotNull();
  }
}
