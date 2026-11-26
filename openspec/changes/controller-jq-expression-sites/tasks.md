All Java work is in `dws-controller`, gate `./mvnw verify` (`mvnw.cmd` on Windows). The corpus work
is in `conformance/expressions/`, checked by `dws-jq`'s gate `make lint && make test`. Tests come
first in each group. Do not commit or push unless asked.

## 1. Shared app-ID rule (dws-controller)

- [ ] 1.1 Add `AppIdsTest`, covering kebab output, the empty-ID, non-DNS-1123 and over-63 errors. Each
      message must use the given subject verbatim. Add a regression assertion that
      `NodeNaming.appId`'s three messages are unchanged.
- [ ] 1.2 Extract `NodeNaming.appId`'s body into a public `compile.AppIds.derive(String id, String
      subject)` with `@UtilityClass`. `NodeNaming.appId` delegates with subject `node '<nodeId>'`.
      Existing `V2StructuralCompilerTest` and `V2GoldenTest` stay green, unmodified.

## 2. Site model and claimed app IDs (dws-controller)

- [ ] 2.1 Add the record `compile.jq.ExpressionSite(owner, site, id, appId, expression, variables,
      secrets)`. It holds immutable copies, `variables` as a `List` and `secrets` as a sorted `Set`.
- [ ] 2.2 Write `ClaimedAppIdsTest` first:
      - a v1 plan claims its step names and its orchestrator app ID;
      - a v2 plan claims every node app ID and every function app ID;
      - each claim carries its claimant text.
- [ ] 2.3 Implement `ClaimedAppIds.of(DeploymentPlan)` and `ClaimedAppIds.of(Map<String,String>)`.

## 3. Detection rules and template rewrite (dws-controller)

- [ ] 3.1 Write `FieldRuleTest` with vectors for each of the six rules:
      - `whole`: wrapped, bare, whitespace, blank, and `${}`;
      - `data-flow`: the string form versus the object form;
      - `template`: nested leaves, keys never evaluated, partial `${`, and
        `"${ a } and ${ b }"` as one part;
      - `top-level`: bare top-level strings, with nested `${ }` kept constant;
      - `balanced`: the `dws-call-a2a` cases, including `"${a} ${b}"` as a literal and
        `${ {a: "}"} }`;
      - `raise`: a multi-line literal, `${}`, and `status` as a constant.
- [ ] 3.2 Implement the package-private `FieldRule`, reproducing `JqEvaluator.isWrapped`/`unwrap`
      (Java `String.trim()`), the SDK raise regex, and `_match_wrapped_expression`.
- [ ] 3.3 Write `JqLexerTest`: strings with escapes, nested `\( … )` interpolation, `#` comments,
      `$name` tokens, and the "ends inside a comment" flag.
- [ ] 3.4 Implement the package-private `JqLexer`.
- [ ] 3.5 Write `TemplateRewriterTest` with the exact expected text for each shape:
      - an object, an array and mixed constants;
      - a subtree with no part, written as compact JSON;
      - a single root part emitted bare;
      - `def`, `as $x`, `empty`, several results;
      - key escaping, including a key containing `\(`;
      - a part ending in a comment, which gets a newline;
      - a constant-only field, which produces no expression.
- [ ] 3.6 Implement the package-private `TemplateRewriter`, serializing constants with an
      order-preserving Jackson mapper.

## 4. `$secrets` scan and variables (dws-controller)

- [ ] 4.1 Write `SecretScannerTest`.
      - Accepted: `$secrets.a`, `$secrets."a-b"`, `$secrets["a-b"]` (with inner whitespace),
        `$secrets .a`, `$secrets.a.b`, escapes decoded, and uses inside interpolation.
      - Rejected, naming the form: bare `$secrets`, `| keys`, `[.k]`, `[$k]`, `["a"+.x]`, `[]`,
        slices, `. as $secrets`, `def f($secrets)`, `{$secrets}`, and `$secrets."a\(.x)"`.
      - Error: the name `bulk`.
      - Not a use: `$secretsX`, and `"$secrets.x"` inside a string.
- [ ] 4.2 Implement the package-private `SecretScanner` on top of `JqLexer`.
- [ ] 4.3 Write `SiteVariablesTest`:
      - the per-site order from the spec, including `context` placement;
      - nested `for` and `catch.do` scopes;
      - catch conditions getting no outer scope;
      - event-bound sites getting none;
      - de-duplication;
      - refused names (regex, `secrets`, `ENV`, `__loc__`), naming the declaring task.
- [ ] 4.4 Implement the package-private `SiteVariables`.

## 5. Walker and finder (dws-controller)

- [ ] 5.1 Add golden fixtures under `src/test/resources/jq-sites/<case>/` (`definition.yaml` plus the
      expected `sites.json`):
      - `all-sites`, using every row of the spec's field table, including `use.retries`,
        `use.errors`, the `listen` `one`/`any`/`all` forms and `until`, and `schedule.on`;
      - `templates`: object, array, mixed constants, `def`, `as $x`, no output, several results;
      - `set-vs-template`;
      - `constant-only`;
      - `secrets`;
      - `scopes`: nested for/try/catch/fork/do;
      - `task-if-ignored`;
      - `adr-identity-examples`.
- [ ] 5.2 Write `ExpressionSiteFinderGoldenTest`. It compares each fixture's finder output field for
      field, and `-Ddws.writeJqSiteGoldens=true` rewrites the `sites.json` files. It also has one
      test per spec scenario not covered by a golden.
- [ ] 5.3 Write `ExpressionSiteFinderErrorTest`. Each error from the spec gets a fixture:
      - collision with a task app ID;
      - site-to-site collision (`orderId` / `order-id`);
      - an over-63-character ID;
      - a refused loop variable;
      - `catch.as: secrets`;
      - a dynamic `$secrets`;
      - `bulk`;
      - an empty expression;
      - several errors reported in one exception.
      Assert that each message names the owner and the site.
- [ ] 5.4 Implement the package-private `SiteWalker`. It walks the raw tree depth-first over `do`,
      `for.do`, `try`, `catch.do` and `fork.branches`, keeps owners and the scope stack, uses the field
      table, and resolves `use.retries` and `use.errors`.
- [ ] 5.5 Implement the public `ExpressionSiteFinder.find(JsonNode, String, ClaimedAppIds)`. It
      collects every error, then throws one `CompilationException`.
- [ ] 5.6 Write `ExpressionSiteFinderCompilerParityTest`. For a definition both strategies accept,
      the finder returns equal sites with `ClaimedAppIds.of(v1Plan)` and with
      `ClaimedAppIds.of(v2Plan)`. Assert that neither `compile()` calls the finder: every existing
      compiler and golden test stays unmodified and green.

## 6. Round-trip corpus cases (conformance/expressions, dws-controller, dws-jq)

- [ ] 6.1 Add these cases under `conformance/expressions/cases/templates/`:
      - `rewrite-object.yaml`, `rewrite-array.yaml`, `rewrite-mixed-constants.yaml`;
      - `rewrite-set-top-level.yaml`, with the nested `${ }` kept literal;
      - `rewrite-def.yaml`, `rewrite-as.yaml`, `rewrite-no-output.yaml`, `rewrite-many-results.yaml`;
      - `rewrite-bare.yaml`, `rewrite-comment.yaml`.

      Each `expect` is v1's result for the same field, and each `source` cites the
      `dws-orchestrator` test or `JqEvaluator`/`EvaluateSetActivity` line that fixes it. Follow the
      corpus README authoring rules: single-quoted `expression` and a `givenX_whenY_thenZ` `test`.
- [ ] 6.2 Add one fixture per case under `dws-controller/src/test/resources/jq-sites/rewrite/`,
      `{case, rule, field}`. Write `RewriteCorpusPinTest`, which reads
      `../conformance/expressions/cases/<case>.yaml` and asserts that the finder's expression equals
      the case's `expression`.
- [ ] 6.3 Update `conformance/expressions/README.md`. Add `templates/rewrite-*` and their pin to the
      area table, and add "v1 behavior, `file:line`" to the expectation sources.
- [ ] 6.4 Run `cd dws-jq && make lint && make test`. Every `rewrite-*` case must pass. Do not edit an
      expectation to make one pass: list a real divergence in `divergences/dws-jq.yaml` with a
      follow-up, and report it.

## 7. Docs and gates

- [ ] 7.1 Root `CLAUDE.md`, `dws-jq` configuration contract bullet: add that `dws-controller`'s
      `compile.jq` produces the site list (2m.1). Name the duplicated variable-name and `bulk` rules,
      and note that 2m.2 must still compare its rendering with `dws-jq/testdata/contract/`.
- [ ] 7.2 `dws-controller/CLAUDE.md` package table: add `compile.jq` (expression-site finder, not
      called from `compile()` yet).
- [ ] 7.3 Roadmap row 2m: mark 2m.1's status. Record the ADR 0015 corrections in the row 2.0d note.
      Do not edit ADR 0014.
- [ ] 7.4 Run `cd dws-controller && ./mvnw spotless:check && ./mvnw verify`. Everything must be green,
      with the existing compile output unchanged.
- [ ] 7.5 Run `openspec validate controller-jq-expression-sites --type change --strict` and fix every
      ERROR. At proposal time the only findings were "requirement text > 500 characters" WARNINGs.
      The existing `jq-expression-*` specs get the same warnings, so splitting the requirements is
      optional.
- [ ] 7.6 Report back:
      - the site shape and its mapping onto `dws-jq`'s env;
      - the full site list by task type;
      - each v1 ambiguity and the choice made;
      - the ADR 0014 corrections (ADR 0015).
