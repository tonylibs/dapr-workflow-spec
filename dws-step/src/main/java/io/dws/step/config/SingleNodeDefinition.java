package io.dws.step.config;

import com.fasterxml.jackson.databind.JsonNode;
import io.dws.step.workflow.TaskKind;
import java.util.List;
import one.util.streamex.StreamEx;

/** Validated, immutable representation of this process's one Step node. */
public record SingleNodeDefinition(
    String workflow, String version, String nodeId, JsonNode task, String functionAppId) {

  /** Kinds ADR 0006 moved to {@code dws-flow}; present in a step definition they fail startup. */
  static final List<String> FLOW_ONLY_TASK_KINDS = List.of("wait", "listen");

  private static final List<String> SUPPORTED_TASK_KINDS =
      StreamEx.of(TaskKind.values()).map(TaskKind::key).toList();

  public String taskKind() {
    return kind().key();
  }

  public TaskKind kind() {
    List<String> matchingKinds = StreamEx.of(SUPPORTED_TASK_KINDS).filter(task::has).toList();
    if (matchingKinds.isEmpty()) {
      throw new IllegalStateException(
          "task must contain one of the supported task kinds: " + SUPPORTED_TASK_KINDS);
    }
    if (matchingKinds.size() > 1) {
      throw new IllegalStateException(
          "task must contain exactly one supported task kind, found: " + matchingKinds);
    }
    return TaskKind.fromKey(matchingKinds.getFirst());
  }
}
