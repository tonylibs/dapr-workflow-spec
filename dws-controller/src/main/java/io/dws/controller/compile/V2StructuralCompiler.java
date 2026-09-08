package io.dws.controller.compile;

import com.fasterxml.jackson.databind.JsonNode;
import io.dws.controller.model.CompiledNode;
import io.dws.controller.model.DeploymentPlan;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.types.Workflow;

/**
 * The v2 (structural) {@link WorkflowCompiler} strategy: compiles a definition into the Flow/Step
 * graph ({@code flowStepGraph}) rather than the legacy flat step list.
 *
 * <p>Orchestration only (ADR 0002): {@link SpecParser} parses, {@link NodeClassifier} walks the
 * definition into the node tree, {@link AppIdRegistry} rejects a graph whose nodes collide on a
 * derived Dapr app ID, and this class computes the plan's identity fields. It populates
 * <em>only</em> {@code flowStepGraph} plus the identity fields, and leaves every legacy field
 * empty, so v1's compiled output is unaffected.
 */
public class V2StructuralCompiler implements WorkflowCompiler {

  @Override
  public DeploymentPlan compile(String specText) {
    SpecParser.requireNonBlank(specText);
    WorkflowFormat format = SpecParser.detectFormat(specText);
    Workflow workflow = SpecParser.parseOrThrow(specText, format);
    JsonNode rawSpec = SpecParser.readRawOrThrow(specText, format);

    String workflowName = Names.kebab(workflow.getDocument().getName());
    String versionId = SpecDigest.versionId(specText, format);
    String version = WorkflowCompiler.version(workflowName, versionId);
    String definitionResource = Names.definitionResource(workflowName, versionId);

    CompiledNode root =
        NodeClassifier.classify(
            workflow,
            rawSpec,
            new SingleNodeDefinition.Envelope(workflowName, version),
            workflowName,
            versionId);
    AppIdRegistry.requireDistinct(root);

    return DeploymentPlan.structural(
        workflowName, versionId, version, definitionResource, specText, root);
  }
}
