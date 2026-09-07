package io.dws.controller.compile;

import com.fasterxml.jackson.databind.JsonNode;
import io.dws.controller.model.CompiledNode;
import io.dws.controller.model.DeploymentPlan;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.types.Workflow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The v2 (structural) {@link WorkflowCompiler} strategy: compiles a definition into the Flow/Step
 * graph ({@code flowStepGraph}) rather than the legacy flat step list.
 *
 * <p>Orchestration only (ADR 0002): {@link SpecParser} parses, {@link NodeClassifier} walks the
 * definition into the node tree, and this class computes the plan's identity fields and rejects a
 * definition whose nodes collide on a derived Dapr app ID. It populates <em>only</em> {@code
 * flowStepGraph} plus the identity fields, and leaves every legacy field empty, so v1's compiled
 * output is unaffected.
 */
public class V2StructuralCompiler implements WorkflowCompiler {

  @Override
  public DeploymentPlan compile(String specText) {
    if (specText == null || specText.isBlank()) {
      throw new CompilationException(List.of("Definition is empty"));
    }
    WorkflowFormat format = SpecParser.detectFormat(specText);
    Workflow workflow = SpecParser.parseOrThrow(specText, format);

    String w = Names.kebab(workflow.getDocument().getName());
    String versionId = SpecDigest.versionId(specText, format);
    String version = WorkflowCompiler.version(w, versionId);
    String defResource = Names.definitionResource(w, versionId);

    JsonNode raw = readRaw(specText, format);
    CompiledNode root =
        NodeClassifier.classify(
            workflow, raw, new SingleNodeDefinition.Envelope(w, version), w, versionId);
    rejectDuplicateAppIds(root);

    return new DeploymentPlan(
        w,
        versionId,
        version,
        defResource,
        specText,
        List.of(),
        List.of(),
        null,
        List.of(),
        List.of(),
        List.of(root));
  }

  /**
   * The definition re-read as a raw tree, so each node's rendered {@code tasks}/{@code task}
   * carries the document's own JSON instead of a round-trip through the typed model.
   */
  private static JsonNode readRaw(String specText, WorkflowFormat format) {
    try {
      return format.mapper().readTree(specText);
    } catch (Exception e) {
      throw new CompilationException(List.of("Definition could not be parsed"));
    }
  }

  /**
   * Rejects a definition whose nodes do not resolve to distinct Dapr app IDs (design §D6). Run as a
   * post-pass over the finished tree, so the message can name both colliding node ids.
   */
  private static void rejectDuplicateAppIds(CompiledNode root) {
    Map<String, String> seen = new LinkedHashMap<>();
    for (CompiledNode node : root.flatten()) {
      String previous = seen.putIfAbsent(node.appId(), node.nodeId());
      if (previous != null) {
        throw new CompilationException(
            List.of(
                "nodes '"
                    + previous
                    + "' and '"
                    + node.nodeId()
                    + "' both derive the app ID '"
                    + node.appId()
                    + "'"));
      }
    }
  }
}
