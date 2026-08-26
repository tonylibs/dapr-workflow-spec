## Context

DWS compiles Open Workflow definitions into immutable, content-addressed workflow versions and one
deployed step service per I/O task. `call: http` and `call: openapi` already have prebuilt runner
images; Phase 5 adds `call: asyncapi`. The runner mirrors `dws-call-openapi` (Node/TypeScript,
Fastify, `ajv`, `node-jq`) and differs only in its outbound leg, which becomes a local Dapr output
binding call instead of an external HTTP request. The controller gains a compile branch that reads
the AsyncAPI document lightly, selects a Dapr binding type from the server protocol, and synthesizes
a version-scoped binding `Component`. Secret handling reuses Phase 4's `use.secrets` /
`secretKeyRef` machinery verbatim.

## Goals / Non-Goals

**Goals:**

- A generic, env-configured `dws-call-asyncapi` image that dispatches one AsyncAPI `send` operation
  per request through the local Dapr sidecar's output binding.
- Payload schema validation (`message.payload`) before dispatch, classified as `VALIDATION`.
- A controller compile branch that pins the document, selects the binding type, synthesizes the
  binding Component with secret-backed credentials, and rejects unsupported protocols.
- Preserve every cross-component invariant (task-name→app-id, content-addressed versioning, the
  shared `POST /run` + `GET /healthz` + `502` step contract).

**Non-Goals:**

- Receiving/subscribing (`listen`), gRPC, A2A.
- `redis`/`sns` bindings and `nats`/`pulsar`/`solace` protocols in v1.
- Automatic AsyncAPI `bindings.*` extension → Dapr `metadata` derivation.
- Any change to the existing runners or to unrelated DSL behavior.

## Decisions

### D1: Runner mirrors `dws-call-openapi`

- **Choice:** copy the file layout (`app.ts`/`index.ts`/`routes.ts`/`config/config.ts`,
  `plugins/{config,asyncapi,runner}.ts`, `jq.ts`, an `asyncapi/{document,operation,validator,engine}.ts`
  set, and a `binding.ts` in place of `request.ts`). Same fail-fast startup, same `POST /run` +
  `GET /healthz` contract, same `OUTPUT=replace|merge`, same `502`-on-transport rule.
- **Rationale:** minimizes new surface and keeps the runner family visually and operationally
  identical. `message.payload` is the same JSON-Schema dialect as `requestBodySchema`, so the `ajv`
  validator (`allErrors`, `coerceTypes: false`) is reused directly.
- **Alternatives considered:** a Go runner (rejected — no official Go AsyncAPI parser of comparable
  fidelity, and it would not share the OpenAPI runner's validation code path).

### D2: Operation resolution reads a plain document object

- **Choice:** `document.ts` fetches, verifies `DOC_SHA256`, and parses with `@asyncapi/parser`
  (failing fast on parser diagnostics of error severity), then exposes the document as a plain
  resolved JSON object. `operation.ts` resolves `OPERATION_ID` against that plain object: it looks
  up `operations[OPERATION_ID]`, requires `action: send`, resolves the operation's `channel.$ref`
  JSON pointer to a channel address, and resolves the operation message (its first `messages[].$ref`,
  or the channel's sole message) to a `payload` schema.
- **Rationale:** operating on a plain object keeps `operation.ts`/`validator.ts` pure and unit-testable
  from JSON fixtures — exactly how `dws-call-openapi`'s `resolveOperation(api: object, …)` is tested —
  while the parser still enforces document validity at boot.
- **Alternatives considered:** driving everything through the parser's intent API (`document.operations()`)
  was rejected because it couples every unit test to the parser runtime and its version-specific model.

### D3: Outbound leg is a local Dapr output binding

- **Choice:** `binding.ts` builds
  `POST http://localhost:<DAPR_HTTP_PORT>/v1.0/bindings/<BINDING_NAME>` with body
  `{ "data": <payload>, "operation": <OPERATION>, "metadata": <METADATA> }` and executes it with
  `undici`. The payload is the `PAYLOAD` jq expression evaluated against the input and validated by
  the `ajv` validator **before** the POST. A non-2xx sidecar response is an `UpstreamError` (`502`);
  a transport failure is a `TransportError` (`502`), preserving the orchestrator's retry contract.
- **Rationale:** the sidecar owns all broker specifics; the runner is broker-agnostic and holds no
  credentials. `create` is the publish verb for every supported binding, so `OPERATION` defaults to
  it.
- **Alternatives considered:** pub/sub publish (rejected in brainstorm Q2); a broker-specific SDK per
  protocol (rejected — defeats the one-image-per-task-kind design).

### D4: Payload-validation failures classify as `VALIDATION`

- **Choice:** add `VALIDATION_MARKER = "validation failed:"` to `WorkflowErrors.classify()`, checked
  after `TIMEOUT`/`DATA_FLOW` and before `CONFIG`/`STEP`, mapping to `ErrorKind.VALIDATION`. The
  runner's schema-violation error message embeds that marker so it survives the Dapr activity
  boundary and the HTTP `step '<app>' failed with status 400: …` wrapper.
- **Rationale:** the DSL's `catch.errors.with.type` filters on the error `type` URI; a payload
  validation fault is semantically a `validation` error, not a `communication` one. Classification
  reads the message (the only detail that crosses the activity boundary), so a marker is the
  established mechanism (`DATA_FLOW_MARKER`, `CONFIG_MARKER`, `TIMEOUT_MARKER`).
- **Alternatives considered:** classifying by HTTP status (rejected — the `400` is not visible as a
  distinct signal; `statusOf` already special-cases only `COMMUNICATION`, and other `4xx`/`5xx`
  runner faults legitimately stay communication errors).

### D5: Controller selects the binding type from `servers.*.protocol`

- **Choice:** the compile branch does a **light** JSON read (not full `@asyncapi/parser` parsing) of
  the fetched document: the first server's `protocol` and `host`, and the operation's channel
  `address`. It maps protocol→Dapr binding `type` via the brainstorm Q2 table, rejects unsupported
  protocols, and synthesizes one version-scoped Dapr binding `Component` named
  `<workflow>-<versionId>-binding-<hash>` scoped to the step app-id. Component metadata carries the
  connection host (binding-specific key) and the destination from the channel address (binding-specific
  key), plus secret-backed auth metadata for any credential referencing a declared `use.secrets` name.
- **Rationale:** mirrors how `workflow-auth` reads the OpenAPI document lightly for the OAuth server
  and synthesizes a version-scoped Component; keeps the controller free of a heavyweight AsyncAPI
  runtime dependency; keeps the binding Component immutable and label-GC'able like every other
  version-scoped resource.
- **Alternatives considered:** delegating binding-type selection to the runner at startup (rejected —
  the Component must exist before the runner starts, and only the controller synthesizes Dapr
  resources); a shared cluster-wide binding Component (rejected — breaks per-version isolation and
  GC).

### D6: Secrets reuse Phase 4 verbatim

- **Choice:** broker credentials in the AsyncAPI document/`use.secrets` are projected as Dapr
  `Component` `secretKeyRef` metadata exactly as the OAuth2 middleware Component does. The controller
  never reads secret values; missing Secrets fail at workload start, documented as an operator
  prerequisite.
- **Rationale:** identical trust boundary and RBAC story as Phase 4; no new secret machinery.

## Risks / Trade-offs

- **[Binding coverage]** Only a subset of AsyncAPI protocols map to a Dapr binding →
  Mitigation: explicit compile-time rejection with a clear supported-set message; deferrals
  documented.
- **[MQTT 3.1.1 only]** `bindings.mqtt3` does not cover MQTT 5 features → Mitigation: accept for v1;
  `mqtt5` still routes to `mqtt3` and is documented as best-effort.
- **[Alpha bindings]** `sqs`/`gcp.pubsub` are alpha in Dapr → Mitigation: supported but flagged; the
  runner contract is unaffected by the binding's stability tier.
- **[Missing Secret]** Broker credential Secrets must be pre-created by an operator → Mitigation:
  document the prerequisite; do not widen controller RBAC.
- **[Destination metadata drift]** The channel address→binding-metadata key differs per broker →
  Mitigation: a single mapping table in the compiler, unit-tested per supported protocol.

## Migration Plan

1. Mark Phase 5's AsyncAPI slice underway in the roadmap.
2. Build and unit-test the `dws-call-asyncapi` runner (document/operation/validator/binding) before
   any controller wiring; its CI gate is independent.
3. Add the controller compile branch, `BindingComponent` model, and `StackSynthesizer` binding
   synthesis with unit tests; add the orchestrator `VALIDATION_MARKER`.
4. Validate end-to-end against a real Dapr sidecar + Kafka binding in an integration test; honest
   FAIL where live infra (Docker/Kafka/Dapr) is unavailable in this environment.
5. Roll back by deleting the affected workflow version — its binding Component, Knative Service, and
   orchestrator are label-scoped and GC together. Existing definitions are unaffected.

## Open Questions

- None blocking. `OPERATION` defaults to `create` for all supported bindings; if a future binding
  needs a different publish verb the controller can override the pinned `OPERATION` from the table.
