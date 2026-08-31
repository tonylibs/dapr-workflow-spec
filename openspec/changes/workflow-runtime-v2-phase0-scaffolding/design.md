# Design: Workflow Runtime v2 — Phase 0 (ADR + scaffolding)

Context: [ADR 0001](../../adr/0001-workflow-runtime-v2-decisions.md),
[Workflow Runtime Architecture](../../roadmaps/workflow-runtime-architecture.md),
[Implementation Roadmap](../../roadmaps/workflow-runtime-architecture-roadmap.md) Phase 0.

## D1: Single-node definition JSON contract

The roadmap's own wording for this deliverable is "a node's own task list plus its children's
target app IDs." Two node kinds exist (per the classification rules), so the contract has two
shapes sharing a common envelope.

### Common envelope

```jsonc
{
  "$schema": "https://dws.io/schemas/single-node-definition.v1.json",
  "workflow": "order-fulfillment",              // owning workflow name
  "version": "order-fulfillment@v3f9a1c2b",     // content-addressed version this node belongs to
  "nodeId": "fulfill-order",                    // this node's own Dapr app ID (sanitized, DNS-1123)
  "kind": "flow" | "step"
  // ...kind-specific fields below
}
```

`workflow`/`version` are carried for logging, drift detection (a running instance can assert its
own `nodeId` matches what it was deployed under), and future debugging — they are not consumed for
dispatch. `nodeId` is redundant with the Dapr app ID the instance is deployed under, but is
included in the payload so a `dws-flow`/`dws-step` instance can fail fast if it's ever handed the
wrong file (definition/deployment mismatch) rather than silently running the wrong node.

### Flow node (`kind: "flow"`)

```jsonc
{
  "workflow": "order-fulfillment",
  "version": "order-fulfillment@v3f9a1c2b",
  "nodeId": "fulfill-order",
  "kind": "flow",
  "scope": "main" | "for" | "try" | "catch" | "forkBranch",
  "tasks": [ /* this scope's DSL 1.0 task objects, in order, unmodified from the compiled
                definition — set/switch/wait/listen/emit/raise/call/run leaves, or nested
                for/try/fork tasks that are themselves child Flow nodes */ ],
  "children": {
    // task name -> that task's compiled child node's Dapr app ID, for every task in `tasks`
    // that compiles to its own Flow or Step node (i.e. every task except a bare `set`/`switch`
    // that stays inline — see D2 for what "every task" means precisely)
    "reserveItems": "reserve-items",
    "validateOrder": "validate-order"
  },
  "catch": "fulfill-order-catch"   // present only when this scope has an attached catch block
}
```

### Step node (`kind: "step"`)

```jsonc
{
  "workflow": "order-fulfillment",
  "version": "order-fulfillment@v3f9a1c2b",
  "nodeId": "reserve-item",
  "kind": "step",
  "task": { /* the single DSL 1.0 task object this Step wraps, unmodified */ },
  "functionAppId": "reserve-item-fn"   // present only when task.call or task.run is set — the
                                        // Dapr app ID of the underlying dws-call-*/dws-run-*
                                        // Knative Service this Step proxies to (ADR 0001
                                        // Decision 1's -fn naming rule). Absent for
                                        // set/switch/wait/listen/emit/raise Step nodes.
}
```

### D1.1: Why every Step task gets `children` entries too, in the parent Flow

A Step task name still appears as a key in its parent Flow's `children` map (not just in
sub-Flow scopes) — ADR 0001 Decision 1 is "every compiled Step task, with no exceptions, gets its
own deployed `dws-step` instance," so from the parent Flow's point of view a leaf `set` task and a
nested `for` task are both just "a child node with an app ID" and are dispatched the same way
(`CallActivityAsync` vs. `CallChildWorkflowAsync` differ, but both are resolved through the same
`children` map — see D3).

### D1.2: Validation and fail-fast behavior

Both runtimes SHALL reject a definition at startup (not lazily, on first dispatch) if:
- `kind` doesn't match the runtime (`dws-flow` given a `kind: "step"` payload, or vice versa),
- required fields for that `kind` are missing (`tasks`/`scope` for flow; `task` for step),
- `nodeId` fails DNS-1123 label validation,
- the JSON fails schema validation at all (malformed, wrong types).

This phase implements exactly this validation and nothing past it — no cross-referencing that
`children` app IDs actually resolve to running services (that's a runtime concern for later
phases, not a startup-time one, since a child may scale-to-zero and not be "up" yet).

## D2: `dws-flow` — one instance, one registered workflow type

Each deployed `dws-flow` instance is dedicated to exactly one compiled Flow scope (ADR 0001
Decision 1). Its Dapr Workflow SDK registration is therefore always the same, constant workflow
type name — **`Flow`** — regardless of which scope it hosts; the scope's *identity* comes from the
Dapr app ID it's deployed under and the pinned definition it loaded, not from the registered type
name. This keeps the SDK wiring itself generic (register `Flow` once, at startup, from the
definition loaded at the same time) rather than needing per-scope code generation.

In this phase, the `Flow` workflow function body:
1. Reads the already-validated single-node definition (loaded once at startup, held in memory —
   `dws-orchestrator`'s existing "pinned definition" pattern, just scoped to one node).
2. Logs the scope it's hosting and its task count.
3. Returns immediately (a no-op workflow run) — no actual task sequencing yet. `CallActivityAsync`/
   `CallChildWorkflowAsync` dispatch per D1's `children` map is Phase 3 scope.

## D3: `dws-step` — one instance, one registered Activity

Symmetrically, each deployed `dws-step` instance registers exactly one Activity, also under a
constant name — **`Step`**. In this phase, the `Step` activity implementation:
1. Reads the validated single-node definition at startup.
2. Logs the task kind it's hosting (`task.call`/`task.run`/`task.set`/etc., whichever key is
   present on `task`).
3. Returns immediately (a no-op activity) — no real dispatch to `set`/`switch`/`wait`/`listen`/
   `emit`/`raise` logic, and no proxying to a `functionAppId` yet. That's Phase 2 scope.

## D4: Local dev and health

Both components expose a liveness/health endpoint (`GET /healthz`, matching the existing
step-service HTTP contract's naming rather than Spring's default `/actuator/health`, so the same
Knative/K8s probe convention already used elsewhere in the repo keeps working unmodified) that
reports healthy once the pinned definition has loaded and passed validation, unhealthy (or the
process exits, for `dotnet`'s simpler failure mode) if it hasn't. Both READMEs document the
`dapr run` invocation (app-id, app port, Dapr HTTP/gRPC ports) needed to run a single instance
locally against a hand-written sample single-node definition file, mirroring the pattern already
documented in `dws-orchestrator/README.md`.

## D5: What's explicitly deferred past this phase

- Real task dispatch in either runtime (Phase 2 for `dws-step` Activities, Phase 3 for `dws-flow`
  sequencing) — this phase's workflow/activity bodies are intentionally inert.
- `dws-controller` producing real single-node definitions (Phase 1's structural compiler) — this
  phase's runtimes are exercised against a hand-written sample file, not compiler output.
- Any deployment/synthesis wiring (Phase 4) or CI (noted in the proposal's Impact section).
- Cross-app integration testing (Phase 5) — this phase's tests are single-process/unit-level only.
