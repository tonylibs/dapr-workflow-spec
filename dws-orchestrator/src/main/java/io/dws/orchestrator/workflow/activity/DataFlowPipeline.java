package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dws.orchestrator.dataflow.DataFlowException;
import io.dws.orchestrator.dataflow.DataFlowException.Phase;
import io.dws.orchestrator.dataflow.SchemaValidator;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.types.CallTask;
import io.serverlessworkflow.api.types.Export;
import io.serverlessworkflow.api.types.Input;
import io.serverlessworkflow.api.types.Output;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskBase;
import java.util.HashMap;
import java.util.Map;

/**
 * Applies a task's Open Workflow Specification data-flow pipeline around its body.
 *
 * <p>The pipeline runs in two phases because the output half needs the body's result: the input
 * phase ({@code input.from} then {@code input.schema}) runs before the body, and the output phase
 * ({@code output.as}, {@code output.schema}, then {@code export.as}/{@code export.schema}) runs
 * after it. Each phase has its own {@code WorkflowActivity} ({@link DataFlowInputActivity}, {@link
 * DataFlowOutputActivity}) because an activity is dispatched by class name and takes one input
 * type; this class holds the logic they share.
 *
 * <p>Both phases are pure jq evaluation and validation with no I/O — like {@link
 * EvaluateSetActivity} they run in the orchestrator's own JVM and exist as activities purely so
 * evaluation stays out of the workflow method's replay loop.
 *
 * <p>Every {@code from}/{@code as} expression sees the workflow context as {@code $context}.
 */
public final class DataFlowPipeline {

  private DataFlowPipeline() {}

  /** Transforms and validates a task's input, returning what the task body should consume. */
  public static JsonNode applyInput(DataFlowInputRequest request) {
    TaskBase base = taskBase(request.taskName());
    JsonNode context = orEmptyObject(request.context());
    JsonNode raw = orNull(request.rawInput());

    Input input = base.getInput();
    if (input == null) {
      return raw;
    }

    JsonNode transformed =
        (input.getFrom() == null)
            ? raw
            : transform(
                input.getFrom().getString(),
                input.getFrom().getObject(),
                raw,
                context,
                request.variables(),
                request.taskName(),
                Phase.INPUT);

    validator().validate(input.getSchema(), transformed, request.taskName(), Phase.INPUT);
    return transformed;
  }

  /**
   * Transforms and validates a task's output, then computes the workflow context this task exports.
   */
  public static DataFlowResult applyOutput(DataFlowOutputRequest request) {
    TaskBase base = taskBase(request.taskName());
    JsonNode context = orEmptyObject(request.context());
    JsonNode raw = orNull(request.rawOutput());

    JsonNode data = raw;
    Output output = base.getOutput();
    if (output != null) {
      if (output.getAs() != null) {
        data =
            transform(
                output.getAs().getString(),
                output.getAs().getObject(),
                raw,
                context,
                request.variables(),
                request.taskName(),
                Phase.OUTPUT);
      }
      validator().validate(output.getSchema(), data, request.taskName(), Phase.OUTPUT);
    }

    // export.as evaluates over the task's *transformed* output, with the pre-export context in
    // scope, and its result replaces the context for the rest of the instance.
    JsonNode exported = context;
    Export export = base.getExport();
    if (export != null) {
      if (export.getAs() != null) {
        exported =
            transform(
                export.getAs().getString(),
                export.getAs().getObject(),
                data,
                context,
                request.variables(),
                request.taskName(),
                Phase.EXPORT);
      }
      validator().validate(export.getSchema(), exported, request.taskName(), Phase.EXPORT);
    }

    return new DataFlowResult(data, exported);
  }

  /**
   * Evaluates one {@code from}/{@code as} transformation in either of its two DSL forms: a bare
   * string (a single jq program over the whole document) or a structured object literal.
   */
  private static JsonNode transform(
      String expression,
      Object literal,
      JsonNode input,
      JsonNode context,
      Map<String, JsonNode> scopeVariables,
      String taskName,
      Phase phase) {
    JqEvaluator jq = WorkflowSupport.jq();
    Map<String, JsonNode> variables = bindings(context, scopeVariables);
    try {
      if (expression != null) {
        return jq.evaluate(expression, input, variables);
      }
      if (literal != null) {
        return jq.evaluateStructured(literal, input, variables);
      }
      return input;
    } catch (JqEvaluator.ExpressionException e) {
      throw new DataFlowException(taskName, phase, e.getMessage(), e);
    }
  }

  /**
   * The jq variables a {@code from}/{@code as} expression sees: always {@code $context}, plus any
   * scope-local bindings the enclosing scope supplies (today only the error caught by an enclosing
   * {@code catch}, named by its {@code catch.as}). A scope binding never shadows {@code $context}
   * silently — {@code context} is written last.
   */
  private static Map<String, JsonNode> bindings(
      JsonNode context, Map<String, JsonNode> scopeVariables) {
    if (scopeVariables == null || scopeVariables.isEmpty()) {
      return Map.of("context", context);
    }
    Map<String, JsonNode> merged = new HashMap<>(scopeVariables);
    merged.put("context", context);
    return merged;
  }

  /**
   * Resolves the {@link TaskBase} carrying {@code input}/{@code output}/{@code export} for any task
   * kind, or {@code null} if the task exposes none. {@code Task.get()} yields the concrete task; a
   * {@code call} task nests one level further (its {@code get()} yields the protocol-specific
   * call), which is the same unwrap the interpreter's flow-directive lookup performs.
   *
   * <p>Public so the interpreter can ask whether a task declares any data flow at all without
   * duplicating this unwrap — it skips both phases entirely when none is declared.
   */
  public static TaskBase baseOf(Task task) {
    Object concrete = task.get();
    if (concrete instanceof CallTask call) {
      concrete = call.get();
    }
    return (concrete instanceof TaskBase base) ? base : null;
  }

  private static TaskBase taskBase(String taskName) {
    TaskBase base = baseOf(DefinitionLookup.taskByName(taskName));
    if (base == null) {
      throw new IllegalStateException(
          "task '" + taskName + "' has no data-flow-capable definition");
    }
    return base;
  }

  private static SchemaValidator validator() {
    return new SchemaValidator(WorkflowSupport.schemaRegistry(), WorkflowSupport.mapper());
  }

  /** The workflow context is never null — an instance starts with an empty object. */
  private static JsonNode orEmptyObject(JsonNode node) {
    return (node == null || node.isNull()) ? WorkflowSupport.mapper().createObjectNode() : node;
  }

  private static JsonNode orNull(JsonNode node) {
    ObjectMapper mapper = WorkflowSupport.mapper();
    return node == null ? mapper.nullNode() : node;
  }
}
