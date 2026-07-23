package io.dws.controller.api;

/** No resources with {@code dws.io/workflow=<name>} exist in the cluster. */
public class WorkflowNotFoundException extends RuntimeException {

    public WorkflowNotFoundException(String name) {
        super("Workflow not found: " + name);
    }
}
