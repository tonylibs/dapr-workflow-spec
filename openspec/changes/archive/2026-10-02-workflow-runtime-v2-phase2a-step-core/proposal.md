## Why

Phase 0 left `dws-step` as a no-op activity. Phase 2a of the Workflow Runtime Architecture roadmap
(`docs/roadmaps/workflow-runtime-architecture-roadmap.md`, "Phase 2 breakdown", row 2a) turns it into
a real Step host: it routes by task kind, defines the activity's data envelope, fails in a
v1-compatible way, and carries a jq evaluator. Doing the routing and error contract once means 2b
(`set`/`switch`), 2c (`emit`/`raise`) and 2d (`call`/`run` proxy) only add behavior and never touch routing.
ADR 0006 moves `wait` and `listen` to `dws-flow`, so `dws-step` must refuse them.

## What Changes

- Route the `Step` activity by the pinned node's task kind to a per-kind handler. Supported kinds:
  `set`, `switch`, `emit`, `raise`, `call`, `run`. Every handler fails with a non-retryable
  "not implemented yet" configuration failure in this change.
- Reject `wait` and `listen` at startup with a message naming ADR 0006. Unknown kinds keep failing at startup.
- Define the activity input (workflow data, scope-local variables, root workflow instance ID, iteration
  index) and output (new workflow data) as a Dapr-serialisable envelope. No new inputs.
- Define the failure contract: three failure categories whose messages are byte-identical to v1's, so the
  Flow side can classify them with `WorkflowErrors`'s rules. Documented as a table in the spec and in
  `dws-step/README.md`.
- Port the jq evaluator (`JqEvaluator`) from `dws-orchestrator` into `dws-step`, with its tests. Not a shared module.
- **Non-goals**: real `set`/`switch` (2b), `emit`/`raise` (2c), `call`/`run` proxying (2d), Go images (2e),
  `wait`/`listen`, data-flow transforms (`input.from`, `output.as`, schemas; open 2.0 decision), Flow-side
  classification (Phase 3), controller classification and the schema scope enum.

## Capabilities

### New Capabilities
- `dws-step-core`: kind routing, startup rejection of `wait`/`listen`/unknown kinds, activity envelope.
- `dws-step-failure-contract`: retryable/non-retryable failure categories and the exact v1 message wording.
- `dws-step-expressions`: the ported jq evaluator and its v1-equivalence guarantee.

### Modified Capabilities
<!-- `dws-step-scaffold` from Phase 0 is not yet synced to openspec/specs; its no-op behavior is replaced
     by the new capabilities above and is not re-specified here. -->

## Impact

- Component: `dws-step/` only (Java 25, Spring Boot). New dependency: `net.thisptr:jackson-jq` (same version as `dws-orchestrator`).
- No change to `dws-orchestrator`, `dws-controller`, `dws-flow`, or any image or chart. v1 is unaffected.
- Gate: `cd dws-step && ./mvnw verify`.
