package io.dws.controller.model;

/** Deployable task kinds that map to a prebuilt step image. */
public enum TaskKind {
  CALL_HTTP,
  CALL_OPENAPI,
  RUN_SHELL,
  RUN_SCRIPT_JS,
  RUN_SCRIPT_PYTHON
}
