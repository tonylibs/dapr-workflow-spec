package io.dws.controller.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompiledNodeFlattenTest {

  private static StepNode step(String id) {
    return new StepNode(id, id, "res-" + id, "{}", Optional.empty());
  }

  @Test
  void walksTheTreeInPreOrder() {
    StepNode leaf = step("leaf");
    FlowNode inner = new FlowNode("inner", "inner", "res-inner", "{}", List.of(leaf));
    FlowNode root = new FlowNode("root", "root", "res-root", "{}", List.of(inner, step("sibling")));

    assertThat(root.flatten())
        .extracting(CompiledNode::nodeId)
        .containsExactly("root", "inner", "leaf", "sibling");
  }

  @Test
  void aStepFlattensToItself() {
    assertThat(step("only").flatten()).extracting(CompiledNode::nodeId).containsExactly("only");
  }
}
