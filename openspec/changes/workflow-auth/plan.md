# OWS Phase 4 — Authentication + Secrets — Implementation Plan

> **For agentic workers:** Use `superpowers:subagent-driven-development` to implement this plan
> task-by-task. Each task below is one subagent hand-off; steps within a task are the micro-loop
> the subagent runs.

**Goal:** Wire `basic` / `bearer` / `oauth2` authentication and DSL `use.secrets` end-to-end
across all four components, with every secret **value** entering a pod exclusively via
K8s `Secret` → `secretKeyRef` and every `oauth2` token fetch handled by Dapr's built-in
`oauth2clientcredentials` middleware (never a hand-rolled cache in a step image).

**Architecture:** DSL surface follows the existing inline-or-named `use.*` pattern
(`CatchPolicy.resolvePolicy` shape). `dws-controller` compiles auth into `StepService`
`secretKeyRef` env and, for `oauth2`, a per-(host, policy) `HTTPEndpoint` + middleware
`Component` + step-scoped `Configuration` triad. `dws-call-http` / `dws-call-openapi` set the
`Authorization` header for `basic` / `bearer` and route through `localhost:${DAPR_HTTP_PORT}/v1.0/invoke/...`
for `oauth2`. `dws-orchestrator` exposes mounted `SECRET_*` env as `$secrets.NAME` for `jq`.

**Tech Stack:** Java 25 / Quarkus (controller) · Java 25 / Spring Boot (orchestrator) · Go 1.26
(`dws-call-http`) · Node 24 / TypeScript / Fastify / pnpm (`dws-call-openapi`) · Dapr sidecar
`oauth2clientcredentials` middleware · K8s `Secret` / `EnvVarSource` / `HTTPEndpoint` /
`Configuration`.

---

## Task 0: Prerequisite check

- [ ] **Step 0.1:** `ls openspec/specs/workflow-timeouts/ openspec/specs/workflow-error-handling/`
      — if either is missing, stop and run `/opsx:archive` on
      `openspec/changes/ows-phase3-errors-timeouts` first. This change's spec deltas assume that
      baseline in `openspec/specs/`.

## Task 1: `StepService.env` gains secret-ref support

Owner: `dws-controller`.
References: `design.md` §D2; `tasks.md` §1.

- [ ] **Step 1.1:** Open `dws-controller/src/main/java/io/dws/controller/model/StepService.java`.
      Sketch the new env representation on paper first: a small `sealed interface EnvEntry`
      permitting `Literal(String value)` and `SecretRef(String secretName, String key)`, held on
      `StepService` as `LinkedHashMap<String, EnvEntry>` (order-preserving).
- [ ] **Step 1.2:** Write the failing unit test first, in a new `StepServiceEnvTest`: build a
      `StepService` with one literal and one ref entry, then assert both round-trip through the
      accessor.
- [ ] **Step 1.3:** Implement the record types and the `env` field. Run
      `./mvnw test -Dtest=StepServiceEnvTest` — expect green.
- [ ] **Step 1.4:** Find every `StepService.env(...)`/`.putAll(...)` call site (`grep -rn
      "\.env(" dws-controller/src`). Update each to construct via the new
      `Literal` path so behaviour is unchanged. Compile and re-run `./mvnw test` for regressions.
- [ ] **Step 1.5:** In `StackSynthesizer.envVars()`, replace the literal-only loop with a
      pattern-match over `EnvEntry`: `Literal → new EnvVarBuilder().withValue(...)`,
      `SecretRef → withValueFrom(EnvVarSource(secretKeyRef(...)))`. Add a
      `StackSynthesizerEnvTest` that asserts a mixed-env `StepService` compiles to the exact
      expected `EnvVar` list.
- [ ] **Step 1.6:** Run `cd dws-controller && ./mvnw verify`. Commit
      `StepService.env`-with-secretKeyRef support (`git add … && git commit -m "…"`).

## Task 2: Authentication compilation

Owner: `dws-controller`.
References: `design.md` §D1 / §D3 / §D4; `specs/workflow-authentication/spec.md`; `tasks.md` §2.

- [ ] **Step 2.1:** Read `CatchPolicy.resolvePolicy` end-to-end for the inline-or-named pattern —
      the new `AuthenticationResolver` mirrors it. Note where the "missing named reference throws"
      diagnostic is built and reuse its exception-message style.
- [ ] **Step 2.2:** Write failing tests in `WorkflowCompilerAuthTest`:
      inline `bearer` → `AUTH_SCHEME=bearer` + `SecretRef` `AUTH_TOKEN`; named `use: my-policy`
      resolves to the same; missing named reference throws with the expected diagnostic; no
      `authentication` declared → no `AUTH_*` env (regression guard); assertion helper
      `assertNoLiteralCredentialValues(stepService)` scans every env entry and fails if any
      `Literal` value would look like a credential (name matches `AUTH_(TOKEN|USERNAME|PASSWORD)`
      but the entry is a `Literal`).
- [ ] **Step 2.3:** Implement `AuthenticationResolver` as a helper on the compiler package.
      Implement the `basic` / `bearer` / `oauth2` branches inside `httpStep` and `openApiStep`.
      For `oauth2`, defer the resource synthesis to Task 4 — this task only writes the env
      (`AUTH_SCHEME=oauth2`, `AUTH_HTTPENDPOINT_NAME=<computed>`) and records the resolved policy
      into a compile-time context object the synthesiser will pick up.
- [ ] **Step 2.4:** Run `./mvnw test -Dtest=WorkflowCompilerAuthTest`, then the full
      `./mvnw test`. Commit.

## Task 3: Secret allow-list validation

Owner: `dws-controller`.
References: `design.md` §D6; `specs/workflow-secrets/spec.md`; `tasks.md` §3.

- [ ] **Step 3.1:** Write failing tests: allow-listed name compiles; missing allow-list rejects
      with a diagnostic naming the referencing location and the missing name; a definition using
      no `use.secret` compiles unchanged whether `use.secrets` is present or absent.
- [ ] **Step 3.2:** In `WorkflowCompiler`, after per-task compilation, walk the whole compiled
      tree and every `{ use.secret: X }` still-present reference (they should have been folded
      into `SecretRef` entries by Task 1 / 2 — the walk is a safety net covering both the auth
      path and any future consumer). Reject if `X ∉ document.use.secrets`.
- [ ] **Step 3.3:** Full `./mvnw test`. Commit.

## Task 4: oauth2 Dapr resource synthesis

Owner: `dws-controller`.
References: `design.md` §D4 / §D5; `specs/workflow-authentication/spec.md`; `tasks.md` §4.

- [ ] **Step 4.1:** Find where existing Dapr resources live in the object graph
      (`grep -rn "Component" dws-controller/src/main/java | grep -i dapr` — inspect the pubsub /
      state-store synthesis site; mirror its shape). Note: `HTTPEndpoint` may not have an
      existing model class — if not, add one alongside the others.
- [ ] **Step 4.2:** Write failing tests in `StackSynthesizerOauth2Test` covering the five
      scenarios listed in `specs/workflow-authentication/spec.md` (single endpoint → three
      resources; same host + same policy → dedup; different hosts → distinct `HTTPEndpoint`s;
      `pathFilter` scope; `Component` metadata is `secretKeyRef`, never literal).
- [ ] **Step 4.3:** In `StackSynthesizer`, add a pass that consumes the compile-time context
      Task 2 populated: compute the dedup hash
      (`sha256(host + issuer + client_id_ref + client_secret_ref + sorted-scopes)[0..8]`) per
      policy usage; group; emit one `HTTPEndpoint` and one `Component` per group; emit one
      `Configuration` per step attaching the middleware with `pathFilter` narrowed to
      `^/v1.0/invoke/<httpendpoint-name>/method/.*$`; label all three with the standard
      `dws.io/*` labels.
- [ ] **Step 4.4:** Empirically confirm the `pathFilter` regex flavour against the Dapr version
      `charts/dws` pins (open the pinned version's docs or the Dapr repo tag; note flavour in
      the code comment).
- [ ] **Step 4.5:** Attach the step-scoped `Configuration` to the Knative Service via the
      standard `dapr.io/config` annotation on the step's Knative Service (mirror any existing
      per-step annotation site — likely already in `StackSynthesizer`).
- [ ] **Step 4.6:** Full `./mvnw verify` from `dws-controller/`. Commit.

## Task 5: `dws-call-http` — auth header + oauth2 sidecar routing

Owner: `dws-call-http` (Go).
References: `design.md` §D3 / §D4; `specs/workflow-authentication/spec.md`; `tasks.md` §5.

- [ ] **Step 5.1:** Read `dws-call-http/internal/config/config.go` and note the existing
      env-parsing / validation style. Read `internal/runner/runner.go`'s `buildRequest` to find
      the extension point — header attachment and URL composition.
- [ ] **Step 5.2:** Write failing tests first in `internal/runner/runner_test.go`
      (table-driven), covering `basic` / `bearer` / `oauth2` / unset per the five scenarios in
      `specs/workflow-authentication/spec.md` + a URL-rewrite case with query string and
      encoded path characters. Also assert that the `oauth2` case does not add its own
      `Authorization`.
- [ ] **Step 5.3:** Add `AuthScheme`, `AuthUsername`, `AuthPassword`, `AuthToken`,
      `AuthHTTPEndpointName` to `Config`. Validate `AuthScheme ∈ {"", "basic", "bearer",
      "oauth2"}` at startup with the existing error style. Read `DAPR_HTTP_PORT` with default
      `3500`.
- [ ] **Step 5.4:** Extend `buildRequest`: switch on `AuthScheme`. For `oauth2`, rewrite the
      target `*url.URL` to `http://localhost:{port}/v1.0/invoke/{name}/method/{path}` preserving
      raw path and query. Run `go test ./internal/runner/... -run TestBuildRequest`.
- [ ] **Step 5.5:** `make test && make lint` from `dws-call-http/`. Commit.
- [ ] **Step 5.6 (optional-if-harness-supports):** Add an integration test that stands up a
      real `daprd` with the `oauth2clientcredentials` middleware pointed at a `httptest.Server`
      mock IdP, calls the step with `AUTH_SCHEME=oauth2`, and asserts the upstream mock received
      `Authorization: Bearer <token>`. If the harness doesn't cover a real sidecar, skip and
      document a manual test recipe in this file.

## Task 6: `dws-call-openapi` — same env contract via `swagger-client`

Owner: `dws-call-openapi` (Node / TS).
References: `design.md` §D8; `specs/workflow-authentication/spec.md`; `tasks.md` §6.

- [ ] **Step 6.1:** Read `dws-call-openapi/src/config.ts` (or the module's equivalent — verify
      exact filename first) and the current request-building site. Locate `swagger-client`'s
      `requestInterceptor` extension point.
- [ ] **Step 6.2:** Add failing Vitest cases in `test/auth.test.ts` mirroring 5.2's table: three
      schemes + unset baseline, with URL-rewrite assertions matching the Go tests.
- [ ] **Step 6.3:** Parse the new env vars in `config.ts` with matching validation style
      (Zod schema if the module uses one — check imports). Wire the `requestInterceptor` that
      attaches `Authorization` for `basic` / `bearer` or rewrites the URL for `oauth2`.
- [ ] **Step 6.4:** `pnpm lint && pnpm test && pnpm build` from `dws-call-openapi/`. Commit.

## Task 7: `$secrets` binding in the orchestrator

Owner: `dws-orchestrator`.
References: `design.md` §D7; `specs/workflow-secrets/spec.md`; `tasks.md` §7.

- [ ] **Step 7.1:** Read `dws-orchestrator/.../config/WorkflowRuntimeBootstrap.java` and note
      where the loaded definition lives (`WorkflowRuntimeState` or equivalent) — the map lives
      alongside it.
- [ ] **Step 7.2:** Read `JqEvaluator`. Check whether a named-binding site already exists (`$input`,
      `$context`, etc.) that we can reuse verbatim; if not, generalise it once and use it for
      `$secrets`.
- [ ] **Step 7.3:** Write failing tests in `JqEvaluatorTest`:
      - `$secrets.API_KEY` returns the mounted value;
      - `$secrets.MISSING` returns jq-null;
      - `$secrets` map is snapshot at construction (mutating the source `Map` after wiring has
        no effect — the map is immutable by construction).
- [ ] **Step 7.4:** Implement: iterate `System.getenv()`, filter `SECRET_` prefix, strip, wrap
      in `Collections.unmodifiableMap`, pass into `JqEvaluator`.
- [ ] **Step 7.5:** `./mvnw verify` from `dws-orchestrator/`. Commit.

## Task 8: Cross-component verification + no-plaintext guard

Owner: shared.
References: `design.md` §Migration Plan; `tasks.md` §8.

- [ ] **Step 8.1:** Add a controller-side integration test (or extend an existing
      `WorkflowCompilerTest` scenario) that:
      1. Compiles a definition using both `bearer` (with `use.secret`) and `oauth2` (with two
         `use.secret` refs);
      2. Serialises the resulting `StepService`, `HTTPEndpoint`, `Component`, and `Configuration`
         to YAML;
      3. Asserts none of the serialisations contain any string that a plaintext credential value
         could plausibly be (use fixed sentinel names in the test's `use.secrets` allow-list
         entries, then `grep` for them in the serialisation — they must appear ONLY as
         `secretKeyRef.name` / `.key`, never as `value`).
- [ ] **Step 8.2:** Run the per-component gate for every touched component:
      - `cd dws-controller && ./mvnw verify`;
      - `cd dws-orchestrator && ./mvnw verify`;
      - `cd dws-call-http && go vet ./... && go test ./...`;
      - `cd dws-call-openapi && pnpm lint && pnpm test && pnpm build`.
      Capture the results in this file's verify tick (Task 8.3).
- [ ] **Step 8.3:** Update `docs/roadmaps/openworkflow-features.md` — Phase 4 row status
      transitions from "next up" (implicit) to done, matching how Phase 3 was updated.
- [ ] **Step 8.4:** Prepare `/opsx:verify` input: this plan's Task 8.1 asserts the
      no-plaintext invariant; spec deltas' scenarios map 1:1 to tests in each component. Ready
      for `/opsx:verify` once every checkbox above is `[x]`.
