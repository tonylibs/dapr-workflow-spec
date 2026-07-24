package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowStub;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.activity.AdminEventActivity;
import io.dws.orchestrator.workflow.activity.AdminEventRequest;
import io.dws.orchestrator.workflow.activity.CallRequest;
import io.dws.orchestrator.workflow.activity.CallServiceActivity;
import io.dws.orchestrator.workflow.activity.EmitEventActivity;
import io.dws.orchestrator.workflow.activity.EmitRequest;
import io.dws.orchestrator.workflow.adapter.TaskNaming;
import io.serverlessworkflow.api.types.DurationInline;
import io.serverlessworkflow.api.types.FlowDirective;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Set;
import io.serverlessworkflow.api.types.SetTask;
import io.serverlessworkflow.api.types.SetTaskConfiguration;
import io.serverlessworkflow.api.types.SwitchCase;
import io.serverlessworkflow.api.types.SwitchItem;
import io.serverlessworkflow.api.types.SwitchTask;
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
 * <p>Effectful steps go through Dapr activities (CALL via service invocation, EMIT via pub/sub);
 * pure steps (SWITCH, SET) run inline via {@link JqEvaluator}; WAIT/LISTEN use the context's timer
 * and external-event primitives. A {@code call} task's target app-id is the task name in kebab-case
 * (see {@link TaskNaming}).
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
    JqEvaluator jq = WorkflowSupport.jq();
    List<TaskItem> items = WorkflowSupport.definition().getDo();

    // All timestamps/ids for lifecycle events come from this builder (workflow-context sourced),
    // so publishing stays replay-deterministic — no Instant.now()/UUID.randomUUID() in execute.
    AdminEventBuilder events = AdminEventBuilder.forContext(ctx);

    JsonNode data = ctx.getInput(JsonNode.class);
    if (data == null) {
      data = mapper.createObjectNode();
    }

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
        FlowDirective then;
        try {
          Dispatch result = dispatch(ctx, task, name, data, jq, mapper);
          data = result.data();
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

  /** The post-dispatch data document plus the task's flow directive. */
  private record Dispatch(JsonNode data, FlowDirective then) {}

  /**
   * Dispatches one task item, returning the (possibly new) data document and its flow directive.
   */
  private Dispatch dispatch(
      WorkflowContext ctx,
      Task task,
      String name,
      JsonNode data,
      JqEvaluator jq,
      ObjectMapper mapper) {
    if (task.getSwitchTask() != null) {
      return new Dispatch(data, evaluateSwitch(task.getSwitchTask(), data, jq));
    } else if (task.getCallTask() != null) {
      CallRequest req = new CallRequest(TaskNaming.toKebabCase(name), "run", data);
      JsonNode next =
          ctx.callActivity(
                  CallServiceActivity.class.getName(),
                  req,
                  WorkflowSupport.defaultTaskOptions(),
                  JsonNode.class)
              .await();
      return new Dispatch(next, thenOf(task.getCallTask().get()));
    } else if (task.getSetTask() != null) {
      return new Dispatch(
          applySet(task.getSetTask(), data, jq, mapper), task.getSetTask().getThen());
    } else if (task.getWaitTask() != null) {
      ctx.createTimer(durationOf(task.getWaitTask().getWait())).await();
      return new Dispatch(data, task.getWaitTask().getThen());
    } else if (task.getListenTask() != null) {
      JsonNode event =
          ctx.waitForExternalEvent(name, DEFAULT_LISTEN_TIMEOUT, JsonNode.class).await();
      return new Dispatch(mergeObjects(data, event, mapper), task.getListenTask().getThen());
    } else if (task.getEmitTask() != null) {
      EmitRequest req =
          new EmitRequest(WorkflowSupport.defaultPubsub(), TaskNaming.toKebabCase(name), data);
      ctx.callActivity(
              EmitEventActivity.class.getName(),
              req,
              WorkflowSupport.defaultTaskOptions(),
              Void.class)
          .await();
      return new Dispatch(data, task.getEmitTask().getThen());
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

  /** Resolves the next program counter from a flow directive (null = sequential continue). */
  private int advance(FlowDirective then, int pc, Map<String, Integer> indexByName) {
    if (then == null) {
      return pc + 1;
    }
    FlowDirectiveEnum keyword = then.getFlowDirectiveEnum();
    if (keyword != null) {
      return switch (keyword) {
        case CONTINUE -> pc + 1;
        case END, EXIT -> COMPLETE;
      };
    }
    String target = then.getString();
    if (target != null && !target.isBlank()) {
      Integer next = indexByName.get(target);
      if (next == null) {
        throw new IllegalStateException("flow references undefined task '" + target + "'");
      }
      return next;
    }
    return pc + 1;
  }

  /** Returns the target of the first case whose {@code when} is truthy, else the default case. */
  private FlowDirective evaluateSwitch(SwitchTask switchTask, JsonNode data, JqEvaluator jq) {
    FlowDirective defaultThen = null;
    for (SwitchItem item : switchTask.getSwitch()) {
      SwitchCase branch = item.getSwitchCase();
      String when = branch.getWhen();
      if (when == null || when.isBlank()) {
        defaultThen = branch.getThen();
      } else if (jq.evaluateBoolean(when, data)) {
        return branch.getThen();
      }
    }
    return defaultThen;
  }

  /**
   * Reads the flow directive from any task that is a {@link TaskBase} (including call variants).
   */
  private FlowDirective thenOf(Object concreteTask) {
    return (concreteTask instanceof TaskBase base) ? base.getThen() : null;
  }

  /** Produces a new data document with each {@code set} entry evaluated over the original data. */
  private JsonNode applySet(SetTask setTask, JsonNode data, JqEvaluator jq, ObjectMapper mapper) {
    ObjectNode result =
        (data != null && data.isObject()) ? data.deepCopy() : mapper.createObjectNode();
    Set set = setTask.getSet();
    if (set == null) {
      return result;
    }
    SetTaskConfiguration cfg = set.getSetTaskConfiguration();
    if (cfg != null && cfg.getAdditionalProperties() != null) {
      for (Map.Entry<String, Object> entry : cfg.getAdditionalProperties().entrySet()) {
        result.set(entry.getKey(), evalSetValue(entry.getValue(), data, jq, mapper));
      }
      return result;
    }
    if (set.getString() != null) {
      JsonNode whole = jq.evaluate(set.getString(), data);
      return (whole != null && whole.isObject()) ? whole : result;
    }
    return result;
  }

  private JsonNode evalSetValue(Object value, JsonNode data, JqEvaluator jq, ObjectMapper mapper) {
    if (value instanceof String expr) {
      return jq.evaluate(expr, data);
    }
    return mapper.valueToTree(value);
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
