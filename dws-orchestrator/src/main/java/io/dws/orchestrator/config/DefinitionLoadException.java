package io.dws.orchestrator.config;

/** Raised when the pod's single workflow definition cannot be fetched, parsed, or validated. */
public class DefinitionLoadException extends RuntimeException {

  public DefinitionLoadException(String message) {
    super(message);
  }

  public DefinitionLoadException(String message, Throwable cause) {
    super(message, cause);
  }
}
