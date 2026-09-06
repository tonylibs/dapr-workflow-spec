package io.dws.controller.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompiledNodeTest {

  private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  @DisplayName("key() is the last dotted segment of nodeId()")
  void keyDerivesFromLastDottedSegment() {
    CompiledNode nested =
        new StepNode("fulfillOrder.catch", "fulfill-order-catch", "res", "{}", Optional.empty());
    assertThat(nested.key()).isEqualTo("catch");
  }

  @Test
  @DisplayName("key() is the whole nodeId() when there is no dot")
  void keyIsWholeNodeIdWhenNoDot() {
    CompiledNode top = new FlowNode("fulfillOrder", "fulfill-order", "res", "{}", List.of());
    assertThat(top.key()).isEqualTo("fulfillOrder");
  }

  @Test
  @DisplayName("a StepNode is a leaf")
  void stepNodeIsLeaf() {
    StepNode step = new StepNode("a.b", "a-b", "res", "{}", Optional.of("a-b-fn"));
    assertThat(step.children()).isEmpty();
  }

  @Test
  @DisplayName("the sealed graph serializes with a nodeType discriminator and round-trips")
  void polymorphicRoundTrip() throws Exception {
    CompiledNode graph =
        new FlowNode(
            "fulfillOrder",
            "fulfill-order",
            "flow-res",
            "{\"flow\":true}",
            List.of(
                new StepNode("fulfillOrder.charge", "charge", "step-res", "{}", Optional.empty()),
                new FlowNode(
                    "fulfillOrder.catch", "fulfill-order-catch", "catch-res", "{}", List.of())));

    String json = mapper.writeValueAsString(graph);
    assertThat(json).contains("\"nodeType\":\"flow\"").contains("\"nodeType\":\"step\"");

    CompiledNode back = mapper.readValue(json, CompiledNode.class);
    assertThat(back).isInstanceOf(FlowNode.class).isEqualTo(graph);
    assertThat(back.children().get(0)).isInstanceOf(StepNode.class);
    assertThat(back.children().get(1)).isInstanceOf(FlowNode.class);
  }
}
