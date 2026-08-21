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
 * Verifies {@link ScopeRunnerWorkflow} resolves the requested task list — the top-level {@code do}
 * list, or a named {@code try} task's {@code try} list — and runs it through {@link
 * InterpreterWorkflow#runTaskList}, the same scope-aware runner every other scope uses.
 */
class ScopeRunnerWorkflowTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final ScopeRunnerWorkflow workflow = new ScopeRunnerWorkflow();

  private void seed(String yaml) throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromString(yaml, WorkflowFormat.YAML);
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "scope-runner-workflow",
        "scope-runner-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        null,
        mock(WorkflowTaskOptions.class),
        "pubsub");
  }

  @SuppressWarnings("unchecked")
  private void stubAdminEvents(WorkflowContext ctx) {
    when(ctx.getInstanceId()).thenReturn("inst-1/scopeRunner");
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

  private void stubEvaluateSet(WorkflowContext ctx) {
    when(ctx.callActivity(
            eq(EvaluateSetActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv -> completed(EvaluateSetActivity.apply((EvaluateSetRequest) inv.getArgument(1))));
  }

  @Test
  void runsTheTopLevelDoListWhenNoTryTaskNameIsGiven() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: scope-runner-workflow
          version: '1.0.0'
        do:
          - stampIt:
              set:
                stamped: '"top-level"'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubAdminEvents(ctx);
    stubEvaluateSet(ctx);
    when(ctx.getInput(ScopeRunnerInput.class))
        .thenReturn(
            new ScopeRunnerInput(
                null, mapper.readTree("{}"), mapper.createObjectNode(), Map.of(), 0));

    workflow.execute(ctx);

    org.mockito.ArgumentCaptor<Object> output = org.mockito.ArgumentCaptor.forClass(Object.class);
    org.mockito.Mockito.verify(ctx).complete(output.capture());
    ScopeResult result = (ScopeResult) output.getValue();
    assertThat(result.data().get("stamped").textValue()).isEqualTo("top-level");
    assertThat(result.end()).isEqualTo(ScopeEnd.FELL_THROUGH);
  }

  @Test
  void runsATryTasksTryListWhenNamed() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: scope-runner-workflow
          version: '1.0.0'
        do:
          - guarded:
              try:
                - stampIt:
                    set:
                      stamped: '"try-body"'
              catch:
                do: []
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubAdminEvents(ctx);
    stubEvaluateSet(ctx);
    when(ctx.getInput(ScopeRunnerInput.class))
        .thenReturn(
            new ScopeRunnerInput(
                "guarded", mapper.readTree("{}"), mapper.createObjectNode(), Map.of(), 1));

    workflow.execute(ctx);

    org.mockito.ArgumentCaptor<Object> output = org.mockito.ArgumentCaptor.forClass(Object.class);
    org.mockito.Mockito.verify(ctx).complete(output.capture());
    ScopeResult result = (ScopeResult) output.getValue();
    assertThat(result.data().get("stamped").textValue()).isEqualTo("try-body");
  }
}
