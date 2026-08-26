# Verification Report

**Change**: `dws-call-grpc`
**Verified at**: `2026-08-25`
**Verifier**: Claude (Claude Code), with running-code spikes and standalone compile checks

---

## 1. Structural Validation (`openspec validate dws-call-grpc --strict --json`)

- [x] The `dws-call-grpc` change is valid.

```
items: 1  passed: 1  failed: 0   (change: dws-call-grpc — valid, 0 issues)
```

Repository-wide `openspec validate --all` was not run for this report; the changed
item validates strictly on its own.

---

## 2. Task Completion (`tasks.md`)

- [x] All tasks are checked.

14/14 tasks are complete. Task 5.3's `./mvnw test` could not run locally (JDK 21
only, the pre-existing `maven.compiler.release=25` mismatch), but it executed
**green in CI under JDK 25** — see §5.

---

## 3. Delta Spec Sync State

| Capability | Sync state | Note |
|---|---|---|
| `grpc-call-step` | Needs sync | Delta spec is ready; sync belongs to archive after all tasks pass. |

---

## 4. Design / Specs Coherence Spot Check

| Sample | Design decision | Evidence | Drift |
|---|---|---|---|
| Descriptor source | Bundled `FileDescriptorSet` primary, reflection fallback | `descriptor.go` + `descriptor_test.go` (both paths against a real server); spike built a set without protoc | None |
| Invocation | connect-go dynamic client (`WithGRPC`/`WithSchema`/`WithResponseInitializer`) | `runner.go` + `runner_test.go`; spike proved a runtime-selected method decodes | None |
| Transport | h2c default, TLS opt-in | `httpClient()`; spike ran over h2c | None |
| Auth | none/basic/bearer metadata; oauth2 rejected | `runner.applyAuth`, `config.parseAuth`, controller `resolveGrpcAuth` + `grpcOauth2Rejected` test | None |
| Controller | `call: grpc` → `CALL_GRPC` StepService, proto hash-pinned, oauth2 rejected | `WorkflowCompiler.grpcStep`; javac compile-check vs real SDK | None |
| DSL nuance | schema requires `with.proto`, so controller output always sets `PROTO_ENDPOINT`; reflection is runner-only | schema `required: [proto, service, method]`; recorded in design.md D2 | None |

---

## 5. Implementation Signal

- [x] Implementation code and change artifacts are committed on `claude/dws-call-grpc-runner-p6519a`.
- [x] All four open technical questions were resolved by a throwaway spike (real
  gRPC health server, method resolved by string name, no protoc); see
  `brainstorm.md`.

Fresh verification evidence:

| Component | Command | Result |
|---|---|---|
| dws-call-grpc | `gofmt -l .` | clean |
| dws-call-grpc | `go vet ./...` | pass |
| dws-call-grpc | `go test -race ./...` | pass — 3 packages (config, activity, runner), 13 test functions / 38 subtests, incl. integration tests against a real in-process gRPC server over h2c (both descriptor sources) |
| dws-call-grpc | `go build -trimpath -ldflags="-s -w"` | pass |
| dws-controller | standalone `javac` of `compile` + `model` packages vs real `serverlessworkflow` 7.26.0 SDK jars | pass — validates `CallGRPC`/`GRPCArguments`/`WithGRPCService` getters and the new grpc branch |
| dws-controller | `./mvnw test` (incl. `WorkflowCompilerTest` grpc cases) under JDK 25 | **pass in CI** — `dws-controller` workflow on PR #62 head `1a914e7` (conclusion: success) |
| dws-call-grpc | full Go gate + image build | **pass in CI** — `dws-call-grpc` workflow on PR #62 head `1a914e7` (conclusion: success) |
| dws-call-grpc image | `docker build` | **pass in CI** — built in the `dws-call-grpc` workflow (validated on PR, pushed on merge) |

Go toolchain note: the module targets Go 1.26.4; a temporary `GOTOOLCHAIN=auto`
fetch of Go 1.26.4 provided the toolchain (system Go is 1.24.7). CI uses
`go-version-file: dws-call-grpc/go.mod`.

---

## 6. Front-Door Routing Leak Detector

- [x] No `docs/superpowers/specs/*.md` files were created by this change.

---

## 7. Deferred Manual Dogfood vs Automated Test Equivalence

The plan has no `[~]` deferred rows. The unchecked task above is an explicit
JDK-version-blocked execution, not silently deferred dogfood.

| Manual / environment check | Automated coverage | Assessment | Real gap? |
|---|---|---|---|
| `dws-controller` grpc compile + `WorkflowCompilerTest` | JUnit cases executed green in CI under JDK 25 | Proves types/getters, branch shape, and the JUnit assertions | No — passed in CI |
| Live-cluster deploy of a `call: grpc` step | Runner integration tests exercise the full call path against a real gRPC server in-process; controller synth tests run in CI | Proves runner behavior end-to-end and controller synthesis; does not prove a live Knative/Dapr round trip | Partial — a live-cluster smoke test remains future work, as for the sibling runners |
| Container image build | Built green in the `dws-call-grpc` CI workflow | Validates the Dockerfile on every PR; pushed on merge | No — passed in CI |

---

## Overall Decision

- [x] PASS — the runner (`dws-call-grpc`) is fully implemented, tested (unit +
  real-server integration), formatted, vetted, and builds. The controller branch
  is implemented, compiles against the real SDK, and its `WorkflowCompilerTest`
  grpc cases pass under JDK 25 in CI. Both the `dws-call-grpc` and `dws-controller`
  CI workflows completed green on PR #62 head `1a914e7`, including both image
  builds.
- [ ] PASS WITH WARNINGS
- [ ] FAIL

**Next step**: ready to `/opsx:sync` the `grpc-call-step` delta spec and archive on
merge. A live-cluster smoke deploy of a `call: grpc` step remains future work, as
for the sibling runners.
