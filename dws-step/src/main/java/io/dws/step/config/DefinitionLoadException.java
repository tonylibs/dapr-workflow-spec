package io.dws.step.config;

/** Raised when the pod's pinned single-node definition cannot be loaded or is invalid. */
public class DefinitionLoadException extends RuntimeException {

  public DefinitionLoadException(String message) {
    super(message);
  }

  public DefinitionLoadException(String message, Throwable cause) {
    super(message, cause);
  }
}
