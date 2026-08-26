# dws-call-grpc

Prebuilt step image for `call: grpc` tasks in the DWS platform. One generic image
serves **every** gRPC call step; behavior is defined entirely by environment
variables. No per-workflow code is written or generated — `dws-controller` only
stamps a Knative Service with the env block below.

The runner registers a single Dapr Workflow activity named `Run` and serves
`GET /healthz` for Knative readiness, following the shared step contract used by
`dws-call-http` and `dws-call-openapi` (`OUTPUT=replace|merge`; upstream/transport
failures are retryable, config failures are not).

## How it works

1. At startup the runner resolves the target method's protobuf descriptor from one
   of two sources (see **Descriptor source**) and builds a dynamic
   [`connectrpc.com/connect`](https://connectrpc.com/) client
   (`WithGRPC()`, `dynamicpb` messages) — no generated stubs.
2. Each `Run` invocation maps the current workflow data onto the request message
   via protobuf JSON (unknown fields discarded), invokes the **unary** method, and
   shapes the response per `OUTPUT`.
3. gRPC non-OK statuses and transport failures are reported as retryable upstream
   failures; descriptor, config, encode, and decode errors are non-retryable
   config failures.

## Descriptor source

- **Bundled `FileDescriptorSet` (recommended):** set `PROTO_ENDPOINT` to a URL
  serving a **serialized `google.protobuf.FileDescriptorSet`** (self-contained —
  all transitive imports included, e.g. `buf build -o set.binpb` or
  `protoc --include_imports --descriptor_set_out=set.binpb ...`). The runner
  fetches it once at boot and verifies it against `PROTO_SHA256` when provided.
  Raw `.proto` **source is not accepted.** Works against services that do not
  expose reflection.
- **Server reflection (fallback):** leave `PROTO_ENDPOINT` unset and the runner
  resolves the method via the target's `grpc.reflection.v1.ServerReflection`
  service. Convenient for development and reflection-enabled targets.

## Configuration

| Env | Required | Default | Meaning |
|---|---|---|---|
| `TASK` | no | `call-grpc` | Task name / Dapr app-id (logging and `/healthz` only) |
| `SERVICE_ADDR` | **yes** | — | Target gRPC server `host:port` |
| `METHOD` | **yes** | — | `package.Service/Method` (fully-qualified service + method) |
| `PROTO_ENDPOINT` | no | — | URL of a serialized `FileDescriptorSet`; unset → reflection |
| `PROTO_SHA256` | no | — | Hex digest the fetched descriptor set must match |
| `TLS` | no | `false` | `true` → TLS; `false` → HTTP/2 cleartext (h2c) |
| `INSECURE_SKIP_VERIFY` | no | `false` | Skip TLS verification (only when `TLS=true`) |
| `AUTH_SCHEME` | no | `none` | `none` \| `basic` \| `bearer` (oauth2 is not supported) |
| `AUTH_USERNAME` / `AUTH_PASSWORD` | when `basic` | — | Basic credentials (secret-injected) |
| `AUTH_TOKEN` | when `bearer` | — | Bearer token (secret-injected) |
| `TIMEOUT` | no | `30s` | Per-call Go duration |
| `OUTPUT` | no | `replace` | `replace` (response verbatim) \| `merge` (onto input) |

Basic/bearer credentials are attached as gRPC `authorization` metadata. `oauth2`
is rejected at startup (and at controller compile time): Dapr has no
gRPC-invocation OAuth2 middleware equivalent, and runner-managed OAuth2 is out of
scope.

## Commands

```shell
make build          # compile bin/dws-call-grpc
make test           # go test -race ./...
make vet            # go vet ./...
make lint           # vet + gofmt check (+ golangci-lint if installed)
make docker         # build registry.io/dws/dws-call-grpc:1.0
```

CI gate: `go vet ./... && go test ./...`.

## Scope

Unary methods only. Streaming methods, `.proto` source compilation, a
`with.arguments` request template, and `oauth2` for gRPC targets are out of scope
for this image.
