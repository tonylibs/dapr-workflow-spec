package io.dws.controller.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;

/**
 * The scope-specific half of a flow node's single-node definition: the DSL scope that produced the
 * node, the task list it owns, and the fields only some scopes carry.
 *
 * <p>ADR 0004 splits flow nodes into two shapes. A <em>sequencer</em> ({@code main}, {@code do})
 * carries a task list and no configuration. A <em>controller</em> ({@code for}, {@code try-catch},
 * {@code fork}) carries that scope's own configuration and an empty task list, delegating each list
 * it owns to a {@code do}-shaped child.
 *
 * @param scope the classification scope, e.g. {@code main}, {@code try-catch}, {@code fork}
 * @param tasks this scope's task list, verbatim from the submitted document; always empty on a
 *     controller
 * @param catchAppId a {@code try-catch} scope's sibling catch node, when the definition supplies
 *     one
 * @param forkMode a {@code fork} scope's {@code any}/{@code all} completion mode
 * @param forConfig a {@code for} scope's own loop configuration
 * @param tryCatchConfig a {@code try-catch} scope's own error filter and retry policy
 */
public record FlowScope(
    String scope,
    List<JsonNode> tasks,
    Optional<String> catchAppId,
    Optional<String> forkMode,
    Optional<ForConfig> forConfig,
    Optional<TryCatchConfig> tryCatchConfig) {

  /**
   * A {@code for} controller's loop configuration, read verbatim from the definition. Every field
   * is optional here rather than validated: ADR 0004 defers v2 semantic validation, and the
   * single-node schema is what requires {@code in} on a {@code for} node.
   */
  public record ForConfig(
      Optional<String> each,
      Optional<String> in,
      Optional<String> at,
      Optional<String> whileCondition) {}

  /** A {@code try-catch} controller's error filter and retry policy, read verbatim. */
  public record TryCatchConfig(Optional<JsonNode> errors, Optional<JsonNode> retry) {}

  public FlowScope {
    tasks = List.copyOf(tasks);
  }

  /** A scope carrying only a task list — every sequencer, and a controller before configuration. */
  public static FlowScope of(String scope, List<JsonNode> tasks) {
    return new FlowScope(
        scope, tasks, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  /** This scope with its dedicated catch node's app ID attached. */
  public FlowScope withCatch(String catchAppId) {
    return new FlowScope(
        scope, tasks, Optional.of(catchAppId), forkMode, forConfig, tryCatchConfig);
  }

  /** This scope with its fork completion mode attached. */
  public FlowScope withForkMode(String forkMode) {
    return new FlowScope(
        scope, tasks, catchAppId, Optional.of(forkMode), forConfig, tryCatchConfig);
  }

  /** This scope with its loop configuration attached. */
  public FlowScope withForConfig(ForConfig forConfig) {
    return new FlowScope(
        scope, tasks, catchAppId, forkMode, Optional.of(forConfig), tryCatchConfig);
  }

  /** This scope with its error filter and retry policy attached. */
  public FlowScope withTryCatchConfig(TryCatchConfig tryCatchConfig) {
    return new FlowScope(
        scope, tasks, catchAppId, forkMode, forConfig, Optional.of(tryCatchConfig));
  }
}
