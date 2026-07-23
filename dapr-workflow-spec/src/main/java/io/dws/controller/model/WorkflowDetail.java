package io.dws.controller.model;

import java.util.List;

/** Detail projection for one workflow, including every version currently present in the cluster. */
public record WorkflowDetail(
        String name,
        String version,
        StackStatus status,
        String definitionResource,
        List<String> steps,
        List<WorkflowVersionStatus> versions) {

    public WorkflowDetail {
        steps = List.copyOf(steps);
        versions = List.copyOf(versions);
    }
}
