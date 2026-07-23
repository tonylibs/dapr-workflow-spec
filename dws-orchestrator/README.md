# dws-orchestrator

A generic, **config-driven** Dapr workflow orchestrator built on the **interpreter pattern**.

It ships as the prebuilt image **`sw-orchestrator`** and executes [Serverless Workflow](https://serverlessworkflow.io/) DSL 1.0–flavoured definitions supplied as `workflow.json` configuration. **No per-workflow code is ever generated** — one registered workflow (`InterpreterWorkflow`) interprets any number of definitions as a program-counter loop.

- **Java 25**, Spring Boot 4.1.0 (`web`, `actuator`)
- **Dapr** workflow runtime + client (`dapr-sdk-workflows`, `dapr-sdk`, BOM 1.18.0)
- **jackson-jq** for `jq`-dialect runtime expressions
- Base package `io.dws.orchestrator`

---

## How it works

1. **On startup**, all `*.json` definitions under `DWS_DEFINITIONS_PATH` (default `/etc/dws/definitions`, typically a mounted ConfigMap) are parsed into a `WorkflowDef` model and registered by `name` + `version`.
2. A single `InterpreterWorkflow` is registered with `WorkflowRuntimeBuilder`. It runs a **program-counter loop** over the definition version **pinned in the instance input**, so replays stay deterministic:
   | Task type | Behaviour |
   |-----------|-----------|
   | `call`   | `ctx.callActivity(CallServiceActivity)` with the default retry policy |
   | `switch` | pure `jq` evaluation in-workflow (no I/O) |
   | `set`    | pure `jq` evaluation in-workflow (no I/O) |
   | `wait`   | `ctx.createTimer` |
   | `listen` | `ctx.waitForExternalEvent` |
   | `emit`   | activity that publishes via Dapr pub/sub |
   | `end`    | `ctx.complete` |
   | `for` / `try` | recognised by the parser; interpretation not yet implemented |
3. **`CallServiceActivity`** is the single I/O activity: it invokes the target Knative service by its Dapr **app-id** (service invocation, `POST /<path>`, default `/run`), passing the current data document as JSON and using the response as the new data document.

---

## Definition JSON schema

A definition is a JSON document:

```jsonc
{
  "document": {                 // metadata
    "dsl": "1.0.0",
    "namespace": "examples",
    "name": "order-workflow",   // required — logical workflow name
    "version": "1.0.0"          // required — pinned at instance start
  },
  "start": "checkInventory",    // required — entry task name
  "tasks": {                    // required — name -> task
    "checkInventory": { "type": "call", "service": "inventory-service", "path": "run", "then": "decide" },
    "decide": {
      "type": "switch",
      "cases": [ { "when": "${ .inStock }", "then": "chargePayment" } ],
      "default": "notifyOutOfStock"
    },
    "chargePayment":    { "type": "call", "service": "payment-service", "then": "orderPlaced" },
    "notifyOutOfStock": { "type": "call", "service": "notification-service", "then": "orderRejected" },
    "orderPlaced":   { "type": "end" },
    "orderRejected": { "type": "end" }
  }
}
```

### Task fields

| Field | Applies to | Meaning |
|-------|-----------|---------|
| `type` | all | `call` \| `switch` \| `set` \| `wait` \| `listen` \| `emit` \| `for` \| `try` \| `end` |
| `then` / `next` | all (except `switch`, `end`) | next task name, or `end` to terminate |
| `service` | `call` | target Dapr app-id |
| `path` | `call` | method path (default `run`) |
| `cases` | `switch` | ordered `[{ "when": <jq>, "then": <task> }]`; first truthy wins |
| `default` | `switch` | fallback task when no case matches |
| `set` | `set` | `{ field: <jq expr> }` merged over the current data |
| `duration` | `wait` | ISO-8601 duration (e.g. `PT5S`) |
| `event` | `listen` | external event name to await |
| `timeout` | `listen` | ISO-8601 wait timeout (default `PT24H`) |
| `pubsub` | `emit` | pub/sub component (default `dws.default-pubsub`) |
| `topic` | `emit` | topic to publish current data to |

**Runtime expressions** use the `jq` dialect and may be written wrapped as `${ .foo }` (DSL convention) or bare (`.foo`). Expression evaluation is pure and replay-safe.

---

## REST API

| Method & path | Purpose |
|---------------|---------|
| `POST /workflows/{name}/instances` | Start a new instance (body = initial data JSON). Returns `202` with `{ instanceId, workflowName, version }`. |
| `GET /workflows/instances/{id}` | Instance status: `{ instanceId, runtimeStatus, output }`. |
| `POST /workflows/instances/{id}/events/{event}` | Raise an external event for `listen` tasks (body = event payload). Returns `202`. |

Health probes: `GET /actuator/health/readiness`, `GET /actuator/health/liveness`.

### Example

```bash
# Start an order workflow
curl -s -XPOST localhost:8080/workflows/order-workflow/instances \
  -H 'content-type: application/json' \
  -d '{"item":"widget","quantity":2}'
# -> {"instanceId":"<id>","workflowName":"order-workflow","version":"1.0.0"}

# Check status
curl -s localhost:8080/workflows/instances/<id>

# Raise an external event (for a listen task named "approval")
curl -s -XPOST localhost:8080/workflows/instances/<id>/events/approval \
  -H 'content-type: application/json' -d '{"approved":true}'
```

---

## Configuration (`application.yaml`)

| Key | Env override | Default |
|-----|--------------|---------|
| `dws.definitions-path` | `DWS_DEFINITIONS_PATH` | `/etc/dws/definitions` |
| `dws.default-pubsub` | `DWS_DEFAULT_PUBSUB` | `pubsub` |
| `dws.retry.max-attempts` | `DWS_RETRY_MAX_ATTEMPTS` | `3` |
| `dws.retry.first-retry-interval` | `DWS_RETRY_FIRST_INTERVAL` | `PT1S` |
| `dws.retry.backoff-coefficient` | `DWS_RETRY_BACKOFF` | `2.0` |
| `dws.retry.max-retry-interval` | `DWS_RETRY_MAX_INTERVAL` | `PT30S` |
| `dapr.grpc.port` | `DAPR_GRPC_PORT` | `50001` |
| `dapr.http.port` | `DAPR_HTTP_PORT` | `3500` |

---

## Build & test

```bash
./mvnw verify          # compile, run unit + interpreter tests
```

Tests cover the definition parser, `switch`/`jq` evaluation, and the interpreter loop (driven against a mocked `WorkflowContext`).

### Container image

```bash
docker build -t sw-orchestrator:latest .
```

Multi-stage: `eclipse-temurin:25-jdk` build → `eclipse-temurin:25-jre` runtime, non-root.

---

## Run locally with Dapr

A local state store + pub/sub (default Redis components from `dapr init`) is required for the workflow runtime.

```bash
# 1. Point at a directory of definitions
export DWS_DEFINITIONS_PATH="$(pwd)/definitions"
mkdir -p definitions
kubectl create configmap dws-definitions --dry-run=client -o jsonpath='{.data.order-workflow\.json}' \
  --from-file=order-workflow.json=k8s/order-workflow-configmap.yaml >/dev/null 2>&1 || true
# (or just copy the order-workflow.json body from k8s/order-workflow-configmap.yaml into ./definitions/)

# 2. Build the app
./mvnw -DskipTests package

# 3. Run under a Dapr sidecar (app-id must match your callers / annotations)
dapr run \
  --app-id dws-orchestrator \
  --app-port 8080 \
  --dapr-http-port 3500 \
  --dapr-grpc-port 50001 \
  --resources-path ~/.dapr/components \
  -- java -jar target/dws-orchestrator.jar
```

Then use the REST API above. `call` tasks invoke other Dapr app-ids (`inventory-service`, `payment-service`, …) which must also be running under Dapr and expose `POST /run`.

---

## Deploy to Kubernetes

```bash
kubectl apply -f k8s/order-workflow-configmap.yaml   # definitions
kubectl apply -f k8s/deployment.yaml                 # dapr-enabled orchestrator
```

The Deployment carries `dapr.io/enabled` annotations with app-id **`dws-orchestrator`** and mounts the `dws-definitions` ConfigMap at `/etc/dws/definitions`. Add more workflows by adding keys to the ConfigMap — no image rebuild required.
