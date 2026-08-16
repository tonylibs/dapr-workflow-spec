package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowStub;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dapr.workflows.WorkflowTaskRetryPolicy;
import io.dws.orchestrator.error.RaisedErrorException;
import io.dws.orchestrator.workflow.activity.*;
import io.dws.orchestrator.workflow.adapter.TaskNaming;
import io.serverlessworkflow.api.types.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  /** Guard against definitions that loop forever between tasks, counted per scope. */
  private static final int MAX_STEPS = 10_000;

  /** Guard against pathologically nested definitions exhausting the call stack. */
  static final int MAX_DEPTH = 16;

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

    JsonNode data =
        Optional.ofNullable(ctx.getInput(JsonNode.class)).orElse(mapper.createObjectNode());

    // The workflow context is a second document, distinct from `data`: `export.as` writes it and it
    // persists for the instance's life. It is threaded through the loop (never stored externally)
    // so replay stays deterministic, and it is not part of the instance's completion output.
    JsonNode context = mapper.createObjectNode();

    publish(ctx, events.instanceStarted());

    try {
      // The top-level `do` is just the outermost scope. Whichever way it ends — ran off the end,
      // `exit`, or `end` — completing the outermost scope is completing the instance.
      ScopeResult result = runTaskList(ctx, items, data, context, Map.of(), 0, events, mapper);
      publish(ctx, events.instanceCompleted());
      ctx.complete(result.data());
    } catch (RuntimeException e) {
      publish(ctx, events.instanceFailed(String.valueOf(e.getMessage())));
      throw e;
    }
  }

  /**
   * Runs one task list as its own scope.
   *
   * <p>The flow-directive index is built from {@code items} alone, which is the DSL's own rule that
   * a directive "may only redirect to tasks declared within their own scope … they cannot target
   * tasks at a different depth". The same routine runs the top-level {@code do} and a try task's
   * {@code try}/{@code catch.do} lists, so a nested task is dispatched — and therefore pipelined
   * and reported — exactly like a top-level one.
   *
   * @param variables scope-local jq variable bindings (the caught error inside a {@code catch.do});
   *     empty at the top level
   * @param depth current nesting depth, 0 for the top-level {@code do}
   */
  ScopeResult runTaskList(
      WorkflowContext ctx,
      List<TaskItem> items,
      JsonNode data,
      JsonNode context,
      Map<String, JsonNode> variables,
      int depth,
      AdminEventBuilder events,
      ObjectMapper mapper) {
    if (depth > MAX_DEPTH) {
      throw new IllegalStateException(
          "workflow exceeded the maximum task nesting depth of " + MAX_DEPTH);
    }
    if (items == null || items.isEmpty()) {
      return new ScopeResult(data, context, ScopeEnd.FELL_THROUGH);
    }

    // Task names are unique across the definition (the controller rejects duplicates), so the
    // first index per name is the only one.
    Map<String, Integer> indexByName = StreamEx.of(items).toMap(TaskItem::getName, items::indexOf);

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
        Dispatch result =
            dispatch(ctx, task, name, data, context, variables, depth, events, mapper);
        data = result.data();
        context = result.context();
        then = result.then();
        if (result.end() == ScopeEnd.END) {
          // A nested scope ended the whole instance: report this task, then unwind immediately.
          publish(ctx, events.taskCompleted(name, taskType));
          return new ScopeResult(data, context, ScopeEnd.END);
        }
      } catch (RuntimeException e) {
        publish(ctx, events.taskFailed(name, taskType, String.valueOf(e.getMessage())));
        throw e;
      }
      publish(ctx, events.taskCompleted(name, taskType));

      FlowDirectiveEnum keyword = then == null ? null : then.directive();
      if (keyword == FlowDirectiveEnum.END) {
        return new ScopeResult(data, context, ScopeEnd.END);
      }
      if (keyword == FlowDirectiveEnum.EXIT) {
        return new ScopeResult(data, context, ScopeEnd.EXIT);
      }
      pc = advance(then, pc, indexByName);
    }

    return new ScopeResult(data, context, ScopeEnd.FELL_THROUGH);
  }

  /**
   * The post-dispatch data and context documents, the task's resolved flow outcome, and — for a
   * task whose body is itself a task scope ({@code try}) — how that inner scope ended.
   */
  private record Dispatch(JsonNode data, JsonNode context, FlowOutcome then, ScopeEnd end) {}

  /**
   * The task body's own result: its raw output document, the context as the body left it, its flow
   * directive, and how the body's own scope ended. Only a {@code try} body can end a scope; every
   * other body returns the incoming context and {@link ScopeEnd#FELL_THROUGH}.
   */
  private record Body(JsonNode data, JsonNode context, FlowOutcome then, ScopeEnd end) {

    /** A leaf body: no nested scope, context untouched. */
    static Body leaf(JsonNode data, JsonNode context, FlowOutcome then) {
      return new Body(data, context, then, ScopeEnd.FELL_THROUGH);
    }
  }

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
      Map<String, JsonNode> variables,
      int depth,
      AdminEventBuilder events,
      ObjectMapper mapper) {
    TaskBase base = DataFlowPipeline.baseOf(task);
    boolean hasInput = base != null && base.getInput() != null;
    boolean hasOutput = base != null && (base.getOutput() != null || base.getExport() != null);

    JsonNode input = data;
    if (hasInput) {
      input =
          ctx.callActivity(
                  DataFlowInputActivity.class.getName(),
                  new DataFlowInputRequest(name, data, context, variables),
                  WorkflowSupport.defaultTaskOptions(),
                  JsonNode.class)
              .await();
    }

    Body body = dispatchBody(ctx, task, name, input, context, variables, depth, events, mapper);

    if (!hasOutput) {
      return new Dispatch(body.data(), body.context(), body.then(), body.end());
    }
    DataFlowResult shaped =
        ctx.callActivity(
                DataFlowOutputActivity.class.getName(),
                new DataFlowOutputRequest(name, body.data(), body.context(), variables),
                WorkflowSupport.defaultTaskOptions(),
                DataFlowResult.class)
            .await();
    return new Dispatch(shaped.data(), shaped.context(), body.then(), body.end());
  }

  /**
   * Runs one task item's body against its (already transformed) input, returning the body's raw
   * output document and its flow directive. The data-flow pipeline is strictly outside this method.
   */
  private Body dispatchBody(
      WorkflowContext ctx,
      Task task,
      String name,
      JsonNode data,
      JsonNode context,
      Map<String, JsonNode> variables,
      int depth,
      AdminEventBuilder events,
      ObjectMapper mapper) {
    return StreamEx.of(
            task.getSwitchTask(),
            task.getCallTask(),
            task.getRunTask(),
            task.getSetTask(),
            task.getWaitTask(),
            task.getListenTask(),
            task.getEmitTask(),
            task.getForTask(),
            task.getTryTask(),
            task.getRaiseTask())
        .nonNull()
        .map(
            concreteTask ->
                dispatchConcreteTask(
                    ctx, concreteTask, name, data, context, variables, depth, events, mapper))
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("task '" + name + "' has an unsupported type"));
  }

  /**
   * Executes the selected task exactly once, returning its raw output and resolved flow outcome.
   *
   * <p>Only a {@code try} body opens a nested scope, so it is the one branch handed the context,
   * scope variables, depth, and event builder; every other branch is a leaf that passes the
   * incoming context straight through.
   */
  private Body dispatchConcreteTask(
      WorkflowContext ctx,
      Object concreteTask,
      String name,
      JsonNode data,
      JsonNode context,
      Map<String, JsonNode> variables,
      int depth,
      AdminEventBuilder events,
      ObjectMapper mapper) {
    return switch (concreteTask) {
      case SwitchTask _ ->
          ctx.callActivity(
                  EvaluateSwitchActivity.class.getName(),
                  new EvaluateSwitchRequest(name, data, variables),
                  WorkflowSupport.defaultTaskOptions(),
                  FlowOutcome.class)
              .thenApply(then -> Body.leaf(data, context, then))
              .await();
      case CallTask callTask -> {
        // Split by call sub-kind, the same distinction the controller's compiler makes:
        // call: http is hosted by a Go activity worker (multi-app dispatch), while call: openapi
        // stays on the HTTP service-invocation path its Node image serves.
        FlowOutcome then = FlowOutcome.of(thenOf(callTask.get()));
        yield callTask.getCallHTTP() != null
            ? dispatchStepActivity(ctx, name, data, context, then)
            : invokeStepService(ctx, name, data, context, then);
      }
      case RunTask runTask ->
          // Both run: shell and run: script are Go activity workers; run: container / run: workflow
          // are rejected at compile time and never reach the orchestrator.
          dispatchStepActivity(ctx, name, data, context, FlowOutcome.of(runTask.getThen()));
      case SetTask setTask ->
          ctx.callActivity(
                  EvaluateSetActivity.class.getName(),
                  new EvaluateSetRequest(name, data, variables),
                  WorkflowSupport.defaultTaskOptions(),
                  JsonNode.class)
              .thenApply(next -> Body.leaf(next, context, FlowOutcome.of(setTask.getThen())))
              .await();
      case WaitTask waitTask ->
          ctx.createTimer(durationOf(waitTask.getWait()))
              .thenApply(ignored -> Body.leaf(data, context, FlowOutcome.of(waitTask.getThen())))
              .await();
      case ListenTask listenTask ->
          ctx.waitForExternalEvent(name, DEFAULT_LISTEN_TIMEOUT, JsonNode.class)
              .thenApply(
                  event ->
                      Body.leaf(
                          mergeObjects(data, event, mapper),
                          context,
                          FlowOutcome.of(listenTask.getThen())))
              .await();
      case EmitTask emitTask ->
          ctx.callActivity(
                  EmitEventActivity.class.getName(),
                  new EmitRequest(
                      WorkflowSupport.defaultPubsub(), TaskNaming.toKebabCase(name), data),
                  WorkflowSupport.defaultTaskOptions(),
                  Void.class)
              .thenApply(ignored -> Body.leaf(data, context, FlowOutcome.of(emitTask.getThen())))
              .await();
      case TryTask tryTask ->
          dispatchTry(ctx, tryTask, name, data, context, variables, depth, events, mapper);
      case RaiseTask _ ->
          ctx.callActivity(
                  RaiseErrorActivity.class.getName(),
                  new RaiseErrorRequest(name, data, variables),
                  WorkflowSupport.defaultTaskOptions(),
                  JsonNode.class)
              .thenApply(InterpreterWorkflow::raiseError)
              .await();
      case ForTask forTask ->
          dispatchFor(ctx, forTask, name, data, context, variables, depth, events, mapper);
      default -> throw new IllegalStateException("task '" + name + "' has an unsupported type");
    };
  }

  /**
   * Dispatches a migrated I/O step ({@code call: http}, {@code run: shell}, {@code run: script}) as
   * a Dapr multi-app activity: it schedules the canonical {@link StepActivity#NAME} activity
   * against the task's app-id ({@link TaskNaming#toKebabCase}), passing the current data as the
   * activity input and carrying the default retry policy in the options so retry behaviour matches
   * the HTTP path. A {@code null}/empty result leaves the data document unchanged.
   */
  private Body dispatchStepActivity(
      WorkflowContext ctx, String name, JsonNode data, JsonNode context, FlowOutcome then) {
    WorkflowTaskRetryPolicy retryPolicy = WorkflowSupport.defaultTaskOptions().getRetryPolicy();
    WorkflowTaskOptions options =
        new WorkflowTaskOptions(retryPolicy, TaskNaming.toKebabCase(name));
    return ctx.callActivity(StepActivity.NAME, data, options, JsonNode.class)
        .thenApply(next -> Body.leaf(next == null ? data : next, context, then))
        .await();
  }

  /**
   * Invokes a step over Dapr service invocation via {@link CallServiceActivity} ({@code POST
   * /run}). This is the unmigrated path {@code call: openapi} still takes — its Node image is not
   * an activity worker (the JS Workflow SDK lacks multi-app activities).
   */
  private Body invokeStepService(
      WorkflowContext ctx, String name, JsonNode data, JsonNode context, FlowOutcome then) {
    return ctx.callActivity(
            CallServiceActivity.class.getName(),
            new CallRequest(TaskNaming.toKebabCase(name), "run", data),
            WorkflowSupport.defaultTaskOptions(),
            JsonNode.class)
        .thenApply(next -> Body.leaf(next, context, then))
        .await();
  }

  /**
   * Fails the task with the error the raise activity resolved.
   *
   * <p>The throw happens here rather than inside the activity so that it is driven by a value Dapr
   * has already recorded, which keeps it deterministic on replay — and so that a genuine evaluation
   * failure inside the activity stays an ordinary activity failure instead of masquerading as the
   * raised error. From this point the error is an ordinary {@link RuntimeException}, so the
   * existing task-failure path and {@link #dispatchTry}'s catch block handle it with no special
   * casing.
   *
   * <p>Declared to return {@link Body} only to satisfy {@code thenApply}'s functional type; it
   * never returns normally.
   */
  private static Body raiseError(JsonNode error) {
    throw new RaisedErrorException(error);
  }

  /**
   * Runs a try task: attempt its body, and on failure ask the catch clause what to do.
   *
   * <p>Each attempt re-runs the <em>whole</em> try list against the try task's original input, not
   * against whatever partial data the failed attempt produced. The retry policy belongs to the try
   * task rather than to any inner task, so a retry is a fresh attempt at the block — which also
   * means a side-effecting task early in the block re-executes. That is the author's lever: the
   * block boundary is theirs to draw.
   *
   * <p>All impurity (the jitter draw, the elapsed-time arithmetic) lives in the decision activity;
   * the clock values it needs come from the workflow context's replay-safe instant.
   */
  private Body dispatchTry(
      WorkflowContext ctx,
      TryTask tryTask,
      String name,
      JsonNode data,
      JsonNode context,
      Map<String, JsonNode> variables,
      int depth,
      AdminEventBuilder events,
      ObjectMapper mapper) {
    long firstFailureMillis = 0L;

    for (int attempt = 1; ; attempt++) {
      try {
        ScopeResult body =
            runTaskList(ctx, tryTask.getTry(), data, context, variables, depth + 1, events, mapper);
        return new Body(body.data(), body.context(), FlowOutcome.of(tryTask.getThen()), body.end());
      } catch (RuntimeException failure) {
        long now = ctx.getCurrentInstant().toEpochMilli();
        if (attempt == 1) {
          firstFailureMillis = now;
        }

        CatchDecision decision =
            ctx.callActivity(
                    CatchDecisionActivity.class.getName(),
                    new CatchDecisionRequest(
                        name,
                        name,
                        String.valueOf(failure.getMessage()),
                        attempt,
                        firstFailureMillis,
                        now,
                        data,
                        context),
                    WorkflowSupport.defaultTaskOptions(),
                    CatchDecision.class)
                .await();

        if (!decision.caught()) {
          // Not handled here: propagate the original failure untouched, so it reaches the standard
          // task-failure path carrying its own detail rather than a rewritten one.
          throw failure;
        }
        if (decision.retry()) {
          ctx.createTimer(Duration.ofMillis(decision.delayMillis())).await();
          continue;
        }
        return recover(ctx, tryTask, data, context, decision, variables, depth, events, mapper);
      }
    }
  }

  /**
   * Runs a for task: evaluate the collection once, then run the body once per element with the
   * iteration variables bound as scope-local jq variables. When {@code while} is declared it is
   * re-evaluated at the top of each iteration and stops the loop when false; when absent no
   * activity crossing happens per iteration.
   *
   * <p>Iterations thread data forward — iteration N + 1's input data is iteration N's body output.
   * Each iteration's body scope is at {@code depth + 1}; iterations themselves are siblings, so the
   * loop does not consume {@link #MAX_DEPTH}.
   */
  private Body dispatchFor(
      WorkflowContext ctx,
      ForTask forTask,
      String name,
      JsonNode data,
      JsonNode context,
      Map<String, JsonNode> variables,
      int depth,
      AdminEventBuilder events,
      ObjectMapper mapper) {
    FlowOutcome then = FlowOutcome.of(forTask.getThen());

    JsonNode collection =
        ctx.callActivity(
                EvaluateForActivity.class.getName(),
                new EvaluateForRequest(name, data, variables),
                WorkflowSupport.defaultTaskOptions(),
                JsonNode.class)
            .await();

    if (collection == null || collection.isEmpty()) {
      return new Body(data, context, then, ScopeEnd.FELL_THROUGH);
    }

    ForTaskConfiguration config = forTask.getFor();
    String eachName = nameOr(config == null ? null : config.getEach(), "item");
    String atName = nameOr(config == null ? null : config.getAt(), "index");
    boolean hasWhile = forTask.getWhile() != null && !forTask.getWhile().isBlank();

    JsonNode iterationData = data;
    JsonNode iterationContext = context;
    for (int index = 0; index < collection.size(); index++) {
      Map<String, JsonNode> scoped = new HashMap<>(variables);
      scoped.put(eachName, collection.get(index));
      scoped.put(atName, mapper.getNodeFactory().numberNode(index));

      if (hasWhile) {
        boolean keepGoing =
            ctx.callActivity(
                    EvaluateWhileActivity.class.getName(),
                    new EvaluateWhileRequest(name, iterationData, scoped),
                    WorkflowSupport.defaultTaskOptions(),
                    Boolean.class)
                .await();
        if (!keepGoing) {
          return new Body(iterationData, iterationContext, then, ScopeEnd.FELL_THROUGH);
        }
      }

      ScopeResult result =
          runTaskList(
              ctx,
              forTask.getDo(),
              iterationData,
              iterationContext,
              scoped,
              depth + 1,
              events,
              mapper);
      iterationData = result.data();
      iterationContext = result.context();
      if (result.end() == ScopeEnd.END) {
        return new Body(iterationData, iterationContext, then, ScopeEnd.END);
      }
      // ScopeEnd.EXIT completes only this iteration's scope; the loop continues.
    }

    return new Body(iterationData, iterationContext, then, ScopeEnd.FELL_THROUGH);
  }

  /** Returns {@code name} when non-blank, otherwise the default. */
  private static String nameOr(String name, String fallback) {
    return (name == null || name.isBlank()) ? fallback : name;
  }

  /**
   * Runs the catch clause's recovery block, with the caught error bound as a scope-local jq
   * variable under the name {@code catch.as} declares.
   *
   * <p>The binding is threaded down the scope rather than merged into the data document or written
   * to {@code $context}: the recovery block is there to repair the data, and {@code $context}
   * outlives the try task, so either would leak the error past the scope that caught it.
   *
   * <p>A failure inside the recovery block propagates — nothing catches a catch.
   */
  private Body recover(
      WorkflowContext ctx,
      TryTask tryTask,
      JsonNode data,
      JsonNode context,
      CatchDecision decision,
      Map<String, JsonNode> variables,
      int depth,
      AdminEventBuilder events,
      ObjectMapper mapper) {
    TryTaskCatch clause = tryTask.getCatch();
    FlowOutcome then = FlowOutcome.of(tryTask.getThen());
    if (clause == null || clause.getDo() == null || clause.getDo().isEmpty()) {
      // Handled with nothing to run: the try task completes with the data as of the failure.
      return new Body(data, context, then, ScopeEnd.FELL_THROUGH);
    }

    Map<String, JsonNode> scoped = new HashMap<>(variables);
    scoped.put(decision.errorVariable(), decision.error());
    ScopeResult recovered =
        runTaskList(ctx, clause.getDo(), data, context, scoped, depth + 1, events, mapper);
    return new Body(recovered.data(), recovered.context(), then, recovered.end());
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
    } else if (task.getRaiseTask() != null) {
      return "raise";
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

  /**
   * Resolves the next program counter within one scope from a flow outcome (null = sequential
   * continue). {@code end}/{@code exit} never reach here — the caller handles them, because they
   * terminate a scope rather than move within it.
   *
   * <p>{@code indexByName} holds only the current scope's tasks, so a target declared at a
   * different depth is rejected. That is the DSL's own rule, not a limitation.
   */
  private int advance(FlowOutcome then, int pc, Map<String, Integer> indexByName) {
    if (then == null) {
      return pc + 1;
    }
    if (then.directive() == FlowDirectiveEnum.CONTINUE) {
      return pc + 1;
    }
    String target = then.target();
    if (target != null && !target.isBlank()) {
      Integer next = indexByName.get(target);
      if (next == null) {
        throw new IllegalStateException(
            "flow references task '" + target + "', which is not declared in this task scope");
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
