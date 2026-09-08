package io.dws.controller.compile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.dws.controller.model.CompiledNode;
import io.dws.controller.model.FlowNode;
import io.dws.controller.model.StepNode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Tests of the one-app-ID-per-deployable rule (design §D6).
 *
 * <p>There is no "two steps share a function app ID" case: {@code functionAppId} is always {@code
 * appId + "-fn"}, so two steps can only collide on it once they have already collided on their node
 * app IDs. The cross-namespace collision below — a node against another node's function app ID — is
 * the only one the function IDs add.
 */
class AppIdRegistryTest {

  @Test
  void acceptsAGraphWhoseDeployablesAreDistinct() {
    CompiledNode root = flow("order.main", step("validateOrder"), step("reserveItem", "-fn"));

    assertThatCode(() -> AppIdRegistry.requireDistinct(root)).doesNotThrowAnyException();
  }

  /**
   * Two nodes at different depths can sanitize to the same DNS-1123 label — {@code fulfillOrder}
   * nested under a fork branch and a top-level {@code fulfill-order}, say — and each would claim
   * the same Knative Service.
   */
  @Test
  void rejectsTwoNodesDerivingTheSameAppId() {
    CompiledNode root = flow("order.main", step("fulfillOrder"), flow("fulfill-order"));

    assertThatThrownBy(() -> AppIdRegistry.requireDistinct(root))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("node 'fulfillOrder'")
        .hasMessageContaining("node 'fulfill-order'")
        .hasMessageContaining("'fulfill-order'");
  }

  /**
   * The {@code -fn} Knative Service is a deployable like any other, so a step literally named
   * {@code reserveItemFn} must not be able to claim the app ID of the function behind a {@code
   * call} step named {@code reserveItem}.
   */
  @Test
  void rejectsANodeCollidingWithAnotherNodesFunctionAppId() {
    CompiledNode root = flow("order.main", step("reserveItem", "-fn"), step("reserveItemFn"));

    assertThatThrownBy(() -> AppIdRegistry.requireDistinct(root))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("the function app ID of node 'reserveItem'")
        .hasMessageContaining("node 'reserveItemFn'")
        .hasMessageContaining("'reserve-item-fn'");
  }

  private static CompiledNode flow(String nodeId, CompiledNode... children) {
    String appId = NodeNaming.appId(nodeId);
    return new FlowNode(nodeId, appId, "dws-def-w-v1-" + appId, "{}", List.of(children));
  }

  private static CompiledNode step(String nodeId) {
    String appId = NodeNaming.appId(nodeId);
    return new StepNode(nodeId, appId, "dws-def-w-v1-" + appId, "{}", Optional.empty());
  }

  private static CompiledNode step(String nodeId, String functionSuffix) {
    String appId = NodeNaming.appId(nodeId);
    return new StepNode(
        nodeId, appId, "dws-def-w-v1-" + appId, "{}", Optional.of(appId + functionSuffix));
  }
}
