# asyncapi-step-execution

## Purpose

The `dws-call-asyncapi` runner's runtime obligations: fetching and integrity-pinning an AsyncAPI
document, resolving a `send` operation to a channel address and message `payload` schema, validating
the jq-interpolated payload, and dispatching it through the local Dapr output binding under the
shared step-service HTTP contract. Established in `dws-call-asyncapi` (OWS DSL roadmap Phase 5,
AsyncAPI slice).

## Requirements

### Requirement: The AsyncAPI runner pins and resolves its document at startup

The `dws-call-asyncapi` runner SHALL fetch the AsyncAPI document from `DOC_ENDPOINT` (scheme `http`,
`https`, or `file`), verify its bytes against the 64-character hex `DOC_SHA256`, parse it with
`@asyncapi/parser`, and resolve `OPERATION_ID` to a channel address and the operation message's
`payload` schema. Any missing required configuration, hash mismatch, parser error, or unresolvable
operation SHALL fail startup with a non-zero exit before the server accepts requests.

#### Scenario: Valid document and operation initialize
- **WHEN** `DOC_ENDPOINT`, `DOC_SHA256`, `OPERATION_ID`, and `BINDING_NAME` are set and the document
  hashes correctly and declares a `send` operation
- **THEN** the runner initializes, `GET /healthz` returns `200`, and the resolved channel address and
  payload schema are cached for the request path

#### Scenario: Hash mismatch fails fast
- **WHEN** the fetched document does not match `DOC_SHA256`
- **THEN** the runner exits non-zero and never serves `/healthz`

#### Scenario: Non-send operation is rejected
- **WHEN** `OPERATION_ID` resolves to an operation whose `action` is not `send`
- **THEN** the runner exits non-zero with a message naming the operation and its action

### Requirement: The AsyncAPI runner validates the payload before dispatch

The runner SHALL build the message payload by evaluating the `PAYLOAD` jq expression (default `.`)
against the request body, validate it against the resolved `message.payload` schema with a single
compiled `ajv` validator, and dispatch only when validation passes. On a schema violation it SHALL
respond `400` with `{ task, error, details }` and the error text SHALL carry a marker that classifies
the failure as a validation error.

#### Scenario: Valid payload is dispatched
- **WHEN** the evaluated payload satisfies the message schema
- **THEN** the runner proceeds to the Dapr binding dispatch

#### Scenario: Invalid payload returns 400 with details
- **WHEN** the evaluated payload violates the message schema
- **THEN** the runner responds `400` with per-violation `details` and does not dispatch to the sidecar

### Requirement: The AsyncAPI runner dispatches through a Dapr output binding

The runner SHALL dispatch by `POST`ing `http://localhost:<DAPR_HTTP_PORT>/v1.0/bindings/<BINDING_NAME>`
with body `{ "data": <validated payload>, "operation": <OPERATION>, "metadata": <METADATA> }`, where
`OPERATION` defaults to `create` and `METADATA` (default `{}`) is passed verbatim. It SHALL honor the
shared step-service contract: `POST /run` (empty body treated as `{}`), `GET /healthz`,
`OUTPUT=replace|merge` response shaping, and `502` for a non-2xx sidecar response or a transport
failure so the orchestrator retry policy re-invokes the step.

#### Scenario: Successful dispatch shapes output
- **WHEN** the sidecar returns a 2xx response and `OUTPUT=merge`
- **THEN** the runner returns the request input shallow-merged with the sidecar's JSON response

#### Scenario: Sidecar failure maps to 502
- **WHEN** the sidecar returns a non-2xx status or the request fails to connect
- **THEN** the runner responds `502` so the step is retried

#### Scenario: Empty request body is treated as empty data
- **WHEN** `POST /run` is called with an empty body
- **THEN** the runner evaluates `PAYLOAD` against `{}` rather than failing to parse
