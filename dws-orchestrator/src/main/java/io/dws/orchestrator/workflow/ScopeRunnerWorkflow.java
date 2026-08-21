package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowStub;
import io.dws.orchestrator.workflow.activity.DefinitionLookup;
import io.serverlessworkflow.api.types.TaskItem;
import java.util.List;

/**
 * Runs one task list (a top-level {@code do} list, or a {@code try} task's {@code try} list) as its
 * own, independent, deterministic workflow instance.
 *
 * <p>Registered under {@link #NAME} — not derived from {@code document.name} — so it never collides
 * with the top-level workflow's own registration, and is distinct from {@link
 * ForkBranchWorkflow#NAME}. Started (without being immediately awaited) via {@link
 * WorkflowContext#callChildWorkflow} wherever a guarded execution needs a single {@code Task}
 * handle it can race against a timeout timer with {@code anyOf} — a workflow-level timeout over the
 * top-level {@code do} list, and a retry per-attempt timeout over a {@code try} task's {@code try}
 * list. Task-level timeout does not use this type; it reuses {@link ForkBranchWorkflow}, which
 * already runs exactly one task item as a child instance.
 *
 * <p>Delegates its entire body to {@link InterpreterWorkflow#runTaskList}, the same scope-aware
 * task-list runner every top-level {@code do}, {@code try}, {@code catch.do}, and {@code for.do}
 * list already goes through — a guarded scope is run exactly like any other scope, with zero
 * duplicated dispatch logic.
 */
public class ScopeRunnerWorkflow implements Workflow {

  public static final String NAME = "dws-scope-runner";

  @Override
  public WorkflowStub create() {
    return this::execute;
  }

  /** Extracted from the {@link WorkflowStub} lambda so it can be driven directly in tests. */
  public void execute(WorkflowContext ctx) {
    ObjectMapper mapper = WorkflowSupport.mapper();
    ScopeRunnerInput input = ctx.getInput(ScopeRunnerInput.class);
    AdminEventBuilder events = AdminEventBuilder.forContext(ctx);

    List<TaskItem> items = itemsFor(input.tryTaskName());
    ScopeResult result =
        new InterpreterWorkflow()
            .runTaskList(
                ctx,
                items,
                input.data(),
                input.context(),
                input.variables(),
                input.depth(),
                events,
                mapper);
    ctx.complete(result);
  }

  private static List<TaskItem> itemsFor(String tryTaskName) {
    if (tryTaskName == null || tryTaskName.isBlank()) {
      return WorkflowSupport.definition().getDo();
    }
    return DefinitionLookup.taskByName(tryTaskName).getTryTask().getTry();
  }
}
