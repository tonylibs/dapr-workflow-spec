package io.dws.orchestrator.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.durabletask.Task;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.activity.AdminEventActivity;
import io.dws.orchestrator.workflow.activity.CatchDecision;
import io.dws.orchestrator.workflow.activity.CatchDecisionActivity;
import io.dws.orchestrator.workflow.activity.CatchDecisionRequest;
import io.dws.orchestrator.workflow.activity.EvaluateSetActivity;
import io.dws.orchestrator.workflow.activity.EvaluateSetRequest;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link ForkBranchWorkflow} resolves its one branch task by name and dispatches it
 * through {@link InterpreterWorkflow#dispatch} — the same pipeline a top-level task uses, including
 * a branch whose task is itself a container ({@code try}).
 */
class ForkBranchWorkflowTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final ForkBranchWorkflow workflow = new ForkBranchWorkflow();

  private void seed(String yaml) throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromString(yaml, WorkflowFormat.YAML);
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "fork-branch-workflow",
        "fork-branch-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        null,
        mock(WorkflowTaskOptions.class),
        "pubsub");
  }

  @SuppressWarnings("unchecked")
  private void stubAdminEvents(WorkflowContext ctx) {
    when(ctx.getInstanceId()).thenReturn("inst-1/raiseAlarm/callNurse");
    when(ctx.getCurrentInstant()).thenReturn(Instant.parse("2026-08-17T00:00:00Z"));
    Task<Void> adminTask = mock(Task.class);
    when(adminTask.await()).thenReturn(null);
    when(ctx.callActivity(
            eq(AdminEventActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(Void.class)))
        .thenReturn(adminTask);
  }

  @SuppressWarnings("unchecked")
  private static <T> Task<T> completed(T value) {
    Task<T> task = mock(Task.class);
    when(task.thenApply(any()))
        .thenAnswer(
            invocation -> {
              Function<T, Object> transform = invocation.getArgument(0);
              Task<Object> mapped = mock(Task.class);
              when(mapped.await()).thenAnswer(ignored -> transform.apply(task.await()));
              return mapped;
            });
    when(task.await()).thenReturn(value);
    return task;
  }

  @Test
  @SuppressWarnings("unchecked")
  void dispatchesALeafBranchTaskByName() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: fork-branch-workflow
          version: '1.0.0'
        do:
          - raiseAlarm:
              fork:
                branches:
                  - callNurse:
                      set:
                        paged: '"nurse"'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubAdminEvents(ctx);
    when(ctx.getInput(ForkBranchInput.class))
        .thenReturn(
            new ForkBranchInput(
                "callNurse",
                mapper.readTree("{\"seed\":1}"),
                mapper.createObjectNode(),
                Map.of(),
                1));
    when(ctx.callActivity(
            eq(EvaluateSetActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv -> completed(EvaluateSetActivity.apply((EvaluateSetRequest) inv.getArgument(1))));

    workflow.execute(ctx);

    org.mockito.ArgumentCaptor<Object> output = org.mockito.ArgumentCaptor.forClass(Object.class);
    org.mockito.Mockito.verify(ctx).complete(output.capture());
    JsonNode result = (JsonNode) output.getValue();
    assertThat(result.get("seed").intValue()).isEqualTo(1);
    assertThat(result.get("paged").textValue()).isEqualTo("nurse");
  }

  @Test
  @SuppressWarnings("unchecked")
  void dispatchesAContainerBranchTaskWithItsOwnCatchRecovery() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: fork-branch-workflow
          version: '1.0.0'
        do:
          - raiseAlarm:
              fork:
                branches:
                  - guarded:
                      try:
                        - explode:
                            raise:
                              error:
                                type: https://example.com/errors/x
                                status: 500
                                title: Boom
                                detail: boom
                      catch:
                        do:
                          - recovered:
                              set:
                                paged: '"recovered"'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubAdminEvents(ctx);
    when(ctx.getInput(ForkBranchInput.class))
        .thenReturn(
            new ForkBranchInput(
                "guarded", mapper.readTree("{}"), mapper.createObjectNode(), Map.of(), 1));
    when(ctx.callActivity(
            eq(io.dws.orchestrator.workflow.activity.RaiseErrorActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv ->
                completed(
                    io.dws.orchestrator.workflow.activity.RaiseErrorActivity.apply(
                        (io.dws.orchestrator.workflow.activity.RaiseErrorRequest)
                            inv.getArgument(1))));
    when(ctx.callActivity(
            eq(CatchDecisionActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(CatchDecision.class)))
        .thenAnswer(
            inv -> {
              CatchDecisionRequest req = inv.getArgument(1);
              return completed(
                  new CatchDecision(true, false, 0L, mapper.readTree("{\"status\":500}"), "error"));
            });
    when(ctx.callActivity(
            eq(EvaluateSetActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv -> completed(EvaluateSetActivity.apply((EvaluateSetRequest) inv.getArgument(1))));

    workflow.execute(ctx);

    org.mockito.ArgumentCaptor<Object> output = org.mockito.ArgumentCaptor.forClass(Object.class);
    org.mockito.Mockito.verify(ctx).complete(output.capture());
    JsonNode result = (JsonNode) output.getValue();
    assertThat(result.get("paged").textValue()).isEqualTo("recovered");
  }
}
