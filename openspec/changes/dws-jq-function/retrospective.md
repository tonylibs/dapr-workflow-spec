# Retrospective: dws-jq-function

> Written: 2026-11-25 (after verify passed: PASS WITH WARNINGS, no CRITICAL; `verify.md` at `eac5959c`)
> Commit range: `30bd68d4..eac5959c`
> Worktree: `C:/Users/Admin/orca/workspaces/dapr-workflow-spec/jq-function` (branch `feat/jq-function`, not merged, not pushed)

Sources: `git log`/`git diff --stat` for the range, this change's `brainstorm.md`, `plan.md`, `tasks.md`,
`verify.md`, the SDD execution ledger `.superpowers/sdd/plan/progress.md` plus the per-task reports in the
same directory (`task-N-report.md`, `task-9-reconcile-report.md`, `final-fix-report.md`), and facts only the
orchestrating session knew (cited as "controller session").

---

## 0. Evidence

- **Commit range**: `30bd68d4..eac5959c` (35 commits; `30bd68d4` is the merge-base with `main`)
- **Diff size**: +9430 / -6 lines across 113 files (`dws-jq` alone: +6256 across 38 files; the rest is the `conformance/expressions/` corpus, CI/release wiring, docs, ADR 0014 and OpenSpec artifacts)
- **Tasks done**: 48/48 (`grep -cE '^\s*- \[x\]' tasks.md` = 48, 0 unchecked). `plan.md` keeps its own 74 step checkboxes unticked (verify S2).
- **Active hours**: about 7 h of work in a ~20 h window (2026-11-24 19:12 to 2026-11-25 15:20, +07:00). Commit density: 18 commits 19:00 to 00:59, then a gap of about 14 h (5-hour API rate limit cut the Task 9 review off at the end of the first stretch, then another session worked in the tree, see §2), then 17 commits 14:00 to 15:20.
- **Subagent dispatches**: about 45 (controller session). The ledger accounts for about 40 of them: 10 task implementers, 1 reconciliation implementer, 10 task reviews, 8 fix rounds (T2 follow-up, T3 r1, T4 r1 and r2, T5 r1, T6 r1, T9 r1, T10 follow-up), 8 scoped re-reviews, 1 opus final review, 1 final fix wave, 1 scoped final re-review. The rest are the verify and retro writers.
- **Findings caught by review before the final review**: 9 Important or blocking findings across T3, T4 (x2), T5, T6 (x2), T9 and T10, all fixed within 1 or 2 rounds (never more than round 2 of 5 allowed). The final opus review added 1 Critical and 1 Important.
- **New external dependencies**: 3 direct Go modules, all in `dws-jq/go.mod`: `github.com/itchyny/gojq` v0.12.20 (MIT), `github.com/santhosh-tekuri/jsonschema/v6` v6.0.3 (corpus case schema) and `gopkg.in/yaml.v3` v3.0.1 (corpus loader), no Dapr SDK (design D1; `verify.md` section on D1). No new dependency in any other package.
- **Bugs encountered post-merge**: n/a, not merged yet. Open residuals at retro time: W1 (Go `%q` redaction gap, parked), W2 (scale-to-zero with Dapr callers, handed to roadmap 2.0d), W3 (CI never run on GitHub), W4 (commits unpushed; verify counted 34 at its time, 35 at `eac5959c`), W5 (no native `-race` on windows/386).
- **OpenSpec validate state at archive**: not run yet (pre-archive). At verify: this change and both delta specs valid, 10 style warnings ("requirement text very long"); `openspec validate --all` has 2 pre-existing failures (`helm-admin-gateway` empty requirements, `workflow-error-format` no delta), both identical on `main`.
- **Test coverage signal**: `go test ./... -count=1 -cover` in `dws-jq` at write time: all 7 packages pass, 440 passing test and subtest lines. Statement coverage: `config` 100.0%, `secrets` 95.5%, `expr` 94.3%, `server` 93.4%, `logging` 92.4%, `conformance` 85.8%, root `main` 46.9% (exercised mostly by `TestMain_*` tests that build and exec the binary, which `-cover` does not attribute). The 55-case expression corpus runs inside `go test` with 0 divergences.

Commit chain (chronological, abridged to feature/fix pairs):

```
7c3994ef chore(main): init specs for jq function
d3bd95d7 feat(jq): scaffold dws-jq and pin its configuration contract          [T1]
2dbfde50 feat(jq): reject blocked builtins and dynamic $secrets by AST walk     [T2]
63d176b7 test(jq): pin binding-form rejections and $secrets error forms
f3b85063 feat(jq): compile once and evaluate with v1 result and truthiness      [T3]
79031ce5 fix(jq): encode results with gojq.Marshal for jq-identical output
541364c7 feat(jq): read referenced secrets from the Dapr secrets API and redact them [T4]
152dc470 fix(jq): redact secrets in gojq's escaped form and reject dot-segment names
93bf31d3 feat(jq): write the DWS structured log format                          [T5]
2d5bda41 fix(jq): redact every attribute value in text-mode logs
33d2c2fd feat(jq): serve POST /run and gate readiness on startup checks         [T6]
ec162809 fix(jq): report the last sidecar failure when the secret fetch bound expires  [T4 r2]
8559f8e3 fix(jq): enforce exact request fields and the 4 MiB cap
4568264e docs(openspec): track dws-jq-function progress through the server task
395e4cbb test(jq): add the conformance/expressions corpus                       [T7]
2e6c9059 test(jq): run the expression corpus in the dws-jq gate                 [T8]
41a7b705 ci(jq): add dws-jq image workflow and release-please package          [T9]
30ed3220 docs(jq): document dws-jq and its contracts
ed49bc89 fix(jq): bind only the exact value key and refuse the bulk secret route [T9 reconciliation]
f2e42b23 fix(jq): name site and expression in configuration startup errors
b835fc4a ci(jq): push the smoke-tested image and avoid Dapr's metrics port
b32bef13 docs(jq): rerun the corpus uncached and clarify readiness and secrets
aa6ded86 ci(jq): retry the smoke health check on every connection error        [T9 r1]
37496e44 docs(jq): state the dialect coverage and error records precisely
4d1af28f docs(jq): record EX-3 results for dws-jq                               [T10]
ced0e69a docs(adr): record dws-jq 2j findings in ADR 0014
a0a5d2bf docs(jq): scope the EX-3 cold-start findings to what was measured
1da55c4e docs(openspec): mark dws-jq-function tasks complete
2d55365d fix(jq): redact secret prefixes in gojq's truncated value previews     [final wave, Critical]
7a942ecd test(jq): pin the configuration contract to the names Load reads
6d3996a1 fix(jq): retry client timeouts and accept null variables
1a321a40 test(jq): reject non-yaml files in the expression corpus
4942e66f fix(jq): name the expression on secret-read startup failures
ef5ca713 docs(jq): warn about scale-to-zero with Dapr callers and scrubbed expressions
eac5959c docs(openspec): treat null variables as absent in the dws-jq request contract
```

---

## 1. Wins

- **Two-stage review earned its cost on a security-sensitive package.** Every task got a fresh reviewer and every fix got a scoped re-review. Review (not the implementers' own tests) caught: `Encode` hand-rolling `encoding/json` and escaping U+2028/2029/DEL unlike jq (T3, `79031ce5`); the redactor building its escaped form with `encoding/json` while gojq writes error messages with its own escaper, so DEL and `<>&` forms leaked (T4, `152dc470`); dot-segment secret names (T4, same commit); the text-mode `level`-key redaction bypass (T5, `2d5bda41`); a vacuous 4 MiB cap test (invalid JSON, so it never reached the cap) and a case-insensitive field-name bypass of the unknown-field 400 (T6, `8559f8e3`); the smoke-test `curl` retry gap on empty-reply/reset during container start (T9, `aa6ded86`).
- **Final whole-branch opus review found what per-task review could not.** CRITICAL: secret prefixes leaked through `gojq.Preview` truncation for secrets longer than about 28 bytes, into both `fault.detail` and logs. Per-task reviews had each checked "the redactor covers raw and escaped forms" and none looked at gojq truncating a value before the redactor sees a whole form. Fixed in `2d55365d` with a new redactor (collects all form matches plus every ` ...` marker with up to 30 preceding prefix bytes, merges ranges, keeps the marker). It also found that the configuration contract test pinned a hand-written `Names` list instead of what `Load` actually reads; `7a942ecd` fixed it, so the root `CLAUDE.md` claim ("a name added or dropped on the `dws-jq` side without a fixture fails the build") is now true.
- **A review-found flake was routed to its owner.** Task 6's implementer saw `TestFetch persistent_404` report a deadline instead of the real 404 under load; the controller routed it to the Task 4 owner (`ec162809`, "report the last sidecar failure when the secret fetch bound expires") rather than patching it in T6.
- **Real measurements, not assumptions, closed the design.** EX-3 (`4d1af28f`) measured warm Dapr p50 2.4 ms / p95 3.5 ms and a cold start via the Knative route of about 0.97 s, and exposed the scale-to-zero incompatibility (see §5). ADR 0014 and roadmap 2j were updated from measured numbers (`ced0e69a`), and a follow-up review scoped the cause claims to what was measured (`a0a5d2bf`).
- **Corpus and conformance went in as a language-neutral asset.** `conformance/expressions/` has 55 cases (13 carried over from data-flow plus 3 false-positive guards in `builtins`), run inside `go test` by `internal/conformance` (`TestExpressionCorpus`, 55/55, 0 known divergences). The reconciliation adopted exact-int comparison with float64 otherwise (ruling in the ledger) so the integer-precision case is not vacuous.
- **Ledger rulings made deviations cheap to audit.** Each ruling in `progress.md` states its cost if wrong (Encode via `gojq.Marshal`, `"variables": null` as absent, `minScale: "0"` kept with a louder comment, exact duplicate request keys keep last-wins like jq). Verify could therefore classify the accepted deviations (design D8, D9, D7) in a few lines rather than re-litigating them.
- **Pre-flight scan before Task 1** (ledger "Pre-flight scan" table) caught the `.exe` suffix issue for `main_test` on Windows, the `TestLoad_defaults` empty-slice-vs-nil trap, and the `tasks.md` 3.6 vs plan `Evaluate` signature mismatch before any code was written.

## 2. Misses

- 🟡 [painful | evidence: ledger "Task 1" note, `.claude/agents` listing] The `go-developer` subagent type is scoped to other Go packages (its definition names `dws-call-http`/`dws-call-grpc`/`dws-run`) and refused `dws-jq`. Every `dws-jq` implementer had to be re-dispatched as `general-purpose`, which loses that agent's Go idiom checks and Context7 wiring. Discovered on Task 1, noted in the ledger, but the agent definition was not fixed.
- 🟡 [painful | evidence: ledger "Resumed (this controller…)", `ed49bc89`, `f2e42b23`, `b835fc4a`, `b32bef13`] A second session resumed `apply` in the same worktree during the rate-limit gap and left uncommitted work: RED tests (exact-case `value` key, `bulk` secret name, config-error records carrying site and expression), a CI workflow change (push the smoke-tested image without rebuilding), `-count=1` in the Makefile, a Knative `dapr.io/metrics-port: 9095` annotation (queue-proxy owns 9090), README edits, and a non-gofmt'd `main_test.go`. The controller adopted it with a ruling and dispatched a reconciliation implementer to finish it, but nothing in the repo or schema prevents two sessions from driving one worktree, and the Task 9 review window was split in two by it. The adopted RED tests were real defects (the `bulk` secret name hits Dapr's `/v1.0/secrets/{store}/bulk` route and would return every secret), so the result was good by luck of the other session's diligence, not by process.
- 🟡 [painful | evidence: Task 9 review, `aa6ded86`] The first CI smoke step retried `curl` only on connection refused; an empty reply or reset during container start failed the job. Found by review, not by running the workflow, because `actionlint`, `make` and `golangci-lint` are not on the Windows host. W3 (verify) records that the workflow has still never run on GitHub.
- 🟡 [painful | evidence: ledger Environment, verify W5] No native race gate: Go on `windows/386` with CGO off has no `-race`. The server and secrets code is concurrent (`Evaluate` under load, readiness gating). The race gate was run once in Docker `golang:1.26.9` on `linux/amd64` rather than on every task.
- 🟡 [painful | evidence: ledger Task 6, `ec162809`] The secrets flake was only found because Task 6's load exposed it, i.e. Task 4's tests passed in isolation while the code reported a deadline instead of the real failure under load. Task 4's review had not run the package under contention.
- 📌 [nit | evidence: verify S2] `plan.md` keeps 74 step checkboxes unticked while `tasks.md` is 48/48. The plan checkboxes were never maintained, so the two artifacts disagree.
- 📌 [nit | evidence: `final-fix-report.md` "Final: parked"] A real but narrow leak is parked, not fixed: Go `%q` quoting in `strptime` and invalid-regex error messages writes a secret containing control characters or DEL in a form the redactor does not cover. One-line fix (add a `strconv.Quote` form to `NewRedactor`), deliberately not given a second wave. Recorded as W1.
- 📌 [nit | evidence: ledger deferred minors] Deferred minors remain: the YAML->JSON runner loses fidelity for `1.0`/huge ints (no current case depends on it), redactor cost about 0.1 s per MiB of ` ...` text, a "gave-up" reason may cite the previous attempt, README wording "starts like a secret" is reversed, and several T3/T4/T5/T8 coverage gaps (no negative/-0/1e999/+-2^63 decode rows, duplicate attribute keys, untested mixed int/float compare branch).
- 📌 [nit | evidence: verify S1, S3] Ten "requirement text very long" validate warnings, and design D9/D7 text does not yet describe the truncated-preview redaction form or the `bulk` refusal that shipped.

## 3. Plan deviations

| Plan task | What changed | Why |
|-----------|--------------|-----|
| T3 (`Encode`) | Plan mandated hand-rolled `finite()` + `encoding/json`; shipped as a thin wrapper over `gojq.Marshal` (`79031ce5`). | Review showed the hand-rolled path escaped U+2028/2029/DEL unlike jq. Spec intent is "jq output rules"; the mechanism was a means. Accepted in verify as same observable behavior. |
| T3 / tasks.md 3.5 | "assert no environ/input/module loader options" not asserted directly; satisfied by `Compile` passing only `gojq.WithVariables` plus policy tests. | gojq options are not introspectable. Ledger ruling. |
| T3 / tasks.md 3.6 | `Evaluate(ctx, timeout, Input)` per plan, not `Evaluate(ctx, input, vars, secrets)` per `tasks.md` wording. | Plan was precise, `tasks.md` informal. Ledger ruling, cost if wrong: a rename. |
| T4 | Added secret-name validation (reject `""`, `"bulk"`, `a/b`, `../x`, dot segments) and `url.PathEscape` on every name. `bulk` was added during the T9 reconciliation (`ed49bc89`), not in T4. | Reviewer minor 1 on T2, then the T9 WIP exposed that `bulk` routes to Dapr's bulk-secrets endpoint. |
| T4 | `value` key bound by exact case (`ed49bc89`). | Go's `encoding/json` struct decode is case-insensitive, so `Value`/`VALUE` bound silently. |
| T6 | Request decoding enforces exact field names (map-keyed, not struct) and a real 4 MiB cap (`8559f8e3`); `"variables": null` accepted as absent in the final wave (`6d3996a1`, spec/design alignment `eac5959c`). | Review found the struct decode bypass and the vacuous cap test; the final ruling matched Jackson callers, which write nulls by default. |
| T8 | Corpus runner compares numbers as exact int, otherwise float64 (deviation from "float64-only"). | A float64-only compare would make the integer-precision case vacuous; `10 == 10.0` still holds. Ledger ruling. |
| T9 | CI pushes the already smoke-tested image instead of rebuilding; Knative example avoids Dapr's metrics port 9090 (`dapr.io/metrics-port: 9095`); `-count=1` in the Makefile and docs (`b835fc4a`). | Adopted from the other session's WIP; rebuilding risks shipping a different image than the one smoked, and the corpus lives outside the Go module so cached results hide changes. |
| T9 / T10 | Knative reference keeps `minScale: "0"` (plan D12) with a louder warning comment and README note (`ef5ca713`) instead of switching to `minScale: 1`. | EX-3 showed Dapr invocation fails cold, but scale policy belongs to roadmap 2.0d / ADR 0014 O-1; the final-review ruling kept D12. |
| Final wave | Redactor rewritten (`2d55365d`) and `config.Names` contract test rebuilt around real reads (`7a942ecd`); client-timeout retries (`6d3996a1`); non-yaml corpus files rejected (`1a321a40`); expression named on secret-read failures (`4942e66f`). | Final review: 1 Critical, 1 Important, plus minors the controller chose to include (single fixer, full list). |
| Post-plan | Added a reconciliation implementer dispatch (not in plan.md) to finish the adopted WIP. | Another session's uncommitted work in the same tree (see §2). |

## 4. Skill / workflow compliance

| Skill                                            | Used |
|--------------------------------------------------|------|
| superpowers:brainstorming                        | ✓ (`brainstorm.md`) |
| superpowers:writing-plans                        | ✓ (`plan.md`, 10 tasks mapped to `tasks.md`) |
| superpowers:using-git-worktrees                  | ✓ (already in the isolated worktree `…/jq-function` on `feat/jq-function`; condition satisfied without creating a new one) |
| superpowers:subagent-driven-development          | ✓ (fresh implementer + task reviewer per task; scoped re-review per fix round; opus final review + one fix wave + scoped re-review; ledger in `.superpowers/sdd/plan/progress.md`) |
| (transitive) superpowers:test-driven-development |  ✓ (RED-first tests per task brief; the adopted T9 RED tests were driven to GREEN by the reconciliation implementer) |
| (transitive) superpowers:requesting-code-review  | ✓ (10 task reviews, 8 scoped re-reviews, 1 final whole-branch review, 1 final re-review) |
| superpowers:finishing-a-development-branch       | ✗ (pending: not run at retro time) |

### Deliberately Skipped Skills

- **`superpowers:finishing-a-development-branch`**
  - **What was skipped**: the whole skill has not run yet. It was not skipped by decision; it is ordered after `openspec archive`, and this retrospective is written before archive (controller session).
  - **Why this cycle**: at write time `git log origin/feat/jq-function..HEAD` lists the unpushed commits (verify W4), `verify.md`'s "Next step" is retro, then push, first CI run, then archive, and the `feat/jq-function` branch is still unmerged. The schema places `retrospective` before `archive`, and `finishing-a-development-branch` after it, so every retro written to schema order will show this row as ✗.
  - **How to prevent recurrence**: `schema graph fix`: either drop `finishing-a-development-branch` from the retro's compliance table (it is a post-archive step and cannot be evidenced pre-archive), or mark it "pending (post-archive)" in the retro template so a pre-archive ✗ is not read as a skip. If the row is kept, the next cycle should re-check it at archive time and flip it to ✓ with a forward-pointer (`> **Update YYYY-MM-DD**: …`) instead of rewriting this section.

## 5. Surprises

- **gojq the library is not gojq the CLI.** ADR 0014 Decision 4 (the dialect table) described CLI behavior. The library has no `debug`, `stderr` or `input_filename` at all (not "disabled", absent), plus `keys_unsorted`, `$__loc__` and `input_line_number` unsupported in v0.12.20. The blocked-builtins policy and corpus had to be reconciled with the library surface (ledger Task 3 self-note; verify "dialect coverage" row).
- **Dapr service invocation cannot wake a scaled-to-zero Knative service.** EX-3: cold via Dapr invocation failed 5/5 with `ERR_DIRECT_INVOKE` (zero pods, unresolvable app-id), while the same cold start via the Knative route took about 0.97 s. D12's `minScale: "0"` default therefore fails for every Dapr-invoked caller. Handed to roadmap 2.0d / ADR 0014 O-1; controller task 2m.2 must not render `minScale: 0` for Dapr callers (verify W2).
- **Go's `encoding/json` struct decoding is case-insensitive.** `{"Input": …}` or a secret store reply keyed `Value` bound to the `input`/`value` fields. It defeated "unknown field = 400" (T6) and exact `value` key binding (T4/T9). Fixed by decoding into maps for the contract-bearing paths.
- **gojq error previews truncate values at 30 bytes** with a ` ...` marker, so a secret longer than about 28 bytes is cut before any "whole value" redactor can match it. Only the opus final review caught it (§1); neither the ADR nor the plan mentioned previews.
- **A redactor that reproduces one engine's escaping from another library is wrong by construction.** The T4 redactor used `encoding/json` escaping but the text came from gojq's own writer (DEL, `<>&`, U+2028/2029 differ). The fix generates the escaped form through `gojq.Marshal`.
- **A passing contract test proved nothing.** The configuration contract test compared fixtures to a hand-maintained `Names` list that `Load` did not itself consult (final review, `7a942ecd`), so the guarantee written into the root `CLAUDE.md` was not true until the final wave.
- **Cached test results hid changes outside the module.** The corpus lives in `conformance/expressions/`, outside the `dws-jq` Go module, so `go test` served cached results after a corpus edit. Resolved by `-count=1` in the Makefile and docs (`b32bef13`, `b835fc4a`).
- **`openspec validate --all` was already red on `main`.** Two unrelated pre-existing failures meant "validate passes" could only be asserted for this change, not the repo.

## 6. Promote candidates → long-term learning

- [ ] 🔴 **Test redaction with long secrets and with every engine-specific escaping and truncation form, not just raw and JSON-escaped forms** → **Promote to project CLAUDE.md** (root `CLAUDE.md`, under the `dws-jq` configuration contract bullet, and `dws-jq/CLAUDE.md`) and **memory** (type: feedback)
  > **Why**: Two separate leaks in one change: T4 (escaping derived from `encoding/json` while gojq writes with its own escaper, `152dc470`) and the final CRITICAL (gojq `Preview` truncation at 30 bytes leaked prefixes of secrets over about 28 bytes, `2d55365d`). A third (Go `%q` quoting, W1) is parked. Each time the earlier tests only used short printable-ASCII tokens, which are identical in every escaping form.
  > **How to apply**: any code that scrubs secrets from engine-generated text (error messages, previews, log attributes): the test matrix must include a secret longer than the engine's truncation width, one with control characters, DEL and `<>&`, and the engine's quoting forms (`%q`, JSON, gojq), and the escaped form must be produced by the same library that writes the text.

- [ ] 🟡 **Go `encoding/json` struct decode matches field names case-insensitively; use map keys (or a case-sensitive decoder) for exact request/response contracts** → **Promote to project CLAUDE.md** (`dws-jq/CLAUDE.md` and the Go style notes for `dws-call-http`/`dws-call-grpc`/`dws-run`) and **memory** (type: feedback)
  > **Why**: `{"Input":…}` bypassed the unknown-field 400 in T6 (`8559f8e3`) and `Value`/`VALUE` bound as the Dapr secret `value` (`ed49bc89`); both were found only because a reviewer or another session thought to probe case. The same decode pattern very likely exists in the sibling Go step services.
  > **How to apply**: whenever a Go handler or client defines an exact wire contract ("any other key is 400", "bind only the `value` key"), decode to `map[string]json.RawMessage` or check keys case-sensitively before struct decode, and add a mixed-case negative test.

- [ ] 🟡 **The `go-developer` agent's scope excludes new Go packages; fix the agent definition so a new `dws-*` Go package does not force a `general-purpose` fallback** → **Promote to agent definition** (`~/.claude/agents/go-developer.md` or the repo's agents directory; the `orchestrator` routing table should name `dws-jq` too)
  > **Why**: Every `dws-jq` implementer (10 tasks plus fixes, `progress.md` Task 1 note) was dispatched as `general-purpose` because `go-developer` is limited to `dws-call-http`, `dws-call-grpc` and `dws-run`; its Go and Context7 guidance was unavailable for the biggest new Go package in the repo.
  > **How to apply**: when adding a Go package (`dws-jq`, `dws-mid-*`), update the `go-developer` description and the `orchestrator` routing list in the same change that adds the row to the root `CLAUDE.md` package map.

- [ ] 🔴 **Dapr service invocation cannot start a scaled-to-zero Knative service: do not render `minScale: 0` for any step or function reached via Dapr invoke** → **Promote to project CLAUDE.md** (root `CLAUDE.md` cross-cutting rules, plus the roadmap 2.0d / ADR 0014 O-1 entry and task 2m.2 of the controller)
  > **Why**: EX-3 measured 5/5 `ERR_DIRECT_INVOKE` on a cold, scaled-to-zero `dws-jq` site called through Dapr, against about 0.97 s via the Knative route (`4d1af28f`, `ced0e69a`). The shipped Knative reference still carries `minScale: "0"` per plan D12 with only a warning (`ef5ca713`), so a copy-paste of the example reproduces the failure.
  > **How to apply**: any controller change that renders Knative Services for Dapr-invoked workloads (2m.2 jq sites, step services) must set a nonzero `minScale` or route through Knative; revisit when roadmap 2.0d resolves O-1.

- [ ] 📌 **Use `-count=1` when tests read files outside the Go module** → **Promote to project CLAUDE.md** (`dws-jq/CLAUDE.md` gate command and the root `CLAUDE.md` package gate table row for `dws-jq`)
  > **Why**: `conformance/expressions/` sits outside the `dws-jq` module, so a cached `go test` after a corpus edit reports old results; the Makefile and docs were changed to `-count=1` only after the other session hit it (`b32bef13`, `b835fc4a`).
  > **How to apply**: whenever a Go test reads fixtures or corpora outside its module root (or via an env override like `DWS_CONFORMANCE_EXPRESSIONS`), run it with `-count=1` in the local gate and CI.

> **Carry-forward note**: unchecked items above carry into the next cycle's retro for re-evaluation (`grep -A 5 '^- \[ \]' openspec/changes/archive/*/retrospective.md`). When the first `dws-jq` CI run on GitHub completes (verify W3) or `finishing-a-development-branch` runs, add a forward-pointer here rather than rewriting §0 or §4.
