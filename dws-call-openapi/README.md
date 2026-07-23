# dws-call-openapi

Prebuilt step image for `call: openapi` tasks in the DWS platform. **One image
serves every openapi call step** — behavior is defined entirely by environment
configuration. It runs as a scale-to-zero Knative service with a Dapr sidecar
and is invoked by `dws-orchestrator` via Dapr service invocation.

At startup the step loads an OpenAPI document, verifies its SHA-256, dereferences
and validates it, and resolves one `OPERATION_ID` into a cached operation
template. Per request it binds parameters (evaluated from the input with jq),
validates them against the document schemas, calls the upstream operation, and
returns the result. Everything that can fail at startup fails fast; nothing is
lazy on the request path.

## Stack

- Node 24 (LTS), TypeScript (strict, ESM), pnpm
- [Fastify 5](https://fastify.dev/) — plain Fastify structured as `fastify-plugin`
  modules (`config`, `openapi`, `runner`)
- [`@readme/openapi-parser`](https://npm.im/@readme/openapi-parser) — load,
  dereference, and validate the OpenAPI 3.0/3.1 document
- [`openapi-client-axios`](https://npm.im/openapi-client-axios) — resolve
  `OPERATION_ID` and execute the request
- [`ajv`](https://ajv.js.org/) — validate bound parameters and request body
- [`node-jq`](https://npm.im/node-jq) — evaluate `PARAMETERS` jq expressions
- No Dapr SDK: secrets are fetched with a single HTTP GET to the sidecar

## HTTP contract

Identical to `dws-call-http`.

### `POST /run`

Request body is the current workflow data (a JSON object; an empty body is
treated as `{}`). The operation is executed and the response is shaped per
`OUTPUT`.

| Outcome | Status | Body |
|---|---|---|
| Success | `200` | `replace`: upstream body verbatim. `merge`: input shallow-merged with the upstream object. |
| Bad input body | `400` | `{ "task", "error" }` |
| Schema violation | `400` | `{ "task", "error", "details": [{ "location", "message" }] }` |
| Upstream non-2xx | `502` | `{ "task", "status", "body" }` — triggers the orchestrator retry policy |
| Transport failure (network, DNS, timeout) | `502` | `{ "task", "error" }` |
| Unexpected error | `500` | `{ "task", "error" }` |

### `GET /healthz`

Readiness probe. Returns `200 {"status":"ok","task":"<task>"}` **only after full
initialization** — the route is unreachable until every plugin has loaded.

## Configuration

All configuration is via environment variables.

| Variable | Required | Default | Description |
|---|---|---|---|
| `DOCUMENT_URL` | yes | — | OpenAPI document location. Scheme `http`, `https`, or `file`. |
| `DOCUMENT_SHA256` | yes | — | 64-char hex SHA-256 of the document bytes. Verified at startup. |
| `OPERATION_ID` | yes | — | The `operationId` to execute. |
| `PARAMETERS` | no | `{}` | JSON object mapping a parameter name (or `requestBody`) to a jq expression evaluated against the input data. |
| `AUTH_TYPE` | no | `none` | `none` \| `bearer` \| `basic` \| `apiKey`. |
| `AUTH_SECRET` | conditional | — | Inline secret value (used when auth is not `none`). |
| `AUTH_SECRET_STORE` | conditional | — | Dapr secret store name (alternative to `AUTH_SECRET`). |
| `AUTH_SECRET_KEY` | conditional | — | Key within the secret store (used with `AUTH_SECRET_STORE`). |
| `OUTPUT` | no | `replace` | `replace` \| `merge`. |
| `TIMEOUT` | no | `30s` | Request timeout. `30s`, `1m`, `500ms`, or a bare millisecond count. |
| `PORT` | no | `8080` | Listen port. |
| `TASK` | no | `OPERATION_ID` | Task/step name; appears in logs and error bodies, and is the Dapr `app-id`. |
| `DAPR_HTTP_PORT` | no | `3500` | Dapr sidecar HTTP port, used to fetch store-backed secrets. |
| `LOG_LEVEL` | no | `info` | pino log level. |

### `PARAMETERS`

Each value is a [jq](https://jqlang.github.io/jq/) expression evaluated against
the request body. Keys name operation parameters; the value is bound to that
parameter's location (`path`, `query`, `header`, `cookie`) as declared in the
document. The special key `requestBody` binds the evaluated value as the JSON
request body. A jq expression that matches nothing (yields `null`) is treated as
absent, so a required-parameter violation is reported rather than sending an
empty value.

```jsonc
// input: { "petId": 42, "pet": { "name": "Rex" } }
{
  "petId": ".petId",          // -> path/query/header param `petId`
  "requestBody": ".pet"       // -> JSON request body
}
```

### Authentication

- **bearer** — `Authorization: Bearer <secret>`
- **basic** — `secret` is raw `user:password`; sent as `Authorization: Basic <base64>`
- **apiKey** — the secret is injected into the header/query named by the
  document's `apiKey` security scheme (falling back to the `X-API-Key` header)

The secret is resolved once at startup: inline from `AUTH_SECRET`, or fetched
from the Dapr secret store via a single `GET
http://localhost:${DAPR_HTTP_PORT}/v1.0/secrets/{store}/{key}`.

## Local development

```bash
pnpm install
pnpm build
pnpm lint && pnpm test && pnpm build   # the CI gate

# Run against a local OpenAPI document.
export DOCUMENT_URL="file:///abs/path/to/openapi.json"
export DOCUMENT_SHA256="$(sha256sum /abs/path/to/openapi.json | cut -d' ' -f1)"
export OPERATION_ID="getPetById"
export PARAMETERS='{"petId":".petId"}'
pnpm start

curl -sX POST localhost:8080/run -H 'content-type: application/json' -d '{"petId":5}'
```

### With a Dapr sidecar

Store-backed secrets require the sidecar. Run the app under `dapr run` so
`http://localhost:3500/v1.0/secrets/...` is reachable:

```bash
export AUTH_TYPE=apiKey
export AUTH_SECRET_STORE=petstore-secrets
export AUTH_SECRET_KEY=api-key

dapr run \
  --app-id get-pet \
  --app-port 8080 \
  --dapr-http-port 3500 \
  --resources-path ./dapr/components \
  -- node dist/index.js
```

## Docker

Multi-stage build → `node:24-slim`, production dependencies only, non-root user:

```bash
docker build -t registry.io/dws/dws-call-openapi:1.0 .
docker run --rm -p 8080:8080 \
  -e DOCUMENT_URL="https://petstore3.swagger.io/api/v3/openapi.json" \
  -e DOCUMENT_SHA256="<digest>" \
  -e OPERATION_ID="getPetById" \
  -e PARAMETERS='{"petId":".petId"}' \
  registry.io/dws/dws-call-openapi:1.0
```

## Kubernetes

`k8s/knative-service.yaml` is an example Knative Service for the `getPetById`
step: scale-to-zero (`minScale: "0"`, with a comment on keeping warm replicas),
Dapr enabled, and `app-id` set from the task name. Each openapi call step reuses
this image and differs only in the env block and the Dapr `app-id`.

## Observability

Structured logs via pino (Fastify's default). One line per request with the
task, upstream status, and duration; a startup line records the resolved
operation, method, path, output mode, auth type, and timeout.
