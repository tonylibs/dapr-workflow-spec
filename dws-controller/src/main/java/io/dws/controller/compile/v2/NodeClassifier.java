package io.dws.controller.compile.v2;

import com.fasterxml.jackson.databind.JsonNode;
import io.dws.controller.compile.CompilationException;
import io.dws.controller.compile.Names;
import io.dws.controller.model.CompiledNode;
import io.dws.controller.model.DuplicateChildKeyException;
import io.dws.controller.model.FlowNode;
import io.dws.controller.model.FlowScope;
import io.dws.controller.model.SingleNodeDefinition;
import io.dws.controller.model.StepNode;
import io.serverlessworkflow.api.types.DoTask;
import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.ForkTask;
import io.serverlessworkflow.api.types.ForkTaskConfiguration;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.TryTask;
import io.serverlessworkflow.api.types.TryTaskCatch;
import io.serverlessworkflow.api.types.Workflow;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * Recursive descent over a parsed definition, producing the v2 Flow/Step graph (ADR 0001, ADR 0002,
 * ADR 0003).
 *
 * <p>Classification is the table in the change's design §D2: {@code main}, each nested {@code do},
 * each {@code for}, each {@code try}, each {@code catch}, each {@code fork} and each fork branch
 * become a {@link FlowNode}; every other task kind becomes a {@link StepNode}, with no exceptions.
 *
 * <p>The walk is driven by the typed model but reads every task object out of a parallel raw {@link
 * JsonNode} of the same definition text, so a node's rendered {@code tasks}/{@code task} carries
 * the definition's own JSON rather than a round-trip through the typed model (which drops unknown
 * fields and reorders keys). {@link RawTaskList} owns that raw half and the guards that keep the
 * two walks in step.
 *
 * <p>Children are classified before their parent is rendered, because a flow's {@code specText}
 * names its children's app IDs.
 */
@UtilityClass
public class NodeClassifier {

  private static final String SCOPE_MAIN = "main";
  private static final String SCOPE_DO = "do";
  private static final String SCOPE_FOR = "for";
  private static final String SCOPE_TRY = "try";
  private static final String SCOPE_CATCH = "catch";
  private static final String SCOPE_FORK = "fork";
  private static final String SCOPE_FORK_BRANCH = "forkBranch";

  private static final String FORK_MODE_ANY = "any";
  private static final String FORK_MODE_ALL = "all";

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
  public static CompiledNode classify(
      Workflow workflow,
      JsonNode rawSpec,
      SingleNodeDefinition.Envelope envelope,
      String workflowName,
      String versionId) {
    Context context = new Context(envelope, workflowName, versionId);
    try {
      return listFlow(
          NodeNaming.mainNodeId(workflowName),
          SCOPE_MAIN,
          workflow.getDo(),
          rawSpec,
          "do",
          context);
    } catch (DuplicateChildKeyException e) {
      // The model's child-key invariant, re-reported as a compile error. Translated here, at the
      // walk's one entry point, rather than at each construction site — a per-site wrapper is
      // exactly what a later site (an append, say) forgets.
      throw new CompilationException(List.of(e.getMessage()));
    }
  }

  /** Classifies one scope's task list, in source order, into that scope's child nodes. */
  private static List<CompiledNode> classifyTasks(
      List<TaskItem> tasks, RawTaskList rawTasks, Context context) {
    List<TaskItem> items = Optional.ofNullable(tasks).orElseGet(List::of);
    List<CompiledNode> children = new ArrayList<>(items.size());
    for (int i = 0; i < items.size(); i++) {
      children.add(classifyTask(items.get(i), rawTasks.get(i), context));
    }
    return List.copyOf(children);
  }

  /**
   * Classifies one task. Its node id is always the task's own name — a named scope keeps that name
   * wherever it appears, including at a fork branch's root, so a flow's {@code children} keys are
   * always its {@code tasks} entries' names (design §D3, §D5).
   */
  private static CompiledNode classifyTask(TaskItem item, RawTaskItem rawItem, Context context) {
    String nodeId = item.getName();
    NodeNaming.requireUndottedTaskName(nodeId);
    Optional<Task> task = Optional.ofNullable(item.getTask());
    JsonNode rawBody = rawItem.body(nodeId);
    return task.map(Task::getForTask)
        .map(forTask -> forFlow(nodeId, forTask, rawBody, context))
        .or(() -> task.map(Task::getTryTask).map(t -> tryFlow(nodeId, t, rawBody, context)))
        .or(() -> task.map(Task::getForkTask).map(t -> forkFlow(nodeId, t, rawBody, context)))
        .or(() -> task.map(Task::getDoTask).map(t -> doFlow(nodeId, t, rawBody, context)))
        .orElseGet(() -> step(nodeId, item.getTask(), rawItem, context));
  }

  /**
   * A nested {@code do} scope: its own flow node over the task list in {@code do}. A Step executes
   * one task and owns no list, so a task that owns one is a Flow — even though the DSL gives this
   * scope no keyword of its own beyond the list itself.
   */
  private static CompiledNode doFlow(
      String nodeId, DoTask doTask, JsonNode rawBody, Context context) {
    return listFlow(nodeId, SCOPE_DO, doTask.getDo(), rawBody, "do", context);
  }

  /** A {@code for} scope: its own flow node over the task list in {@code for.do}. */
  private static CompiledNode forFlow(
      String nodeId, ForTask forTask, JsonNode rawBody, Context context) {
    return listFlow(nodeId, SCOPE_FOR, forTask.getDo(), rawBody, "do", context);
  }

  /**
   * A {@code try} scope: its own flow node over the guarded task list, plus a sibling {@code catch}
   * flow node when the definition supplies a non-empty {@code catch.do}. The catch node appears
   * both in {@code children} and in the dedicated {@code catch} field (design §D5). A {@code catch}
   * with no {@code do} (e.g. retry-only recovery) gets no catch node and no {@code catch} field.
   *
   * <p>Note where the recovery configuration ends up: not here. This try node's own {@code
   * specText} carries the guarded task list but neither {@code errors} nor {@code retry} — those
   * survive verbatim only in the <em>parent's</em> {@code tasks} entry for this try task. Whether
   * that is correct is the unresolved scope-configuration question in the change's design (Open
   * Questions), which blocks Phase 2.
   */
  private static CompiledNode tryFlow(
      String nodeId, TryTask tryTask, JsonNode rawBody, Context context) {
    RawTaskList rawTry = RawTaskList.in(rawBody, "try", tryTask.getTry());
    Optional<CompiledNode> catchNode =
        Optional.ofNullable(tryTask.getCatch())
            .filter(caught -> caught.getDo() != null && !caught.getDo().isEmpty())
            .map(caught -> catchFlow(nodeId, caught, rawBody.get("catch"), context));

    // The catch node is built first either way: the try node's own scope has to name its app ID.
    FlowScope scope = FlowScope.of(SCOPE_TRY, rawTry.copies());
    FlowNode tryNode =
        flow(
            context,
            nodeId,
            catchNode.map(CompiledNode::appId).map(scope::withCatch).orElse(scope),
            classifyTasks(tryTask.getTry(), rawTry, context));
    return catchNode.map(tryNode::withChild).orElse(tryNode);
  }

  /** A {@code catch} scope: its own flow node over the recovery task list in {@code catch.do}. */
  private static CompiledNode catchFlow(
      String tryNodeId, TryTaskCatch caught, JsonNode rawCatch, Context context) {
    return listFlow(
        NodeNaming.catchNodeId(tryNodeId), SCOPE_CATCH, caught.getDo(), rawCatch, "do", context);
  }

  /**
   * A {@code fork} scope (ADR 0003): its own flow node with an empty task list, one branch child
   * per {@code fork.branches} entry, and {@code forkMode} from {@code compete}.
   */
  private static CompiledNode forkFlow(
      String nodeId, ForkTask forkTask, JsonNode rawBody, Context context) {
    Optional<ForkTaskConfiguration> configuration = Optional.ofNullable(forkTask.getFork());
    List<TaskItem> branches =
        configuration.map(ForkTaskConfiguration::getBranches).orElseGet(List::of);
    RawTaskList rawBranches = RawTaskList.in(rawBody.get("fork"), "branches", branches);

    List<CompiledNode> children = new ArrayList<>(branches.size());
    for (int i = 0; i < branches.size(); i++) {
      children.add(branchFlow(nodeId, branches.get(i), rawBranches.get(i), context));
    }
    String forkMode =
        configuration.filter(ForkTaskConfiguration::isCompete).isPresent()
            ? FORK_MODE_ANY
            : FORK_MODE_ALL;
    return flow(
        context, nodeId, FlowScope.of(SCOPE_FORK, List.of()).withForkMode(forkMode), children);
  }

  /** One fork branch: its own flow node whose single child is the classified branch root task. */
  private static CompiledNode branchFlow(
      String forkNodeId, TaskItem branch, RawTaskItem rawBranch, Context context) {
    String branchNodeId = NodeNaming.branchNodeId(forkNodeId, branch.getName());
    CompiledNode root = classifyTask(branch, rawBranch, context);
    return flow(
        context,
        branchNodeId,
        FlowScope.of(SCOPE_FORK_BRANCH, List.of(rawBranch.copy())),
        List.of(root));
  }

  /**
   * Every non-structural task kind, per design §D2 — including {@code switch}, which owns no list.
   */
  private static CompiledNode step(String nodeId, Task task, RawTaskItem rawItem, Context context) {
    String appId = NodeNaming.appId(nodeId);
    String functionAppId =
        Optional.ofNullable(task)
            .filter(t -> t.getCallTask() != null || t.getRunTask() != null)
            .map(t -> NodeNaming.functionAppId(appId))
            .orElse(null);
    return new StepNode(
        nodeId,
        appId,
        Names.nodeDefinitionResource(context.workflow(), context.versionId(), appId),
        SingleNodeDefinition.step(context.envelope(), appId, rawItem.copy(), functionAppId),
        Optional.ofNullable(functionAppId));
  }

  /**
   * The shape every scope shares whose whole body is one task list read from a single field:
   * classify that list, then render a flow node over it.
   */
  private static CompiledNode listFlow(
      String nodeId,
      String scope,
      List<TaskItem> tasks,
      JsonNode rawParent,
      String rawField,
      Context context) {
    RawTaskList rawTasks = RawTaskList.in(rawParent, rawField, tasks);
    List<CompiledNode> children = classifyTasks(tasks, rawTasks, context);
    return flow(context, nodeId, FlowScope.of(scope, rawTasks.copies()), children);
  }

  /**
   * Builds one flow node. The node renders its own {@code specText}, so all this supplies is the
   * scope and the already-classified children. A duplicate child key throws from the constructor
   * and is translated to a compile error by {@link #classify}.
   */
  private static FlowNode flow(
      Context context, String nodeId, FlowScope scope, List<CompiledNode> children) {
    String appId = NodeNaming.appId(nodeId);
    return new FlowNode(
        nodeId,
        appId,
        Names.nodeDefinitionResource(context.workflow(), context.versionId(), appId),
        context.envelope(),
        scope,
        children);
  }
}
