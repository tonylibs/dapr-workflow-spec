## Context

Phase 0 scaffolded `dws-step` (`StepActivity` no-op, `SingleNodeDefinitionLoader`). ADR 0001 gives one Dapr
app per node; ADR 0006 puts `wait`/`listen` in `dws-flow`. v1 classifies failures by message wording
(`WorkflowErrors`) because only the message survives the activity boundary.

## Goals / Non-Goals

**Goals:** a stable routing seam; v1-identical failure wording; jq parity.
**Non-Goals:** any real task behavior; data-flow transforms; Flow-side classification.

## Decisions

- **D1 Routing**: a `TaskKind` enum + `TaskHandler` interface + `TaskHandlerRegistry` (kind -> handler).
  `StepActivity` resolves the handler once and delegates. Later phases add a handler per kind only.
- **D2 Startup validation**: the loader rejects `wait`/`listen` (message names ADR 0006) and unknown kinds,
  using `TaskKind` as the single source of supported kinds.
- **D3 Envelope**: `StepInput(JsonNode data, Map<String,JsonNode> variables, String workflowInstanceId,
  String iterationIndex)`; output is the new workflow data `JsonNode`. Mirrors v1's `CallRequest` fields. No new inputs.
- **D4 Failures**: `StepFailureException` hierarchy `StepUpstreamException` (retryable), `StepConfigException`,
  `StepValidationException`, building messages exactly as in the spec table. Marker strings are constants.
  The failure-contract test embeds a copy of v1's classification rules and v1 fixtures.
- **D5 jq**: `JqEvaluator` and `ExpressionException` copied from `dws-orchestrator` with tests; `jackson-jq` added to the pom.

## Risks / Trade-offs

- Copy drift between v1 and `dws-step` evaluators is accepted (port, not share), mitigated by ported tests.
- Retryability is carried by exception type; how the Flow maps it to a Dapr retry policy is Phase 3.

## Open Questions

- Where data-flow transforms live in v2 (2.0 decision). Unaffected here.
