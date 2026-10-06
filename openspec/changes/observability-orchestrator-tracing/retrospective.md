# Retrospective: observability-orchestrator-tracing

> Written: 2026-10-06 (after verify passed with warnings)
> Commit range: `887352d..b3ef7f9` (plus the archive commit)
> Worktree: `/home/projects/dapr-workflow-spec-dws-orchestrator-observability` on `feat/observability-orchestrator-tracing`

---

## 0. Evidence

- **Commit range**: `887352d..b3ef7f9` (6 commits before verify and archive)
- **Diff size**: +2256 / −12 lines across 27 files
- **Tasks done**: 18/19 (`grep -cE '^\s*- \[x\]' tasks.md`). Task 7.2, the live run, is open.
- **Active hours**: not tracked
- **Subagent dispatches**: 5 — quarkus-developer ×2 (implementation, review nits), platform-deployment-developer ×1 (chart), explorer ×1 (recon), code-reviewer ×1
- **New external dependencies**: none
- **Bugs encountered post-merge**: n/a (not merged)
- **OpenSpec validate state at archive**: this change valid. 2 unrelated failures: `helm-admin-gateway` (spec) and `workflow-error-format` (change), both pre-existing.
- **Test coverage signal**: controller `./mvnw verify` → 277 tests, 0 failures. Chart: 4 render/schema scripts pass on Helm 3.19.0.

```
72ca5cf docs(openspec): propose observability-orchestrator-tracing (phase 2a)
be85897 docs(observability): record phase 2a identity decision and evidence status
aa45b6d feat(chart): render tracing-only dws-tracing Configuration for compiled workflows
8c7f59c feat(controller): instrument the compiled orchestrator when observability is on
01ae8b2 test(controller): tighten orchestrator tracing assertions and warn on store failure
b3ef7f9 docs(openspec): tick completed observability-orchestrator-tracing tasks
```

---

## 1. Wins

- [evidence: brainstorm.md, ADR 0005 addendum] Recon found, before any code, that the OTel Operator sets `OTEL_SERVICE_NAME` and that variable outranks a `service.name` resource attribute. A plan built on `OTEL_RESOURCE_ATTRIBUTES=service.name=…` would have named every orchestrator after its Deployment, not its workflow.
- [evidence: `aa45b6d`, render test section 10] The Phase 1 default-render baseline stayed byte-identical. The chart dev's first Role rule broke it. Gating the rule on observability fixed it, and the spec was updated to match.
- [evidence: `ObservabilityFlagsTest`, `StackApplierTest.flagChangeAppliesOnNextDeployOnly`] The off path is covered for flag absent, false, store throw, empty and timeout. The apply-level test proves a flag flip changes the next version only.
- [evidence: code-reviewer report] An independent review found 0 blocking issues. Its test-strength nits (a tautological equality test, a vacuous assertion, an unpinned 1 s bound, an unasserted WARN) were fixed in `01ae8b2`.
- [evidence: `docs/roadmaps/observability-phase2a-evidence.md`] The evidence file says "NOT EXECUTED" in plain words and carries no invented trace IDs.

## 2. Misses

- 🔴 [blocking for closing Phase 2a | evidence: tasks 7.2, verify §7] No live cluster run. There is no connected-trace evidence, no trace ID and no replay finding, so the replay ADR was not written. The agent/Operator/Temurin 25/Spring Boot 4 compatibility claim rests on source and release notes, not observation.
- 🟡 [painful | evidence: `8c7f59c`] The quarkus-developer wrote Task 1 code before its test, so the red run was never seen. In `01ae8b2` the log-level change for the secret-sourced env case also preceded its test.
- 🟡 [painful | evidence: worktree rebuild] The git worktree metadata pointed at a missing gitdir and had to be rebuilt from a fresh clone. The sandbox also wipes `/tmp` and tool caches between calls, so each sub-agent re-installed JDK/Helm tooling.
- 📌 [nit | evidence: reviewer nit 8] The `""` cell of the four-way render matrix compares two identical helm error strings, so it passes vacuously. The other three cells are meaningful.
- 📌 [nit | evidence: reviewer nit 1] `StackApplierFailurePublishTest` passes a Mockito mock whose `resolve` returns null. It is safe only because `client.configMaps()` throws first.

## 3. Plan deviations

| Plan task | What changed | Why |
|-----------|--------------|-----|
| Handoff: "flags from the existing store" | The Phase 1 store keys did not exist, so `observability.enabled` and `observability.instrumentation` were defined here | Nothing to reuse; the keys are documented in `config-component.yaml` |
| Handoff: `service.name` as identity | `OTEL_SERVICE_NAME` carries the app ID, and `OTEL_RESOURCE_ATTRIBUTES` carries workflow name and version | Operator override (ADR 0005 addendum) |
| 4.2 | The chart Role rule is gated with `dws-tracing` | Keeps the default render byte-identical to the Phase 1 baseline |
| 2.1 | An existing `dapr.io/config` skips all stamping, including env | `dapr.io/config` is single-valued; half-instrumenting would leave an agent with a sidecar that has no tracing |
| 3.1 / 3.4 | Tests rewritten after review | Replaced tautologies with hand-built expectations and real apply-path comparisons |
| 7.2 | Not executed | No cluster, Docker or privileges in the authoring environment |

## 4. Skill / workflow compliance

| Skill | Used |
|-------|------|
| superpowers:brainstorming | ✓ (`brainstorm.md`, auto mode, alternatives recorded) |
| superpowers:writing-plans | ✓ (`plan.md`) |
| superpowers:using-git-worktrees | ✗ |
| superpowers:subagent-driven-development | ✓ (partial: one specialist per component, one whole-branch review) |
| (transitive) superpowers:test-driven-development | ✓ (partial: Task 1 and review item 7 slipped) |
| (transitive) superpowers:requesting-code-review | ✓ (whole-branch pass, not per task) |
| superpowers:finishing-a-development-branch | pending — runs after archive |

### Deliberately Skipped Skills

- **`superpowers:using-git-worktrees`**
  - **What was skipped**: creating a dedicated worktree. The session started in a checkout whose `.git` was already broken, so work continued on a branch in that directory after the repair.
  - **Why this cycle**: the broken gitdir pointer was repaired in place from a fresh `--no-checkout` clone, and a branch (`feat/observability-orchestrator-tracing`) was created from `origin/main` at `887352d`. The branch is isolated and the baseline was clean.
  - **How to prevent recurrence**: `scope-judgment rule` — when the provided workspace is already a per-task checkout on its own branch, treat it as the worktree and record that in the plan instead of creating a nested one.

## 5. Surprises

- The OTel Operator injects `OTEL_SERVICE_NAME` from the Deployment name, so the documented `service.name` resource-attribute approach silently loses.
- `dapr.io/config` is a single-valued annotation, so the sidecar tracing source cannot simply be layered onto an existing orchestrator Configuration.
- Phase 1's store keys, assumed by the handoff, did not exist.
- Upstream sources disagree about whether activities can see the trace context (Dapr docs versus Java SDK 1.18's `ActivityRunner` span). That is why replay is observed and not designed.

## 6. Promote candidates → long-term learning

- [ ] 🟡 **Check the OTel Operator's own env injection before choosing the service-name mechanism** → **Promote to** project `CLAUDE.md` (cross-package contract notes)
  > **Why**: `OTEL_SERVICE_NAME` outranks `service.name` in `OTEL_RESOURCE_ATTRIBUTES`; the first design would have named the pod after its Deployment.
  > **How to apply**: any change that sets OTel identity on a pod the Operator instruments.

- [ ] 🟡 **Tell specialists to write the failing test first and report the red run** → **Promote to** schema (`superpowers-bridge` dispatch template)
  > **Why**: two slips in this change (Task 1 and review item 7) meant no red run was observed.
  > **How to apply**: every code micro-task dispatch.

- [ ] 🟡 **A change whose acceptance needs a live cluster should say so at proposal time** → **Promote to** memory (type: feedback)
  > **Why**: the live trace and replay finding were acceptance items but impossible here; discovering it late left the change PASS WITH WARNINGS.
  > **How to apply**: during proposal, probe the environment for cluster access before committing to live-run acceptance items.

- [ ] 📌 **Sandbox wipes `/tmp` and tool caches between calls** → **One-off** (recorded only)
  > **Why**: environment-specific; the fix was per-call tooling inside gitignored paths.
