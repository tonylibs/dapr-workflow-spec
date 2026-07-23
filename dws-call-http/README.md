# dws-call-http

Generic, prebuilt step image for `call: http` tasks in the DWS platform.

**One image serves every HTTP call step.** Behavior is defined entirely by
environment configuration — no per-step code or rebuild. The image is deployed
as a [Knative](https://knative.dev/) service (scale-to-zero) with a
[Dapr](https://dapr.io/) sidecar, and is invoked by `dws-orchestrator` via Dapr
service invocation.

## How it works

The orchestrator POSTs the current workflow data (a JSON object) to `/run`. The
step interpolates that data into the configured outbound request, calls the
upstream service, and returns the result shaped per the `OUTPUT` mode. A non-2xx
upstream response is surfaced as a `502` so the orchestrator's retry policy
triggers.

## Routes

| Method | Path       | Purpose                                                        |
| ------ | ---------- | ------------------------------------------------------------- |
| `POST` | `/run`     | Step entrypoint. Body = workflow data (JSON). Runs the call.  |
| `GET`  | `/healthz` | Liveness / readiness probe.                                   |

### `POST /run`

- **Request body**: the current workflow data as a JSON object. An empty body is
  treated as `{}`.
- **Success (`200`)**: the response JSON, shaped by `OUTPUT`.
- **Upstream non-2xx (`502`)**: `{"task": <task>, "status": <upstreamStatus>, "body": <upstreamBody>}`.
- **Transport failure (`502`)**: `{"task": <task>, "error": <detail>}` (connection refused, timeout, DNS).
- **Bad request body (`400`)** / **config or decode error (`500`)**: `{"task": <task>, "error": <detail>}`.

`502` is chosen for upstream and transport failures specifically so the
orchestrator retry policy re-invokes the step; `400`/`500` are used where a retry
would not help.

## Placeholder interpolation

`ENDPOINT`, `QUERY` values, and `BODY_TEMPLATE` support `{placeholder}` tokens.
Each token is replaced with the matching **top-level key** from the input JSON.

- Strings are substituted as-is.
- JSON numbers render without a trailing `.0` when integral (`42`, not `42.0`).
- Booleans render as `true` / `false`.
- A missing key is a hard error (the call is not made).

Example: `ENDPOINT=https://svc/orders/{orderId}` with input `{"orderId": 42}`
calls `https://svc/orders/42`.

## Environment variables

| Var                    | Required | Default       | Description                                                                                   |
| ---------------------- | -------- | ------------- | --------------------------------------------------------------------------------------------- |
| `PORT`                 | no       | `8080`        | HTTP listen port.                                                                             |
| `TASK`                 | no       | `call-http`   | Task/step name. Appears in error bodies and logs; set to the Dapr app-id.                     |
| `ENDPOINT`             | **yes**  | —             | Target URL. Supports `{placeholders}`. e.g. `https://svc/orders/{orderId}`.                    |
| `METHOD`               | no       | `POST`        | HTTP method (case-insensitive; uppercased internally).                                        |
| `HEADERS`              | no       | —             | JSON object of static headers. e.g. `{"X-Api-Key":"secret"}`.                                 |
| `QUERY`                | no       | —             | JSON object of query params; values may use `{placeholders}`. e.g. `{"sku":"{sku}"}`.         |
| `BODY_MODE`            | no       | `passthrough` | `passthrough` (forward input as body), `none` (no body), or `template` (render `BODY_TEMPLATE`). |
| `BODY_TEMPLATE`        | cond.    | —             | Required when `BODY_MODE=template`. String body with `{placeholders}`.                         |
| `OUTPUT`               | no       | `replace`     | `replace` (respond with upstream body) or `merge` (shallow-merge upstream object into input). |
| `TIMEOUT`              | no       | `30s`         | Go duration for the whole outbound call. e.g. `5s`, `1m`. Must be positive.                    |
| `INSECURE_SKIP_VERIFY` | no       | `false`       | Skip upstream TLS verification. Use only for trusted internal/test targets.                    |

Invalid config (missing `ENDPOINT`, malformed `HEADERS`/`QUERY` JSON, unknown
`BODY_MODE`/`OUTPUT`, bad `TIMEOUT`/`INSECURE_SKIP_VERIFY`, or `template` mode
without `BODY_TEMPLATE`) causes a **non-zero exit at startup** with a clear
message.

### Body modes

- **`passthrough`** (default): the input JSON is forwarded verbatim as the
  request body with `Content-Type: application/json`.
- **`none`**: no request body is sent (typical for `GET`).
- **`template`**: `BODY_TEMPLATE` is interpolated with `{placeholders}` and sent
  as the body with `Content-Type: application/json`.

### Output modes

- **`replace`** (default): respond with the upstream body as-is (object, array,
  or scalar). An empty upstream body yields `{}`.
- **`merge`**: shallow-merge the upstream **object** into the input data and
  respond with the merged object. Upstream keys win on conflict.

## Examples

### `check-inventory` (GET, replace)

```sh
TASK=check-inventory \
METHOD=get \
ENDPOINT=https://inventory-svc/check \
OUTPUT=replace \
./dws-call-http
```

Request:

```sh
curl -sX POST localhost:8080/run -d '{"sku":"ABC-1"}'
# -> upstream response body, e.g. {"available":true,"onHand":12}
```

### Create order (POST passthrough, merge)

```sh
TASK=create-order \
METHOD=post \
ENDPOINT=https://orders-svc/orders \
OUTPUT=merge \
./dws-call-http
```

```sh
curl -sX POST localhost:8080/run -d '{"customerId":"c1","sku":"ABC-1"}'
# upstream returns {"orderId":"o-77","status":"created"}
# -> {"customerId":"c1","sku":"ABC-1","orderId":"o-77","status":"created"}
```

### Templated body with headers and query

```sh
TASK=notify \
METHOD=post \
ENDPOINT=https://notify-svc/send \
HEADERS='{"X-Api-Key":"secret"}' \
QUERY='{"channel":"{channel}"}' \
BODY_MODE=template \
BODY_TEMPLATE='{"to":"{userId}","text":"Order {orderId} shipped"}' \
./dws-call-http
```

## Development

```sh
make build   # compile bin/dws-call-http
make test    # go test -race ./...
make vet     # go vet ./...
make lint    # vet + gofmt check (+ golangci-lint if installed)
make docker  # build registry.io/dws/dws-call-http:1.0
```

Build gate:

```sh
go vet ./... && go test ./...
```

## Deployment

See [`k8s/knative-service.yaml`](k8s/knative-service.yaml) for an example
Knative Service (the `check-inventory` step). Key points:

- `image: registry.io/dws/dws-call-http:1.0`
- `dapr.io/enabled: "true"` and `dapr.io/app-id` set to the **task name**.
- `minScale: "0"` for scale-to-zero.
- Step behavior configured entirely through the `env` block.

To deploy another step, copy the manifest and change `metadata.name`, the Dapr
`app-id`, the `TASK` env, and the call configuration.
