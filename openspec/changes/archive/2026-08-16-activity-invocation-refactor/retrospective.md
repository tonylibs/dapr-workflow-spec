# Retrospective: activity-invocation-refactor

> Written: 2026-08-16 (after verify passed)
> Commit range: `70d0aa1..0f459c4`
> Worktree: branch `claude/activity-invocation-migration-km2oyg` (not merged at time of writing; PR #41 open)

---

## 0. Evidence

- **Commit range**: `70d0aa1..0f459c4` (11 commits)
- **Diff size**: +2375 / -671 lines across 40 files
- **Tasks done**: 24/24 (`grep -cE '^\s*- \[x\]' tasks.md` → 24)
- **Active hours**: ~1 working session (multi-turn)
- **Subagent dispatches**: 4 (one per component: dws-orchestrator, dws-call-http, dws-run, dws-controller), run in parallel
- **New external dependencies**: `github.com/dapr/durabletask-go v0.12.4` (Apache-2.0), `github.com/dapr/go-sdk v1.15.0` (Apache-2.0) — added to both `dws-call-http` and `dws-run`
- **Bugs encountered post-merge**: none (not merged). One **pre-merge** defect found via runtime e2e: cross-app dispatch failed on the pinned Dapr 1.16.0 (`dapr-callee-app-id` metadata) — root-caused to a runtime/client version mismatch and fixed by bumping the chart to 1.18.0.
- **OpenSpec validate state at archive**: pass
- **Test coverage signal**: orchestrator `./mvnw verify` 123 tests; controller `./mvnw test` 64 tests; `dws-call-http` + `dws-run` `go test ./...` green; plus a hand-built self-hosted Dapr 1.18.0 e2e proving cross-app dispatch end-to-end.

Commit chain (時序):

```
a5742b4 docs(opsx): author activity-invocation-refactor planning artifacts
0489705 feat(call-http): run as a Dapr Workflow activity worker
557278c feat(run): run as a Dapr Workflow activity worker
f0ce3b1 feat(controller): keep activity-invoked steps live via conditional min-scale
6d48cdc feat(orchestrator): dispatch call:http/run steps as multi-app activities
175690f docs(opsx): complete apply tasks + record cross-app prerequisites
8a9ef8a docs(opsx): add verify report for activity-invocation-refactor
ea89e24 docs(opsx): add local e2e checklist for multi-app activity dispatch
e60c068 docs(opsx): record cross-app dispatch defect as blocking version prerequisite
d957516 feat(controller): synthesize WorkflowAccessPolicy for cross-app steps
0f459c4 fix(chart): bump Dapr appVersion to 1.18.0 (validated cross-app dispatch)
```

---

## 1. Wins

- [evidence: 0489705, 557278c] Parallel subagents cleanly split the four independent components; the shared Go→Java failure-marker contract (`upstream failure:` / `config failure:`) was pinned up front, so the agents stayed decoupled and their outputs composed without rework.
- [evidence: 6d48cdc `WorkflowErrors`] Reusing the existing message-marker classification (which already keyed off the activity-boundary message) meant the status-free error path dropped in without changing the author-facing error shape — both dispatch paths yield the same `{type,status,instance,title,detail}`.
- [evidence: 6d48cdc, WorkflowCompiler.java:207-213] Detecting the call sub-kind via the same SDK accessors the compiler uses kept the controller/orchestrator invariant intact rather than inventing a new signal.
- [evidence: d957516 `StackSynthesizer.workflowAccessPolicies`] Discovering cdk8s already generates `imports.io.dapr.WorkflowAccessPolicy` (Dapr 1.18.2 CRD bundle) let the policy synth mirror the existing `Component` pattern exactly — no raw-CRD plumbing.
- [evidence: e2e RESULT `{"hello":"world","stock":42}`] The runtime e2e caught a defect that every unit gate was green on, and then proved the fix. Evidence-before-assertion paid for itself.

## 2. Misses

- 🔴 [blocking | evidence: ea89e24 e2e-checklist, later corrected e60c068/0f459c4] The first e2e checklist recommended `dapr init --runtime-version 1.15.5` and asserted "Dapr 1.16.0 runs this fine" — both wrong. Multi-app needs ≥1.16.0 and in practice ≥1.18.0 here. I documented a version claim I had not run. The user's real-cluster test hit exactly this. Fixed, but it cost a round-trip.
- 🟡 [painful | evidence: verify.md first draft 8a9ef8a] The first verify report concluded "Ready for archive" while the runtime path was entirely unvalidated — unit-green was treated as done-done. Only the user's "test the activities calling" surfaced that the feature could not actually run.
- 🟡 [painful | evidence: e2e run iterations] The self-hosted-slim reproduction took ~6 fix iterations (scheduler broadcast, mdns bogus IP, orphaned daprd, duplicate instance id, early-poll races) before green — a lot of harness plumbing for one assertion. A container-mode `dapr init` would have wired the control plane correctly, but docker wasn't available.
- 📌 [nit | evidence: dws-call-http/internal/activity vs dws-run/internal/worker] The two Go step images name their activity package differently; harmless but inconsistent.

## 3. Plan deviations

| Plan task | What changed | Why |
|-----------|--------------|-----|
| 1.1 | `StepActivityRequest` record not created | The Go `Run` worker takes the raw workflow-data JSON as input; app-id rides in `WorkflowTaskOptions`, so no request wrapper was needed — just a `StepActivity.NAME` constant. |
| 3.1 / 4.1 | Used `github.com/dapr/durabletask-go` (via go-sdk client), not `github.com/dapr/go-sdk/workflow` | The plan's package path does not exist; workflow authoring moved to durabletask-go. |
| (new) group 8 | Added WorkflowAccessPolicy synthesis | Surfaced as a production-security follow-up during verify, then requested by the user; implemented within this change. |
| (new) chart bump | `Chart.yaml` 1.16.0 → 1.18.0 | Originally a stated non-goal; the runtime e2e proved it is the actual fix for cross-app dispatch, so it was applied and validated. |

## 4. Skill / workflow compliance

| Skill                                            | Used |
|--------------------------------------------------|------|
| superpowers:brainstorming                        | ⚠️ partial |
| superpowers:writing-plans                        | ⚠️ partial |
| superpowers:using-git-worktrees                  | ✗ |
| superpowers:subagent-driven-development          | ✓ |
| (transitive) superpowers:test-driven-development | ✓ |
| (transitive) superpowers:requesting-code-review  | ✗ |
| superpowers:finishing-a-development-branch       | ✗ (PR open, not yet finished) |
| superpowers:systematic-debugging                 | ✓ (invoked for the dispatch defect) |

### Deliberately Skipped Skills

- **`superpowers:brainstorming` / `superpowers:writing-plans`**
  - **What was skipped**: The interactive skill invocations. brainstorm.md and plan.md were authored directly from the user's fully-specified requirement.
  - **Why this cycle**: The `/opsx:propose` entry arrived with a locked, itemized scope (6 numbered scope items + explicit out-of-scope). The brainstorming skill's job (converge scope, resolve forks) was already done by the user before the change was opened, and `/opsx:propose` explicitly asks to keep momentum. Running interactive brainstorming would have re-litigated a settled scope.
  - **How to prevent recurrence**: `scope-judgment rule` — when `/opsx:propose` (not `/opsx:new`) is invoked with a requirement that already satisfies the 5 promotion criteria in the repo CLAUDE.md, capturing the decisions is compliant; a partial mark here is expected, not a gap to close.

- **`superpowers:using-git-worktrees`**
  - **What was skipped**: Whole skill — worked directly on the designated branch.
  - **Why this cycle**: The session's harness pins a specific branch (`claude/activity-invocation-migration-km2oyg`) and pushes to it; a worktree would fight that fixed-branch contract.
  - **How to prevent recurrence**: `one-off — schema boundary case`. The remote-execution harness owns branch/worktree lifecycle here; the skill does not apply.

- **`superpowers:requesting-code-review`**
  - **What was skipped**: Whole skill.
  - **Why this cycle**: Work was pushed to an open PR (#41) under active human review; review is happening on the PR rather than via the skill.
  - **How to prevent recurrence**: `CLAUDE.md trigger` — for PR-backed remote sessions, note that PR review substitutes for the local review skill; run the skill only for pre-PR local branches.

## 5. Surprises

- Unit-green ≠ feature-works. Every component gate passed and CI was green across 11 commits, yet the headline feature (cross-app dispatch) did not function on the deployed runtime version. The gap was entirely in a version-compatibility layer no unit test can see.
- The Dapr runtime version was recorded as an explicit **non-goal** ("no version bump") in the proposal, but turned out to be the single thing standing between "code complete" and "feature works." A stated non-goal was the actual critical path.
- The `dapr-callee-app-id` metadata error is a moving target even within Dapr's own releases (dapr/dapr#10039: 1.17.7 worked, 1.17.8 regressed), which made "just pick a recent version" non-obvious — the fix was to match the runtime to the *client libraries the images already linked* (1.18-era), not to chase the newest.

## 6. Promote candidates → long-term learning

- [ ] 🔴 **Never document a version/command as validated without running it** → **Promote to memory** (type: feedback)
  > **Why**: The e2e checklist shipped `--runtime-version 1.15.5` and "1.16.0 runs fine" from inference, not execution; the user's test hit exactly that wall.
  > **How to apply**: When writing setup/run instructions or version pins into a deliverable, either run them first or explicitly label them "unverified — placeholder". Applies to any checklist/README/chart value.

- [ ] 🔴 **"Verify" for a feature whose value is a runtime behavior must include a runtime check, not just unit gates** → **Promote to project CLAUDE.md** (`openspec` verify guidance)
  > **Why**: verify.md first concluded "ready for archive" with the actual feature non-functional at runtime.
  > **How to apply**: In `/opsx:verify`, when the change's value is an integration/deploy behavior (cross-service dispatch, wire protocol, deployment resource), the verdict must gate on an executed integration/e2e signal or explicitly state runtime is unvalidated.

- [ ] 🟡 **A stated non-goal can still be the critical path — re-examine non-goals at verify** → **Promote to memory** (type: feedback)
  > **Why**: "No Dapr version bump" was declared out of scope yet was the one thing blocking the feature.
  > **How to apply**: At verify time, for each non-goal ask "could this non-goal prevent the goal from actually working?" before declaring done.

- [ ] 📌 **cdk8s in this repo already generates all Dapr CRDs (incl. WorkflowAccessPolicy) from the 1.18.2 bundle** → **One-off** (record only)
  > **Why**: Saved raw-CRD plumbing; specific to this repo's build setup, unlikely to generalize.
  > **How to apply**: When synthesizing a new Dapr resource here, check `target/classes/imports/io/dapr/` for a generated model before hand-rolling a GenericKubernetesResource.
