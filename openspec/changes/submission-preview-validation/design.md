## Context

Phase 1 (`console-definition-submission`) shipped a raw-text DSL editor whose only
action applies to the cluster. Its failure mode is a flat `string[]` from
`dws-controller`'s `CompilationException`, with no field path and no preview.

Two facts from reading the code shape everything below:

1. `dws-controller` already exposes compile-without-apply
   (`WorkflowResource.deploy`, `?dryRun=true` → `DeploymentPlan`), and
   `dws-admin`'s `ControllerRelayService.relayDeploy` already takes a `dryRun`
   boolean and appends the query param. **No new relay work is needed** — only a
   console caller that asks for it.
2. `dws-controller` has no hand-written DSL model. It parses with
   `io.serverlessworkflow.api.WorkflowReader` from `serverlessworkflow-api`
   `7.26.0.Final`; every DSL type it imports is generated from that SDK's own
   `schema/workflow.yaml`, which ships inside `serverlessworkflow-types`'s jar and
   carries `$id: https://serverlessworkflow.io/schemas/1.0.1/workflow.yaml`.

Constraint that follows from (2): a spec-conformance layer is only useful if it
agrees with the compiler. Validating against a *different* schema version makes
the new layer a source of wrong answers. `brainstorm.md` records the measured
1.0.1↔1.0.3 diff that settles this.

`dws-admin` runs NestJS 11 on Node 24, already boots with `rawBody: true` and a
raw parser registered for `application/yaml`/`text/yaml` (`src/main.ts`), and has
no YAML parser, no ajv, and no JSON Schema anywhere today.

## Goals / Non-Goals

**Goals:**

- An operator can check a draft definition and see what it would deploy, without
  applying it.
- Structural (DSL-shape) errors carry a field path, not just a sentence.
- The spec layer and the compile layer agree on what "valid DSL" means, by
  construction rather than by assumption.
- A future SDK bump in `dws-controller` cannot silently desynchronise the two.

**Non-Goals:**

- Porting any of `WorkflowCompiler`'s deployability rules (DNS-1123 secret names,
  `run: container` rejection, script-language rules, image resolution, OAuth
  wiring) into `dws-admin`. They stay in `dws-controller`, reached via `dryRun`.
- Any change to `dws-controller`.
- Inline CodeMirror diagnostics / gutter markers. Mapping a JSON pointer back to a
  YAML source line needs a CST the console does not have; the path is rendered as
  text in Phase 2.
- Rendering the task graph (that is Phase 4) or file import (Phase 3).
- Persisting or caching validation results.

## Decisions

### D1: Vendor the DSL schema from the SDK jar `dws-controller` already pins, not from the spec repo

- **Choice**: extract `schema/workflow.yaml` from
  `io.serverlessworkflow:serverlessworkflow-types:<version from
  dws-controller/pom.xml>` (today `7.26.0.Final`, schema version `1.0.1`) and
  check the result into `dws-admin`.
- **Rationale**: that file is literally the schema `WorkflowCompiler`'s parser was
  generated from, so layer 1 and layer 2 cannot disagree about document shape.
- **Alternative considered — upstream `open-workflow-specification.org` 1.0.3**
  (what roadmap §6 proposed): rejected on measured evidence. 1.0.3 types
  `run.shell.arguments` / `run.script.arguments` as an array of strings; 1.0.1
  types it as an object, `WorkflowCompiler` reads it as
  `Map<String,Object>` via `getArguments().getAdditionalProperties()`, and
  `dws-controller/src/test/resources/fixtures/run-shell.yaml` uses the object
  form. Shipping 1.0.3 would reject a definition the platform deploys today.
  Four further divergences (`emit.event.with` required set, inline `oauth2`
  required set, `for.in` union, `uriTemplate` pattern) go the other way and would
  pass documents the compiler rejects. Full table in `brainstorm.md`.
- **Consequence to state plainly**: DWS's spec conformance layer is pinned to
  DSL 1.0.1 as realised by SDK 7.26.0.Final. Moving to 1.0.3 is a
  `dws-controller` SDK upgrade, not a console-side decision.

### D2: Vendoring mechanics — offline script, checked-in JSON snapshot, drift test

- **Choice**: `dws-admin/scripts/vendor-dsl-schema.mjs` reads
  `<serverlessworkflow.version>` out of `../dws-controller/pom.xml`, downloads that
  `serverlessworkflow-types` jar from Maven Central, extracts
  `schema/workflow.yaml`, and writes:
  - `src/definition-validation/schema/workflow-schema.json` — the schema converted
    to JSON (reviewable diff, no YAML parse at runtime for the schema itself), and
  - `src/definition-validation/schema/provenance.json` —
    `{ sdkVersion, schemaId, sourceJar, sha256 }`.
  Run manually (`pnpm vendor:schema`), not as part of `pnpm build`.
- **Drift guard**: a unit test reads `../dws-controller/pom.xml`, and asserts
  `provenance.sdkVersion` still matches it and that the checked-in schema's `$id`
  matches `provenance.schemaId`. A controller-side SDK bump that forgets this
  module fails `pnpm test`.
- **Alternatives considered**: fetching during `pnpm build` (couples the Node build
  to Maven Central and needs network in CI — rejected); hand-copying the file (no
  mechanical link to the pom, exactly the silent drift §6 wanted to avoid).
- **Note**: if `../dws-controller/pom.xml` is absent (component checked out
  standalone), the drift test skips with an explicit message rather than failing —
  it must not turn a packaging choice into a red build.

### D3: `POST /definitions/validate`, raw body in, 200 report out

- **Choice**: a new `DefinitionValidationModule` with route
  `POST /definitions/validate`. Body is the raw definition bytes, exactly as the
  relay takes them (`@Req() RawBodyRequest`), with `Content-Type`
  `application/yaml` or `application/json`. Response is always **200** with a
  report:
  ```jsonc
  { "valid": true }
  { "valid": false, "errors": [
      { "path": "/do/0/callWeather/call", "message": "must be equal to constant \"http\"",
        "keyword": "const", "line": 7, "column": 9 } ] }
  ```
  `line`/`column` are present only for YAML/JSON *parse* failures (where the
  parser reports a position); schema errors carry `path` (ajv `instancePath`).
- **Rationale for 200-not-400**: this endpoint answers a question about a
  document; a well-formed request that reports "the document is invalid" is a
  successful request. 400 stays reserved for a malformed *request* (missing body,
  unsupported content type). This deliberately differs from the relay's
  400-with-`errors[]`, which is `dws-controller`'s contract, not ours.
- **Path choice**: `/definitions/...`, not `/workflows/validate` — `/workflows` is
  the read API's namespace (`GET /workflows`, `GET /workflows/:name`), and a
  validation report is not a workflow resource.
- **Auth**: same bearer path as every other admin route; the console calls it
  through the existing `adminFetch`.

### D4: Console runs the two layers sequentially, spec first

- **Choice**: `Preview` calls `POST /definitions/validate`; only on `valid: true`
  does it call `POST /workflows?dryRun=true`.
- **Rationale**: a document that fails spec conformance fails compilation too, and
  the compiler's flat sentence is strictly worse feedback than ajv's pointer, so
  there is nothing to gain from showing both. Sequential also keeps the common
  invalid-draft case off the controller entirely.
- **Alternative considered**: fire both in parallel and merge. Rejected — doubles
  the failure surface, and the merged view has to explain two error vocabularies
  for one defect.

### D5: Task-name uniqueness as a custom walk on top of ajv

- **Choice**: after ajv passes, walk `do` plus every nested body (`try.do`,
  `catch.do`, `for.do`, `fork.branches`) collecting task names; report each
  duplicate as an error whose `path` points at the *second* occurrence.
- **Rationale**: JSON Schema cannot express cross-referential uniqueness across
  nested lists. Mirrors `WorkflowCompiler.duplicateTaskNames`/`collectTaskNames`
  so the two layers give the same verdict, earlier and with a path.
- **Explicitly**: this is parity with an existing controller check, not new
  coverage. It is the one case where layer 1 would otherwise pass something layer
  2 rejects.

### D6: ajv configuration

- **Choice**: `Ajv2020` (`ajv/dist/2020`) with `allErrors: true`,
  `strict: false`, plus `ajv-formats`. Compile the schema once at module init and
  reuse the validator.
- **Rationale**: draft 2020-12 is what the schema declares. `allErrors` so an
  operator sees every problem in one pass rather than one per round-trip.
  `strict: false` because the vendored schema is generated upstream and is not
  written to ajv's strict-mode rules — turning strict on would fail at *compile*
  time on the schema itself, not on user input. `ajv-formats` because the schema
  uses `format: uri-template`, `duration`, `date-time`.
- **Error cap**: report at most 50 errors and set a `truncated` flag; a badly
  broken document can otherwise produce hundreds of `anyOf`-branch errors.

### D7: YAML/JSON parse is its own failure mode, before schema validation

- **Choice**: parse with the `yaml` package (`parse`, which also accepts JSON,
  since JSON is a YAML subset) inside a try/catch. A `YAMLParseError` carries
  `linePos`, so a parse failure is reported with real `line`/`column` — feedback
  the controller cannot give today at all.
- **Rationale**: an unparseable buffer has no `instancePath` to point at, so it is
  a distinct error kind, not a schema error.

### D8: Console types — parse the plan, do not cast it

- **Choice**: add a zod schema for `DeploymentPlan` (mirroring the Java record:
  `workflow`, `versionId`, `version`, `definitionResource`, `specText`, `steps[]`,
  `bindings[]`, `orchestrator`, `oauthEndpoints[]`, `bindingComponents[]`) and
  `safeParse` the dry-run response, exactly as `applyResultSchema` already does
  for `ApplyResult`.
- **Rationale**: the existing file's own stated reason — a silent shape drift
  otherwise reaches the UI as "undefined". `oauthEndpoints`/`bindingComponents`
  are parsed permissively (`z.array(z.unknown())`) because Phase 2 does not render
  them and their shape is not console-facing.
- **Preview render**: workflow name + version, the `steps[]` table (name, kind,
  image), `bindings[]` (task, direction, topic), and the orchestrator's name and
  image. `specText` is not re-rendered — the operator is looking at it.

### D9: New console client functions, `submitDefinition` untouched

- **Choice**: add `validateDefinitionSpec(definition, format, signal)` and
  `previewDefinition(definition, signal)` to `admin-client.ts`; leave
  `submitDefinition` byte-identical. A discriminated `DefinitionPreview` union
  (`{kind: "plan"} | {kind: "spec-error"} | {kind: "deploy-error"}`) mirrors the
  existing `DefinitionSubmission` union's style.
- **Rationale**: `admin-client.ts` is the single transport boundary (design D6 in
  the auth work: the one place allowed to call `getAccessToken`). Route components
  must not fetch.

## Risks / Trade-offs

- **[Risk] Vendored schema and controller SDK drift apart on a later bump.** →
  Mitigation: D2's provenance file + test that reads `dws-controller/pom.xml`; the
  bump fails `pnpm test` in `dws-admin` until the snapshot is regenerated.
- **[Risk] Layer 1 rejects something layer 2 would accept (false positive) because
  the SDK's parser is more lenient than its own schema** — e.g. Jackson ignoring a
  constraint the schema states. → Mitigation: seed the test suite with
  `dws-controller`'s existing fixtures (`run-shell.yaml`, `run-script-js.yaml`,
  `order.yaml`, `try-order.yaml`) and assert every fixture the controller accepts
  passes spec validation. This is the concrete regression net for D1.
- **[Trade-off] Pinned to DSL 1.0.1, not the newest published 1.0.3.** Accepted:
  matching the compiler is worth more than matching the newest spec revision, and
  the console would be lying about `call: mcp` or array-form `arguments` support
  the platform does not have.
- **[Trade-off] Two round-trips for a preview (admin, then controller).** Accepted:
  the first is local to `dws-admin` and cheap, and it short-circuits the common
  case of a draft that is not valid DSL yet.
- **[Risk] ajv `anyOf` error explosion makes the error list unreadable** — the DSL
  schema is one big union over task kinds. → Mitigation: D6's 50-error cap plus
  reporting errors in `instancePath` order so the deepest useful ones are grouped;
  if this proves noisy in practice, error pruning is a follow-up, not a blocker.
- **[Risk] `POST /definitions/validate` is a CPU-bound endpoint on an unbounded
  body.** → Mitigation: cap the accepted body size explicitly (1 MiB) and return
  413 above it, rather than parsing whatever arrives.

## Migration Plan

- Deploy order: `dws-admin` first (adds the endpoint), then `dws-console`. The
  console's `Preview` is the only caller; shipping admin first is a no-op for
  existing users.
- Rollback: revert the console change alone — the new admin endpoint is additive
  and unreferenced by anything else. No database migration, no Helm value, no
  Dapr component change; the route is served by the existing admin Deployment on
  the existing port and is reached through the existing Gateway path.
- Acceptance: `pnpm lint && pnpm test && pnpm build` green in both `dws-admin` and
  `dws-console`; every `dws-controller` fixture that compiles today passes
  `POST /definitions/validate`; a definition with a duplicate nested task name is
  rejected by layer 1 with a path; `Preview` on a valid definition renders the
  plan and applies nothing.

## Open Questions

- Should `Preview` results invalidate when the buffer changes (auto-clear) or
  persist until re-run? Leaning auto-clear on edit so a stale plan is never read
  as current — to settle during implementation, it is a component-local choice.
- Whether to surface the vendored schema's DSL version in the console UI ("checked
  against DSL 1.0.1"). Useful once 1.0.1 and the published spec diverge further,
  but it exposes an internal pin; deferred unless it comes up in review.
