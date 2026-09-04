# Retrospective: submission-preview-validation

> Written: 2026-09-05 (after verify passed)
> Commit range: `4a26c5ef..3aae1bd1` (branch `submission-preview-validation`, from `submission-validate-files`)
> Worktree: `C:\Users\Admin\orca\workspaces\dapr-workflow-spec\main` (not yet merged)

---

## 0. Evidence

- **Commit range**: `fd0eecf1^..3aae1bd1` — 9 commits (1 proposal, 6 feature, 1 fix, 1 docs), plus the verify commit
- **Diff size**: +6,782 / −43 across 34 files. The vendored `workflow-schema.json` alone is ~4,900 of those insertions, so hand-written change is roughly +1,900 / −43
- **Tasks done**: 27/27
- **Active hours**: ~1.5h wall clock; the two implementation agents ran ~18 min and ~25 min in parallel
- **Subagent dispatches**: 2 (nestjs-developer → Tasks 1-4; frontend-developer → Tasks 5-6), both one-shot, no follow-up messages needed
- **New external dependencies**: `ajv` (MIT), `ajv-formats` (MIT), `yaml` (ISC) — all in `dws-admin` only. Zero added to `dws-console`
- **Bugs encountered post-merge**: none (not yet merged). One defect found and fixed pre-merge: the 100 kB body-parser cap (`7e79d200`)
- **OpenSpec validate state at archive**: pass for this change (`--strict`); repo-wide `--all` has 2 pre-existing failures in other items
- **Test coverage signal**: `dws-admin` 17 suites / 108 tests; `dws-console` 8 files / 92 tests (13 added, baseline 79)

Commit chain:

```
fd0eecf1 docs(openspec): propose validation preview change
6d02cb98 chore: vendor the DSL JSON Schema from the SDK dws-controller pins
22b9decd feat: add spec-validation and dry-run preview calls to the admin client
6c5ec610 feat: detect duplicate task names across nested definition bodies
aa95e2d0 feat: validate definitions against the vendored DSL schema
2cc9f2df feat: preview a definition's deployment plan before submitting it
566f3385 feat: expose POST /definitions/validate on dws-admin
7e79d200 fix: lift the definition body cap to the documented 1 MiB
3aae1bd1 docs: record Phase 2 validation preview as shipped
```

---

## 1. Wins

- [evidence: `brainstorm.md` Q1 table; `provenance.json`] **Checking the roadmap's
  "unconfirmed" assumption before building on it was the highest-value 20 minutes
  of the cycle.** The roadmap named vendoring upstream 1.0.3 as the design.
  Extracting the schema the controller's parser is actually generated from (1.0.1,
  shipped inside `serverlessworkflow-types-7.26.0.Final.jar`) and diffing the two
  showed 1.0.3 would reject `run-shell.yaml` — a fixture the platform deploys
  today. That defect would have surfaced as "the validator is wrong" long after
  the design was set.
- [evidence: `fixture-parity.spec.ts`, 11 fixtures] **The correctness argument was
  turned into a test rather than left in prose.** Every controller fixture is
  asserted, and the two the controller rejects on deployability grounds are
  asserted *spec-valid despite* that — the layer boundary itself is now a test,
  not a paragraph in a design doc.
- [evidence: `6d02cb98` `vendor-dsl-schema.mjs` + `schema-provenance.spec.ts`]
  **Drift was made impossible to introduce quietly, not merely visible in review.**
  Both vendoring options the roadmap offered would have caught drift only if
  someone noticed; reading the SDK version out of `dws-controller/pom.xml` and
  asserting it in a test turns a forgotten revendor into a red build.
- [evidence: `22b9decd` diff — 147 insertions, 0 deletions] **`submitDefinition`
  is provably untouched.** The zero-deletion diff is the evidence, not a claim.
- [evidence: two agents, disjoint directories, zero conflicts] **Parallel dispatch
  worked because the endpoint contract was fully pinned in `plan.md` before either
  agent started.** The console agent built against a route that did not exist yet
  and needed no rework.

## 2. Misses

- 🔴 [blocking | evidence: `7e79d200`] **The plan specified a 1 MiB cap in the
  route and never checked the body parser underneath it.** `main.ts` registered
  `express.raw` and Nest's json parser with no `limit`, so the real cap was
  express's 100 kB default — the endpoint documented and spec'd a limit it could
  never apply, and the `POST /workflows` relay silently shared the defect. Caught
  by the implementing agent reading `main.ts`, not by the plan or the spec. A
  scenario asserting an *accepted* 500 kB body would have caught it; every test
  written asserted only the rejection side.
- 🟡 [painful | evidence: plan.md Task 1 Step 3; Task 4 Step 1] **Two defects in
  the plan's own verification material.** The guard-rail command used a wrong JSON
  path (`$defs.runTask.properties.run` is undefined; `run` is under
  `allOf[1]`), so the single command written to catch the wrong-schema mistake
  would itself have printed a misleading result. And the controller test's `VALID`
  constant was not valid under the schema the plan mandates (missing
  `document.dsl`/`namespace`, non-semver `version`). Both were written from
  memory of the shape rather than checked against the artifact in hand.
- 🟡 [painful | evidence: plan.md Task 6 Step 2] **The plan's console tests assumed
  a testing stack this repo does not have** (`@testing-library/user-event`,
  `jest-dom`). The agent adapted correctly, but the plan should have read
  `dws-console/package.json` before writing assertions against it.
- 📌 [nit | evidence: `dws-console/src/routeTree.gen.ts` uncommitted] Generator
  churn from the resolved router-cli version sat dirty in the worktree throughout
  and is still uncommitted — small, but it means "is the tree clean?" no longer
  answers "is my work committed?".
- 📌 [nit | evidence: verify.md §6] **No end-to-end run.** Both sides are unit
  tested and the contract is pinned, but nothing has exercised console →
  dws-admin → dws-controller against a live stack. The first real preview is
  still unproven.

## 3. Plan deviations

| Plan task | What changed | Why |
|-----------|--------------|-----|
| 1.3 | Verification command's JSON path corrected to `$defs.runTask.allOf[1].properties.run` | Plan's path returns `undefined`; the fact it checks still holds |
| 2.5 | `!seen.add(name)` → `seen.has(name)` / `seen.add(name)` | `Set.add` returns the Set, not a boolean — the plan flagged this inline and the agent applied it |
| 3.1 | `VALID` fixture replaced with a schema-complete definition | The plan's own example was invalid DSL; assertion left intact |
| 2.6 | `DEPLOYABILITY_REJECTS` became a real, used array driving a boundary assertion; `broken.yaml` asserted *rejected* rather than excluded | The plan declared it and never used it (an immediate lint failure); asserting rejection is stronger than the plan's fallback |
| 3.3 | `main.ts` was modified after all (parser `limit`), by the orchestrator not the agent | See Misses 🔴. The agent correctly declined a shared-path change outside its task's file list and reported it instead |
| 5.3 / 6.1 | `deployment-plan-view.tsx` uses the console's `.tbl`/`.tbl-wrap` styling, not `data-table.tsx`; deployability errors use `Banner variant="warn"` | `data-table.tsx` requires a live TanStack Table v9 instance, which a fixed listing has no use for; the warn variant is what makes the two error kinds visibly distinct, as the spec requires |
| 6.2 | `tsr.config.json` gained `routeFileIgnorePattern` | Required by the plan's own choice to colocate `new.test.tsx` under `src/routes/` |

## 4. Skill / workflow compliance

| Skill                                            | Used |
|--------------------------------------------------|------|
| superpowers:brainstorming                        | ✗ |
| superpowers:writing-plans                        | ✓ |
| superpowers:using-git-worktrees                  | ✗ |
| superpowers:subagent-driven-development          | ✗ |
| (transitive) superpowers:test-driven-development | ✓ |
| (transitive) superpowers:requesting-code-review  | ✗ |
| superpowers:finishing-a-development-branch       | ✗ (not yet — branch is unmerged) |

### Deliberately Skipped Skills

- **`superpowers:brainstorming`**
  - **What was skipped**: the whole skill; `brainstorm.md` was written directly as
    a raw capture of empirical investigation.
  - **Why this cycle**: the user's request arrived with the design already decided
    and documented (roadmap §6, "design decided 2026-09-03"), naming the approach,
    both layers, the non-goal, and three specific questions to resolve. The
    skill's core loop — ask clarifying questions one at a time, propose 2-3
    approaches — had no open design space to explore; what the three questions
    needed was reading `pom.xml`, extracting a jar, and diffing two schemas.
  - **How to prevent recurrence**: `scope-judgment rule` — when a change's design
    is already recorded in a repo doc and the request is "resolve these named
    open questions, then implement," the brainstorm artifact's job is evidence
    capture, not option generation. That is what it was used for here, and the
    schema's own instruction ("brainstorm.md is a RAW CAPTURE… format varies")
    accommodates it. Worth tightening the schema's brainstorm instruction to say
    so explicitly rather than leaving it to judgment each time.

- **`superpowers:using-git-worktrees`**
  - **What was skipped**: creating an isolated worktree for the apply phase.
  - **Why this cycle**: the session already runs inside a dedicated worktree
    (`…\dapr-workflow-spec\main`, distinct from the repo root) and the user asked
    for a sub-branch specifically. A nested worktree would have added a merge step
    with no isolation gained.
  - **How to prevent recurrence**: `schema graph fix` — the schema's apply phase
    should treat "already in a non-primary worktree on a feature branch" as
    satisfying the isolation requirement, rather than implying a fresh worktree
    unconditionally.

- **`superpowers:subagent-driven-development`**
  - **What was skipped**: the skill's per-task fresh-subagent + two-stage-review
    loop. Two agents were dispatched instead, each owning a 4-task and 2-task
    contiguous slice by component.
  - **Why this cycle**: the task graph splits cleanly along a component boundary
    with a fully specified interface between them (`POST /definitions/validate`,
    pinned in plan.md before dispatch). Per-task dispatch would have serialised
    two independent chains that have no shared files, and re-paid context
    acquisition six times for the same two codebases.
  - **How to prevent recurrence**: `skill description tightening` — the skill
    should name the case where tasks partition by component with a pinned
    interface, and say that per-component dispatch is the intended shape there,
    rather than leaving "one subagent per task" as the only documented path.

- **`superpowers:requesting-code-review`**
  - **What was skipped**: a dedicated review pass over the implementation.
  - **Why this cycle**: nothing forced it — this is the genuine gap of the cycle,
    not a justified skip. Review happened only as orchestrator spot-checks
    (diffstat for `submitDefinition`, `provenance.json` contents) plus each
    agent's self-report. A `code-reviewer` pass over `fd0eecf1..3aae1bd1` would
    have been cheap and is exactly where the 🔴 body-cap class of defect gets
    caught systematically rather than by luck.
  - **How to prevent recurrence**: `schema graph fix` — the superpowers-bridge
    schema has no artifact or apply step between `plan` and `verify` that requires
    review evidence. `verify.md` should require a "review performed by / findings"
    section, so skipping review is visible in the artifact rather than silent.

## 5. Surprises

- **The controller has no hand-written DSL model at all.** The roadmap's open
  question was phrased as "nothing pins a DSL version in `dws-controller`'s Java
  model (`Document`, `Workflow`, etc.)" — which reads as if those are DWS classes.
  They are generated SDK types, so the version *is* pinned, in `pom.xml`, one
  level of indirection away. The question was answerable the whole time.
- **The SDK ships its own schema inside the jar.** This removed the need to trust
  any external source for the vendored file and made the "same artifact by
  construction" argument available at all. It was not something the design
  anticipated; it changed option 3 from "least-bad" to "obviously right".
- **`duplicate task names` was already enforced in the controller.** The roadmap
  presented the custom uniqueness check as filling a hole. It is parity, earlier
  and with a pointer — still worth building, but the framing mattered enough to
  correct in the docs.
- **A body parser can silently invalidate a documented API limit.** The route's
  cap was correct, tested, and unreachable. Layer-below defaults deserve the same
  scrutiny as the code under review.

## 6. Promote candidates → long-term learning

- [ ] 🔴 **When a spec, schema, or contract is vendored to match another
      component's behavior, pin it to the artifact that component actually
      consumes — not to the upstream publication of the same thing.**
      → **Promote to memory** (type: feedback)
  > **Why**: roadmap §6 specified vendoring the OWS spec repo's 1.0.3 schema to
  > validate documents that `dws-controller` parses with SDK 7.26.0.Final's
  > 1.0.1 schema. The two disagree on `run.shell.arguments` (object vs array), so
  > the validator would have rejected `run-shell.yaml`, a fixture the platform
  > deploys today.
  > **How to apply**: at design time for any change that adds a second validator,
  > schema copy, type definition, or protocol description alongside an existing
  > one — find what the existing side loads at runtime and vendor that, then make
  > the version link mechanical (read it from the other component's manifest) and
  > assert it in a test.

- [ ] 🔴 **A cap, limit, or quota enforced in application code is not enforced
      until the layer beneath it is checked.** → **Promote to memory**
      (type: feedback)
  > **Why**: `POST /definitions/validate` correctly enforced and tested a 1 MiB
  > cap that no request could ever reach, because `main.ts` left both body
  > parsers at express's 100 kB default. Every test asserted the rejection side,
  > so the suite was green and the documented behavior was wrong.
  > **How to apply**: whenever writing a size, rate, timeout, or depth limit,
  > check the framework/proxy/gateway default underneath it in the same change,
  > and write one test on the *accepted* side of the boundary — not only on the
  > rejected side.

- [ ] 🟡 **Verification commands and example fixtures written into a plan must be
      executed against the real artifact before the plan ships.**
      → **Promote to memory** (type: feedback)
  > **Why**: this plan's single guard-rail command used a JSON path that does not
  > exist (`$defs.runTask.properties.run`), and its "valid definition" constant
  > was invalid under the very schema the plan mandates. Both were written from a
  > mental model of the shape rather than from the file.
  > **How to apply**: when writing a plan that embeds a shell command, a fixture,
  > or an expected output, run it in the planning session and paste the real
  > result — especially for the one command whose whole job is catching the
  > plan's central mistake.

- [ ] 🟡 **Dispatch subagents per component boundary with a pinned interface, not
      per plan task, when the task graph partitions cleanly.**
      → **Promote to project CLAUDE.md** (`AGENTS.md`, orchestration section)
  > **Why**: this repo is a monorepo of independently built components with
  > separate gates. Tasks 1-4 (dws-admin) and 5-6 (dws-console) share no files;
  > two parallel agents finished with zero conflicts because `plan.md` pinned the
  > HTTP contract between them before either started.
  > **How to apply**: when a plan's tasks group by component and the cross-component
  > interface is fully specified, dispatch one agent per component in parallel and
  > require explicit path-scoped `git add`; fall back to per-task dispatch when
  > tasks interleave in the same files.

- [ ] 🟡 **The superpowers-bridge schema has no step that requires code-review
      evidence between `plan` and `verify`.** → **Promote to schema**
      (`openspec/schemas/superpowers-bridge`)
  > **Why**: review was skipped this cycle with no friction and nothing recorded
  > it; the one blocking defect found (the body cap) was caught by an implementer
  > reading adjacent code, not by any systematic pass.
  > **How to apply**: add a required "Review" section to the verify artifact's
  > template (who/what reviewed, findings, disposition), so an unreviewed change
  > cannot produce a complete verify.md.

- [ ] 📌 **Exporting a component from a TanStack route file to make it testable
      costs a code-splitting warning.** → **One-off** (recorded, not promoted)
  > **Why**: specific to TanStack Router's file-based route plugin and to this
  > repo's choice to colocate route tests; the general fix (component in
  > `src/components/`, route file as a thin wrapper) is already conventional and
  > does not need a rule.
