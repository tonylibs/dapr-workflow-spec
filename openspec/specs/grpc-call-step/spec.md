# grpc-call-step Specification

## Purpose

The `call: grpc` step protocol for DWS workflows: the `dws-controller` compile behavior that maps a
`call: grpc` task to a `CALL_GRPC` StepService backed by the prebuilt `dws-call-grpc` image, and the
runner contract that image implements — resolving the target method's protobuf descriptor from a
hash-pinned bundled `FileDescriptorSet` (or server reflection), invoking the runtime-selected unary
method with a dynamic gRPC client over h2c or TLS, mapping workflow data through the request/response
via protobuf JSON and the shared `OUTPUT=replace|merge` shaping, and classifying failures as
retryable (upstream/transport) or non-retryable (config). Basic/bearer authentication reuses the
Phase 4 secret-reference contract; oauth2 is rejected for gRPC. Introduced in `dws-call-grpc`
(Protocol Expansion, Phase 5 slice 1).

## Requirements

### Requirement: The controller compiles `call: grpc` tasks to a gRPC step service

`dws-controller` SHALL compile a `call: grpc` task into a `CALL_GRPC` StepService
that references the prebuilt `dws-call-grpc` image and carries the target address,
method, descriptor source, output mode, timeout, and authentication as environment
variables. The step's Dapr app-id SHALL be the kebab-case task name, identical to
`call: http` and `call: openapi`. The controller SHALL NOT trigger any container
build or code generation.

#### Scenario: gRPC call compiles to a step service

- **WHEN** a definition contains a `call: grpc` task with a service host/port and a
  fully-qualified method
- **THEN** the compiled StepService uses the `dws-call-grpc` image with
  `SERVICE_ADDR` and `METHOD` set and a kebab-case app-id equal to the task name

#### Scenario: Bundled descriptor is pinned by content hash

- **WHEN** a `call: grpc` task declares a `proto` external resource
- **THEN** the controller sets `PROTO_ENDPOINT` to that URL and `PROTO_SHA256` to
  the SHA-256 of the fetched descriptor set

#### Scenario: Basic and bearer authentication reuse the Phase 4 contract

- **WHEN** a `call: grpc` endpoint declares an inline or named `basic` or `bearer`
  policy referencing declared secrets
- **THEN** the compiled StepService receives `AUTH_SCHEME` and the typed
  `secretKeyRef` credential environment values, with no credential literal present

#### Scenario: OAuth2 is rejected for gRPC

- **WHEN** a `call: grpc` endpoint declares an `oauth2` policy
- **THEN** the controller rejects the workflow before deployment with a message
  that oauth2 is not supported for gRPC calls

### Requirement: The gRPC runner resolves the target method descriptor at startup

The `dws-call-grpc` image SHALL resolve the descriptor for the method named by
`METHOD` before serving. When `PROTO_ENDPOINT` is set it SHALL fetch a serialized
`FileDescriptorSet` once, verify it against `PROTO_SHA256` when provided, and parse
it. When `PROTO_ENDPOINT` is unset it SHALL resolve the method via the target's
gRPC server reflection service. A method that cannot be resolved, or that is a
streaming method, SHALL cause the process to exit non-zero at startup.

#### Scenario: Bundled descriptor set resolves the method

- **WHEN** `PROTO_ENDPOINT` serves a self-contained `FileDescriptorSet` containing
  the `METHOD` service and method
- **THEN** the runner resolves the method and becomes ready

#### Scenario: Reflection resolves the method when no descriptor is bundled

- **WHEN** `PROTO_ENDPOINT` is unset and the target exposes server reflection
- **THEN** the runner resolves the method via reflection and becomes ready

#### Scenario: Unresolvable or streaming method fails fast

- **WHEN** the descriptor source lacks the named method, or the method is a
  streaming method
- **THEN** the runner exits non-zero at startup instead of serving

### Requirement: The gRPC runner invokes the unary method and shapes the result

For each `Run` activity invocation the runner SHALL build the request message from
the current workflow data as protobuf JSON, invoke the unary method over gRPC using
h2c by default or TLS when `TLS=true`, and return the response shaped by `OUTPUT`.
`OUTPUT=replace` SHALL return the response object; `OUTPUT=merge` SHALL shallow-merge
it onto the input. A nil/empty input SHALL be treated as `{}`.

#### Scenario: Unary call returns the decoded response

- **WHEN** the workflow data maps onto the request message and the target returns
  an OK response
- **THEN** the runner returns the response as JSON per `OUTPUT`

#### Scenario: Plaintext h2c is the default transport

- **WHEN** `TLS` is unset or `false`
- **THEN** the runner dials the target over HTTP/2 cleartext

#### Scenario: Basic and bearer credentials are attached as metadata

- **WHEN** `AUTH_SCHEME` is `basic` or `bearer` with injected credentials
- **THEN** the runner attaches exactly one `authorization` metadata header of the
  corresponding scheme

### Requirement: The gRPC runner classifies failures for orchestrator retry

The runner SHALL classify a gRPC non-OK status and any transport failure as a
retryable upstream failure, and configuration, descriptor, encode, and decode
failures as non-retryable config failures, using the same activity failure-message
markers as `dws-call-http`.

#### Scenario: Upstream status is retryable

- **WHEN** the target returns a non-OK gRPC status
- **THEN** the activity fails with the `upstream failure` marker

#### Scenario: Configuration error is not retryable

- **WHEN** `AUTH_SCHEME=oauth2`, `SERVICE_ADDR`/`METHOD` is missing, or the response
  cannot be decoded
- **THEN** the activity fails with the `config failure` marker
