package io.dws.orchestrator.workflow.activity;

import static java.util.function.Predicate.not;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dws.orchestrator.dataflow.DataFlowException;
import io.dws.orchestrator.dataflow.DataFlowException.Phase;
import io.dws.orchestrator.dataflow.SchemaValidator;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.types.CallTask;
import io.serverlessworkflow.api.types.SchemaUnion;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskBase;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

    // The schema is validated whenever `input` is declared, with or without a `from` — so the
    // validation wraps the transform rather than being a step chained after it.
    return Optional.ofNullable(base.getInput())
        .map(
            input ->
                validated(
                    Optional.ofNullable(input.getFrom())
                        .map(
                            from ->
                                transform(
                                    from.getString(),
                                    from.getObject(),
                                    raw,
                                    context,
                                    request.variables(),
                                    request.taskName(),
                                    Phase.INPUT))
                        .orElse(raw),
                    input.getSchema(),
                    request.taskName(),
                    Phase.INPUT))
        .orElse(raw);
  }

  /**
   * Transforms and validates a task's output, then computes the workflow context this task exports.
   */
  public static DataFlowResult applyOutput(DataFlowOutputRequest request) {
    TaskBase base = taskBase(request.taskName());
    JsonNode context = orEmptyObject(request.context());
    JsonNode raw = orNull(request.rawOutput());
    JsonNode data =
        Optional.ofNullable(base.getOutput())
            .map(
                output ->
                    validated(
                        Optional.ofNullable(output.getAs())
                            .map(
                                as ->
                                    transform(
                                        as.getString(),
                                        as.getObject(),
                                        raw,
                                        context,
                                        request.variables(),
                                        request.taskName(),
                                        Phase.OUTPUT))
                            .orElse(raw),
                        output.getSchema(),
                        request.taskName(),
                        Phase.OUTPUT))
            .orElse(raw);

    // export.as evaluates over the task's *transformed* output, with the pre-export context in
    // scope, and its result replaces the context for the rest of the instance.
    JsonNode exported =
        Optional.ofNullable(base.getExport())
            .map(
                export ->
                    validated(
                        Optional.ofNullable(export.getAs())
                            .map(
                                as ->
                                    transform(
                                        as.getString(),
                                        as.getObject(),
                                        data,
                                        context,
                                        request.variables(),
                                        request.taskName(),
                                        Phase.EXPORT))
                            .orElse(context),
                        export.getSchema(),
                        request.taskName(),
                        Phase.EXPORT))
            .orElse(context);

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
      // Safe as a chain because neither evaluator ever returns null (an empty result is NullNode),
      // so `or` falls through to the literal form only when no expression was declared.
      return Optional.ofNullable(expression)
          .map(expr -> jq.evaluate(expr, input, variables))
          .or(
              () ->
                  Optional.ofNullable(literal)
                      .map(li -> jq.evaluateStructured(li, input, variables)))
          .orElse(input);
    } catch (JqEvaluator.ExpressionException e) {
      throw new DataFlowException(taskName, phase, e.getMessage(), e);
    }
  }

  /**
   * Validates a phase's document against its declared schema and hands it back, so validation
   * composes as a step inside a transformation chain instead of sitting beside it as a side effect.
   */
  private static JsonNode validated(
      JsonNode document, SchemaUnion schema, String taskName, Phase phase) {
    validator().validate(schema, document, taskName, phase);
    return document;
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
    // The call unwrap is conditional: every other task kind already *is* the TaskBase.
    return Optional.ofNullable(task.get())
        .map(concrete -> (concrete instanceof CallTask call) ? call.get() : concrete)
        .filter(TaskBase.class::isInstance)
        .map(TaskBase.class::cast)
        .orElse(null);
  }

  private static TaskBase taskBase(String taskName) {

    return Optional.ofNullable(baseOf(DefinitionLookup.taskByName(taskName)))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "task '" + taskName + "' has no data-flow-capable definition"));
  }

  private static SchemaValidator validator() {
    return new SchemaValidator(WorkflowSupport.schemaRegistry(), WorkflowSupport.mapper());
  }

  /** The workflow context is never null — an instance starts with an empty object. */
  private static JsonNode orEmptyObject(JsonNode node) {
    return Optional.ofNullable(node)
        .filter(not(JsonNode::isNull))
        .orElseGet(() -> WorkflowSupport.mapper().createObjectNode());
  }

  private static JsonNode orNull(JsonNode node) {
    ObjectMapper mapper = WorkflowSupport.mapper();
    return Optional.ofNullable(node).orElseGet(mapper::nullNode);
  }
}
