## Why

DWS supports `call: http` and `call: openapi` I/O tasks but has no way to dispatch a message to an
event broker from a workflow. The Open Workflow Specification DSL 1.0 defines `call: asyncapi` for
exactly this, and `docs/roadmaps/openworkflow-features.md` Phase 5 (Protocol Expansion) schedules
it as a new prebuilt runner image. Without it, an author who wants a step to publish to Kafka, an
AMQP queue, MQTT, SQS, or Google Pub/Sub has to fall back to a hand-written `call: http` shim
against a broker's REST proxy — losing the AsyncAPI contract, its payload schema, and version
pinning.

## What Changes

- Add `dws-call-asyncapi`, a new prebuilt Node/TypeScript runner image mirroring `dws-call-openapi`
  file-for-file: a Fastify server exposing `POST /run` + `GET /healthz`, fail-fast env-driven
  configuration, an AsyncAPI document fetched from `DOC_ENDPOINT` and integrity-pinned by
  `DOC_SHA256`, parsed with `@asyncapi/parser`, resolving `OPERATION_ID` to a channel address and
  the operation message's `payload` schema, one compiled `ajv` validator, and `node-jq` payload
  interpolation against the workflow data document.
- Dispatch through Dapr's **output Bindings** building block, not pub/sub: the runner `POST`s
  `http://localhost:<DAPR_HTTP_PORT>/v1.0/bindings/<BINDING_NAME>` with
  `{ "data": <validated, interpolated payload>, "operation": <OPERATION>, "metadata": {…} }`. The
  payload is validated with the `ajv` validator **before** the outbound POST.
- Extend `dws-controller` with a `call: asyncapi` compile branch: a light read of the AsyncAPI
  document's `servers.*.protocol`/`host` and the operation's channel address, a protocol→Dapr
  binding-type decision, synthesis of a version-scoped Dapr binding `Component` (connection metadata
  plus secret-backed credentials reusing Phase 4's `use.secrets` machinery), and pinning of
  `DOC_ENDPOINT` + `DOC_SHA256` + `OPERATION_ID` + `BINDING_NAME` + `OPERATION` as the step's env.
- Classify a runner payload-validation failure as `ErrorKind.VALIDATION` in `dws-orchestrator` so
  `catch.errors.with.type` filtering matches it, using the same marker convention as the existing
  `DATA_FLOW_MARKER`/`CONFIG_MARKER`/`STEP_MARKER`/`TIMEOUT_MARKER` classifiers.
- Add a path-filtered CI workflow `.github/workflows/dws-call-asyncapi.yml` mirroring the other
  runners' gates, and mark Phase 5's AsyncAPI slice underway in the roadmap.

## Capabilities

### New Capabilities

- `asyncapi-step-execution`: The `dws-call-asyncapi` runner's request-time behavior — document
  integrity pinning, operation resolution, payload validation, jq interpolation, and Dapr output
  binding dispatch with the shared step-service HTTP contract.
- `asyncapi-step-compilation`: The controller's compile-time behavior for `call: asyncapi` —
  protocol→binding-type selection, version-scoped Dapr binding Component synthesis, secret-backed
  credentials, unsupported-protocol rejection, and step env pinning.

### Modified Capabilities

- `workflow-error-handling`: A step-service payload-validation failure classifies as
  `ErrorKind.VALIDATION` rather than a generic communication/runtime failure.

## Impact

Affected components are a new `dws-call-asyncapi` runner, `dws-controller` (a new compile branch,
model, and Dapr binding Component synthesis), and `dws-orchestrator` (one added error marker).
Supported AsyncAPI server protocols in v1 are `kafka`, `amqp`, `mqtt`/`mqtt5`, `sqs`, and
`googlepubsub`; `redis`/`sns` are output-only and deferred; `nats`/`pulsar`/`solace` have no Dapr
binding component and are rejected at compile time. Existing definitions without `call: asyncapi`
compile and run unchanged; no existing runner or DSL behavior is altered.
