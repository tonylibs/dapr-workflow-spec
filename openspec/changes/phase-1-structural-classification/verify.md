# Verification Report

**Change**: `phase-1-structural-classification`
**Verified at**: `2026-09-08 02:05`
**Verifier**: Claude Code (orchestrator, direct execution)

---

## 1. Structural Validation (`openspec validate --all --json`)

- [x] This change validates clean (`openspec validate phase-1-structural-classification` → valid).

**Result**: 48 passed / 3 failed across the whole repo. All 3 failures are pre-existing and
unrelated — `git diff --name-only 84acce8~1..HEAD` touches none of them.

| Item | Type | Issues |
|---|---|---|
| `phase-1-structural-classification` | change | none (valid) |
| helm-admin-gateway | spec | Spec must have at least one requirement (pre-existing, unrelated) |
| ows-phase3-errors-timeouts | change | MODIFIED omits a scenario (pre-existing, unrelated) |
| workflow-error-format | change | No deltas found (pre-existing, unrelated) |

---

## 2. Task Completion (`tasks.md`)

- [x] All 37 checkboxes are `- [x]`; zero `- [ ]` remain.

Each was substantiated at HEAD by the Task 7 reviewer rather than ticked on the implementer's word.
Item 3.2 was reworded during that task because it described `NodeNaming.branchScopeNodeId`, a method
deliberately deleted mid-branch when a fork-branch root became an ordinary named scope.

**Incomplete tasks**: none.

| Task | Reason incomplete | Blocks archive |
|---|---|---|
| — | — | — |

---

## 3. Delta Spec Sync State

| Capability | Sync state | Note |
|---|---|---|
| `workflow-structural-classification` | ✗ Needs sync | New capability; `openspec/specs/workflow-structural-classification/` does not exist yet. Created by `openspec archive`. |
| `workflow-compiler-strategy` | ✗ Needs sync | Exists in `openspec/specs/`; this change's MODIFIED requirements (v2 populates `flowStepGraph` + identity fields, `CompiledNode.flatten()`) are not yet merged in. |
| `single-node-definition-contract` | ✗ Needs sync | Base requirements are owned by the still-unarchived `workflow-runtime-v2-phase0-scaffolding` change, so `openspec/specs/` has no such capability yet. This change contributes ADDED requirements only (`fork` and `do` scopes, `forkMode`), so the two archive in either order without conflict. |

---

## 4. Design / Specs Coherence Spot Check

| Sample | design.md says | specs say | Gap |
|---|---|---|---|
| Fork classification (§D2) | Own `FlowNode`, `forkMode` from `compete`, empty `tasks`, branch children | "Fork compiles to its own Flow node" + 4 scenarios | none |
| Identifier derivation (§D3) | Flat name for named scopes; dotted only for `main`/`catch`/branch | "Derived identifiers and DNS-1123 app IDs" + 7 scenarios | none |
| `children` from `key()` (§D5) | Projection at render time, never a stored map | "the children object is projected from each child's key" | none |
| Collision rejection (§D6) | Pre-order walk, both nodeIds named | "Ambiguous derived identifiers are rejected" + 7 scenarios | none |
| Nested `do` (§D2) | `do` is a Flow scope | scenario + schema enum + classification table row | none |

**Drift warnings** (non-blocking):

- The four rejections added mid-branch as review fixes (dotted task names, duplicate `children` keys,
  multi-property task items, empty derived app ID) were initially implemented and tested but
  unspecified. Folded into the "Ambiguous derived identifiers are rejected" requirement during the
  final fix wave, together with the two new rejections from that wave, so they survive archive.
- `design.md`'s goal bullet claimed a tree "for any definition v1 accepts". The DNS-1123
  character-class check falsifies that — `Names.kebab` is shared with v1 and unchanged, so a
  non-ASCII task name compiles under v1 and is rejected by v2. Narrowed in `6942147`.

---

## 5. Implementation Signal

- [x] Working tree clean (`git status --porcelain` empty).
- [x] All commits pushed to `claude/v2-structural-compiler-classification-zpt6ro`.

**Commit range**: `84acce8` through `287da8c` inclusive — 28 commits, all of them this change's own
work (the branch's prior state ends at `1990347`).

**Gate**: `cd dws-controller && ./mvnw verify` → `Tests run: 158, Failures: 0, Errors: 0` /
`BUILD SUCCESS`, run at verification time on the current HEAD.

---

## 6. Front-Door Routing Leak Detector (warning, non-blocking)

- [x] No files. `ls docs/superpowers/specs/*.md` → none.

Both skill-produced artifacts were redirected as the schema requires: `superpowers:brainstorming`
output went to this change's `brainstorm.md`, and `superpowers:writing-plans` output to its
`plan.md`.

| File | Content captured in change | Action |
|---|---|---|
| — | — | — |

---

## 7. Deferred Manual Dogfood vs Automated Test Equivalence

`plan.md` contains zero `[~]` deferred rows (`grep -c '\[~\]'` → 0). Every task was executed and
gated. Section intentionally empty per the schema's rule.

---

## Overall Decision

- [x] ⚠️ PASS WITH WARNINGS — may proceed to retrospective and archive.

**Warnings**, none blocking:

1. **Phase 2 is gated by an unresolved design conflict.** A `for` node's `specText` carries no
   `each`/`in` and a `try`/`catch` node's carries no `errors`/`retry`; that configuration exists only
   in the parent's verbatim `tasks` entry. `docs/adr/0003-fork-as-a-flow-node.md` decides the
   opposite ("The scope's own behavior … lives entirely inside the child instance"). Recorded as an
   Open Question in `design.md` with an explicit "Phase 2 must not start until this is resolved".
   Deliberately not fixed here — the owner chose to record and gate rather than widen this change.
2. **v2 performs no semantic validation**, where v1 does via `semanticErrors`. A definition with no
   `do` compiles to a childless `main`. Unreachable while v1 is the default; must be closed before
   the Phase 5 default flip, and belongs in that change's scope.
3. **`json-schema-validator` is declared test-scope but still ships.** Verified at package time: the
   jar is present in `target/quarkus-app/lib/main/` and listed in `quarkus-app-dependencies.txt`, and
   `Dockerfile.jvm:96` copies `lib/` wholesale. Harmless — no main-source path reaches it, since
   `SpecParser` resolves to `WorkflowReader`'s no-validation reader — but the scope declaration does
   not mean what it appears to mean.

**Next step**: write `retrospective.md`, then `openspec archive -y`, then
`superpowers:finishing-a-development-branch`.
