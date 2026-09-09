package io.dws.controller.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests of the wire-format {@code children} projection and its key-uniqueness rule (ADR 0002). */
class ChildIndexTest {

  @Test
  void keysChildrenByTheirLastDottedSegmentInSourceOrder() {
    assertThat(
            ChildIndex.of(
                List.of(
                    node("fulfillOrder", "fulfill-order"),
                    node("fulfillOrder.catch", "fulfill-order-catch"))))
        .containsExactly(
            entry("fulfillOrder", "fulfill-order"), entry("catch", "fulfill-order-catch"));
  }

  @Test
  void projectsAnEmptyObjectForALeaf() {
    assertThat(ChildIndex.of(List.of())).isEmpty();
  }

  /**
   * The collision this rejects is reachable from a real definition: a {@code do} task list is a
   * YAML sequence of single-key entries, not a map, so nothing stops two sibling tasks from
   * literally sharing the same name — both then resolve to the same {@code key()} (and the same
   * sanitized app ID). Merging them would leave one node unreachable through this map though both
   * still get a Deployment.
   */
  @Test
  void rejectsTwoChildrenResolvingToTheSameKey() {
    List<CompiledNode> children =
        List.of(node("catch", "catch"), node("fulfillOrder.catch", "fulfill-order-catch"));

    assertThatThrownBy(() -> ChildIndex.of(children))
        .isInstanceOf(DuplicateChildKeyException.class)
        .hasMessageContaining("'catch'")
        .hasMessageContaining("'fulfill-order-catch'")
        .hasMessageContaining("must be unique");
  }

  private static CompiledNode node(String nodeId, String appId) {
    return new StepNode(nodeId, appId, "dws-def-w-v1-" + appId, "{}", Optional.empty());
  }
}
