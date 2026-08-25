## 1. Roadmap

- [ ] 1.1 Mark Phase 5's AsyncAPI slice underway in `docs/roadmaps/openworkflow-features.md`
  (call `asyncapi` row and Phase 5 note).

## 2. `dws-call-asyncapi` runner scaffold

- [ ] 2.1 Create the component skeleton mirroring `dws-call-openapi`: `package.json` (add
  `@asyncapi/parser`, drop `swagger-client`/`@readme/openapi-parser`), `tsconfig.json`,
  `vitest.config.ts`, `eslint.config.mjs`, `Dockerfile`, `.dockerignore`, `.gitignore`,
  `pnpm-workspace.yaml`, `README.md`, `k8s/knative-service.yaml`.
- [ ] 2.2 Port `src/app.ts`, `src/index.ts`, `src/routes.ts`, `src/plugins/{config,asyncapi,runner}.ts`
  from the OpenAPI runner, renaming the domain plugin/engine to `asyncapi`.

## 3. Runner configuration and document

- [ ] 3.1 Implement `src/config/config.ts`: `DOC_ENDPOINT`, `DOC_SHA256`, `OPERATION_ID`,
  `BINDING_NAME`, `OPERATION` (default `create`), `PAYLOAD` (default `.`), `METADATA` (default `{}`),
  `OUTPUT`, `TIMEOUT`, `PORT`, `TASK`, `DAPR_HTTP_PORT`, `LOG_LEVEL`, all fail-fast validated.
- [ ] 3.2 Implement `src/asyncapi/document.ts`: fetch (`http`/`https`/`file`), `verifySha256`, parse
  with `@asyncapi/parser`, fail fast on error diagnostics, expose the resolved plain document object.
- [ ] 3.3 Implement `src/asyncapi/operation.ts`: resolve `OPERATION_ID` → `action` (`send` required),
  channel address, and message `payload` schema against the plain document.

## 4. Runner validation, dispatch, and engine

- [ ] 4.1 Implement `src/asyncapi/validator.ts`: one compiled `ajv`+`ajv-formats` validator over the
  message `payload` schema (`allErrors`, `coerceTypes: false`), returning flattened issues.
- [ ] 4.2 Implement `src/jq.ts` payload evaluation (single `PAYLOAD` expression) and `src/binding.ts`
  (build + execute the Dapr output-binding POST via `undici`).
- [ ] 4.3 Implement `src/runner.ts` + `src/asyncapi/engine.ts`: evaluate → validate (400 with the
  validation marker) → dispatch → shape output; `UpstreamError`/`TransportError` → `502`.

## 5. Runner tests

- [ ] 5.1 Unit tests mirroring `dws-call-openapi/test/`: `document.test.ts`, `operation.test.ts`,
  `validator.test.ts`, `binding.test.ts`, `config.test.ts`, `run.test.ts`, plus AsyncAPI fixtures.
- [ ] 5.2 Run the component CI gate: `pnpm install`, `pnpm lint`, `pnpm test`, `pnpm build`.

## 6. Controller compilation

- [ ] 6.1 Add `TaskKind.CALL_ASYNCAPI`, `ImageCatalog.callAsyncapi`, and the `BindingComponent`
  model; thread the new image through `DwsConfig`, `application.yaml`, and `CompilerProducer`.
- [ ] 6.2 Add the `call: asyncapi` branch to `WorkflowCompiler`: light document read (server
  protocol/host + channel address), protocol→binding-type table, env pinning, secret-backed
  credential resolution, and unsupported-protocol rejection.
- [ ] 6.3 Extend `StackSynthesizer` to render the version-scoped Dapr binding `Component` scoped to
  the step app-id, and thread it through `StackApplier`/`StackReader` GC by label.
- [ ] 6.4 Add compiler and synthesizer tests (supported protocols, unsupported rejection, secret
  refs, no-plaintext, non-AsyncAPI unchanged); run `./mvnw test` from `dws-controller`.

## 7. Orchestrator error classification

- [ ] 7.1 Add `VALIDATION_MARKER` to `WorkflowErrors.classify()` mapping to `ErrorKind.VALIDATION`,
  guarded before the step-communication check; add `WorkflowErrorsTest` cases.
- [ ] 7.2 Run `./mvnw verify` from `dws-orchestrator`.

## 8. CI and integration

- [ ] 8.1 Add `.github/workflows/dws-call-asyncapi.yml` (path-filtered lint/test/build + image
  build; push only on merge to `main`), mirroring `dws-call-openapi.yml`.
- [ ] 8.2 Add an integration test against a real Dapr sidecar + Kafka binding. (Requires
  Docker/Kafka/live Dapr; live execution is environment-blocked here — see `verify.md`.)
- [ ] 8.3 Run all component-specific validation commands and record results in `verify.md`; honest
  FAIL for any check needing live infra this environment does not have.
