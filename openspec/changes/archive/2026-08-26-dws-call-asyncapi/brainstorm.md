# OWS Phase 5 — `call: asyncapi` runner (`dws-call-asyncapi`)

## Background

Phase 4 (authentication + secrets) is complete. Phase 5 ("Protocol Expansion") adds the remaining
`call` protocols the DSL defines but DWS does not yet deploy: gRPC, AsyncAPI, and A2A. This change
delivers the **AsyncAPI** slice: a new prebuilt runner image `dws-call-asyncapi` plus the controller
compile branch and Dapr resource synthesis that deploy it.

This is an architectural change: it introduces a new independently-built component, a new
compile-time task→resource mapping, a new deployed Dapr resource kind (an output binding
`Component`), and a runtime dispatch path. It follows the shape of `dws-run` (new runner component)
and `workflow-auth` (new controller compile/synth branch + secret reuse).

## Decision chain

### Q1 — Stack: new pattern, or copy `dws-call-openapi`?

**Decision:** copy `dws-call-openapi` file-for-file. AsyncAPI is the message-broker counterpart of
OpenAPI, and `@asyncapi/parser` is the official, npm-published counterpart of the OpenAPI parser the
existing runner uses. The Fastify `app`/`index`/`routes`/`config` scaffold, the fetch + `DOC_SHA256`
integrity pin, the `node-jq` interpolation, and the `ajv` + `ajv-formats` single-compiled-validator
shape are all reused unchanged. `message.payload` is the same JSON-Schema dialect
`requestBodySchema` already validates, so validation is a direct reuse, not a new approach. Only the
outbound leg differs: instead of `swagger-client` → `undici` to an external URL, we build a local
Dapr output-binding call.

### Q2 — Runtime call target: Dapr Bindings or pub/sub?

**Decision:** Dapr's **output Bindings** building block —
`POST http://localhost:<DAPR_HTTP_PORT>/v1.0/bindings/<BINDING_NAME>` with
`{ "data": …, "operation": <OPERATION>, "metadata": {…} }`.

`call` is inherently a **single outbound dispatch** (AsyncAPI `action: send`); receiving / subscribing
is the DSL's `listen` task and is out of scope here. Dapr output bindings model send-only dispatch
directly and give one uniform HTTP contract across every broker, so the runner stays broker-agnostic
(the sidecar's binding Component holds all broker specifics). Pub/sub was rejected because it couples
the runner to CloudEvents envelope semantics and a topic-centric model that does not match a plain
AsyncAPI `send`.

**Coverage trade-off (accepted):** Dapr bindings exist only for a subset of AsyncAPI protocols.

| AsyncAPI `servers.*.protocol` | Dapr binding component | v1 support |
|---|---|---|
| `kafka` | `bindings.kafka` (stable) | ✅ |
| `amqp` | `bindings.rabbitmq` (stable) | ✅ |
| `mqtt` / `mqtt5` | `bindings.mqtt3` (beta, MQTT 3.1.1) | ✅ |
| `sqs` | `bindings.aws.sqs` (alpha) | ✅ |
| `googlepubsub` | `bindings.gcp.pubsub` (alpha) | ✅ |
| `redis` | `bindings.redis` (output-only) | deferred |
| `sns` | `bindings.aws.sns` (output-only) | deferred |
| `nats` / `pulsar` / `solace` | none | ❌ rejected at compile time |

### Q3 — How does a payload validation failure classify?

**Decision:** it must classify as `ErrorKind.VALIDATION` so `catch.errors.with.type` filtering
matches it, consistent with Phase 3's reconciliation to
`https://serverlessworkflow.io/spec/1.0.0/errors/validation`. The runner returns `400` with a
`{task, error, details}` body on a schema violation (identical to `dws-call-openapi`). The
orchestrator classifies from the **failure message**, not the exception type, via marker substrings
(`WorkflowErrors.classify()`), so the runner cannot influence classification through the status code
alone — a `400` today classifies as `COMMUNICATION`. We add a new `VALIDATION_MARKER`
(`"validation failed:"`) to `WorkflowErrors.classify()`, checked in the same guarded order as the
existing markers, and have the runner emit a validation error whose text carries that marker so it
survives the Dapr activity boundary. This keeps validation faults from falling through to a generic
communication/runtime failure the way an unclassified runner error would.

## Open questions — resolved

### 1. Exact env-var surface

Resolved. The controller pins the four broker-facing vars named in the handoff plus the document
pin, and the runner adds the same optional knobs `dws-call-openapi` has:

| Var | Req. | Default | Meaning |
|---|---|---|---|
| `DOC_ENDPOINT` | yes | — | AsyncAPI document location (`http`/`https`/`file`). Named per the Phase 5 handoff; intentionally distinct from OpenAPI's `DOCUMENT_URL`. |
| `DOC_SHA256` | yes | — | 64-hex integrity pin, verified at boot. |
| `OPERATION_ID` | yes | — | AsyncAPI 3.0 operation key (the map key under `operations`); its `action` must be `send`. |
| `BINDING_NAME` | yes | — | Dapr output-binding component name (the app's version-scoped Component). |
| `OPERATION` | no | `create` | Dapr binding operation verb; `create` is the publish verb for every supported binding. |
| `PAYLOAD` | no | `.` | A single jq expression evaluated against the input to build the message payload. Default `.` sends the whole workflow-data document. |
| `METADATA` | no | `{}` | JSON object of string values passed verbatim as the binding call's `metadata`. |
| `OUTPUT` | no | `replace` | `replace` \| `merge`, identical to the other runners. |
| `TIMEOUT` | no | `30s` | Request timeout. |
| `PORT`/`TASK`/`DAPR_HTTP_PORT`/`LOG_LEVEL` | no | as `dws-call-openapi` | Shared knobs. |

### 2. Unsupported brokers — hard-fail or silent skip?

Resolved: **hard-fail at compile time** with a clear error naming the unsupported protocol and the
supported set. A silently-skipped protocol would deploy a runner whose `BINDING_NAME` points at a
Component that was never synthesized — a start-time failure with no actionable message. Failing at
`POST` time with `task '<name>': AsyncAPI server protocol 'nats' has no supported Dapr binding
(supported: kafka, amqp, mqtt, mqtt5, sqs, googlepubsub)` is the DWS convention (`run: container`,
`run: workflow`, unsupported script languages all reject the same way).

### 3. Metadata mapping from the AsyncAPI document

Resolved for v1: **no automatic mapping** of AsyncAPI protocol-binding extensions
(`operations.*.bindings.*`, `channels.*.bindings.*`) into the Dapr call's `metadata`. The channel
**address** flows into the binding **Component** metadata (the destination — e.g. Kafka
`publishTopic`, RabbitMQ `queueName`, MQTT `topic`), pinned by the controller. Per-request `metadata`
is author-controlled through the optional `METADATA` env var and passed through verbatim. Auto-deriving
Dapr metadata from AsyncAPI `bindings.*` extensions is deferred until a concrete broker binding needs
a field beyond the destination; the hook (`METADATA`) is in place so adding it later is additive.

## Approved design (summary)

- New `dws-call-asyncapi` component mirrors `dws-call-openapi`'s file layout, contract, Dockerfile,
  and CI gate (`pnpm lint && pnpm test && pnpm build`).
- Outbound dispatch is a local Dapr output-binding POST; the runner never talks to the broker
  directly and holds no broker credentials.
- The controller adds a `call: asyncapi` branch: light document read (servers protocol/host + channel
  address), protocol→binding-type table, version-scoped binding `Component` synthesis with
  secret-backed credentials via `use.secrets`, `BINDING_NAME`/`OPERATION`/`DOC_*`/`OPERATION_ID`
  env pinning, and unsupported-protocol rejection.
- The orchestrator gains one `VALIDATION_MARKER` so a runner payload-validation `400` classifies as
  `ErrorKind.VALIDATION`.

## Explicit deferrals

- `redis`/`sns` output bindings (output-only, straightforward to add later via the same table).
- `nats`/`pulsar`/`solace` — would need the pub/sub fallback discussed but not built in v1.
- Automatic AsyncAPI `bindings.*` extension → Dapr `metadata` mapping.
- Receiving/subscribing (`listen`), gRPC, and A2A — separate Phase 5 slices.
