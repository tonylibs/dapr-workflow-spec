package io.dws.controller.model;

/** Live status of a workflow version, derived from its orchestrator Deployment. */
public enum StackStatus {
    /** Orchestrator has at least one ready replica. */
    READY,
    /** Orchestrator exists but has no ready replica yet. */
    PENDING,
    /** Superseded by a newer version and annotated for drain. */
    DRAINING
}
