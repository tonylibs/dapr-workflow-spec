# Retrospective: console-api-wiring

> Written: 2026-08-12 (after verify passed)
> Commit range: `a28719c..497d7c8`
> Worktree: main checkout on `claude/dws-console-api-invocation-32nzog` (no separate worktree — see §4)

---

## 0. Evidence

> **Update 2026-08-12 (same day)**: §4 recorded `requesting-code-review` as an unfilled gap. That
> review was then actually run (`/code-review high`) before archive; it found 7 defects including a
> 🔴 that both the test suite and the live dogfood had missed. §2, §4 and §5 below carry the
> corrected analysis inline, marked "(review round)".

- **Commit range**: `a28719c..HEAD` (4 commits)
- **Diff size**: +2176 / -464 across 23 files (implementation commit `497d7c8` alone: +1685 / -491 across 20 files, of which `pnpm-lock.yaml` is +266)
- **Tasks done**: 18/18 (`grep -cE '^\s*- \[x\]' tasks.md` → 18; `- [ ]` → 0)
- **Active hours**: ~1.5h single session (planning `/opsx:propose` through archive)
- **Subagent dispatches**: 0 (inline executor — see §4)
- **New external dependencies**: `vitest@4.1.10` (MIT, devDependency only)
- **Bugs encountered post-merge**: none (not yet merged). Pre-merge: 2 defects found by live dogfood (fixed in `497d7c8`), then 7 more found by code review (fixed in the review-round commit) — see §2/§5
- **OpenSpec validate state at archive**: pass (`validate --all` → 16 items, 0 invalid)
- **Test coverage signal**: vitest 30 tests, 1 file (`admin-adapters.test.ts`), covering the whole adapter layer; client/hooks/routes covered by typecheck + build + browser dogfood

Commit chain (時序):

```
a28719c chore(opsx): scaffold console-api-wiring change (superpowers-bridge)
04b45e7 docs(opsx): propose console-api-wiring — wire dws-console to live dws-admin read API
497d7c8 feat(dws-console): wire read routes to the live dws-admin API
```

---

## 1. Wins

- [evidence: brainstorm.md §"Key finding"] The field-level DTO-vs-view-model diff done *before* writing code found that `mock-data.ts`'s own comment ("the return shapes intentionally track the documented endpoints") was wrong. `TaskEventDto` is one record per lifecycle phase; `TaskEvent` is one row per task. Catching that during planning turned the riskiest part of the change into a designed adapter (D2) instead of a mid-implementation surprise.
- [evidence: `admin-adapters.test.ts`, 28 tests] Writing the adapter tests first pinned the contract before any implementation existed, and immediately caught one wrong expectation of my own (`2d ago` vs `1d ago` for a 1d21h span) — the code was right and the test was wrong, which is only distinguishable because the test was written against explicit arithmetic.
- [evidence: `497d7c8` `admin-adapters.ts` `toInstanceDetail`] `retries` is derived honestly from repeated `started` events rather than invented; the live run confirmed it (seeded a task with two `started` events → header showed `RETRIES 1`).
- [evidence: verify.md §7] The live dogfood ran against a *real* dws-admin (Postgres 16 + drizzle migrations + seeded read model), not a stub, so the cursor values, 404s and 400s exercised were the service's own.
- [evidence: `mock-data.ts` diff, -296 lines] Keeping the view-model types while deleting the fixtures meant the four routes changed only where behavior changed; `status.tsx`, `data-table.tsx`, `skeleton.tsx`, `states.tsx` needed no edits at all.

## 2. Misses

- 🔴 (review round) [blocking | evidence: `dws-admin/src/events/controller-events.handler.ts:35-50`] **The status vocabulary was wrong everywhere.** The view-model unions were the Phase 1–2 mockups' (`DEPLOYED`, `ACTIVE`, `RUNNING`); dws-admin stores `created`/`updated`, `applied`/`failed`/`drained`/`collected`, `started`/`completed`/`failed`. Live, every status pill would have rendered as an unrecognized value in the neutral hue and every filter chip would have returned zero rows. Neither planning nor implementation checked the *producer's* literals — both read the consumer's fixtures.
- 🔴 (review round) [blocking | evidence: `admin-client.ts` `DEFAULT_BASE_URL`] The unset-env default was `""` (same-origin), but dws-admin's paths (`/workflows`, `/instances`) collide exactly with the console's own routes — the fetch would have returned the console's HTML with a 200 and thrown inside `.json()`. The design's "empty = behind one ingress" note was unroutable for the same reason.
- 🟡 (review round) [painful | evidence: `admin-hooks.ts` `fetchAllPages`] Detail sub-collections fetched one page and discarded `nextCursor`. Design D5 called this an accepted trade-off ("counts are small in practice") — but a truncated task list silently *understates the header's failure and retry counts*, which is wrong data, not less data. Framing it as a pagination trade-off hid that it was a correctness bug.

- 🔴 [blocking | evidence: browser console `Access to fetch at 'http://127.0.0.1:3001/workflows' … blocked by CORS policy`] Neither the brainstorm nor the design considered that a browser cannot call `dws-admin` cross-origin — it sends no `Access-Control-Allow-Origin`. Planning treated "base URL is configurable" as the whole problem. Would have shipped a console that works in tests and fails in a browser, had the dogfood not been run.
- 🟡 [painful | evidence: verify.md §7, 404 view took >12s before fix] TanStack Query's default 3× retry was never considered in the design, so `404` → not-found rendered only after backing-off retries. The spec required the not-found view; nothing required it to appear *promptly*, so specs alone would not have caught it.
- 🟡 [painful | evidence: first browser run asserted `table.tbl tbody tr`] My first dogfood assertion matched skeleton rows, which are also `<table class="tbl"><tbody><tr>`. It reported "rows present" while the page was actually stuck pending. Nearly produced a false PASS.
- 📌 [nit | evidence: `src/lib/mock-data.ts`] The file is now named `mock-data.ts` but contains no mock data — only view-model types. Kept because the original requirement named the path explicitly; a rename to `view-models.ts` is a follow-up.
- 📌 [evidence: `src/routes/workflows/index.tsx`] The "filter by name…" search input is still decorative — `GET /workflows` has no name filter. Left as-is (out of scope), but it now sits next to genuinely live controls, which is more misleading than it was in a mock.

## 3. Plan deviations

| Plan task | What changed | Why |
|-----------|--------------|-----|
| (new) 2.0 | Added `vitest` + `test` script; adapter tests written first | `dws-console` had no test runner; user chose real TDD over the plan's "typecheck/lint/build only" gate. `proposal.md` said "no dependencies added" — updated to match reality |
| (new) 6.0 | Added dev-server proxy + `retryUnlessClientError` | Both found by the live dogfood (§2); neither was in any artifact |
| 5.3 | Instance list dropped `columnFilteringFeature` and multi-select status chips → single status, server-side | `GET /instances` takes one `status`; client-side filtering could only ever filter the pages already fetched |
| 6.2 | "Run against a live dws-admin" — no dws-admin was running and Docker was unavailable | Stood one up from source instead: local Postgres 16 + `pnpm db:migrate` + seed. A minimal Dapr sidecar stub was needed only because `DaprServer` refuses to boot without one — that path is pubsub ingest, unrelated to the read API |
| Apply phase | No worktree, no subagents | User decision — see §4 |

## 4. Skill / workflow compliance

| Skill                                            | Used |
|--------------------------------------------------|------|
| superpowers:brainstorming                        | ✗    |
| superpowers:writing-plans                        | ✗    |
| superpowers:using-git-worktrees                  | ✗    |
| superpowers:subagent-driven-development          | ✗    |
| (transitive) superpowers:test-driven-development | ✓    |
| (transitive) superpowers:requesting-code-review  | ✓ (review round — run late, after this table was first written) |
| superpowers:finishing-a-development-branch       | ✓    |

### Deliberately Skipped Skills

- **`superpowers:brainstorming` / `superpowers:writing-plans`**
  - **What was skipped**: the skill invocations; their *artifacts* (`brainstorm.md` decision log with Q1–Q8 + trade-offs, `plan.md` micro-steps with commit points) were written directly to the schema's redirected output paths.
  - **Why this cycle**: the user invoked `/opsx:propose`, whose own instruction is "create the change and generate all artifacts in one step" and which loops artifacts via `openspec instructions <id>`. That command's flow never reaches the per-artifact `Use the Skill tool to invoke …` line that `/opsx:new` → `/opsx:continue` would surface. Concretely: the `propose` skill body drove the loop, and the brainstorm instruction's PRECHECK was read from `openspec instructions brainstorm` output rather than executed as a skill.
  - **How to prevent recurrence**: `schema graph fix` — `superpowers-bridge`'s `propose`/`ff` path needs the same PRECHECK + "invoke the skill" step the per-artifact path has. Without it, any adopter using `/opsx:propose` (the documented fast path) silently bypasses two of the schema's five required skills. The bridge README's Entry-routing table already sends converged brainstorms to `/opsx:propose`, so this is the *common* path, not an edge case.
- **`superpowers:using-git-worktrees`**
  - **What was skipped**: the whole skill.
  - **Why this cycle**: the session runs in an ephemeral single-purpose container already checked out on the mandated branch `claude/dws-console-api-invocation-32nzog` (`git worktree list` → one entry), and session rules pin all pushes to that branch. Git cannot check out the same branch in a second worktree, so the skill's isolation guarantee was already satisfied by the container boundary and could not be re-established without a side branch that the push rules forbid.
  - **How to prevent recurrence**: `one-off — schema boundary case`. It is a boundary because the schema assumes a developer workstation with a shared long-lived checkout, where worktrees buy isolation; in a per-session disposable container with an enforced branch, the isolation already exists and the skill's mechanism conflicts with the harness's branch policy. Worth a schema note ("skip when the checkout is already disposable and branch-pinned") rather than a fix.
- **`superpowers:subagent-driven-development` / `superpowers:requesting-code-review`**
  - **What was skipped**: the subagent executor and the per-task + final code-review dispatches.
  - **Why this cycle**: the session's operating rules state "Do not call the AgentTool unless the user requested it", which directly contradicts the schema's mandate. This was surfaced to the user before any edit and they chose "Work inline on current branch" over "Worktree + subagents per schema". TDD, which subagent-driven-development would have activated transitively, was preserved explicitly (`admin-adapters.test.ts` RED→GREEN); code review was not.
  - **How to prevent recurrence**: `scope-judgment rule` — when the executor is declined, the schema's *transitive* skills must be re-attached explicitly rather than lost with it. TDD was re-attached at the start; `requesting-code-review` was not, and writing this very subsection is what surfaced it. It was then run before archive and found 7 defects, 2 of them blocking (§2) — so the gap was not theoretical. The rule: declining the executor obliges the cycle to name a concrete replacement for each transitive skill *before implementation starts* (inline TDD ✓, standalone `/code-review` ✓ but late).

## 5. Surprises

- (review round) **My dogfood validated my own assumption.** The live run in §7 of verify.md passed every route because I seeded the database with the uppercase statuses the code expected. Running against a "real" service proves nothing if the fixtures come from the same wrong belief as the code — the *producer* has to be the source of the data or of the expected values. This is the sharpest lesson of the cycle: it defeated both the unit tests and the integration run simultaneously.
- (review round) **`tsc` found two dead comparisons the moment the types were right.** `d.status === "RUNNING"` and `dep.status === "DRAINED"` had been silently always-false; fixing the unions turned them into TS2367 errors immediately. Wrong types don't just fail to catch bugs — they actively suppress the checks that would have.

- **`mock-data.ts`'s own comment was wrong.** It claimed the shapes "intentionally track the documented endpoints"; they track the *mockups*. `TaskEvent` carries `attempts`, `attemptHistory`, `retryPolicy`, `caughtBy`, `caughtError` — none of which any endpoint returns. An in-repo comment asserting API alignment was the least reliable input to this change.
- **The dogfood found both real defects; the test suite found neither.** 28 green adapter tests, a clean typecheck and a successful build all passed while the app was unusable in a browser (CORS) and appeared hung on 404 (retries). Both defects live in the layer the tests deliberately excluded — client + hooks + browser.
- **`dws-admin` maps all six read routes and *then* exits** if no Dapr sidecar answers (`Error: DAPR_SIDECAR_COULD_NOT_BE_STARTED`). The read API and the pubsub ingest share a process lifecycle, so a read-only consumer cannot run the service without stubbing an ingest dependency it never uses.
- **Vite silently kept a stale env.** Editing `vite.config.ts` made the running dev server restart itself with the *new config* but its *original* `VITE_DWS_ADMIN_URL`, producing a run that looked correctly configured and still called the old cross-origin URL. Cost one confusing verification round.

## 6. Promote candidates → long-term learning

- [ ] 🔴 **Never let the producer's vocabulary be inferred from the consumer's fixtures — read the writer's source.**
  → **Promote to** memory
  > **Why**: this cycle's worst defect (every status pill neutral, every filter returning zero rows) survived planning, 28 unit tests and a live browser run against a real service, because all three encoded the same assumed vocabulary. It was found only by a code review that opened the *producing* handler.
  > **How to apply**: when wiring a consumer to a producer's enum/status/vocabulary, open the code that WRITES the values (the handler, the migration, the publisher) and copy the literals from there; assert them in a test that names the producing file, so drift fails loudly.

- [ ] 🔴 **A feature is not verified until it has been exercised through its real transport.**
  → **Promote to** memory
  > **Why**: this cycle's two defects (CORS, 4xx retry) were both invisible to unit tests, typecheck and build, and both would have reached a user. The green suite actively created false confidence.
  > **How to apply**: when a change adds or rewires a network boundary (new client, new base URL, first call to a service), treat "run it against the real dependency in a real browser/client" as a required verification step, not an optional dogfood — and assert on content that only appears on success, never on a container that also exists while loading.

- [ ] 🔴 **`/opsx:propose` bypasses `superpowers-bridge`'s brainstorming and writing-plans skills.**
  → **Promote to** schema
  > **Why**: the propose/ff fast path generates artifacts by looping `openspec instructions`, which surfaces each artifact's "invoke the skill" line as *text to read* rather than executing it. Two of the schema's five required skills are skipped on what its own README calls the common entry path.
  > **How to apply**: when maintaining `superpowers-bridge`, add the brainstorm/plan PRECHECK + skill invocation to the propose/ff flow, or document that fast-path users must run those two skills manually.

- [ ] 🔴 **Run code review before declaring verification complete, not after.**
  → **Promote to** CLAUDE.md
  > **Why**: verify.md was marked PASS, and the retrospective was being written, while two blocking defects were still in the diff. The code review that found them ran only because writing §4 exposed the skipped skill.
  > **How to apply**: treat "a review pass has run over the final diff" as a precondition of any PASS verdict — sequence it before writing verify.md, never after.

- [ ] 🟡 **Declining a schema's executor must not silently drop its transitive skills.**
  → **Promote to** CLAUDE.md
  > **Why**: `subagent-driven-development` transitively carries TDD *and* code review. When it was declined this cycle, TDD survived only because it was raised explicitly; code review was lost silently and the diff shipped unreviewed.
  > **How to apply**: when a user declines a workflow's executor, enumerate that executor's transitive skills and get an explicit replacement (or explicit waiver) for each, before starting implementation.

- [ ] 🟡 **Don't trust in-repo comments that assert cross-component contract alignment.**
  → **Promote to** memory
  > **Why**: `mock-data.ts` stated its shapes tracked the documented endpoints; a field-level diff against the actual DTOs showed five fields with no API source and a fundamentally different granularity for task events.
  > **How to apply**: before wiring a consumer to a producer, diff the two type declarations field-by-field from source, and treat any prose claim of alignment as unverified.

- [ ] 📌 **Restart dev servers from a clean process when changing env-dependent config.**
  → **Promote to** memory
  > **Why**: Vite auto-restarted on a `vite.config.ts` edit but retained the launching process's env, yielding a run that looked reconfigured but wasn't.
  > **How to apply**: after changing a config file whose behavior depends on env vars, kill the server process and start it fresh; verify the new value is in effect before drawing conclusions from a test run.

- [ ] 📌 **Rename `dws-console/src/lib/mock-data.ts` to `view-models.ts`.**
  → **Promote to** one-off
  > **Why**: the file no longer holds mock data; the name now misdescribes it for every future reader.
  > **How to apply**: follow-up PR — pure rename plus import updates across six files, no behavior change.
