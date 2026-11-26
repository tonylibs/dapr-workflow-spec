## ADDED Requirements

### Requirement: Rewrite cases are pinned to the controller's template rewrite

The corpus SHALL hold, in area `templates`, one case named `templates/rewrite-<shape>` for each
template shape that the controller's expression-site finder rewrites. The shapes are:
- an object with parts and constants;
- an array;
- nested mixed constants;
- `set` with bare top-level strings and a nested constant left unevaluated;
- a part using `def`;
- a part using `as $x`;
- a part with no output;
- a part with several results;
- a single bare `${ }` field;
- a part ending in a `#` comment.

Each case SHALL meet these conditions:
- `expression` is exactly the text the controller produces for the field. A controller test fails if
  the finder's output for the case's source field differs.
- `input` and `variables` are the data that field is evaluated over.
- `expect` is the value v1 gives for the same field over the same data, or v1's evaluation failure.
- `source` cites the v1 test or `JqEvaluator`/activity line that fixes v1's result.

The controller keeps each source field in its own test resources and names the case it is pinned to.
A case's file has no field beyond those the case schema allows.

Owning components: `conformance/expressions` holds the cases; `dws-controller` pins the expressions;
`dws-jq`'s runner checks the results.

#### Scenario: Controller and corpus agree on the rewrite
- **GIVEN** the controller fixture for `templates/rewrite-object`, whose field is
  `{ id: "${ .id }", source: "api" }` under the template rule
- **WHEN** the controller's `./mvnw verify` runs
- **THEN** the finder's expression for that field equals the `expression` of
  `cases/templates/rewrite-object.yaml`

#### Scenario: Rewritten expression gives v1's answer on gojq
- **GIVEN** `cases/templates/rewrite-set-top-level.yaml`, whose expect is the object v1's
  `EvaluateSetActivity` returns for the same `set` entries over the same input
- **WHEN** `dws-jq`'s `make test` runs the corpus
- **THEN** the case's verdict is pass

#### Scenario: Drift fails the controller gate
- **GIVEN** a change to the finder's rewrite format that is not reflected in the corpus
- **WHEN** the controller's `./mvnw verify` runs
- **THEN** the pinning test fails and names the case

## MODIFIED Requirements

### Requirement: Expectations come from the documented dialect, never from a runtime
Each expectation SHALL come from one of these sources:
- ADR 0012 Decision 9 (builtin policy);
- ADR 0014 Decision 3 (result and truthiness rules);
- ADR 0014 Decision 5 (the documented dialect: gojq's, minus the Decision 9 builtins);
- the data-flow corpus value it was carried over from;
- v1's behavior for the same expression or field, cited as a `dws-orchestrator` test or source
  `file:line`. The `loops`, `catch` and `templates/rewrite-*` cases use this source.

When an engine disagrees, the case SHALL be listed in that engine's divergence list with a reason
and a follow-up. The expectation MUST NOT be edited to match the engine.

Owning component: `conformance/expressions`.

#### Scenario: Engine disagreement is listed, not hidden
- **WHEN** `dws-jq` produces a different value than `builtins/clock-local-time` expects
- **THEN** the case is listed in `divergences/dws-jq.yaml` with a reason
- **AND** its expected value is unchanged
