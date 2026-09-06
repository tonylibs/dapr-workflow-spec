package io.dws.controller.compile;

import io.dws.controller.model.DeploymentPlan;

/**
 * The controller's pure compile pass: parse + validate an Open Workflow Specification DSL 1.0
 * definition and produce a {@link DeploymentPlan}. No Kubernetes calls.
 *
 * <p>Phase 1 (ADR 0002) splits this into two strategies behind this interface so a structural v2
 * compiler can be built without touching v1:
 *
 * <ul>
 *   <li>{@link V1OrchestratorCompiler} — the legacy pass; populates only the legacy {@code
 *       DeploymentPlan} fields, leaving {@code flowStepGraph} empty.
 *   <li>{@link V2StructuralCompiler} — the structural pass; populates only {@code flowStepGraph},
 *       leaving the legacy {@code steps}/{@code orchestrator} fields empty.
 * </ul>
 *
 * <p>The interface deliberately keeps the {@code WorkflowCompiler} name so every consumer's field
 * type is unchanged by the split. {@link io.dws.controller.compile.CompilerProducer} selects the
 * active strategy.
 */
public interface WorkflowCompiler {

  /**
   * Compiles a DSL 1.0 definition into a {@link DeploymentPlan}. Throws {@link
   * CompilationException} if the definition is empty, unparseable, or semantically invalid.
   */
  DeploymentPlan compile(String specText);

  /**
   * The public version string: {@code <workflow>@v<sha256-8>}. A pure naming helper shared by the
   * compile and read passes; independent of which strategy is active.
   */
  static String version(String workflow, String versionId) {
    return workflow + "@" + versionId;
  }
}
