## 0. Prerequisite (out of scope — one-line sanity check)

- [ ] 0.1 Confirm `openspec/changes/ows-phase3-errors-timeouts` has been archived (its
      `workflow-error-handling` / `workflow-timeouts` deltas landed in `openspec/specs/`). If not,
      archive it first — this change's deltas assume that baseline.

## 1. `StepService.env` gains secret-ref support (`dws-controller`)

- [ ] 1.1 In `dws-controller/.../model/StepService.java`, replace the literal-only
      `Map<String, String> env` with an env representation that models each entry as either a
      literal value or a reference `(secretName, secretKey)`. Preserve iteration order (deterministic
      for tests). Provide a small constructor / builder both call sites (`WorkflowCompiler`,
      `StackSynthesizer`) already reach for.
- [ ] 1.2 Update `dws-controller/.../k8s/StackSynthesizer.envVars()` to walk the new representation
      and emit either a literal `EnvVar` or an `EnvVar` with `valueFrom.secretKeyRef` set. Ensure
      the emitted resource is deterministic (stable ordering, no timestamps in the diff).
- [ ] 1.3 Update every existing `WorkflowCompiler` call site that populates `StepService.env` with a
      literal to construct via the new representation's literal path — behaviour unchanged, only
      the shape.
- [ ] 1.4 Unit tests: `StackSynthesizerTest` covers a mixed env (one literal entry + one
      `secretKeyRef` entry) and asserts the emitted `EnvVar` list matches expected shape byte for
      byte; a `StepService` with an entirely literal env produces the same output as before this
      change.

## 2. Authentication compilation (`dws-controller`)

- [ ] 2.1 In `WorkflowCompiler`, add an `AuthenticationCompiler` helper that resolves an
      endpoint's `authentication` — inline (`basic` / `bearer` / `oauth2`) or named
      (`authentication.use: <name>` → `document.use.authentications.<name>`). Mirror
      `CatchPolicy.resolvePolicy`'s inline-or-named pattern. Missing named reference SHALL throw a
      compile-time exception naming both the endpoint and the missing policy.
- [ ] 2.2 In `WorkflowCompiler.httpStep` and `WorkflowCompiler.openApiStep`, invoke the resolver
      and, for `basic`, emit `AUTH_SCHEME=basic` plus `AUTH_USERNAME` / `AUTH_PASSWORD` as
      `secretKeyRef` env entries; for `bearer`, emit `AUTH_SCHEME=bearer` plus `AUTH_TOKEN` as
      `secretKeyRef`; for `oauth2`, emit `AUTH_SCHEME=oauth2` plus `AUTH_HTTPENDPOINT_NAME=<name>`
      (literal — it's a resource name, not a secret) and record the policy for later oauth2
      resource synthesis.
- [ ] 2.3 Unit tests in `WorkflowCompilerTest` (or a new `WorkflowCompilerAuthTest`):
      - inline `bearer` → `AUTH_SCHEME=bearer` + secret-ref `AUTH_TOKEN`;
      - named reference resolves to the same shape as inline;
      - missing named reference fails compile with the expected diagnostic;
      - no `authentication` declared → no `AUTH_*` env on the step (regression against today's
        silent-drop behaviour);
      - assertion helper: no literal `EnvVar` value on any auth-related env entry (guards D2's
        "values never cross the compile boundary").

## 3. Secret allow-list validation (`dws-controller`)

- [ ] 3.1 In `WorkflowCompiler`, walk the definition after compilation and collect every
      `{ use.secret: X }` reference. Fail compile if any X is not present in
      `document.use.secrets`. Diagnostic names both the referencing location and X.
- [ ] 3.2 Unit tests: allow-listed name compiles; missing allow-list rejects with the expected
      diagnostic; a definition using no `use.secret` values compiles unchanged whether
      `use.secrets` is present or absent.

## 4. oauth2 Dapr resource synthesis (`dws-controller`)

- [ ] 4.1 Add `HTTPEndpoint` / `Component` / `Configuration` model types under
      `dws-controller/.../model/` (or extend the cdk8s object graph if that's where existing
      Dapr resources live — mirror the shape used for the existing `Configuration` /
      `Component` resources for the pub/sub and state store).
- [ ] 4.2 In `StackSynthesizer`, deduplicate `oauth2` policies by
      `sha256(host + issuer + client_id_secret_ref + client_secret_secret_ref +
      scopes-sorted).substring(0, 8)`. For each unique tuple, synthesise one `HTTPEndpoint`
      (`ep-<sanitized-host>-<hash8>`) and one `middleware.http.oauth2clientcredentials` `Component`
      whose `clientId` / `clientSecret` pull via `secretKeyRef`.
- [ ] 4.3 Per step using an `oauth2` scheme, synthesise a `Configuration` (`cfg-<step-name>`)
      whose `appHttpPipeline` attaches the middleware with `pathFilter` narrowed to
      `^/v1.0/invoke/<httpendpoint-name>/method/.*$` (confirm the anchor flavour empirically
      against the pinned Dapr version — see D4). Attach the `Configuration` to the step's Knative
      Service via the standard Dapr annotation.
- [ ] 4.4 Ensure all three resource kinds carry the same `dws.io/*` labels as the rest of the
      stack (so they are garbage-collected with the version).
- [ ] 4.5 Unit tests in `StackSynthesizerTest`:
      - one `oauth2` endpoint produces one `HTTPEndpoint` + one `Component` + one `Configuration`;
      - two endpoints on the same host with the same policy share one `HTTPEndpoint` and one
        `Component`;
      - two endpoints on different hosts with the same policy get two `HTTPEndpoint`s;
      - `Configuration.pathFilter` matches only the intended `/v1.0/invoke/...` prefix;
      - `Component` metadata for `clientId` / `clientSecret` is a `secretKeyRef`, never a literal
        value;
      - all three resources carry the expected labels.

## 5. `dws-call-http` — auth header + oauth2 sidecar routing (Go)

- [ ] 5.1 In `dws-call-http/internal/config/config.go`, add fields `AuthScheme string`,
      `AuthUsername string`, `AuthPassword string`, `AuthToken string`,
      `AuthHTTPEndpointName string`, populated from env at startup with the same validation style
      as the existing fields. `AuthScheme` accepts `""` / `"basic"` / `"bearer"` / `"oauth2"`;
      other values fail startup.
- [ ] 5.2 In `dws-call-http/internal/runner/runner.go`'s `buildRequest`:
      - `basic` → set `Authorization: Basic <base64(AuthUsername:AuthPassword)>`;
      - `bearer` → set `Authorization: Bearer <AuthToken>`;
      - `oauth2` → rewrite the target URL to
        `http://localhost:${DAPR_HTTP_PORT}/v1.0/invoke/${AuthHTTPEndpointName}/method/<path+query>`,
        preserve method / body / non-`Authorization` headers, and do NOT set `Authorization` (the
        sidecar middleware does that);
      - unset scheme → no change from today.
      Read `DAPR_HTTP_PORT` from env with a sensible default (3500 — Dapr's documented default).
- [ ] 5.3 Unit tests for `buildRequest`: table-driven cases per scheme covering the header shape,
      the URL rewrite (including a request with query string and encoded path characters), and
      the no-scheme baseline. Assert that oauth2 does not add its own `Authorization`.
- [ ] 5.4 Integration test: spin a mock IdP + mock upstream (both `httptest.Server`s), point the
      Dapr sidecar's `oauth2clientcredentials` middleware at the mock IdP (real daprd in a docker
      compose sidecar file, or the existing test harness if one exists — check
      `dws-call-http/internal/`), and assert an `oauth2` call reaches the upstream with the
      injected `Authorization: Bearer <token>`. If the existing test harness does not include a
      real Dapr sidecar, fall back to asserting only the URL rewrite / header-suppression
      behaviour in the step image itself, and cover the middleware end-to-end via a manual test
      recorded in the tasks output.
- [ ] 5.5 `go vet ./... && go test ./...` from `dws-call-http/`.

## 6. `dws-call-openapi` — same env contract wired into swagger-client (Node / TS)

- [ ] 6.1 In `dws-call-openapi/src/config.ts` (or the equivalent config module), add the same
      `AUTH_SCHEME` / `AUTH_USERNAME` / `AUTH_PASSWORD` / `AUTH_TOKEN` /
      `AUTH_HTTPENDPOINT_NAME` env parsing with matching validation (unknown scheme → startup
      failure via the same error style as the existing config fields).
- [ ] 6.2 Wire a `swagger-client` `requestInterceptor` (or the module's chosen request-shaping
      hook — verify against the existing code) that:
      - for `basic` / `bearer`, sets `Authorization` on the outbound request;
      - for `oauth2`, rewrites the `url` to the sidecar invoke form and drops any
        `Authorization` header the resolved operation would have added.
- [ ] 6.3 Vitest coverage in `dws-call-openapi/test/auth.test.ts` (new file if missing) for the
      three schemes, mirroring 5.3's table-driven shape but in TypeScript.
- [ ] 6.4 `pnpm lint && pnpm test && pnpm build` from `dws-call-openapi/`.

## 7. `$secrets` binding in the orchestrator (`dws-orchestrator`)

- [ ] 7.1 In `dws-orchestrator/.../config/WorkflowRuntimeBootstrap.java`, at startup, iterate
      `System.getenv()`, collect every entry whose key starts with `SECRET_`, strip the prefix,
      and populate an immutable `Map<String, String>` on `WorkflowRuntimeState` (or the runtime's
      equivalent state holder — mirror where the loaded definition itself lives).
- [ ] 7.2 In `JqEvaluator`, register a `$secrets` binding backed by that map so `set` / `switch`
      expressions can reference `$secrets.NAME`. If `JqEvaluator` already supports named bindings
      (check whether Phase 1's `$context` / `$input` binding site is generalisable), reuse that
      site; otherwise generalise it.
- [ ] 7.3 Unit tests in `JqEvaluatorTest`:
      - `$secrets.API_KEY` returns the mounted value;
      - an unset `$secrets.MISSING` returns `null` (jq's null-for-missing-key default);
      - map is snapshot at startup (mutating env after construction has no effect on the bound
        map — the map is immutable by construction).
- [ ] 7.4 `./mvnw verify` from `dws-orchestrator/`.

## 8. Cross-component verification

- [ ] 8.1 From `dws-controller/`: `./mvnw verify`.
- [ ] 8.2 From `dws-orchestrator/`: `./mvnw verify`.
- [ ] 8.3 From `dws-call-http/`: `make test && make lint`.
- [ ] 8.4 From `dws-call-openapi/`: `pnpm lint && pnpm test && pnpm build`.
- [ ] 8.5 Grep the compile output of a representative auth-using definition for any plaintext
      credential value; result MUST be empty. A small integration-style test in
      `dws-controller` (feed a definition, dump the resulting `StepService` +
      `HTTPEndpoint` / `Component` YAML, assert no credential value appears anywhere) is the
      preferred long-lived guard.
- [ ] 8.6 Update `docs/roadmaps/openworkflow-features.md`'s Phase 4 row to reflect status
      (in-progress → done) once merged.
