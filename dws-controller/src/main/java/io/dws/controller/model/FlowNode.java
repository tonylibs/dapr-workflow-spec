package io.dws.controller.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.collections4.ListUtils;

/**
 * An internal node of the compiled Flow/Step graph (ADR 0002): a {@code flow} that dispatches its
 * {@link #children()} as child workflows/activities. Populated only by the v2 structural compiler.
 *
 * <p>A flow owns its own wire definition: it holds the envelope and {@link FlowScope} it was
 * compiled from and renders {@link #specText()} on demand, rather than being handed a rendered
 * string. That is what makes {@link #withChild} safe — {@code specText} embeds a {@code children}
 * map keyed by each child's {@link CompiledNode#key()}, so a node that stored its text would go
 * stale the moment a child was appended, leaving the new child unreachable at runtime while present
 * in the object graph.
 *
 * <p>Child-key uniqueness is a class invariant, checked on construction rather than at render time,
 * so an offending graph fails while the compiler is still on the stack.
 *
 * @param nodeId pre-sanitized derived id, e.g. {@code "fulfillOrder.catch"}
 * @param appId sanitized DNS-1123 Dapr app-id
 * @param definitionResource this node's own ConfigMap/Configuration-store key
 * @param envelope the {@code workflow}/{@code version} fields every node's {@code specText} shares
 * @param scope the DSL scope this node was classified from, and the task list it owns
 * @param children this flow's execution children
 * @param dataflow this flow's companion Step node, when present
 */
public record FlowNode(
    String nodeId,
    String appId,
    String definitionResource,
    SingleNodeDefinition.Envelope envelope,
    FlowScope scope,
    List<CompiledNode> children,
    @JsonInclude(JsonInclude.Include.NON_ABSENT) Optional<StepNode> dataflow)
    implements CompiledNode {

  public FlowNode {
    children = List.copyOf(children);
    ChildIndex.of(children); // rejects a duplicate key before the node exists
    if (!dataflow.map(StepNode::appId).equals(scope.dataflowAppId())) {
      throw new IllegalArgumentException(
          "dataflow companion must match its scope's dataflow app ID");
    }
  }

  public FlowNode(
      String nodeId,
      String appId,
      String definitionResource,
      SingleNodeDefinition.Envelope envelope,
      FlowScope scope,
      List<CompiledNode> children) {
    this(nodeId, appId, definitionResource, envelope, scope, children, Optional.empty());
  }

  public FlowNode(
      String nodeId,
      String appId,
      String definitionResource,
      SingleNodeDefinition.Envelope envelope,
      FlowScope scope) {
    this(nodeId, appId, definitionResource, envelope, scope, List.of());
  }

  /**
   * This node's single-node definition JSON (Phase 0 schema), rendered from the scope and the
   * current children.
   *
   * <p>Read-only on the wire: it is derived, so a {@code /plan} payload round-trips through the
   * components above rather than through this.
   */
  @Override
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public String specText() {
    return SingleNodeDefinition.flow(envelope, appId, scope, ChildIndex.of(children));
  }

  /** This flow with {@code child} appended after its current children, in source order. */
  public FlowNode withChild(CompiledNode child) {
    //    List<CompiledNode> appended = new ArrayList<>(children);
    //    appended.add(child);
    return withChildren(ListUtils.union(children, List.of(child)));
  }

  /** This flow with {@code children} in place of its current ones. */
  public FlowNode withChildren(List<CompiledNode> children) {
    return new FlowNode(nodeId, appId, definitionResource, envelope, scope, children, dataflow);
  }

  /** This flow with its companion and the matching reference in its definition. */
  public FlowNode withDataflow(StepNode companion) {
    return new FlowNode(
        nodeId,
        appId,
        definitionResource,
        envelope,
        scope.withDataflow(companion.appId()),
        children,
        Optional.of(companion));
  }

  @Override
  public List<CompiledNode> flatten() {
    List<CompiledNode> all = new ArrayList<>();
    all.add(this);
    dataflow.ifPresent(all::add);
    children.forEach(child -> all.addAll(child.flatten()));
    return List.copyOf(all);
  }
}
