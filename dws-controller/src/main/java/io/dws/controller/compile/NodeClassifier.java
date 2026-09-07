package io.dws.controller.compile;

import com.fasterxml.jackson.databind.JsonNode;
import io.dws.controller.model.CompiledNode;
import io.dws.controller.model.FlowNode;
import io.dws.controller.model.StepNode;
import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.ForkTask;
import io.serverlessworkflow.api.types.ForkTaskConfiguration;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.TryTask;
import io.serverlessworkflow.api.types.TryTaskCatch;
import io.serverlessworkflow.api.types.Workflow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Recursive descent over a parsed definition, producing the v2 Flow/Step graph (ADR 0001, ADR 0002,
 * ADR 0003).
 *
 * <p>Classification is the table in the change's design §D2: {@code main}, each {@code for}, each
 * {@code try}, each {@code catch}, each {@code fork} and each fork branch become a {@link
 * FlowNode}; every other task kind becomes a {@link StepNode}, with no exceptions.
 *
 * <p>The walk is driven by the typed model but reads every task object out of a parallel raw {@link
 * JsonNode} of the same definition text, so a node's rendered {@code tasks}/{@code task} carries
 * the definition's own JSON rather than a round-trip through the typed model (which drops unknown
 * fields and reorders keys). Both carry the same elements in the same order; a disagreement is
 * rejected as a {@link CompilationException} rather than silently papered over.
 *
 * <p>Children are classified before their parent is rendered, because a flow's {@code specText}
 * names its children's app IDs.
 */
final class NodeClassifier {

  private static final String SCOPE_MAIN = "main";
  private static final String SCOPE_FOR = "for";
  private static final String SCOPE_TRY = "try";
  private static final String SCOPE_CATCH = "catch";
  private static final String SCOPE_FORK = "fork";
  private static final String SCOPE_FORK_BRANCH = "forkBranch";

  private static final String FORK_MODE_ANY = "any";
  private static final String FORK_MODE_ALL = "all";

  private NodeClassifier() {}

  /** Everything the recursion carries unchanged from the top of the definition. */
  private record Context(
      SingleNodeDefinition.Envelope envelope, String workflow, String versionId) {}

  /**
   * Classifies a whole definition into the {@code main} flow node and, transitively, every node
   * below it.
   *
   * @param workflow the parsed definition
   * @param rawSpec the same definition parsed as raw JSON, walked in lockstep with {@code workflow}
   * @param envelope the {@code workflow}/{@code version} fields every node's {@code specText}
   *     shares
   * @param workflowName the kebab-cased workflow name
   * @param versionId the content-addressed {@code v<sha256-8>} version id
   */
  static CompiledNode classify(
      Workflow workflow,
      JsonNode rawSpec,
      SingleNodeDefinition.Envelope envelope,
      String workflowName,
      String versionId) {
    Context context = new Context(envelope, workflowName, versionId);
    JsonNode rawTasks = rawArray(rawSpec, "do");
    List<CompiledNode> children = classifyTasks(workflow.getDo(), rawTasks, context);
    return flow(
        context,
        NodeNaming.mainNodeId(workflowName),
        SCOPE_MAIN,
        elements(rawTasks),
        children,
        null,
        null);
  }

  /** Classifies one scope's task list, in source order, into that scope's child nodes. */
  private static List<CompiledNode> classifyTasks(
      List<TaskItem> tasks, JsonNode rawTasks, Context context) {
    List<TaskItem> items = tasks == null ? List.of() : tasks;
    requireAligned(items, rawTasks);
    List<CompiledNode> children = new ArrayList<>(items.size());
    for (int i = 0; i < items.size(); i++) {
      TaskItem item = items.get(i);
      children.add(classifyTask(item, rawTasks.get(i), context));
    }
    return List.copyOf(children);
  }

  /**
   * Classifies one task. Its node id is always the task's own name — a named scope keeps that name
   * wherever it appears, including at a fork branch's root, so a flow's {@code children} keys are
   * always its {@code tasks} entries' names (design §D3, §D5).
   */
  private static CompiledNode classifyTask(TaskItem item, JsonNode rawItem, Context context) {
    String nodeId = item.getName();
    Task task = item.getTask();
    JsonNode rawBody = rawBody(rawItem, item.getName());
    if (task != null && task.getForTask() != null) {
      return forFlow(nodeId, task.getForTask(), rawBody, context);
    }
    if (task != null && task.getTryTask() != null) {
      return tryFlow(nodeId, task.getTryTask(), rawBody, context);
    }
    if (task != null && task.getForkTask() != null) {
      return forkFlow(nodeId, task.getForkTask(), rawBody, context);
    }
    return step(nodeId, task, rawItem, context);
  }

  /** A {@code for} scope: its own flow node over the task list in {@code for.do}. */
  private static CompiledNode forFlow(
      String nodeId, ForTask forTask, JsonNode rawBody, Context context) {
    JsonNode rawDo = rawArray(rawBody, "do");
    List<CompiledNode> children = classifyTasks(forTask.getDo(), rawDo, context);
    return flow(context, nodeId, SCOPE_FOR, elements(rawDo), children, null, null);
  }

  /**
   * A {@code try} scope: its own flow node over the guarded task list, plus a sibling {@code catch}
   * flow node when the definition supplies one. The catch node appears both in {@code children} and
   * in the dedicated {@code catch} field (design §D5).
   */
  private static CompiledNode tryFlow(
      String nodeId, TryTask tryTask, JsonNode rawBody, Context context) {
    JsonNode rawTry = rawArray(rawBody, "try");
    List<CompiledNode> children = new ArrayList<>(classifyTasks(tryTask.getTry(), rawTry, context));
    TryTaskCatch caught = tryTask.getCatch();
    String catchAppId = null;
    if (caught != null && caught.getDo() != null) {
      CompiledNode catchNode = catchFlow(nodeId, caught, rawBody.get("catch"), context);
      children.add(catchNode);
      catchAppId = catchNode.appId();
    }
    return flow(context, nodeId, SCOPE_TRY, elements(rawTry), children, catchAppId, null);
  }

  /** A {@code catch} scope: its own flow node over the recovery task list in {@code catch.do}. */
  private static CompiledNode catchFlow(
      String tryNodeId, TryTaskCatch caught, JsonNode rawCatch, Context context) {
    JsonNode rawDo = rawArray(rawCatch, "do");
    return flow(
        context,
        NodeNaming.catchNodeId(tryNodeId),
        SCOPE_CATCH,
        elements(rawDo),
        classifyTasks(caught.getDo(), rawDo, context),
        null,
        null);
  }

  /**
   * A {@code fork} scope (ADR 0003): its own flow node with an empty task list, one branch child
   * per {@code fork.branches} entry, and {@code forkMode} from {@code compete}.
   */
  private static CompiledNode forkFlow(
      String nodeId, ForkTask forkTask, JsonNode rawBody, Context context) {
    ForkTaskConfiguration configuration = forkTask.getFork();
    List<TaskItem> branches =
        configuration == null || configuration.getBranches() == null
            ? List.of()
            : configuration.getBranches();
    JsonNode rawBranches = rawArray(rawBody.get("fork"), "branches");
    requireAligned(branches, rawBranches);

    List<CompiledNode> children = new ArrayList<>(branches.size());
    for (int i = 0; i < branches.size(); i++) {
      children.add(branchFlow(nodeId, branches.get(i), rawBranches.get(i), context));
    }
    String forkMode =
        configuration != null && configuration.isCompete() ? FORK_MODE_ANY : FORK_MODE_ALL;
    return flow(context, nodeId, SCOPE_FORK, List.of(), children, null, forkMode);
  }

  /** One fork branch: its own flow node whose single child is the classified branch root task. */
  private static CompiledNode branchFlow(
      String forkNodeId, TaskItem branch, JsonNode rawBranch, Context context) {
    String branchNodeId = NodeNaming.branchNodeId(forkNodeId, branch.getName());
    CompiledNode root = classifyTask(branch, rawBranch, context);
    return flow(
        context,
        branchNodeId,
        SCOPE_FORK_BRANCH,
        List.of(rawBranch.deepCopy()),
        List.of(root),
        null,
        null);
  }

  /**
   * Every non-structural task kind, per design §D2 — including {@code switch}, which owns no list.
   */
  private static CompiledNode step(String nodeId, Task task, JsonNode rawItem, Context context) {
    String appId = NodeNaming.appId(nodeId);
    String functionAppId =
        task != null && (task.getCallTask() != null || task.getRunTask() != null)
            ? NodeNaming.functionAppId(appId)
            : null;
    return new StepNode(
        nodeId,
        appId,
        Names.nodeDefinitionResource(context.workflow(), context.versionId(), appId),
        SingleNodeDefinition.step(context.envelope(), appId, rawItem.deepCopy(), functionAppId),
        Optional.ofNullable(functionAppId));
  }

  private static FlowNode flow(
      Context context,
      String nodeId,
      String scope,
      List<JsonNode> tasks,
      List<CompiledNode> children,
      String catchAppId,
      String forkMode) {
    String appId = NodeNaming.appId(nodeId);
    return new FlowNode(
        nodeId,
        appId,
        Names.nodeDefinitionResource(context.workflow(), context.versionId(), appId),
        SingleNodeDefinition.flow(
            context.envelope(), appId, scope, tasks, childAppIds(children), catchAppId, forkMode),
        children);
  }

  /**
   * The wire-format {@code children} object: a render-time projection keyed by each child's own
   * {@link CompiledNode#key()} (ADR 0002), in source order — never a stored parent-side map.
   */
  private static Map<String, String> childAppIds(List<CompiledNode> children) {
    return children.stream()
        .collect(
            Collectors.toMap(
                CompiledNode::key,
                CompiledNode::appId,
                (first, second) -> first,
                LinkedHashMap::new));
  }

  private static List<JsonNode> elements(JsonNode rawTasks) {
    if (rawTasks == null || !rawTasks.isArray()) {
      return List.of();
    }
    List<JsonNode> elements = new ArrayList<>(rawTasks.size());
    rawTasks.forEach(element -> elements.add(element.deepCopy()));
    return List.copyOf(elements);
  }

  private static JsonNode rawArray(JsonNode parent, String field) {
    JsonNode array = parent == null ? null : parent.get(field);
    return array != null && array.isArray() ? array : null;
  }

  private static JsonNode rawBody(JsonNode rawItem, String name) {
    JsonNode body = rawItem == null ? null : rawItem.get(name);
    if (body == null || !body.isObject()) {
      throw new CompilationException(
          List.of(
              "task '"
                  + name
                  + "' could not be matched to its source text; the parsed definition and the "
                  + "submitted document disagree"));
    }
    return body;
  }

  private static void requireAligned(List<TaskItem> tasks, JsonNode rawTasks) {
    int rawSize = rawTasks == null || !rawTasks.isArray() ? 0 : rawTasks.size();
    if (rawSize != tasks.size()) {
      throw new CompilationException(
          List.of(
              "a task list could not be matched to its source text ("
                  + tasks.size()
                  + " parsed tasks, "
                  + rawSize
                  + " in the submitted document)"));
    }
  }
}
