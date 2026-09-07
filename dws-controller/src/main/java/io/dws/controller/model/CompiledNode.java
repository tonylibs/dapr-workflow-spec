package io.dws.controller.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * A node in the compiled Flow/Step graph produced by the v2 structural compiler (ADR 0002).
 *
 * <p>A Composite over a sealed interface: {@link FlowNode} is an internal node with {@link
 * #children()}, {@link StepNode} is a leaf. The tree is the natural output of v2's recursive
 * classification pass over nested scopes ({@code try}/{@code for}/{@code catch}/fork branches) and
 * matches the Phase 0 single-node schema shape (a {@code flow} node has {@code children}, a {@code
 * step} node has none). Phase 1's seam only defines this shape; v1 never populates it.
 *
 * <p>Serialization uses a {@code nodeType} discriminator so the sealed graph round-trips cleanly on
 * the {@code /plan} dry-run endpoint (unlike {@code DeploymentPlan}'s plain-record fields).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "nodeType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = FlowNode.class, name = "flow"),
  @JsonSubTypes.Type(value = StepNode.class, name = "step")
})
public sealed interface CompiledNode permits FlowNode, StepNode {

  /** The pre-sanitized derived id of this node, e.g. {@code "fulfillOrder.catch"}. */
  String nodeId();

  /** The sanitized DNS-1123 Dapr app-id for this node, e.g. {@code "fulfill-order-catch"}. */
  String appId();

  /** This node's own ConfigMap/Configuration-store key. */
  String definitionResource();

  /** This node's single-node definition JSON (Phase 0 schema). */
  String specText();

  /** Child nodes; a leaf {@link StepNode} returns an empty list. */
  List<CompiledNode> children();

  /**
   * This node's label as seen from its parent, i.e. the last dotted segment of {@link #nodeId()}
   * (e.g. {@code "fulfillOrder.catch"} → {@code "catch"}). Derived from the node itself, never
   * assigned by the parent, so building the wire-format keyed {@code children} object is a
   * serialization-time projection rather than a stored structure.
   */
  default String key() {
    int i = nodeId().lastIndexOf('.');
    return i < 0 ? nodeId() : nodeId().substring(i + 1);
  }

  /**
   * This node followed by all its descendants in pre-order — the flat "one Deployment per node"
   * view {@code StackSynthesizer} will consume in Phase 4, and the walk the compiler's
   * duplicate-app-ID check runs over.
   */
  default List<CompiledNode> flatten() {
    List<CompiledNode> all = new ArrayList<>();
    all.add(this);
    children().forEach(child -> all.addAll(child.flatten()));
    return List.copyOf(all);
  }
}
