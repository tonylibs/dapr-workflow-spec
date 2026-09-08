package io.dws.controller.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.dws.controller.model.CompiledNode;
import io.dws.controller.model.StepNode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests of the wire-format {@code children} projection and its key-uniqueness rule (ADR 0002). */
class ChildIndexTest {

  @Test
  void keysChildrenByTheirLastDottedSegmentInSourceOrder() {
    assertThat(ChildIndex.of(List.of(node("fulfillOrder"), node("fulfillOrder.catch"))))
        .containsExactly(
            entry("fulfillOrder", "fulfill-order"), entry("catch", "fulfill-order-catch"));
  }

  @Test
  void projectsAnEmptyObjectForALeaf() {
    assertThat(ChildIndex.of(List.of())).isEmpty();
  }

  /**
   * The collision this rejects is reachable from a real definition: a {@code try} task whose
   * guarded list contains a task literally named {@code catch} produces a child keyed {@code catch}
   * alongside the dedicated catch node's {@code <try>.catch}. Merging them would leave one node
   * unreachable through this map though both still get a Deployment.
   */
  @Test
  void rejectsTwoChildrenResolvingToTheSameKey() {
    List<CompiledNode> children = List.of(node("catch"), node("fulfillOrder.catch"));

    assertThatThrownBy(() -> ChildIndex.of(children))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("'catch'")
        .hasMessageContaining("'fulfill-order-catch'")
        .hasMessageContaining("must be unique");
  }

  private static CompiledNode node(String nodeId) {
    String appId = NodeNaming.appId(nodeId);
    return new StepNode(nodeId, appId, "dws-def-w-v1-" + appId, "{}", Optional.empty());
  }
}
