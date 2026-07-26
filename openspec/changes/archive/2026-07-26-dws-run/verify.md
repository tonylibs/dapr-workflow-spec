# Verification Report

**Change**: `dws-run`
**Verified at**: `2026-07-26 07:45`
**Verifier**: Claude (opsx:verify, superpowers-bridge schema)

PRECHECK: 37 commits since `merge-base HEAD origin/main`; 53 tasks already `- [x]` at precheck
time (54 after this run closed 5.6). Both positive — proceeded.

---

## 1. Structural Validation (`openspec validate --all --json`)

- [x] 全數 items `"valid": true`

**結果**：

```text
all valid: True | items: 5
  True admin-event-ingestion (spec)
  True admin-read-model-schema (spec)
  True admin-service-scaffold (spec)
  True dws-run (change)
  True event-publishing (change)
```

| Item | Type | Issues |
|---|---|---|
| — | — | none |

---

## 2. Task Completion (`tasks.md`)

- [x] 所有 `- [ ]` 已變為 `- [x]` — **54 / 54**

**未完成任務**：none.

Task 5.6 (`each of the three image builds succeeds`) was the last open item and was closed during
this verification. It was deliberately left unchecked all through apply because this environment has
no Docker daemon, so the images could not be built locally — the substitute criterion was a diff
proving the three Dockerfiles differ only in runtime `FROM` and `ENV MODE=`.

It is now closed on **stronger** evidence than the original criterion asked for: CI on PR #17 built
all three images for real.

| CI job | Conclusion |
|---|---|
| `build & push image (Dockerfile.shell, dws-run-shell)` | ✅ success |
| `build & push image (Dockerfile.script-js, dws-run-script-js)` | ✅ success |
| `build & push image (Dockerfile.script-python, dws-run-script-python)` | ✅ success |

The task text was amended to record that this was proven by CI rather than locally, so the record
does not overclaim a local build that never happened.

---

## 3. Delta Spec Sync State

| Capability | Sync 狀態 | 備註 |
|---|---|---|
| `run-step-execution` | ✗ 待 sync | New capability; `openspec/specs/run-step-execution/` does not exist yet. Archive creates it. |
| `run-step-configuration` | ✗ 待 sync | New capability; same. |
| `run-task-compilation` | ✗ 待 sync | New capability; same. |

`openspec/specs/` currently holds only `admin-event-ingestion`, `admin-read-model-schema`,
`admin-service-scaffold`. All three delta capabilities are **additions**, not modifications, so no
existing requirement is being overwritten and there is no merge conflict to resolve. This is the
expected pre-archive state.

**Archive-blocking issue resolved before reaching this point.** `run-task-compilation` previously
asserted a `dws.io/step-type` label that no code emitted — it existed only in hand-written example
manifests. Syncing that would have written a false requirement into `openspec/specs/`. The
controller now emits the label (`Labels.STEP_TYPE`, stamped in `StackSynthesizer.knativeService()`)
with slugs matching the manifests exactly, so the requirement is true before sync.

---

## 4. Design / Specs Coherence Spot Check

8 decisions (D1–D8), 24 requirements, 71 scenarios across three delta specs.

| 抽樣項 | design 描述 | specs 對應 | 差距 |
|---|---|---|---|
| D3 ARGUMENTS map shape | JSON object, key order preserved, rendered per runtime | `run-step-configuration` — "ARGUMENTS is a JSON object", "Shell renders arguments as flags", "Script images inject arguments as in-scope variables" | none |
| D5 exit-code semantics | non-zero is data under `code`/`all`, failure otherwise | `run-step-execution` — "Exit-code semantics depend on RETURN" (4 scenarios) | none |
| D6 RETURN/OUTPUT composition | two stages; JSON-parse fallback | `run-step-execution` — "RETURN selects the raw result value", "OUTPUT shapes the raw value", "Non-JSON output falls back to a raw string" | none |
| D7 three-way TaskKind split | rationale is `dws.io/step-type` readability from cluster state | `run-task-compilation` — "Run subtypes map to distinct task kinds and images", incl. "Step type is readable from cluster state" | none (was drifted; corrected — see below) |
| D8 orchestrator dispatch | one `getRunTask()` branch reusing `CallServiceActivity` | `run-task-compilation` — "Run tasks are dispatched over the existing service-invocation path" | none (was wrong; corrected — see below) |

**漂移警告**（非阻塞）：

Two drifts were found by the final whole-branch review and **corrected before this verification**,
rather than being recorded as accepted warnings:

- **D8 asserted the opposite of reality.** It claimed no `dws-orchestrator` change was needed
  because routing is name-derived. True inside `CallServiceActivity`, but reaching it requires
  `task.getCallTask() != null`, which a `run` task never satisfies — every `run` task hit
  `IllegalStateException("... has an unsupported type")`. D8 has been rewritten with the correction
  and the process lesson; the spec scenario "Orchestrator is unchanged" (which asserted the defect
  as a requirement) was replaced with dispatch and task-type-label scenarios.
- **D7's rationale referenced a label that did not exist.** Now emitted — see §3.

Remaining non-blocking observation: `TaskKind` is read only by the new label mapping and by tests.
That is now a real consumer, so the enum is no longer write-only.

---

## 5. Implementation Signal

- [x] Worktree 內無未 staged 的檔案 (`git status --porcelain` empty)
- [x] 所有相關 commit 已推送

**Commit 範圍**: `0c8b81e..1413f57` on `worktree-dws-run`, merged to
`claude/superpowers-plugin-uw11kz` and pushed through `3a1a66a`. 37 commits.

**Gate results — each run directly during verification, exit codes read without a pipe:**

| Suite | Command | Result |
|---|---|---|
| `dws-run` | `go vet ./...` / `gofmt -l .` / `go test -count=1 -race ./...` | clean / clean / all packages ok, **0 skipped** |
| `dws-controller` | `JAVA_HOME=…java-25… ./mvnw -o test` | **44 tests, 0 failures, 0 errors** |
| `dws-orchestrator` | `JAVA_HOME=…java-25… ./mvnw -o verify` | **19 tests, 0 failures, 0 errors** |
| PR #17 CI | 8 check runs | **all success**, incl. three `dws-run` image builds |

**Known environment limitation, stated rather than worked around**: `make lint`'s optional
`golangci-lint` sub-step cannot run here — the installed binary is built with go1.25 and cannot load
a go1.26 module. Reproduced on the unmodified `dws-call-http`, so it is pre-existing and
environment-wide, not caused by this change. The repo's documented CI gate
(`go vet ./... && go test ./...`) passes, as do `make vet` and `make fmt-check`. The plan's wording
"`make test` and `make lint` green" is therefore **not** fully satisfied locally; it is recorded here
as an unmet criterion rather than redefined.

---

## 6. Front-Door Routing Leak Detector（warning，非阻塞）

- [x] 無檔案

`ls docs/superpowers/specs/*.md` → no matches. Brainstorm output was correctly redirected to
`openspec/changes/dws-run/brainstorm.md`; `writing-plans` output to `plan.md`. No leak.

| 檔案 | 內容是否已 captured 進 change | 建議動作 |
|---|---|---|
| — | — | none |

---

## 7. Deferred Manual Dogfood vs Automated Test Equivalence

`plan.md` contains **zero** `[~]` deferred rows (`grep -c '\[~\]'` → 0). Per the schema's rule, this
section is not applicable and is left blank — PASS.

| Deferred dogfood (plan §) | Equivalent automated test | Coverage assessment | 真正 gap? |
|---|---|---|---|
| N/A — no `[~]` rows in plan.md | — | — | — |

---

## Overall Decision

- [x] ⚠️ PASS WITH WARNINGS — 可進入後續步驟但需注意

**PASS** on all seven checks. Two warnings, neither archive-blocking:

1. **`make lint` cannot be fully run in this environment** (§5). The golangci-lint sub-step is
   broken repo-wide for go1.26 modules. An acceptance criterion in `plan.md`/`tasks.md` is therefore
   unmet locally, recorded honestly rather than restated as met. CI does not gate on golangci-lint,
   so this does not affect merge.

2. **One Minor finding from the final review remains open by explicit user scoping**:
   `jsReservedWords` in `dws-run/internal/runner/arguments.go` omits `undefined`, `NaN`, `Infinity`,
   `process`, and `console`. Each breaks the generated JS prelude — `process` and `console` are the
   worse pair, since shadowing them makes the *author's* script fail rather than producing a clean
   validation error. Same failure class as the reserved-keyword bug already fixed. The user scoped
   the fix wave to four findings; this is the fifth. Carry to retrospective as a follow-up.

Four other deferred minors were triaged by the final review as fine to defer: `parseTimeout`'s
hardcoded key, the untested `ENVIRONMENT` override, `validIdentifier` rejecting Unicode identifiers
(fails safe and matches the controller), and the missing "optional fields absent" compiler test.

One cross-component observation for a **separate** change, deliberately not fixed here:
`dws-call-http/internal/server/server_test.go` has no test asserting `500` — the identical gap this
change fixed in `dws-run`. It has propagated through two of three step services. Out of scope per
the proposal's explicit non-goal.

**下一步**：produce `retrospective.md` while context is hot, then `openspec archive -y`. PR #17 is
already open on this branch and updates on push, so the archived cycle lands in its diff rather than
as trailing post-merge commits.
