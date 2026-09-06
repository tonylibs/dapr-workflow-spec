package io.dws.controller.compile;

import io.dws.controller.model.DeploymentPlan;
import java.util.List;

/**
 * The v2 (structural) {@link WorkflowCompiler} strategy: compiles a definition into the Flow/Step
 * graph ({@code flowStepGraph}) rather than the legacy flat step list.
 *
 * <p>Phase 1 seam stub (ADR 0002): classification rules, derived-identifier sanitization, and fork
 * handling are later Phase 1 tasks. This stub only establishes the compatibility contract — it
 * populates <em>only</em> {@code flowStepGraph} (currently empty) and leaves every legacy field
 * empty — so subsequent tasks fill in the graph without touching the seam or v1.
 */
public class V2StructuralCompiler implements WorkflowCompiler {

  @Override
  public DeploymentPlan compile(String specText) {
    return new DeploymentPlan(
        "",
        "",
        "",
        "",
        specText == null ? "" : specText,
        List.of(),
        List.of(),
        null,
        List.of(),
        List.of(),
        List.of());
  }
}
