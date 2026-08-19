# Retrospective: fork-task

> Written: 2026-08-17 (after verify passed)
> Commit range: `f0f0607..1695812`
> Worktree: in-place on `claude/fork-task-superpowers-bridge-q26uj6` (no separate worktree — see §4)

---

## 0. Evidence

- **Commit range**: `f0f0607..1695812` (11 commits: 1 pre-existing scaffold `f75266e`, 1 planning `daee859`, 8 implementation, 1 fix-wave)
- **Diff size**: +3277 / −17 lines across 20 files (≈2300 lines are planning artifacts under `openspec/changes/fork-task/`; code+tests ≈900 lines)
- **Tasks done**: 29/29 (`grep -cE '^- \[x\]' tasks.md` → 29)
- **Active hours**: ~1 session, coordinator + serial subagent dispatches
- **Subagent dispatches**: 12 (5 implementer dispatches [T1, T2, T3+4, T5+6, T7], 5 per-task reviews, 1 final whole-branch review, 1 final fix wave; plus 1 fix-wave re-review = 13 counting the re-review)
- **New external dependencies**: none — `ctx.allOf`/`anyOf`/`callChildWorkflow` already on pinned `dapr-sdk-workflows:1.18.0`
- **Bugs encountered post-merge**: none yet (pre-merge). One important bug caught by final review before merge (instance-id collision — see §2).
- **OpenSpec validate state at archive**: `fork-task` valid; one unrelated pre-existing spec (`helm-postgres-deployment`) invalid, out of scope (verify §1).
- **Test coverage signal**: dws-orchestrator 132 tests (was 130; +2 fork spec-coverage tests in the fix wave, on top of the T7 fork tests), dws-controller 69 tests; both 0 failures/errors, `./mvnw verify`/`test` BUILD SUCCESS on JDK 25.

Commit chain (時序):

```
f75266e Scaffold fork-task-impl change (superpowers-bridge schema)
daee859 Propose fork-task change: fork parallel branches + generalized nested do (Phase 2 slice 2.4)
e9e6999 orchestrator: resolve fork-branch task names in DefinitionLookup
ecf1d67 controller: walk fork branches and for.do, extend duplicate-name detection
293f4a6 orchestrator: add ForkBranchInput, widen dispatch/Dispatch for fork-branch reuse
0292087 orchestrator: add ForkBranchWorkflow, dispatching one fork branch per child instance
611a0f1 orchestrator: wire fork dispatch — child workflow per branch, allOf/anyOf join/race
51abbce orchestrator: register ForkBranchWorkflow with the Dapr workflow runtime
556979c orchestrator: integration-test fork join, race, and try/catch composition
8f33d88 docs: mark roadmap Phase 2 slice 2.4 (fork) done, Phase 2 complete
1695812 orchestrator: fix fork child-workflow instance-id collision on loop/retry re-invocation
```

---

## 1. Wins

- [evidence: §0 SDK grounding] Every load-bearing SDK fact was verified against the pinned jars via `javap` before it entered an artifact — `ForkTaskConfiguration.getBranches()` returning `List<TaskItem>` (same shape as `try`/`for` bodies), `WorkflowContext.allOf`/`anyOf`/`callChildWorkflow` signatures, `CompositeTaskFailedException extends RuntimeException`. This turned the "hardest slice" (per roadmap §4a) into a set of mechanical, one-branch-per-container extensions.
- [evidence: commit `ecf1d67`, verify §4] Cross-component consistency held: `WorkflowCompiler.walk()`, `collectTaskNames()`, and `DefinitionLookup.search()` now all recurse the same three containers (try/catch.do, fork.branches, for.do), so the deploy⇔resolve invariant is intact — the final review verified this explicitly.
- [evidence: commit `1695812`, final review Important finding] The final whole-branch review caught a real correctness bug (child-workflow instance-id collision on fork-in-loop / fork-in-retry) that all five per-task reviews missed because no per-task test covered that composition. The review-loop net worked as designed.
- [evidence: fix-wave report] The instance-id fix was grounded, not guessed: SDK decompilation proved the auto-derived id is `newUuid()` (deterministic UUIDv5 over namespace + parentInstanceId + replay-safe instant + per-call counter), replay-safe and unique-per-call — so the fix does not trade one bug for a replay-determinism bug.
- [evidence: for-task proposal gap] The change closed for-task's explicitly-logged controller gap (`call`/`run` under `for.do` now deploys StepServices) in the same slice, and corrected a stale roadmap claim ("nested do only wired to try/catch.do" was already false post-for-task).

## 2. Misses

- 🟡 [painful | evidence: commit `1695812`, final review] The plan (and design.md) specified an explicit deterministic child instance-id that collides when the same fork re-executes within one instance (fork in a `for` loop, or a retrying `try`). This is exactly the fork×try/retry composition the slice advertises, yet no plan task tested it — it slipped through planning and all per-task reviews, and was only caught at the final whole-branch review. Cost: one extra fix+re-review cycle.
- 🟡 [painful | evidence: verify §7 note] The interpreter integration tests mock `WorkflowContext`, so they cannot exercise the real Dapr child-instance-id uniqueness constraint. The collision bug was therefore invisible to the test harness — a passing suite did not imply correctness for this class of bug. The fix rests on SDK-decompilation evidence, not a live-backend test.
- 📌 [nit | evidence: task-5 report] The plan's Task 5 instructed `import io.dapr.durabletask.Task`, which collides with the file's existing `io.serverlessworkflow.api.types.*` wildcard (two `Task` types). The implementer correctly fully-qualified instead. Minor plan defect, self-corrected.
- 📌 [nit | evidence: task-7 report] The plan's Task 7 test code used `new CompositeTaskFailedException(...)` directly, but that SDK type's constructors are package-private — didn't compile. Implementer used a reflective helper preserving the real type. Minor plan defect, self-corrected and review-verified as sound.

## 3. Plan deviations

| Plan task | What changed | Why |
|-----------|--------------|-----|
| 4.3 (instance-id) | Dropped the explicit `branchInstanceId`; used the SDK's 3-arg auto-id `callChildWorkflow` | The plan/design explicit-id scheme collides on fork re-invocation (loop/retry); auto-id is replay-safe + unique-per-call (final review + fix wave, commit `1695812`) |
| 5 (Task 5 imports) | Fully-qualified `io.dapr.durabletask.Task` instead of importing it | Import collides with the `io.serverlessworkflow.api.types.*` wildcard (both define `Task`) |
| 7 (Task 7 test scaffolding) | `stubAnyOf` raw-type workaround; reflective `CompositeTaskFailedException` construction | Brief's verbatim code didn't compile against the pinned SDK (wildcard-capture; package-private constructors) — test-only fixes, no production impact |
| 8 (docs) | Handled directly by the coordinator rather than a dispatched implementer | Exact markdown transcription + the verification runs the coordinator had to do anyway |

## 4. Skill / workflow compliance

| Skill                                            | Used |
|--------------------------------------------------|------|
| superpowers:brainstorming                        | ✓ (prior planning session, captured in brainstorm.md) |
| superpowers:writing-plans                        | ✓ (plan.md) |
| superpowers:using-git-worktrees                  | ✗ (see below) |
| superpowers:subagent-driven-development          | ✓ |
| (transitive) superpowers:test-driven-development | ✓ (each implementer followed RED→GREEN per brief) |
| (transitive) superpowers:requesting-code-review  | ✓ (per-task review + final whole-branch review + fix-wave re-review) |
| superpowers:finishing-a-development-branch       | ✓ (next step — PR) |

### Deliberately Skipped Skills

- **`superpowers:using-git-worktrees`**
  - **What was skipped**: the entire skill — no separate git worktree was created; all implementation happened in-place in the primary checkout on the designated feature branch.
  - **Why this cycle**: the harness supplied a hard project constraint (top-of-session Git Development Branch Requirements) that all development happen on, and all pushes go to, `claude/fork-task-superpowers-bridge-q26uj6`, and NEVER to a different branch without explicit permission. A worktree requires its own distinct branch; using one would either violate that push constraint or require a merge-back dance. The branch was already a dedicated, clean, isolated feature branch (`git status` clean after the planning-commit push), so the isolation the skill provides was already satisfied.
  - **How to prevent recurrence**: `scope-judgment rule` — when the runtime already pins a single mandated development+push branch AND the working tree is clean, that branch IS the isolated workspace; a worktree is redundant and conflicts with the push mandate. (This is a genuine schema/runtime-interaction boundary: the superpowers-bridge apply instruction assumes worktree-based isolation, but a CI-style single-branch mandate supersedes it. If this recurs across cycles, it motivates a schema note that the worktree step is satisfiable by an already-isolated mandated branch.)

## 5. Surprises

- The roadmap's own "nested `do`" status row was stale (claimed "only wired to try/catch.do") — `for-task` had already added the `for.do` recursion to `DefinitionLookup` but never updated the summary. Reading the code rather than trusting the doc caught this during brainstorming.
- The SDK's fan-out primitive constraint (`Task<V>` has no `thenCompose`/flatMap) is what forced the child-workflow-per-branch architecture: you cannot turn a multi-step branch body into one combinable `Task` any other way. A surprise only in that it ruled out the "obvious" refactor-to-async approach cleanly and early.
- Mocked `WorkflowContext` tests give false confidence for concurrency-backend invariants (instance-id uniqueness) — green suite, real bug. Recorded as a Miss and a promote candidate.

## 6. Promote candidates → long-term learning

- [ ] 🟡 **A parallel/child-workflow instance-id must include a per-invocation disambiguator, or use the SDK's auto-derived id — never a purely static string** → **Promote to** project CLAUDE.md (`dws-orchestrator` section or cross-cutting architecture)
  > **Why**: fork-task's design specified a static child instance-id (`getInstanceId()+"/"+task+"/"+branch`) that collides when the construct re-executes within one instance (loop/retry); caught only at final review (commit `1695812`).
  > **How to apply**: whenever code calls `ctx.callChildWorkflow(..., explicitInstanceId, ...)` or otherwise mints a durable-instance id, at design/review time ask "can this call site run more than once per parent instance (loop, retry)?" — if yes, the id needs the iteration/attempt in it or must be SDK-auto-derived.

- [ ] 🟡 **Mocked `WorkflowContext` tests cannot verify Dapr-backend invariants (instance-id uniqueness, real allOf/anyOf ordering) — treat those as decompilation/live-cluster concerns, not "the suite is green"** → **Promote to** project CLAUDE.md (`dws-orchestrator` testing section)
  > **Why**: the fork instance-id collision was invisible to a 130-test green suite because `callChildWorkflow` is stubbed; correctness rested on SDK decompilation instead.
  > **How to apply**: when reviewing orchestrator tests that stub `WorkflowContext`, do not accept "tests pass" as evidence for any property the mock controls (child-id uniqueness, timer/event ordering, sub-orchestration semantics) — require SDK-behavior evidence or a live-backend smoke.

- [ ] 📌 **superpowers-bridge apply's worktree step is satisfiable by an already-isolated mandated single-branch runtime** → **One-off** (record; promote only if it recurs)
  > **Why**: a CI-style "develop and push only to branch X" mandate conflicts with worktree-per-plan; this cycle resolved it by working in-place on the clean mandated branch.
  > **How to apply**: if a future cycle under the same single-branch mandate hits the same tension, this is the precedent; if it recurs a third time, promote to a schema note.
