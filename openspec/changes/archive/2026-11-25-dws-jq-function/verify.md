# Verification Report

> Produced by the `openspec-verify-change` flow after apply completed. Section structure follows the
> schema template; prose is in English.

**Change**: `dws-jq-function`
**Verified at**: `2026-11-25`
**Verifier**: Claude (Sonnet 5.5), read-only verify pass at `eac5959c` on branch `feat/jq-function`

**PRECHECK**: commit evidence `git log 30bd68d4..HEAD` = 35 commits (> 0); `grep -c '^- \[x\]' tasks.md` = 48 (> 0). Proceeded.

---

## 1. Structural Validation (`openspec validate --all --json`)

- [x] The change `dws-jq-function` and both of its delta specs are valid. Two other items fail; both are pre-existing and unrelated (below).

**Result**:

```text
openspec validate dws-jq-function   -> Change 'dws-jq-function' is valid (10 WARNING lines, 0 ERROR)
openspec validate --all --json      -> items 74, passed 72, failed 2
                                       (change: 7 items / 6 passed / 1 failed; spec: 67 / 66 / 1)
go vet ./... && go test -count=1 ./... (dws-jq) -> all 7 packages ok
TestExpressionCorpus -> 55 cases, 55 pass, 0 fail, 0 known divergence, 0 stale divergence
```

| Item | Type | Issues |
|---|---|---|
| `helm-admin-gateway` | spec | "Spec must have at least one requirement". Pre-existing. Not touched by this branch. |
| `workflow-error-format` | change | "Change must have at least one delta". Pre-existing. Not touched by this branch. |

Confirmed pre-existing: `git diff main -- openspec/specs/helm-admin-gateway openspec/changes/workflow-error-format` is empty. Both match `main`, so they are non-blocking for this change.

The 10 WARNINGs on `dws-jq-function` are all "Requirement text is very long (>500 characters)", on the ADDED requirements of `jq-expression-function` and `jq-expression-conformance`. These are style warnings only; the requirements are long because they enumerate the contract (fields, env vars, verdicts). See SUGGESTION S1.

---

## 2. Task Completion (`tasks.md`)

- [x] All tasks are `- [x]`: 48 checked, 0 unchecked.

**Incomplete tasks**: none.

| Task | Reason | Blocks archive? |
|---|---|---|
| — | — | — |

Note: `plan.md` keeps its own 74 `- [ ]` step checkboxes, unticked. `tasks.md` is the tracking artifact and is complete; the plan checkboxes were not maintained. See SUGGESTION S2.

---

## 3. Delta Spec Sync State

| Capability | Sync state | Notes |
|---|---|---|
| `jq-expression-function` | ✗ Needs sync (new capability) | `openspec/specs/jq-expression-function/` does not exist yet. Archive creates it from the delta (all ADDED, 9 requirements). |
| `jq-expression-conformance` | ✗ Needs sync (new capability) | `openspec/specs/jq-expression-conformance/` does not exist yet. Archive creates it (5 ADDED requirements). |

Both are new capabilities, so there is no merge conflict with an existing main spec. Sync happens at archive time.

---

## 4. Design / Specs Coherence Spot Check

| Sample | design | specs | Gap |
|---|---|---|---|
| D3 env contract (9 names, validation, 3 fixtures) | Table of `JQ_*`, `PORT`, `DAPR_*`, `DWS_LOG_FORMAT`; `TestConfigContract` pins names | "Deploy-time configuration contract" + 4 scenarios | None. Implementation pins `config.Names()` by recording the reads of `Load` (accepted deviation, see notes). |
| D4 startup sequence / readiness | Parse, policy walk, compile, listen, 503 until secrets are read, then 200 | "compiled once at startup", "Readiness waits for secrets", "Health endpoint" | None. |
| D5 builtin policy by AST walk | Blocked set, look-alikes allowed, module directives rejected | "Blocked builtins are rejected by inspecting the parsed expression" (5 scenarios) | None. |
| D6/D7 static `$secrets`, Dapr HTTP fetch | Only literal names; dynamic access rejected; fetch with 60 s retry | "Secrets are read statically from the configured store" | Implementation is stricter than D7 (see deviations): secret name `bulk` refused, only the lowercase `value` key bound. Spec text updated to match. |
| D8 `/run` semantics | 4 MiB cap, strict fields, `"variables": null` = absent, 422 faults | "POST /run evaluates…", "Expression failures are 422 faults" | None. `null` variables was added to design and spec in `eac5959c`. |
| D9/D10 redaction and logging | Redactor on faults and logs; 12 golden log cases | "Secret values never leave…", "Structured logs follow the shared DWS log format" | None. Redaction covers raw, JSON-escaped and truncated-preview forms (beyond D9's two forms, in the safe direction). |
| D11 corpus and runner | `case.schema.json`, 6 areas, 4 verdicts, stale-divergence fails | "Language-neutral corpus format", "Seed coverage", "Runner verdicts…" | None. Corpus has 55 cases (13 data-flow carry-overs + 3 false-positive guards in `builtins`, as D11 says). |
| D12 packaging, CI, release | Dockerfile, Makefile, k8s example, workflow, release-please | "dws-jq runs the corpus in its package gate" | None. `.github/workflows/dws-jq.yml` filters `conformance/expressions/**`. |
| D13/D14 EX-3 and doc updates | Measure if a cluster exists; update CLAUDE.md, log-format, ADR 0014, roadmap | Not a spec requirement | None. EX-3 was run; results are in ADR 0014 and `dws-jq/README.md`. |

**Drift warnings (non-blocking)**:

- Design D9 names two redaction forms (raw and JSON-escaped). The shipped redactor also covers gojq's truncated value previews (`2d55365d`). This is a safe extension; design text could say so (S3).
- Design D8 and the spec say the encoded `result` follows jq output rules. The implementation wraps `gojq.Marshal` in `Encode`. This is an accepted deviation, same observable behavior.

---

## 5. Implementation Signal

- [x] Worktree has no uncommitted or unstaged files (`git status --short` is empty).
- [ ] Commits are not pushed: `git log origin/feat/jq-function..HEAD` lists 34 commits. Push belongs to the finishing step (see W4).

**Commit range**: `30bd68d4..eac5959c` (35 commits; `30bd68d4` = `git merge-base main HEAD`). 105 files changed, about 7.2k lines added. The first commit is `7c3994ef` (OpenSpec init), the last is `eac5959c` (null variables in the request contract).

---

## 6. Front-Door Routing Leak Detector (warning, non-blocking)

`ls docs/superpowers/specs/*.md` -> no such files.

- [x] No files. No leak.

| File | Captured in change? | Suggested action |
|---|---|---|
| — | — | — |

---

## 7. Deferred Manual Dogfood vs Automated Test Equivalence

`plan.md` has no `[~]` rows (grep count 0). Section intentionally empty. The one live-environment check, EX-3 (cold start on Knative + Dapr), was executed and recorded in ADR 0014 and `dws-jq/README.md`, not deferred.

| Deferred dogfood (plan section) | Equivalent automated test | Coverage | Real gap? |
|---|---|---|---|
| — | — | — | — |

---

## Completeness

- Tasks: 48/48.
- Spec coverage: 14 ADDED requirements (9 in `jq-expression-function`, 5 in `jq-expression-conformance`), all implemented; none uncovered.

## Correctness: requirement to code to test

### `jq-expression-function`

| Requirement | Code | Covering tests (scenarios) |
|---|---|---|
| Deploy-time configuration contract | `dws-jq/internal/config/config.go:59` (`Load`), `:65` (`Names`), `:77` (`load`), `:114` (`parseVariables`), `:141` (`parseTimeout`); fixtures `dws-jq/testdata/contract/env-*.json` | `TestLoad_defaults` (minimal loads with defaults); `TestLoad_rejects` "variables duplicate/leading digit/reserved…" (invalid variable list); `TestLoad_rejects` "timeout zero/negative" (non-positive limit); `TestConfigContract` + `TestLoadIsTheOnlyEnvironmentRead` (fixtures and loader agree) |
| Expression unwrapped and compiled once | `internal/expr/unwrap.go:10`, `internal/expr/program.go:57` (`Compile`); wiring `main.go:42` | `TestUnwrap`, `TestCompile` (wrapped = bare); `TestMain_startupFailure` "malformed expression" (never ready); `TestMain_startupFailure` "undeclared variable"; `TestCompile_compileErrors` |
| Blocked builtins rejected by AST walk | `internal/expr/policy.go:46` (`Check`), `:185` (`function`), `:103` (`importError`), `:110` (`funcDef`) | `TestCheck_blocked` (named; nested `{a: (. \| debug)}`; `import`/`include` directives); `TestCheck_allowed` (look-alikes `{debug: .x}`, clock `now`, `0 \| localtime`); corpus `builtins/*` |
| Secrets read statically from the store | `policy.go:202` (`staticSecretName`), `:125` (`checkBinding`); `internal/secrets/fetch.go:58` (`Fetch`), `:99`, `:140`; `main.go:138` (`readSecrets`) | `TestCheck_secrets` (static vs dynamic); `TestFetch` subtests (fetched and bound; 404 bound without leaking; retries; bulk route rejected); `TestMain_servesWithSecrets`; `TestMain_startupFailure` "dynamic secret access", "secret store unset"; `TestMain_secretFetchFailure`; `TestMain_warnsOnUnusedSecretStore`; `TestHealthz` / `TestMain_cancelDuringSecretFetch` (readiness waits) |
| POST /run evaluates the expression | `internal/server/server.go:106` (`run`), `:155` (`decode`), `:184` (`decodeRequest`), `:204` (`variables`); `internal/expr/program.go:95` (`Evaluate`), `internal/expr/json.go:19`/`:81` | `TestRun_success` ("first output", "no output", "declared variable left out is null", "null variables is absent", "large integers keep precision", object result); `TestRun_badRequest` ("undeclared variable", "secrets cannot be injected", unknown/case-variant fields); `TestRun_bodyLimit`; `TestRun_notReady`; `TestDecodeEncode`; corpus `templates/*` (combined template) |
| Expression failures are 422 faults | `server.go:126`, `:144` (`fault`); `program.go:119` (`evaluationDetail`) | `TestRun_fault`; `TestRun_timeout`; `TestEvaluate_errors`; `TestEvaluate_cancelledContext`; `TestRun_unencodableResult` |
| Secret values never leave in faults or logs | `internal/secrets/redact.go:43`/`:76`; `internal/logging/logging.go:84`/`:154` | `TestRun_redactsDetail`; `TestRun_redactsTruncatedSecret`; `TestRedactor*` (encoded forms, previews, overlap); `TestNew_redactsBeforeScrub`; `TestText_redactsEveryAttributeValue` |
| Health endpoint reports readiness | `server.go:98` (`healthz`), `:94` (`MarkReady`) | `TestHealthz` (503 starting, 200 ok); `TestMain_servesWithSecrets` |
| Structured logs follow the shared DWS format | `internal/logging/logging.go:68` (`New`), `:160` (`Handle`); `internal/logging/trace.go:34`/`:50` | `TestLogFormatContract` (12 golden cases); `TestNew_textFallback`, `TestNew_caseInsensitive` (unrecognised format falls back); `TestWithTraceparent*`, `TestRun_logsTraceOnFailure`, `TestNew_noTraceWithoutTraceparent` (trace context) |

### `jq-expression-conformance`

| Requirement | Code / data | Covering tests (scenarios) |
|---|---|---|
| Language-neutral corpus format | `conformance/expressions/case.schema.json`, `README.md`, `cases/<area>/*.yaml` (55 cases: builtins 16, loops 12, contract 10, dialect 8, templates 5, catch 4), `divergences/dws-jq.yaml` | `TestRun_schemaInvalid`; `TestRun_structuralErrors`; `TestRun_areaMismatch` (malformed case fails the run); `TestRun_duplicateTest`; `TestExpressionCorpus` |
| Seed coverage | the 13 `conformance/data-flow/cases/builtins/` names are all present in `expressions/cases/builtins` (`comm` diff empty; plus 3 false-positive guards); `dialect/*` has one case per ADR 0014 Decision 5 row | `TestExpressionCorpus` ("blocked builtin vector" via `builtins/*`; "template part with no result / several results" via `templates/part-empty`, `templates/part-many`) |
| Expectations from the documented dialect, not a runtime | `divergences/dws-jq.yaml` (empty list, `runtime: dws-jq (gojq)`) | `TestRun_knownDivergence`; `TestExpressionCorpus` (0 known divergences, so none hidden) |
| Runner verdicts and stale-divergence rule | `internal/conformance/runner.go:1-387`, `compare.go:1-97` | `TestRun_staleDivergence` (stale fails run); `TestRun_numbersByValue` (10 vs 10.0); `TestRun_faultPhase`; `TestRun_verdicts`; `TestReport_OK`, `TestReport_Totals` |
| dws-jq runs the corpus in its gate | `internal/conformance/corpus_test.go:19` (UTC, `DWS_CONFORMANCE_EXPRESSIONS` override); `.github/workflows/dws-jq.yml` paths include `conformance/expressions/**`; `dws-jq/Makefile` `test` | `TestExpressionCorpus` ("gate passes with the seeded corpus": 55/55); workflow path filter inspected for "corpus change triggers CI" (not run on GitHub yet, see W3) |

## Coherence

- Design adherence: D1 (plain `net/http`, no Dapr SDK: `go.mod` has gojq, jsonschema, yaml only), D2 (package layout matches exactly), D3 to D11 as in section 4, D12 (`Dockerfile` `golang:1.26.9` to distroless nonroot, `k8s/knative-service.yaml` with `minScale: "0"` and `/healthz` readiness probe, release-please package plus manifest `0.0.0`, `component-release.yml`), D14 (root `CLAUDE.md` rows and contract bullet, `docs/log-format.md`, ADR 0014, roadmap row 2j all changed).
- Pattern consistency with `dws-call-http`: `Makefile` targets are identical (`diff` of target names is empty); typed errors classified with `errors.As` (`server.go:126`, `runner.go:314`); no testify dependency; table-driven same-package tests; logging confined to `main.go` and the server request logger.
- Deliberate difference from `dws-call-http`: HTTP server, not an `activity.Handler` worker (design D1, rationale recorded).

## Accepted deviations (notes, not defects)

- `expr.Encode` wraps `gojq.Marshal` for jq-identical output.
- Corpus numbers compare as exact integer if both are integers, else as `float64`.
- `"variables": null` is treated as absent (design D8 and spec updated in `eac5959c`).
- Secret name `bulk` is refused (it collides with Dapr's bulk route) and only the lowercase `value` key is bound.
- The configuration contract is pinned by recording the environment reads of `Load`, so `config.Names` cannot drift from what `Load` reads.

## Issues

### CRITICAL
None.

### WARNING
- W1. Go `%q` quoting in `strptime` and invalid-regex error messages can leak a secret that contains control characters or DEL, because the redactor matches raw, JSON-escaped and preview forms but not Go's `%q` escapes. Narrow (needs an expression that feeds a secret into `strptime` or a regex, and a secret with control characters). Follow-up: add the `%q` form to `internal/secrets/redact.go`, or reject such secrets at fetch time.
- W2. EX-3 found that Dapr service invocation cannot wake a Knative site that has scaled to zero. The shipped example (`k8s/knative-service.yaml`) carries a warning and the README documents it. Handed to roadmap 2.0d / ADR 0014 O-1. The controller (2m.2) must not render `minScale: 0` for Dapr-invoked callers until that is resolved.
- W3. `.github/workflows/dws-jq.yml` and the release-please wiring have not run on GitHub yet; they were validated locally only (smoke steps replayed by hand, Linux `-race` gate run once in Docker `golang:1.26.9`). Re-check on the first PR run.
- W4. The 34 commits are not pushed (`origin/feat/jq-function..HEAD`). Push and open the PR in the finishing step.
- W5. The native `windows/386` toolchain cannot run `-race`; the race gate relies on Linux CI and the one-off Docker run. Known and documented in the design Risks section.

### SUGGESTION
- S1. 10 "requirement text very long" validate warnings. Optionally move enumerations into scenarios at archive time; not needed to pass.
- S2. `plan.md` step checkboxes (74) are unticked while `tasks.md` is 48/48. Tick or ignore before archive so the two artifacts do not disagree.
- S3. Update design D9 to mention the truncated-preview redaction form and D7 to mention the `bulk` refusal, so the design text matches the shipped (stricter) behavior.
- S4. The controller side of the `dws-jq` configuration contract (2m.2) is not yet pinned against `dws-jq/testdata/contract/env-*.json`; the root `CLAUDE.md` bullet already states this. Track it in 2m.2.

---

## Overall Decision

- [ ] ✅ PASS
- [x] ⚠️ PASS WITH WARNINGS: no CRITICAL issues. Five non-blocking warnings (W1 to W5) and four suggestions, all recorded above.
- [ ] ❌ FAIL

**Next step**: write the retrospective, record W1 (`%q` redaction gap) and W2 (scale-to-zero with Dapr callers) as follow-ups, push the branch and check the first `dws-jq` CI run on GitHub, then archive (`openspec archive`), which creates `openspec/specs/jq-expression-function` and `openspec/specs/jq-expression-conformance`.
