package io.dws.controller.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompiledNodeFlattenTest {

  private static final SingleNodeDefinition.Envelope ENVELOPE =
      new SingleNodeDefinition.Envelope("w", "w@v1");
  private static final FlowScope SCOPE = FlowScope.of("do", List.of());

  private static StepNode step(String id) {
    return new StepNode(id, id, "res-" + id, "{}", Optional.empty());
  }

  @Test
  void walksTheTreeInPreOrder() {
    StepNode leaf = step("leaf");
    FlowNode inner = new FlowNode("inner", "inner", "res-inner", ENVELOPE, SCOPE, List.of(leaf));
    FlowNode root =
        new FlowNode("root", "root", "res-root", ENVELOPE, SCOPE, List.of(inner, step("sibling")));

    assertThat(root.flatten())
        .extracting(CompiledNode::nodeId)
        .containsExactly("root", "inner", "leaf", "sibling");
  }

  @Test
  void aStepFlattensToItself() {
    assertThat(step("only").flatten()).extracting(CompiledNode::nodeId).containsExactly("only");
  }
}
