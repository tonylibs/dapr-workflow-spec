package io.dws.controller.model;

/** One deployed version of a workflow, as read back from the cluster. */
public record WorkflowVersionStatus(
        String versionId, String version, StackStatus status, int replicas, int readyReplicas, boolean draining) {}
