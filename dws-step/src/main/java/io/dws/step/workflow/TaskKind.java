package io.dws.step.workflow;

import java.util.Locale;
import one.util.streamex.StreamEx;

/**
 * The task kinds {@code dws-step} hosts — the single source of supported kinds. {@code wait} and
 * {@code listen} are deliberately absent: ADR 0006 runs them as {@code dws-flow} controller nodes.
 */
public enum TaskKind {
  SET,
  SWITCH,
  EMIT,
  RAISE,
  CALL,
  RUN;

  /** The task key as written in the definition, e.g. {@code call}. */
  public String key() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** The kind for a task key, or {@code null} when {@code key} is not a supported kind. */
  public static TaskKind fromKey(String key) {
    return StreamEx.of(values()).findFirst(kind -> kind.key().equals(key)).orElse(null);
  }
}
