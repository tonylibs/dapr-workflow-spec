# Retrospective: workflow-runtime-v2-phase3a-flow-core

> Written: 2026-10-02 (after verify passed with warnings)
> Commit range: `63f261d..5570852` (plus verify/retro/archive commits)
> Worktree: /home/dapr-workflow-spec-dws-flow-core (branch `feat/dws-flow-core`)

## 0. Evidence

- **Commit range**: `63f261d..5570852` (10 commits before verify/archive)
- **Diff size**: +1476 / -27 lines across 24 files (includes OpenSpec artifacts)
- **Tasks done**: 9/9
- **Subagent dispatches**: 4 (explorer recon, dotnet-developer impl, code-reviewer, dotnet-developer review fixes)
- **New external dependencies**: none (`System.Xml.XmlConvert` replaced by hand-rolled formatting)
- **Bugs post-merge**: n/a (not merged)
- **OpenSpec validate at archive**: this change valid; 2 unrelated pre-existing failures (`helm-admin-gateway`, `workflow-error-format`)
- **Test coverage signal**: 45 xunit tests passing (38 before review fixes)

```
146cbc3 docs(openspec): propose workflow-runtime-v2-phase3a-flow-core
29591ab feat(flow): route node scopes through a dispatch table
93f0073 feat(flow): resolve then directives as v1 does
60b2328 feat(flow): run sequencer tasks and call children by type
8db6751 feat(flow): fail tasks that exceed their timeout
1635cb4 docs(flow): document sequencer behavior
36bc6f2 docs(flow): check off phase3a tasks 1-5
013da57 fix(flow): format task timeouts without a day component
67c95d4 test(flow): lock in the friendly message for an undeclared child app ID
5570852 fix(flow): make the replay test assert genuine replay semantics
```

## 1. Wins

- [evidence: reviewer MEDIUM #1, `013da57`] Independent review caught a real v1 wording divergence (`P1D` vs `PT24H`) before merge.
- [evidence: `5570852`] Replay test strengthened and shown to fail on injected `DateTime.UtcNow`.
- [evidence: reviewer `git diff --stat`] Scope stayed inside `dws-flow/` and the change directory.

## 2. Misses

- 🟡 [painful | evidence: dotnet-developer report] The handoff gate `cd dws-flow && dotnet test` silently runs nothing; found only mid-implementation.
- 🟡 [painful | evidence: sandbox] No .NET SDK in the sandbox and no persistence outside the worktree; the SDK had to be installed in a git-excluded worktree directory.
- 🟡 [painful | evidence: runtime notices] Several stale "blocked awaiting approval" notices interrupted flow.
- 📌 [nit | evidence: plan.md] Plan assumed `AppId`; Dapr 1.18.5 uses `TargetAppId`.
- 📌 [nit | evidence: reviewer] Reviewer could not run tests in its own sandbox; the orchestrator re-ran the gate.

## 3. Plan deviations

| Plan task | What changed | Why |
|---|---|---|
| 3 | `TargetAppId` instead of `AppId` | Dapr.Workflow 1.18.5 API |
| 1.2 | `ThenOutcome`/`ThenKind` instead of a `Next` type | name collision in C# |
| 1.5 | hand-rolled duration format | `XmlConvert` emits `P1D` |
| 2.2 | fake `WorkflowContext` replay | no official Dapr .NET replay harness |
| gate | `dotnet test test/dws-flow.Tests.csproj` | bare command targets web csproj |

## 4. Skill / workflow compliance

| Skill | Used |
|---|---|
| superpowers:brainstorming | ✗ |
| superpowers:writing-plans | ✓ |
| superpowers:using-git-worktrees | ✗ (pre-existing worktree reused) |
| superpowers:subagent-driven-development | ✗ (partial) |
| (transitive) superpowers:test-driven-development | ✓ (via developer; red/green evidence reported) |
| (transitive) superpowers:requesting-code-review | ✓ (one whole-branch review) |
| superpowers:finishing-a-development-branch | pending |

### Deliberately Skipped Skills

- **`superpowers:brainstorming`**
  - **What was skipped**: the interactive Q&A; `brainstorm.md` is a decision log built from the handoff and recon.
  - **Why this cycle**: the handoff pre-locked scope, behavior and acceptance, leaving no questions to ask the user.
  - **How to prevent recurrence**: none needed when a handoff is fully specified; record this in brainstorm.md (done).
- **`superpowers:using-git-worktrees`**
  - **What was skipped**: creating a worktree.
  - **Why this cycle**: the session already ran in a dedicated worktree on `feat/dws-flow-core`.
  - **How to prevent recurrence**: n/a.
- **`superpowers:subagent-driven-development`**
  - **What was skipped**: one fresh subagent and review per micro-task.
  - **Why this cycle**: all five tasks edit the same package and share interfaces; one dotnet-developer ran them sequentially with a single whole-branch review.
  - **How to prevent recurrence**: dispatch per task with a review gate when tasks touch separate packages.

## 5. Follow-ups

- `switch` seam: v2 Step returns data only; 2b must define how a Step returns a next-task directive. 3a supports static `then` only.
- Child type classification is inferred from the task body; revisit if the definition schema adds a type.
- Decide whether the instance-ID iteration segment should be empty rather than `0` outside loops.
- Real Dapr runtime replay coverage (integration test) is not provided.
- Root-level CI for dws-flow is Phase 2g.

## 6. Cross-cutting lessons

- Verify the literal gate command from a handoff before trusting it.
- Review models need a runnable environment, or the orchestrator must re-run the gate.
