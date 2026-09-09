# Retrospective: scope-node-sequencer-controller-split

> Written: 2026-09-09 (after verify passed)
> Commit range: `93740df..383f24e`
> Worktree: ephemeral remote container on branch `claude/hello-im6ak2` (see §4)

---

## 0. Evidence

- **Commit range**: `93740df..383f24e` (12 commits)
- **Diff size**: +2676 / −310 across 36 files
- **Tasks done**: 32/32
- **Active hours**: ~5.2 (08:52–14:05 UTC), including one ~40-minute stall on a session rate limit
- **Subagent dispatches**: 20 — 8 task implementers, 8 task reviewers, 1 final whole-branch review,
  1 fix wave, 1 scoped re-review, 1 prose fix
- **New external dependencies**: none
- **Bugs encountered post-merge**: none (not merged)
- **OpenSpec validate state at archive**: pass for this change's two items; 3 unrelated repo-wide
  failures pre-date the branch and were reproduced at two earlier commits to prove it
- **Test coverage signal**: 189 → 203 tests (+14), `./mvnw verify` BUILD SUCCESS

Commit chain:

```
a4f6655 docs(openspec): propose scope-node-sequencer-controller-split (ADR 0004)
27ed7af docs(openspec): add plan.md for scope-node-sequencer-controller-split
c03def0 feat(controller): let FlowScope carry for and try-catch configuration
9afcf30 feat(controller): derive for and try body node ids
d02bab9 feat(controller): compile for scopes as controllers over a do child
a5f2522 feat(controller): compile try scopes as try-catch controllers
7eb48bf refactor(controller): retire forkBranch nodes and their derived ids
6c44cb1 feat(openspec): pin the sequencer/controller shapes in the node schema
686699a test(controller): regenerate v2 golden fixtures for the two-shape node model
459fd30 docs(controller): sync the runtime architecture doc with ADR 0004
e582a76 fix(controller): carry every catch guard onto the try-catch controller
383f24e docs(openspec): enumerate all five catch-block fields on try-catch controllers
```

---

## 1. Wins

- The decisive defect is demonstrably fixed. `raise-and-recovery/nodes/process-payment.json` now
  carries `errors.with.status: 402` on the node that decides recovery; before this change that filter
  survived only in the parent's verbatim task entry, so a Phase 3 runtime reading the node would have
  recovered from every error.
- **Reviewing fixtures against an independent authority caught what self-consistency could not.**
  Regenerated golden fixtures always match the compiler — that is circular. Every fixture review was
  aimed at ADR 0004's worked-example tables instead, and node counts (9 / 7 / 6) were confirmed
  row-by-row against them.
- **Reviewers verified mechanisms, not just claims.** One decompiled `json-schema-validator` 2.0.0 to
  confirm the adapted `SchemaRegistry` API was not silently returning an empty violation set; another
  installed `jsonschema` and validated the new gate against five synthetic documents; a third compiled
  a probe definition to prove `catch.when` was being dropped. None of that was in their instructions.
- The deliberate red window worked as designed. `V2GoldenTest` stayed red from `d02bab9` through
  `6c44cb1` and closed at `686699a`; every other test class was green at every commit in between.
- The rate-limit kill lost no work. The fix-wave agent finished all nine edits before dying and left
  them on disk; the controller ran the gate and committed them. Zero rework.

## 2. Misses

- 🔴 **The change did only ~60% of its own job until the final review caught it.** `catch.when`,
  `catch.exceptWhen` and `catch.as` were still being discarded — the exact defect class ADR 0004
  exists to end, on three sibling fields. Nothing in the ADR, proposal, design, or tasks mentioned
  them. Root cause: the ADR's Decision table enumerates two fields (`errors`, `retry`), and every
  artifact downstream inherited that enumeration without anyone checking the SDK's `TryTaskCatch`,
  which has six. The ADR itself diagnoses this pattern — `forkMode` was added in Phase 1 while `for`
  and `catch` were not — and the change repeated it one level down.
- 🟡 **I introduced a normative-text error while drafting the delta spec.** The classification
  requirement lost "as a `FlowNode`, and", leaving it asserting that controllers compile to
  `StepNode`s. `openspec validate` cannot catch a sentence that parses but says the opposite of the
  truth, and eight task reviews did not read it because it was not in any task's diff.
- 🟡 **The capability spec governing the rewritten schema was never added to the delta.** The change
  rewrote the `scope` enum in the schema file, but `single-node-definition-contract`'s spec of record
  still documented `try`/`catch`/`forkBranch`. After archive the repo would have contradicted itself,
  and it is the spec Phase 2/3 implementers read.
- 🟡 **`dws-flow` would have rejected every shape the compiler now emits.** Its loader accepted
  `["main", "for", "try", "catch", "forkBranch"]`. Real exposure was zero (no CI, no deploy path), but
  the root `CLAUDE.md` warns specifically about this class of cross-package drift and no task looked.
- 📌 One commit message (`9afcf30`) has its attribution trailers folded onto the subject line rather
  than separated by a blank line.
- 📌 The `<versionId>` placeholder convention, the stale `forkBranch` fixture files, and the byte-exact
  `specText` comparison were all discovered by reading `V2GoldenTest` while writing the plan. None was
  documented anywhere; a future fixture-regenerating change will rediscover them.

## 3. Plan deviations

| Plan task | What changed | Why |
|---|---|---|
| 3 | Also updated `classifiesForkAsItsOwnFlowNodeWithBranchNodes` | That fork's branch root is a `for`, so it gained a `.do` child. Mechanical consequence; Task 5 rewrote the test anyway |
| 4 | Also updated `classifiesNestedTryForAndCatch` | The new `fulfill-order-try` node changed its `flatten()` sequence |
| 6 | Also fixed `SingleNodeDefinitionTest.rendersAFlowNodeWithChildrenAndCatch` | That class validates rendered output against the real schema file, so tightening the schema genuinely broke a test using the retired `scope: "try"` shape |
| 8 | Also fixed the raise-and-recovery example and acceptance criterion #7 | Same class of staleness as the sections the brief named; reviewer confirmed as legitimate, not creep |
| — | Added an unplanned fix wave (I1–I4, M1–M4, M6) plus a prose fix | Final whole-branch review returned 4 Important findings; see §2 |

## 4. Skill / workflow compliance

| Skill | Used |
|---|---|
| superpowers:brainstorming | ✓ |
| superpowers:writing-plans | ✓ |
| superpowers:using-git-worktrees | ✗ (adapted — see below) |
| superpowers:subagent-driven-development | ✓ |
| (transitive) superpowers:test-driven-development | ✓ |
| (transitive) superpowers:requesting-code-review | ✓ |
| superpowers:finishing-a-development-branch | ✗ (not reached — see below) |

### Deliberately Skipped Skills

- **`superpowers:using-git-worktrees`**
  - **What was skipped**: the skill invocation itself; its goal (an isolated workspace, not on
    main/master) was satisfied by other means.
  - **Why this cycle**: the session's own instructions mandate that all development happen on
    `claude/hello-im6ak2` and that pushing any other branch is forbidden. `git worktree add` requires
    a second branch name by construction, so invoking the skill would have produced a workspace whose
    branch could never be pushed. The execution environment is already an ephemeral remote container
    holding a fresh single-purpose clone, which is the isolation the skill exists to create.
  - **How to prevent recurrence**: `skill description tightening` — `using-git-worktrees`'s
    instruction should name the remote-container case explicitly: when the session is already an
    ephemeral isolated clone pinned to a mandated branch, the workspace requirement is met and the
    skill should confirm that rather than create a second worktree. Today the skill's text assumes a
    developer's shared working copy, which is the wrong model for this harness.

- **`superpowers:finishing-a-development-branch`**
  - **What was skipped**: the whole skill, not yet reached.
  - **Why this cycle**: its first action is opening a pull request, and this session's operating
    instructions state that a PR must not be created without the user explicitly asking. The user
    asked for the change to be implemented; they have not asked for a PR. The branch is pushed and
    ready, so the work is not blocked — it waits on a decision that is the user's to make.
  - **How to prevent recurrence**: `scope-judgment rule` — "implement this change" ends at a pushed,
    green, archived branch. Opening a PR is a separate, outward-facing act and should stay an explicit
    ask. This is the correct behavior, recorded here so the ✗ is not read as an omission.

## 5. Surprises

- **A regenerated fixture proves nothing on its own.** It matches the compiler by construction. The
  only load-bearing check is against the ADR's tables, and that had to be stated explicitly in every
  fixture-related dispatch or the reviewer would have "verified" a tautology.
- **`openspec validate` refuses to let a MODIFIED requirement drop a scenario name.** This makes
  renaming a scenario impossible when a behavior change invalidates its title, so six scenarios now
  carry titles their bodies contradict ("classifies into five nodes" → nine), each with an inline
  comment explaining why. The final reviewer judged the comments sufficient; the supported alternative
  (REMOVED + ADDED blocks) would lose the requirements' edit history.
- **The typed SDK model injects DSL defaults the author never wrote** (`each: item`, `at: index`).
  Reading controller configuration from the raw `JsonNode` walk was not a stylistic preference; going
  through the typed model would have silently written fields into pinned definitions that the source
  document never contained.
- **Two independent stale-comment findings surfaced from task reviews**, both describing the
  pre-ADR-0004 shape. Routing them to the docs task rather than fixing them in place kept task diffs
  clean and gave them a reviewer who was reading for prose accuracy.

## 6. Promote candidates → long-term learning

- [ ] 🔴 **When a change fixes "config X is dropped", enumerate the type's full field set before
      declaring the fix complete.**
      → **Promote to** CLAUDE.md
      > **Why**: `catch.when` / `exceptWhen` / `as` were missed because every artifact inherited the
      > ADR's two-field enumeration and nobody opened `TryTaskCatch`. This is the second occurrence of
      > the pattern in this codebase; ADR 0004 documents the first (`forkMode` added, `for`/`catch` not).
      > **How to apply**: when a change carries fields from a source type onto a target, list that
      > type's members (javap, the SDK schema, or the DSL spec) and account for every one — carried,
      > or explicitly deferred in writing.

- [ ] 🟡 **Normative spec prose needs a reviewer of its own; `openspec validate` only checks structure.**
      → **Promote to** schema
      > **Why**: the classification requirement asserted the opposite of the change and survived eight
      > task reviews, because delta-spec text belongs to no task's diff.
      > **How to apply**: superpowers-bridge's verify instruction should add a check that re-reads each
      > MODIFIED requirement body against the shipped behavior, not just its scenario names.

- [ ] 🟡 **A schema change is not done until every capability spec describing that schema is in the delta.**
      → **Promote to** schema
      > **Why**: `single-node-definition-contract` documented the retired enum and would have shipped
      > contradicting the schema file it describes.
      > **How to apply**: the bridge's `specs` artifact instruction should say that changing a file
      > under `openspec/schemas/` requires a delta for every capability whose spec references it.

- [ ] 🟡 **Cross-package consumers of a changed contract must be grepped, not assumed.**
      → **Promote to** CLAUDE.md
      > **Why**: `dws-flow`'s loader rejected every new shape. The root CLAUDE.md already warns about
      > this drift class for task-name→app-id resolution; the warning did not generalize to the
      > single-node schema.
      > **How to apply**: when changing a shared contract, grep the whole repo for its field names
      > before the final review, and record the result even when it is empty.

- [ ] 📌 **Document the golden-fixture regeneration mechanics.**
      → **Promote to** `dws-controller/CLAUDE.md`
      > **Why**: the `<versionId>` placeholder direction, the need to delete stale node files, and the
      > byte-exact `specText` comparison were each rediscovered by reading the test.
      > **How to apply**: add a short "regenerating v2 golden fixtures" section so the next change that
      > touches node shape does not re-derive them.

- [ ] 📌 **A subagent killed mid-task may have finished the work — check the tree before re-dispatching.**
      → **Promote to** memory
      > **Why**: the fix-wave agent hit a session rate limit after completing all nine edits but before
      > committing. Re-dispatching would have duplicated ~40 minutes of work.
      > **How to apply**: on any subagent failure, run `git status` and inspect the working tree before
      > deciding whether to retry.
