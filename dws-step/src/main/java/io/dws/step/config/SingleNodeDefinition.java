package io.dws.step.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import one.util.streamex.StreamEx;

/** Validated, immutable representation of this process's one Step node. */
public record SingleNodeDefinition(
    String workflow, String version, String nodeId, JsonNode task, String functionAppId) {

  private static final List<String> KNOWN_TASK_KINDS =
      List.of("set", "switch", "wait", "listen", "emit", "raise", "call", "run");

  public String taskKind() {
    List<String> matchingKinds = StreamEx.of(KNOWN_TASK_KINDS).filter(task::has).toList();
    if (matchingKinds.isEmpty()) {
      throw new IllegalStateException(
          "task must contain one of the supported task kinds: " + KNOWN_TASK_KINDS);
    }
    if (matchingKinds.size() > 1) {
      throw new IllegalStateException(
          "task must contain exactly one supported task kind, found: " + matchingKinds);
    }
    return matchingKinds.getFirst();
  }
}
