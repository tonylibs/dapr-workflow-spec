package io.dws.controller.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.serverlessworkflow.api.WorkflowFormat;
import org.junit.jupiter.api.Test;

class SpecParserTest {

  private static final String YAML =
      """
      document:
        dsl: '1.0.0'
        namespace: default
        name: minimal
        version: '1.0.0'
      do:
        - noop:
            set:
              ok: true
      """;

  @Test
  void detectsYamlAndJson() {
    assertThat(SpecParser.detectFormat(YAML)).isEqualTo(WorkflowFormat.YAML);
    assertThat(SpecParser.detectFormat("  {\"document\": {}}")).isEqualTo(WorkflowFormat.JSON);
  }

  @Test
  void parsesAValidDefinition() {
    assertThat(SpecParser.parseOrThrow(YAML, WorkflowFormat.YAML).getDocument().getName())
        .isEqualTo("minimal");
  }

  @Test
  void wrapsParseFailuresInCompilationException() {
    assertThatThrownBy(() -> SpecParser.parseOrThrow("::not yaml::", WorkflowFormat.YAML))
        .isInstanceOf(CompilationException.class);
  }
}
