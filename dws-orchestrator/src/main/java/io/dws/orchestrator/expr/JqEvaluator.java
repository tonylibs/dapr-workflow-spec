package io.dws.orchestrator.expr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Version;
import net.thisptr.jackson.jq.Versions;
import net.thisptr.jackson.jq.exception.JsonQueryException;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates Serverless Workflow runtime expressions using the jq dialect (jackson-jq).
 *
 * <p>Expressions may be written wrapped as {@code ${ .foo }} (the DSL convention) or as a
 * bare jq program {@code .foo}; both forms are accepted. Evaluation is pure and
 * deterministic, so it is safe to run inline inside a workflow (no I/O, replay-safe).
 */
public class JqEvaluator {

  private static final Version JQ_VERSION = Versions.JQ_1_6;

  private final ObjectMapper mapper;
  private final Scope rootScope;

  public JqEvaluator(ObjectMapper mapper) {
    this.mapper = mapper;
    this.rootScope = Scope.newEmptyScope();
    BuiltinFunctionLoader.getInstance().loadFunctions(JQ_VERSION, rootScope);
  }

  /** Returns the first result of {@code expression} applied to {@code input} (NullNode if none). */
  public JsonNode evaluate(String expression, JsonNode input) {
    List<JsonNode> results = evaluateAll(expression, input);
    return results.isEmpty() ? NullNode.getInstance() : results.get(0);
  }

  /** jq truthiness of the first result: everything except {@code null} and {@code false} is true. */
  public boolean evaluateBoolean(String expression, JsonNode input) {
    JsonNode result = evaluate(expression, input);
    if (result == null || result.isNull()) {
      return false;
    }
    if (result.isBoolean()) {
      return result.booleanValue();
    }
    return true;
  }

  private List<JsonNode> evaluateAll(String expression, JsonNode input) {
    String program = unwrap(expression);
    JsonNode in = input == null ? NullNode.getInstance() : input;
    try {
      JsonQuery query = JsonQuery.compile(program, JQ_VERSION);
      List<JsonNode> out = new ArrayList<>();
      Scope childScope = Scope.newChildScope(rootScope);
      query.apply(childScope, in, out::add);
      return out;
    } catch (JsonQueryException e) {
      throw new ExpressionException("failed to evaluate jq expression: " + program, e);
    }
  }

  /** Strips the {@code ${ ... }} DSL wrapper if present; otherwise returns the input trimmed. */
  static String unwrap(String expression) {
    if (expression == null) {
      throw new ExpressionException("expression must not be null");
    }
    String trimmed = expression.trim();
    if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
      return trimmed.substring(2, trimmed.length() - 1).trim();
    }
    return trimmed;
  }

  ObjectMapper mapper() {
    return mapper;
  }

  /** Thrown when a runtime expression cannot be compiled or evaluated. */
  public static class ExpressionException extends RuntimeException {
    public ExpressionException(String message) {
      super(message);
    }

    public ExpressionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
