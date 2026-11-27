package io.dws.controller.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;

/**
 * The scope-specific half of a flow node's single-node definition: the DSL scope that produced the
 * node, the task list it owns, and the fields only some scopes carry.
 *
 * <p>ADR 0004 splits flow nodes into two shapes. A <em>sequencer</em> ({@code main}, {@code do})
 * carries a task list and no configuration. A <em>controller</em> ({@code for}, {@code try-catch},
 * {@code fork}, {@code wait}, {@code listen}) carries that scope's own configuration and an empty
 * task list. The {@code wait} and {@code listen} controllers have no execution children (ADR 0006).
 *
 * <p>An unset field is left out of the {@code /plan} JSON rather than written as {@code null}
 * ({@link JsonInclude.Include#NON_ABSENT}), and every {@code Optional<JsonNode>} drops a JSON
 * {@code null} on construction: Jackson reads an absent or null {@code Optional<JsonNode>} back as
 * {@code Optional.of(NullNode)}, which would otherwise render as {@code "errors": null}.
 *
 * @param scope the classification scope, e.g. {@code main}, {@code try-catch}, {@code fork}
 * @param tasks this scope's task list, verbatim from the submitted document; always empty on a
 *     controller
 * @param catchAppId a {@code try-catch} scope's sibling catch node, when the definition supplies
 *     one
 * @param forkMode a {@code fork} scope's {@code any}/{@code all} completion mode
 * @param forConfig a {@code for} scope's own loop configuration
 * @param tryCatchConfig a {@code try-catch} scope's own recovery configuration — error filter,
 *     retry policy, recovery guards, and error variable name
 * @param waitDuration a {@code wait} scope's duration
 * @param listenConfig a {@code listen} scope's event selection, timeout, and read mode
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record FlowScope(
    String scope,
    List<JsonNode> tasks,
    Optional<String> catchAppId,
    Optional<String> forkMode,
    Optional<ForConfig> forConfig,
    Optional<TryCatchConfig> tryCatchConfig,
    Optional<JsonNode> waitDuration,
    Optional<ListenConfig> listenConfig) {

  /**
   * A {@code for} controller's loop configuration, read verbatim from the definition. Every field
   * is optional here rather than validated: ADR 0004 defers v2 semantic validation, and the
   * single-node schema is what requires {@code in} on a {@code for} node.
   */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  public record ForConfig(
      Optional<String> each,
      Optional<String> in,
      Optional<String> at,
      Optional<String> whileCondition) {}

  /**
   * A {@code try-catch} controller's recovery configuration, read verbatim from the definition's
   * {@code catch} block. {@code errors} filters which errors this catch matches and {@code retry}
   * names or spells out the retry policy; {@code when} and {@code exceptWhen} are the additional
   * guards deciding whether to recover at all, and {@code as} names the error variable the recovery
   * body binds. Every field is optional here rather than validated, for the same reason as {@link
   * ForConfig}: ADR 0004 defers v2 semantic validation to the single-node schema.
   */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  public record TryCatchConfig(
      Optional<JsonNode> errors,
      Optional<JsonNode> retry,
      Optional<String> as,
      Optional<String> when,
      Optional<String> exceptWhen) {
    public TryCatchConfig {
      errors = withoutJsonNull(errors);
      retry = withoutJsonNull(retry);
    }
  }

  /** A {@code listen} controller's event selection, task timeout, and event read mode. */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  public record ListenConfig(JsonNode to, Optional<JsonNode> timeout, Optional<String> read) {
    public ListenConfig {
      timeout = withoutJsonNull(timeout);
    }
  }

  public FlowScope {
    tasks = List.copyOf(tasks);
    waitDuration = withoutJsonNull(waitDuration);
  }

  private static Optional<JsonNode> withoutJsonNull(Optional<JsonNode> value) {
    return value.filter(node -> !node.isNull());
  }

  /** A scope carrying only a task list — every sequencer, and a controller before configuration. */
  public static FlowScope of(String scope, List<JsonNode> tasks) {
    return new FlowScope(
        scope,
        tasks,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  /** This scope with its dedicated catch node's app ID attached. */
  public FlowScope withCatch(String catchAppId) {
    return new FlowScope(
        scope,
        tasks,
        Optional.of(catchAppId),
        forkMode,
        forConfig,
        tryCatchConfig,
        waitDuration,
        listenConfig);
  }

  /** This scope with its fork completion mode attached. */
  public FlowScope withForkMode(String forkMode) {
    return new FlowScope(
        scope,
        tasks,
        catchAppId,
        Optional.of(forkMode),
        forConfig,
        tryCatchConfig,
        waitDuration,
        listenConfig);
  }

  /** This scope with its loop configuration attached. */
  public FlowScope withForConfig(ForConfig forConfig) {
    return new FlowScope(
        scope,
        tasks,
        catchAppId,
        forkMode,
        Optional.of(forConfig),
        tryCatchConfig,
        waitDuration,
        listenConfig);
  }

  /** This scope with its recovery configuration attached. */
  public FlowScope withTryCatchConfig(TryCatchConfig tryCatchConfig) {
    return new FlowScope(
        scope,
        tasks,
        catchAppId,
        forkMode,
        forConfig,
        Optional.of(tryCatchConfig),
        waitDuration,
        listenConfig);
  }

  /** This scope with its {@code wait} duration attached. */
  public FlowScope withWait(JsonNode duration) {
    return new FlowScope(
        scope,
        tasks,
        catchAppId,
        forkMode,
        forConfig,
        tryCatchConfig,
        Optional.of(duration),
        listenConfig);
  }

  /** This scope with its {@code listen} configuration attached. */
  public FlowScope withListen(ListenConfig config) {
    return new FlowScope(
        scope,
        tasks,
        catchAppId,
        forkMode,
        forConfig,
        tryCatchConfig,
        waitDuration,
        Optional.of(config));
  }
}
