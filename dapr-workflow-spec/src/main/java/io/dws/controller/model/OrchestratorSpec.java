package io.dws.controller.model;

import java.util.Map;

/**
 * The dedicated orchestrator Deployment for one workflow version. Runs the generic
 * {@code sw-orchestrator} image pointed at the immutable definition ConfigMap.
 */
public record OrchestratorSpec(
        String name,
        String image,
        String appId,
        int appPort,
        int replicas,
        Map<String, String> env) {

    public OrchestratorSpec {
        env = Map.copyOf(env);
    }
}
