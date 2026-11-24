# Expression conformance corpus

One language-neutral set of jq expression cases that every jq engine in DWS is measured against:
[ADR 0014](../../docs/adr/0014-dws-jq-expression-function.md) Decision 5. Each case is an
expression, an input, optional variables, and the expected result or fault. The first runner is
`dws-jq` (gojq). A jackson-jq runner for Step nodes and v1 (follow-up EX-1) follows the same rules,
and shows where a Step expression and a Flow expression can disagree.

This corpus works at the expression level. [`conformance/data-flow/`](../data-flow/README.md) works at
the workflow level; its 13 `builtins` cases are carried over here as expression vectors.

```
conformance/expressions/
├── case.schema.json      # JSON Schema 2020-12 for one case file
├── cases/<area>/*.yaml   # the corpus, one case per file
└── divergences/<engine>.yaml   # an engine's known divergences, each with its follow-up
```

| Area | What it covers |
|---|---|
| `contract` | `${ }` unwrapping, first output only, no output, truthiness, evaluation errors (ADR 0014 Decision 3) |
| `builtins` | the 13 data-flow builtin cases (11 blocked builtins, `now` and the local-time functions), plus look-alike guards for the policy walk |
| `dialect` | one case per row of the ADR 0014 Decision 5 table |
| `loops` | v1's `for.in` and `while` expressions |
| `catch` | v1's `when`, `exceptWhen`, `as` and `retry.when` expressions, run against a 503 error |
| `templates` | the combined-template forms of ADR 0014 Decision 9, O-2 |

## Case format

A case is plain YAML data, validated by [`case.schema.json`](case.schema.json).

| Field | Required | Meaning |
|---|---|---|
| `name` | yes | `<area>/<file name>`, unique; divergence lists refer to it |
| `test` | yes | the test name a runner reports, `givenX_whenY_thenZ`, unique |
| `area` | yes | the directory: `contract`, `builtins`, `dialect`, `loops`, `catch`, `templates` |
| `summary` | yes | what the case proves, one sentence |
| `source` | yes | where the expectation comes from: ADR 0012 Decision 9, ADR 0014 Decision 3 or 5 (plus the gojq README), a data-flow case, or a v1 test `file:line` |
| `engine` | no | what an engine does with the expression, when that matters |
| `expression` | yes | the jq expression, bare or `${ }` wrapped, always a single-quoted YAML scalar |
| `input` | yes | the value bound to `.`; may be `null` |
| `variables` | no | an object; each entry is bound to `$<name>`. A declared variable left out is `null` |
| `expect` | yes | either `{result, truthy}` or `{fault}` |

`expect.result` is the first output, or `null` if there is none. `expect.truthy` is `false` for `null`
and `false`, and `true` for everything else. Numbers compare by value, so `10` and `10.0` are equal.

`expect.fault` means the expression fails:

| Field | Meaning |
|---|---|
| `phase` | `compile`: it fails before any evaluation. `evaluate`: it fails while evaluating |
| `detailContains` | strings the fault detail must contain, such as the blocked builtin. May be empty when only the phase is specified |

```yaml
name: builtins/env
test: givenEnvBuiltin_whenExpressionCompiles_thenCompileFaultNamesBuiltin
area: builtins
summary: 'The `env` builtin is blocked; using it in output faults the task, naming the builtin.'
source: 'ADR 0012 Decision 9 (blocked: exposes the pod''s environment).'
expression: '${ { home: env.HOME } }'
input: {"value":1}
expect:
  fault: { phase: compile, detailContains: ["env"] }
```

## Authoring rules

- **Expectations come from the documented dialect, not from an engine.** The sources are ADR 0012
  Decision 9 (builtin policy), ADR 0014 Decision 3 (result and truthiness), ADR 0014 Decision 5 (gojq's
  dialect, minus the Decision 9 builtins, checked against the gojq README), and the data-flow value a
  case was carried over from. If an engine disagrees, list a divergence for it. Never edit the
  expectation.
- **A case depends on nothing outside the expression.** No service, no secret, and no wall-clock or
  time-zone value beyond its type (`clock-now` and `clock-local-time` check types or fixed instants).
- **Be honest about the engine.** If an engine is known or suspected to differ, say so in `engine`. A
  case passes only on the documented behavior, never on what an engine happens to do.
- **Every `expression` is a single-quoted YAML scalar.** An unquoted `${ { ... } }` is a YAML flow
  mapping. Write an inner `'` as `''`.
- **v1 cases cite their test.** `loops/` and `catch/` cases name the `dws-orchestrator/src/test/...:<line>`
  they were taken from, with the input and variables that test used.

## Running an engine against the corpus

A runner, in any language:

1. Loads every `cases/**/*.yaml`, validates it against `case.schema.json`, and checks that `name` and
   `area` match the path and that every `test` is unique. A malformed case fails the run before any case
   is evaluated.
2. Evaluates the expression the way its engine serves `POST /run`: unwraps `${ }`, compiles, binds
   `input` to `.` and each `variables` entry to `$<name>`, takes the first output, and computes
   truthiness.
3. Maps a failure to a neutral fault `(phase, detail)`. A `compile` fault matches only a failure before
   evaluation, and an `evaluate` fault matches only a failure during evaluation.
4. Compares the outcome with `expect`. Numbers compare by value.
5. Gives each case exactly one verdict:
   - **pass**
   - **fail**
   - **known divergence**: the case is listed in `divergences/<engine>.yaml` and still differs.
   - **stale divergence**: the case is listed but now passes. This must fail the run, so the entry is
     removed in the same change that fixes it.

   It prints one line per case and the totals. The run is green when every case that is not listed
   passes, and every listed case still diverges.

The divergence list is per engine:

```yaml
runtime: dws-jq (gojq)
divergences:
  - case: dialect/key-order
    reason: one line
    followUp: EX-1
```

## Engine notes

| Engine | Where it runs | Status |
|---|---|---|
| gojq | `dws-jq`; the documented dialect is gojq's, minus the ADR 0012 Decision 9 builtins | Runner in `dws-jq/internal/conformance`; list in `divergences/dws-jq.yaml` |
| jackson-jq 1.2.0 | `dws-step` and v1 (`dws-orchestrator`), jq 1.6 builtins | Not yet run (EX-1). The `engine` field of the cases records what it is expected to do; the "to confirm" cells of ADR 0014 Decision 5 become fact when EX-1 lands |

The two engines differ in documented ways: gojq always sorts object keys, has no `keys_unsorted`, no regex
look-around, and no `$__loc__`, and has the full date function set where jackson-jq has only `now`,
`todateiso8601` and `fromdateiso8601`.

## dws-jq

```sh
cd dws-jq
go test ./internal/conformance/ -v
```

The runner runs the whole corpus through the same compile-and-evaluate code that serves `POST /run`,
with the time zone set to UTC. It is part of `make test`, and `dws-jq`'s CI workflow also runs when
anything under `conformance/expressions/` changes. Set `DWS_CONFORMANCE_EXPRESSIONS=<dir>` to run another
copy of the corpus.
