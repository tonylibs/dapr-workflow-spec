## ADDED Requirements

### Requirement: Expression-site finder is one compile-time step shared by every compiler

Owning component: `dws-controller`.

The controller SHALL provide one pure, framework-free function, the expression-site finder. Its
inputs are a definition's raw JSON tree (as parsed by `SpecParser`, YAML or JSON), the kebab-cased
workflow name, and the app IDs the compiled version already claims, each with a description of its
claimant. Its output is the ordered list of the definition's expression sites. It SHALL make no
Kubernetes call and SHALL not depend on which compiler strategy is active, so the v1 compiler, the
v2 compiler and the OWS 6b trigger path call the same code.

In this change, neither `V1OrchestratorCompiler.compile` nor `V2StructuralCompiler.compile` calls
the finder. Their `DeploymentPlan`, their `/plan` JSON, and the set of definitions they accept or
reject SHALL be unchanged.

The controller SHALL also provide a way to list the app IDs a `DeploymentPlan` claims:
- for v1, every `StepService` name and the orchestrator app ID;
- for v2, every node app ID and every function app ID in `flowStepGraph`.

#### Scenario: Same sites from either compiler's view of a definition
- **GIVEN** a definition that both compilers accept
- **WHEN** the finder runs with the app IDs claimed by the v1 plan, and again with the app IDs
  claimed by the v2 plan
- **THEN** both runs return the same sites in the same order

#### Scenario: Compile output is unchanged
- **GIVEN** every definition fixture the existing compiler tests use
- **WHEN** each is compiled by the v1 and the v2 strategy after this change
- **THEN** each `DeploymentPlan` is equal to the one produced before this change
- **AND** no definition that compiled before is rejected

#### Scenario: Sites are listed in definition order
- **GIVEN** a definition whose workflow-level `input.from` is an expression, followed by task `a`
  (a `set`) and then task `b` (a `for` with `in` and `while`)
- **WHEN** the finder runs
- **THEN** the sites are listed in this order: the workflow `input.from` site, `a`'s `set` site,
  `b`'s `in` site, then `b`'s `while` site
- **AND** tasks nested in `do`, `for.do`, `try`, `catch.do` and fork branches appear where they occur
  in a depth-first walk in source order

### Requirement: A site carries owner, name, ID, app ID, expression, variables and secrets

Each site SHALL have these fields:
- `owner`: the dotted path of what the expression belongs to. This is a task name, `<task>.catch`,
  `<task>.to.<i>`, `<task>.to`, the kebab-cased workflow name, `<workflow>.on.<i>` or `<workflow>.on`.
- `site`: the site name within the owner.
- `id`: `<owner>.jq.<site>`, unsanitized. It is the value for `dws-jq`'s `JQ_SITE`.
- `appId`: `id` sanitized by the same rule as every compiled v2 node (`Names.kebab`, then a DNS-1123
  label of at most 63 characters). It is the Knative Service name and Dapr app ID that 2m.2 renders.
- `expression`: the final jq expression text, which becomes `JQ_EXPRESSION`.
- `variables`: an ordered, duplicate-free list of variable names without `$`. It never contains
  `secrets`, and becomes `JQ_VARIABLES`.
- `secrets`: the sorted, duplicate-free set of secret names the expression reads statically. A
  non-empty set means 2m.2 sets `JQ_SECRET_STORE` and a secret scope.

#### Scenario: ADR 0014 identity examples
- **GIVEN** a `for` task `processItems` with a `while`, a `try` task `fulfillOrder` whose catch has
  `when`, and a workflow `order-flow` whose `schedule.on` first filter has `with.data` given as an
  expression
- **WHEN** the finder runs
- **THEN** three sites have the IDs `processItems.jq.while`, `fulfillOrder.catch.jq.when` and
  `order-flow.on.0.jq.data`
- **AND** their app IDs are `process-items-jq-while`, `fulfill-order-catch-jq-when` and
  `order-flow-on-0-jq-data`

### Requirement: The finder covers every field that carries a jq expression

Each row below SHALL produce at most one site per occurrence. A site is produced only when the
field's detection rule (next requirement) finds an expression. `<i>` is a 0-based list index; a
`one` consumption strategy counts as index `0`; `<k>` is a correlation key.

| DSL place | Owner | Site | Rule |
|---|---|---|---|
| workflow `input.from` | `<workflow>` | `input.from` | data-flow |
| workflow `output.as` | `<workflow>` | `output.as` | data-flow |
| task `input.from` | `<task>` | `input.from` | data-flow |
| task `output.as` | `<task>` | `output.as` | data-flow |
| task `export.as` | `<task>` | `export.as` | data-flow |
| `for.in` | `<task>` | `in` | whole |
| `for.while` | `<task>` | `while` | whole |
| `try.catch.when` / `exceptWhen` | `<task>.catch` | `when` / `exceptWhen` | whole |
| `try.catch.retry.when` / `exceptWhen`, inline or via `use.retries` | `<task>.catch` | `retry.when` / `retry.exceptWhen` | whole |
| `set` | `<task>` | `set` | top-level |
| `switch[<i>].<case>.when` | `<task>` | `switch.<i>.when` | whole |
| `raise.error`, inline or via `use.errors` | `<task>` | `raise` | raise |
| `emit.event.with` | `<task>` | `emit` | template |
| `call: http` `with.headers` / `with.query` / `with.body` | `<task>` | `with.headers` / `with.query` / `with.body` | template |
| `call: openapi` `with.parameters` | `<task>` | `with.parameters` | top-level |
| `call: grpc` `with.arguments` | `<task>` | `with.arguments` | template |
| `call: asyncapi` `with.message` | `<task>` | `with.message` | template |
| `call: a2a` `with.parameters` | `<task>` | `with.parameters` | balanced |
| `run.shell` / `run.script` `arguments` / `environment` | `<task>` | `run.arguments` / `run.environment` | template |
| `listen.to` filter `with`, without `data` | `<task>.to.<i>` | `with` | template |
| `listen.to` filter `with.data` | `<task>.to.<i>` | `data` | template |
| `listen.to` filter `correlate.<k>.expect` | `<task>.to.<i>` | `correlate.<k>.expect` | template |
| `listen.to` filter `correlate.<k>.from` | `<task>.to.<i>` | `correlate.<k>.from` | whole |
| `listen.to.any.until`, string form | `<task>.to` | `until` | whole |
| `schedule.on` filter `with.data` | `<workflow>.on.<i>` | `data` | template |
| `schedule.on` filter `correlate.<k>.from` | `<workflow>.on.<i>` | `correlate.<k>.from` | whole |
| `schedule.on.any.until`, string form | `<workflow>.on` | `until` | whole |

An `until` given as a nested consumption strategy has its filters walked like `to` filters. Their
owner is `<task>.until.<i>` (or `<workflow>.until.<i>` for `schedule.on`).

These fields SHALL NOT produce sites:
- task-level `if`, which v1 does not evaluate;
- `wait`, task `timeout` and workflow `timeout` durations, and `retry.delay`, which v1 parses as
  durations and never evaluates as jq;
- `for.each`, `for.at` and `catch.as`, which are variable names;
- `catch.errors.with`, which v1 matches by string equality;
- `call` endpoints, documents and operation IDs, which the controller requires to be static;
- `run.shell.command` and `run.script.code`.

#### Scenario: Every inventory site is found
- **GIVEN** the golden definition `all-sites`, which uses every row of the table at least once
- **WHEN** the finder runs
- **THEN** the returned list equals the golden site list for `all-sites`, field for field

#### Scenario: Task-level if is not a site
- **GIVEN** a `set` task with `if: ${ .enabled }` and `set: { flag: true }`
- **WHEN** the finder runs
- **THEN** no site has `site` `if`
- **AND** no site is produced for that task, because its `set` has no string values

#### Scenario: Retry policy referenced from use.retries
- **GIVEN** a `try` task `charge` whose `catch.retry` is the name `transient`, and
  `use.retries.transient.when` is `${ $error.status == 503 }`
- **WHEN** the finder runs
- **THEN** a site with owner `charge.catch`, site `retry.when` and expression
  `$error.status == 503` is produced

### Requirement: Each field's expressions are detected by the rule its evaluator uses today

The finder SHALL apply the rule named in the table above. Each rule reproduces the evaluator that
handles that field today: `dws-orchestrator`'s `JqEvaluator`, the Serverless Workflow SDK's error
setters, or the call image.

- **whole**: the field is one string, always an expression whether or not it is wrapped. If, after
  `String.trim()`, it starts with `${` and ends with `}`, the expression is the trimmed text between
  them. Otherwise the expression is the trimmed string. A null, missing or whitespace-only field
  produces no site (v1 treats it as absent, or as the default `switch` case).
- **data-flow**: a string field is handled as **whole**. An object or array field is handled as
  **template**.
- **template**: the field is walked recursively, and object keys are never expressions. A string leaf
  is a part when it is *wrapped*: after `String.trim()` it starts with `${` and ends with `}`. Its
  expression is the trimmed inner text. There is no brace balancing and no interpolation:
  `"${ a } and ${ b }"` is one part whose expression is `a } and ${ b`. Every other leaf, including a
  string that only contains `${`, is a constant.
- **top-level** (`set`, `call: openapi` parameters): a string field is handled as **whole**. In an
  object field, each top-level value that is a string is a part, wrapped or not, unwrapped as in
  **whole**. Every other value, including nested objects and arrays and any `${ }` strings inside
  them, is a constant.
- **balanced** (`call: a2a` parameters): a string field is handled as **whole**. An object or array
  field is handled as **template**, except that a leaf is wrapped only when the `{` of a leading `${`
  closes exactly at the last character. Braces inside jq double-quoted strings, with `\"` escapes, are
  not counted, and the string is not trimmed. This is `dws-call-a2a`'s
  `_match_wrapped_expression`.
- **raise**: each of `type`, `title`, `instance` and `detail` is a part only if it matches
  `^\s*\$\{.+\}\s*$` without DOTALL (the SDK's setter regex). The part is unwrapped as in **whole**.
  `status` and every non-matching field are constants. A field that is absent stays absent.

#### Scenario: set evaluates only top-level strings
- **GIVEN** `set: { a: "${ .x }", b: ".y", c: { d: "${ .z }" }, e: 1 }`
- **WHEN** the finder runs
- **THEN** the site's expression is
  `{ "a": ([ .x ] | .[0]), "b": ([ .y ] | .[0]), "c": {"d":"${ .z }"}, "e": 1 }`

#### Scenario: Outside set only wrapped strings are parts
- **GIVEN** a task with `output.as: { id: "${ .id }", source: "api", note: "cost ${ .x }" }`
- **WHEN** the finder runs
- **THEN** the `output.as` site's expression is
  `{ "id": ([ .id ] | .[0]), "source": "api", "note": "cost ${ .x }" }`

#### Scenario: A bare data-flow string is always an expression
- **GIVEN** a task with `input.from: .order`
- **WHEN** the finder runs
- **THEN** an `input.from` site with expression `.order` is produced

#### Scenario: Two placeholders in one string are one v1 part
- **GIVEN** a task with `export.as: { msg: "${ .a } and ${ .b }" }`
- **WHEN** the finder runs
- **THEN** the site's expression is `{ "msg": ([ .a } and ${ .b ] | .[0]) }`
- **AND** no compile error is raised, because jq syntax is checked by `dws-jq` at startup

#### Scenario: a2a uses its own balanced-wrapper rule
- **GIVEN** a `call: a2a` task with `with.parameters: { q: "${ .q }", label: "${a} ${b}" }`
- **WHEN** the finder runs
- **THEN** the site's expression is `{ "q": ([ .q ] | .[0]), "label": "${a} ${b}" }`

#### Scenario: raise keeps a multi-line expression literal, as the SDK does
- **GIVEN** a `raise` whose `title` is `${ .reason }` and whose `detail` is a two-line string
  starting with `${` and ending with `}`
- **WHEN** the finder runs
- **THEN** in the `raise` site's expression `title` is the part `([ .reason ] | .[0])`, and `detail`
  is a JSON string constant

### Requirement: A field with parts is rewritten into one jq expression

The rewrite SHALL be deterministic, so golden tests can compare text exactly:
- A field that is a single part, whether **whole** or a wrapped string at the field's root, SHALL be
  that part's expression unchanged, with no wrapper.
- Otherwise:
  - an object SHALL be written as `{ "<k1>": <v1>, "<k2>": <v2> }`, in definition key order, every
    key a JSON-escaped string; an empty object is `{}`;
  - an array SHALL be written as `[ <v1>, <v2> ]`; an empty array is `[]`;
  - a part SHALL be written as `([ <expr> ] | .[0])`;
  - a constant SHALL be written as its compact JSON serialization. A constant is a non-part leaf, or
    an object or array subtree that contains no part (for example `{"ok":true}`).
- When a part's expression ends inside a jq `#` comment, a newline SHALL be put before ` ] | .[0])`,
  so the comment cannot swallow the wrapper.
- A field with no parts SHALL produce no site. Its constant stays in the definition unchanged.

#### Scenario: Object, array and mixed constants
- **GIVEN** a task with `output.as: { items: [ "${ .a }", 2, null ], meta: { ok: true } }`
- **WHEN** the finder runs
- **THEN** the expression is
  `{ "items": [ ([ .a ] | .[0]), 2, null ], "meta": {"ok":true} }`

#### Scenario: Parts using def and as keep their bindings inside the part
- **GIVEN** a task with
  `output.as: { a: "${ def inc: . + 1; .n | inc }", b: "${ .n as $x | $x * $x }" }`
- **WHEN** the finder runs
- **THEN** the expression is
  `{ "a": ([ def inc: . + 1; .n | inc ] | .[0]), "b": ([ .n as $x | $x * $x ] | .[0]) }`

#### Scenario: A single bare field is just its expression
- **GIVEN** a task with `output.as: "${ .result }"`
- **WHEN** the finder runs
- **THEN** the expression is `.result`

#### Scenario: Constant-only field has no site
- **GIVEN** a task with `output.as: { source: "api", n: 1 }` and `set: { a: 1 }`
- **WHEN** the finder runs
- **THEN** no site is produced for that task

#### Scenario: Trailing comment does not swallow the wrapper
- **GIVEN** a task with `output.as: { a: "${ .a # the id }" }`
- **WHEN** the finder runs
- **THEN** the expression is `{ "a": ([ .a # the id` followed by a newline and ` ] | .[0]) }`

### Requirement: Declared variables follow v1's scope plus the ADR 0014 inventory

A site's `variables` SHALL be built in this order, keeping the first occurrence of each name:
1. `context`, when the site is a data-flow site, `for.in`, `for.while`, a catch or retry condition,
   or a `listen` `with` / `correlate.expect` / `until` site;
2. the enclosing scope variables, outermost first:
   - each enclosing `for` adds its `each` name (default `item`) and then its `at` name (default
     `index`);
   - an enclosing `catch.do` adds its `catch.as` name (default `error`);
   - scope variables pass into nested `do`, `try`, `catch.do` and fork-branch lists;
3. for `for.while`, the loop's own `each` and `at` names;
4. for a catch or retry condition, the catch's `as` name. Catch and retry conditions get **no**
   enclosing scope variables, because v1's catch policy binds only `$context` and the error.

These sites declare no variables: `listen` and `schedule.on` `data` and `correlate.from` (bound to
the event only), and the `schedule.on` `until`. `secrets` is never declared, because `dws-jq` binds
`$secrets` itself.

#### Scenario: Nested loop and catch scopes
- **GIVEN** a `for` task `outer` with `each: order`, whose body has a `try` task `t` with
  `catch.as: err`, whose `catch.do` has a `for` task `inner` (default `each`/`at`) with a `while`
- **WHEN** the finder runs
- **THEN** `inner`'s `while` site declares `["context", "order", "index", "err", "item"]`
- **AND** `t.catch`'s `when` site, if present, declares `["context", "err"]`

#### Scenario: Step sites get scope variables only
- **GIVEN** a `set` task directly under a `for` with `each: line`, `at: n`
- **WHEN** the finder runs
- **THEN** the `set` site declares `["line", "n"]`

### Requirement: Static $secrets names are collected and every other use is an error

The finder SHALL scan each final expression with a jq-aware lexer. The lexer skips string literals,
including escapes, and jq comments. It descends into `\( … )` interpolations. A `$secrets` token is
the characters `$secrets` not followed by an identifier character. Each such token SHALL be followed,
after optional whitespace, by exactly one of:
- `.` plus an identifier `[A-Za-z_][A-Za-z0-9_]*`;
- `.` plus a string literal with no interpolation;
- `[` plus a string literal with no interpolation, then `]`, with optional whitespace inside.

The name in each case is collected, with string escapes decoded. Any other use of the `$secrets`
token SHALL be a compile error naming owner, site and the form found. Examples: bare `$secrets`,
`$secrets | keys`, `$secrets[.k]`, `$secrets[]`, `"a" + .x` inside the brackets, `. as $secrets`,
`def f($secrets)`, `{$secrets}`.

A collected name equal to `bulk` SHALL be a compile error. `$secrets` text inside a string constant,
for example a non-`set` constant `"$secrets.x"` written as JSON, is not a use. `$secrets` is accepted
in every site kind; which sites may use it is decided in roadmap row 2.0d.

#### Scenario: Static forms are collected
- **GIVEN** a `set` whose top-level values are `$secrets.apiKey`,
  `${ $secrets["db-password"] }` and `"Bearer \($secrets."token")"`
- **WHEN** the finder runs
- **THEN** the site's `secrets` is `["apiKey", "db-password", "token"]`

#### Scenario: Dynamic access is rejected
- **GIVEN** a `switch` case whose `when` is `$secrets[.key] == "x"`
- **WHEN** the finder runs
- **THEN** a compile error names the owner, the site `switch.0.when` and the non-static form

#### Scenario: Secret named bulk is rejected
- **GIVEN** an `output.as` of `${ $secrets.bulk }`
- **WHEN** the finder runs
- **THEN** a compile error names the owner, the site `output.as` and the secret name `bulk`

#### Scenario: Secrets text in a constant is not a use
- **GIVEN** a task with `output.as: { a: "${ .a }", hint: "$secrets.x" }`
- **WHEN** the finder runs
- **THEN** the site's `secrets` is empty and no error is raised

### Requirement: Compile errors name owner and site and are reported together

The finder SHALL collect every error below and then throw one `CompilationException` listing them
all. Each message SHALL name the site's owner and site name:
- a site `appId` equal to another site's `appId`, or to an app ID the version already claims. The
  message names both claimants.
- a site `id` that sanitizes to an empty app ID, to a value that is not a DNS-1123 label, or to more
  than 63 characters;
- a declared variable that `dws-jq` would refuse: not matching `^[A-Za-z_][A-Za-z0-9_]*$`, or equal
  to `secrets`, `ENV` or `__loc__`. The message also names the variable and the task that declares
  it.
- a non-static `$secrets` use, or a secret named `bulk`;
- an empty expression: a **whole** field that is exactly `${}` after trimming, or a part whose inner
  text is empty.

The finder SHALL NOT otherwise check jq syntax, since `dws-jq` checks it at startup.

#### Scenario: Collision with a task app ID
- **GIVEN** a `for` task `processItems` with a `while`, and a `call: http` task named
  `processItemsJqWhile`, so the version claims `process-items-jq-while`
- **WHEN** the finder runs with that plan's claimed app IDs
- **THEN** a compile error names site `processItems.jq.while` and the claimant of
  `process-items-jq-while`

#### Scenario: Collision between two sites
- **GIVEN** a `listen` task `waitPay` whose first filter correlates on keys `orderId` and `order-id`,
  both with `from`
- **WHEN** the finder runs
- **THEN** a compile error names both sites, because both sanitize to
  `wait-pay-to-0-jq-correlate-order-id-from`

#### Scenario: Refused loop variable name
- **GIVEN** a `for` task `scan` with `each: my-item` and a `while`
- **WHEN** the finder runs
- **THEN** a compile error names owner `scan`, site `while` and the variable `my-item`

#### Scenario: Reserved variable name
- **GIVEN** a `try` task whose `catch.as` is `secrets` and whose `catch.when` is set
- **WHEN** the finder runs
- **THEN** a compile error names owner `<task>.catch`, site `when` and the variable `secrets`

#### Scenario: Several errors are reported at once
- **GIVEN** a definition with one dynamic `$secrets` use and one site that collides with a task
  app ID
- **WHEN** the finder runs
- **THEN** one `CompilationException` lists both errors
