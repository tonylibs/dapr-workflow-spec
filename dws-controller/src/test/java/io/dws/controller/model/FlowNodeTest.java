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

  /**
   * Jackson reads an absent {@code Optional<JsonNode>} back as {@code Optional.of(NullNode)}, so an
   * unset JSON-valued config field must neither be written nor come back as a JSON {@code null}.
   */
  @Test
  void aControllerWithUnsetJsonConfigRoundTripsWithoutNulls() throws Exception {
    FlowScope scope =
        FlowScope.controller(
            new FlowScope.TryCatchConfig(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of("error"),
                Optional.empty(),
                Optional.empty()));
    FlowNode flow = new FlowNode("guarded", "guarded", "dws-def-guarded", ENVELOPE, scope);

    String json = mapper.writeValueAsString(flow);
    FlowNode back = (FlowNode) mapper.readValue(json, CompiledNode.class);

    assertThat(json).doesNotContain(":null");
    assertThat(back).isEqualTo(flow);
    assertThat(back.specText()).isEqualTo(flow.specText()).doesNotContain("errors", "retry");
  }

  @Test
  void aCompanionStaysAttachedWhenExecutionChildrenChangeAndTheGraphRoundTrips() throws Exception {
    StepNode companion = step("group.dataflow", "group-dataflow");
    StepNode child = step("child", "child");
    FlowNode group =
        new FlowNode(
                "group", "group", "dws-def-group", ENVELOPE, FlowScope.sequencer("do", List.of()))
            .withDataflow(companion)
            .withChild(child);

    assertThat(group.children()).containsExactly(child);
    assertThat(group.flatten()).containsExactly(group, companion, child);
    assertThat(mapper.readValue(mapper.writeValueAsString(group), CompiledNode.class))
        .isEqualTo(group);
  }

  @Test
  void rendersTheDataflowReferenceFromTheAttachedCompanionOnly() {
    FlowNode bare =
        new FlowNode(
            "group", "group", "dws-def-group", ENVELOPE, FlowScope.sequencer("do", List.of()));

    FlowNode withCompanion = bare.withDataflow(step("group.dataflow", "group-dataflow"));

    assertThat(bare.specText()).doesNotContain("\"dataflow\"");
    assertThat(withCompanion.specText()).contains("\"dataflow\" : \"group-dataflow\"");
  }

  /** ADR 0012 Decision 3 emits companions for structural tasks only; {@code main} is never one. */
  @Test
  void rejectsADataflowCompanionOnMain() {
    FlowNode main = flow();
    StepNode companion = step("order-fulfillment.dataflow", "order-fulfillment-dataflow");

    assertThatThrownBy(() -> main.withDataflow(companion))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("main");
  }

  private static FlowNode flow(CompiledNode... children) {
    return new FlowNode(
        "order-fulfillment.main",
        "order-fulfillment-main",
        "dws-def-order-fulfillment-v1a2b3c4d-order-fulfillment-main",
        ENVELOPE,
        FlowScope.sequencer("main", List.of()),
        List.of(children));
  }

  private static StepNode step(String nodeId, String appId) {
    return new StepNode(nodeId, appId, "dws-def-" + appId, "{}", Optional.empty());
  }
}
