## Context

DWS compiles Open Workflow definitions into immutable, content-addressed workflow
versions and one deployed step service per I/O task. `call: http` and
`call: openapi` already have prebuilt runner images and controller compile
branches; `call: grpc` does not. Phase 5 (Protocol Expansion) begins with the
gRPC runner. The SDK already models `CallGRPC` on the `CallTask` oneOf, so no SDK
change is needed.

All four open technical questions were settled by a throwaway spike (real gRPC
health server, method resolved by string name, no protoc). See `brainstorm.md`
for the evidence; this document records the resulting contracts.

## Goals / Non-Goals

**Goals:**

- A single generic `dws-call-grpc` image serving every `call: grpc` step, driven
  only by environment variables, following the shared step contract.
- Dynamic invocation of a runtime-selected unary method without generated stubs or
  per-workflow codegen.
- Descriptor resolution from a hash-pinned bundled `FileDescriptorSet` or server
  reflection.
- h2c plaintext by default, TLS opt-in.
- Reuse of Phase 4's basic/bearer auth contract; oauth2 rejected for gRPC.
- A `dws-controller` compile branch that stamps the image with env vars only.

**Non-Goals:**

- OAuth2 for gRPC targets, `.proto` source compilation in the runner, streaming
  methods, a `with.arguments` request template, and the AsyncAPI/A2A runners.
- Any change to the orchestrator (it invokes the grpc step by kebab-case app-id
  exactly as any other `call`, via the existing `Run` activity contract).

## Decisions

### D1: One generic image, `Run` activity, shared step contract

- **Choice:** copy `dws-call-http` structure exactly. `main.go` loads config,
  builds `runner.New(cfg)`, registers a single Dapr Workflow activity named `Run`
  (`activity.Name`) via `registry.AddActivityN`, starts `wfClient.StartWorker`,
  and serves `GET /healthz` on `PORT`. `internal/config`, `internal/runner`,
  `internal/activity` split responsibilities identically.
- **Rationale:** the orchestrator dispatches every `call`/`run` step as the `Run`
  activity keyed by Dapr app-id; a grpc step is indistinguishable from an http step
  at the dispatch layer. Reusing the shape keeps the four runners uniform.

### D2: Descriptor source — bundled `FileDescriptorSet` primary, reflection fallback

- **Choice:** if `PROTO_ENDPOINT` is set, fetch the bytes once at boot, verify
  against `PROTO_SHA256` when present, and parse with
  `protodesc.NewFiles(&descriptorpb.FileDescriptorSet{...})`. Otherwise dial the
  target and resolve via `grpc.reflection.v1.ServerReflection`
  (`jhump/protoreflect/grpcreflect`). Resolve the service+method named by `METHOD`
  from the resulting files; hold the `protoreflect.MethodDescriptor` for the
  process lifetime.
- **Rationale:** mirrors `dws-call-openapi`'s boot-time document fetch and hash
  pin, works against reflection-disabled services, and needs no protoc in the
  image. Reflection stays available for dev and reflection-enabled targets.
- **Contract:** `PROTO_ENDPOINT` serves a **self-contained** serialized
  `FileDescriptorSet` (all transitive imports included). Raw `.proto` source is not
  accepted. Failure to fetch/parse/resolve is a startup error (process exits
  non-zero) — a misconfigured step never serves.

### D3: Invocation — connect-go dynamic client

- **Choice:** `connect.NewClient[dynamicpb.Message, dynamicpb.Message](httpClient,
  url, connect.WithGRPC(), connect.WithSchema(md), connect.WithResponseInitializer(
  fn))` where `url = "<scheme>://<SERVICE_ADDR>/<fullService>/<method>"`, `md` is
  the resolved method descriptor, and `fn` sets
  `*resp = *dynamicpb.NewMessage(md.Output())`. Requests are
  `connect.NewRequest(reqMsg)` with `reqMsg` a `*dynamicpb.Message` of `md.Input()`.
- **Rationale:** the pre-decided dependency; the spike proved it works for a method
  selected only at runtime. Avoids adding grpc-go/jhump for invocation.
- **Streaming:** rejected — `md.IsStreamingClient() || md.IsStreamingServer()` is a
  config error at boot.

### D4: Transport — h2c default, TLS opt-in

- **Choice:** `TLS=false` (default) builds an `http.Client` with
  `&http2.Transport{AllowHTTP: true, DialTLSContext: <plain TCP dial>}` and a
  `http://` connect URL. `TLS=true` builds a standard TLS `http2.Transport`
  (honoring `INSECURE_SKIP_VERIFY`) and an `https://` URL. `TIMEOUT` is applied
  per call via `context.WithTimeout`, not the client, so it covers the whole unary
  round trip.
- **Rationale:** in-cluster gRPC between sidecars is normally plaintext; making
  plaintext the default matches the common case while keeping TLS explicit.

### D5: Data mapping — protojson passthrough + `OUTPUT` shaping

- **Choice:** the request message is `protojson.Unmarshal(inputJSON, reqMsg)` with
  `DiscardUnknown: true` over the current workflow-data document (nil input →
  `{}`). The response message is `protojson.Marshal`ed back to JSON, then shaped:
  `OUTPUT=replace` returns the response object verbatim; `OUTPUT=merge`
  shallow-merges it onto the input, identical to `dws-call-http`'s `shapeOutput`.
- **Rationale:** keeps the grpc runner's data contract identical to the other
  runners. `DiscardUnknown` tolerates workflow data carrying fields beyond the
  request message, so a shared data document flows through a grpc step cleanly.

### D6: Auth — none/basic/bearer as metadata; oauth2 rejected

- **Choice:** reuse Phase 4's env contract. `basic` sets
  `authorization: Basic base64(user:pass)`; `bearer` sets
  `authorization: Bearer <token>`; both attach as connect request headers (gRPC
  metadata). `AUTH_SCHEME=oauth2` is a config error at boot. The controller rejects
  an `oauth2` policy on a `call: grpc` task at compile time.
- **Rationale:** Dapr's OAuth2 client-credentials middleware is HTTP-pipeline only;
  there is no gRPC-invocation equivalent, and runner-managed OAuth2 was deferred in
  Phase 4. Basic/bearer need only header construction, so they reuse the existing
  secret-injected contract unchanged.

### D7: Error classification

- **Choice:** `runner.Run` returns `*UpstreamError` for a gRPC non-OK status
  (`connect.CodeOf`) and `*TransportError` for dial/connection/timeout failures;
  `activity.classify` maps both to the retryable `step '<task>' upstream failure:`
  marker. Descriptor, config, request-encode, and response-decode failures are
  plain errors mapped to the non-retryable `step '<task>' config failure:` marker.
- **Rationale:** identical to `dws-call-http`, so the orchestrator's existing
  retry policy behaves the same for grpc steps. gRPC status codes are upstream
  outcomes and therefore retryable, matching non-2xx HTTP.

### D8: Controller compile branch

- **Choice:** add `TaskKind.CALL_GRPC`, extend `ImageCatalog` with `callGrpc` (and
  `dws.images.call-grpc` config + `application.yaml`/`DwsConfig`), and add a
  `call.getCallGRPC() != null` branch to `WorkflowCompiler.walk()` producing a
  `grpcStep(...)`. `grpcStep` reads `with.service` (host+port → `SERVICE_ADDR`),
  `with.method` → `METHOD`, `with.proto` endpoint → `PROTO_ENDPOINT` (+ fetched
  `PROTO_SHA256`, mirroring `openApiStep`), `with.output` → `OUTPUT`,
  `call.getTimeout()` → `TIMEOUT`, and resolves endpoint/service authentication via
  the existing `resolveAuth`, rejecting the oauth2 scheme for grpc.
- **Rationale:** the controller's only job is stamping env onto a Knative Service;
  the grpc branch matches the http/openapi branches one-for-one. The exact
  `CallGRPC`/`GRPCArguments` getters are pinned to the 7.26.0 SDK at implementation
  time and verified by compilation + `WorkflowCompilerTest`.

## Risks / Trade-offs

- **`with.proto` as a compiled descriptor set, not source.** Operators must supply
  a `FileDescriptorSet`, not a `.proto` file. Documented in the runner README and
  the controller error messages; consistent with Phase 4's operator-provisioned
  Secrets. Mitigation: reflection fallback covers targets that expose it.
- **h2c default.** A misconfigured TLS target dialed as plaintext fails fast at the
  first call (transport error, retryable then surfaced), not silently.
- **SDK getter drift.** The `CallGRPC` model shape is verified at compile time; if
  7.26.0 names differ from the assumed `getWith().getService()/.getMethod()/
  .getProto()`, the branch is adjusted with no contract change.

## Migration Plan

Additive. No existing definition changes behavior. A definition that previously
failed only because it used `call: grpc` (previously ignored by `walk()`, so it
deployed no step and the orchestrator would fail to invoke it) now compiles to a
working step. New CI workflow and roadmap update ship with the change.

## Open Questions

None blocking. `with.arguments` request templating, streaming, and oauth2-for-grpc
are recorded as explicit deferrals in `brainstorm.md`.
