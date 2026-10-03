# Phase 3a dws-flow Sequencer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a `main`/`do` Flow node really run its task list, calling Step and Flow children by type.

**Architecture:** `FlowWorkflow` becomes a thin entry that dispatches by node scope through one `ScopeDispatch` table: `main`/`do` -> `SequencerRunner`; `for`/`try-catch`/`fork` -> a not-implemented failure. The `SequencerRunner` depends on a small `IChildCaller` seam (activity call, child-workflow call, timer race) over `WorkflowContext`, so tests use stand-in children and replay. Pure helpers (`ThenResolver`, `ChildClassifier`, `TaskTimeout`, `InstanceIds`) hold the v1-compatible logic.

**Tech Stack:** .NET 10, Dapr.Workflow 1.18.5, xunit 2.9.3, FluentAssertions 8.10.0.

**Spec:** `specs/dws-flow-sequencer/spec.md`, `design.md`, `tasks.md` in this change directory.

## Global Constraints

- Edit only `dws-flow/` (and this change directory's `tasks.md` checkboxes). No change to `dws-step`, `dws-controller`, schema, roadmaps.
- Workflow code deterministic: no `DateTime.Now`, `Guid.NewGuid`, `Random`, I/O; log only when `!context.IsReplaying`.
- Step activity name `Step`; input `StepInput`-shaped `{data, variables, workflowInstanceId, iterationIndex}`.
- Instance ID `<root>:<childAppId>:<iteration>` (iteration `0` outside loops).
- v1 wordings verbatim: `task '<name>' timed out after <ISO-8601>`, `flow references task '<t>', which is not declared in this task scope`, `workflow exceeded 10000 steps; check for a definition loop`; controller scopes: `config failure: scope '<scope>' is not implemented yet`.
- Gate: `cd dws-flow && DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1 ../.dotnet-sdk/dotnet test`.
- Do not apply `input.from`/`output.as`. Do not push.

## Review Focus

- Task `then` naming a task that exists only in a different scope -> not-declared failure.
- `timeout` given as ISO string vs `{seconds: n}` object -> both parsed, rendered ISO-8601 like v1 `Duration.toString`.
- Child failure message containing the word "timed out after" -> passes through unchanged (3c classifies).
- `then: exit`/`end` on the last task and on a middle task -> completes with current data.
- Empty `tasks` list -> completes with input data.

---

### Task 1: Scope dispatch, envelope, controller routing (tasks.md 1.1)

**Files:** Create `dws-flow/src/FlowInput.cs`, `dws-flow/src/ScopeDispatch.cs`; Modify `dws-flow/src/FlowWorkflow.cs`; Test `dws-flow/test/ScopeDispatchTests.cs`.

**Interfaces:**
- Produces: `record FlowInput(JsonNode? Data, IReadOnlyDictionary<string,JsonNode?>? Variables, string? RootInstanceId, string? IterationIndex)`; `ScopeDispatch.Resolve(string scope) -> ScopeKind { Sequencer, NotImplemented }`; `ScopeDispatch.NotImplementedMessage(string scope)`.

- [ ] **Step 1:** Write failing tests: `main`,`do` -> Sequencer; `for`,`try-catch`,`fork` -> NotImplemented with message `config failure: scope 'for' is not implemented yet`.
- [ ] **Step 2:** Run `dotnet test --filter ScopeDispatchTests`; expect compile failure.
- [ ] **Step 3:** Implement the types; make `FlowWorkflow` input `FlowInput`, root instance ID defaulting to `context.InstanceId` when null.
- [ ] **Step 4:** Run tests; expect PASS.
- [ ] **Step 5:** Commit `feat(flow): route node scopes through a dispatch table`.

### Task 2: `then` resolution and ordering (1.2)

**Files:** Create `dws-flow/src/ThenResolver.cs`; Test `dws-flow/test/ThenResolverTests.cs`.

**Interfaces:**
- Produces: `ThenResolver.Next(JsonNode? then, int pc, IReadOnlyDictionary<string,int> indexByName) -> Next { Continue(int pc) | End | Exit }`; throws `InvalidOperationException` with the v1 not-declared message.

- [ ] **Step 1:** Failing tests: absent/`continue` -> pc+1; name -> index; `end`/`exit`; unknown -> exception message; past last task handled by caller (fell through).
- [ ] **Step 2:** Run; expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run; PASS.
- [ ] **Step 5:** Commit `feat(flow): resolve then directives as v1 does`.

### Task 3: Child classification, dispatch, failure propagation (1.3, 1.4)

**Files:** Create `dws-flow/src/ChildClassifier.cs`, `InstanceIds.cs`, `IChildCaller.cs`, `SequencerRunner.cs`; Test `dws-flow/test/SequencerRunnerTests.cs` with a recording stand-in `IChildCaller`.

**Interfaces:**
- Produces: `ChildKind { Step, Flow }`; `ChildClassifier.Classify(JsonObject taskBody)`; `InstanceIds.For(root, appId, iteration)`; `IChildCaller { Task<JsonNode?> CallStep(string appId, FlowInput input); Task<JsonNode?> CallFlow(string appId, string instanceId, FlowInput input); }`; `SequencerRunner.Run(FlowInput input, SingleNodeDefinition def, IChildCaller caller, ...) -> Task<JsonNode?>`.

- [ ] **Step 1:** Failing tests: tasks run in order, each output is next input; Step child gets app ID `children[task]` and the full envelope; `for`/`try`/`fork`/`wait`/`listen` task -> Flow call with instance ID `root:app:0` and `root:app:1.0` at iteration `1.0`; child exception message propagates unchanged; empty tasks returns input; 10000-step loop fails.
- [ ] **Step 2:** Run; expect FAIL.
- [ ] **Step 3:** Implement runner and a Dapr-backed `IChildCaller` over `WorkflowContext` (`CallActivityAsync("Step", input, new WorkflowTaskOptions{AppId=...})`, `CallChildWorkflowAsync(...)` with `InstanceId`); unwrap `WorkflowTaskFailedException.FailureDetails.ErrorMessage` and rethrow with that message.
- [ ] **Step 4:** Run; PASS.
- [ ] **Step 5:** Commit `feat(flow): run sequencer tasks and call children by type`.

### Task 4: Task timeout (1.5)

**Files:** Create `dws-flow/src/TaskTimeout.cs`; Modify `SequencerRunner.cs`, `IChildCaller.cs`; Test `dws-flow/test/TaskTimeoutTests.cs`.

**Interfaces:**
- Produces: `TaskTimeout.Parse(JsonNode? timeout) -> TimeSpan?`; `TaskTimeout.Format(TimeSpan) -> string` (ISO-8601, e.g. `PT5S`); `IChildCaller` gains a race: `Task<(bool TimedOut, JsonNode? Result)> WithTimeout(TimeSpan t, Task<JsonNode?> call)` backed by a durable timer and `Task.WhenAny`, timer cancelled when the call wins.

- [ ] **Step 1:** Failing tests: object/ISO timeout parsing and formatting; stand-in timer wins -> failure `task 'x' timed out after PT5S`; call wins -> result; the Review Focus "timed out after" passthrough case.
- [ ] **Step 2:** Run; FAIL. **Step 3:** Implement. **Step 4:** Run; PASS.
- [ ] **Step 5:** Commit `feat(flow): fail tasks that exceed their timeout`.

### Task 5: Replay test, docs, gate (2.2, 3.1, 3.2)

**Files:** Test `dws-flow/test/ReplayTests.cs`; Modify `dws-flow/README.md`, `dws-flow/CLAUDE.md`.

- [ ] **Step 1:** Write a replay test: run the workflow against a recorded history (SDK test harness if available; otherwise a fake `WorkflowContext` replaying recorded results with `IsReplaying=true`) and assert identical commands and result; also assert no non-deterministic API usage in `dws-flow/src` workflow code (grep-style test for `DateTime.`, `Guid.NewGuid`, `Random`).
- [ ] **Step 2:** Run; confirm it fails if determinism is broken, passes now.
- [ ] **Step 3:** Update README/CLAUDE.md (Sequencer behavior, instance ID, scope routing, fix stale static `Load` example).
- [ ] **Step 4:** Run full gate; all green.
- [ ] **Step 5:** Commit `docs(flow): document sequencer behavior`.
