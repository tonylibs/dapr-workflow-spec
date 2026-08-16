# E2E checklist — multi-app activity dispatch

Goal: prove a migrated step (`call: http` / `run: shell` / `run: script`) is invoked as a **cross-app
Dapr Workflow activity** named `Run`, with the upstream-vs-config failure markers surfacing the same
error shape as the old HTTP path.

> **VALIDATED on Dapr 1.18.0 (2026-08-16).** Cross-app dispatch works end-to-end with the real
> `dws-call-http` worker: the `Run` activity dispatched to app-id `check-inventory`, executed, and
> the workflow completed → `output={"hello":"world","stock":42}`, 0 dispatch failures.
>
> **Use Dapr ≥ 1.18.0** (the chart pins `1.18.0`). Earlier runtimes fail: 1.15.5 →
> `required metadata dapr-app-id not found` (multi-app needs ≥1.16.0); 1.16.0 →
> `required metadata dapr-callee-app-id or dapr-app-id not found` (runtime older than the 1.18-era
> client libs the images link — durabletask-go v0.12.4 / kit v0.18.1 / dapr v1.18.0). This was a
> version mismatch, **not** a branch logic defect (cf. [dapr/dapr#10039](https://github.com/dapr/dapr/issues/10039)).
>
> Self-hosted slim also needs, purely as local harness plumbing (not cluster concerns): the
> scheduler started with `--override-broadcast-host-port localhost:50006`, and `DAPR_HOST_IP=127.0.0.1`
> exported so mdns advertises a routable address instead of a placeholder.

Two tracks:
- **Track A (smoke, light):** a throwaway Go workflow host schedules the `Run` activity cross-app
  against the real step worker. Isolates the dispatch mechanism — no config store, no Java.
- **Track B (faithful):** the real `dws-orchestrator` (Java) drives it end to end from a posted
  workflow definition. More setup, but exercises the actual production path.

---

## 0. Runtime prerequisites (both tracks)

Multi-app workflow needs **placement** (actors), **scheduler** (workflow), and a **state store with
`actorStateStore: "true"`** shared by every app. Self-hosted slim doesn't auto-run placement/scheduler,
so start them yourself. Redis is the state store.

```bash
# 0.1 CLI + slim runtime (daprd + placement + scheduler binaries)
# Multi-app needs runtime >= 1.16.0. 1.15.5 does NOT support it (see KNOWN ISSUE).
# Use a version from the validated combination once identified (follow-ups.md item 3).
dapr --version                       # CLI 1.18.x
dapr init --slim --runtime-version 1.18.0    # validated; matches the chart's appVersion

# 0.2 State store: redis
redis-server --port 6379 &           # or your local redis

# 0.3 placement + scheduler (slim = run manually)
~/.dapr/bin/placement -port 50005 &
~/.dapr/bin/scheduler --port 50006 --etcd-data-dir /tmp/dapr-scheduler &
```

**Expect:** placement logs `leader is ... placement tables`; scheduler logs `starting Dapr Scheduler`.
If either binary is missing, re-run `dapr init --slim`.

### Shared components dir

Create `./components/` used by **both** apps (`--resources-path ./components`), so both resolve the
*same* store — the cross-app dispatch prerequisite.

`components/statestore.yaml`:
```yaml
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: statestore
spec:
  type: state.redis
  version: v1
  metadata:
    - name: redisHost
      value: localhost:6379
    - name: redisPassword
      value: ""
    - name: actorStateStore          # REQUIRED — without this, workflow/actors won't start
      value: "true"
```

**Checkpoint:** `dapr run ... -- sleep 2` for any app should log
`component loaded. name: statestore, type: state.redis/v1` and **no** `actorStateStore` warning.

---

## 1. Build the step worker

```bash
cd dws-call-http && go build -o /tmp/dws-call-http .
# (or dws-run: cd dws-run && go build -o /tmp/dws-run .)
```

The worker needs `ENDPOINT` (required) + `TASK`. **app-id = kebab-case of the task name** — e.g. task
`checkInventory` ⇒ app-id `check-inventory`. Point `ENDPOINT` at any reachable HTTP echo for a
success run (e.g. `https://httpbin.org/post`), or at a closed port to force the upstream-failure path.

---

## 2. Track A — smoke (recommended first)

### 2.1 Run the step worker as app `check-inventory`
```bash
cd dws-call-http
TASK=checkInventory ENDPOINT=https://httpbin.org/post OUTPUT=merge PORT=8081 \
  dapr run --app-id check-inventory --app-port 8081 \
           --dapr-grpc-port 50001 --resources-path ../components \
           -- /tmp/dws-call-http
```
**Expect:**
- worker log `starting dws-call-http activity worker  activity=Run task=checkInventory ...`
- daprd log `established connection to placement` and a workflow/actor worker starting
- `curl localhost:8081/healthz` ⇒ `200 {"status":"ok","task":"checkInventory"}`

### 2.2 Throwaway host that schedules `Run` cross-app
A minimal host registers a workflow that calls the `Run` activity **on another app-id**. Confirm the
exact app-id option name first:
```bash
go doc github.com/dapr/durabletask-go/workflow | grep -i appid
```
The workflow body is essentially:
```go
// register: registry.AddWorkflowN("probe", probe)
func probe(ctx *workflow.WorkflowContext) (any, error) {
    var out map[string]any
    err := ctx.CallActivity("Run",
        workflow.WithActivityInput(map[string]any{"hello": "world"}),
        workflow.WithActivityAppID("check-inventory"),   // <- cross-app target; confirm symbol via go doc
    ).Await(&out)
    return out, err
}
```
Run the host as its own app (`dapr run --app-id probe-host --resources-path ../components -- /tmp/probe`)
and start one instance (via the host's `ScheduleWorkflow` / a tiny HTTP trigger you add).

**Expect (success path):**
- **step worker** logs an invocation of `checkInventory`, hits `ENDPOINT`, returns shaped JSON
- host instance completes; output = input **merged** with the upstream response (because `OUTPUT=merge`)
- redis: `redis-cli KEYS '*'` shows workflow/actor state keys (e.g. `check-inventory||...`,
  `probe-host||...`) — proves both apps share the store

**Expect (failure path):** rerun the worker with `ENDPOINT=http://127.0.0.1:9` (closed):
- step worker returns error `step 'checkInventory' upstream failure: ...`
- the workflow **retries** the activity (default retry policy) rather than failing immediately —
  this is the 502-equivalent. A config error (e.g. bad `OUTPUT`) would instead give
  `step 'checkInventory' config failure: ...` and **not** retry.

---

## 3. Track B — faithful (real orchestrator)

Needs the definition available to the orchestrator via a Dapr **configuration store** keyed by
`DEFINITION_KEY`, plus pubsub for lifecycle events. Fuller, but the true path.

1. **Components** (in `./components`): add `pubsub.yaml` (`pubsub.redis`) and a `configuration` store
   the orchestrator reads the definition from. Keep `statestore.yaml` from §0.
2. **Definition:** a minimal workflow whose `do` has one `call: http` task named `checkInventory`
   (with `with.endpoint` present — schema-required, ignored for routing). Store it under
   `DEFINITION_KEY=<name>@v<hash>` in the configuration store.
3. **Run the step worker** exactly as §2.1 (app-id `check-inventory`).
4. **Run the orchestrator:**
   ```bash
   cd dws-orchestrator && ./mvnw -q package -DskipTests
   DEFINITION_KEY=<name>@v<hash> \
     dapr run --app-id <name> --app-port 8080 \
              --resources-path ../components \
              -- java -jar target/*.jar
   ```
   **Expect:** boot log loads the one definition; `POST /{name}/instances` is live.
5. **Trigger an instance:**
   ```bash
   curl -XPOST localhost:8080/<name>/instances -H 'content-type: application/json' -d '{"sku":"A1"}'
   ```
   **Expect:** response carries an `instanceId`; orchestrator dispatches the `Run` activity to app-id
   `check-inventory` (NOT a `POST /run` HTTP call — that path is gone for `call: http`); step worker
   logs the invocation; instance completes with the shaped data.

**Contrast check (openapi unchanged):** a `call: openapi` task named `lookupPrice` still goes over
HTTP service invocation to `POST /run` on app-id `lookup-price` — the openapi image is not an activity
worker. Its Knative `min-scale` stays `0`; the migrated steps get `min-scale=1`.

---

## 4. Pass/fail summary

| # | Check | Pass looks like |
|---|-------|-----------------|
| 1 | State store shared, actorStateStore on | both apps log `statestore` loaded, no actor-store warning |
| 2 | Worker registers `Run` | `activity=Run task=<name>` in worker log; `/healthz` 200 |
| 3 | Cross-app dispatch reaches worker | worker logs the invocation after an instance starts |
| 4 | Success shaping | output = replace|merge per `OUTPUT`, matching old HTTP behavior |
| 5 | Upstream failure ⇒ retry | `... upstream failure: ...`, activity retried (502-equivalent) |
| 6 | Config failure ⇒ no retry | `... config failure: ...`, distinct, not retried |
| 7 | Redis holds workflow/actor state | `redis-cli KEYS '*'` shows both app-ids' keys |
| 8 | openapi unchanged | `call: openapi` still hits `POST /run`, min-scale 0 |

## Gotchas
- **No `actorStateStore: "true"`** ⇒ workflow engine won't start; worker hangs with no activity ever
  dispatched. Most common failure.
- **placement/scheduler not running** (slim) ⇒ daprd logs connection-refused to `:50005`/`:50006`.
- **app-id mismatch** ⇒ dispatch silently never arrives. app-id MUST equal `TaskNaming.toKebabCase(name)`.
- **Different `--resources-path` per app** ⇒ apps resolve different stores; cross-app dispatch fails.
  Use one shared `./components` for every app.
- **Version compatibility is the current blocker** (see KNOWN ISSUE): cross-app dispatch needs a
  matched (daprd, dapr SDK/durabletask-go) pair. 1.15.5 is unsupported; 1.16.0 did not propagate
  `dapr-callee-app-id` in testing. `1.17+` additionally enables durable/deduplicated results.
