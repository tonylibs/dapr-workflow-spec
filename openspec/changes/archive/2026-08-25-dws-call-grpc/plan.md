# dws-call-grpc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship `dws-call-grpc`, the prebuilt runner image for `call: grpc` steps, and the `dws-controller` compile branch that deploys it — one generic image, config-driven, no per-workflow codegen.

**Architecture:** A Go component mirroring `dws-call-http`: `main.go` registers one `Run` Dapr Workflow activity and serves `/healthz`; `internal/config` parses env; `internal/runner` resolves the method descriptor (bundled `FileDescriptorSet` or reflection) and invokes the unary method with a `connectrpc.com/connect` dynamic (`dynamicpb`) client over h2c or TLS; `internal/activity` maps runner errors to the retryable/non-retryable markers. `dws-controller` gains a `call: grpc` branch in `WorkflowCompiler.walk()`, a `CALL_GRPC` `TaskKind`, and a `callGrpc` image in `ImageCatalog`/config.

**Tech Stack:** Go 1.26, `connectrpc.com/connect` (`WithGRPC`), `google.golang.org/protobuf` (`dynamicpb`, `protojson`, `protodesc`, `descriptorpb`), `jhump/protoreflect/grpcreflect` (reflection fallback), `golang.org/x/net/http2` (h2c), `github.com/dapr/durabletask-go`/`dapr/go-sdk` (worker); Java 25/Quarkus for the controller branch.

**Spec:** `openspec/changes/dws-call-grpc/design.md`; `openspec/changes/dws-call-grpc/specs/grpc-call-step/spec.md`

## Global Constraints

- One generic image serves every `call: grpc` step; all behavior is env-driven. The controller never builds an image or generates code.
- Follow the shared step contract: single `Run` activity, `GET /healthz`, `OUTPUT=replace|merge`, retryable "upstream failure" vs non-retryable "config failure" markers.
- Descriptor from `PROTO_ENDPOINT` (self-contained `FileDescriptorSet`, hash-pinned) or server reflection; a misconfigured/streaming method fails fast at startup.
- h2c plaintext by default; TLS opt-in. Unary methods only.
- Reuse Phase 4's `AUTH_*` env contract for basic/bearer; reject oauth2 for grpc at controller compile and runner load.
- CI gate mirrors the other Go component: `go vet ./... && go test ./...`.

---

### Task 1: Scaffold the `dws-call-grpc` Go module and config

**Files:**
- Add: `dws-call-grpc/go.mod`, `dws-call-grpc/go.sum`
- Add: `dws-call-grpc/internal/config/config.go`, `dws-call-grpc/internal/config/config_test.go`

**Step 1: Write config tests**
- [ ] Required `SERVICE_ADDR`/`METHOD`; `METHOD` parses `package.Service/Method` into full service name + method; TLS/`INSECURE_SKIP_VERIFY` defaults; `AUTH_SCHEME` matrix; oauth2 rejected; `OUTPUT`/`TIMEOUT` defaults and validation.

**Step 2: Implement config**
- [ ] `config.Load()` with fail-fast validation mirroring `dws-call-http/internal/config`.

**Step 3: Verify**
- [ ] `cd dws-call-grpc && go test ./internal/config/...`

### Task 2: Descriptor resolution + dynamic invocation runner

**Files:**
- Add: `dws-call-grpc/internal/runner/runner.go`, `dws-call-grpc/internal/runner/descriptor.go`
- Add: `dws-call-grpc/internal/runner/runner_test.go`, `dws-call-grpc/internal/runner/descriptor_test.go`

**Step 1: Write runner tests**
- [ ] Descriptor resolves from an in-test `FileDescriptorSet` fixture (built without protoc via `protodesc.ToFileDescriptorProto`); streaming method rejected; request built from workflow data; `replace`/`merge` shaping; `UpstreamError` (non-OK status) vs `TransportError` (dial failure).

**Step 2: Implement runner**
- [ ] `descriptor.go`: fetch+verify+parse bundled set, or reflection; resolve `MethodDescriptor` by `METHOD`.
- [ ] `runner.go`: build h2c/TLS `http.Client`, connect-go dynamic client (`WithGRPC`/`WithSchema`/`WithResponseInitializer`), attach basic/bearer metadata, `protojson` in/out, `OUTPUT` shaping, error types.

**Step 3: Verify**
- [ ] `cd dws-call-grpc && go test ./internal/runner/...`

### Task 3: Activity wrapper + main + packaging

**Files:**
- Add: `dws-call-grpc/internal/activity/activity.go`, `dws-call-grpc/internal/activity/activity_test.go`
- Add: `dws-call-grpc/main.go`, `dws-call-grpc/Dockerfile`, `dws-call-grpc/Makefile`, `dws-call-grpc/README.md`, `dws-call-grpc/k8s/knative-service.yaml`

**Step 1: Activity tests** — mirror `dws-call-http`: upstream vs config markers, nil input → `{}`, nil result → input unchanged.

**Step 2: Implement** activity, `main.go` (register `Run`, start worker, `/healthz`), Dockerfile/Makefile/README/k8s manifest.

**Step 3: Verify**
- [ ] `cd dws-call-grpc && go vet ./... && gofmt -l . && go test -race ./...`

### Task 4: Integration test against a real gRPC server

**Files:**
- Add: `dws-call-grpc/internal/runner/integration_test.go`

**Step 1 & 2: Write + pass** — stand up an in-process gRPC server (h2c), run the full runner end-to-end via both the bundled-descriptor path and the reflection path; assert response shaping and an upstream-status classification.

**Step 3: Verify**
- [ ] `cd dws-call-grpc && go test ./...`

### Task 5: Controller `call: grpc` compile branch

**Files:**
- Modify: `dws-controller/src/main/java/io/dws/controller/model/TaskKind.java`, `.../model/ImageCatalog.java`
- Modify: `.../compile/WorkflowCompiler.java`
- Modify: controller config (`DwsConfig`, `application.yaml`) and image-catalog wiring
- Modify: `dws-controller/src/test/java/io/dws/controller/compile/WorkflowCompilerTest.java`

**Step 1: Write compiler tests** — grpc StepService env (`SERVICE_ADDR`/`METHOD`/`OUTPUT`/`TIMEOUT`), `PROTO_ENDPOINT`+`PROTO_SHA256`, kebab app-id, basic/bearer reuse, oauth2 rejection.

**Step 2: Implement** `TaskKind.CALL_GRPC`, `ImageCatalog.callGrpc`, `dws.images.call-grpc` config, and `grpcStep(...)` in `walk()`; verify the 7.26.0 `CallGRPC` getters by compilation.

**Step 3: Verify**
- [ ] `cd dws-controller && ./mvnw test -Dtest=WorkflowCompilerTest` (or full `./mvnw test` if the toolchain allows)

### Task 6: CI workflow, roadmap, and change verification

**Files:**
- Add: `.github/workflows/dws-call-grpc.yml`
- Modify: `docs/roadmaps/openworkflow-features.md`
- Add: `openspec/changes/dws-call-grpc/verify.md`

**Step 1–3:** path-filtered CI mirroring `dws-run`/`dws-call-http`; mark Phase 5 slice 1 in the roadmap; run every component gate and record results (honest FAIL for any check needing infra this environment lacks — Docker/live cluster/Java 25 toolchain), following `workflow-auth`'s `verify.md` format.
