package io.dws.orchestrator.workflow;

import java.util.ArrayList;
import java.util.List;
import one.util.streamex.StreamEx;

/**
 * The two identifying signals threaded through every task dispatch so {@code call: a2a}'s
 * deterministic {@code messageId} derivation (ADR 0004 Decision 8) has something stable to key off
 * — independent of the durable-task child-instance machinery and of whatever jq variable names a
 * workflow author happens to choose.
 *
 * <p>{@code rootInstanceId} is the top-level workflow instance's {@code
 * WorkflowContext#getInstanceId()}, captured exactly once in {@link InterpreterWorkflow#execute}
 * and threaded unchanged through every {@link ForkBranchWorkflow}/{@link ScopeRunnerWorkflow} child
 * instance created along the way (a task-level {@code timeout} guard, or a {@code try}/{@code
 * catch.retry} per-attempt guard). A child instance's own {@code ctx.getInstanceId()} is a fresh,
 * durabletask-derived value on every creation — reading it directly from inside a child would give
 * two attempts of what the DSL author considers one logical call two different ids, defeating
 * dedupe on exactly the retry/timeout case it exists for. Threading the value explicitly, rather
 * than re-reading it inside the child, is what keeps it stable across attempts.
 *
 * <p>{@code iterationPath} is the sequence of {@code for} loop indices the current task is nested
 * inside, outermost first, appended to by {@link InterpreterWorkflow#dispatchFor} for each loop
 * nesting level it enters. This replaces an earlier mechanism that looked the index up by a fixed
 * jq variable name ({@code "index"}) in the merged scope variables — that collided whenever two
 * different loops bound their index under a name this internal bookkeeping could not tell apart
 * (two nested loops both using the default {@code for.at} name, or an inner loop using a custom
 * {@code for.at} name nested inside an outer default-named loop). Threading the position
 * explicitly, independent of author-chosen binding names, makes that collision structurally
 * impossible: two dispatches at different logical positions always produce different paths.
 */
public record DispatchContext(String rootInstanceId, List<Integer> iterationPath) {

  public DispatchContext(String rootInstanceId, List<Integer> iterationPath) {
    this.rootInstanceId = rootInstanceId;
    this.iterationPath = List.copyOf(iterationPath);
  }

  /** The root context for a fresh top-level dispatch: no enclosing {@code for} loop yet. */
  public static DispatchContext root(String rootInstanceId) {
    return new DispatchContext(rootInstanceId, List.of());
  }

  /** A copy of this context with {@code index} appended as the innermost iteration position. */
  public DispatchContext withIteration(int index) {
    List<Integer> next = new ArrayList<>(iterationPath);
    next.add(index);
    return new DispatchContext(rootInstanceId, next);
  }

  /**
   * The dot-joined encoding of {@link #iterationPath} (e.g. {@code "1.0"} for the second outer
   * iteration's first inner iteration), or {@code null} when this task is not nested inside any
   * {@code for} loop — the top-level/non-looped case, matching the "no iteration context" fallback
   * {@code dws-call-a2a}'s {@code messageId} derivation already documents for a missing header. The
   * exact separator is an internal encoding detail: {@code dws-call-a2a} treats the whole value as
   * an opaque string and never parses it.
   */
  public String iterationIndexEncoded() {
    return iterationPath.isEmpty()
        ? null
        : StreamEx.of(iterationPath).map(String::valueOf).joining(".");
  }
}
