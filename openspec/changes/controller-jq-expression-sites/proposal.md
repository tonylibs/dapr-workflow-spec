## Why

`dws-jq` (roadmap 2j) is released as 0.1.0, but nothing tells it what to evaluate. Its configuration
contract (`JQ_EXPRESSION`, `JQ_VARIABLES`, `JQ_SITE`, secret names) has to come from the controller,
which today has no notion of an "expression site". Roadmap row **2m.1** is that missing piece. It is
the first step of 2m, and 2m.2 (deploy), 2k.1 (`dws-step`), 5.3a (v1) and OWS 6b (`schedule.on`) all
wait on it. ADR 0014 Decision 7 and Decision 9 O-2 say *what* a site is. This change makes the
controller produce sites at compile time, reproducing the expression rules v1 applies today. Nothing
is deployed.

## What Changes

- **New compile step in `dws-controller`: the expression-site finder.** Given a definition's raw tree,
  the workflow name and the app IDs the compiled version already claims, it returns an ordered list
  of expression sites. Each site has:
  - its owner (a task, `<task>.catch`, `<task>.to.<i>`, the workflow, or the trigger `<workflow>.on.<i>`)
    and its site name;
  - the derived ID `<owner>.jq.<site>`, and its app ID, sanitized by the same DNS-1123 rule as every v2
    node;
  - the final expression text;
  - the variable names it may use;
  - the static `$secrets` names it reads.

  The finder is one pure function that the v1 compiler, the v2 compiler and the 6b trigger path can all
  call. It is **not wired into either compiler's `compile()` yet**, so compile output and
  accept/reject behavior stay byte-for-byte the same (see design D1).
- **Coverage.**
  - Flow-side sites from the ADR 0014 inventory: `for.in` and `for.while`; catch and retry `when` and
    `exceptWhen`; `listen` filters, correlation and `until`; and `schedule.on` `with.data`,
    `correlate.from` and `until`.
  - Step-side sites that Decision 9 moves to `dws-jq`: `set`, `switch` `when`, `emit`, `raise`,
    `input.from`, `output.as`, `export.as` (task and workflow level), and the `call`/`run` arguments.
  - Task-level `if` is **left out**: v1 ignores it today (nothing reads `TaskBase.getIf()`).
- **Template rewrite (O-2).**
  - A field with `${ }` parts becomes one jq expression: objects become `{ "key": (…) }` with keys
    quoted, arrays become `[ … ]`, constants are written as JSON, and each part becomes
    `([ expr ] | .[0])`.
  - A field that is a single bare `${ expr }` becomes `expr`.
  - A field with no parts produces no site.
  - Which strings count as parts follows the rule v1 (or the image) applies to that exact field today
    (design D3). One consequence differs from the handoff wording: v1's `set` evaluates **top-level**
    string values only, so a `${ }` string nested inside a `set` value stays a constant.
- **`$secrets` scan.** A small jq lexer, aware of strings, interpolation and comments, collects
  `$secrets.name`, `$secrets."name"` and `$secrets["name"]`. Any other use of `$secrets`, and a secret
  named `bulk`, is a compile error. The same forms are rejected at startup by `dws-jq`.
- **Compile errors**, each naming owner and site, all collected before one throw:
  - an app-ID collision with another site or with any app ID the version claims;
  - an invalid derived app ID;
  - a dynamic, bound or `bulk` `$secrets` use;
  - a variable name `dws-jq` would refuse;
  - an empty expression.
- **Shared app-ID rule.** v2's DNS-1123 sanitization is extracted so sites and nodes use one rule. v2's
  existing messages are kept verbatim.
- **Round-trip corpus cases.** New `conformance/expressions/cases/templates/rewrite-*.yaml` cases hold
  the controller's rewrite output as their `expression`, with v1's result for the same field as
  `expect`. A controller test pins each fixture's rewrite to its case. `dws-jq`'s corpus runner proves
  that gojq gives v1's answer.

## Capabilities

### New Capabilities
- `jq-expression-sites`: the controller's compile-time expression-site finder. It covers which
  definition fields are sites, how each field's expressions are detected and rewritten into one jq
  expression, owner, site and ID naming, declared variables, the static `$secrets` scan, and the
  compile errors.

### Modified Capabilities
- `jq-expression-conformance`: adds a requirement for controller-pinned `templates/rewrite-*` cases.
  The case format and runner rules are unchanged.

## Impact

- **Components**:
  - `dws-controller`: new package `io.dws.controller.compile.jq`, a shared app-ID helper, and tests. Its
    gate is `./mvnw verify`.
  - `conformance/expressions/`: new cases only.
  - `dws-jq` is unchanged in code, but its gate (`make lint && make test`) must pass on the new cases.
  - Each package keeps its own build; there is no cross-package Java or Go dependency.
- **DSL behavior**: none. Definitions accepted today are still accepted, and nothing new is rejected,
  because the finder is not called from `compile()` yet. Its errors are what 2m.2 / 5.3a / 6b will turn
  on.
- **Deployed resources**: none. `DeploymentPlan` and the `/plan` dry-run JSON are unchanged.
- **Runtime interpretation**: none. The definitions sent to `dws-orchestrator`, `dws-flow` and
  `dws-step` are untouched.
- **Contract produced** (consumed by 2m.2): a site maps one-to-one onto `dws-jq`'s env:
  - `expression` → `JQ_EXPRESSION`;
  - `variables` → `JQ_VARIABLES`;
  - `id` → `JQ_SITE`;
  - `appId` → the Knative Service name and Dapr app ID;
  - a non-empty `secrets` → `JQ_SECRET_STORE` plus the secret scope.
- **Non-goals**:
  - deploying, access policies, secret scopes and `min-scale` (all 2m.2);
  - deciding which expressions may use `$secrets` (2.0d);
  - jq syntax validation (`dws-jq` startup; EX-2);
  - task-level `if`;
  - `wait` and `timeout` duration expressions, which v1 does not evaluate as jq;
  - changing any runtime caller.
- **Compatibility**: none for existing definitions. Two v1 quirks are reproduced on purpose: a nested
  `set` value is not evaluated, and `"${ a } and ${ b }"` counts as one wrapped string. Where an image's
  rule differs from the orchestrator's, the image's rule is used for that image's fields (design D3).
