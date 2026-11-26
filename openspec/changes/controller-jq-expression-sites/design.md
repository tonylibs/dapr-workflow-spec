## Context

[ADR 0014](../../../docs/adr/0014-dws-jq-expression-function.md) puts every jq evaluation in a
per-expression `dws-jq` function:
- Decision 7 defines a site's identity, `<owner>.jq.<site>`.
- Decision 9 O-2 defines how a template field becomes one expression.
- Decision 9 O-5 decides that `dws-jq` reads only the `$secrets` names its expression uses.

`dws-jq` 0.1.0 has shipped. Its env contract (`dws-jq/README.md`, pinned by
`dws-jq/testdata/contract/`) is `JQ_EXPRESSION`, `JQ_VARIABLES`, `JQ_TIMEOUT`, `JQ_SECRET_STORE` and
`JQ_SITE`. It enforces these rules at startup:
- variable names match `^[A-Za-z_][A-Za-z0-9_]*$`, are unique, and are not `secrets`, `ENV` or
  `__loc__`;
- only static `$secrets` access is allowed;
- no secret may be named `bulk`.

Today's controller has no notion of an expression:
- `V1OrchestratorCompiler` walks the SDK model and deploys only I/O steps. It does not walk nested
  `do` lists, and it rejects `schedule.on` in `TimeSchedule`.
- `V2StructuralCompiler` walks the raw tree alongside the SDK model (`NodeClassifier`, `RawTaskList`).
  It derives node app IDs with `NodeNaming.appId` (`Names.kebab` plus a DNS-1123 check of at most 63
  characters) and checks them with `AppIdRegistry`.

What v1 does today was mapped from `dws-orchestrator` (files under
`src/main/java/io/dws/orchestrator/`). It is the behavior the rewrite must reproduce:

| Fact | Where |
|---|---|
| `isWrapped` is `trim().startsWith("${") && trim().endsWith("}")`. `unwrap` trims, strips `${` and `}`, then trims again. There is no interpolation, and `"${ a } and ${ b }"` counts as wrapped. | `JqEvaluator:97-103,145-154` |
| The template walk is recursive, keys are never evaluated, and only wrapped string leaves are evaluated. It is used **only** for the object form of `input.from`, `output.as` and `export.as`. | `JqEvaluator:63-94`; `DataFlowPipeline:144-149` |
| The string form of `from` / `as` is always jq, wrapped or not. | `DataFlowPipeline:144-145` |
| `set` evaluates **top-level** string values only, wrapped or not. Nested values are copied verbatim. | `EvaluateSetActivity:88-91` |
| `switch.when`, `for.in`, `for.while`, and catch / retry `when` and `exceptWhen` are always jq. A blank value means absent. | the `Evaluate*Activity` classes; `CatchPolicy` |
| `raise` uses the SDK setter regex `^\s*\$\{.+\}\s*$` (no DOTALL) per field, and `.asText()` on each result. | `RaiseErrorActivity` |
| The first output is the result, `empty` gives `null`, and truthiness is not `null` and not `false`. | `JqEvaluator:48-51,113-123` |
| Scope variables come from the `for` `each` / `at` names (defaults `item` / `index`) and the `catch.as` name (default `error`), inherited into nested lists. Catch conditions see only `$context` and the error. `$context` is bound only in data flow and catch. `$secrets` is bound only in `set` and `switch`. | `InterpreterWorkflow:767-776,909-910`; `CatchPolicy:110-135` |
| Task-level `if`, `emit.event`, `listen` filters and `call` / `run` `with` are **never** evaluated by the orchestrator. Images evaluate some arguments: `dws-call-openapi` runs every parameter string as bare jq; `dws-call-a2a` uses a brace-balanced whole-string wrapper; `dws-call-http`, `dws-call-grpc` and `dws-run` evaluate none. | `InterpreterWorkflow:443-486`; `dws-call-openapi/src/jq.ts`; `dws-call-a2a/.../jq_eval.py:85-134` |

## Goals / Non-Goals

**Goals:**
- One pure finder: a definition goes in, and an ordered list of sites comes out, with
  `{owner, site, id, appId, expression, variables, secrets}`. The v1, v2 and 6b paths can all call it.
- Reproduce, field by field, the rule today's evaluator uses to decide what is an expression.
- Rewrite each template field into one expression (O-2), deterministically.
- Detect every error `dws-jq` would raise from its configuration: names, secrets and empty
  expressions. Also detect app-ID collisions.
- Golden tests, plus corpus round-trip cases that `dws-jq` runs.

**Non-Goals:**
- Wiring the finder into `compile()`, `DeploymentPlan` or `/plan`; deploying; access policy; secret
  scopes; `min-scale`. All of that is 2m.2.
- jq syntax validation (`dws-jq` startup; EX-2).
- Deciding which sites may use `$secrets` (2.0d), or whether Step sites should gain `$context`
  (2k.1).
- Task-level `if`, duration expressions, and any runtime change.

## Decisions

### D1: The finder is a separate step, not wired into `compile()` yet

`ExpressionSiteFinder.find(JsonNode rawSpec, String workflow, ClaimedAppIds claimed)` returns
`List<ExpressionSite>` or throws `CompilationException`. `ClaimedAppIds.of(DeploymentPlan)` lists:
- for v1, each `StepService.name()` and `orchestrator().appId()`;
- for v2, every `CompiledNode.appId()` and `StepNode.functionAppId()`.

Each entry carries its claimant text.

- **Why**: wiring it in would change behavior that this change promises to keep.
  - It would reject definitions v1 accepts today, for example a v1 `set` using `$secrets[.k]`,
    which works on jackson-jq.
  - It would add a field to the `/plan` JSON.
  - v1 does not call `dws-jq` until 5.3, and v2 not until 2k and Phase 4. Turning the errors on
    belongs with the deploy that needs them (2m.2, 5.3a, 6b).
- **Alternative rejected**: call it from both `compile()` methods and add `jqSites` to
  `DeploymentPlan`. That breaks the "existing compile output unchanged" acceptance, and enforces
  rules before any runtime depends on them.

### D2: Walk the raw JSON tree, not the SDK model

The finder reads `SpecParser.readRaw`'s `JsonNode`:
- it keeps strings exactly as written, needed for `trim` and regex parity;
- it keeps definition key order, needed for deterministic rewrite text;
- it keeps union forms that the SDK deserializers normalize, such as `set`'s map, `input.from`'s
  string versus object form, and `retry` as a name or inline.

The walk visits `do`, `for.do`, `try`, `catch.do` and `fork.branches` depth-first in source order. That
is a superset of both compilers' walks: v1 skips nested `do`, but the orchestrator still runs it.
`use.retries` and `use.errors` references are resolved from the same raw tree.
- **Alternative rejected**: the SDK model. It loses the raw strings (raise setters already split them
  into expression and literal), and it has no `schedule.read` support.

### D3: Each field uses the detection rule of the evaluator that runs it today

There are six rules: `whole`, `data-flow`, `template`, `top-level`, `balanced` and `raise`. They are
specified in the spec's field table. O-2 named two rules ("in `set` every string; elsewhere `${ }`
strings"), but v1 actually has more:
- `set` is top-level only.
- The string forms of `from` / `as` and the conditions are always expressions.
- `raise` uses the SDK regex.
- Where an image evaluates its own arguments (openapi, a2a), that image's rule is the rule for its
  field, because O-4 moves exactly those evaluations to `dws-jq`.

Where nothing evaluates a field today, there is no v1 behavior to keep, so the DSL's own `${ }` rule
(`template`) applies. That covers `emit`, `listen`, `schedule.on`, and the http / grpc / asyncapi /
run arguments.
- **Why**: "reproduce v1 exactly" means one rule per field, matching the evaluator. A single rule would
  silently change results. For example, a bare `.y` in `set` would become a literal `".y"`, and a
  nested `${ }` in `set` would start evaluating.
- **Alternative rejected**: one uniform rule. It is simpler, but it breaks parity in both directions.
- **Choice under ambiguity**: openapi today runs `${ .x }` as the invalid program `(${ .x })`.
  Unwrapping it (a `top-level` part) is a strict superset: valid definitions keep their meaning.

### D4: Rewrite format

The spec fixes the exact text: `{ "k": v }`, `[ a, b ]`, `([ expr ] | .[0])`, and compact JSON for
constants and part-free subtrees. A single part at the root is emitted bare. Constants are serialized
with an order-preserving Jackson mapper. That is the existing `orderedJson`, not the key-sorted
`json` used for digests.
- A part whose text ends inside a `#` comment gets a newline before ` ] | .[0])`. Otherwise the
  comment would eat the wrapper and the site would fail at `dws-jq` startup for a definition v1 runs
  fine.
- Keys are written as JSON strings. jq accepts every JSON string escape, and a literal `\(` in a key
  is escaped as `\\(`, so no key can open an interpolation.
- The first-output rule holds because `([ e ] | .[0])` gives `e`'s first output, or `null`. One
  difference from v1 remains: jackson-jq collects all outputs eagerly, so `1, error("x")` fails in
  v1. gojq inside an array also evaluates `e` fully, so both fail. Parity holds.

### D5: Owner, site and ID naming; one shared app-ID rule

The owner paths and site names are the spec's table:
- `<task>`, `<task>.catch`, `<task>.to.<i>`, `<task>.to`, `<workflow>`, `<workflow>.on.<i>` and
  `<workflow>.on`;
- list items use 0-based indexes, not case or branch names, because those can sanitize alike.

`id` = `<owner>.jq.<site>` and goes unsanitized to `JQ_SITE`. `appId` = `id` through the v2 node rule.
That rule moves from `compile.v2.NodeNaming.appId` into a public `compile.AppIds.derive(String id,
String subject)`, and `NodeNaming.appId` delegates with subject `node '<nodeId>'`. Its
`CompilationException` messages stay verbatim (controller style guide: moved code keeps its
messages).
- **Why indexes**: `switch` case names and correlation keys are free text. Indexes are deterministic,
  and the collision check covers what kebab-casing still folds together.
- **Note for 2m.2**: the ID prefix is a path, not always the caller. The `<task>.catch` sites are
  called by the `<task>` try-catch controller, `listen` sites by the `<task>` node or its bridge, and
  trigger sites by `dws-schedule-event`. 2m.2 maps the owner path to the caller's app ID for the
  access policy.

### D6: Declared variables are v1's scope plus the inventory's `$context`

The spec fixes the order and contents. `context` is declared where the ADR 0014 inventory binds it,
including `for.in` and `for.while`, where v1 does not bind it. Declaring an extra name is harmless: an
expression that is valid in v1 still compiles. The enclosing scope variables are what v1 binds. The
inventory leaves them out, but nested loops depend on them.
- Names are de-duplicated, keeping the first occurrence. v1 shadows silently, and `dws-jq` refuses
  duplicates.
- Each name is checked at the site that declares it, with `dws-jq`'s rule.
- A loop variable that no site declares (for example `each: my-item` with no `while` and no nested
  expression) is not an error, because v1 accepts it and no `dws-jq` would ever see it.

### D7: `$secrets` scan with a small jq lexer, not a parser

`JqLexer` tokenizes enough jq to find `$secrets` safely:
- string literals, with escapes and nested `\( … )` interpolation;
- `#` comments to end of line;
- `$name` tokens;
- punctuation.

`SecretScanner` applies the spec's static-form grammar to the token following each `$secrets`.
- **Why not jackson-jq or another Java jq parser**: its dialect is not gojq's (ADR 0014 Decision 6).
  Also, only a token stream is needed. Every form `dws-jq` accepts is a short token sequence, and
  everything else is rejected.
- **Failure direction**: if the lexer accepts something `dws-jq` rejects, the site fails at startup.
  That is safe and visible. If it rejects something `dws-jq` accepts, a definition is refused at
  compile time. To limit that, the accepted grammar is exactly the README's three forms, with
  whitespace allowed where gojq allows it.

### D8: Errors are collected, then thrown once

This follows the `semanticErrors` pattern: `new CompilationException(List<String>)`. Each message
starts with `expression site '<id>' (owner '<owner>', site '<site>')`. Collisions name both claimants,
as `AppIdRegistry` does.

### D9: Round trip through the corpus, pinned from the controller

The controller has no gojq. Each rewrite shape gets a fixture under
`dws-controller/src/test/resources/jq-sites/rewrite/<shape>.json`:

```json
{"case": "templates/rewrite-object", "rule": "template", "field": {...}}
```

`RewriteCorpusPinTest` reads `../conformance/expressions/cases/<case>.yaml` with Jackson YAML and
asserts that `expression` equals the finder's rewrite. Each case's `expect` is v1's result for that
field, cited by `source`, and `dws-jq`'s corpus runner checks it on gojq. This is the
`SchedulerContractTest` pattern: the controller pins the text, and the other side runs it.
- **Alternative rejected**: jackson-jq as a test dependency of the controller. It would compare
  jackson-jq with jackson-jq and say nothing about gojq. EX-1 runs the same corpus on jackson-jq
  later.

### D10: Mapping onto `dws-jq`'s configuration contract

| Site field | `dws-jq` / 2m.2 |
|---|---|
| `expression` | `JQ_EXPRESSION` |
| `variables` | `JQ_VARIABLES` (JSON array) |
| `id` | `JQ_SITE` |
| `appId` | Knative Service name and Dapr app ID |
| `secrets` non-empty | `JQ_SECRET_STORE` (the version's store) plus the Dapr secret scope listing exactly these names |
| (none) | `JQ_TIMEOUT` (no DSL source; default `5s`), `PORT`, `DAPR_*`, `DWS_LOG_FORMAT` (2m.2) |

The variable-name rule and the `bulk` rule are copied from `dws-jq`, not shared. The root `CLAUDE.md`
`dws-jq` contract bullet gains a sentence naming the controller copy, and 2m.2's fixture comparison
will pin the env names.

### Package layout

`io.dws.controller.compile.jq` holds the public classes `ExpressionSite` (record),
`ExpressionSiteFinder` and `ClaimedAppIds`, plus these package-private classes:
- `SiteWalker`: the raw walk, owners and the scope stack;
- `FieldRule`: the six detectors;
- `TemplateRewriter`;
- `JqLexer` and `SecretScanner`;
- `SiteVariables`.

Following the style guide, each rule that end-to-end tests cannot reach has its own type and its own
unit tests.

## Risks / Trade-offs

- [The lexer and gojq disagree on an edge, such as `$ secrets` with a space, or a keyword after
  `.`] → It accepts only the README's forms. Unit vectors mirror `dws-jq`'s accepted and rejected list.
  A false accept fails safe at `dws-jq` startup.
- [Rules are duplicated from `dws-jq` (names, `bulk`)] → The contract bullet names both copies, and
  2m.2's fixture test will pin the env side.
- [Per-field rules faithfully keep v1 quirks, such as `"${ a } and ${ b }"`, or a multi-line raise
  literal] → This is intended. The quirks are listed in ADR 0015 so a later DSL-conformance change can
  remove them deliberately.
- [Golden files go stale] → A `-Ddws.writeJqSiteGoldens=true` switch regenerates them. A diff is then
  reviewed like any other change.
- [A future SDK or DSL field carries an expression the table misses] → The `all-sites` golden is the
  inventory. A new field means a new table row and a new golden entry.

## Migration Plan

None. The change only adds a library step and tests. Rollback is a revert. 2m.2 wires the finder into
the deploy path.

## Open Questions

1. **`set` scope (handoff versus v1)**: the handoff says "in `set` every string is an expression".
   v1 evaluates top-level strings only, so this design follows v1. Confirm, or decide that 2k / 5.3
   should start evaluating nested strings (a behavior change).
2. **Wiring (D1)**: confirm that turning the errors on belongs to 2m.2 / 5.3a / 6b, not to 2m.1.
3. **Step sites and `$context`**: v1 binds it only in data flow and catch. OWS allows it everywhere.
   2k.1 decides whether `set` and friends declare it.
4. **`$secrets` names not in `use.secrets`**: not checked here. 2m.2 needs them declared to grant
   scopes. Decide whether that is a 2m.2 compile error.
5. **ADR 0014 corrections**: the repository has added dated notes to ADR 0014 before ("2j notes").
   This workflow's rule is that ADRs are immutable, so the corrections are recorded in a new ADR 0015.
   If you prefer a "2m.1 notes" section in ADR 0014, ADR 0015 can be dropped.
