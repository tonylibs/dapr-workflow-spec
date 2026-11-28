package io.dws.controller.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Tests of {@link FlowScope}'s two shapes (ADR 0004): a sequencer or exactly one controller. */
class FlowScopeTest {

  private static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules().build();

  @Test
  void aControllerTakesItsScopeNameFromItsConfiguration() {
    FlowScope scope = FlowScope.controller(new FlowScope.ForkConfig("any"));

    assertThat(scope.scope()).isEqualTo("fork");
    assertThat(scope.tasks()).isEmpty();
  }

  @Test
  void rejectsAControllerScopeNameWithoutConfiguration() {
    assertThatThrownBy(() -> FlowScope.sequencer("for", List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a sequencer");
  }

  @Test
  void rejectsConfigurationThatBelongsToAnotherScope() {
    Optional<FlowScope.ScopeConfig> wait = Optional.of(waitConfig());

    assertThatThrownBy(() -> new FlowScope("do", List.of(), wait))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot carry wait configuration");
  }

  @Test
  void rejectsAControllerThatOwnsATaskList() throws Exception {
    List<JsonNode> tasks = List.of(JSON.readTree("{\"a\":{\"set\":{}}}"));
    Optional<FlowScope.ScopeConfig> fork = Optional.of(new FlowScope.ForkConfig("all"));

    assertThatThrownBy(() -> new FlowScope("fork", tasks, fork))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("owns no task list");
  }

  @Test
  void rejectsAWaitWithoutADuration() {
    assertThatThrownBy(() -> new FlowScope.WaitConfig(NullNode.getInstance()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duration");
  }

  @ParameterizedTest
  @MethodSource("everyScopeShape")
  void roundTripsThroughThePlanJson(FlowScope scope) throws Exception {
    String json = JSON.writeValueAsString(scope);

    assertThat(json).doesNotContain(":null");
    assertThat(JSON.readValue(json, FlowScope.class)).isEqualTo(scope);
  }

  static Stream<FlowScope> everyScopeShape() throws Exception {
    return Stream.of(
        FlowScope.sequencer("main", List.of(JSON.readTree("{\"a\":{\"set\":{}}}"))),
        FlowScope.sequencer("do", List.of()),
        FlowScope.controller(
            new FlowScope.ForConfig(
                Optional.of("item"), Optional.of(".items"), Optional.empty(), Optional.empty())),
        FlowScope.controller(FlowScope.TryCatchConfig.empty().withCatch("pay-catch")),
        FlowScope.controller(new FlowScope.ForkConfig("any")),
        FlowScope.controller(waitConfig()),
        FlowScope.controller(
            new FlowScope.ListenConfig(
                JSON.readTree("{\"one\":{\"with\":{\"type\":\"approved\"}}}"),
                Optional.of(JSON.readTree("\"PT1H\"")),
                Optional.of("data"))));
  }

  private static FlowScope.WaitConfig waitConfig() {
    try {
      return new FlowScope.WaitConfig(JSON.readTree("{\"seconds\":10}"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
