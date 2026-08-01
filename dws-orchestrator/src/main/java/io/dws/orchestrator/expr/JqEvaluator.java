package io.dws.orchestrator.expr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Version;
import net.thisptr.jackson.jq.Versions;
import net.thisptr.jackson.jq.exception.JsonQueryException;

/**
 * Evaluates Open Workflow Specification runtime expressions using the jq dialect (jackson-jq).
 *
 * <p>Expressions may be written wrapped as {@code ${ .foo }} (the DSL convention) or as a bare jq
 * program {@code .foo}; both forms are accepted. Evaluation is pure and deterministic, so it is
 * safe to run inline inside a workflow (no I/O, replay-safe).
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
    return evaluate(expression, input, Map.of());
  }

  /**
   * As {@link #evaluate(String, JsonNode)}, with {@code variables} bound as jq {@code $name}
   * variables. The data-flow pipeline uses this to expose the workflow context as {@code $context}.
   */
  public JsonNode evaluate(String expression, JsonNode input, Map<String, JsonNode> variables) {
    List<JsonNode> results = evaluateAll(expression, input, variables);
    return results.isEmpty() ? NullNode.getInstance() : results.get(0);
  }

  /**
   * Evaluates the object (structured-literal) form of a {@code from}/{@code as} transformation:
   * objects and arrays are rebuilt element-wise, and a string leaf is evaluated as a runtime
   * expression <em>only</em> when it is {@code ${ ... }}-wrapped. An unwrapped string is a literal,
   * as are all non-string scalars.
   *
   * <p>This is deliberately stricter than {@code set}, where every string value is an expression by
   * convention: inside a structured literal a bare {@code "active"} is the string "active", not the
   * jq program {@code active}.
   */
  public JsonNode evaluateStructured(
      Object literal, JsonNode input, Map<String, JsonNode> variables) {
    JsonNode template = (literal instanceof JsonNode node) ? node : mapper.valueToTree(literal);
    return evaluateTemplate(template, input, variables);
  }

  private JsonNode evaluateTemplate(
      JsonNode template, JsonNode input, Map<String, JsonNode> variables) {
    if (template == null || template.isNull()) {
      return NullNode.getInstance();
    }
    if (template.isObject()) {
      ObjectNode result = mapper.createObjectNode();
      Iterator<String> names = template.fieldNames();
      while (names.hasNext()) {
        String field = names.next();
        result.set(field, evaluateTemplate(template.get(field), input, variables));
      }
      return result;
    }
    if (template.isArray()) {
      ArrayNode result = mapper.createArrayNode();
      for (JsonNode element : template) {
        result.add(evaluateTemplate(element, input, variables));
      }
      return result;
    }
    if (template.isTextual() && isWrapped(template.textValue())) {
      return evaluate(template.textValue(), input, variables);
    }
    return template;
  }

  /** True when a string is a {@code ${ ... }}-wrapped runtime expression rather than a literal. */
  static boolean isWrapped(String value) {
    if (value == null) {
      return false;
    }
    String trimmed = value.trim();
    return trimmed.startsWith("${") && trimmed.endsWith("}");
  }

  /**
   * jq truthiness of the first result: everything except {@code null} and {@code false} is true.
   */
  public boolean evaluateBoolean(String expression, JsonNode input) {
    return evaluateBoolean(expression, input, Map.of());
  }

  /** As {@link #evaluateBoolean(String, JsonNode)}, with named jq variables bound. */
  public boolean evaluateBoolean(
      String expression, JsonNode input, Map<String, JsonNode> variables) {
    JsonNode result = evaluate(expression, input, variables);
    if (result == null || result.isNull()) {
      return false;
    }
    if (result.isBoolean()) {
      return result.booleanValue();
    }
    return true;
  }

  private List<JsonNode> evaluateAll(
      String expression, JsonNode input, Map<String, JsonNode> variables) {
    String program = unwrap(expression);
    JsonNode in = input == null ? NullNode.getInstance() : input;
    try {
      JsonQuery query = JsonQuery.compile(program, JQ_VERSION);
      List<JsonNode> out = new ArrayList<>();
      Scope childScope = Scope.newChildScope(rootScope);
      for (Map.Entry<String, JsonNode> variable : variables.entrySet()) {
        JsonNode value = variable.getValue();
        childScope.setValue(variable.getKey(), value == null ? NullNode.getInstance() : value);
      }
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
