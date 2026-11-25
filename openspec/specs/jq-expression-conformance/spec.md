# jq-expression-conformance Specification

## Purpose
TBD - created by archiving change dws-jq-function. Update Purpose after archive.

## Requirements

### Requirement: Language-neutral expression corpus format
The repository SHALL contain `conformance/expressions/` with these parts:
- `case.schema.json` (JSON Schema 2020-12);
- `cases/<area>/<name>.yaml`, one case per file;
- `README.md`, stating the case format and the runner rules;
- `divergences/<engine>.yaml`, one list per engine.

Each case SHALL have these fields:
- `name`: equal to `<area>/<file name>`;
- `test`: `givenX_whenY_thenZ`, unique;
- `area`: equal to the directory;
- `summary`;
- `source`;
- `engine`: optional;
- `expression`;
- `input`;
- `variables`: optional object;
- `expect`: exactly one of `{result, truthy}` or `{fault: {phase, detailContains}}`, with `phase`
  one of `compile` or `evaluate`.

No case SHALL depend on a service, a secret, the wall-clock value or the time zone, beyond its type.

Owning component: `conformance/expressions` (data). The first runner is `dws-jq`.

#### Scenario: Case file is self-describing
- **WHEN** a runner loads `cases/loops/while-count.yaml`
- **THEN** the file validates against `case.schema.json`
- **AND** its `name` is `loops/while-count`
- **AND** its `area` is `loops`

#### Scenario: Malformed case fails the run
- **WHEN** a case has `area: loops` but lives under `cases/catch/`
- **THEN** the runner fails before evaluating any case

### Requirement: Seed coverage
The corpus SHALL include all of the following:
- the 13 cases of `conformance/data-flow/cases/builtins/` as expression vectors, under the same file
  names in area `builtins`:
  - the 11 blocked builtins expect a `compile` fault whose detail contains the builtin;
  - `clock-now` and `clock-local-time` keep their expected values;
- at least one case per row of ADR 0014 Decision 5's dialect table, in area `dialect`;
- the `for.in`, `while` and catch / retry `when` / `exceptWhen` expressions of v1's tests, with their
  inputs and variables, in areas `loops` and `catch`;
- combined-template cases in area `templates`:
  - a part using `def`;
  - a part using `as $x`;
  - a part that returns nothing;
  - a part with several results;
  - the ADR 0014 O-2 example;
- contract cases in area `contract`:
  - `${ }` unwrapping;
  - first output only;
  - no output;
  - truthiness of `false`, `null`, `0`, `""` and `[]`.

Owning component: `conformance/expressions`.

#### Scenario: Blocked builtin vector
- **WHEN** the runner evaluates `builtins/env`
- **THEN** the expected outcome is a `compile` fault whose detail contains `env`

#### Scenario: Template part with no result
- **WHEN** the runner evaluates the template case whose expression is `{ "a": ([ empty ] | .[0]), "b": 1 }`
- **THEN** the expected result is `{"a": null, "b": 1}`

#### Scenario: Template part with several results
- **WHEN** the runner evaluates `{ "a": ([ .[] ] | .[0]) }` with input `[3, 4]`
- **THEN** the expected result is `{"a": 3}`

### Requirement: Expectations come from the documented dialect, never from a runtime
Each expectation SHALL come from one of these sources:
- ADR 0012 Decision 9 (builtin policy);
- ADR 0014 Decision 3 (result and truthiness rules);
- ADR 0014 Decision 5 (the documented dialect: gojq's, minus the Decision 9 builtins);
- the data-flow corpus value it was carried over from.

When an engine disagrees, the case SHALL be listed in that engine's divergence list with a reason
and a follow-up. The expectation MUST NOT be edited to match the engine.

Owning component: `conformance/expressions`.

#### Scenario: Engine disagreement is listed, not hidden
- **WHEN** `dws-jq` produces a different value than `builtins/clock-local-time` expects
- **THEN** the case is listed in `divergences/dws-jq.yaml` with a reason
- **AND** its expected value is unchanged

### Requirement: Runner verdicts and the stale-divergence rule
A runner SHALL:
1. Validate every case against the schema, check that `name` and `area` match the path, and check
   that every `test` is unique.
2. Evaluate the expression the way its engine serves `/run`: unwrap, compile, bind `input` and
   `variables`, take the first output, and compute truthiness.
3. Compare numbers by value.
4. Treat a `compile` fault as matching only a failure before evaluation, and an `evaluate` fault as
   matching only a failure during evaluation.
5. Give each case exactly one verdict:
   - pass;
   - fail;
   - known divergence: the case is listed and still differs;
   - stale divergence: the case is listed but passes.
6. Print one line per case and the totals.

The run SHALL fail on any fail or stale-divergence verdict.

Owning component: the runner of each engine (`dws-jq` in this change).

#### Scenario: Stale divergence fails the run
- **WHEN** a case listed in `divergences/dws-jq.yaml` now matches its expectation
- **THEN** the run fails, naming the case, until the entry is removed

#### Scenario: Numbers compare by value
- **WHEN** a case expects `10` and the engine returns `10.0`
- **THEN** the verdict is pass

### Requirement: dws-jq runs the corpus in its package gate
`dws-jq`'s tests SHALL run the whole corpus through the same compile-and-evaluate code that serves
`POST /run`, with the time zone set to UTC. Because they are part of the tests, `make test` and the
CI workflow run them. The CI workflow SHALL also be triggered by changes under
`conformance/expressions/`. An environment variable MAY point the runner at another copy of the
corpus.

Owning component: `dws-jq`.

#### Scenario: Corpus change triggers dws-jq CI
- **WHEN** a pull request changes only `conformance/expressions/cases/dialect/key-order.yaml`
- **THEN** the `dws-jq` workflow runs, and its test job runs the corpus

#### Scenario: Gate passes with the seeded corpus
- **WHEN** `make test` runs in `dws-jq`
- **THEN** every case has the pass verdict or a still-valid known-divergence verdict
- **AND** the builtin cases fault naming their builtin
- **AND** the template cases pass
