# Implementation Plan — `dws-call-asyncapi`

This plan groups the work by component and names each component's focused validation command. The
runner is built and validated first (independent CI gate), then the controller and orchestrator
wiring, then CI and integration.

## Component A — `dws-call-asyncapi` runner (Node 24, TypeScript, Fastify, pnpm)

Mirror `dws-call-openapi` file-for-file. Reused unchanged in spirit: the Fastify scaffold, the
fail-fast config loader, the `DOC_SHA256` integrity pin, the `ajv` single-validator shape, `node-jq`,
and the `POST /run` + `GET /healthz` + `502`-on-transport contract. Changed: the outbound leg
(`binding.ts` instead of `request.ts`), the domain module (`asyncapi/*` instead of `openapi/*`), and
the parser dependency (`@asyncapi/parser` instead of `@readme/openapi-parser` + `swagger-client`).

### Files

- `package.json` — deps `@asyncapi/parser`, `ajv`, `ajv-formats`, `fastify`, `fastify-plugin`,
  `node-jq`, `undici`, `yaml`. No `swagger-client`/`@readme/openapi-parser`.
- `tsconfig.json`, `vitest.config.ts`, `eslint.config.mjs`, `pnpm-workspace.yaml`, `Dockerfile`,
  `.dockerignore`, `.gitignore` — copied verbatim (`pnpm-workspace.yaml` keeps the `node-jq` build
  allowance).
- `src/index.ts`, `src/app.ts`, `src/routes.ts` — copied; `app.ts` registers the `asyncapi` plugin.
- `src/config/config.ts` — env surface per brainstorm Q1; `AuthConfig`/secret-store forms dropped
  (broker credentials live in the Dapr Component, not the runner).
- `src/asyncapi/document.ts` — `fetchDocument` / `verifySha256` / `parseDocument` (JSON or YAML) then
  `@asyncapi/parser` validation; returns the plain resolved object.
- `src/asyncapi/operation.ts` — `resolveOperation(doc, operationId)` → `{ operationId, action,
  channelName, address, payloadSchema }`; requires `action: send`; resolves `channel.$ref` and the
  operation/channel message `payload` via internal JSON-pointer resolution.
- `src/asyncapi/validator.ts` — `OperationValidator` over `payloadSchema` (`allErrors`,
  `coerceTypes: false`), `validate(payload)` → issues.
- `src/asyncapi/engine.ts` — startup assembly: fetch+verify+parse, resolve operation, compile
  validator; caches `{ config, doc, template, validator }`.
- `src/jq.ts` — `evaluatePayload(expression, input)` (single jq run; default `.`).
- `src/binding.ts` — `buildBindingRequest(engine, payload)` → `{ url, method: 'POST', headers, body }`
  targeting `…/v1.0/bindings/<BINDING_NAME>` with `{ data, operation, metadata }`; `executeRequest`
  reused from an `http.ts` copy.
- `src/runner.ts` — `runOperation`: evaluate `PAYLOAD` → validate (throw `BindingError` → 400 with
  `"validation failed: …"`) → dispatch → shape `OUTPUT`; `UpstreamError`/`TransportError` → 502.
- `src/plugins/{config,asyncapi,runner}.ts` — copied, renamed decorator to `engine`.
- `README.md`, `k8s/knative-service.yaml` — adapted for the binding contract and env surface.

### Tests (`test/`)

`config.test.ts`, `document.test.ts` (hash mismatch, YAML/JSON), `operation.test.ts` (send resolves,
non-send rejected, channel/message resolution, missing operation), `validator.test.ts` (payload
pass/fail/details), `binding.test.ts` (URL shape, body `{data,operation,metadata}`, OUTPUT modes,
502 mapping), `run.test.ts` (end-to-end with a stub sidecar). Fixtures: `fixtures/kafka-orders.json`
(AsyncAPI 3.0), `fixtures/no-send.json`.

**Validation:** `pnpm lint && pnpm test && pnpm build`.

## Component B — `dws-controller` (Java 25, Quarkus)

### Model

- `TaskKind.CALL_ASYNCAPI`.
- `ImageCatalog.callAsyncapi` (+ `DwsConfig.images().callAsyncapi()`, `application.yaml`,
  `CompilerProducer`).
- New `BindingComponent(String name, String type, Map<String,EnvValue> metadata, String appId)`
  record; add a `List<BindingComponent> bindingComponents` to `DeploymentPlan` (with a compatibility
  constructor, as `oauthEndpoints` did).

### `WorkflowCompiler`

- `walk`: add `else if (call != null && call.getCallAsyncAPI() != null)` →
  `asyncApiStep(taskName, call.getCallAsyncAPI(), context)`.
- `asyncApiStep`: resolve `DOC_ENDPOINT` from `with.document.endpoint`; fetch bytes; pin
  `DOC_SHA256`; pin `OPERATION_ID`; light-parse the document (reuse the existing
  `parseOpenApiDocument`/`detectFormat` Jackson helpers) to read `servers[0].protocol`/`host` and the
  operation's channel `address`; map protocol→binding type (reject unsupported); register a
  `BindingComponent` on the context (dedup by canonical content like OAuth) → `BINDING_NAME`; pin
  `OPERATION=create`; project any credential `${ $secrets.X }` reference as `secretKeyRef` metadata
  via the existing `secretRef(...)` helper.
- Reject `nats`/`pulsar`/`solace`/unknown with `task '<name>': AsyncAPI server protocol '<p>' has no
  supported Dapr binding (supported: kafka, amqp, mqtt, mqtt5, sqs, googlepubsub)`.

### `StackSynthesizer`

- `bindingComponents(plan, namespace)` → one Dapr `Component` per `BindingComponent`, `version: "v1"`,
  metadata from the model (literal `value` or `secretKeyRef`), `scopes: [appId]`, plan labels.
- `StackApplier`/`StackReader` (if they enumerate resource kinds) include the new Component in
  apply + label GC. Binding Components are named `<workflow>-<versionId>-binding-<hash>` so they
  update-in-place per version and GC by label like the OAuth Components.

**Validation:** `./mvnw test` (compiler + synthesizer). Note: `maven.compiler.release=25`; a JDK-25
`JAVA_HOME` is required (documented pre-existing environment constraint).

## Component C — `dws-orchestrator` (Java 25, Spring Boot)

- `WorkflowErrors`: `private static final String VALIDATION_MARKER = "validation failed:";` and, in
  `classify()`, `if (message.contains(VALIDATION_MARKER)) return ErrorKind.VALIDATION;` placed after
  the `DATA_FLOW_MARKER` check and before the `CONFIG_MARKER`/`STEP_MARKER` checks. Update the class
  Javadoc noting the new producer.
- `WorkflowErrorsTest`: a case asserting a `"step '…' failed with status 400: validation failed: …"`
  message classifies as `VALIDATION`, and that a plain `"failed with status 502"` stays
  `COMMUNICATION`.

**Validation:** `./mvnw verify`.

## Component D — CI and integration

- `.github/workflows/dws-call-asyncapi.yml` — path filter `dws-call-asyncapi/**`; jobs: `pnpm
  install --frozen-lockfile`, `pnpm lint`, `pnpm test`, `pnpm build`, and a Docker build (push to
  `ghcr.io/tonylibs/dws-call-asyncapi` only on merge to `main`). Mirror `dws-call-openapi.yml`.
- Integration test: a real Dapr sidecar + Kafka binding, asserting a published message lands on the
  topic. Requires Docker/Kafka/live Dapr; execution is environment-blocked here and reported as an
  honest FAIL in `verify.md`, matching `workflow-auth`'s tasks 6.2/6.3.

## Cross-component contract check

The env var names (`DOC_ENDPOINT`, `DOC_SHA256`, `OPERATION_ID`, `BINDING_NAME`, `OPERATION`) and the
validation marker (`"validation failed:"`) are shared between the runner and the controller/orchestrator
and MUST stay in sync — the same implicit-contract discipline as the task-name→app-id adapter.
