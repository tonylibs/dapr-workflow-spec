# Brainstorm: Phase 3a, dws-flow core (Sequencer)

Raw capture. The handoff for this change locked scope, behavior and acceptance up front, so the
decision chain below was resolved from the handoff plus read-only recon of v1 (`dws-orchestrator`),
`dws-step`, `dws-flow` and ADRs 0001/0003/0004/0006. No interactive Q&A round was held.

## Background
- `dws-flow` loads one node definition and registers `FlowWorkflow`, a no-op placeholder.
- v1 `InterpreterWorkflow.runTaskList` is a program-counter loop with `then` (name/end/exit/continue),
  a 10000-step cap, "not declared in this task scope" on unknown targets, and a task timeout that races
  a timer against the call and fails with `task '<name>' timed out after <ISO-8601 duration>`.
- Failures in v1 surface with the inner message unchanged; classification is message-based
  (`WorkflowErrors.classify`), so 3c must receive the child's message untouched.
- `dws-step` activity `Step` takes `StepInput(data, variables, workflowInstanceId, iterationIndex)` and
  returns new data only.

## Decisions
- Q1 Which task is a Step vs Flow child? `children` maps task name -> app ID only. Decision: classify by
  the task body: structural scopes (`for`, `try`, `fork`, `wait`, `listen`) are Flow children (child
  workflow); everything else is a Step child (activity `Step`). One classification point, no per-kind
  behavior beyond that.
- Q2 Instance ID `<root>:<nodePath>:<iteration>`. nodePath = the child's app ID (unique per node,
  already in `children`). iteration = the encoded iteration index, `0` when not inside a loop.
- Q3 Envelope: Flow child input mirrors `StepInput` (data, variables, rootInstanceId, iterationIndex).
- Q4 Timeout: race child call against a durable timer, v1 wording and ISO-8601 duration rendering.
- Q5 Controller scopes (`for`, `try-catch`, `fork`) on this node: fail with a config failure
  `config failure: scope '<scope>' is not implemented yet`, routed through a single dispatch table so
  3b-3g only replace entries.
- Q6 Determinism: no clock/random/I/O in workflow code; time via durable timers; tested by replay.
- Q7 `switch` seam: v1 `EvaluateSwitchActivity` returns a `FlowOutcome{keyword,target}` beyond new data.
  v2 Step returns data only and 2b owns the contract. Decision: implement `then` only; report the gap.

## Rejected
- Applying `input.from`/`output.as`: open 2.0 decision.
- Calling function images directly: violates ADR 0001.
