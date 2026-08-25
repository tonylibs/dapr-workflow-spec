# OWS Phase 5 (slice 1) — `dws-call-grpc` runner

## Background

Phase 4 (authentication + secrets) is merged. Roadmap Phase 5 is "Protocol
Expansion: gRPC, AsyncAPI, A2A" via new prebuilt runner images. This change
delivers the first slice: `dws-call-grpc`, the single prebuilt step image that
serves every `call: grpc` task, plus the `dws-controller` compile branch that
makes it reachable from a real workflow.

The runner copies the shape of `dws-call-http` exactly: `main.go` loads config,
registers one Dapr Workflow activity named `Run`, starts the worker, and serves
`GET /healthz` for Knative readiness. `internal/config`, `internal/runner`, and
`internal/activity` mirror the sibling. One generic image serves every step;
behavior is entirely environment-configured. `dws-controller` only ever stamps a
Knative Service with new env vars — it never triggers a container build or any
per-workflow codegen. (Confirmed: `WorkflowCompiler.walk()` maps a `call` task to
a `StepService` referencing a fixed prebuilt image from `ImageCatalog`; there is
no build path in the controller.)

The Open Workflow SDK (`serverlessworkflow-api` 7.26.0.Final) already models
`call: grpc` — `CallTask` is a oneOf that includes `CallGRPC` (see
`dws-controller/CLAUDE.md`). No SDK change is required.

This is an architectural change: a new independently-built monorepo component
plus a cross-component contract (task → `CALL_GRPC` StepService, and the runner's
env contract).

## Decision chain

The handoff named four open technical questions and asked for a real spike pass
before `design.md` locks anything in. A throwaway spike module under the
scratchpad (real gRPC server, `grpc.health.v1.Health/Check` resolved by string
name at runtime, no protoc) settled all four empirically.

### Q1 — Descriptor source: server reflection vs bundled FileDescriptorSet

A dynamic runner needs the target method's protobuf descriptor to build the
request and decode the response. Two sources were weighed:

- **Bundled `FileDescriptorSet`**, fetched once at boot from a `PROTO_ENDPOINT`
  URL and pinned by content hash. This mirrors how `dws-call-openapi` resolves
  its OpenAPI document (`DOCUMENT_URL` + `DOCUMENT_SHA256`, fetched at boot,
  verified against a controller-computed hash). Deterministic, works against
  services that do **not** expose reflection, needs no protoc in the runner.
- **Server reflection** at boot (`jhump/protoreflect/grpcreflect`), querying the
  target's `grpc.reflection.v1.ServerReflection` service. Zero configuration, but
  requires the target to expose reflection — commonly **disabled** on in-cluster
  production services.

**Decision:** support **both**, with the bundled set as the primary/recommended
path and reflection as the fallback:

- `PROTO_ENDPOINT` set → fetch a serialized `google.protobuf.FileDescriptorSet`
  once at boot, verify it against `PROTO_SHA256` (when provided), parse it with
  `protodesc.NewFiles`, and resolve the method by name. The DSL's `with.proto`
  external resource maps to `PROTO_ENDPOINT`; the operator points it at a
  self-contained descriptor set (`buf build -o` or
  `protoc --include_imports --descriptor_set_out`). This is a documented DWS
  convention, same spirit as Phase 4's "operator must create the Secret".
- `PROTO_ENDPOINT` unset → resolve the method via server reflection at boot.

This honours the "optional `PROTO_ENDPOINT`" annotation in the handoff, does not
assume reflection in production (bundled is primary), keeps dev/test ergonomic
(reflection needs no fixture), and reuses the proven `dws-call-openapi`
fetch-once-pin-by-hash pattern for the primary path.

**Spike evidence:**
- Built a `FileDescriptorSet` **without protoc** by taking a linked proto's
  `FileDescriptorProto` via `protodesc.ToFileDescriptorProto`, marshaling a
  `FileDescriptorSet` (874 bytes), re-parsing with `protodesc.NewFiles`, and
  resolving `grpc.health.v1.Health.Check` from the parsed set. This is exactly
  the runner's boot path and the mechanism for generating test fixtures without
  a protoc toolchain.
- Server reflection via `grpcreflect.NewClientV1` resolved the same method and
  its input type against a reflection-enabled server.

### Q2 — Invocation mechanics: connect-go generic client vs jhump grpcdynamic

Dependencies were pre-decided as `connectrpc.com/connect` (with `WithGRPC()`,
since targets are plain gRPC servers) + `dynamicpb`. The open question: does
connect-go's generic `NewClient[dynamicpb.Message, dynamicpb.Message]` actually
work for a service/method selected only at runtime, or must we fall back to
`jhump/protoreflect`'s `grpcdynamic.Stub` over a plain `grpc.ClientConn` (the
approach grpcurl uses)?

The hazard: connect-go's unary receive path does `var msg Res` and unmarshals
into `&msg`. For `Res = dynamicpb.Message`, a zero value carries no descriptor,
so `proto.Unmarshal` cannot populate it. connect-go v1.20.0 resolves this with
two options: `WithSchema(md)` (exposes the `protoreflect.MethodDescriptor` as
`Spec.Schema`) and `WithResponseInitializer(fn)` (the client calls `fn` to
construct the response message before unmarshaling). The initializer sets
`*msg = *dynamicpb.NewMessage(md.Output())`.

**Decision:** use **connect-go** — the pre-decided dependency works. No jhump
needed for invocation, keeping the dependency set minimal.

**Spike evidence:** a `connect.NewClient[dynamicpb.Message, dynamicpb.Message]`
with `WithGRPC()`, `WithSchema(md)`, and the response initializer above invoked
`grpc.health.v1.Health/Check` on a real server (method resolved by string name)
and decoded the dynamic response (`status = SERVING`). The jhump
`grpcdynamic.Stub` path also succeeded, confirming it as a viable fallback, but
it is not required and is therefore not adopted.

### Q3 — Plaintext HTTP/2 (h2c)

Most in-cluster gRPC services skip TLS between sidecars, so the runner must speak
HTTP/2 cleartext. connect-go's `WithGRPC()` speaks whatever the supplied
`*http.Client` speaks.

**Decision:** default to h2c plaintext; TLS is opt-in via `TLS=true`
(`INSECURE_SKIP_VERIFY` applies only then). h2c uses an `http2.Transport` with
`AllowHTTP: true` and a `DialTLSContext` that dials plain TCP. Plaintext-by-
default matches the in-cluster gRPC norm; TLS-by-opt-in keeps the insecure case
explicit.

**Spike evidence:** the connect-go call above ran over an h2c client
(`http2.Transport{AllowHTTP: true, DialTLSContext: plain TCP}`) against a
plaintext gRPC server and succeeded.

### Q4 — OAuth2 auth path for gRPC

`dws-call-http` routes OAuth2 calls through the local Dapr HTTP sidecar
(`daprInvocationURL()` → `/v1.0/invoke/<endpoint>/method/...`), relying on Dapr's
`middleware.http.oauth2clientcredentials` applied to a synthesized `HTTPEndpoint`.
That middleware is an **HTTP-pipeline** middleware; Dapr gRPC service invocation
has no equivalent middleware hook for injecting client-credentials tokens on an
outbound external gRPC call. Runner-managed OAuth2 (token acquisition inside the
image) was explicitly **deferred** in Phase 4 and remains out of scope.

**Decision:** for this slice, support `none`, `basic`, and `bearer` only, attached
as gRPC request metadata (`authorization: Basic <b64>` / `Bearer <token>`), reusing
Phase 4's `AUTH_SCHEME`/`AUTH_USERNAME`/`AUTH_PASSWORD`/`AUTH_TOKEN` env contract
verbatim (values injected via `secretKeyRef`). **Defer `oauth2` for gRPC:** the
controller rejects an `oauth2` policy on a `call: grpc` task at compile time with a
clear message, and the runner rejects `AUTH_SCHEME=oauth2` at config load. This is
honest about the Dapr gap and consistent with Phase 4's deferral of runner-managed
OAuth2.

## Approved design (summary)

- One generic Go image, config-driven, one `Run` activity, `GET /healthz`.
- Request built from current workflow data via `protojson` (passthrough, mirroring
  `dws-call-http`'s default body mode); response `protojson` → JSON → `OUTPUT`
  shaping (`replace` | `merge`) identical to the shared step contract.
- Descriptor from `PROTO_ENDPOINT` (bundled set, hash-pinned) or reflection.
- Invocation via connect-go dynamic client over h2c (default) or TLS.
- Auth: none/basic/bearer as metadata; oauth2 rejected.
- Errors classified exactly like `dws-call-http`: transport failures and gRPC
  non-OK status codes → retryable "upstream failure"; config/descriptor/decode
  errors → non-retryable "config failure".
- `dws-controller`: a `call.getCallGRPC()` branch in `WorkflowCompiler.walk()`
  emitting a `CALL_GRPC` `StepService`; `TaskKind` and `ImageCatalog` gain the
  grpc entries; reuse the existing basic/bearer auth resolution and reject oauth2
  for grpc.

## Config surface (finalized)

| Env | Required | Meaning |
|---|---|---|
| `TASK` | no (default `call-grpc`) | Task name / Dapr app-id (log/health only) |
| `SERVICE_ADDR` | yes | Target gRPC server `host:port` |
| `METHOD` | yes | `package.Service/Method` (fully-qualified service + method) |
| `PROTO_ENDPOINT` | no | URL of a serialized `FileDescriptorSet`; unset → reflection |
| `PROTO_SHA256` | no | Hex digest the fetched descriptor set must match (controller-set) |
| `TLS` | no (default `false`) | `true` → TLS; `false` → h2c plaintext |
| `INSECURE_SKIP_VERIFY` | no (default `false`) | Skip TLS verification (only when `TLS=true`) |
| `AUTH_SCHEME` | no (default `none`) | `none` \| `basic` \| `bearer` (oauth2 rejected) |
| `AUTH_USERNAME`/`AUTH_PASSWORD` | when basic | Basic credentials (secret-injected) |
| `AUTH_TOKEN` | when bearer | Bearer token (secret-injected) |
| `TIMEOUT` | no (default `30s`) | Per-call Go duration |
| `OUTPUT` | no (default `replace`) | `replace` \| `merge` |

## Test and rollout decisions

- `internal/config` tests: required-field validation, METHOD parsing, TLS/auth
  matrix, oauth2 rejection — mirroring `dws-call-http/internal/config/config_test.go`.
- `internal/runner` tests: descriptor resolution from a bundled `FileDescriptorSet`
  fixture (generated in-test without protoc), request build from workflow data,
  response shaping (`replace`/`merge`), transport vs upstream (non-OK status)
  classification.
- `internal/activity` tests: the two failure-marker forms, nil-input handling —
  mirroring `dws-call-http/internal/activity/activity_test.go`.
- One integration test against a **real** in-process gRPC server (h2c), exercising
  both the bundled-descriptor and reflection paths end-to-end.
- Controller: `WorkflowCompilerTest` cases for the grpc StepService env, method/
  proto wiring, basic/bearer auth reuse, and oauth2 rejection.
- CI gate mirrors the other Go component: `go vet ./... && go test ./...`, plus a
  new path-filtered `.github/workflows/dws-call-grpc.yml`.

## Explicit deferrals

- `oauth2` for gRPC targets (no Dapr gRPC middleware equivalent; runner-managed
  OAuth2 deferred since Phase 4).
- Compiling `.proto` **source** in the runner (protocompile) — `PROTO_ENDPOINT`
  takes a pre-compiled `FileDescriptorSet` only.
- Client/server/bidi **streaming** methods — unary only in this slice.
- A `with.arguments` request template — request is workflow-data passthrough,
  matching `dws-call-http`'s default; template mode is a later extension.
- AsyncAPI and A2A runners (later Phase 5 slices).
