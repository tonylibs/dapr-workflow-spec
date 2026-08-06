package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.types.Error;
import io.serverlessworkflow.api.types.ErrorDetails;
import io.serverlessworkflow.api.types.ErrorInstance;
import io.serverlessworkflow.api.types.ErrorTitle;
import io.serverlessworkflow.api.types.ErrorType;
import io.serverlessworkflow.api.types.RaiseTask;
import io.serverlessworkflow.api.types.RaiseTaskError;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.UriTemplate;
import io.serverlessworkflow.api.types.UseErrors;
import java.util.Map;

/**
 * Resolves a RAISE task's configured error into the DSL's five-field runtime error object. Pure jq
 * evaluation with no I/O — like {@link EvaluateSetActivity} it runs in the orchestrator's own JVM
 * (no network hop, nothing extra deployed) and exists as an activity purely so evaluation stays out
 * of the workflow method's replay loop.
 *
 * <p>It <em>returns</em> the resolved object rather than throwing {@link
 * io.dws.orchestrator.error.RaisedErrorException} itself. The caller folds the already-recorded
 * result into that exception once the activity has completed, which keeps a genuine evaluation
 * failure here — a malformed jq expression in {@code detail}, say — an ordinary activity failure
 * instead of something indistinguishable from the raised error it was asked to produce.
 *
 * <p>Each of {@code type}/{@code instance}/{@code title}/{@code detail} is a one-of over a literal
 * and an expression, and the SDK's deserializer has already decided which: a {@code ${ ...
 * }}-wrapped string populates the expression accessor, a plain string the literal one. So this
 * class only asks which accessor is set — it never re-inspects the string the way {@code set} must.
 * {@code status} has no expression variant in the pinned SDK and is therefore used verbatim.
 */
public class RaiseErrorActivity implements WorkflowActivity {

  @Override
  public Object run(WorkflowActivityContext ctx) {
    return apply(ctx.getInput(RaiseErrorRequest.class));
  }

  /** Resolves the task's error definition and evaluates its fields over the task's data. */
  public static ObjectNode apply(RaiseErrorRequest request) {
    Task task = DefinitionLookup.taskByName(request.taskName());
    RaiseTask raiseTask = task.getRaiseTask();
    if (raiseTask == null) {
      throw new IllegalStateException("task '" + request.taskName() + "' is not a raise task");
    }

    Error error = resolveError(raiseTask.getRaise().getError());
    JqEvaluator jq = WorkflowSupport.jq();
    ObjectMapper mapper = WorkflowSupport.mapper();
    JsonNode data = request.data();
    Map<String, JsonNode> variables = scope(request.variables());

    ObjectNode resolved = mapper.createObjectNode();
    resolved.put("type", typeOf(error.getType(), data, variables, jq));
    resolved.put("status", error.getStatus());
    resolved.put(
        "instance", instanceOf(error.getInstance(), data, variables, jq, request.taskName()));
    resolved.put("title", titleOf(error.getTitle(), data, variables, jq));
    resolved.put("detail", detailOf(error.getDetail(), data, variables, jq));
    return resolved;
  }

  /**
   * An inline error definition, or one named in the document's {@code use.errors}. An unresolvable
   * name fails loudly rather than raising an empty error — the same stance {@link CatchPolicy}
   * takes for a named retry policy.
   */
  private static Error resolveError(RaiseTaskError raiseError) {
    Error inline = raiseError.getRaiseErrorDefinition();
    if (inline != null) {
      return inline;
    }
    String name = raiseError.getRaiseErrorReference();
    UseErrors errors =
        WorkflowSupport.definition().getUse() == null
            ? null
            : WorkflowSupport.definition().getUse().getErrors();
    Error named =
        (errors == null || errors.getAdditionalProperties() == null)
            ? null
            : errors.getAdditionalProperties().get(name);
    if (named == null) {
      throw new IllegalStateException(
          "error '" + name + "' is not defined in the document's use.errors");
    }
    return named;
  }

  private static String typeOf(
      ErrorType type, JsonNode data, Map<String, JsonNode> variables, JqEvaluator jq) {
    if (type == null) {
      return null;
    }
    if (type.getExpressionErrorType() != null) {
      return jq.evaluate(type.getExpressionErrorType(), data, variables).asText();
    }
    UriTemplate literal = type.getLiteralErrorType();
    if (literal == null) {
      return null;
    }
    return literal.getLiteralUri() != null
        ? literal.getLiteralUri().toString()
        : literal.getLiteralUriTemplate();
  }

  private static String titleOf(
      ErrorTitle title, JsonNode data, Map<String, JsonNode> variables, JqEvaluator jq) {
    if (title == null) {
      return null;
    }
    return title.getExpressionErrorTitle() != null
        ? jq.evaluate(title.getExpressionErrorTitle(), data, variables).asText()
        : title.getLiteralErrorTitle();
  }

  private static String detailOf(
      ErrorDetails detail, JsonNode data, Map<String, JsonNode> variables, JqEvaluator jq) {
    if (detail == null) {
      return null;
    }
    return detail.getExpressionErrorDetails() != null
        ? jq.evaluate(detail.getExpressionErrorDetails(), data, variables).asText()
        : detail.getLiteralErrorDetails();
  }

  /**
   * Honours a declared {@code instance}; falls back to the raising task's location when absent, the
   * same JSON-Pointer-shaped reference {@link io.dws.orchestrator.error.WorkflowErrors#build} sets
   * for an implicitly synthesised error.
   */
  private static String instanceOf(
      ErrorInstance instance,
      JsonNode data,
      Map<String, JsonNode> variables,
      JqEvaluator jq,
      String taskName) {
    if (instance == null) {
      return "/" + taskName;
    }
    if (instance.getExpressionErrorInstance() != null) {
      return jq.evaluate(instance.getExpressionErrorInstance(), data, variables).asText();
    }
    return instance.getLiteralErrorInstance() != null
        ? instance.getLiteralErrorInstance()
        : "/" + taskName;
  }

  /** Scope-local jq bindings for this task, null-tolerant exactly as for a SET task. */
  private static Map<String, JsonNode> scope(Map<String, JsonNode> variables) {
    return variables == null ? Map.of() : variables;
  }
}
