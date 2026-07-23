package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowStub;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.model.SwitchCase;
import io.dws.orchestrator.model.TaskDef;
import io.dws.orchestrator.model.WorkflowDef;
import io.dws.orchestrator.workflow.activity.CallRequest;
import io.dws.orchestrator.workflow.activity.CallServiceActivity;
import io.dws.orchestrator.workflow.activity.EmitEventActivity;
import io.dws.orchestrator.workflow.activity.EmitRequest;

import java.time.Duration;
import java.util.Map;

/**
 * The single, generic workflow. Rather than being generated per definition, it interprets
 * a {@link WorkflowDef} as a program-counter loop over named tasks. The definition version
 * is pinned in {@link WorkflowInput}, so replays always resolve the identical definition and
 * the loop is deterministic.
 *
 * <p>Effectful steps (CALL, EMIT) go through Dapr activities; pure steps (SWITCH, SET) run
 * inline via {@link JqEvaluator}; durable-time and external-event steps (WAIT, LISTEN) use the
 * context's timer and event primitives.
 */
public class InterpreterWorkflow implements Workflow {

  /** Guard against definitions that loop forever between tasks. */
  private static final int MAX_STEPS = 10_000;

  /** Flow directive that terminates the workflow. */
  private static final String END_DIRECTIVE = "end";

  /** Fallback wait for a LISTEN task that does not specify a timeout. */
  private static final Duration DEFAULT_LISTEN_TIMEOUT = Duration.ofDays(1);

  @Override
  public WorkflowStub create() {
    return this::execute;
  }

  /**
   * Runs the interpreter loop. Extracted from the {@link WorkflowStub} lambda so it can be
   * driven directly against a mocked {@link WorkflowContext} in tests.
   */
  public void execute(WorkflowContext ctx) {
    WorkflowInput input = ctx.getInput(WorkflowInput.class);
    WorkflowDef def = WorkflowSupport.registry()
        .find(input.workflowName(), input.version())
        .orElseThrow(() -> new IllegalStateException(
            "no definition for " + input.workflowName() + "@" + input.version()));

    JqEvaluator jq = WorkflowSupport.jq();
    ObjectMapper mapper = WorkflowSupport.mapper();

    JsonNode data = input.data() == null ? mapper.createObjectNode() : input.data();
    String cursor = def.start();

    for (int step = 0; cursor != null; step++) {
      if (step > MAX_STEPS) {
        throw new IllegalStateException("workflow exceeded " + MAX_STEPS + " steps; check for a definition loop");
      }
      if (END_DIRECTIVE.equalsIgnoreCase(cursor)) {
        ctx.complete(data);
        return;
      }

      TaskDef task = def.task(cursor);
      if (task == null) {
        throw new IllegalStateException("flow references undefined task '" + cursor + "'");
      }

      switch (task.type()) {
        case CALL -> {
          CallRequest req = new CallRequest(task.service(), task.pathOrDefault(), data);
          data = ctx.callActivity(CallServiceActivity.class.getName(), req,
              WorkflowSupport.defaultTaskOptions(), JsonNode.class).await();
          cursor = task.flowTarget();
        }
        case SWITCH -> cursor = evaluateSwitch(task, data, jq);
        case SET -> {
          data = applySet(task, data, jq, mapper);
          cursor = task.flowTarget();
        }
        case WAIT -> {
          ctx.createTimer(Duration.parse(task.duration())).await();
          cursor = task.flowTarget();
        }
        case LISTEN -> {
          Duration timeout = task.timeout() == null ? DEFAULT_LISTEN_TIMEOUT : Duration.parse(task.timeout());
          JsonNode event = ctx.waitForExternalEvent(task.event(), timeout, JsonNode.class).await();
          data = mergeObjects(data, event, mapper);
          cursor = task.flowTarget();
        }
        case EMIT -> {
          String pubsub = task.pubsub() == null ? WorkflowSupport.defaultPubsub() : task.pubsub();
          EmitRequest req = new EmitRequest(pubsub, task.topic(), data);
          ctx.callActivity(EmitEventActivity.class.getName(), req,
              WorkflowSupport.defaultTaskOptions(), Void.class).await();
          cursor = task.flowTarget();
        }
        case END -> {
          ctx.complete(data);
          return;
        }
        case FOR, TRY -> throw new UnsupportedOperationException(
            "task type " + task.type() + " is recognised but not yet interpreted");
      }
    }

    // A null flow target ends the workflow with the current data.
    ctx.complete(data);
  }

  /** Returns the target of the first case whose {@code when} is truthy, else the default. */
  private String evaluateSwitch(TaskDef task, JsonNode data, JqEvaluator jq) {
    if (task.cases() != null) {
      for (SwitchCase branch : task.cases()) {
        if (branch.when() != null && jq.evaluateBoolean(branch.when(), data)) {
          return branch.then();
        }
      }
    }
    return task.defaultCase();
  }

  /** Produces a new data document with each {@code set} entry evaluated over the original data. */
  private JsonNode applySet(TaskDef task, JsonNode data, JqEvaluator jq, ObjectMapper mapper) {
    ObjectNode result = data != null && data.isObject()
        ? data.deepCopy()
        : mapper.createObjectNode();
    if (task.set() != null) {
      for (Map.Entry<String, String> entry : task.set().entrySet()) {
        result.set(entry.getKey(), jq.evaluate(entry.getValue(), data));
      }
    }
    return result;
  }

  /** Overlays {@code overlay} onto a copy of {@code base} when both are objects; else returns overlay. */
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
