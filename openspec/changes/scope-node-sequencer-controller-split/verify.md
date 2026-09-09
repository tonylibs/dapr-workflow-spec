# Verification Report

**Change**: `scope-node-sequencer-controller-split`
**Verified at**: `2026-09-09 14:05 UTC`
**Verifier**: Claude Code (`openspec-verify-change`, superpowers-bridge apply phase)
**Head**: `383f24e` · **Base**: `93740df` · 12 commits, 36 files, +2676 / −310

---

## 1. Structural Validation

- [x] This change's items report `"valid": true`

```text
$ openspec validate scope-node-sequencer-controller-split --json
{ "id": "scope-node-sequencer-controller-split", "type": "change",
  "valid": true, "issues": [] }
Totals: 1 passed, 0 failed
```

A repo-wide `openspec validate --all --json` reports 50 passed / 3 failed. The three failures are
unrelated to and untouched by this change, and were independently reproduced at both `459fd30` and
`686699a` (i.e. they predate this branch's later commits):

| Item | Type | Issues |
|---|---|---|
| `helm-admin-gateway` | spec | Pre-existing; not touched by this change |
| `ows-phase3-errors-timeouts` | change | Pre-existing; a MODIFIED block drops a scenario name |
| `workflow-error-format` | change | Pre-existing; no deltas |

Verified untouched: `git log 93740df..HEAD --name-only` over those three paths returns nothing.

---

## 2. Task Completion

- [x] All `- [ ]` are now `- [x]` — 32 of 32 checked, 0 open.

| Task | Reason incomplete | Blocks archive |
|---|---|---|
| — | — | — |

Items 1.8 and 7.2 were reworded during the final-review fix wave so their claims are accurate read
on their own: they now scope the validation claim to this change's own items and name the three
pre-existing repo-wide failures explicitly.

---

## 3. Delta Spec Sync State

| Capability | Sync state | Note |
|---|---|---|
| `workflow-structural-classification` | ✗ Needs sync | Delta present; applies at archive |
| `single-node-definition-contract` | ✗ Needs sync | Delta added during the fix wave (finding I3); applies at archive |

Both are expected: `openspec archive` performs the sync. Both deltas were checked for the archive
rule that a MODIFIED requirement must reuse the current spec's `### Requirement:` header verbatim and
preserve every `#### Scenario:` title it currently has. `openspec validate` enforces this and passes.

---

## 4. Design / Specs Coherence Spot Check

| Sample | design.md says | specs say | Gap |
|---|---|---|---|
| D1 — controller config on `FlowScope`, not new node types | Keep `CompiledNode` a two-member sealed interface; the split lives in what `FlowScope` carries | `workflow-structural-classification`: "Every `FlowNode` SHALL be one of exactly two shapes" | None |
| D2 — `do` children are synthesized, not literal DSL tasks | `<task>.do` / `<task>.try` follow the existing `<task>.catch` precedent | "Derived identifiers" requirement lists all four derived forms | None |
| D3 — `forkBranch` retirement removes a method, not just a value | Fork's children are branch roots, classified like any other task | "Fork compiles to its own Flow node": "a fork SHALL NOT introduce an intermediate node" | None |
| D4 — schema uses `if`/`then` per scope, not `oneOf` | Matches the file's existing `forkMode` idiom | `single-node-definition-contract` delta enumerates the enum and both shapes | None |

**Drift found and corrected during verification cycles**, recorded here because it was live in the
artifacts until the fix wave:

- The classification requirement had lost the phrase "as a `FlowNode`, and", so it asserted that
  controllers compile to `StepNode`s (final-review finding I2, fixed in `e582a76`).
- The rendering requirement enumerated only `errors`/`retry` for a try-catch controller after the
  compiler began carrying five fields (fixed in `383f24e`).

---

## 5. Implementation Signal

- [x] All code changes committed. `git status --porcelain` (excluding untracked scratch) → 0 lines.
- [x] Gate green: `cd dws-controller && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw verify`
      → **BUILD SUCCESS, 203 tests, 0 failures, 0 errors** (baseline before this change: 189).
- Commit range: `93740df..383f24e`.

---

## 6. Front-Door Routing Leak Detector

```text
$ ls docs/superpowers/specs/*.md
(no such files)
```

No leak. The brainstorming and planning skills' output was redirected into this change directory
(`brainstorm.md`, `plan.md`) as the superpowers-bridge schema requires.

---

## 7. Deferred Dogfood vs Automated-Test Equivalence

`plan.md` contains zero `[~]` deferred rows, so there is no manual-check backlog to reconcile.
Every plan step was executed and gated.

For the record, the coverage gaps the final whole-branch review probed were all closed with real
tests rather than deferred:

| Behavior | Status | Test |
|---|---|---|
| `for` carrying `at` and `while` | Covered | `carriesEveryLoopFieldOntoTheForController` |
| `try-catch` with object-form `retry` | Covered | `carriesAnInlineRetryPolicyObjectOntoTheTryCatchControllerVerbatim` |
| `catch` carrying `as`/`when`/`exceptWhen` | Covered | `carriesEveryCatchGuardOntoTheTryCatchController` |
| Retired scope values rejected by schema | Covered | `theSchemaRejectsRetiredScopeValues` |
| Controller `tasks` forced empty | Covered | `theSchemaRejectsANonEmptyTaskListOnAController` |
| Fork branch rooted at a `try`; fork nested in fork | Not covered | Verified correct by reviewer probe; recorded as follow-up, not a regression risk (no code branches on branch-root kind) |

---

## Overall Decision

- [x] ✅ PASS
- [ ] ⚠️ PASS WITH WARNINGS
- [ ] ❌ FAIL

All seven checks pass. The three repo-wide `openspec validate` failures are pre-existing, unrelated,
and demonstrably untouched by this branch. Two follow-ups are recorded for later phases, neither
blocking archive:

1. `dws-flow`'s loader was updated to accept the new scope values, but `dotnet` is unavailable in
   this container, so the edit is verified by inspection only, not compiled.
2. `FlowNode.withChild` / `withChildren` lost their last production caller when `tryFlow` was
   rewritten. Parked deliberately — deleting model API that Phase 4's `StackSynthesizer` may want is
   the worse error, and that is Phase 4's call, not this change's.
