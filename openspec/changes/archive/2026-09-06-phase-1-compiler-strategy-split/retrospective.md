# Retrospective: phase-1-compiler-strategy-split

> Written: 2026-09-06 (after verify passed with warnings)
> Commit range: `origin/main..HEAD`
> Worktree: implemented directly on `claude/phase-1-compiler-refactor-jesadm` (no separate worktree)

---

## 0. Evidence

- **Commit range**: `origin/main..HEAD` (4 commits)
- **Diff size**: +2474 / −1485 across 21 files (the bulk is the verbatim compiler-class move:
  ~1480 lines removed from `WorkflowCompiler.java` and re-added as `V1OrchestratorCompiler.java`).
- **Tasks done**: 16/16
- **Active hours**: ~1
- **Subagent dispatches**: 0 (direct execution)
- **New external dependencies**: none (reused Jackson, dapr-sdk Configuration API already on the classpath)
- **Bugs encountered post-merge**: none (not yet merged)
- **OpenSpec validate state at archive**: this change valid; 3 unrelated pre-existing repo failures
- **Test coverage signal**: dws-controller suite 0 failures / 0 errors; new tests: CompiledNodeTest (4),
  CompilerStrategyTest (5); golden zero-diff across 9 definitions

Commit chain (chronological):

```
b7fb738 refactor(controller): extract WorkflowCompiler interface
c221cfd feat(controller): add flowStepGraph sealed node model to DeploymentPlan
6b45b60 feat(controller): select compiler strategy from Dapr Configuration flag
5068d95 docs(openspec): add phase-1-compiler-strategy-split change
```

---

## 1. Wins

- [evidence: golden zero-diff, §5 verify] The one hard requirement — byte-for-byte-identical v1
  `DeploymentPlan` output — was *proven*, not asserted: 9 definitions captured pre/post, `diff -rq`
  empty after excluding the additive field.
- [evidence: b7fb738] The class move was verbatim; the only edits were the class/constructor name,
  `@Override`, and qualifying the internal `version(...)` call to the static now on the interface.
- [evidence: c221cfd, CompiledNodeTest] The sealed graph serializes and round-trips with a
  `nodeType` discriminator, satisfying the ADR's dry-run-endpoint requirement.
- [evidence: 6b45b60, CompilerStrategyTest] Strategy selection defaults to v1 on absent flag,
  fetch error, and empty result — three separate tests — so flag resolution can never fail a deploy.
- [evidence: DeploymentPlan.java] Additive widening via a back-compat constructor left every
  existing `new DeploymentPlan(...)` call site (StackSynthesizerTest et al.) compiling unchanged.

## 2. Misses

- 🟡 [painful | evidence: env JDK 21 vs pom release 25] The environment ships only JDK 21; the build
  needs 25. Had to fetch Temurin 25 from a GitHub release (get.helm.sh and api.adoptium.net were
  blocked by egress policy). Cost real time before any code could compile.
- 📌 [nit | evidence: §2 verify] Could not render the Helm chart locally (no linux `helm`; vendored
  binary is Windows-only). Relied on CI `helm.yml`. Chart is a close mirror of a known-good template.
- 📌 [nit | evidence: DeploymentPlan 3-arg/2-list ctors] The pre-existing compatibility constructors
  chained to the canonical; adding a field meant the 10-arg legacy ctor became the new delegation
  target. Verified by test-compile, but the constructor chain is a small latent trap for the next
  field addition.

## 3. Plan deviations

| Plan task | What changed | Why |
|-----------|--------------|-----|
| 6.3 | Helm lint/template not run locally | No linux helm binary reachable; CI covers it |
| Task 1 | Golden capture done up front on the pre-refactor code, before any edits | Needed the baseline captured against the original class before renaming it |

## 4. Skill / workflow compliance

| Skill | Used |
|---|---|
| superpowers:brainstorming | ✗ |
| superpowers:writing-plans | ✗ |
| superpowers:using-git-worktrees | ✗ |
| superpowers:subagent-driven-development | ✗ |
| (transitive) superpowers:test-driven-development | ~ partial |
| (transitive) superpowers:requesting-code-review | ✗ |
| superpowers:finishing-a-development-branch | ✓ (PR step) |

### Deliberately Skipped Skills

- **`superpowers:brainstorming`**
  - **What was skipped**: the interactive brainstorming Q&A; brainstorm.md was written as a raw
    decision-log capture instead.
  - **Why this cycle**: the design space was already closed by an *Accepted* ADR
    (`docs/adr/0002-workflow-compiler-strategy-split.md`, Status: Accepted 2026-09-04) plus a fully
    specified user request. Every Q1–Q5 decision was pre-fixed; a fresh brainstorm would have
    re-derived settled decisions.
  - **How to prevent recurrence**: `schema graph fix` — the brainstorm artifact's instruction could
    add an explicit branch: "if an Accepted ADR already fixes the decisions, capture it as the
    decision log and note the ADR rather than re-running the interactive skill."

- **`superpowers:writing-plans`**
  - **What was skipped**: invoking the skill; plan.md was authored directly against the template.
  - **Why this cycle**: the ADR + design.md already decomposed the work into the exact file-level
    steps; the plan is a transcription, not a discovery.
  - **How to prevent recurrence**: `one-off — schema boundary case`: a pure, ADR-specified refactor
    is a boundary where planning is mechanical. Not generalizable; a feature with open design should
    still use the skill.

- **`superpowers:using-git-worktrees` / `superpowers:subagent-driven-development` /
  `superpowers:requesting-code-review`**
  - **What was skipped**: isolated worktree, subagent-per-task execution, and the code-review
    subagent dispatch.
  - **Why this cycle**: the task specified a single designated branch
    (`claude/phase-1-compiler-refactor-jesadm`) to develop and push to, and the work is one tightly
    coupled seam in one module — parallel subagents would have contended on the same 6 files. The
    orchestrator ran it directly on that branch.
  - **How to prevent recurrence**: `scope-judgment rule` — when the harness pins one branch and the
    change is a single-module, tightly-coupled refactor, direct execution is correct; reserve
    worktree + subagent fan-out for multi-module or parallelizable work. A human code review still
    happens on the PR.

- **`superpowers:test-driven-development` (partial)**
  - **What was skipped**: strict RED-GREEN per micro-step for the moved v1 code.
  - **Why this cycle**: v1 logic was moved verbatim under a hard "do not change behavior" constraint;
    the correct verification for a verbatim move is a golden-output equivalence check (which was
    written first and gated the move), not new failing unit tests over unchanged logic. New code
    (model, producer) *did* get tests written alongside.
  - **How to prevent recurrence**: `skill description tightening` — TDD guidance could note that for
    behavior-preserving moves, a captured golden/characterization baseline is the RED, and zero-diff
    is the GREEN.

## 5. Surprises

- The environment's documented "JDK 25 not available" issue was real and immediately blocking; the
  workaround (GitHub-hosted Temurin tarball) worked where the canonical download hosts did not.
- Adding a record component silently re-targeted the existing compatibility-constructor chain to the
  new 10-arg legacy constructor — it compiled and behaved correctly, but was not obvious up front.

## 6. Promote candidates → long-term learning

- [ ] 🟡 **Capture a golden/characterization baseline before any behavior-preserving move** →
      **Promote to project CLAUDE.md** (`dws-controller/CLAUDE.md`)
  > **Why**: this cycle's hard requirement was byte-for-byte-identical output; capturing goldens on
  > the original code before renaming turned "trust the move" into a proof.
  > **How to apply**: whenever a task says "verbatim move / no behavior change", capture serialized
  > outputs for representative fixtures first, diff after.

- [ ] 📌 **superpowers-bridge brainstorm step should short-circuit on an Accepted ADR** →
      **Promote to schema**
  > **Why**: re-running interactive brainstorming over decisions an Accepted ADR already fixed is
  > wasted motion and risks contradicting the ADR.
  > **How to apply**: brainstorm.instruction gains an "ADR present and Accepted → capture, don't
  > re-derive" branch.

- [ ] 📌 **Keep the JDK-25 bootstrap note handy** → **One-off**
  > **Why**: the environment ships JDK 21 but the pom needs 25; the unblock is a GitHub Temurin
  > tarball since adoptium/get.helm.sh are egress-blocked.
  > **How to apply**: only relevant in this constrained environment; does not generalize.
