package io.dws.controller.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests of {@link FlowNode} as a domain object: it renders its own definition and grows safely. */
class FlowNodeTest {

  private static final SingleNodeDefinition.Envelope ENVELOPE =
      new SingleNodeDefinition.Envelope("order-fulfillment", "order-fulfillment@v1a2b3c4d");

  private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

  /**
   * The reason {@code specText} is derived rather than stored: an appended child must appear in the
   * {@code children} map the runtime dispatches through, not just in the object graph.
   */
  @Test
  void appendingAChildReRendersSpecText() {
    FlowNode flow = flow(step("validateOrder", "validate-order"));
    assertThat(flow.specText()).contains("\"validateOrder\" : \"validate-order\"");

    FlowNode grown = flow.withChild(step("reserveItems", "reserve-items"));

    assertThat(grown.children()).hasSize(2);
    assertThat(grown.specText())
        .contains("\"validateOrder\" : \"validate-order\"")
        .contains("\"reserveItems\" : \"reserve-items\"");
  }

  @Test
  void appendingLeavesTheOriginalUntouched() {
    FlowNode flow = flow(step("validateOrder", "validate-order"));

    flow.withChild(step("reserveItems", "reserve-items"));

    assertThat(flow.children()).hasSize(1);
    assertThat(flow.specText()).doesNotContain("reserve-items");
  }

  @Test
  void appendedChildrenKeepSourceOrder() {
    FlowNode flow =
        flow(step("first", "first"))
            .withChild(step("second", "second"))
            .withChild(step("third", "third"));

    assertThat(flow.children())
        .extracting(CompiledNode::nodeId)
        .containsExactly("first", "second", "third");
  }

  /** The invariant is enforced on construction, so a bad append fails immediately. */
  @Test
  void appendingAChildThatCollidesOnItsKeyIsRejected() {
    FlowNode flow = flow(step("fulfillOrder.catch", "fulfill-order-catch"));

    assertThatThrownBy(() -> flow.withChild(step("catch", "catch")))
        .isInstanceOf(DuplicateChildKeyException.class)
        .hasMessageContaining("must be unique");
  }

  @Test
  void withChildrenReplacesRatherThanAppends() {
    FlowNode flow = flow(step("validateOrder", "validate-order"));

    FlowNode replaced = flow.withChildren(List.of(step("other", "other")));

    assertThat(replaced.children()).extracting(CompiledNode::nodeId).containsExactly("other");
    assertThat(replaced.specText()).doesNotContain("validate-order");
  }

  /**
   * {@code specText} stays on the wire for the {@code /plan} dry run, even though it is derived.
   */
  @Test
  void specTextIsStillSerializedButNotRequiredToReadBack() throws Exception {
    FlowNode flow = flow(step("validateOrder", "validate-order"));

    String json = mapper.writeValueAsString(flow);
    assertThat(json)
        .contains("\"specText\"")
        .doesNotContain(
            "\"waitDuration\"", "\"listenConfig\"", "\"dataflowAppId\"", "\"dataflow\"");

    assertThat(mapper.readValue(json, CompiledNode.class)).isEqualTo(flow);
  }

  @Test
  void aCompanionStaysAttachedWhenExecutionChildrenChangeAndTheGraphRoundTrips() throws Exception {
    StepNode companion = step("group.dataflow", "group-dataflow");
    StepNode child = step("child", "child");
    FlowNode group =
        new FlowNode("group", "group", "dws-def-group", ENVELOPE, FlowScope.of("do", List.of()))
            .withDataflow(companion)
            .withChild(child);

    assertThat(group.children()).containsExactly(child);
    assertThat(group.flatten()).containsExactly(group, companion, child);
    assertThat(mapper.readValue(mapper.writeValueAsString(group), CompiledNode.class))
        .isEqualTo(group);
  }

  @Test
  void rejectsACompanionReferenceThatDoesNotMatchTheGraph() {
    FlowScope scope = FlowScope.of("do", List.of()).withDataflow("group-dataflow");
    assertThatThrownBy(() -> new FlowNode("group", "group", "dws-def-group", ENVELOPE, scope))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dataflow");
    assertThatThrownBy(
            () ->
                new FlowNode(
                    "group",
                    "group",
                    "dws-def-group",
                    ENVELOPE,
                    scope,
                    List.of(),
                    Optional.of(step("other.dataflow", "other-dataflow"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dataflow");
  }

  private static FlowNode flow(CompiledNode... children) {
    return new FlowNode(
        "order-fulfillment.main",
        "order-fulfillment-main",
        "dws-def-order-fulfillment-v1a2b3c4d-order-fulfillment-main",
        ENVELOPE,
        FlowScope.of("main", List.of()),
        List.of(children));
  }

  private static StepNode step(String nodeId, String appId) {
    return new StepNode(nodeId, appId, "dws-def-" + appId, "{}", Optional.empty());
  }
}
