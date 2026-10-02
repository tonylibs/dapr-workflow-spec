## Context

`dws-flow` (.NET 10, Dapr.Workflow 1.18.5) loads one `SingleNodeDefinition` (`scope`, `tasks`, `children`)
and registers workflow `Flow`. v1 behavior to match lives in `InterpreterWorkflow` (order, `then`,
timeout wording). The Step child contract is `StepInput(data, variables, workflowInstanceId,
iterationIndex)` -> new data. ADR 0001 (one app per node), 0004 (Sequencer vs Controller), 0006
(instance ID format).

## Goals / Non-Goals

**Goals:** Sequencer for `main`/`do`; child dispatch by type; v1-compatible `then`, timeout wording and
failure messages; controller-scope routing seam; replay safety.

**Non-Goals:** controller behavior (3b-3g), data-flow transforms, CI path filters, deployment, any
change outside `dws-flow/`.

## Decisions

### D1: Child type classification
- **Choice:** one function maps a task body to Step or Flow: `for`/`try`/`fork`/`wait`/`listen` -> Flow
  child, otherwise Step child. App ID comes from `children[taskName]`.
- **Rationale:** `children` carries no type; the task body is the only source.
- **Alternatives:** extend the definition schema (out of scope, schema is frozen).

### D2: Instance ID
- `<rootInstanceId>:<childAppId>:<iteration>`; iteration is the dot-joined iteration index, `0` outside
  loops. Deterministic, so child calls are idempotent on replay.

### D3: Envelope
- Flow child input has the same fields as `StepInput`. Root workflow input is the same shape.
  Output is the new workflow data. Data passes through untouched.

### D4: `then`
- continue -> next; task name -> index lookup in this scope (unknown name fails with v1 wording
  `flow references task '<t>', which is not declared in this task scope`); `end`/`exit` -> complete with
  current data; 10000-step cap with v1 wording.

### D5: Timeout
- Race `CallActivity`/`CallChildWorkflow` against a durable timer; on timer win fail with
  `task '<name>' timed out after <ISO-8601 duration>` (v1 rendering). Cancel the timer when the call wins.

### D6: Failure propagation
- Child failure message is rethrown unchanged (no wrapping prefix).

### D7: Controller routing
- A scope dispatch table keyed by scope: `main`/`do` -> Sequencer; `for`/`try-catch`/`fork` -> a
  not-implemented handler failing with `config failure: scope '<scope>' is not implemented yet`.

## Risks / Trade-offs

- [Child type inferred from task body] -> isolated in one function; revisit if the schema gains a type.
- [`switch` directive transport undefined] -> `then` only; gap reported to 2b.
- [Dapr .NET replay test API unknown] -> use the SDK's workflow test/replay facilities; fall back to
  driving the workflow with a fake `WorkflowContext` that records calls and replays history.

## Migration Plan

N/A: no deployment change.

## Open Questions

- How a v2 `switch` Step returns the next-task directive (2b).
- Whether the iteration segment of the instance ID should be empty rather than `0` outside loops.
