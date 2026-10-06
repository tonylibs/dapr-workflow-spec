# Retrospective: workflow-auth

> Written: 2026-10-05 (after verify passed with warnings)
> Commit range: `de42dd98..91f9b269` (implementation), plus the 2026-10-05 verification/archive edits
> Worktree: `workflow-auth-verification` (branch `chore/workflow-auth-verification`)

## 0. Evidence

- **Commit range**: `de42dd98..91f9b269` (18 commits)
- **Diff size**: +4351 / -90 lines across 54 files (includes OpenSpec artifacts)
- **Tasks done**: 21/21 (6.2 and 6.3 closed 2026-10-05, about six weeks after implementation finished)
- **Active span**: implementation 2026-08-21 → 2026-08-23; live verification 2026-10-05
- **Subagent dispatches**: unknown for the implementation cycle (not recorded in artifacts); 0 in the verification cycle
- **New external dependencies**: none at runtime; Dapr chart dependency moved to 1.18.1 (task 6.1)
- **Bugs post-merge**: none recorded against this change
- **OpenSpec validate at archive**: `workflow-auth` valid; 4 unrelated invalid specs (`dws-step-failure-contract`, `helm-admin-gateway`, `single-node-definition-contract`, `workflow-timeouts`)
- **Test coverage signal**: controller 224 tests (CI green; 6 Windows-only `V2GoldenTest` failures locally), orchestrator 162/162, call-openapi 95/95, call-http 3 packages ok; live OAuth probe PASS on Dapr 1.18.2

```
1f5ab782 feat: add typed step environment values
1d610094 test: cover typed environment safety
c73a9113 feat: compile workflow auth policies
38fd6066 fix: canonicalize oauth scopes
4c5412dd feat: synthesize workflow secret and oauth resources
a1347734 fix: bind oauth resources to dapr 1.18.1
1b1c60c7 fix: validate oauth scope entries
ad8665ba feat: expose workflow secrets to jq
04938db7 feat: authenticate http workflow calls
f06acb84 fix: isolate oauth authorization header
576eaf0d feat: authenticate openapi workflow calls
740a49ab fix: prefer generated openapi authorization
65403d71 test: verify oauth endpoint isolation
beecaa90 fix: isolate oauth integration release trigger
89c78a7a fix: resolve openapi oauth server target
b2b09fc0 fix: resolve relative openapi servers for oauth
16e6732b ci: gate chart release on oauth probe
91f9b269 docs: add workflow auth change artifacts
```

## 1. Wins

- [evidence: `verify.md` §5] The live probe passed on its first run: `/intended` got `Bearer issued-oauth-token` and `/unrelated` got no header. The `pathFilter` design held without any change.
- [evidence: `verify.md` §4 drift table] The hand-written probe resources still match the controller's output six weeks and several controller refactors later (`166ae594`, `28023da4`). Names, scopes, `secretKeyRef`s and the `pathFilter` regex are all the same.
- [evidence: `a1347734`, `StackSynthesizer.java` comment on `scopes`] Pinning to the released 1.18.1 middleware behavior (comma-delimited scopes) instead of the docs (space-delimited) avoided a silent runtime mismatch.
- [evidence: `StackSynthesizerTest.oauthMiddlewareUsesSecretMetadataAndNarrowPathFilter`] Synthesizer tests assert that no plaintext credential appears in the serialized Component.

## 2. Misses

- 🔴 [blocking | evidence: 2026-08-23 `verify.md` FAIL] The change sat unarchived for about six weeks because the only live proof needed a disposable kind cluster that wasn't available. The roadmap claimed this also gated Phase 5, which was false (Phase 5 shipped meanwhile).
- 🟡 [painful | evidence: `scripts/verify-dapr-oauth-path-filter.sh`] The probe always installs its own Dapr Helm release. That is unsafe on a cluster that already runs Dapr: cluster-scoped name collisions, and an exit-trap `helm uninstall` that could delete shared objects. It needed a hand-edited copy to run on the local cluster.
- 🟡 [painful | evidence: `verify.md` §6 prerequisites] The controller gate can't run on Windows as written: `cdk8s-cli import` reads `C:\…` paths as URL schemes.
- 🟡 [painful | evidence: `SingleNodeDefinition.java:72`] `V2GoldenTest` fails on Windows because Jackson pretty-prints with the OS line separator. This is outside this change but turns the controller gate red locally.
- 📌 [nit | evidence: probe comment vs manifest] The probe's comment says `appHttpPipeline`; the manifest and controller both use `httpPipeline`.

## 3. Plan deviations

| Plan task | What changed | Why |
|---|---|---|
| 6.2 | Probe ran on the existing Dapr 1.18.2 control plane, Helm install lines removed | No disposable cluster; the shared cluster already had Dapr |
| 6.3 | Integration result recorded at 1.18.2, not the 1.18.1 default | The project owner accepted the patch-level difference |
| 6.3 | Controller gate run as `mvn -Dexec.skip=true verify` with copied cdk8s imports; call-http gate run in a Linux container | Windows toolchain gaps (cdk8s path bug; no make/cgo) |

## 4. Skill / workflow compliance

| Skill | Used |
|---|---|
| superpowers:brainstorming | ✓ (`brainstorm.md` present) |
| superpowers:writing-plans | ✓ (`plan.md` present) |
| superpowers:using-git-worktrees | ✓ (verification cycle ran in a dedicated worktree) |
| superpowers:subagent-driven-development | ✗ (not evidenced for implementation; not applicable to verification) |
| (transitive) superpowers:test-driven-development | ✓ (feat/test commit pairs, e.g. `1f5ab782`/`1d610094`) |
| (transitive) superpowers:requesting-code-review | ✓ (2026-08-23 verify.md: "Independent final review") |
| superpowers:finishing-a-development-branch | pending (no commit/push requested in this cycle) |

### Deliberately Skipped Skills

- **`superpowers:subagent-driven-development`**
  - **What was skipped**: per-task subagent dispatch with review gates.
  - **Why this cycle**: the implementation cycle ran under Codex, and the artifacts don't record how tasks were dispatched. The verification cycle wrote no code (only probe execution and gates), so there was nothing to dispatch.
  - **How to prevent recurrence**: record the dispatch count in `verify.md` §5 at implementation time so the retro can cite it. One-off schema boundary case for verification-only cycles, because they produce no code tasks.

## 5. Surprises

- The "disposable kind cluster" assumption was wrong for this environment. The available cluster was long-lived, shared, and already ran Dapr 1.18.2. The probe's self-install design assumed an empty cluster.
- In slim Linux images without `unzip`, the Maven wrapper falls back to `.tar.gz` and then fails the `.zip` SHA-256 pin. The error looks like a supply-chain warning rather than a missing tool.
- Four of the five "unrelated invalid" OpenSpec items from August had been fixed by October, and four different ones now fail validation.

## 6. Promote candidates → long-term learning

- [ ] 🟡 Live probes must be able to reuse an existing Dapr control plane.
  → **Promote to** one-off (probe change: `DAPR_INSTALL=false` switch)
  > **Why**: the self-installing probe is unsafe on shared clusters and blocked closure for six weeks.
  > **How to apply**: when writing any live-cluster probe, make the control-plane install optional and the cleanup scoped to what the probe itself created.
- [ ] 🟡 Controller build is not Windows-portable (cdk8s absolute path; Jackson OS line separator in `specText`).
  → **Promote to** CLAUDE.md (gate table note) or a fix change
  > **Why**: the documented gate fails on Windows for reasons unrelated to the change under test.
  > **How to apply**: on Windows, expect the cdk8s import failure and the `V2GoldenTest` failures; compare against Linux CI for the same tree before treating them as regressions.
- [ ] 📌 Don't let a roadmap claim one phase "gates" another without checking the dependency.
  → **Promote to** one-off
  > **Why**: the roadmap's "gates Phase 5" claim was false; Phase 5 shipped independently.
  > **How to apply**: when writing a "blocks/gates" line in the roadmap, cite the specific task or artifact that creates the dependency.
