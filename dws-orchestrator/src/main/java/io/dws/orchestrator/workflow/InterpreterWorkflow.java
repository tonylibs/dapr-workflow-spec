package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowStub;
import io.dws.orchestrator.workflow.activity.*;
import io.dws.orchestrator.workflow.adapter.TaskNaming;
import io.serverlessworkflow.api.types.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import one.util.streamex.StreamEx;

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
    return StreamEx.of(
            task.getSwitchTask(),
            task.getCallTask(),
            task.getRunTask(),
            task.getSetTask(),
            task.getWaitTask(),
            task.getListenTask(),
            task.getEmitTask(),
            task.getForTask(),
            task.getTryTask())
        .nonNull()
        .map(concreteTask -> dispatchConcreteTask(ctx, concreteTask, name, data, mapper))
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("task '" + name + "' has an unsupported type"));
  }

  /**
   * Executes the selected task exactly once, returning its raw output and resolved flow outcome.
   */
  private Body dispatchConcreteTask(
      WorkflowContext ctx, Object concreteTask, String name, JsonNode data, ObjectMapper mapper) {
    return switch (concreteTask) {
      case SwitchTask _ ->
          ctx.callActivity(
                  EvaluateSwitchActivity.class.getName(),
                  new EvaluateSwitchRequest(name, data),
                  WorkflowSupport.defaultTaskOptions(),
                  FlowOutcome.class)
              .thenApply(then -> new Body(data, then))
              .await();
      case CallTask callTask ->
          ctx.callActivity(
                  CallServiceActivity.class.getName(),
                  new CallRequest(TaskNaming.toKebabCase(name), "run", data),
                  WorkflowSupport.defaultTaskOptions(),
                  JsonNode.class)
              .thenApply(next -> new Body(next, FlowOutcome.of(thenOf(callTask.get()))))
              .await();
      case RunTask runTask ->
          ctx.callActivity(
                  CallServiceActivity.class.getName(),
                  new CallRequest(TaskNaming.toKebabCase(name), "run", data),
                  WorkflowSupport.defaultTaskOptions(),
                  JsonNode.class)
              .thenApply(next -> new Body(next, FlowOutcome.of(runTask.getThen())))
              .await();
      case SetTask setTask ->
          ctx.callActivity(
                  EvaluateSetActivity.class.getName(),
                  new EvaluateSetRequest(name, data),
                  WorkflowSupport.defaultTaskOptions(),
                  JsonNode.class)
              .thenApply(next -> new Body(next, FlowOutcome.of(setTask.getThen())))
              .await();
      case WaitTask waitTask ->
          ctx.createTimer(durationOf(waitTask.getWait()))
              .thenApply(ignored -> new Body(data, FlowOutcome.of(waitTask.getThen())))
              .await();
      case ListenTask listenTask ->
          ctx.waitForExternalEvent(name, DEFAULT_LISTEN_TIMEOUT, JsonNode.class)
              .thenApply(
                  event ->
                      new Body(
                          mergeObjects(data, event, mapper), FlowOutcome.of(listenTask.getThen())))
              .await();
      case EmitTask emitTask ->
          ctx.callActivity(
                  EmitEventActivity.class.getName(),
                  new EmitRequest(
                      WorkflowSupport.defaultPubsub(), TaskNaming.toKebabCase(name), data),
                  WorkflowSupport.defaultTaskOptions(),
                  Void.class)
              .thenApply(ignored -> new Body(data, FlowOutcome.of(emitTask.getThen())))
              .await();
      case ForTask forTask ->
          throw new UnsupportedOperationException(
              "task '" + name + "' uses for/try, which is recognised but not yet interpreted");
      case TryTask tryTask ->
          throw new UnsupportedOperationException(
              "task '" + name + "' uses for/try, which is recognised but not yet interpreted");
      default -> throw new IllegalStateException("task '" + name + "' has an unsupported type");
    };
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
