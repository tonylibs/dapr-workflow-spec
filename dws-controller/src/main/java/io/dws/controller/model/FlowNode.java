package io.dws.controller.model;

import java.util.List;

/**
 * An internal node of the compiled Flow/Step graph (ADR 0002): a {@code flow} that dispatches its
 * {@link #children()} as child workflows/activities. Populated only by the v2 structural compiler.
 *
 * @param nodeId pre-sanitized derived id, e.g. {@code "fulfillOrder.catch"}
 * @param appId sanitized DNS-1123 Dapr app-id
 * @param definitionResource this node's own ConfigMap/Configuration-store key
 * @param specText this node's single-node definition JSON (Phase 0 schema)
 * @param children this flow's child nodes
 */
public record FlowNode(
    String nodeId,
    String appId,
    String definitionResource,
    String specText,
    List<CompiledNode> children)
    implements CompiledNode {

  public FlowNode {
    children = List.copyOf(children);
  }
}
