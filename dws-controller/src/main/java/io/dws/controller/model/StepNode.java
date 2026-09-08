package io.dws.controller.model;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A leaf node of the compiled Flow/Step graph (ADR 0002): a {@code step} dispatched as an activity
 * against the constant {@code "Step"} activity name. Populated only by the v2 structural compiler.
 *
 * @param nodeId pre-sanitized derived id, e.g. {@code "fulfillOrder.charge"}
 * @param appId sanitized DNS-1123 Dapr app-id
 * @param definitionResource this node's own ConfigMap/Configuration-store key
 * @param specText this node's single-node definition JSON (Phase 0 schema)
 * @param functionAppId optional app-id of a co-located function image for this step
 */
public record StepNode(
    String nodeId,
    String appId,
    String definitionResource,
    String specText,
    Optional<String> functionAppId)
    implements CompiledNode {

  /** A step is a leaf: it never has children. */
  @Override
  public List<CompiledNode> children() {
    return Collections.emptyList();
  }
}
