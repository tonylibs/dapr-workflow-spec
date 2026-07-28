package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowStub;
import io.dws.orchestrator.workflow.activity.AdminEventActivity;
import io.dws.orchestrator.workflow.activity.AdminEventRequest;
import io.dws.orchestrator.workflow.activity.CallRequest;
import io.dws.orchestrator.workflow.activity.CallServiceActivity;
import io.dws.orchestrator.workflow.activity.DataFlowInputActivity;
import io.dws.orchestrator.workflow.activity.DataFlowInputRequest;
import io.dws.orchestrator.workflow.activity.DataFlowOutputActivity;
import io.dws.orchestrator.workflow.activity.DataFlowOutputRequest;
import io.dws.orchestrator.workflow.activity.DataFlowPipeline;
import io.dws.orchestrator.workflow.activity.DataFlowResult;
import io.dws.orchestrator.workflow.activity.EmitEventActivity;
import io.dws.orchestrator.workflow.activity.EmitRequest;
import io.dws.orchestrator.workflow.activity.EvaluateSetActivity;
import io.dws.orchestrator.workflow.activity.EvaluateSetRequest;
import io.dws.orchestrator.workflow.activity.EvaluateSwitchActivity;
import io.dws.orchestrator.workflow.activity.EvaluateSwitchRequest;
import io.dws.orchestrator.workflow.activity.FlowOutcome;
import io.dws.orchestrator.workflow.adapter.TaskNaming;
import io.serverlessworkflow.api.types.DurationInline;
import io.serverlessworkflow.api.types.FlowDirective;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.TimeoutAfter;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single, generic workflow. It interprets the pod's one immutable Open Workflow Specification
 * definition (SDK {@code io.serverlessworkflow.api.types.Workflow}) directly as a program-counter
 * loop over the {@code do} task list. The definition is fixed for the pod's lifetime, so the loop
 * is deterministic across replays without pinning a version in the instance input.
 *
 * <p>Every task type dispatches uniformly through a Dapr activity: CALL via service invocation and
 * EMIT via pub/sub reach the network, while the pure SWITCH/SET steps run as local, in-process
 * activities (nothing extra is deployed for them). WAIT/LISTEN instead use the context's own timer
 * and external-event primitives, which are the idiomatic durable mechanism. A {@code call} task's
 * target app-id is the task name in kebab-case (see {@link TaskNaming}).
 */
public class InterpreterWorkflow implements Workflow {

  /** Guard against definitions that loop forever between tasks. */
  private static final int MAX_STEPS = 10_000;

  /** Sentinel program-counter value meaning "workflow already completed". */
  private static final int COMPLETE = Integer.MIN_VALUE;

  /** Fallback wait for a LISTEN task that does not constrain a timeout. */
  private static final Duration DEFAULT_LISTEN_TIMEOUT = Duration.ofDays(1);

  @Override
  public WorkflowStub create() {
    return this::execute;
  }

  /**
   * Runs the interpreter loop. Extracted from the {@link WorkflowStub} lambda so it can be driven
   * directly against a mocked {@link WorkflowContext} in tests.
   */
  public void execute(WorkflowContext ctx) {
    ObjectMapper mapper = WorkflowSupport.mapper();
    List<TaskItem> items = WorkflowSupport.definition().getDo();

    // All timestamps/ids for lifecycle events come from this builder (workflow-context sourced),
    // so publishing stays replay-deterministic — no Instant.now()/UUID.randomUUID() in execute.
    AdminEventBuilder events = AdminEventBuilder.forContext(ctx);

    JsonNode data = ctx.getInput(JsonNode.class);
    if (data == null) {
      data = mapper.createObjectNode();
    }

    // The workflow context is a second document, distinct from `data`: `export.as` writes it and it
    // persists for the instance's life. It is threaded through the loop (never stored externally)
    // so replay stays deterministic, and it is not part of the instance's completion output.
    JsonNode context = mapper.createObjectNode();

    publish(ctx, events.instanceStarted());

    Map<String, Integer> indexByName = new HashMap<>();
    for (int i = 0; i < items.size(); i++) {
      indexByName.put(items.get(i).getName(), i);
    }

    try {
      int pc = 0;
      for (int steps = 0; pc >= 0 && pc < items.size(); steps++) {
        if (steps > MAX_STEPS) {
          throw new IllegalStateException(
              "workflow exceeded " + MAX_STEPS + " steps; check for a definition loop");
        }

        TaskItem item = items.get(pc);
        String name = item.getName();
        Task task = item.getTask();
        String taskType = taskTypeOf(task);

        publish(ctx, events.taskStarted(name, taskType));
        FlowOutcome then;
        try {
          Dispatch result = dispatch(ctx, task, name, data, context, mapper);
          data = result.data();
          context = result.context();
          then = result.then();
        } catch (RuntimeException e) {
          publish(ctx, events.taskFailed(name, taskType, String.valueOf(e.getMessage())));
          throw e;
        }
        publish(ctx, events.taskCompleted(name, taskType));

        pc = advance(then, pc, indexByName);
        if (pc == COMPLETE) {
          publish(ctx, events.instanceCompleted());
          ctx.complete(data);
          return;
        }
      }

      // Ran off the end of the task list: complete with the current data.
      publish(ctx, events.instanceCompleted());
      ctx.complete(data);
    } catch (RuntimeException e) {
      publish(ctx, events.instanceFailed(String.valueOf(e.getMessage())));
      throw e;
    }
  }

  /** The post-dispatch data and context documents plus the task's resolved flow outcome. */
  private record Dispatch(JsonNode data, JsonNode context, FlowOutcome then) {}

  /** The task body's own result: its raw output document and its flow directive. */
  private record Body(JsonNode data, FlowOutcome then) {}

  /**
   * Runs one task item's full Open Workflow Specification data-flow pipeline around its body:
   * {@code input.from}/{@code input.schema} before, {@code output.as}/{@code output.schema} and
   * {@code export.as}/{@code export.schema} after. Both phases are skipped entirely — no activity
   * scheduled, data passed straight through — for a task that declares no {@code input}/{@code
   * output}/{@code export}, which is every definition that predates this pipeline.
   */
  private Dispatch dispatch(
      WorkflowContext ctx,
      Task task,
      String name,
      JsonNode data,
      JsonNode context,
      ObjectMapper mapper) {
    TaskBase base = DataFlowPipeline.baseOf(task);
    boolean hasInput = base != null && base.getInput() != null;
    boolean hasOutput = base != null && (base.getOutput() != null || base.getExport() != null);

    JsonNode input = data;
    if (hasInput) {
      input =
          ctx.callActivity(
                  DataFlowInputActivity.class.getName(),
                  new DataFlowInputRequest(name, data, context),
                  WorkflowSupport.defaultTaskOptions(),
                  JsonNode.class)
              .await();
    }

    Body body = dispatchBody(ctx, task, name, input, mapper);

    if (!hasOutput) {
      return new Dispatch(body.data(), context, body.then());
    }
    DataFlowResult shaped =
        ctx.callActivity(
                DataFlowOutputActivity.class.getName(),
                new DataFlowOutputRequest(name, body.data(), context),
                WorkflowSupport.defaultTaskOptions(),
                DataFlowResult.class)
            .await();
    return new Dispatch(shaped.data(), shaped.context(), body.then());
  }

  /**
   * Runs one task item's body against its (already transformed) input, returning the body's raw
   * output document and its flow directive. The data-flow pipeline is strictly outside this method.
   */
  private Body dispatchBody(
      WorkflowContext ctx, Task task, String name, JsonNode data, ObjectMapper mapper) {
    if (task.getSwitchTask() != null) {
      EvaluateSwitchRequest req = new EvaluateSwitchRequest(name, data);
      FlowOutcome then =
          ctx.callActivity(
                  EvaluateSwitchActivity.class.getName(),
                  req,
                  WorkflowSupport.defaultTaskOptions(),
                  FlowOutcome.class)
              .await();
      return new Body(data, then);
    } else if (task.getCallTask() != null) {
      CallRequest req = new CallRequest(TaskNaming.toKebabCase(name), "run", data);
      JsonNode next =
          ctx.callActivity(
                  CallServiceActivity.class.getName(),
                  req,
                  WorkflowSupport.defaultTaskOptions(),
                  JsonNode.class)
              .await();
      return new Body(next, FlowOutcome.of(thenOf(task.getCallTask().get())));
    } else if (task.getRunTask() != null) {
      CallRequest req = new CallRequest(TaskNaming.toKebabCase(name), "run", data);
      JsonNode next =
          ctx.callActivity(
                  CallServiceActivity.class.getName(),
                  req,
                  WorkflowSupport.defaultTaskOptions(),
                  JsonNode.class)
              .await();
      return new Body(next, FlowOutcome.of(task.getRunTask().getThen()));
    } else if (task.getSetTask() != null) {
      EvaluateSetRequest req = new EvaluateSetRequest(name, data);
      JsonNode next =
          ctx.callActivity(
                  EvaluateSetActivity.class.getName(),
                  req,
                  WorkflowSupport.defaultTaskOptions(),
                  JsonNode.class)
              .await();
      return new Body(next, FlowOutcome.of(task.getSetTask().getThen()));
    } else if (task.getWaitTask() != null) {
      ctx.createTimer(durationOf(task.getWaitTask().getWait())).await();
      return new Body(data, FlowOutcome.of(task.getWaitTask().getThen()));
    } else if (task.getListenTask() != null) {
      JsonNode event =
          ctx.waitForExternalEvent(name, DEFAULT_LISTEN_TIMEOUT, JsonNode.class).await();
      return new Body(
          mergeObjects(data, event, mapper), FlowOutcome.of(task.getListenTask().getThen()));
    } else if (task.getEmitTask() != null) {
      EmitRequest req =
          new EmitRequest(WorkflowSupport.defaultPubsub(), TaskNaming.toKebabCase(name), data);
      ctx.callActivity(
              EmitEventActivity.class.getName(),
              req,
              WorkflowSupport.defaultTaskOptions(),
              Void.class)
          .await();
      return new Body(data, FlowOutcome.of(task.getEmitTask().getThen()));
    } else if (task.getForTask() != null || task.getTryTask() != null) {
      throw new UnsupportedOperationException(
          "task '" + name + "' uses for/try, which is recognised but not yet interpreted");
    } else {
      throw new IllegalStateException("task '" + name + "' has an unsupported type");
    }
  }

  /** The DSL task-type name used in {@code io.dws.task.*} event payloads. */
  private String taskTypeOf(Task task) {
    if (task.getSwitchTask() != null) {
      return "switch";
    } else if (task.getCallTask() != null) {
      return "call";
    } else if (task.getRunTask() != null) {
      return "run";
    } else if (task.getSetTask() != null) {
      return "set";
    } else if (task.getWaitTask() != null) {
      return "wait";
    } else if (task.getListenTask() != null) {
      return "listen";
    } else if (task.getEmitTask() != null) {
      return "emit";
    } else if (task.getForTask() != null) {
      return "for";
    } else if (task.getTryTask() != null) {
      return "try";
    }
    return "unknown";
  }

  /** Publishes a lifecycle event through the admin-event activity (tolerant of publish failure). */
  private void publish(WorkflowContext ctx, AdminEventRequest req) {
    ctx.callActivity(
            AdminEventActivity.class.getName(),
            req,
            WorkflowSupport.defaultTaskOptions(),
            Void.class)
        .await();
  }

  /** Resolves the next program counter from a flow outcome (null = sequential continue). */
  private int advance(FlowOutcome then, int pc, Map<String, Integer> indexByName) {
    if (then == null) {
      return pc + 1;
    }
    FlowDirectiveEnum keyword = then.directive();
    if (keyword != null) {
      return switch (keyword) {
        case CONTINUE -> pc + 1;
        case END, EXIT -> COMPLETE;
      };
    }
    String target = then.target();
    if (target != null && !target.isBlank()) {
      Integer next = indexByName.get(target);
      if (next == null) {
        throw new IllegalStateException("flow references undefined task '" + target + "'");
      }
      return next;
    }
    return pc + 1;
  }

  /**
   * Reads the flow directive from any task that is a {@link TaskBase} (including call variants).
   */
  private FlowDirective thenOf(Object concreteTask) {
    return (concreteTask instanceof TaskBase base) ? base.getThen() : null;
  }

  /** Converts an Open Workflow Specification inline/ISO-8601 duration to a {@link Duration}. */
  private Duration durationOf(TimeoutAfter after) {
    if (after == null) {
      return Duration.ZERO;
    }
    DurationInline inline = after.getDurationInline();
    if (inline != null) {
      return Duration.ofDays(inline.getDays())
          .plusHours(inline.getHours())
          .plusMinutes(inline.getMinutes())
          .plusSeconds(inline.getSeconds());
    }
    String literal =
        after.getDurationExpression() != null
            ? after.getDurationExpression()
            : after.getDurationLiteral();
    return (literal != null && !literal.isBlank()) ? Duration.parse(literal) : Duration.ZERO;
  }

  /**
   * Overlays {@code overlay} onto a copy of {@code base} when both are objects; else returns
   * overlay.
   */
  private JsonNode mergeObjects(JsonNode base, JsonNode overlay, ObjectMapper mapper) {
    if (overlay == null || overlay.isNull()) {
      return base;
    }
    if (base == null || !base.isObject() || !overlay.isObject()) {
      return overlay;
    }
    ObjectNode merged = base.deepCopy();
    java.util.Iterator<String> names = overlay.fieldNames();
    while (names.hasNext()) {
      String field = names.next();
      merged.set(field, overlay.get(field));
    }
    return merged;
  }
}
