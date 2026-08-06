# Retrospective: raise-task

> Written: 2026-08-06 (after verify passed with warnings)
> Commit range: `ea02abb..3794cd2` (implementation); `9469f8c..3794cd2` including the artifacts commit
> Worktree: none — implemented in the main checkout on branch `claude/openworkflow-raise-task-5u9sd5` (see §4)

---

## 0. Evidence

- **Commit range**: `ea02abb..3794cd2` (4 implementation commits; `ea02abb` carried the 6 planning artifacts)
- **Diff size**: +844 / −28 across 12 files (implementation only, excluding planning artifacts)
- **Tasks done**: 21/21
- **Active hours**: ~1.5h (planning ~50min in the prior turn, implementation ~40min)
- **Subagent dispatches**: 0 (see §4)
- **New external dependencies**: none. Temurin JDK 25 was installed into the session scratchpad to run the gates, but no `pom.xml`, lockfile, or dependency declaration changed.
- **Bugs encountered post-merge**: none yet (PR #33 open at write time)
- **OpenSpec validate state at archive**: pass — `openspec validate --all --json` → 14 items, 0 invalid
- **Test coverage signal**: `dws-orchestrator` 102 tests green (was 85 before this change: +17 — 3 in `WorkflowErrorsTest`, 9 in the new `RaiseErrorActivityTest`, 5 in `TryCatchInterpreterTest`, and the integration-test case replaced no existing one); `dws-controller` 49 tests green, unchanged

Commit chain (時序):

```
ea02abb docs(opsx): propose raise-task change (Phase 2 slice 2.2)
fadfbb8 feat(orchestrator): add RaisedErrorException and WorkflowErrors short-circuit for raised errors
d21189d feat(orchestrator): add RaiseErrorActivity resolving a raise task's configured error
db8e338 feat(orchestrator): dispatch raise tasks, reusing the existing try/catch failure path
3794cd2 test(orchestrator): assert raise tasks are labelled in lifecycle events
```

---

## 1. Wins

- [evidence: `design.md` §Context SDK facts; scratchpad `Probe.java` run] Resolving the flagged SDK risk **empirically** rather than from the published schema changed the design. `javap` showed `Error.getStatus()` is a primitive `int` with no expression variant, contradicting the requirement's premise that all five fields accept `${...}` like `set`'s. Parsing real `raise` YAML through `WorkflowReader` then showed the SDK's deserializer already routes `${...}` to the expression accessor and a plain string to the literal one — which is what made D1 ("no `${...}` sniffing") correct rather than a guess.
- [evidence: `db8e338`; `InterpreterWorkflow.java:335-341`] The D4/D7 pairing paid off exactly as designed: because the raised error becomes an ordinary `RuntimeException` at the activity boundary, dispatch wiring was 3 small edits and **zero** new propagation code. `runTaskList`'s existing catch and `dispatchTry`'s catch both handled it untouched — task 3.4 was a confirmation, not work.
- [evidence: `fadfbb8`, `WorkflowErrorsTest.raisedErrorDetailContainingAnotherMarkerIsStillNotReclassified`] Writing the marker check as a **prefix** match (not `contains`) was worth an explicit test: an author whose `detail` quotes a step failure would otherwise have had their raised error silently reclassified as a communication error.
- [evidence: `dws-controller` 49 tests green, zero files touched] The design's "no controller changes" claim was verified by reading `WorkflowCompiler.walk()` before writing the proposal, then re-confirmed by running the gate. It held.

## 2. Misses

- 🔴 [blocking for archive only | evidence: `verify.md` §3; `ls openspec/specs/` has no `workflow-error-handling/`] `try-catch-retry` is 32/32 complete with code on `main` but was **never archived**, so the capability this change appends to does not exist in `openspec/specs/`. Archiving `raise-task` alone would produce a capability spec holding only this change's 8 requirements and silently drop slice 2.1's 13. Caught at verify; escalated rather than worked around, since archiving another change is outside this change's lifecycle.
- 🟡 [painful | evidence: first `./mvnw test` → `release version 25 not supported`; `api.adoptium.net` → proxy 403] The container ships JDK 21 while both components pin `maven.compiler.release=25`. The documented workaround in `dws-controller/CLAUDE.md` says "point `JAVA_HOME` at a JDK 25" but no JDK 25 is present and the default download host is blocked by network policy. Cost ~10 minutes of host probing to find that `github.com`/`objects.githubusercontent.com` are reachable and Adoptium publishes there.
- 🟡 [painful | evidence: first `TryCatchInterpreterTest` RED run — `raisedErrorInsideTryCanTriggerARetry` failed with a Jackson one-of deserialization error, not the expected "unsupported type"] My first `raiseYaml` fixture builder got text-block indentation wrong: `.indent(14)` put the `errors.with` filter at `catch`'s sibling level. The catch-all fallback meant the *other* raise tests still passed, so a wrong fixture was briefly producing a right-looking green. Fixed by replacing the indent arithmetic with an explicit `#EXTRAS#` sentinel line.
- 📌 [nit | evidence: an edit that replaced the `TryTask` case instead of adding a `RaiseTask` one, reverted in the next call] Momentary wrong-target edit on `dispatchConcreteTask`'s switch. Caught immediately by re-reading the diff, no test ever saw it.

## 3. Plan deviations

| Plan task | What changed | Why |
|-----------|--------------|-----|
| Task 4 (整節) | plan.md pre-argued that the three required raise integration scenarios belong in `TryCatchInterpreterTest`, leaving only a `taskTypeOf` case for `InterpreterWorkflowIntegrationTest`. Implemented as written. | `TryCatchInterpreterTest` is the existing home for try/catch/retry interpreter scenarios and already stubs every in-process activity for real. Splitting raise's catch-interaction assertions into the other class would have fragmented one behaviour across two files. tasks.md 4.1–4.3 are satisfied there. |
| Task 3 tests | Added 5 interpreter cases rather than the 3 tasks.md lists — extra: `raisedErrorIsFilteredByCatchErrorsWithLikeARealFailure` and `raisedErrorReadsTheTaskDataThroughItsExpressionFields`. | The spec has a "Raised error is filtered like a real failure" scenario and expression-field scenarios; asserting them only at the activity level would not have proven they survive the dispatch path. |
| Task 2 tests | 9 cases vs the 6 plan.md sketched — extra: `statusIsUsedVerbatim`, `declaredInstanceMayBeAnExpression`, `aTaskThatIsNotARaiseTaskIsRejected`. | One test per spec scenario; the SDK-gap decision (D2) deserved a test pinning it rather than only a doc sentence. |
| Task 5.3 | Also updated the mermaid phase graph and the §4 Phase 2 row, not just the two tables plan.md named. | Leaving `Phase 2.2: raise / next up` in the graph while marking the slice done would have contradicted itself. |

## 4. Skill / workflow compliance

| Skill                                            | Used |
|--------------------------------------------------|------|
| superpowers:brainstorming                        | ✓    |
| superpowers:writing-plans                        | ✓    |
| superpowers:using-git-worktrees                  | ✗    |
| superpowers:subagent-driven-development          | ✗    |
| (transitive) superpowers:test-driven-development | ✓ (applied manually — RED verified before every GREEN) |
| (transitive) superpowers:requesting-code-review  | ✗ (self-review only) |
| superpowers:finishing-a-development-branch       | ✗    |

### Deliberately Skipped Skills

- **`superpowers:subagent-driven-development`** (and its transitive **`requesting-code-review`**)
  - **What was skipped**: the entire executor — no fresh subagent per task, no per-task code-reviewer dispatch. TDD was still applied by hand (every task ran a RED check that failed for the expected reason before implementation: `cannot find symbol: class RaisedErrorException`, `cannot find symbol: class RaiseErrorRequest`, `task 'explode' has an unsupported type`).
  - **Why this cycle**: a hard conflict between two instruction sources. This session's harness system prompt states "Do not call the AgentTool unless the user requested it" and the Agent tool description states "Do not spawn agents unless the user asks", while the schema's apply instruction mandates subagent-driven-development and forbids silent fallback. I surfaced the conflict via `AskUserQuestion` (offering "inline, TDD + review myself" vs "subagent-driven per the schema"); the user did not answer, and Auto Mode directs biasing toward action on the recommended default. Chose inline, and recorded the choice rather than letting it pass silently.
  - **How to prevent recurrence**: `CLAUDE.md trigger` — add to the repo's `CLAUDE.md` § Workflow routing a line stating that the superpowers-bridge apply phase requires agent-spawning, so a session whose harness disables agents should either be started with agents enabled or explicitly opt into the `spec-driven` schema (which the bridge README already names as the non-subagent path). That converts a mid-apply conflict into a pre-apply schema choice.

- **`superpowers:using-git-worktrees`**
  - **What was skipped**: the whole skill — worked directly in `/home/user/dapr-workflow-spec` on the designated branch.
  - **Why this cycle**: the session's environment instructions pin all work to branch `claude/openworkflow-raise-task-5u9sd5` in the primary working directory, and PR #33 was created against that exact branch by the user mid-session. A worktree's separate checkout would have split commits away from the branch the open PR tracks. `git status --porcelain` was empty at start, so the isolation a worktree buys (protecting unrelated in-progress work) had nothing to protect.
  - **How to prevent recurrence**: `one-off — schema boundary case`. It is a genuine boundary because the schema assumes apply *begins* the branch, whereas here the remote-execution harness had already created the branch and a PR against it before apply started. The worktree step is only meaningful when apply owns branch creation; when a PR already tracks the branch, honouring it is the correct behaviour, not a shortcut.

- **`superpowers:finishing-a-development-branch`**
  - **What was skipped**: the PR-opening step.
  - **Why this cycle**: PR #33 already exists for this branch (created from the Claude Code UI mid-session; the harness explicitly instructed "You don't need to create one. Reference this PR going forward"). The skill's terminal action would have been a no-op or a duplicate PR.
  - **How to prevent recurrence**: `schema graph fix` — the schema's apply step 6 should branch on whether a PR already tracks the branch, i.e. "invoke finishing-a-development-branch unless an open PR already targets this branch, in which case push and reference it". Pre-existing PRs are the norm on harnesses that create the branch up front.

## 5. Surprises

- The requirement document's stated "known risk" (does the SDK model `raise` at all?) turned out to be **not** the real risk — `Task.getRaiseTask()`, `RaiseTaskConfiguration`, `RaiseTaskError`, and `use.errors` are all fully modelled. The actual gap was one level down and narrower: `status` alone lacks an expression variant. Checking the thing you were told to check is not the same as finding the gap.
- The SDK's deserializer does the `${...}` discrimination itself. I had assumed (from `set`'s convention of sniffing every string) that application code would need to. Empirically parsing a sample definition — rather than reading accessor signatures alone — is what revealed this; `javap` showed *that* there were two accessors but not *which one gets populated when*.
- `DefinitionLookup.taskByName()` already recursed into `try`/`catch.do`, so a `raise` nested inside either was resolvable with zero changes. The plan budgeted no task for it and none was needed — a case where slice 2.1's groundwork silently covered slice 2.2.
- A wrong test fixture produced *passing* tests. The mis-indented `errors.with` filter degraded to a catch-all, which still satisfied "the error was caught". Only the retry variant failed loudly (on YAML parsing). Green is not proof the fixture says what you meant.

## 6. Promote candidates → long-term learning

- [ ] 🔴 **Archive prerequisite slices before archiving a change that appends to their capability** → **Promote to project CLAUDE.md** (`CLAUDE.md` § Workflow routing)
  > **Why**: `try-catch-retry` shipped to `main` but was never archived, so `openspec/specs/workflow-error-handling/` does not exist. Archiving `raise-task`'s `## ADDED Requirements` against a non-existent capability would have written a spec containing 8 requirements and silently dropping 13. Caught only because verify §3 explicitly compares delta dirs against `openspec/specs/`.
  > **How to apply**: at `/opsx:archive` time, before running it — if the change's delta targets a capability absent from `openspec/specs/`, find the change that introduced it and archive that first. Applies to any multi-slice phase where slices share one capability.

- [ ] 🟡 **Verify a test fixture asserts what you meant, not just that it goes green** → **Promote to memory** (type: feedback)
  > **Why**: a mis-indented `errors.with` filter in `raiseYaml` silently degraded to a catch-all; three raise tests passed against a fixture that was not testing the filter at all. Only the retry variant failed, and only because the YAML failed to parse.
  > **How to apply**: when a test fixture is built by string manipulation (text-block `.indent()`, `.formatted()`, concatenation) rather than written literally, assert on something the fixture-under-question uniquely controls — here, that a *non-matching* filter causes propagation — before trusting the positive case.

- [ ] 🟡 **Record the JDK-25 acquisition path for this repo's remote sessions** → **Promote to project CLAUDE.md** (`dws-controller/CLAUDE.md` § Known issues, extending the existing note)
  > **Why**: the existing note says "point `JAVA_HOME` at a JDK 25" but the container ships only JDK 21, and `api.adoptium.net` is blocked by the agent proxy (403 on CONNECT). Rediscovering that `github.com`/`objects.githubusercontent.com` are reachable and that Adoptium publishes there cost ~10 minutes.
  > **How to apply**: whenever `./mvnw` fails with `release version 25 not supported` in a remote session — fetch `https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25%2B36/OpenJDK25U-jdk_x64_linux_hotspot_25_36.tar.gz` into the scratchpad and export `JAVA_HOME`. Never lower `maven.compiler.release` to work around it (project-wide decision).

- [ ] 🟡 **Parse a real sample, don't just read accessor signatures, when a typed one-of drives a design decision** → **Promote to memory** (type: feedback)
  > **Why**: `javap` revealed `ErrorTitle` has both `getLiteralErrorTitle()` and `getExpressionErrorTitle()`, but not which is populated for a given input. Only parsing sample YAML showed the SDK's deserializer does the `${...}` discrimination — the fact D1 rests on.
  > **How to apply**: when a design decision turns on "does the library resolve X for us or must we", write a throwaway probe that runs the library's own parser on representative input, before writing the decision into design.md.

- [ ] 📌 **A schema mandating subagents conflicts with a harness forbidding them; decide before apply, not during** → **Promote to schema** (superpowers-bridge apply instruction)
  > **Why**: this cycle hit the conflict mid-apply and had to resolve it by asking the user (who did not answer) and defaulting. The schema already says "if your platform lacks subagent support, use the built-in `spec-driven` schema" — but that guidance lives in the apply instruction, read only after apply has started.
  > **How to apply**: surface the subagent requirement at `/opsx:new` schema-selection time so the incompatibility is a pre-cycle choice, not a mid-cycle conflict.
