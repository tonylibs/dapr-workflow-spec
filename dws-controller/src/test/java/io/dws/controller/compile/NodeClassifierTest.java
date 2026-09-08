package io.dws.controller.compile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.types.Workflow;
import org.junit.jupiter.api.Test;

/**
 * Direct tests of {@link NodeClassifier}'s raw/typed alignment guard ({@code requireAligned}),
 * which end-to-end compilation cannot reach: DSL 1.0's {@code do}/{@code try}/{@code catch.do}/
 * {@code fork.branches} are always JSON arrays (schema/workflow.yaml's {@code taskList}), so a real
 * document's typed and raw walks never disagree in length. This constructs that disagreement
 * directly against the package-private classifier, the way a future SDK bug or upstream drift
 * could.
 */
class NodeClassifierTest {

  private static final String SPEC =
      """
      document:
        dsl: '1.0.0'
        namespace: default
        name: misaligned
        version: '1.0.0'
      do:
        - first:
            set:
              a: 1
        - second:
            set:
              b: 2
      """;

  @Test
  void rejectsATaskListWhoseRawAndTypedLengthsDisagree() throws Exception {
    Workflow workflow = SpecParser.parseOrThrow(SPEC, WorkflowFormat.YAML);
    ObjectNode raw = (ObjectNode) WorkflowFormat.YAML.mapper().readTree(SPEC);
    ArrayNode doArray = (ArrayNode) raw.get("do");
    doArray.remove(1); // raw now has 1 element; the typed model still has 2

    SingleNodeDefinition.Envelope envelope =
        new SingleNodeDefinition.Envelope("misaligned", "misaligned@v1");

    assertThatThrownBy(() -> NodeClassifier.classify(workflow, raw, envelope, "misaligned", "v1"))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("2 parsed tasks")
        .hasMessageContaining("1 in the submitted document");
  }
}
