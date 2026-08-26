# Retrospective: dws-call-asyncapi

> Written: 2026-08-25 (after verify passed — PASS WITH WARNINGS)
> Commit range: `53f9f08..03b8f05`
> Worktree: main checkout, branch `claude/dws-call-asyncapi-runner-h1egcf` (PR #63, open)

---

## 0. Evidence

- **Commit range**: `53f9f08..03b8f05` — 3 authored commits (`c6e45c0`, `3bf738b`, `03b8f05`) plus
  one merge commit (`190d7a8`, bringing `origin/main` `67a31b6` in).
- **Diff size**: +7127 / −17 across 62 files for the range (the bulk is `pnpm-lock.yaml` and the new
  runner; the merge pulls in unrelated `main` files). PR #63 reports +7078 / −9 as the net vs base.
- **Tasks done**: 18/20 (`- [x]` × 18; `- [ ]` × 1 = 8.2 live integration; `- [~]` × 1 = 8.3, gated
  on 8.2). All implementation tasks complete; the two open items need live Dapr+Kafka infra.
- **Active hours**: ~1 session (single continuous build, no worktree switch).
- **Subagent dispatches**: 0 — built inline despite the user's "can dispatch subagents if possible"
  (see §4).
- **New external dependencies**: `@asyncapi/parser` ^3.4.0 (Apache-2.0) in the new
  `dws-call-asyncapi` package. Everything else (`ajv`, `ajv-formats`, `fastify`, `fastify-plugin`,
  `node-jq`, `undici`, `yaml`) mirrors `dws-call-openapi`'s already-vetted set. No `swagger-client`
  / `@readme/openapi-parser`.
- **Bugs encountered post-merge**: none functional. One process event: `main` advanced after the
  branch point, so PR #63 opened `mergeable_state: dirty` (roadmap conflict) — resolved in
  `190d7a8`.
- **OpenSpec validate state**: pass (`openspec validate dws-call-asyncapi --json` → 1 passed, 0
  failed).
- **Test coverage signal**: runner **47 vitest tests** (6 files) green; `dws-controller`
  `WorkflowCompilerTest` +2 cases; `dws-orchestrator` `WorkflowErrorsTest` +1 case. All three CI
  workflows `success` on the PR head.

Commit chain (chronological):

```
53f9f08 Merge pull request #60 ... (branch point)
c6e45c0 feat(dws-call-asyncapi): add call:asyncapi runner + openspec change
3bf738b feat(controller,orchestrator): compile call:asyncapi + classify payload validation
190d7a8 Merge remote-tracking branch 'origin/main' ... (resolve roadmap conflict)
03b8f05 docs(openspec): record dws-call-asyncapi CI-green evidence
```

---

## 1. Wins

- **File-for-file mirror of `dws-call-openapi` paid off** [evidence: `c6e45c0`, `dws-call-asyncapi/src/*`].
  Reusing the Fastify scaffold, `DOC_SHA256` pin, ajv shape, and the `POST /run`+`502` contract meant
  the only genuinely new code was `binding.ts` and the `asyncapi/*` resolution — the runner passed
  lint/test/build on the first full run.
- **Pure, fixture-testable operation resolution** [evidence: `operation.test.ts` 10 tests,
  `resolveOperation(doc, operationId)`]. Operating on a plain parsed document instead of the parser's
  intent model kept `operation.ts`/`validator.ts` unit-testable from JSON fixtures — same pattern the
  OpenAPI runner uses — while `@asyncapi/parser` still enforces validity at boot.
- **Controller/orchestrator Java compiled green in CI despite no local JDK 25**
  [evidence: `dws-controller` run 42 `success`, `dws-orchestrator` run 57 `success`]. The SDK-getter
  assumptions (`AsyncApiArguments.getDocument().getEndpoint()`, `getOperation()`, `getSubscription()`,
  `getAuthentication()`), confirmed against the DSL 1.0.0 schema before writing, all held.
- **Cross-component contract kept in sync** [evidence: `WorkflowErrors.VALIDATION_MARKER =
  "validation failed:"` ↔ `runner.ts` `BindingError('validation failed: …')`]. The marker string and
  the env-var names are the implicit contract between runner and controller/orchestrator, and both
  sides were written together.
- **Honest verify.md upgraded on real evidence** [evidence: `03b8f05`]. The verdict moved from FAIL
  (self-reviewed, uncompiled) to PASS WITH WARNINGS only after CI actually compiled the Java —
  assertions followed evidence, not the reverse.

## 2. Misses

- 🟡 [painful | evidence: `190d7a8`] The branch was cut from `53f9f08` but `main` had advanced to
  `67a31b6`; the PR opened conflicted. Rebasing/merging `main` *before* pushing would have avoided a
  `dirty` PR on arrival. The conflict was trivial (one roadmap table) but cost a merge round-trip.
- 🟡 [painful | evidence: local `java -version` = 21, `pom.xml` `maven.compiler.release=25`] Could not
  compile the two Java modules locally, so ~40% of the change shipped self-reviewed-only until CI
  confirmed it. Correct in the end, but the gap was real risk carried on the branch.
- 📌 [nit | evidence: PR #63 body] The UI-generated PR body says
  `StackSynthesizer.bindingConfigurations()`; the actual method is `bindingComponents()`. Cosmetic
  summary drift, not a code issue.

## 3. Plan deviations

| Plan task | What changed | Why |
|-----------|--------------|-----|
| 8.2 | Live Dapr+Kafka integration test not written | No Docker/Kafka/Dapr available; writing an unrunnable test would be dead weight. Documented as the sole remaining blocker in verify.md rather than faked. |
| 6.4 / 7.2 | Marked `[~]` then upgraded to `[x]` | Could not run `./mvnw` locally (JDK 21 vs 25); CI on the PR head compiled and tested green, so the evidence arrived post-push and the tasks were re-checked honestly. |
| 3.3 (`PAYLOAD`) | Single jq expression (default `.`), not a `{field: expr}` map like OpenAPI's `PARAMETERS` | A message payload is one object; a single expression is simpler and matches "interpolate against the workflow data document". Decision recorded in brainstorm §Q1. |

## 4. Skill / workflow compliance

| Skill                                            | Used |
|--------------------------------------------------|------|
| superpowers:brainstorming                        | ✓ |
| superpowers:writing-plans                        | ✓ (as the opsx `plan.md` artifact) |
| superpowers:using-git-worktrees                  | ✗ |
| superpowers:subagent-driven-development          | ✗ |
| (transitive) superpowers:test-driven-development | ~ (tests written with each module, not strict red-green) |
| (transitive) superpowers:requesting-code-review  | ✗ |
| superpowers:finishing-a-development-branch       | ✓ (branch pushed; PR #63 created from UI; CI driven to green) |

### Deliberately Skipped Skills

- **`superpowers:using-git-worktrees`**
  - **What was skipped**: the whole skill — no separate worktree was created.
  - **Why this cycle**: this is a remote CCR session whose container was provisioned with a fresh
    clone already checked out on the designated branch `claude/dws-call-asyncapi-runner-h1egcf`
    (system prompt "Primary working directory" + the branch instructions). There is no other
    workspace to isolate from — the isolation the skill provides is already the container's default.
  - **How to prevent recurrence**: `one-off — schema boundary case`. It is a boundary because the
    remote-execution harness already gives one-branch-per-container isolation; the worktree skill
    targets a local multi-branch checkout, which this environment never is. No prevention needed.

- **`superpowers:subagent-driven-development`**
  - **What was skipped**: the whole skill — all four components were implemented inline in one
    session.
  - **Why this cycle**: the four edit surfaces share one implicit contract (env-var names
    `DOC_ENDPOINT`/`BINDING_NAME`/`OPERATION`; the marker string `"validation failed:"`; the
    protocol→binding table). A subagent starts cold and would re-derive the whole cross-component
    context; a divergence in the shared strings between two agents is exactly the class of bug this
    change had to avoid. The plan's own "Cross-component contract check" section names this coupling.
  - **How to prevent recurrence**: `scope-judgment rule` — dispatch subagents for *independent*
    slices; keep tightly-coupled cross-component contracts (shared literals that must match byte-for-
    byte) in one agent. This cycle's judgment was correct; the rule is to keep applying it, not to
    change it.

- **`superpowers:requesting-code-review`**
  - **What was skipped**: no formal review-skill pass before pushing.
  - **Why this cycle**: two of four modules could not be compiled locally (JDK 21 vs 25), so the
    highest-signal review available was CI itself — it compiled and tested all three modules on JDK
    25. Self-review-against-the-mirrored-source plus green CI substituted for a review pass I could
    not fully ground locally.
  - **How to prevent recurrence**: `CLAUDE.md trigger` — for a change that can't be built in the
    session's toolchain, treat the first green CI run as the review gate and re-check the diff
    adversarially against it before claiming done (already done here via the verify.md upgrade). A
    follow-up on a JDK-25 machine could still add a `/code-review` pass.

## 5. Surprises

- **`@asyncapi/parser` installed and validated cleanly on Node 22** even though the package targets
  Node 24 — only an engines-version warning, no runtime issue for the fixtures. CI (Node 24) confirms.
- **The `main` branch had moved** between branch-cut and PR creation, so the very first PR event was a
  merge conflict, not a CI result. The subscription flow's "merge conflict first" ordering handled it.
- **The SDK's AsyncAPI arguments class is `AsyncApiArguments`** (title-cased `Api`), while the call
  wrapper is `CallAsyncAPI` (`API`) — inconsistent casing in the generated types, confirmed from the
  schema `title:` fields before writing, avoided an import miss.

## 6. Promote candidates → long-term learning

- [ ] 🟡 **Sync the working branch onto the latest default branch before the first push, not after the PR opens conflicted** → **Promote to project CLAUDE.md** (`CLAUDE.md` §Git Operations)
  > **Why**: PR #63 opened `mergeable_state: dirty` because the branch was cut from an older `main` (`53f9f08`) while `main` had advanced to `67a31b6`; the conflict surfaced only as a PR event.
  > **How to apply**: before the first `git push -u` of a feature branch, `git fetch origin <default>` and merge/rebase it in, so the PR arrives mergeable.

- [ ] 🟡 **When the session toolchain can't build a module, treat first-green-CI as the review/verify gate and let assertions follow that evidence** → **Promote to memory** (type: feedback)
  > **Why**: ~40% of this change (two Java modules) shipped self-reviewed-only under JDK 21 vs required 25; verify.md correctly stayed FAIL until CI compiled it green, then upgraded to PASS WITH WARNINGS.
  > **How to apply**: whenever `verify.md` for a change reports an environment-blocked build, keep the verdict non-PASS until CI confirms, then re-check tasks and the verdict against the CI run — never assert green from self-review alone.

- [ ] 📌 **Keep byte-for-byte cross-component contracts (shared env-var names, marker strings) in a single agent; only fan out genuinely independent slices** → **One-off** (records the §4 subagent-skip rationale)
  > **Why**: this change's runner/controller/orchestrator coupling is exactly the case where two cold subagents could drift on a shared literal (`"validation failed:"`, `BINDING_NAME`).
  > **How to apply**: at dispatch-decision time, if two candidate slices must agree on an exact string, keep them together.
