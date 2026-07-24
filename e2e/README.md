# DWS end-to-end test suite

Data-driven e2e coverage for the workflow platform: a single **definition catalog**
(`catalog/`) pairs Open Workflow Specification DSL 1.0 definitions with the expectations
the suites assert. Every case is checked on two axes:

1. **Visible** — the definition deploys through the controller's real `/workflows` API and
   is observable via `GET /workflows`, `GET /workflows/{name}` and
   `GET /workflows/{name}/plan`, with the exact step services and topic bindings the
   manifest pins.
2. **Works as the flow** — the definition, parsed by the real SDK, drives the orchestrator's
   real `InterpreterWorkflow`: service invocation order, branch selection, timers, external
   events, emits and the final output must match the manifest's scenarios.

## Tiers

| Tier | Where | What runs | When |
|------|-------|-----------|------|
| A: visible | `dws-controller/src/test/java/io/dws/controller/e2e/WorkflowCatalogVisibilityE2eTest.java` | real HTTP API + compiler + apply pass against the CRUD Kubernetes mock server | every PR/push (`e2e.yml`, also `./mvnw test`) |
| B: works as the flow | `dws-orchestrator/src/test/java/io/dws/orchestrator/e2e/WorkflowCatalogFlowE2eTest.java` | real DSL parse + real interpreter against a scripted `WorkflowContext` | every PR/push (`e2e.yml`, also `./mvnw test`) |
| C: real runtime | `runtime/` (docker compose + `run.sh`) | actual orchestrator image, real Dapr sidecars/placement/scheduler, real `dws-call-http` steps, Redis | nightly / `workflow_dispatch` only — never a PR gate |

The catalog is mounted onto both modules' test classpaths as `/e2e-catalog` via an extra
`<testResource>` in each pom, so there is exactly one copy of every definition.

## Adding a new definition case

1. Create `catalog/<case>/definition.yaml` (any valid DSL 1.0 document) and
   `catalog/<case>/manifest.yaml` (see the field reference below).
2. Append `<case>` to `catalog/index.yaml`.
3. Run both suites — they pick the case up automatically:

   ```bash
   (cd dws-orchestrator && ./mvnw test -Dtest=WorkflowCatalogFlowE2eTest)
   (cd dws-controller  && ./mvnw test -Dtest=WorkflowCatalogVisibilityE2eTest)
   ```

Supported DSL constructs: `call: http`, `call: openapi`, `run`, `switch`, `set`, `wait`,
`listen`, `emit`. Do **not** add flow scenarios for `for`/`try` — the interpreter
recognises but does not implement them yet.

## Manifest reference

```yaml
name: order                 # kebab-cased document.name; the controller's workflow name
deploy:
  valid: true               # false => the controller must reject the POST with 400
  expectSteps:              # step services (kebab-cased call/run task names), in walk order
    [check-inventory, charge-payment, notify-out-of-stock]
  expectBindings: []        # topic bindings: emit => the event type, listen => the task name verbatim
scenarios:                  # tier B: one entry per input/branch; [] to skip the flow tier
  - name: in-stock
    input: { item: widget }
    calls:                  # scripted activity results, consumed strictly in this order
      - appId: check-inventory
        result: { item: widget, inStock: true }
      - appId: charge-payment
        result: { item: widget, inStock: true, charged: true }
    expectCalls: [check-inventory, charge-payment]   # asserted invocation order
    expectOutput: { item: widget, inStock: true, charged: true }
    # optional:
    # events:       - { name: waitForApproval, payload: { approved: true } }
    # expectTimers: [PT2S]
    # expectEmits:  - { pubsub: pubsub, topic: publish-placed }
```

Rules worth knowing (they mirror the production code, not conventions of the suite):

- A `call` task invokes the Dapr app-id derived from the **kebab-cased task name**
  (`checkInventory` → `check-inventory`); `with.endpoint` is schema-required but ignored.
- `events[].name` must equal the **listen task's name** — the interpreter waits on
  `ctx.waitForExternalEvent(<task name>, …)`. A mismatched name fails the test loudly.
- At deploy time an `emit` task's binding topic is the event's `type`; at runtime the
  interpreter publishes to the **kebab-cased task name** on the default pubsub.
- `switch`/`set`/`wait` deploy nothing, so they don't appear in `expectSteps`.
- Manifests are parsed strictly: an unknown key fails the suite.

## Tier C: real Dapr runtime

```bash
bash e2e/runtime/run.sh
```

Builds the orchestrator and `dws-call-http` images, boots Redis + Dapr
(placement/scheduler/sidecars, sqlite name resolution), seeds the `order` definition
into Redis (the Configuration API is read-only for apps — the controller does the same
in-cluster), then starts an instance via `POST /workflows/order/instances` and polls
`GET /workflows/instances/{id}` until `COMPLETED`, asserting the in-stock branch's output.
Scoped to the single `order` case; startup ordering makes it the flakiest tier, which is
why CI runs it only nightly or on manual dispatch.
