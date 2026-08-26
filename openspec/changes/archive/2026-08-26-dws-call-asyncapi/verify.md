# Verification Report

**Change**: `dws-call-asyncapi`
**Verified at**: `2026-08-25 Asia/Ho_Chi_Minh`
**Verifier**: Claude, with self-review of the environment-blocked Java modules

---

## 1. Structural Validation (`openspec validate dws-call-asyncapi --json`)

- [x] The change is valid.

```
items: 1 passed, 0 failed
dws-call-asyncapi (change) — valid
```

---

## 2. Task Completion (`tasks.md`)

- [ ] All tasks are checked.

Runner tasks (1–5), controller/orchestrator implementation and build/test (6.1–6.4, 7.1–7.2), and
CI (8.1) are complete — the Java builds could not run locally but **passed in CI** on the PR head
`190d7a8` (all three workflows `success`). The only remaining items need live infra:

| Task | Reason not complete | Blocks archive |
|---|---|---|
| 8.2 | Integration test needs a live Dapr sidecar + Kafka broker (Docker/Kafka/Dapr unavailable). | Yes |
| 8.3 | Depends on the live integration check above. | Yes |

---

## 3. Delta Spec Sync State

| Capability | Sync state | Note |
|---|---|---|
| `asyncapi-step-execution` | Needs sync | Delta ready; sync belongs to archive after all tasks pass. |
| `asyncapi-step-compilation` | Needs sync | Delta ready; sync belongs to archive. |
| `workflow-error-handling` | Needs sync | ADDED requirement for step payload-validation classification. |

---

## 4. Fresh Verification Evidence

| Component | Command | Result |
|---|---|---|
| `dws-call-asyncapi` | `pnpm lint` | Passed (exit 0) |
| `dws-call-asyncapi` | `pnpm test` | **47 tests passed** across 6 files (config, document, operation, validator, binding, run) |
| `dws-call-asyncapi` | `pnpm build` | Passed (`tsc -p tsconfig.json`, no errors) |
| `dws-controller` | `./mvnw test` | **Not run locally** (JDK 21 vs required 25; offline Maven) — **CI green** on PR #63 head `190d7a8` (`dws-controller` run 42, `success`) |
| `dws-orchestrator` | `./mvnw verify` | **Not run locally** (same constraint) — **CI green** on PR #63 head `190d7a8` (`dws-orchestrator` run 57, `success`) |
| Integration | Dapr sidecar + Kafka binding | **Not run** — Docker/Kafka/Dapr unavailable |

The runner was tested on Node 22 (CI uses Node 24); the only diff is an engines-version warning.
The `dws-call-asyncapi` CI workflow (run 1, `success`) also validated lint/test/build and the
Dockerfile on the PR head.

---

## 5. Environment-blocked Java modules — self-review

Because the two Java modules cannot be compiled in this environment, the following were verified by
inspection against the existing code they mirror:

- **Controller compile branch** (`WorkflowCompiler.asyncApiStep` + helpers): uses only SDK getters
  that mirror the OpenAPI branch (`call.getCallAsyncAPI()`, `with.getDocument().getEndpoint()`,
  `with.getOperation()`, `with.getSubscription()`, `with.getAuthentication()`), whose names were
  confirmed against the DSL 1.0.0 schema (`schema/workflow.yaml`, `callAsyncAPI.with`:
  `document`/`channel`/`operation`/`server`/`protocol`/`message`/`subscription`/`authentication`).
  Protocol/host/channel-address are read from the fetched document with Jackson (fully controlled).
- **Model/synth** (`TaskKind.CALL_ASYNCAPI`, `ImageCatalog.callAsyncapi`, `BindingComponent`,
  `DeploymentPlan.bindingComponents` + compat constructors, `StackSynthesizer.bindingComponents`,
  `StackApplier` apply loop): SDK-independent, follow the `oauthEndpoints`/`OAuthEndpoint` pattern
  exactly. `ImageCatalog` constructor callers (`DwsConfig` + two test classes) were all updated.
- **Spotless** runs `apply` at `process-sources`, so hand-formatting is reformatted before compile
  and cannot fail the build; only compilation matters.
- **Orchestrator** (`WorkflowErrors.VALIDATION_MARKER`): a one-line marker + guarded classify branch
  plus a `WorkflowErrorsTest` case; low risk.

**Residual risk — now resolved by CI:** the assumption that `AsyncApiArguments.getDocument()` exposes
`getEndpoint()` (and the other SDK getter names) held — the `dws-controller` and `dws-orchestrator`
CI workflows compiled and passed on JDK 25 on the PR head. No follow-up compile is outstanding.

---

## 6. Front-Door Routing Leak Detector

- [x] No `docs/superpowers/specs/*.md` files were created (the superpowers-bridge routing keeps
  design artifacts under `openspec/changes/dws-call-asyncapi/`).

---

## 7. Deferred Manual Dogfood vs Automated Test Equivalence

| Manual / environment check | Automated coverage | Real gap? |
|---|---|---|
| Live Dapr Kafka publish | Runner `run.test.ts` mocks the sidecar binding endpoint (undici MockAgent) | Yes — must run the live sidecar+Kafka integration test |
| Controller `call: asyncapi` compile | `WorkflowCompilerTest` cases written (binding step + unsupported-protocol reject) | Yes — must run `./mvnw test` on JDK 25 |

---

## Overall Decision

- [ ] PASS
- [x] PASS WITH WARNINGS — all three CI workflows (runner, controller, orchestrator) are green on
  the PR head and the PR is mergeable; the one gap is the live Dapr+Kafka integration test (8.2/8.3),
  which no environment available here can run.
- [ ] FAIL

**Next step**: provision Docker + Kafka + Dapr and run the integration test (8.2), then check
8.2/8.3. Archive is blocked only on that live check; the unit/compile surface is verified by CI.
