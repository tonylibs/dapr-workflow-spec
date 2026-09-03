# Retrospective: api-gateway

> Written: 2026-09-03 (after verification)
> Commit range: `570fe5ce..0403bbbe`
> Worktree: `C:\Users\Admin\orca\workspaces\dapr-workflow-spec\API-gateway`

---

## 0. Evidence

- **Commit range**: `570fe5ce..0403bbbe` (10 commits)
- **Diff size**: +7,221 / -1,530 lines across 83 files
- **Tasks done**: 32/32 (`tasks.md`)
- **Active hours**: approximately 24 hours (first change commit `24b87045` at 2026-09-02 01:46 +07:00; latest verification commit `0403bbbe` at 2026-09-03 01:58 +07:00)
- **Subagent dispatches**: 3 scoped agents (`nestjs-developer`, `frontend-developer`, and `platform-deployment-developer`; `verify.md` §2 and Notes)
- **New external dependencies**: Apache APISIX Helm chart 2.16.0; `@testing-library/react` 16.3.3 and `jsdom` 30.0.1 as console development dependencies
- **Bugs encountered after initial implementation**: four fixed defects: incomplete npm lockfile, generated route-tree drift, invalid APISIX `externalTrafficPolicy` for `ClusterIP`, and unreachable sidecar port 3500; one blocking pub/sub discovery conflict remains open (`verify.md` §5.3-5.4)
- **OpenSpec validate state at archive**: pass (`openspec validate api-gateway --type change --strict --no-interactive`)
- **Test coverage signal**: dws-admin 77 tests in 12 suites; dws-console 79 tests in 7 files; chart lint, render matrices, and a 17-assertion real-cluster Gateway/SSE script passed (`verify.md` §3 and §5)

Commit chain (chronological):

```text
570fe5ce update auth roadmap
24b87045 init specs
fa1ed095 feat: consolidate dws-admin on a single Dapr app port
3e6c4e36 feat: authenticate every dws-console admin transport
62ea6e96 feat: route DWS through a shared Gateway API front door
57db0b10 docs: record api-gateway change artifacts and verification
2677b15f docs: clear pre-existing openspec spec-lint debt
4182839b fix: repair API gateway CI checks
402641e8 fix: stabilize API gateway CI assertions
d07c4b6b test: rehearse the console.ingress migration on a live cluster
0403bbbe test: close the deferred live SSE-over-Gateway/APISIX/Dapr verification
```

---

## 1. Wins

- [evidence: `fa1ed095`, `3e6c4e36`, and `62ea6e96`] The change delivered the coordinated one-listener Nest, authenticated console transport, and shared Gateway topology as separately reviewable commits.
- [evidence: `verify.md` §3.1-3.3] Focused application and chart gates covered the changed contracts before the live deployment work.
- [evidence: `verify.md` §5.1-5.3; `0403bbbe`] The real-cluster probe proved that SSE frames arrive while the connection remains open across Gateway, APISIX, Dapr invocation, and Nest; it also exposed two runtime defects that static renders could not detect.

## 2. Misses

- 🔴 [blocking | `verify.md` §5.4] The Dapr bearer middleware rejects Dapr's unauthenticated `GET /dapr/subscribe` discovery call, so authenticated gateway deployments do not register the lifecycle-event subscription and cannot receive normal pub/sub events. This contradicts the `helm-admin-auth-middleware` delta requirement and remains a tracked follow-up.
- 🟡 [painful | `4182839b`, `402641e8`] CI initially failed because `package-lock.json` omitted declared test dependencies, the Helm job referenced a deleted test, the committed route tree contained Vite-plugin-only registration output, and the integration check depended on a removed Dapr log phrase.
- 🟡 [painful | `verify.md` §5.3] Static Helm checks did not detect APISIX's invalid `externalTrafficPolicy` value or Dapr's loopback-only sidecar listener; both made a real bundled-gateway deployment unusable until the live probe found them.

## 3. Plan deviations

| Plan task | What changed | Why |
|-----------|--------------|-----|
| Task 9, step 6 | Deferred live SSE verification became a real-cluster 17-assertion probe. | The cross-service streaming path was the key remaining uncertainty and render/unit gates could not prove it. |
| Task 9, step 7 | Documentation/evidence and later CI repairs were committed in several commits rather than the single planned final commit. | Independent CI and real-cluster findings arrived after the main implementation commits. |
| Task 5, step 3 | `apisix.service.externalTrafficPolicy` was explicitly cleared. | APISIX's upstream default is invalid when this chart configures a `ClusterIP` Service (`verify.md` §5.3). |
| Task 8, step 2 | The admin annotation added `dapr.io/sidecar-listen-addresses`. | A Service targeting daprd port 3500 cannot reach Dapr's loopback-only default listener (`verify.md` §5.3). |

## 4. Skill / workflow compliance

| Skill | Used |
|-------|------|
| superpowers:brainstorming | ✓ |
| superpowers:writing-plans | ✓ |
| superpowers:using-git-worktrees | ✗ |
| superpowers:subagent-driven-development | ✓ |
| (transitive) superpowers:test-driven-development | ✓ |
| (transitive) superpowers:requesting-code-review | ✗ |
| superpowers:finishing-a-development-branch | ✗ |

### Deliberately Skipped Skills

- **`superpowers:using-git-worktrees`**
  - **What was skipped**: creating a dedicated implementation worktree.
  - **Why this cycle**: implementation was performed directly on the already-isolated PR branch `feat/api-gateway`; the current worktree is still that branch and is ahead of `origin/feat/api-gateway` by two evidence commits.
  - **How to prevent recurrence**: `scope-judgment rule` — when work is explicitly constrained to an existing PR branch, record the branch isolation as the worktree-equivalent before applying changes.
- **`superpowers:requesting-code-review`**
  - **What was skipped**: a final dedicated review report over the completed diff.
  - **Why this cycle**: `verify.md` records three implementation/testing subagents and runtime verification, but no final review artifact or reviewer result; the late CI and live-cluster defects demonstrate the missing independent pass.
  - **How to prevent recurrence**: `schema graph fix` — require a final code-review artifact before `verify` can report a pass or pass-with-warnings decision.
- **`superpowers:finishing-a-development-branch`**
  - **What was skipped**: branch finishing/merge completion.
  - **Why this cycle**: `git status --short --branch` at retrospective time reports `feat/api-gateway...origin/feat/api-gateway [ahead 2]`; the branch is not at a completed merge/cleanup state.
  - **How to prevent recurrence**: `scope-judgment rule` — run branch-finishing only after the archive and all PR checks are green, then record the merged commit rather than treating a local branch as complete.

## 5. Surprises

- Dapr's HTTP bearer middleware gates internal sidecar-to-app subscription discovery as well as browser-originated service invocation; it has no path allowlist (`verify.md` §5.4).
- Dapr's default app-facing HTTP listener is loopback-only, so a Kubernetes Service targeting daprd needs an explicit sidecar listen-address annotation (`verify.md` §5.3).
- The `tsr generate` CLI and the TanStack Start Vite plugin produce different route-tree output; only the CLI output is the committed CI contract (`402641e8`).

## 6. Promote candidates → long-term learning

- [ ] 🔴 **A bearer gate on a Dapr app pipeline must be checked against Dapr's own control callbacks, not only public routes.**
  → **Promote to** CLAUDE.md (cross-cutting Dapr integration guidance)
  > **Why**: `verify.md` §5.4 proved the shared bearer middleware blocks `/dapr/subscribe`, silently preventing the admin event projection from receiving pub/sub messages.
  > **How to apply**: whenever adding `appHttpPipeline` middleware, enumerate and exercise every Dapr-to-app endpoint, including subscription discovery and delivery, before declaring the app port secured.

- [ ] 🟡 **A Kubernetes render test cannot prove a Service can reach a sidecar listener.**
  → **Promote to** verification skill
  > **Why**: valid Helm renders still contained an APISIX Service validation error and a daprd loopback reachability failure; both appeared only in the real-cluster probe (`verify.md` §5.3).
  > **How to apply**: for a change that introduces or retargets a Service backend, add a live request through the actual Service/proxy path to its acceptance criteria.

- [ ] 🟡 **Regenerate committed generated files with the exact CI generator, not a build plugin that mutates them differently.**
  → **Promote to** CLAUDE.md (`dws-console` CI guidance)
  > **Why**: the CI route-tree guard failed because the committed file had Vite plugin registration output which `tsr generate` removes (`402641e8`).
  > **How to apply**: before committing generated console routes, run `npm run generate-routes` and require a clean `git diff -- src/routeTree.gen.ts`.
