package io.dws.controller.compile;

import io.dws.controller.model.CompiledNode;
import io.dws.controller.model.StepNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * The one Dapr app-ID namespace a compiled graph's deployables share (design §D6).
 *
 * <p>Checked as a post-pass over the finished tree rather than during classification, so a
 * rejection can name both claimants.
 */
@UtilityClass
class AppIdRegistry {

  /**
   * Rejects a graph whose deployables do not resolve to distinct Dapr app IDs.
   *
   * <p>A {@code call}/{@code run} step's {@code functionAppId} is claimed here too: the {@code -fn}
   * Knative Service is a deployable like any other, and leaving it out let a step named {@code
   * reserveItemFn} silently claim the same {@code reserve-item-fn} app ID as the function behind a
   * {@code call} step named {@code reserveItem}.
   *
   * @throws CompilationException if two deployables derive the same app ID
   */
  static void requireDistinct(CompiledNode root) {
    Map<String, String> claimants = new LinkedHashMap<>();
    for (CompiledNode node : root.flatten()) {
      claim(claimants, node.appId(), "node '" + node.nodeId() + "'");
      if (node instanceof StepNode step) {
        step.functionAppId()
            .ifPresent(
                functionAppId ->
                    claim(
                        claimants,
                        functionAppId,
                        "the function app ID of node '" + node.nodeId() + "'"));
      }
    }
  }

  /** Binds one app ID to the thing claiming it, rejecting a second claim on the same ID. */
  private static void claim(Map<String, String> claimants, String appId, String claimant) {
    String previous = claimants.putIfAbsent(appId, claimant);
    if (previous != null) {
      throw new CompilationException(
          List.of(previous + " and " + claimant + " both derive the app ID '" + appId + "'"));
    }
  }
}
