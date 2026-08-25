## Why

DWS can call HTTP and OpenAPI services but has no runner for `call: grpc`, the
next protocol on the roadmap (Phase 5). Workflow authors targeting in-cluster or
external gRPC services have no deployable step image today, and `dws-controller`
rejects nothing for `call: grpc` because no image exists to stamp. Phase 4 auth is
merged, so protected gRPC calls can reuse the existing basic/bearer contract.

## What Changes

- Add a new independently-built monorepo component `dws-call-grpc` (Go): one
  generic prebuilt image serving every `call: grpc` step, configured entirely by
  environment variables, following the shared step contract (single `Run` Dapr
  Workflow activity, `GET /healthz`, `OUTPUT=replace|merge`, retryable
  "upstream failure" vs non-retryable "config failure" classification).
- Resolve the target method descriptor from a bundled `FileDescriptorSet`
  (`PROTO_ENDPOINT`, hash-pinned, primary) or server reflection (fallback).
- Invoke the runtime-selected unary method with a `connectrpc.com/connect`
  dynamic (`dynamicpb`) client over h2c (default) or TLS.
- Build the request from workflow data via `protojson` (passthrough) and shape the
  response per `OUTPUT`.
- Support `none`/`basic`/`bearer` auth as gRPC metadata, reusing Phase 4's
  `AUTH_*` environment contract; reject `oauth2` for gRPC (no Dapr gRPC middleware
  equivalent).
- Extend `dws-controller`'s `WorkflowCompiler` with a `call: grpc` branch that
  emits a `CALL_GRPC` StepService, plus `TaskKind` and `ImageCatalog` entries and
  the grpc image config; reuse existing basic/bearer auth resolution and reject
  oauth2 for grpc.
- Add a path-filtered `dws-call-grpc` CI workflow and update the roadmap.

## Capabilities

### New Capabilities

- `grpc-call-step`: Deploy and execute `call: grpc` workflow tasks through a
  generic, config-driven gRPC runner image with dynamic descriptor resolution and
  basic/bearer authentication.

### Modified Capabilities

- None.

## Impact

Affected components: `dws-call-grpc` (new) and `dws-controller` (compile branch,
`TaskKind`, `ImageCatalog`, config). Deployed workflows using `call: grpc` gain a
Knative Service per grpc task using the new image; the orchestrator invokes it by
kebab-case app-id exactly as for http/openapi, unchanged. Existing definitions
without `call: grpc` compile and run identically. `oauth2` on a `call: grpc` task
is rejected at compile time; basic/bearer reuse the Phase 4 secret-reference
contract. Streaming methods and `.proto` source compilation are out of scope.
