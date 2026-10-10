package io.dws.controller.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The dedicated orchestrator Deployment for one workflow version. Runs the generic {@code
 * sw-orchestrator} image pointed at the immutable definition ConfigMap.
 */
public record OrchestratorSpec(
    String name, String image, String appId, int appPort, int replicas, Map<String, EnvValue> env) {

  public OrchestratorSpec {
    // Not Map.copyOf: its iteration order is randomized per JVM, which would reorder the rendered
    // env list (and so the pod template) between controller restarts.
    env = Collections.unmodifiableMap(new LinkedHashMap<>(env));
  }
}
