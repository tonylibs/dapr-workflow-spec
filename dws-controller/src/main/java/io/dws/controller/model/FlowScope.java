package io.dws.controller.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The scope-specific half of a flow node's single-node definition: the DSL scope that produced the
 * node, the task list it owns, and — on a controller — that scope's own configuration.
 *
 * <p>ADR 0004 splits flow nodes into two shapes, and this type admits only those two. A
 * <em>sequencer</em> ({@code main}, {@code do}, built with {@link #sequencer}) carries a task list
 * and no configuration. A <em>controller</em> ({@code for}, {@code try-catch}, {@code fork}, {@code
 * wait}, {@code listen}, built with {@link #controller}) carries exactly one {@link ScopeConfig},
 * whose type names the scope, and an empty task list. The {@code wait} and {@code listen}
 * controllers have no execution children either (ADR 0006).
 *
 * <p>An unset field is left out of the {@code /plan} JSON rather than written as {@code null}
 * ({@link JsonInclude.Include#NON_ABSENT}), and every {@code Optional<JsonNode>} drops a JSON
 * {@code null} on construction: Jackson reads an absent or null {@code Optional<JsonNode>} back as
 * {@code Optional.of(NullNode)}, which would otherwise render as {@code "errors": null}.
 *
 * @param scope the classification scope, e.g. {@code main}, {@code try-catch}, {@code fork}
 * @param tasks a sequencer's task list, verbatim from the submitted document; always empty on a
 *     controller
 * @param config a controller's own configuration; absent on a sequencer
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record FlowScope(String scope, List<JsonNode> tasks, Optional<ScopeConfig> config) {

  public static final String MAIN = "main";
  public static final String DO = "do";
  public static final String FOR = "for";
  public static final String TRY_CATCH = "try-catch";
  public static final String FORK = "fork";
  public static final String WAIT = "wait";
  public static final String LISTEN = "listen";

  private static final Set<String> SEQUENCERS = Set.of(MAIN, DO);

  /** One controller scope's own configuration; its type determines the scope it belongs to. */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = ForConfig.class, name = FOR),
    @JsonSubTypes.Type(value = TryCatchConfig.class, name = TRY_CATCH),
    @JsonSubTypes.Type(value = ForkConfig.class, name = FORK),
    @JsonSubTypes.Type(value = WaitConfig.class, name = WAIT),
    @JsonSubTypes.Type(value = ListenConfig.class, name = LISTEN)
  })
  public sealed interface ScopeConfig
      permits ForConfig, TryCatchConfig, ForkConfig, WaitConfig, ListenConfig {

    /** The controller scope this configuration belongs to. */
    String scope();
  }

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
      Optional<String> whileCondition)
      implements ScopeConfig {
    @Override
    public String scope() {
      return FOR;
    }
  }

  /**
   * A {@code try-catch} controller's recovery configuration, read verbatim from the definition's
   * {@code catch} block. {@code errors} filters which errors this catch matches and {@code retry}
   * names or spells out the retry policy; {@code when} and {@code exceptWhen} are the additional
   * guards deciding whether to recover at all, and {@code as} names the error variable the recovery
   * body binds. Every field is optional here rather than validated, for the same reason as {@link
   * ForConfig}: ADR 0004 defers v2 semantic validation to the single-node schema. {@code
   * catchAppId} is the sibling catch node, present only when the definition supplies a non-empty
   * {@code catch.do}.
   */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  public record TryCatchConfig(
      Optional<String> catchAppId,
      Optional<JsonNode> errors,
      Optional<JsonNode> retry,
      Optional<String> as,
      Optional<String> when,
      Optional<String> exceptWhen)
      implements ScopeConfig {
    public TryCatchConfig {
      errors = withoutJsonNull(errors);
      retry = withoutJsonNull(retry);
    }

    /** A recovery configuration with no catch node, filter, policy, or guards. */
    public static TryCatchConfig empty() {
      return new TryCatchConfig(
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty());
    }

    /** This configuration with its dedicated catch node's app ID attached. */
    public TryCatchConfig withCatch(String catchAppId) {
      return new TryCatchConfig(Optional.of(catchAppId), errors, retry, as, when, exceptWhen);
    }

    @Override
    public String scope() {
      return TRY_CATCH;
    }
  }

  /** A {@code fork} controller's {@code any}/{@code all} completion mode. */
  public record ForkConfig(String mode) implements ScopeConfig {
    public ForkConfig {
      Objects.requireNonNull(mode, "mode");
    }

    @Override
    public String scope() {
      return FORK;
    }
  }

  /** A {@code wait} controller's duration, verbatim: an ISO-8601 string or a duration object. */
  public record WaitConfig(JsonNode duration) implements ScopeConfig {
    public WaitConfig {
      requirePresent(duration, "duration");
    }

    @Override
    public String scope() {
      return WAIT;
    }
  }

  /** A {@code listen} controller's event selection, task timeout, and event read mode. */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  public record ListenConfig(JsonNode to, Optional<JsonNode> timeout, Optional<String> read)
      implements ScopeConfig {
    public ListenConfig {
      requirePresent(to, "to");
      timeout = withoutJsonNull(timeout);
    }

    @Override
    public String scope() {
      return LISTEN;
    }
  }

  public FlowScope {
    tasks = List.copyOf(tasks);
    Optional<String> configScope = Objects.requireNonNull(config, "config").map(ScopeConfig::scope);
    if (configScope.isEmpty() && !SEQUENCERS.contains(scope)) {
      throw new IllegalArgumentException("scope '" + scope + "' is not a sequencer");
    }
    configScope
        .filter(owner -> !owner.equals(scope))
        .ifPresent(
            owner -> {
              throw new IllegalArgumentException(
                  "scope '" + scope + "' cannot carry " + owner + " configuration");
            });
    if (configScope.isPresent() && !tasks.isEmpty()) {
      throw new IllegalArgumentException("controller scope '" + scope + "' owns no task list");
    }
  }

  /** A sequencer scope ({@code main} or {@code do}) over its task list. */
  public static FlowScope sequencer(String scope, List<JsonNode> tasks) {
    return new FlowScope(scope, tasks, Optional.empty());
  }

  /** A controller scope carrying its own configuration and an empty task list. */
  public static FlowScope controller(ScopeConfig config) {
    return new FlowScope(config.scope(), List.of(), Optional.of(config));
  }

  private static Optional<JsonNode> withoutJsonNull(Optional<JsonNode> value) {
    return value.filter(node -> !node.isNull());
  }

  private static void requirePresent(JsonNode value, String field) {
    if (value == null || value.isNull() || value.isMissingNode()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }
}
