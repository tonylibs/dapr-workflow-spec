# dws-orchestrator

A generic, **config-driven** Dapr workflow orchestrator built on the **interpreter pattern**.

It ships as the prebuilt image **`sw-orchestrator`** and interprets a [Serverless Workflow](https://serverlessworkflow.io/) **DSL 1.0** definition parsed with the official Java SDK. **No per-workflow code is ever generated** — one registered `InterpreterWorkflow` walks the definition's `do` task list.

**Each orchestrator pod serves exactly one workflow definition**, loaded once at startup from a **Dapr Configuration store** by an immutable, versioned key. New versions are rolled out by the controller as **new deployments**, not by mutating a running pod.

- **Java 25**, Spring Boot 4.1.0 (`web`, `actuator`)
- **Dapr** workflow runtime + client + Configuration API (`dapr-sdk-workflows`, `dapr-sdk`, BOM 1.18.0)
- **Serverless Workflow SDK** `io.serverlessworkflow:serverlessworkflow-api` 7.x (DSL 1.0 model + reader/validation)
- **jackson-jq** for `jq`-dialect `when`/`set` expressions
- Base package `io.dws.orchestrator`

---

## Startup flow (load exactly once, fail fast)

1. Read env: **`DAPR_CONFIG_STORE`** (default `dws-definitions`) and **`DEFINITION_KEY`** (required; immutable versioned key, e.g. `order-workflow@v3`).
2. Fetch the definition text via `DaprClient.getConfiguration(store, key)`, retrying with backoff while the sidecar boots.
3. Parse and structurally validate with the Serverless Workflow SDK (`WorkflowReader`, YAML or JSON). **Any missing key or invalid document is logged clearly and the process exits non-zero.**
4. Register one `InterpreterWorkflow` **named from `document.name`**.

The orchestrator does **not** subscribe to configuration changes — the definition is immutable for the pod's lifetime.

---

## Interpreter

The loop runs directly over the SDK model (`Workflow.getDo()` → `List<TaskItem>`) as a program counter, honouring flow directives (`then: <task> | continue | end`):

| Task | Behaviour |
|------|-----------|
| `call`   | `ctx.callActivity(CallServiceActivity)` with the default retry policy |
| `switch` | pure `jq` evaluation of each case `when` in-workflow (first truthy wins; a case with no `when` is the default) |
| `set`    | pure `jq` evaluation in-workflow |
| `wait`   | `ctx.createTimer` |
| `listen` | `ctx.waitForExternalEvent` |
| `emit`   | activity that publishes via Dapr pub/sub |
| `end`    | `ctx.complete` |
| `for` / `try` | recognised by the SDK; interpretation not yet implemented |

### The one adapter: task name → Dapr resource

The single orchestrator-specific convention is that a task's **name in kebab-case** identifies the Dapr resource:

- `call` task `checkInventory` → invokes app-id **`check-inventory`** (`POST /run`, current data as JSON, response becomes the new data)
- `emit` task `orderPlaced` → publishes to topic **`order-placed`** on the default pub/sub
- `listen` task `approval` → waits for external event **`approval`**

`jq` expressions in `when`/`set` may be written wrapped as `${ .foo }` (DSL convention) or bare (`.foo`); evaluation is pure and replay-safe.

---

## Definition format

A standard Serverless Workflow **DSL 1.0** document (`k8s`/tests use `order.yaml`):

```yaml
document:
  dsl: 1.0.0
  namespace: examples
  name: order-workflow
  version: '1.0.0'
do:
  - checkInventory:
      call: http
      with: { method: post, endpoint: http://inventory-service/run }
      then: decide
  - decide:
      switch:
        - inStock:
            when: ${ .inStock }
            then: chargePayment
        - outOfStock:
            then: notifyOutOfStock
  - chargePayment:
      call: http
      with: { method: post, endpoint: http://payment-service/run }
      then: end
  - notifyOutOfStock:
      call: http
      with: { method: post, endpoint: http://notification-service/run }
      then: end
```

> The `call` block's `with.endpoint` is required by the DSL schema but **ignored** for routing. The orchestrator resolves the target purely from the kebab-cased task name: `checkInventory` → `check-inventory`, `chargePayment` → `charge-payment`, `notifyOutOfStock` → `notify-out-of-stock`. Name your tasks to match the intended Dapr app-ids.

---

## REST API

This pod serves **one** workflow; the start endpoint accepts only that name.

| Method & path | Purpose |
|---------------|---------|
| `POST /workflows/{name}/instances` | Start an instance (body = initial data JSON). `404` if `{name}` is not this pod's workflow. Returns `202` with `{ instanceId, workflowName }`. |
| `GET /workflows/instances/{id}` | Instance status: `{ instanceId, runtimeStatus, output }`. |
| `POST /workflows/instances/{id}/events/{event}` | Raise an external event for `listen` tasks (body = payload). Returns `202`. |

Health probes: `GET /actuator/health/readiness`, `GET /actuator/health/liveness`.

```bash
curl -s -XPOST localhost:8080/workflows/order-workflow/instances \
  -H 'content-type: application/json' -d '{"item":"widget","inStock":true}'
# -> {"instanceId":"<id>","workflowName":"order-workflow"}
curl -s localhost:8080/workflows/instances/<id>
```

---

## Configuration

| Key | Env | Default |
|-----|-----|---------|
| `dws.config-store` | `DAPR_CONFIG_STORE` | `dws-definitions` |
| `dws.definition-key` | `DEFINITION_KEY` | *(required)* |
| `dws.default-pubsub` | `DWS_DEFAULT_PUBSUB` | `pubsub` |
| `dws.config-fetch.max-attempts` | `DWS_CONFIG_MAX_ATTEMPTS` | `30` |
| `dws.config-fetch.retry-interval` | `DWS_CONFIG_RETRY_INTERVAL` | `PT2S` |
| `dws.retry.max-attempts` | `DWS_RETRY_MAX_ATTEMPTS` | `3` |
| `dws.retry.first-retry-interval` | `DWS_RETRY_FIRST_INTERVAL` | `PT1S` |
| `dws.retry.backoff-coefficient` | `DWS_RETRY_BACKOFF` | `2.0` |
| `dws.retry.max-retry-interval` | `DWS_RETRY_MAX_INTERVAL` | `PT30S` |

---

## Build & test

```bash
./mvnw verify
```

Tests: `jq`/switch evaluation, the interpreter loop parsing the fixture `order.yaml` via the SDK and asserting execution order for both branches (mocked `WorkflowContext`), and startup-failure cases (missing key, invalid document) with a mocked `DaprClient`.

### Container image

```bash
docker build -t sw-orchestrator:latest .
```

---

## Run locally with Dapr

Needs a Configuration component named `dws-definitions` (Redis) plus the workflow state store/pub-sub from `dapr init`.

```bash
# 1. Write the definition into the backing Redis store (apps can only READ via Dapr).
redis-cli set 'order-workflow@v1' "$(cat order.yaml)"

# 2. Add a Configuration component ~/.dapr/components/dws-definitions.yaml:
#    spec.type: configuration.redis, metadata redisHost=localhost:6379

# 3. Build and run under a Dapr sidecar. app-id must equal document.name.
./mvnw -DskipTests package
DAPR_CONFIG_STORE=dws-definitions DEFINITION_KEY=order-workflow@v1 \
  dapr run --app-id order-workflow --app-port 8080 \
    --dapr-http-port 3500 --dapr-grpc-port 50001 \
    --resources-path ~/.dapr/components \
    -- java -jar target/dws-orchestrator.jar
```

`call` tasks invoke other Dapr app-ids (`check-inventory`, `charge-payment`, …) which must run under Dapr and expose `POST /run`.

---

## Deploy to Kubernetes

```bash
kubectl apply -f k8s/configuration-component.yaml   # Dapr Configuration store (Redis-backed)
kubectl apply -f k8s/deployment.yaml                # dapr-enabled orchestrator, app-id order-workflow
```

- The Deployment sets `DAPR_CONFIG_STORE=dws-definitions` and `DEFINITION_KEY=order-workflow@v1`, with `dapr.io/app-id: order-workflow`. There is **no definitions ConfigMap volume** — definitions live in the Configuration store.
- **The Dapr Configuration API is read-only for applications.** The controller therefore writes definition keys **directly into the backing store (Redis)**; the orchestrator only reads them.
- **Roll out a new version** by writing a new immutable key (e.g. `order-workflow@v2`) and deploying a new pod with `DEFINITION_KEY=order-workflow@v2`. Running pods keep serving their pinned version until replaced.
```
