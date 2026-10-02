# dws-step-expressions

## Purpose

The pure, I/O-free jq runtime expression evaluator in `dws-step`, ported from `dws-orchestrator` so both give the same results.

## Requirements

### Requirement: jq evaluation matches v1
`dws-step` SHALL provide a pure, I/O-free runtime expression evaluator in the same jq dialect as
`dws-orchestrator` (jackson-jq, jq 1.6), accepting both `${ .foo }` and bare `.foo` forms with named
variables bound as `$name`. The same inputs SHALL give the same results as v1. The evaluator SHALL be
ported into `dws-step`, not shared as a module.

#### Scenario: Wrapped and bare forms
- **WHEN** `${ .foo }` and `.foo` are evaluated against the same input
- **THEN** both return the same result

#### Scenario: Named variables
- **WHEN** an expression references a bound variable such as `$context`
- **THEN** it resolves to the bound value

#### Scenario: v1 parity
- **WHEN** v1's `JqEvaluator` test cases are run against the ported evaluator
- **THEN** all give the same results as in v1
