package io.dws.controller.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * Builds a flow node's wire-format {@code children} object: a render-time projection keyed by each
 * child's own {@link CompiledNode#key()} (ADR 0002), in source order — never a stored parent-side
 * map.
 */
@UtilityClass
class ChildIndex {

  /**
   * Projects {@code children} to their {@code key() -> appId()} mapping.
   *
   * <p>Two children resolving to the same key (Finding 1/2 — most commonly a task literally named
   * {@code catch} shadowing the dedicated catch child) is an error, not a silent merge: a merge
   * would leave one node reachable through this map and one unreachable, though both still get a
   * Deployment.
   *
   * @throws DuplicateChildKeyException if two children share a key
   */
  static Map<String, String> of(List<CompiledNode> children) {
    Map<String, String> appIds = new LinkedHashMap<>();
    for (CompiledNode child : children) {
      String key = child.key();
      String previous = appIds.putIfAbsent(key, child.appId());
      if (previous != null) {
        throw new DuplicateChildKeyException(previous, child.appId(), key);
      }
    }
    return appIds;
  }
}
