## 1. Vendor the DSL schema into dws-admin

- [x] 1.1 Add `scripts/vendor-dsl-schema.mjs` to `dws-admin`: read
  `<serverlessworkflow.version>` from `../dws-controller/pom.xml`, download
  `serverlessworkflow-types-<version>.jar` from Maven Central, extract
  `schema/workflow.yaml`.
- [x] 1.2 Have the script write `src/definition-validation/schema/workflow-schema.json`
  (YAML converted to JSON) and `src/definition-validation/schema/provenance.json`
  (`sdkVersion`, `schemaId`, `sourceJar`, `sha256`); wire it as `pnpm vendor:schema`.
- [x] 1.3 Run the script and commit both generated files. Confirm the schema `$id`
  is the `1.0.1` identifier and record it in provenance.
- [x] 1.4 Add `schema-provenance.spec.ts`: assert provenance `sdkVersion` matches
  `../dws-controller/pom.xml`, assert the schema `$id` matches `schemaId`, and skip
  with an explicit message when the pom is absent.

## 2. dws-admin spec validation service

- [x] 2.1 Add deps `ajv`, `ajv-formats`, `yaml` to `dws-admin`.
- [x] 2.2 Write failing tests for `DefinitionValidationService.validate`: valid
  document passes; malformed YAML returns a parse error with line/column; missing
  `document` and missing `do` return schema errors with pointers; unsupported task
  shape returns a pointer at the offending field.
- [x] 2.3 Implement the service: parse with `yaml`, then validate with `Ajv2020`
  (`allErrors: true`, `strict: false`, `ajv-formats`), compiling the schema once at
  construction. Map ajv errors to `{path, message, keyword}`.
- [x] 2.4 Add the 50-error cap and `truncated` flag, with a test that exceeds it.
- [x] 2.5 Write failing tests for nested task-name uniqueness (duplicate across a
  `try`/`catch` body; duplicates in `for` and `fork` bodies; all-distinct passes),
  then implement the walk over `do`, `try.do`, `catch.do`, `for.do`, `fork.branches`.
- [x] 2.6 Add a fixture-parity test: every definition under
  `dws-controller/src/test/resources/fixtures/` that the controller compiles
  successfully validates as spec-valid here — including `run-shell.yaml`'s object
  form of `arguments`. Skip with a message if the fixtures are absent.

## 3. dws-admin HTTP endpoint

- [x] 3.1 Write failing controller tests: 200 `{valid:true}`; 200 `{valid:false,
  errors}`; 400 on empty body; 400 on unsupported content type; 413 above 1 MiB.
- [x] 3.2 Implement `DefinitionValidationController` (`POST /definitions/validate`)
  reading the raw body via `RawBodyRequest`, matching the relay's raw-bytes
  handling; register `DefinitionValidationModule` in `AppModule`.
- [x] 3.3 Confirm `main.ts`'s existing raw parser covers the accepted YAML content
  types; extend only if a gap shows up in the tests.
- [x] 3.4 Run `pnpm lint && pnpm test && pnpm build` in `dws-admin`; commit.

## 4. dws-console transport

- [x] 4.1 Write failing tests in `admin-client.test.ts` for
  `validateDefinitionSpec`: sends the raw buffer with the format's content type,
  returns the report, throws `ApiError` on non-2xx.
- [x] 4.2 Implement `validateDefinitionSpec` in `admin-client.ts`.
- [x] 4.3 Write failing tests for `previewDefinition`: posts `?dryRun=true`,
  returns `{kind:"plan"}` on success, `{kind:"deploy-error"}` on 400 with
  `errors[]`, throws `ApiError` on an unexpected plan shape.
- [x] 4.4 Add the zod `deploymentPlanSchema` and implement `previewDefinition`,
  leaving `submitDefinition` byte-identical.

## 5. dws-console editor UI

- [x] 5.1 Write failing component tests for `/workflows/new`: preview disabled when
  the buffer is empty or signed out; spec errors render path + message; a parse
  error renders line/column; a plan renders steps, bindings, and orchestrator; a
  deployability rejection renders flat strings distinctly; preview issues no
  dry-run when the spec layer fails.
- [x] 5.2 Add the preview control and the sequential two-layer handler to
  `src/routes/workflows/new.tsx`, leaving submit untouched.
- [x] 5.3 Add plan rendering (version header, steps table, bindings table,
  orchestrator) using existing console components and tokens.
- [x] 5.4 Clear a rendered preview when the buffer changes, so a stale plan is
  never read as current.
- [x] 5.5 Run `pnpm lint && pnpm test && pnpm build` in `dws-console`; commit.

## 6. Documentation

- [x] 6.1 Update `docs/roadmaps/dws-console-submission.md`: Phase 2 row to done,
  and rewrite §6's three open questions as resolved — recording that the vendored
  schema is the SDK's 1.0.1, not upstream 1.0.3, and why.
- [x] 6.2 Note in §6 that duplicate task names are enforced in both layers, and
  that `dws-admin`'s check is early/path-precise parity rather than new coverage.
- [x] 6.3 Update the Notion mirror
  (`https://app.notion.com/p/3c92f73e4fd981988252dcbff0736f60`) to match.
- [x] 6.4 Update `dws-admin`'s README with the new endpoint and the
  `pnpm vendor:schema` workflow.
