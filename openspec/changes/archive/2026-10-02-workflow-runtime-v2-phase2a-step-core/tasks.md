# Tasks: workflow-runtime-v2-phase2a-step-core

All work is in `dws-step/` (owner: java-spring-developer). Test first. Gate: `cd dws-step && ./mvnw verify`.

## 1. Failure contract
- [x] 1.1 Add `StepUpstreamException`, `StepConfigException`, `StepValidationException` with v1-identical messages; tests against v1 fixtures and a ported copy of v1's classify rules
## 2. Expressions
- [x] 2.1 Add `jackson-jq` dependency; port `JqEvaluator` + `ExpressionException` and v1's `JqEvaluator` tests
## 3. Routing
- [x] 3.1 Add `TaskKind`, `TaskHandler`, handler registry, and six "not implemented yet" handlers (config failure)
- [x] 3.2 Make `StepActivity` route by kind and define the `StepInput` envelope; serialisation round-trip test
- [x] 3.3 Loader rejects `wait`/`listen` (message names ADR 0006) and unknown kinds; tests
## 4. Docs and gate
- [x] 4.1 Document the failure-contract table, routing and envelope in `dws-step/README.md`
- [x] 4.2 `cd dws-step && ./mvnw verify` passes
