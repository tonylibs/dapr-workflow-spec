package io.dws.orchestrator.expr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JqEvaluatorTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final JqEvaluator evaluator = new JqEvaluator(mapper);

  private JsonNode json(String raw) throws Exception {
    return mapper.readTree(raw);
  }

  @Test
  void evaluatesTruthyBooleanFromWrappedExpression() throws Exception {
    // Arrange
    JsonNode input = json("{\"inStock\": true}");

    // Act
    boolean result = evaluator.evaluateBoolean("${ .inStock }", input);

    // Assert
    assertThat(result).isTrue();
  }

  @Test
  void evaluatesFalseFromBareExpression() throws Exception {
    JsonNode input = json("{\"inStock\": false}");

    assertThat(evaluator.evaluateBoolean(".inStock", input)).isFalse();
  }

  @Test
  void treatsMissingFieldAsFalse() throws Exception {
    JsonNode input = json("{\"other\": 1}");

    assertThat(evaluator.evaluateBoolean(".inStock", input)).isFalse();
  }

  @Test
  void treatsNonBooleanValueAsTruthy() throws Exception {
    JsonNode input = json("{\"name\": \"widget\"}");

    assertThat(evaluator.evaluateBoolean(".name", input)).isTrue();
  }

  @Test
  void evaluatesArithmeticExpressionToValue() throws Exception {
    JsonNode input = json("{\"a\": 1, \"b\": 2}");

    JsonNode result = evaluator.evaluate("${ .a + .b }", input);

    assertThat(result.intValue()).isEqualTo(3);
  }

  @Test
  void unwrapStripsDollarBraceWrapper() {
    assertThat(JqEvaluator.unwrap("${ .foo }")).isEqualTo(".foo");
    assertThat(JqEvaluator.unwrap(".foo")).isEqualTo(".foo");
  }

  @Test
  void invalidExpressionRaisesExpressionException() throws Exception {
    JsonNode input = json("{}");

    assertThatThrownBy(() -> evaluator.evaluate("${ .. || }", input))
        .isInstanceOf(JqEvaluator.ExpressionException.class);
  }

  @Test
  void boundVariableIsReadableAsDollarContext() throws Exception {
    // Arrange
    JsonNode input = json("{\"amount\": 2}");
    JsonNode context = json("{\"total\": 40}");

    // Act
    JsonNode result =
        evaluator.evaluate("${ $context.total + .amount }", input, Map.of("context", context));

    // Assert
    assertThat(result.intValue()).isEqualTo(42);
  }

  @Test
  void unboundContextVariableIsAnEmptyObjectWhenBoundAsSuch() throws Exception {
    JsonNode result =
        evaluator.evaluate(
            "${ $context.missing // \"none\" }", json("{}"), Map.of("context", json("{}")));

    assertThat(result.textValue()).isEqualTo("none");
  }

  @Test
  void structuredFormEvaluatesWrappedStringsAndKeepsLiterals() throws Exception {
    // Arrange: only the ${ }-wrapped value is an expression; "active" is a literal string.
    JsonNode template = json("{\"total\": \"${ .price * .qty }\", \"status\": \"active\"}");

    // Act
    JsonNode result =
        evaluator.evaluateStructured(template, json("{\"price\":5,\"qty\":3}"), Map.of());

    // Assert
    assertThat(result.get("total").intValue()).isEqualTo(15);
    assertThat(result.get("status").textValue()).isEqualTo("active");
  }

  @Test
  void structuredFormRecursesObjectsAndArraysAndKeepsNonStringScalars() throws Exception {
    JsonNode template =
        json(
            "{\"nested\": {\"id\": \"${ .orderId }\"},"
                + " \"items\": [\"${ .first }\", \"literal\"],"
                + " \"count\": 7, \"enabled\": true}");

    JsonNode result =
        evaluator.evaluateStructured(
            template, json("{\"orderId\":\"o-1\",\"first\":\"a\"}"), Map.of());

    assertThat(result.get("nested").get("id").textValue()).isEqualTo("o-1");
    assertThat(result.get("items").get(0).textValue()).isEqualTo("a");
    assertThat(result.get("items").get(1).textValue()).isEqualTo("literal");
    assertThat(result.get("count").intValue()).isEqualTo(7);
    assertThat(result.get("enabled").booleanValue()).isTrue();
  }

  @Test
  void structuredFormCanReadTheContextVariable() throws Exception {
    JsonNode template = json("{\"seen\": \"${ $context.seen }\"}");

    JsonNode result =
        evaluator.evaluateStructured(
            template, json("{}"), Map.of("context", json("{\"seen\": 3}")));

    assertThat(result.get("seen").intValue()).isEqualTo(3);
  }
}
