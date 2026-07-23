package io.dws.orchestrator.expr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
