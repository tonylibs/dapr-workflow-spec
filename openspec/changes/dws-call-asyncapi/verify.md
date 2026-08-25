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

Runner tasks (1–5), controller/orchestrator implementation (6.1–6.3, 7.1), and CI (8.1) are
complete. The remaining items are environment-blocked, not missing implementation:

| Task | Reason not complete | Blocks archive |
|---|---|---|
| 6.4 | `./mvnw test` (dws-controller) needs JDK 25 (only JDK 21 present) and a populated Maven cache (empty; offline). Compiler code is written and self-reviewed but not compiled here. | Yes |
| 7.2 | `./mvnw verify` (dws-orchestrator) blocked for the same reason. | Yes |
| 8.2 | Integration test needs a live Dapr sidecar + Kafka broker (Docker/Kafka/Dapr unavailable). | Yes |
| 8.3 | Depends on the checks above. | Yes |

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
| `dws-controller` | `./mvnw test` | **Not run** — JDK 25 required (JDK 21 present); Maven cache empty/offline |
| `dws-orchestrator` | `./mvnw verify` | **Not run** — same JDK/cache constraint |
| Integration | Dapr sidecar + Kafka binding | **Not run** — Docker/Kafka/Dapr unavailable |

The runner was tested on Node 22 (CI uses Node 24); the only diff is an engines-version warning.

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

**Residual risk:** the exact return *type* of `AsyncApiArguments.getDocument()` is assumed to expose
`getEndpoint()` (as the OpenAPI `document` does, sharing the `$defs/externalResource` `$ref`). A
follow-up `./mvnw test` on JDK 25 is required to confirm.

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
- [ ] PASS WITH WARNINGS
- [x] FAIL — finish the JDK-25 controller/orchestrator builds and the live Dapr+Kafka integration
  test, check tasks 6.4/7.2/8.2/8.3, then re-run verification.

**Next step**: On a JDK 25 + populated Maven environment, run `./mvnw test` (dws-controller) and
`./mvnw verify` (dws-orchestrator); provision Docker + Kafka + Dapr and run the integration test.
Archive and branch-finalization are intentionally blocked until then.
