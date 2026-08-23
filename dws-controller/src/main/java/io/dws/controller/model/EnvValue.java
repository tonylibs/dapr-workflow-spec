package io.dws.controller.model;

/** A value projected into a deployed service's environment. */
public sealed interface EnvValue permits EnvValue.Literal, EnvValue.SecretKeyRef {

  /** A non-sensitive literal value rendered directly in the workload manifest. */
  record Literal(String value) implements EnvValue {}

  /** A reference to one key in a Kubernetes Secret. */
  record SecretKeyRef(String name, String key) implements EnvValue {}
}
