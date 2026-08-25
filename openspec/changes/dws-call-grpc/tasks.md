## 1. dws-call-grpc module and configuration

- [ ] 1.1 Create the `dws-call-grpc` Go module (`go.mod`, Go 1.26) and `internal/config` with fail-fast validation: required `SERVICE_ADDR`/`METHOD`, `METHOD` parsing into fully-qualified service + method, TLS/`INSECURE_SKIP_VERIFY`/`OUTPUT`/`TIMEOUT` defaults, `AUTH_SCHEME` matrix, and oauth2 rejection.
- [ ] 1.2 Add `internal/config/config_test.go` mirroring `dws-call-http`; run `go test ./internal/config/...` from `dws-call-grpc`.

## 2. Descriptor resolution and dynamic invocation

- [ ] 2.1 Implement `internal/runner/descriptor.go`: resolve the `METHOD` descriptor from a hash-pinned bundled `FileDescriptorSet` (`PROTO_ENDPOINT`) or server reflection; reject streaming/unresolvable methods at startup.
- [ ] 2.2 Implement `internal/runner/runner.go`: h2c-default/TLS-opt-in client, connect-go dynamic (`dynamicpb`) client with `WithGRPC`/`WithSchema`/`WithResponseInitializer`, basic/bearer metadata, `protojson` request/response, `OUTPUT=replace|merge` shaping, and `UpstreamError`/`TransportError` types.
- [ ] 2.3 Add runner and descriptor unit tests (fixtures built without protoc); run `go test ./internal/runner/...` from `dws-call-grpc`.

## 3. Activity wrapper, entrypoint, and packaging

- [ ] 3.1 Implement `internal/activity/activity.go` mapping runner errors to the retryable "upstream failure" / non-retryable "config failure" markers, and `main.go` registering the single `Run` activity, starting the worker, and serving `GET /healthz`.
- [ ] 3.2 Add `internal/activity/activity_test.go`; add `Dockerfile`, `Makefile`, `README.md`, and `k8s/knative-service.yaml`.
- [ ] 3.3 Run `go vet ./...`, `gofmt -l .`, and `go test -race ./...` from `dws-call-grpc`.

## 4. Integration test against a real gRPC service

- [ ] 4.1 Add an integration test that stands up an in-process gRPC server (h2c) and drives the runner end-to-end via both the bundled-descriptor and reflection descriptor sources, asserting response shaping and an upstream-status classification; run `go test ./...` from `dws-call-grpc`.

## 5. Controller `call: grpc` compile branch

- [ ] 5.1 Add `TaskKind.CALL_GRPC`, extend `ImageCatalog` with `callGrpc`, and wire `dws.images.call-grpc` through `DwsConfig`/`application.yaml` and the image-catalog construction.
- [ ] 5.2 Add a `call.getCallGRPC()` branch to `WorkflowCompiler.walk()` producing a `CALL_GRPC` StepService (`SERVICE_ADDR`/`METHOD`/`OUTPUT`/`TIMEOUT`, `PROTO_ENDPOINT`+`PROTO_SHA256`), reusing existing basic/bearer auth resolution and rejecting oauth2 for grpc.
- [ ] 5.3 Add `WorkflowCompilerTest` cases (grpc env, proto pinning, kebab app-id, basic/bearer reuse, oauth2 rejection); run `./mvnw test -Dtest=WorkflowCompilerTest` from `dws-controller`.

## 6. CI, roadmap, and verification

- [ ] 6.1 Add a path-filtered `.github/workflows/dws-call-grpc.yml` mirroring the other Go component (test/vet/build gate; image build validation without push on PRs).
- [ ] 6.2 Mark Phase 5 slice 1 (`dws-call-grpc`) in `docs/roadmaps/openworkflow-features.md`.
- [ ] 6.3 Run every affected component's validation gate and record results in `verify.md`, with an honest FAIL for any check that needs infrastructure this environment lacks (Docker/live cluster/Java 25 toolchain), following `workflow-auth`'s `verify.md` format.
