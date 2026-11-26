# ADR 0015: Expression Sites Follow the Rule of Each Field's Current Evaluator

- **Status:** Proposed (2026-11-27), with OpenSpec change `controller-jq-expression-sites` (roadmap row 2m.1)
- **Date:** 2026-11-27
- **Supersedes:** none. It refines [ADR 0014](0014-dws-jq-expression-function.md) Decision 7 and
  Decision 9 O-2, which stay in force.
- **Context:** `openspec/changes/controller-jq-expression-sites/design.md`,
  [`docs/roadmaps/workflow-runtime-architecture-roadmap.md`](../roadmaps/workflow-runtime-architecture-roadmap.md)
  rows 2m and 2.0d.
- **Related:** [ADR 0001](0001-workflow-runtime-v2-decisions.md) (app-ID sanitization),
  [ADR 0012](0012-data-flow-in-v2-node-handlers.md) (data flow), [ADR 0014](0014-dws-jq-expression-function.md).

## Context

ADR 0014 makes the controller emit one `dws-jq` site per expression. O-2 describes the template
rewrite with two detection rules: in `set` every string is an expression, and elsewhere only `${ }`
strings are. The inventory lists which variables each Flow site binds.

Building the controller side (2m.1) needed the exact rules v1 applies today, since migrations 5.3 and
2k must not change results. A read of `dws-orchestrator`, `dws-call-openapi` and `dws-call-a2a` shows
that the rules are per field and more numerous than O-2 says. It also shows that parts of the inventory
and of the Decision 9 host table do not describe v1. The decisions below are durable: 2m.2, 2k, 5.3,
3b to 3g and 6b all consume the site list.

## Decision 1: One detection rule per field, taken from the evaluator that runs it today

| Rule | Fields | Today's evaluator |
|---|---|---|
| whole: always an expression, wrapped or not | `for.in`, `for.while`, `switch` `when`, catch / retry `when` and `exceptWhen`, `correlate.from`, string `until`, and the string form of `set`, `input.from`, `output.as` and `export.as` | `JqEvaluator.unwrap` |
| template: wrapped (`trim`, `${`…`}`) string leaves anywhere | the object form of `input.from`, `output.as` and `export.as` | `JqEvaluator` template walk |
| top-level: each top-level string value, wrapped or not; nested values are constants | `set` object form, `call: openapi` `with.parameters` | `EvaluateSetActivity`; `dws-call-openapi` |
| balanced: brace-balanced whole-string wrapper, untrimmed | `call: a2a` `with.parameters` | `dws-call-a2a` |
| raise: SDK regex `^\s*\$\{.+\}\s*$` (no DOTALL) per field | `raise.error` `type`, `title`, `instance`, `detail` | SDK error setters |
| template, by the DSL rule, because nothing evaluates these today | `emit.event.with`, `listen` / `schedule.on` filters, `correlate.expect`, the http / grpc / asyncapi / run arguments | none |

Task-level `if`, durations, and `catch.errors.with` are not sites. v1 does not evaluate any of them
as jq.

## Decision 2: Site identity is an owner path plus a site name

- The owner path is one of: `<task>`, `<task>.catch`, `<task>.to.<i>`, `<task>.to`, `<workflow>`,
  `<workflow>.on.<i>`, `<workflow>.on`.
- List items are named by 0-based index.
- `id = <owner>.jq.<site>`, and `appId` is the v2 node rule applied to `id`.
- The owner path is not always the caller. A `<task>.catch` site is called by the `<task>` controller.
- Collisions are checked among sites and against every app ID the version claims.

## Decision 3: Declared variables are v1's scope plus the inventory's `$context`

- Enclosing `for` `each` / `at` names and `catch.as` names are declared outermost first, then the site's
  own.
- Catch and retry conditions get only `context` and the error name.
- Event-bound sites (`data`, `correlate.from`, trigger `until`) get none.
- Names are de-duplicated and checked against `dws-jq`'s rule.

## Decision 4: The finder is separate from `compile()` until a runtime needs it

The finder runs on the raw definition tree and takes the version's claimed app IDs. It is not called
from either compiler in 2m.1, so `DeploymentPlan` and accept/reject behavior stay unchanged. 2m.2,
5.3a and 6b turn its errors on when they deploy `dws-jq`.

## Corrections to ADR 0014 found in 2m.1

1. **O-2's two rules are incomplete.** v1's `set` evaluates top-level strings only, so a nested
   `${ }` in `set` is a literal. Outside `set`, the string form of the data-flow fields and every
   condition are expressions without `${ }`. `raise` uses the SDK regex.
2. **The inventory's "bound as" column**:
   - v1 binds no `$context` in `for.in` or `for.while`, only scope variables.
   - The column also leaves out enclosing scope variables, which v1 inherits into nested loops and
     `catch.do`.
   - Catch conditions match the column: `$context` and the error only.
3. **Decision 9's host table** says `dws-orchestrator` evaluates "everything v1 evaluates", including
   `emit` and the `call` / `run` arguments.
   - The orchestrator never evaluates `emit.event`, `listen` filters or any `with`. `emit` publishes
     the current data, and `call: http` ignores `with.body`.
   - Only `dws-call-openapi`, `dws-call-asyncapi` and `dws-call-a2a` evaluate arguments, each by its
     own rule.
4. **Decision 7's example `fulfillOrder.catch.jq.when`** names a path (`fulfillOrder.catch`, the catch
   body), while the inventory's owner is the try-catch controller `fulfillOrder`. The two are
   different nodes, so the access policy (2m.2) must map the path to the caller.
5. **Decision 7's collision rule** says nothing about collisions between two sites. Two correlation
   keys that kebab-case alike (`orderId`, `order-id`) are one.
6. **Task-level `if`** is listed as "not evaluated by v1". It is also not rejected: v1 runs the task
   unconditionally. This is a silent behavior gap, worth a separate fix.

## Consequences

- One finder serves v1, v2 and triggers. Every later migration (2k, 5.3, 2l) moves an evaluation
  without changing which strings are expressions.
- v1 quirks are kept on purpose: `"${ a } and ${ b }"` is one wrapped string, and multi-line raise
  literals stay literal. Removing them later is a deliberate DSL-conformance change.
- `dws-jq`'s variable-name and `bulk` rules now exist in two places. The root `CLAUDE.md` contract
  bullet names both.
- `templates/rewrite-*` corpus cases, pinned by a controller test, make gojq prove v1's results for
  each template shape.
