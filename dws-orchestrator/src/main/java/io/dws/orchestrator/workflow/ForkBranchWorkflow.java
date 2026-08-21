package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowStub;
import io.dws.orchestrator.workflow.activity.DefinitionLookup;
import io.serverlessworkflow.api.types.Task;

/**
 * Runs exactly one task item as its own, independent, deterministic workflow instance.
 *
 * <p>Registered under {@link #NAME} — not derived from {@code document.name} — so it never collides
 * with the top-level workflow's own registration. Two call sites start an instance of this workflow
 * via {@link WorkflowContext#callChildWorkflow}: {@link InterpreterWorkflow}'s {@code dispatchFork}
 * starts one per {@code fork} branch, without awaiting immediately, so several branches run
 * concurrently and are combined at the call site with {@code allOf}/{@code anyOf}; and {@code
 * dispatchWithTimeout} starts one for a task that declares a {@code timeout}, racing it against a
 * timer with {@code anyOf}.
 *
 * <p>Delegates its entire body to {@link InterpreterWorkflow#dispatch}, the same per-task pipeline
 * every top-level task already goes through (data flow, nested {@code try}/{@code for}/{@code
 * fork}, lifecycle events) — a branch or a guarded task is dispatched exactly like a top-level task
 * would be, with zero duplicated dispatch logic. Completes with the full {@link
 * InterpreterWorkflow.Dispatch} (not just its data), because {@code dispatchWithTimeout} needs the
 * dispatched task's own flow directive to keep advancing its scope on a non-timeout completion; a
 * fork branch's caller reads only {@link InterpreterWorkflow.Dispatch#data} from it, per the DSL's
 * own join semantics.
 */
public class ForkBranchWorkflow implements Workflow {

  public static final String NAME = "dws-fork-branch";

  @Override
  public WorkflowStub create() {
    return this::execute;
  }

  /** Extracted from the {@link WorkflowStub} lambda so it can be driven directly in tests. */
  public void execute(WorkflowContext ctx) {
    ObjectMapper mapper = WorkflowSupport.mapper();
    ForkBranchInput input = ctx.getInput(ForkBranchInput.class);
    AdminEventBuilder events = AdminEventBuilder.forContext(ctx);

    Task task = DefinitionLookup.taskByName(input.taskName());
    InterpreterWorkflow.Dispatch result =
        new InterpreterWorkflow()
            .dispatch(
                ctx,
                task,
                input.taskName(),
                input.data(),
                input.context(),
                input.variables(),
                input.depth(),
                events,
                mapper);
    ctx.complete(result);
  }
}
