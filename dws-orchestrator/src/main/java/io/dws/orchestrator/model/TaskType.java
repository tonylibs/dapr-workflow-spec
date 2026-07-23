package io.dws.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Task kinds understood by the interpreter. Mirrors the Serverless Workflow DSL 1.0
 * task vocabulary, restricted to the subset this orchestrator executes.
 */
public enum TaskType {
  CALL,
  SWITCH,
  SET,
  WAIT,
  LISTEN,
  EMIT,
  FOR,
  TRY,
  END;

  @JsonCreator
  public static TaskType fromString(String value) {
    return TaskType.valueOf(value.trim().toUpperCase());
  }
}
