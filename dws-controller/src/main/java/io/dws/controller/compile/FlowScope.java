package io.dws.controller.compile;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;

/**
 * The scope-specific half of a flow node's single-node definition: the DSL scope that produced the
 * node, the task list it owns, and the fields only some scopes carry.
 *
 * <p>Grouping the optional fields here keeps every scope's construction site naming only what that
 * scope actually has, rather than padding a positional argument list with nulls.
 *
 * @param scope the classification scope, e.g. {@code main}, {@code try}, {@code forkBranch}
 * @param tasks this scope's task list, verbatim from the submitted document
 * @param catchAppId a {@code try} scope's sibling catch node, when the definition supplies one
 * @param forkMode a {@code fork} scope's {@code any}/{@code all} completion mode
 */
record FlowScope(
    String scope, List<JsonNode> tasks, Optional<String> catchAppId, Optional<String> forkMode) {

  FlowScope {
    tasks = List.copyOf(tasks);
  }

  /**
   * A scope carrying only a task list — every scope but {@code try}-with-catch and {@code fork}.
   */
  static FlowScope of(String scope, List<JsonNode> tasks) {
    return new FlowScope(scope, tasks, Optional.empty(), Optional.empty());
  }

  /** This scope with its dedicated catch node's app ID attached. */
  FlowScope withCatch(String catchAppId) {
    return new FlowScope(scope, tasks, Optional.of(catchAppId), forkMode);
  }

  /** This scope with its fork completion mode attached. */
  FlowScope withForkMode(String forkMode) {
    return new FlowScope(scope, tasks, catchAppId, Optional.of(forkMode));
  }
}
