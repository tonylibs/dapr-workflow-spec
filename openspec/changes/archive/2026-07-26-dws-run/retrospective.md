# Retrospective — `dws-run`

PRECHECK: `verify.md` exists; Overall Decision is `⚠️ PASS WITH WARNINGS`, not `❌ FAIL`. Proceeded.

---

## §0 Evidence

| Metric | Value |
|---|---|
| Commits (`merge-base origin/main`..HEAD, no merges) | **38** |
| Diff size | **53 files, +7304 / −40** |
| Files by component | `dws-run` 21 · `openspec` 7 · `dws-controller` 1 · `dws-orchestrator` 1 · `.github` 1 |
| Tasks done | **54 / 54** |
| Subagent dispatches | **24** — 12 implementers, 11 reviewers/re-reviewers, 1 final whole-branch review |
| New external dependencies | **0** (`dws-run/go.mod` has zero `require` lines; nothing added to `dws-controller`) |
| Post-merge bugs | **0** — not yet merged |
| `openspec validate --all` at archive | **all 5 items valid** |
| Test signal | `dws-run` 51 Go test funcs, 0 skipped · `dws-controller` 44 · `dws-orchestrator` 19 |
| CI at head | **8/8 checks green**, incl. all three image builds |

**Commit chain** (oldest → newest): `835158f 1207d47 c40e363 db4d173 90ec3aa 94dcf26 8f44bcb abe78ec
210fd36 c10a0c8 77583e6 1c7b2f1 db8694d 29b4cf8 a0cc9ab 2bc3803 9b544a6 0872f20 ab6991f 0916554
325ca8e d741bc6 918a9ea 0df1e07 214f4b8 9f8f02a 74206ef 974026a 3c9467e f36ef68 bb20593 41e147e
786ba24 cead846 1ec15df 71aecca 1413f57 5c9180c`

---

## §1 Wins

- **Verifying the DSL against the SDK before planning caught two wrong premises in the original
  request.** `javap` on `serverlessworkflow-types:7.26.0.Final` showed `run` has no `stdin` property
  at all, and that `arguments` is `Map<String,Object>` rather than a list. Both were stated as fact
  in the request. Had I planned from the request text, `ARGUMENTS` would have shipped as a JSON array
  that silently discarded every argument name the workflow author wrote.

- **Reviewers were asked to reproduce, not to read — and that repeatedly paid.** The Task 3 reviewer
  built a naive string-concatenating `shellArgv` and confirmed the metacharacter test still failed
  against it; it also discovered the `#` fix reduced the failure signature from "injection executed"
  to "value missing," which nobody had noticed. The Task 10 reviewer re-ran the ordering test against
  a sorting mapper. The fix-wave reviewer reverted to `786ba24` to prove the new precision test
  genuinely failed against old `normalize()`, and added a sixth `TaskKind` constant to prove the slug
  mapping is compile-time exhaustive rather than silently falling through.

- **Implementers pushed back on the plan instead of coding around it.** Task 2 refused to accept a
  hanging timeout test and found the plan's `exec.CommandContext` only signals the direct child.
  Task 3 found a test that could not pass against correct code. Task 11 found `arguments.go`'s doc
  comment contradicted its own keyword map and trusted the map. Each was reported rather than
  worked around.

- **Cross-component consistency was verified exactly, not assumed** (§0: `dws-controller` 44 tests).
  The Task 11 reviewer diffed the controller's keyword sets against the Go component's in both
  directions: JS identical at 46 entries, Python at 35, internal prelude names at 4. A mismatch
  either way is a real bug — one direction compiles definitions that cannot run, the other rejects
  legal ones.

- **Zero dependencies added** (§0), matching `dws-call-http`. The ordered-arguments decoder is
  hand-written against `encoding/json` rather than pulling an ordered-map library.

---

## §2 Misses

### 🔴 Blocking

**The design asserted the feature worked when it did not, and the acceptance criterion made that
assertion unfalsifiable.** D8 claimed `dws-orchestrator` needed no change because routing is
name-derived. That is true *inside* `CallServiceActivity`, but reaching it requires
`task.getCallTask() != null`, which a `run` task never satisfies — so every `run` task hit
`IllegalStateException("... has an unsupported type")`. The controller would have deployed a healthy
`dws-run` Knative Service that the orchestrator failed the instance before ever invoking.

The compounding error is worse than the original one: I made **"empty `dws-orchestrator` diff" an
acceptance criterion**. Every task dutifully confirmed the diff was empty, and each confirmation read
as evidence the assumption held. The design even contained the tripwire — *"if the implementation
finds itself editing the orchestrator, an assumption in this design is wrong"* — and I read it
backwards. A criterion asserting the **absence** of a change can only ever confirm itself.

Caught only by the final whole-branch review (`41e147e` corrects the artifacts, `786ba24` the code),
because that was the first review to look **across** the component boundary rather than within one
task's diff. The orchestrator had zero `run` coverage, so nothing could have failed.

### 🟡 Painful

- **Four defects in my own plan, each caught by execution rather than by planning.**
  - The `ctx.Err()` timeout proxy (`94dcf26`) would have misreported a legitimate non-zero exit near
    the deadline as a `SpawnError`, silently destroying the exit code Task 4's semantics depend on —
    and it would have surfaced as a Task 4 bug, not a Task 2 one.
  - `TestShellMetacharactersStayInsideOneArgument` (`210fd36`) could not pass against a correct
    implementation: `printf` re-applies its format across the `"$@"` operands `shellArgv` appends.
  - Task 4's Interfaces block declared `selectValue` returning `(any, bool, error)`, contradicting
    its own authoritative code block (`db8694d`).
  - The Task 6 test list omitted the `500` branch (`9b544a6`), leaving half the load-bearing
    502/500 split uncovered — a refactor collapsing the two `errors.As` checks would have passed CI.

- **A spec asserted a label that no code emitted.** `dws.io/step-type` appeared only in hand-written
  example manifests, yet D7's entire rationale rested on it and the spec asserted it. Archiving would
  have written a false requirement into `openspec/specs/`. Fixed in `71aecca`.

- **A false green nearly went unnoticed.** I read `$?` through a pipe (`./mvnw test | tail`) and
  reported the controller baseline as passing when the build had actually failed on
  `release version 25 not supported`. Caught on re-read, corrected in the ledger, and thereafter
  every Maven invocation either redirected to a file or parsed surefire XML — and I passed the
  warning into every subsequent dispatch.

### 📌 Nit

- The `stringify` `json.Number` case added for Finding 1 is **not load-bearing**: `encoding/json`
  special-cases `json.Number` in the pre-existing `default: json.Marshal(t)` branch, so the new test
  passes against the old code too. The real corruption lived entirely in `config.go`'s `normalize()`.
  Disclosed by the fix-wave reviewer; the case is reasonable defensive explicitness but I would have
  reported it as more essential than it was.
- `stringify`'s `float64` branch is now dead in production, reachable only from direct Go
  construction in tests.
- One report miscounted the JS keyword list as 41 when both lists actually hold 46 — prose only,
  both verified identical element-by-element.

---

## §3 Plan deviations

| Task | Deviation | Why |
|---|---|---|
| 2 | Implemented `interpreterFor` in its final two-value form immediately | The plan deliberately specified a broken `python3 -e` placeholder for Task 3 to "fix". The churn had no TDD value, and its RED step would have been vacuous. Consequence tracked forward: Task 3's RED had to target `shellArgv`/`scriptSource` only. |
| 2 | Added process-group kill (`Setpgid` + `Cancel` + `WaitDelay`) | Plan's sample used bare `exec.CommandContext`, which signals only the direct child; `sh -c "sleep 5"` kept running and held the pipes. Accepted as a genuine bug fix in my code. |
| 3 | Modified the metacharacter test's shell command (added `#`) | The plan's test could not pass against a correct implementation. Production code unchanged; `plan.md` corrected in `210fd36`. |
| 6 | Added a `500` test not in the plan's list | Spec-mandated behavior with no coverage. Root cause inherited from `dws-call-http`, which has the identical gap. |
| 12 | Fixed a wrong doc comment in already-merged `dws-run` code | `class` is reserved in **both** JS and Python; the comment claimed Python-legal. Found by Task 11, which correctly declined to fix it out of scope. |
| — | **`dws-orchestrator` modified at all** | The change's headline non-goal, reversed on explicit user approval after the final review proved the feature was inert without it. |
| — | Four post-review fixes | Integer precision, retry loop, step-type label, OCI labels — user-approved, one commit each. |

---

## §4 Skill / workflow compliance

| Skill | Used | Evidence |
|---|---|---|
| `superpowers:brainstorming` | ✓ | `brainstorm.md` — decision log Q1–Q9, redirected to the change dir, not `docs/superpowers/specs/` |
| `superpowers:writing-plans` | ✓ | `plan.md` — 12 TDD tasks, redirected to the change dir |
| `superpowers:using-git-worktrees` | ✓ | Native `EnterWorktree`; `.claude/worktrees/dws-run` on `worktree-dws-run` |
| `superpowers:subagent-driven-development` | ✓ | 24 dispatches (§0); ledger at `.superpowers/sdd/plan/progress.md` |
| `superpowers:test-driven-development` | ✓ (transitive) | RED evidence required and recorded per task; the one carried skip was removed in Task 5 |
| `superpowers:requesting-code-review` | ✓ (transitive) | Task review after every task; scoped re-review after every fix round |
| `openspec-verify-change` | ✓ | `verify.md`, 7 checks |
| `superpowers:finishing-a-development-branch` | ✗ | See below |

### Deliberately Skipped Skills

**`superpowers:finishing-a-development-branch`** — the schema's apply step 6 opens the PR as the
final action. PR #17 was created by the user from the Claude Code UI mid-cycle, before archive.
The skill's purpose (deciding how to integrate finished work, then opening the PR) is therefore
already satisfied by an existing open PR that updates on every push. Invoking it would either
duplicate the PR or do nothing. The schema's intent — *the PR diff must contain the complete
archived cycle* — is still met, because archive lands before any further push.

---

## §5 Surprises

- **`stdin` does not exist on `run` in DSL 1.0.0.** The original request treated whole-input-to-stdin
  as a "v1 simplification" with partial jq expressions as deferred debt. `RunTaskConfiguration` has
  exactly two members, `isAwait()` and `getReturn()`. The deferral was withdrawn as moot rather than
  carried as debt that could never be paid.

- **`arguments` is a map, not a list** — so the DSL frames script arguments as *values passed to the
  script*, not `process.argv` entries. This reshaped D3 entirely.

- **`golangci-lint` is broken repo-wide**, not for this change: a go1.25-built binary cannot load a
  go1.26 module, reproduced on unmodified `dws-call-http`. An acceptance criterion I wrote
  (`make lint` green) is unachievable in this environment for **any** component.

- **`TaskKind` was write-only** before this change — set by the compiler, asserted in tests, read by
  nothing. `dws.io/step-type` now gives it a real consumer.

- **The missing-500-test gap is repo-wide.** `dws-call-http` has the identical hole. I inherited it
  by faithfully mirroring the sibling; it has propagated through two of three step services.

- **Java 25 was not installed** despite the project requiring it; the first `apt` attempt 404'd on a
  stale index. Without `apt-get update`, half this change would have shipped unverified.

---

## §6 Promote candidates → long-term learning

1. **Never make "component X is unchanged" an acceptance criterion.** It is a claim to be proven by
   reading X's code, not satisfied by a clean `git diff`. Restate it as a behavioral test —
   *"a `run` task executes end to end"* — which fails loudly when the assumption is wrong. This one
   defect cost the most and was the most preventable.

2. **A cross-boundary review is not the same as N per-task reviews.** Twelve task reviews each passed
   cleanly; the defect lived in the gap *between* the controller that produced the step and the
   orchestrator that was supposed to invoke it. Budget for the whole-branch pass — it found the only
   Critical in the change.

3. **Ask reviewers to reproduce, not to read.** Every high-value finding here came from a reviewer
   that ran something: built a naive implementation, reverted a file, added an enum constant,
   executed a generated prelude. "Looks correct" found nothing that mattered.

4. **Never read `$?` through a pipe.** `./mvnw test | tail` returns the pipe's status. This produced
   a false green in this very session; parse the report XML or redirect and check `$?`.

5. **Verify SDK reality before planning against a request's description of it.** Two of this
   change's core premises were wrong in the request, and both were cheap to check with `javap`.

6. **When a test can't fail, it isn't a test.** Two plan-authored tests were structurally incapable
   of failing against correct code. Requiring observed RED output — not a claim of it — caught both.

### Follow-ups

| Item | Scope |
|---|---|
| `jsReservedWords` omits `undefined`/`NaN`/`Infinity`/`process`/`console` — each breaks the generated prelude; `process`/`console` make the *author's* script fail | This repo, `dws-run` + controller list. Open by explicit user scoping of the fix wave to four findings. |
| `dws-call-http` (and likely `dws-call-openapi`) have no test asserting `500` | Separate direct PR — test backfill, per the repo's own routing rules |
| `openspec/config.yaml` uses tab indentation, so the CLI cannot parse it and silently drops the project's `context`/`rules` for **every** change | Separate direct PR — config tweak. Applied manually throughout this change. |
| `TASK` is never set by the compiler for any step image, so every step reports its default name in 502 bodies and logs | Pre-existing, all three step images |
