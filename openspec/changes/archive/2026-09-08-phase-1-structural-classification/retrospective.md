# Retrospective — phase-1-structural-classification

**Written**: 2026-09-08
**Cycle**: Phase 1's second half — the v2 structural compiler's classification pass.

---

## §0 Evidence

| Metric | Value |
|---|---|
| Commits | 28, `84acce8` through `287da8c` inclusive |
| Diff size | 75 files, +5131 / −64 |
| Tasks done | 37/37 (100%) |
| Wall clock | ~11 hours, 2026-09-07 15:30 → 2026-09-08 02:10 UTC |
| Subagent dispatches | 21 — 9 implementer (one killed by a rate limit), 12 review |
| New external dependencies | 0. `com.networknt:json-schema-validator` was already on the classpath transitively; this cycle only declared it explicitly in test scope. |
| Post-merge bugs | n/a — not merged yet |
| OpenSpec validate at archive | This change valid; 3 pre-existing unrelated repo failures untouched |
| Test coverage signal | `dws-controller` 128 → 158 tests (+30). New: `SpecParserTest`, `NodeNamingTest`, `SingleNodeDefinitionTest`, `NodeClassifierTest`, `V2StructuralCompilerTest`, `CompiledNodeFlattenTest`, `V2GoldenTest` (6 worked examples + a schema negative control). |
| New production classes | `SpecParser`, `NodeNaming`, `SingleNodeDefinition`, `NodeClassifier` |

**Commit chain**: `84acce8` plan → `002f780`/`cdc9852` schema+doc → `c56e00b` SpecParser →
`36bfc4f`/`2ffccd8` NodeNaming → `5e9f942` SingleNodeDefinition → `2eeba0f` plan fix →
`4d34d7d`/`2da9964`/`1b03157`/`28926e7` classification pass → `267f0ea` golden fixtures →
`ed0f16f`/`d78ae5b` docs+tasks → `8579025`…`6436c86` final fix wave → `6942147` goal narrowing →
`287da8c` verify.

---

## §1 Wins

- **Reviews found four real bugs that tests did not.** The `children`-key collision on dotted task
  names (`28926e7`), the `catch`-named-task shadowing, the dead `catch.do` guard emitting an empty
  Dapr app per retry-only `try`, and `functionAppId` sitting outside the uniqueness check
  (`8579025`). Every one compiled clean and passed a green suite before review.
- **Reviewers ran code instead of reading diffs.** The Task 5 and final reviewers both compiled
  scratch programs against `target/classes` and reproduced each finding concretely. The
  `reserveItem`/`reserveItemFn` collision and the `naïveStep` schema violation were demonstrated,
  not hypothesized.
- **Two vacuity checks paid off.** A reviewer repointed `SCHEMA` at a permissive `{}` and confirmed
  the negative control fails as it should; an implementer temporarily reinstated the superseded
  branch-root rule to prove the new children-key invariant test actually fails
  (`expected ["notifyRecipients"] but was ["for"]`).
- **v1 stayed genuinely untouched.** The final review confirmed `V1OrchestratorCompiler`'s only
  change is the `SpecParser` delegation, and `WorkflowCompilerTest` never changed. The compatibility
  contract held throughout.
- **Escalations were honest.** Implementers reported DONE_WITH_CONCERNS four times, and three of
  those concerns were real: the validator API mismatch, the `children`-key/`tasks`-key divergence,
  and the incomplete scope enumeration. None were buried.

---

## §2 Misses

- 🔴 **The design contradicted itself on the exact point the change existed to fix.** §D5 asserted a
  flow's `children` keys always match its `tasks` entries while §D3's `<branch-nodeId>.<kind>` rule
  broke that for a structural task at a fork branch root. An implementer caught it, not the design
  review. Cost: a mid-task ruling, artifact patches across four files, and a fix round.
- 🔴 **Two plan-authored tests were unfalsifiable.** `assertThat(spec.at(...)).isNotNull()` passes for
  any path, since `JsonNode.at()` returns `MissingNode`; and `NodeNamingTest`'s `"a".repeat(70)` makes
  the kebab output equal its input, so the message assertion cannot distinguish nodeId from appId.
  Both came from the plan, and both survived until a review read them.
- 🟡 **The plan specified a non-existent library API.** Task 4's and Task 6's test code used
  `json-schema-validator` 1.x (`JsonSchemaFactory`/`SpecVersion`) against a 2.0.0 classpath. Caught
  by the implementer at execution time; cost one plan correction (`2eeba0f`) and a brief regeneration.
- 🟡 **A stale mermaid survived my own correction commit.** `2da9964` updated design, plan, proposal
  and spec when the branch-root rule changed but missed the diagram Task 1 had written — the exact
  doc/code contradiction this change exists to eliminate. Found by the Task 6 review, fixed in
  `ed0f16f`.
- 🟡 **An hour lost to a misdiagnosed environment.** Task 3 reported "OpenJDK 25 doesn't support
  `-release 25`" and asked to downgrade `pom.xml`. Real cause: `JAVA_HOME` pointed at JDK 21 while
  `java` on PATH was 25 — documented at `dws-controller/CLAUDE.md:109`. Task 2's agent had silently
  worked around it, hiding the problem for one task.
- 📌 **`docs(docs)` commit scope** on `ed0f16f` where `docs(roadmaps)` is the closer precedent.

---

## §3 Plan deviations

| Task | Deviation | Why |
|---|---|---|
| 3 | `NodeNaming.branchScopeNodeId` added, then deleted | The branch-root rule it implemented was withdrawn mid-cycle after §D5's contract conflict surfaced |
| 4, 6 | Validator API rewritten from the plan's code | Plan specified a 1.x API absent from the resolved 2.0.0 jar |
| 5 | `CompilerStrategyTest.v2LeavesLegacyEmpty` edited | Sanctioned in advance: its `flowStepGraph().isEmpty()` assertion was true only of the stub |
| 6 | Fixture count 6, not the requested 5 | The task text said five; the source doc carries six, and the sixth is the only `catch` with an `errors` filter |
| — | `pom.xml` edited in the final wave | Sanctioned for the explicit test-scope dependency declaration only |
| — | Worktree skipped | The session's branch mandate forbids a second branch, and git refuses a second checkout of a checked-out branch |

---

## §4 Skill / workflow compliance

| Skill | Used |
|---|---|
| `superpowers:brainstorming` | ✓ — 6 questions, decisions captured in `brainstorm.md` |
| `superpowers:writing-plans` | ✓ — `plan.md`, 7 tasks with TDD micro-steps |
| `superpowers:using-git-worktrees` | ✗ — see below |
| `superpowers:subagent-driven-development` | ✓ — fresh subagent per task, ledger at `.superpowers/sdd/plan/progress.md` |
| `superpowers:test-driven-development` | ✓ — transitively; RED→GREEN evidence in every task report |
| `superpowers:requesting-code-review` | ✓ — every task reviewed, plus a whole-branch final review |
| `openspec-verify-change` | ✓ — `verify.md`, PASS WITH WARNINGS |
| `superpowers:finishing-a-development-branch` | pending |

### Deliberately Skipped Skills

**`superpowers:using-git-worktrees`**

- **What was skipped**: worktree creation for the apply phase; work happened in the main checkout.
- **Why this cycle**: the session's operating instructions mandate development on
  `claude/v2-structural-compiler-classification-zpt6ro` and forbid pushing elsewhere. Git refuses to
  check out a branch already checked out in another worktree, so an isolated worktree would have
  required a second branch — which the mandate forbids. Not a judgment call about whether isolation
  was worth it.
- **How to prevent recurrence**: schema graph fix. The apply phase's step 1 should treat "a
  caller-mandated branch already checked out here" as a first-class case and say what to do — work
  in place, or create a worktree on a temp branch and merge back. Right now it reads as
  unconditional, so every mandated-branch session must improvise the same exception.

---

## §5 Surprises

- **`TryTaskCatch.getDo()` returns `[]`, never null.** The plan's `getCatch().getDo() != null` guard
  was therefore dead, and every retry-only `try/catch` — the most common form — got an extra empty
  Dapr app whose `catch:` pointed at a flow with nothing to run.
- **Test scope does not remove a jar from a Quarkus package.** `json-schema-validator` still lands in
  `target/quarkus-app/lib/main/` and in `quarkus-app-dependencies.txt`, and `Dockerfile.jvm:96`
  copies `lib/` wholesale. Both an implementer's and my own mental model of scope were wrong; the
  conclusion survived for a different reason than either of us gave.
- **`Names.kebab` passes non-ASCII letters through**, because it filters on
  `Character.isLetterOrDigit`. v1 has shipped that behavior for Knative Service names all along.
- **The DSL constrains task names not at all** — the SDK's own `workflow.yaml` gives a task item
  `minProperties: 1 / maxProperties: 1` and no name pattern. Everything downstream that assumed a
  task name is a clean identifier was assuming.

---

## §6 Promote candidates → long-term learning

- [ ] 🔴 **An assertion that cannot fail is worse than no assertion.** Two plan-authored tests
      (`at(...).isNotNull()`, `"a".repeat(70)`) passed unconditionally and hid the behavior they
      claimed to guard.
      → **Promote to** CLAUDE.md
      > **Why**: both survived TDD, a self-review and a task review, because a green test reads as
      > evidence regardless of whether it can go red.
      > **How to apply**: when writing or reviewing a test, state what change to the implementation
      > would make it fail. If the answer is "none", rewrite it. For schema/validator assertions,
      > include a negative control.

- [ ] 🔴 **When two design sections state a relationship, verify it against a worked example before
      the plan is written.** §D5's "children keys match tasks names" and §D3's branch-root rule
      contradicted each other; the fork example would have shown it in one line.
      → **Promote to** skill (`superpowers:writing-plans`)
      > **Why**: the conflict cost a mid-execution ruling, four artifact patches and a fix round —
      > all after code existed.
      > **How to apply**: during the plan's self-review, take each cross-section invariant and trace
      > it through the spec's most complex worked example, not the simplest.

- [ ] 🟡 **Verify library APIs against the resolved jar before writing plan code against them.**
      The plan specified `json-schema-validator` 1.x classes absent from the 2.0.0 on the classpath.
      → **Promote to** skill (`superpowers:writing-plans`)
      > **Why**: plan code is transcribed verbatim by implementers, so a wrong API becomes a blocked
      > task rather than a compile error caught while writing.
      > **How to apply**: before pasting library calls into a plan, resolve the dependency and
      > `javap` the classes, or read the version actually on the classpath.

- [ ] 🟡 **A correction to a decision must sweep every artifact that states it, diagrams included.**
      `2da9964` updated four prose artifacts and missed the mermaid.
      → **Promote to** CLAUDE.md
      > **Why**: this repo's changes routinely span ADRs, roadmap docs, schemas, specs and plans, and
      > diagrams are the easiest to miss because they are not prose.
      > **How to apply**: after changing a decision, grep the repository for the superseded literal
      > (an old identifier, an old rule's exact wording) rather than editing the files you remember.

- [ ] 📌 **Check `JAVA_HOME`, not `java -version`, when Maven reports a release-version mismatch.**
      → **Promote to** memory
      > **Why**: an agent lost roughly an hour and proposed downgrading a project-wide compiler
      > setting; the fix was one environment variable, already documented in the package's own
      > CLAUDE.md.
      > **How to apply**: on "release version N not supported", print `JAVA_HOME` before concluding
      > anything about the JDK on PATH.
