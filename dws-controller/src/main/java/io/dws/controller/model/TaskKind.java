package io.dws.controller.model;

/** Deployable task kinds that map to a prebuilt step image. */
public enum TaskKind {
  CALL_HTTP,
  CALL_OPENAPI,
  CALL_GRPC,
  CALL_ASYNCAPI,
  CALL_A2A,
  RUN_SHELL,
  RUN_SCRIPT_JS,
  RUN_SCRIPT_PYTHON
}
