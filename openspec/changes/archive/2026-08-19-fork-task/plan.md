# Fork Task Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Interpret the OWS DSL 1.0 `fork` task in `dws-orchestrator` (concurrent branches, join/race
via `compete`) and walk `fork`/`for` bodies in `dws-controller` so nested `call`/`run` deploy step
services — roadmap Phase 2 slice 2.4, the last slice of Phase 2.

**Architecture:** Each `fork` branch runs as its own child Dapr workflow instance
(`ForkBranchWorkflow`), started concurrently via `WorkflowContext.callChildWorkflow` and combined
with `ctx.allOf` (join, `compete: false`) or `ctx.anyOf` (race, `compete: true`). The branch
workflow delegates its whole body to the existing `InterpreterWorkflow.dispatch` pipeline
(widened to package-private for reuse), so nested `try`/`for`/`fork` inside a branch work with zero
duplicated dispatch logic. `DefinitionLookup.search` and `WorkflowCompiler.walk()`/
`collectTaskNames()` each gain a `ForkTask` branch (plus a `ForTask` branch on the controller side,
closing `for-task`'s logged gap), mirroring the `TryTask` branch each already has.

**Tech Stack:** Java 25, Spring Boot (`dws-orchestrator`); Java 25, Quarkus (`dws-controller`);
`dapr-sdk-workflows:1.18.0`; `serverlessworkflow-api:7.26.0.Final`; JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Task names are unique across the whole definition at every depth (existing invariant; a `call`/
  `run` task's Dapr app-id derives from its name alone). `dws-controller` rejects duplicates at
  compile time — this slice extends that rejection to `fork` branches and `for.do`.
- `dws-orchestrator`: `cd dws-orchestrator && ./mvnw verify` (compile + test). Single class:
  `./mvnw test -Dtest=InterpreterWorkflowIntegrationTest`.
- `dws-controller`: `cd dws-controller && ./mvnw test` (unit; `./mvnw verify` for packaged
  integration tests, not needed for this change). Single class:
  `./mvnw test -Dtest=WorkflowCompilerTest`.
- No new Maven dependency in either component — `ctx.allOf`/`ctx.anyOf`/`callChildWorkflow` are
  already on the pinned `dapr-sdk-workflows:1.18.0` classpath (verified via `javap`).
- Preserve immutable, content-addressed workflow definition versioning — this change adds no new
  version-affecting field to the compiler's canonicalization path.
- Commit after each task with the repo's plain, descriptive commit-message style (see recent log:
  `git log --oneline -10` in each component's directory) — no fixed prefix convention observed in
  this repo, so match whatever the two or three most recent commits touching these files do.

---

## Task 1: `DefinitionLookup` — recurse into `fork` branches; widen for cross-package reuse

**Files:**
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/DefinitionLookup.java`
- Test: `dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/activity/DefinitionLookupTest.java`

**Interfaces:**
- Produces: `public static Task taskByName(String taskName)` (was package-private via a
  package-private class — both widened to `public` here so `ForkBranchWorkflow`, in the sibling
  `io.dws.orchestrator.workflow` package, can call it in Task 4).

- [ ] **Step 1: Write the failing tests** — add to the existing `DefinitionLookupTest`
      (`src/test/java/io/dws/orchestrator/workflow/activity/DefinitionLookupTest.java`), after the
      existing `taskInsideForDoInsideTryResolvesByName` test:

```java
  @Test
  void taskInsideForkBranchResolvesByName() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: lookup-workflow
          version: '1.0.0'
        do:
          - raiseAlarm:
              fork:
                compete: false
                branches:
                  - callNurse:
                      set:
                        paged: '"nurse"'
                  - callSecurity:
                      set:
                        paged: '"security"'
        """);
    Task callNurse = DefinitionLookup.taskByName("callNurse");
    assertThat(callNurse.getSetTask()).isNotNull();
    Task callSecurity = DefinitionLookup.taskByName("callSecurity");
    assertThat(callSecurity.getSetTask()).isNotNull();
  }

  @Test
  void taskInsideForkBranchInsideTryResolvesByName() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: lookup-workflow
          version: '1.0.0'
        do:
          - guarded:
              try:
                - raiseAlarm:
                    fork:
                      branches:
                        - deeplyNested:
                            set:
                              done: '"yes"'
              catch:
                do: []
        """);
    Task task = DefinitionLookup.taskByName("deeplyNested");
    assertThat(task.getSetTask()).isNotNull();
  }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd dws-orchestrator && ./mvnw test -Dtest=DefinitionLookupTest`
Expected: FAIL — `taskByName` throws `IllegalStateException: definition has no task named
'callNurse'` (no `ForkTask` recursion branch exists yet).

- [ ] **Step 3: Add the `ForkTask` recursion branch and widen visibility**

In `DefinitionLookup.java`, add the import and widen the class/method:

```java
import io.serverlessworkflow.api.types.ForkTask;
```

Change the class declaration and `taskByName` from package-private to public:

```java
public final class DefinitionLookup {

  private DefinitionLookup() {}

  public static Task taskByName(String taskName) {
```

Add the new branch to `search`, immediately after the existing `ForTask` block (which ends
`if (nested != null) { return nested; }` followed by `}`), before the loop's closing brace:

```java
      ForTask forTask = task.getForTask();
      if (forTask != null) {
        Task nested = search(forTask.getDo(), taskName);
        if (nested != null) {
          return nested;
        }
      }
      ForkTask forkTask = task.getForkTask();
      if (forkTask != null) {
        Task nested = search(forkTask.getFork().getBranches(), taskName);
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
  }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd dws-orchestrator && ./mvnw test -Dtest=DefinitionLookupTest`
Expected: PASS (all five tests, including the three pre-existing ones).

- [ ] **Step 5: Commit**

```bash
git -C dws-orchestrator add src/main/java/io/dws/orchestrator/workflow/activity/DefinitionLookup.java src/test/java/io/dws/orchestrator/workflow/activity/DefinitionLookupTest.java
git commit -m "orchestrator: resolve fork-branch task names in DefinitionLookup"
```

---

## Task 2: `WorkflowCompiler` — walk `fork` branches and `for.do`; extend duplicate-name detection

**Files:**
- Modify: `dws-controller/src/main/java/io/dws/controller/compile/WorkflowCompiler.java`
- Test: `dws-controller/src/test/java/io/dws/controller/compile/WorkflowCompilerTest.java`

**Interfaces:**
- Consumes: none new.
- Produces: no new public surface — `walk()`/`collectTaskNames()` stay `private`; behavior change
  only (definitions nesting `call`/`run`/`emit`/`listen` under `fork` branches or `for.do` now
  produce `StepService`/`TopicBinding` entries; duplicate names in either are now rejected).

- [ ] **Step 1: Write the failing tests** — add to `WorkflowCompilerTest.java`, after the existing
      `nestedTryTasksProduceTopicBindings` test and before `duplicateTaskNameAcrossDepthsRejected`:

```java
  @Test
  @DisplayName("call/run tasks nested in fork branches compile to step services")
  void nestedForkBranchesCompileToStepServices() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: forkcompile
          version: '1.0.0'
        do:
          - raiseAlarm:
              fork:
                compete: true
                branches:
                  - callNurse:
                      call: http
                      with:
                        method: post
                        endpoint: http://paging.local/api/nurse
                  - callSecurity:
                      run:
                        shell:
                          command: "page-security"
        """;

    DeploymentPlan plan = compiler.compile(yaml);

    assertThat(plan.steps())
        .extracting(StepService::name)
        .containsExactlyInAnyOrder("call-nurse", "call-security");
    assertThat(step(plan, "call-nurse").kind()).isEqualTo(TaskKind.CALL_HTTP);
    assertThat(step(plan, "call-security").kind()).isEqualTo(TaskKind.RUN_SHELL);
  }

  @Test
  @DisplayName("call/run tasks nested in for.do compile to step services")
  void nestedForDoCompilesToStepServices() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: forcompile
          version: '1.0.0'
        do:
          - loop:
              for:
                each: item
                in: .items
              do:
                - fetchItem:
                    call: http
                    with:
                      method: get
                      endpoint: http://catalog.local/api/item
        """;

    DeploymentPlan plan = compiler.compile(yaml);

    assertThat(plan.steps()).extracting(StepService::name).containsExactly("fetch-item");
    assertThat(step(plan, "fetch-item").kind()).isEqualTo(TaskKind.CALL_HTTP);
  }

  @Test
  @DisplayName("a fork task whose branches are all in-process compiles to an unchanged resource set")
  void forkWithInProcessBranchesCompilesUnchanged() {
    DeploymentPlan withoutFork = compiler.compile(fixture("order.yaml"));

    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: forkinprocess
          version: '1.0.0'
        do:
          - raiseAlarm:
              fork:
                branches:
                  - callNurse:
                      set:
                        paged: '"nurse"'
                  - callSecurity:
                      set:
                        paged: '"security"'
        """;
    DeploymentPlan withFork = compiler.compile(yaml);

    assertThat(withFork.steps()).isEmpty();
    assertThat(withFork.bindings()).isEmpty();
    // Sanity: the unrelated order.yaml plan is untouched by compiling a second definition.
    assertThat(withoutFork.steps()).hasSize(3);
  }

  @Test
  @DisplayName("a duplicate task name across two fork branches is rejected")
  void duplicateTaskNameAcrossForkBranchesRejected() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: dupfork
          version: '1.0.0'
        do:
          - raiseAlarm:
              fork:
                branches:
                  - callNurse:
                      set:
                        paged: '"nurse"'
                  - callNurse:
                      set:
                        paged: '"again"'
        """;

    assertThatThrownBy(() -> compiler.compile(yaml))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("callNurse");
  }

  @Test
  @DisplayName("a duplicate task name inside for.do is rejected")
  void duplicateTaskNameInsideForDoRejected() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: dupfor
          version: '1.0.0'
        do:
          - fetchOrder:
              call: http
              with:
                method: get
                endpoint: http://orders.local/api/a
          - loop:
              for:
                each: item
                in: .items
              do:
                - fetchOrder:
                    call: http
                    with:
                      method: get
                      endpoint: http://orders.local/api/b
        """;

    assertThatThrownBy(() -> compiler.compile(yaml))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("fetchOrder");
  }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd dws-controller && ./mvnw test -Dtest=WorkflowCompilerTest`
Expected: FAIL — `nestedForkBranchesCompileToStepServices` and `nestedForDoCompilesToStepServices`
assert `plan.steps()` is non-empty but `walk()` currently emits nothing for either; the two
duplicate-name tests expect a `CompilationException` that currently isn't thrown (no error, since
`collectTaskNames` doesn't look inside `fork`/`for` yet).

- [ ] **Step 3: Add the `ForkTask` and `ForTask` branches to `walk()` and `collectTaskNames()`**

Add imports:

```java
import io.serverlessworkflow.api.types.ForkTask;
import io.serverlessworkflow.api.types.ForTask;
```

In `walk()`, insert two branches after the existing `TryTask` branch's closing `}` (right before
the `// switch/set/wait/for/raise (and the task lists nested under for/fork) deploy nothing.`
comment, which becomes stale and is updated), and update that comment:

```java
      } else if (task.getTryTask() != null) {
        // A try task deploys nothing itself, but the tasks nested in its try/catch.do lists are
        // ordinary tasks and need their own step services — the orchestrator invokes them by the
        // same kebab-cased app-id it uses for a top-level task.
        TryTask tryTask = task.getTryTask();
        walk(tryTask.getTry(), steps, bindings);
        if (tryTask.getCatch() != null) {
          walk(tryTask.getCatch().getDo(), steps, bindings);
        }
      } else if (task.getForkTask() != null) {
        // Same reasoning as try: a fork task deploys nothing itself, but each branch's task is
        // dispatched exactly like a top-level task, so it needs its own step service/binding.
        walk(task.getForkTask().getFork().getBranches(), steps, bindings);
      } else if (task.getForTask() != null) {
        // Same reasoning again: the body of for.do is dispatched once per iteration exactly like
        // a top-level task list.
        walk(task.getForTask().getDo(), steps, bindings);
      }
      // switch/set/wait/raise deploy nothing themselves; their nested container types (try, fork,
      // for) are all walked above.
    }
  }
```

In `collectTaskNames()`, insert the same two checks after the existing `TryTask` check:

```java
    for (TaskItem item : tasks) {
      if (!seen.add(item.getName())) {
        duplicates.add(item.getName());
      }
      TryTask tryTask = item.getTask() == null ? null : item.getTask().getTryTask();
      if (tryTask != null) {
        collectTaskNames(tryTask.getTry(), seen, duplicates);
        if (tryTask.getCatch() != null) {
          collectTaskNames(tryTask.getCatch().getDo(), seen, duplicates);
        }
      }
      ForkTask forkTask = item.getTask() == null ? null : item.getTask().getForkTask();
      if (forkTask != null) {
        collectTaskNames(forkTask.getFork().getBranches(), seen, duplicates);
      }
      ForTask forTask = item.getTask() == null ? null : item.getTask().getForTask();
      if (forTask != null) {
        collectTaskNames(forTask.getDo(), seen, duplicates);
      }
    }
  }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd dws-controller && ./mvnw test -Dtest=WorkflowCompilerTest`
Expected: PASS (all tests, including the five pre-existing `try`-nesting/duplicate-name tests —
confirm no regression there).

- [ ] **Step 5: Commit**

```bash
git -C dws-controller add src/main/java/io/dws/controller/compile/WorkflowCompiler.java src/test/java/io/dws/controller/compile/WorkflowCompilerTest.java
git commit -m "controller: walk fork branches and for.do, extend duplicate-name detection"
```

---

## Task 3: `ForkBranchInput`; widen `InterpreterWorkflow.dispatch`/`Dispatch` for cross-class reuse

**Files:**
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/ForkBranchInput.java`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/InterpreterWorkflow.java`

**Interfaces:**
- Produces: `record ForkBranchInput(String taskName, JsonNode data, JsonNode context, Map<String,
  JsonNode> variables, int depth)`; `Dispatch dispatch(WorkflowContext, Task, String, JsonNode,
  JsonNode, Map<String, JsonNode>, int, AdminEventBuilder, ObjectMapper)` (widened from `private`
  to package-private); `record Dispatch(JsonNode data, JsonNode context, FlowOutcome then,
  ScopeEnd end)` (widened from `private` to package-private, since it is `dispatch`'s return type
  and must be nameable from `ForkBranchWorkflow` in Task 4).
- Consumed by: `ForkBranchWorkflow` (Task 4).

No new test in this task — it is a pure visibility change plus one new data-only record, exercised
indirectly by Task 4's `ForkBranchWorkflowTest` and Task 7's integration tests. (Right-sizing per
the plan's own convention: a visibility change has no independent behavior to test until something
calls through it — Task 4 is that caller.)

- [ ] **Step 1: Create `ForkBranchInput.java`**

```java
package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * The input a {@link ForkBranchWorkflow} child instance receives: which branch task to dispatch,
 * by name, and the data/context/variables/depth to dispatch it with.
 *
 * <p>The branch's {@code Task} object itself is not carried across the child-workflow boundary —
 * {@code taskName} is unique across the whole definition (the same invariant every other
 * name-based lookup in this codebase relies on), so {@link
 * io.dws.orchestrator.workflow.activity.DefinitionLookup#taskByName} resolves it from the pod's
 * own pinned definition, identically to how any in-process activity resolves its target.
 */
public record ForkBranchInput(
    String taskName,
    JsonNode data,
    JsonNode context,
    Map<String, JsonNode> variables,
    int depth) {}
```

- [ ] **Step 2: Widen `dispatch` and `Dispatch` in `InterpreterWorkflow.java`**

Change the `Dispatch` record declaration (currently `private record Dispatch(...)`) to
package-private:

```java
  /**
   * The post-dispatch data and context documents, the task's resolved flow outcome, and — for a
   * task whose body is itself a task scope ({@code try}) — how that inner scope ended.
   *
   * <p>Package-private (not {@code private}) so {@link ForkBranchWorkflow} can call {@link
   * #dispatch} and read its result — a fork branch dispatches its one task through the exact same
   * pipeline a top-level task uses.
   */
  record Dispatch(JsonNode data, JsonNode context, FlowOutcome then, ScopeEnd end) {}
```

Change the `dispatch` method signature (currently `private Dispatch dispatch(`) to
package-private:

```java
  /**
   * Runs one task item's full Open Workflow Specification data-flow pipeline around its body:
   * {@code input.from}/{@code input.schema} before, {@code output.as}/{@code output.schema} and
   * {@code export.as}/{@code export.schema} after. Both phases are skipped entirely — no activity
   * scheduled, data passed straight through — for a task that declares no {@code input}/{@code
   * output}/{@code export}, which is every definition that predates this pipeline.
   *
   * <p>Package-private (not {@code private}) so {@link ForkBranchWorkflow} can dispatch a fork
   * branch's one task through this exact pipeline, with no duplicated dispatch logic.
   */
  Dispatch dispatch(
      WorkflowContext ctx,
      Task task,
      String name,
      JsonNode data,
      JsonNode context,
      Map<String, JsonNode> variables,
      int depth,
      AdminEventBuilder events,
      ObjectMapper mapper) {
```

(The method body is unchanged — only the two modifiers move from `private` to package-private.)

- [ ] **Step 3: Compile to confirm the visibility change alone is sound**

Run: `cd dws-orchestrator && ./mvnw test-compile`
Expected: BUILD SUCCESS (no behavior changed yet; this just confirms nothing else in the file
depended on `Dispatch`/`dispatch` staying `private` in a way that breaks).

- [ ] **Step 4: Commit**

```bash
git -C dws-orchestrator add src/main/java/io/dws/orchestrator/workflow/ForkBranchInput.java src/main/java/io/dws/orchestrator/workflow/InterpreterWorkflow.java
git commit -m "orchestrator: add ForkBranchInput, widen dispatch/Dispatch for fork-branch reuse"
```

---

## Task 4: `ForkBranchWorkflow` — dispatch one branch as its own child workflow instance

**Files:**
- Create: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/ForkBranchWorkflow.java`
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/DefinitionLookup.java`
  (widen to `public`, if not already done as part of Task 1 — Task 1 already makes this change;
  this task only *consumes* the now-public `taskByName`)
- Test: Create `dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/ForkBranchWorkflowTest.java`

**Interfaces:**
- Consumes: `ForkBranchInput` (Task 3), `InterpreterWorkflow.dispatch`/`Dispatch` (Task 3, now
  package-private), `DefinitionLookup.taskByName` (Task 1, now `public`), `AdminEventBuilder
  .forContext(WorkflowContext)`, `WorkflowSupport.mapper()`.
- Produces: `public static final String NAME = "dws-fork-branch"` (the registration name Task 6
  registers and Task 5's `dispatchFork` calls via `ctx.callChildWorkflow`); `public void
  execute(WorkflowContext ctx)`.

- [ ] **Step 1: Write the failing test** — create
      `src/test/java/io/dws/orchestrator/workflow/ForkBranchWorkflowTest.java`:

```java
package io.dws.orchestrator.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.durabletask.Task;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.activity.AdminEventActivity;
import io.dws.orchestrator.workflow.activity.CatchDecision;
import io.dws.orchestrator.workflow.activity.CatchDecisionActivity;
import io.dws.orchestrator.workflow.activity.CatchDecisionRequest;
import io.dws.orchestrator.workflow.activity.EvaluateSetActivity;
import io.dws.orchestrator.workflow.activity.EvaluateSetRequest;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link ForkBranchWorkflow} resolves its one branch task by name and dispatches it
 * through {@link InterpreterWorkflow#dispatch} — the same pipeline a top-level task uses,
 * including a branch whose task is itself a container ({@code try}).
 */
class ForkBranchWorkflowTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final ForkBranchWorkflow workflow = new ForkBranchWorkflow();

  private void seed(String yaml) throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromString(yaml, WorkflowFormat.YAML);
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "fork-branch-workflow",
        "fork-branch-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        null,
        mock(WorkflowTaskOptions.class),
        "pubsub");
  }

  @SuppressWarnings("unchecked")
  private void stubAdminEvents(WorkflowContext ctx) {
    when(ctx.getInstanceId()).thenReturn("inst-1/raiseAlarm/callNurse");
    when(ctx.getCurrentInstant()).thenReturn(Instant.parse("2026-08-17T00:00:00Z"));
    Task<Void> adminTask = mock(Task.class);
    when(adminTask.await()).thenReturn(null);
    when(ctx.callActivity(
            eq(AdminEventActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(Void.class)))
        .thenReturn(adminTask);
  }

  @SuppressWarnings("unchecked")
  private static <T> Task<T> completed(T value) {
    Task<T> task = mock(Task.class);
    when(task.thenApply(any()))
        .thenAnswer(
            invocation -> {
              Function<T, Object> transform = invocation.getArgument(0);
              Task<Object> mapped = mock(Task.class);
              when(mapped.await()).thenAnswer(ignored -> transform.apply(task.await()));
              return mapped;
            });
    when(task.await()).thenReturn(value);
    return task;
  }

  @Test
  @SuppressWarnings("unchecked")
  void dispatchesALeafBranchTaskByName() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: fork-branch-workflow
          version: '1.0.0'
        do:
          - raiseAlarm:
              fork:
                branches:
                  - callNurse:
                      set:
                        paged: '"nurse"'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubAdminEvents(ctx);
    when(ctx.getInput(ForkBranchInput.class))
        .thenReturn(
            new ForkBranchInput(
                "callNurse", mapper.readTree("{\"seed\":1}"), mapper.createObjectNode(), Map.of(), 1));
    when(ctx.callActivity(
            eq(EvaluateSetActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv -> completed(EvaluateSetActivity.apply((EvaluateSetRequest) inv.getArgument(1))));

    workflow.execute(ctx);

    org.mockito.ArgumentCaptor<Object> output = org.mockito.ArgumentCaptor.forClass(Object.class);
    org.mockito.Mockito.verify(ctx).complete(output.capture());
    JsonNode result = (JsonNode) output.getValue();
    assertThat(result.get("seed").intValue()).isEqualTo(1);
    assertThat(result.get("paged").textValue()).isEqualTo("nurse");
  }

  @Test
  @SuppressWarnings("unchecked")
  void dispatchesAContainerBranchTaskWithItsOwnCatchRecovery() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: fork-branch-workflow
          version: '1.0.0'
        do:
          - raiseAlarm:
              fork:
                branches:
                  - guarded:
                      try:
                        - explode:
                            raise:
                              error:
                                type: https://example.com/errors/x
                                status: 500
                                title: Boom
                                detail: boom
                      catch:
                        do:
                          - recovered:
                              set:
                                paged: '"recovered"'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubAdminEvents(ctx);
    when(ctx.getInput(ForkBranchInput.class))
        .thenReturn(
            new ForkBranchInput(
                "guarded", mapper.readTree("{}"), mapper.createObjectNode(), Map.of(), 1));
    when(ctx.callActivity(
            eq(io.dws.orchestrator.workflow.activity.RaiseErrorActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv ->
                completed(
                    io.dws.orchestrator.workflow.activity.RaiseErrorActivity.apply(
                        (io.dws.orchestrator.workflow.activity.RaiseErrorRequest)
                            inv.getArgument(1))));
    when(ctx.callActivity(
            eq(CatchDecisionActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(CatchDecision.class)))
        .thenAnswer(
            inv -> {
              CatchDecisionRequest req = inv.getArgument(1);
              return completed(
                  new CatchDecision(true, false, 0L, mapper.readTree("{\"status\":500}"), "error"));
            });
    when(ctx.callActivity(
            eq(EvaluateSetActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv -> completed(EvaluateSetActivity.apply((EvaluateSetRequest) inv.getArgument(1))));

    workflow.execute(ctx);

    org.mockito.ArgumentCaptor<Object> output = org.mockito.ArgumentCaptor.forClass(Object.class);
    org.mockito.Mockito.verify(ctx).complete(output.capture());
    JsonNode result = (JsonNode) output.getValue();
    assertThat(result.get("paged").textValue()).isEqualTo("recovered");
  }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd dws-orchestrator && ./mvnw test -Dtest=ForkBranchWorkflowTest`
Expected: FAIL to compile — `ForkBranchWorkflow` does not exist yet.

- [ ] **Step 3: Create `ForkBranchWorkflow.java`**

```java
package io.dws.orchestrator.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowStub;
import io.dws.orchestrator.workflow.activity.DefinitionLookup;
import io.serverlessworkflow.api.types.Task;

/**
 * Runs exactly one {@code fork} branch as its own, independent, deterministic workflow instance.
 *
 * <p>Registered under {@link #NAME} — not derived from {@code document.name} — so it never
 * collides with the top-level workflow's own registration. {@link
 * InterpreterWorkflow}'s {@code dispatchFork} starts one instance of this workflow per branch via
 * {@link WorkflowContext#callChildWorkflow}, without awaiting it immediately, so several branches
 * run concurrently and are combined at the call site with {@code allOf}/{@code anyOf}.
 *
 * <p>Delegates its entire body to {@link InterpreterWorkflow#dispatch}, the same per-task pipeline
 * every top-level task already goes through (data flow, nested {@code try}/{@code for}/{@code
 * fork}, lifecycle events) — a branch is dispatched exactly like a top-level task would be, with
 * zero duplicated dispatch logic.
 */
public class ForkBranchWorkflow implements Workflow {

  public static final String NAME = "dws-fork-branch";

  @Override
  public WorkflowStub create() {
    return this::execute;
  }

  /** Extracted from the {@link WorkflowStub} lambda so it can be driven directly in tests. */
  public void execute(WorkflowContext ctx) {
    ObjectMapper mapper = WorkflowSupport.mapper();
    ForkBranchInput input = ctx.getInput(ForkBranchInput.class);
    AdminEventBuilder events = AdminEventBuilder.forContext(ctx);

    Task task = DefinitionLookup.taskByName(input.taskName());
    InterpreterWorkflow.Dispatch result =
        new InterpreterWorkflow()
            .dispatch(
                ctx,
                task,
                input.taskName(),
                input.data(),
                input.context(),
                input.variables(),
                input.depth(),
                events,
                mapper);
    ctx.complete(result.data());
  }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd dws-orchestrator && ./mvnw test -Dtest=ForkBranchWorkflowTest`
Expected: PASS (both tests).

- [ ] **Step 5: Commit**

```bash
git -C dws-orchestrator add src/main/java/io/dws/orchestrator/workflow/ForkBranchWorkflow.java src/test/java/io/dws/orchestrator/workflow/ForkBranchWorkflowTest.java
git commit -m "orchestrator: add ForkBranchWorkflow, dispatching one fork branch per child instance"
```

---

## Task 5: `InterpreterWorkflow` — `dispatchFork` and dispatch wiring

**Files:**
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/InterpreterWorkflow.java`

**Interfaces:**
- Consumes: `ForkBranchInput`, `ForkBranchWorkflow.NAME` (Tasks 3-4).
- Produces: `case ForkTask forkTask -> dispatchFork(...)` wired into `dispatchBody`/
  `dispatchConcreteTask`; `taskTypeOf` returns `"fork"` for a fork task. No test file created in
  this task — Task 7 covers `dispatchFork` behavior via `InterpreterWorkflowIntegrationTest`,
  because exercising it meaningfully requires stubbing `ctx.callChildWorkflow`/`allOf`/`anyOf`,
  which Task 7 sets up once for all fork scenarios rather than duplicating the setup here.

- [ ] **Step 1: Add imports**

At the top of `InterpreterWorkflow.java`, add:

```java
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.dapr.durabletask.Task;
```

(`io.serverlessworkflow.api.types.*` already covers `ForkTask`/`TaskItem` via the existing
wildcard import.)

- [ ] **Step 2: Wire `ForkTask` into `dispatchBody`'s task-type list**

Change:

```java
    return StreamEx.of(
            task.getSwitchTask(),
            task.getCallTask(),
            task.getRunTask(),
            task.getSetTask(),
            task.getWaitTask(),
            task.getListenTask(),
            task.getEmitTask(),
            task.getForTask(),
            task.getTryTask(),
            task.getRaiseTask())
```

to:

```java
    return StreamEx.of(
            task.getSwitchTask(),
            task.getCallTask(),
            task.getRunTask(),
            task.getSetTask(),
            task.getWaitTask(),
            task.getListenTask(),
            task.getEmitTask(),
            task.getForTask(),
            task.getForkTask(),
            task.getTryTask(),
            task.getRaiseTask())
```

- [ ] **Step 3: Wire the `ForkTask` case into `dispatchConcreteTask`'s switch**

Change:

```java
      case ForTask forTask ->
          dispatchFor(ctx, forTask, name, data, context, variables, depth, events, mapper);
      default -> throw new IllegalStateException("task '" + name + "' has an unsupported type");
```

to:

```java
      case ForTask forTask ->
          dispatchFor(ctx, forTask, name, data, context, variables, depth, events, mapper);
      case ForkTask forkTask ->
          dispatchFork(ctx, forkTask, name, data, context, variables, depth, events, mapper);
      default -> throw new IllegalStateException("task '" + name + "' has an unsupported type");
```

- [ ] **Step 4: Add the `dispatchFork` method** — place it directly after `dispatchFor` (which ends
      just before `private static String nameOr(...)`):

```java
  /**
   * Runs a fork task: starts each branch as its own child workflow instance concurrently, then
   * either waits for all of them ({@code compete: false}, returning their outputs as a JSON array
   * in declared branch order) or races them ({@code compete: true}, returning whichever settles
   * first and never awaiting the rest).
   *
   * <p>Each branch runs as an independent {@link ForkBranchWorkflow} instance so its own body can
   * be an arbitrary multi-step task (including a nested {@code try}/{@code for}/{@code fork})
   * without needing the interpreter's "await eagerly" dispatch style to become non-blocking — the
   * concurrency comes from running N deterministic instances side by side, combined here with the
   * context's own {@code allOf}/{@code anyOf}. {@code $context} does not thread between branches
   * or back out: the context leaving {@code fork} is the same context that entered it.
   */
  private Body dispatchFork(
      WorkflowContext ctx,
      ForkTask forkTask,
      String name,
      JsonNode data,
      JsonNode context,
      Map<String, JsonNode> variables,
      int depth,
      AdminEventBuilder events,
      ObjectMapper mapper) {
    FlowOutcome then = FlowOutcome.of(forkTask.getThen());
    List<TaskItem> branches =
        forkTask.getFork() == null ? null : forkTask.getFork().getBranches();
    if (branches == null || branches.isEmpty()) {
      throw new IllegalStateException("task '" + name + "': fork has no branches");
    }

    List<Task<JsonNode>> handles = new java.util.ArrayList<>();
    for (TaskItem branch : branches) {
      String branchInstanceId = ctx.getInstanceId() + "/" + name + "/" + branch.getName();
      ForkBranchInput input =
          new ForkBranchInput(branch.getName(), data, context, variables, depth + 1);
      handles.add(
          ctx.callChildWorkflow(ForkBranchWorkflow.NAME, input, branchInstanceId, JsonNode.class));
    }

    if (forkTask.getFork().isCompete()) {
      List<Task<?>> raceHandles = new java.util.ArrayList<>(handles);
      Task<?> winner = ctx.anyOf(raceHandles).await();
      JsonNode result = (JsonNode) winner.await();
      return new Body(result, context, then, ScopeEnd.FELL_THROUGH);
    }

    List<JsonNode> results = ctx.allOf(handles).await();
    ArrayNode array = mapper.createArrayNode();
    results.forEach(array::add);
    return new Body(array, context, then, ScopeEnd.FELL_THROUGH);
  }
```

- [ ] **Step 5: Add the `fork` branch to `taskTypeOf`**

Change:

```java
    } else if (task.getForTask() != null) {
      return "for";
    } else if (task.getTryTask() != null) {
```

to:

```java
    } else if (task.getForTask() != null) {
      return "for";
    } else if (task.getForkTask() != null) {
      return "fork";
    } else if (task.getTryTask() != null) {
```

- [ ] **Step 6: Compile to confirm the wiring is sound** (full behavior is exercised in Task 7)

Run: `cd dws-orchestrator && ./mvnw test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git -C dws-orchestrator add src/main/java/io/dws/orchestrator/workflow/InterpreterWorkflow.java
git commit -m "orchestrator: wire fork dispatch — child workflow per branch, allOf/anyOf join/race"
```

---

## Task 6: Register `ForkBranchWorkflow` in `WorkflowRuntimeBootstrap`

**Files:**
- Modify: `dws-orchestrator/src/main/java/io/dws/orchestrator/config/WorkflowRuntimeBootstrap.java`

No test file — this is Spring/Dapr runtime wiring exercised only by an actual running Dapr
sidecar, matching how every prior activity's registration line was added (untested directly;
covered indirectly once the orchestrator is deployed). Verified structurally in Task 8's
`./mvnw verify`.

- [ ] **Step 1: Add the import and registration line**

Add the import:

```java
import io.dws.orchestrator.workflow.ForkBranchWorkflow;
```

In `startRuntime()`, after `builder.registerWorkflow(workflowName, InterpreterWorkflow.class);`
and before the first `builder.registerActivity(...)` call, add:

```java
    WorkflowRuntimeBuilder builder =
        new WorkflowRuntimeBuilder().registerWorkflow(workflowName, InterpreterWorkflow.class);
    builder.registerWorkflow(ForkBranchWorkflow.NAME, ForkBranchWorkflow.class);
    builder.registerActivity(CallServiceActivity.class);
```

- [ ] **Step 2: Compile**

Run: `cd dws-orchestrator && ./mvnw test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git -C dws-orchestrator add src/main/java/io/dws/orchestrator/config/WorkflowRuntimeBootstrap.java
git commit -m "orchestrator: register ForkBranchWorkflow with the Dapr workflow runtime"
```

---

## Task 7: `InterpreterWorkflowIntegrationTest` — `dispatchFork` join/race/failure scenarios

**Files:**
- Modify: `dws-orchestrator/src/test/java/io/dws/orchestrator/workflow/InterpreterWorkflowIntegrationTest.java`

**Interfaces:**
- Consumes: `ForkBranchInput`, `ForkBranchWorkflow.NAME`.
- Produces: two new test helpers (`stubForkBranch`, `stubJoin`/`stubRace`) other fork tests in this
  file reuse.

This task tests `dispatchFork`'s own orchestration (which branches it starts, how it combines
them) against a **mocked** `ctx.callChildWorkflow` — it does not re-run `ForkBranchWorkflow`'s real
dispatch logic a second time (Task 4's `ForkBranchWorkflowTest` already covers that in isolation).
Each stubbed branch handle resolves to a canned `JsonNode`, keyed off which branch name the test
configured it for.

- [ ] **Step 1: Write the failing tests** — add to `InterpreterWorkflowIntegrationTest.java`, after
      the existing `// ---- for ----` section's last test (`nonArrayForInFailsTheTaskAndInstance`),
      a new `// ---- fork ----` section:

```java
  // ---- fork ----------------------------------------------------------------

  /**
   * Stubs {@code ctx.callChildWorkflow(ForkBranchWorkflow.NAME, ...)} so each branch resolves to
   * the {@link Task} the test hands in for that branch name, keyed off the {@link ForkBranchInput}
   * each call is made with. Does not re-run {@link ForkBranchWorkflow}'s real dispatch — that is
   * {@code ForkBranchWorkflowTest}'s job; this only tests {@code dispatchFork}'s own orchestration.
   */
  @SuppressWarnings("unchecked")
  private static void stubForkBranches(WorkflowContext ctx, Map<String, Task<JsonNode>> byBranch) {
    when(ctx.callChildWorkflow(
            org.mockito.ArgumentMatchers.eq(io.dws.orchestrator.workflow.ForkBranchWorkflow.NAME),
            any(),
            any(String.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv -> {
              io.dws.orchestrator.workflow.ForkBranchInput input = inv.getArgument(1);
              Task<JsonNode> task = byBranch.get(input.taskName());
              if (task == null) {
                throw new AssertionError("no stub for branch " + input.taskName());
              }
              return task;
            });
  }

  /** {@code ctx.allOf} resolving to each handle's {@code await()} result, in list order. */
  @SuppressWarnings("unchecked")
  private static void stubAllOf(WorkflowContext ctx) {
    when(ctx.allOf(any(List.class)))
        .thenAnswer(
            inv -> {
              List<Task<Object>> handles = inv.getArgument(0);
              Task<List<Object>> combined = mock(Task.class);
              when(combined.await())
                  .thenAnswer(
                      ignored -> handles.stream().map(Task::await).toList());
              return combined;
            });
  }

  /** {@code ctx.anyOf} resolving to the handle at {@code winningIndex}, regardless of order. */
  @SuppressWarnings("unchecked")
  private static void stubAnyOf(WorkflowContext ctx, int winningIndex) {
    when(ctx.anyOf(any(List.class)))
        .thenAnswer(
            inv -> {
              List<Task<?>> handles = inv.getArgument(0);
              Task<Task<?>> combined = mock(Task.class);
              when(combined.await()).thenReturn(handles.get(winningIndex));
              return combined;
            });
  }

  @Test
  @SuppressWarnings("unchecked")
  void forkJoinsAllBranchesIntoAnArrayInDeclaredOrder() throws Exception {
    seedInline(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: fork-workflow
          version: '1.0.0'
        do:
          - raiseAlarm:
              fork:
                compete: false
                branches:
                  - callNurse:
                      set:
                        paged: '"nurse"'
                  - callSecurity:
                      set:
                        paged: '"security"'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{}"));

    Task<JsonNode> nurse = taskWithThenApply();
    when(nurse.await()).thenReturn(mapper.readTree("{\"paged\":\"nurse\"}"));
    Task<JsonNode> security = taskWithThenApply();
    when(security.await()).thenReturn(mapper.readTree("{\"paged\":\"security\"}"));
    stubForkBranches(ctx, Map.of("callNurse", nurse, "callSecurity", security));
    stubAllOf(ctx);

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    assertThat(output.isArray()).isTrue();
    assertThat(output.get(0).get("paged").textValue()).isEqualTo("nurse");
    assertThat(output.get(1).get("paged").textValue()).isEqualTo("security");
  }

  @Test
  @SuppressWarnings("unchecked")
  void forkCompeteReturnsTheWinningBranchOnly() throws Exception {
    seedInline(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: fork-workflow
          version: '1.0.0'
        do:
          - raiseAlarm:
              fork:
                compete: true
                branches:
                  - callNurse:
                      set:
                        paged: '"nurse"'
                  - callSecurity:
                      set:
                        paged: '"security"'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{}"));

    Task<JsonNode> nurse = taskWithThenApply();
    when(nurse.await()).thenReturn(mapper.readTree("{\"paged\":\"nurse\"}"));
    Task<JsonNode> security = taskWithThenApply();
    when(security.await()).thenReturn(mapper.readTree("{\"paged\":\"security\"}"));
    stubForkBranches(ctx, Map.of("callNurse", nurse, "callSecurity", security));
    stubAnyOf(ctx, 0); // callNurse's handle is index 0 (declared first)

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    assertThat(output.isArray()).isFalse();
    assertThat(output.get("paged").textValue()).isEqualTo("nurse");
  }

  @Test
  @SuppressWarnings("unchecked")
  void forkJoinFailurePropagatesAndIsCaughtByEnclosingTry() throws Exception {
    seedInline(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: fork-workflow
          version: '1.0.0'
        do:
          - guarded:
              try:
                - raiseAlarm:
                    fork:
                      compete: false
                      branches:
                        - callNurse:
                            set:
                              paged: '"nurse"'
                        - callSecurity:
                            raise:
                              error:
                                type: https://example.com/errors/paging-down
                                status: 500
                                title: Down
                                detail: paging system down
              catch:
                do:
                  - recovered:
                      set:
                        paged: '"fallback"'
        """);
    WorkflowContext ctx = mock(WorkflowContext.class);
    stubContext(ctx);
    when(ctx.getInput(JsonNode.class)).thenReturn(mapper.readTree("{}"));

    Task<JsonNode> nurse = taskWithThenApply();
    when(nurse.await()).thenReturn(mapper.readTree("{\"paged\":\"nurse\"}"));
    Task<JsonNode> security = taskWithThenApply();
    when(security.await())
        .thenThrow(
            new io.dapr.durabletask.CompositeTaskFailedException(
                "one branch failed", List.of(new RuntimeException("paging system down"))));
    stubForkBranches(ctx, Map.of("callNurse", nurse, "callSecurity", security));
    // allOf itself throws the composite failure, matching the real SDK contract.
    when(ctx.allOf(any(List.class)))
        .thenAnswer(
            inv -> {
              throw new io.dapr.durabletask.CompositeTaskFailedException(
                  "one branch failed", List.of(new RuntimeException("paging system down")));
            });
    when(ctx.callActivity(
            eq(io.dws.orchestrator.workflow.activity.CatchDecisionActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(io.dws.orchestrator.workflow.activity.CatchDecision.class)))
        .thenAnswer(
            inv ->
                completed(
                    new io.dws.orchestrator.workflow.activity.CatchDecision(
                        true, false, 0L, mapper.readTree("{\"status\":500}"), "error")));
    when(ctx.callActivity(
            eq(EvaluateSetActivity.class.getName()),
            any(),
            any(WorkflowTaskOptions.class),
            eq(JsonNode.class)))
        .thenAnswer(
            inv -> completed(EvaluateSetActivity.apply((EvaluateSetRequest) inv.getArgument(1))));

    workflow.execute(ctx);

    JsonNode output = completionOutput(ctx);
    assertThat(output.get("paged").textValue()).isEqualTo("fallback");
  }
```

Add the missing import at the top of the file:

```java
import java.util.Map;
```

(`java.util.List` is already imported.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd dws-orchestrator && ./mvnw test -Dtest=InterpreterWorkflowIntegrationTest`
Expected: FAIL — `ctx.callChildWorkflow`/`allOf`/`anyOf` are never invoked by the current
`InterpreterWorkflow` (no `fork` dispatch exists before Task 5), so the stubbed answers are never
exercised and `completionOutput`/assertions fail (either an `IllegalStateException: task ...
unsupported type`, or `NullPointerException` from `workflow.execute` reaching the old default
branch).

*(If Tasks 5-6 were completed before this task in your working order, these tests instead fail
because `dispatchFork` was written but not yet verified end-to-end — either way, this step's job is
confirming the tests fail for the right reason before the wiring lands. If following this plan
task-by-task in order, Task 5 already landed the wiring; re-run this step immediately after adding
the tests, before assuming green.)*

- [ ] **Step 3: (No production code change — Task 5 already wired `dispatchFork`.) Re-run to
      confirm green**

Run: `cd dws-orchestrator && ./mvnw test -Dtest=InterpreterWorkflowIntegrationTest`
Expected: PASS (all tests in the file, including every pre-existing scenario — confirm zero
regressions across switch/call/run/raise/for/data-flow tests).

- [ ] **Step 4: Commit**

```bash
git -C dws-orchestrator add src/test/java/io/dws/orchestrator/workflow/InterpreterWorkflowIntegrationTest.java
git commit -m "orchestrator: integration-test fork join, race, and try/catch composition"
```

---

## Task 8: Full verification and roadmap update

**Files:**
- Modify: `docs/roadmaps/openworkflow-features.md`
- Modify: `docs/roadmaps/README.md`

- [ ] **Step 1: Run the full `dws-orchestrator` suite**

Run: `cd dws-orchestrator && ./mvnw verify`
Expected: BUILD SUCCESS, all tests green (unit + any `*IT.java` under `verify`).

- [ ] **Step 2: Run the full `dws-controller` suite**

Run: `cd dws-controller && ./mvnw test`
Expected: BUILD SUCCESS, all tests green — including confirmation that a `fork`/`for` task nesting
only in-process tasks compiles to the same resource set as if absent (Task 2's
`forkWithInProcessBranchesCompilesUnchanged`), while nesting `call`/`run` inside either now deploys
the expected `StepService`s (Task 2's other new tests).

- [ ] **Step 3: Update `docs/roadmaps/openworkflow-features.md`**

In §1's task-type table, change the `fork` row:

```markdown
| `fork` | ✅ | in-process orchestration, one Dapr child workflow instance per branch — join (`compete: false`, default) via `ctx.allOf` returns branch outputs as an array in declared order, race (`compete: true`) via `ctx.anyOf` returns the first branch to settle and abandons the rest; shipped in `fork-task` (new capability `workflow-parallelism`) |
```

and the `nested do` row:

```markdown
| nested `do` | ✅ | scope-aware task-list runner generalized to every container task type (`try`/`catch.do`, `for.do`, `fork` branches); `dws-controller`'s compile-time walk covers all three, so `call`/`run` inside any of them deploys the expected step services; shipped across `try-catch-retry`, `for-task`, and `fork-task` |
```

In §3's Phase dependency graph, change:

```
  P2c --> P2d[Phase 2.4: fork parallel +<br/>generalize nested do<br/>next up]
  P1 --> P3[Phase 3: Fault Tolerance<br/>Problem Details, timeouts]
  P2d --> P3
```

to:

```
  P2c --> P2d[Phase 2.4: fork parallel +<br/>generalize nested do ✅]
  P1 --> P3[Phase 3: Fault Tolerance<br/>Problem Details, timeouts<br/>next up]
  P2d --> P3
```

In §4's Phased roadmap table, change the Phase 2 row's Route/Scope description to mark it done:

```markdown
| **2** ✅ | `try`/`catch`/`retry`, `raise`, `for`, `fork` (parallel), nested `do` | orchestrator, controller | done — `try-catch-retry`, `raise-task`, `for-task`, `fork-task` |
```

In §4a's slice table, change the slice 2.4 row:

```markdown
| 2.4 | `fork` (parallel branches) + generalizing nested `do` to any task type that nests a list | ✅ done — `openspec/changes/fork-task`; each branch runs as its own Dapr child workflow instance; `WorkflowCompiler.walk()`/`collectTaskNames()` extended to both `fork` branches and `for.do` |
```

- [ ] **Step 4: Update `docs/roadmaps/README.md`'s summary line**

Read the current summary line referencing Phase 2's status (search for "Phase 2" or "slice 2.4" —
the file's own text will show the exact current wording, likely something like "Phase 2 in
progress, slice 2.4 (`fork`) next"). Update it to reflect Phase 2 complete and Phase 3 as next,
matching the phrasing style already used for Phase 0/0.5/1/8's "done" entries elsewhere in that
same file.

Do **NOT** touch `openwiki/architecture/roadmap.md` — confirmed stale generated mirror (same
caveat every prior slice's plan logged; regenerated by the scheduled OpenWiki workflow, never
hand-edited).

- [ ] **Step 5: Commit**

```bash
git add docs/roadmaps/openworkflow-features.md docs/roadmaps/README.md
git commit -m "docs: mark roadmap Phase 2 slice 2.4 (fork) done, Phase 2 complete"
```

---

## Self-Review

**Spec coverage** (against `specs/workflow-parallelism/spec.md` and the `workflow-iteration` delta):
- "fork is recognised" / "branches run concurrently" → Task 5 (`dispatchFork` wiring) + Task 7
  (`forkJoinsAllBranchesIntoAnArrayInDeclaredOrder`, which asserts array shape as evidence branches
  ran, and Task 1/4's coverage that each branch is independently dispatched).
- "each branch starts from fork's own input data, independently" → Task 4's `ForkBranchWorkflow`
  design itself (each child instance gets its own `data` copy via serialization, not a shared
  reference) plus Task 7's two-branch tests using distinct `set` outputs.
- "compete: false joins, ordered array" → Task 7 `forkJoinsAllBranchesIntoAnArrayInDeclaredOrder`.
- "compete: true races, abandons rest" → Task 7 `forkCompeteReturnsTheWinningBranchOnly`.
- "$context does not thread across branches" → Task 5's `dispatchFork` implementation (context
  passed to `Body` is the same `context` parameter that entered, untouched) — no dedicated test
  added because there is no observable behavior to assert beyond "unchanged," which every existing
  test already implicitly confirms by never mutating context inside a `set`-only branch; if a
  reviewer wants an explicit assertion, extend `forkJoinsAllBranchesIntoAnArrayInDeclaredOrder`
  with an `export.as` branch and assert the top-level context afterward equals the pre-fork context.
- "fork branches resolve by name across nested scopes" → Task 1's two new `DefinitionLookupTest`
  cases.
- "fork composes with try/catch/retry" → Task 7 `forkJoinFailurePropagatesAndIsCaughtByEnclosingTry`.
- "a branch may nest try/for/fork" → Task 4's `dispatchesAContainerBranchTaskWithItsOwnCatchRecovery`.
- "fork branches deploy resources like top-level equivalents" + duplicate-name rejection → Task 2's
  four new `WorkflowCompilerTest` cases.
- `workflow-iteration` MODIFIED requirement (`for.do` now deploys `call`/`run`) → Task 2's
  `nestedForDoCompilesToStepServices` and `duplicateTaskNameInsideForDoRejected`.

No gaps found.

**Placeholder scan**: no "TBD"/"similar to Task N"/unshown code in any step above — every code
block is complete, compilable-intent Java or Markdown, not a description of what to write.

**Type consistency**: `ForkBranchInput(String taskName, JsonNode data, JsonNode context,
Map<String, JsonNode> variables, int depth)` — same field order/types used in Task 3's declaration,
Task 4's `ForkBranchWorkflow.execute`, and Task 5's `dispatchFork`. `ForkBranchWorkflow.NAME` used
identically in Task 5 (`ctx.callChildWorkflow(ForkBranchWorkflow.NAME, ...)`) and Task 6
(`registerWorkflow(ForkBranchWorkflow.NAME, ...)`). `InterpreterWorkflow.Dispatch`/`dispatch`
widened once in Task 3, consumed once in Task 4 — no second, drifting copy.
