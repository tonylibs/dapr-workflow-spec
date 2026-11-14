package io.dws.controller.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The time-trigger kinds a definition's {@code schedule} can carry (scheduling Phase 6a). The
 * {@link #wireName()} is the {@code kind} value in a trigger ConfigMap's {@code trigger.json} — a
 * contract with {@code dws-schedule-time}, which rejects any other value.
 */
public enum TriggerKind {
  EVERY("every"),
  CRON("cron"),
  AFTER("after");

  private final String wireName;

  TriggerKind(String wireName) {
    this.wireName = wireName;
  }

  /** The DSL field name, which is also the {@code kind} value in {@code trigger.json}. */
  @JsonValue
  public String wireName() {
    return wireName;
  }
}
