# dws-call-asyncapi

Prebuilt step image for `call: asyncapi` tasks in the DWS platform. **One image
serves every asyncapi call step** — behavior is defined entirely by environment
configuration. It runs as a scale-to-zero Knative service with a Dapr sidecar
and is invoked by `dws-orchestrator` via Dapr service invocation.

At startup the step fetches an AsyncAPI document, verifies its SHA-256, validates
it with [`@asyncapi/parser`](https://npm.im/@asyncapi/parser), and resolves one
`OPERATION_ID` (which must be a `send` operation) into a channel address and the
operation message's `payload` schema. Per request it evaluates `PAYLOAD` with jq
to build the message, validates it against the payload schema, and dispatches it
through the local Dapr **output binding** — a single `POST` to
`/v1.0/bindings/<BINDING_NAME>`. Everything that can fail at startup fails fast;
nothing is lazy on the request path.

`call: asyncapi` is an outbound `send` dispatch only; receiving/subscribing is the
DSL's `listen` task and is out of scope here. Dapr's output Bindings building
block models send-only dispatch directly, so the runner stays broker-agnostic —
the sidecar's binding Component (synthesized by `dws-controller`) holds every
broker specific and the runner holds no broker credentials.

### Request pipeline

```
input JSON ──jq(PAYLOAD)──▶ message payload
           ──ajv validate (message.payload)──▶ (400 on violation)
           ──POST /v1.0/bindings/<BINDING_NAME>
              { data, operation, metadata }──▶ Dapr sidecar
           ──OUTPUT replace|merge──▶ response
```

## Stack

- Node 24 (LTS), TypeScript (strict, ESM), pnpm
- [Fastify 5](https://fastify.dev/) — plain Fastify structured as `fastify-plugin`
  modules (`config`, `asyncapi`, `runner`)
- [`@asyncapi/parser`](https://npm.im/@asyncapi/parser) — validate the AsyncAPI
  3.0 document (the AsyncAPI-side counterpart of the OpenAPI parser used by
  `dws-call-openapi`)
- [`undici`](https://undici.nodejs.org/) — executes the Dapr binding call
- [`ajv`](https://ajv.js.org/) — validate the message payload (same shape
  `dws-call-openapi` validates a request body with)
- [`node-jq`](https://npm.im/node-jq) — evaluate the `PAYLOAD` jq expression

## HTTP contract

Identical to `dws-call-http` / `dws-call-openapi`.

### `POST /run`

Request body is the current workflow data (a JSON object; an empty body is
treated as `{}`). The message is dispatched and the response is shaped per
`OUTPUT`.

| Outcome | Status | Body |
|---|---|---|
| Success | `200` | `replace`: sidecar body verbatim. `merge`: input shallow-merged with the sidecar object. |
| Bad input body | `400` | `{ "task", "error" }` |
| Payload schema violation | `400` | `{ "task", "error", "details": [{ "location", "message" }] }` — `error` opens with `validation failed:` |
| Sidecar non-2xx | `502` | `{ "task", "status", "body" }` — triggers the orchestrator retry policy |
| Transport failure (network, timeout) | `502` | `{ "task", "error" }` |
| Unexpected error | `500` | `{ "task", "error" }` |

### `GET /healthz`

Readiness probe. Returns `200 {"status":"ok","task":"<task>"}` **only after full
initialization** — the route is unreachable until every plugin has loaded.

## Configuration

All configuration is via environment variables.

| Variable | Required | Default | Description |
|---|---|---|---|
| `DOC_ENDPOINT` | yes | — | AsyncAPI document location. Scheme `http`, `https`, or `file`. |
| `DOC_SHA256` | yes | — | 64-char hex SHA-256 of the document bytes. Verified at startup. |
| `OPERATION_ID` | yes | — | The AsyncAPI 3.0 operation key to dispatch; its `action` must be `send`. |
| `BINDING_NAME` | yes | — | Dapr output-binding component name the sidecar dispatches through. |
| `OPERATION` | no | `create` | Dapr binding operation verb (`create` is the publish verb for every supported binding). |
| `PAYLOAD` | no | `.` | A single jq expression evaluated against the input to build the message payload. The default sends the whole workflow-data document. |
| `METADATA` | no | `{}` | JSON object of string values passed verbatim as the binding call's `metadata`. |
| `OUTPUT` | no | `replace` | `replace` \| `merge`. |
| `TIMEOUT` | no | `30s` | Request timeout. `30s`, `1m`, `500ms`, or a bare millisecond count. |
| `PORT` | no | `8080` | Listen port. |
| `TASK` | no | `OPERATION_ID` | Task/step name; appears in logs and error bodies, and is the Dapr `app-id`. |
| `DAPR_HTTP_PORT` | no | `3500` | Dapr sidecar HTTP port for the binding call. |
| `LOG_LEVEL` | no | `info` | pino log level. |

### Supported broker protocols

The controller selects the Dapr binding component type from the AsyncAPI server
`protocol`:

| AsyncAPI `servers.*.protocol` | Dapr binding | v1 |
|---|---|---|
| `kafka` | `bindings.kafka` | ✅ |
| `amqp` | `bindings.rabbitmq` | ✅ |
| `mqtt` / `mqtt5` | `bindings.mqtt3` | ✅ |
| `sqs` | `bindings.aws.sqs` | ✅ |
| `googlepubsub` | `bindings.gcp.pubsub` | ✅ |
| `redis` / `sns` | output-only | deferred |
| `nats` / `pulsar` / `solace` | — | rejected at compile time |

## Local development

```bash
pnpm install
pnpm lint && pnpm test && pnpm build   # the CI gate

# Run against a local AsyncAPI document (needs a Dapr sidecar for a real dispatch).
export DOC_ENDPOINT="file:///abs/path/to/asyncapi.json"
export DOC_SHA256="$(sha256sum /abs/path/to/asyncapi.json | cut -d' ' -f1)"
export OPERATION_ID="publishOrder"
export BINDING_NAME="orders-binding"
pnpm start
```

### With a Dapr sidecar

```bash
dapr run \
  --app-id publish-order \
  --app-port 8080 \
  --dapr-http-port 3500 \
  --resources-path ./dapr/components \
  -- node dist/index.js

curl -sX POST localhost:8080/run -H 'content-type: application/json' \
  -d '{"orderId":"o1","amount":5}'
```

## Docker

Multi-stage build → `node:24-slim`, production dependencies only, non-root user:

```bash
docker build -t registry.io/dws/dws-call-asyncapi:1.0 .
```

## Kubernetes

`k8s/knative-service.yaml` is an example Knative Service for one `publish-order`
step: scale-to-zero, Dapr enabled, and `app-id` set from the task name. Each
asyncapi call step reuses this image and differs only in the env block and the
Dapr `app-id`.
