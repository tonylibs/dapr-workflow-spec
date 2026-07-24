# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

DWS (dapr-workflow-spec) is a config-driven workflow platform for Kubernetes built on
[Dapr](https://dapr.io/) and the [Open Workflow Specification](https://open-workflow-specification.org/)
DSL 1.0. Workflow definitions are plain YAML/JSON — **no per-workflow code is ever written or
generated**. A definition is `POST`ed to the controller, which compiles it and deploys the
corresponding Dapr-backed resources; a generic orchestrator then interprets the definition at
runtime.

This is a **monorepo of four independently-built components**, each with its own toolchain,
`Dockerfile`, and path-filtered CI workflow. There is no shared build system — always `cd` into
the component directory before running its commands.

| Component | Language/Framework | Role |
|---|---|---|
| [`dws-controller`](dws-controller) | Java 25, Quarkus | Accepts DSL 1.0 definitions, compiles them, deploys one stack per definition (definition ConfigMap, Dapr Configuration component, Knative Services per I/O task, orchestrator Deployment). Has its own [`dws-controller/CLAUDE.md`](dws-controller/CLAUDE.md) — read it before working in that directory. |
| [`dws-orchestrator`](dws-orchestrator) | Java 25, Spring Boot | Generic, config-driven Dapr workflow orchestrator (interpreter pattern). Loads one workflow definition at startup and walks its task list. |
| [`dws-call-http`](dws-call-http) | Go 1.26 | Prebuilt step image for `call: http` tasks. One image serves every HTTP call step; behavior is entirely environment-configured. |
| [`dws-call-openapi`](dws-call-openapi) | Node 24, TypeScript, Fastify | Prebuilt step image for `call: openapi` tasks. Loads an OpenAPI document, resolves an operation, executes it via `swagger-client` + `undici`. |

### How it fits together

1. A client `POST`s a DSL 1.0 definition to `dws-controller`.
2. The controller validates and compiles it, then deploys: an immutable, versioned definition
   (stored in a Dapr Configuration component), one scale-to-zero Knative Service per I/O (`call`)
   task using the prebuilt `dws-call-http`/`dws-call-openapi` images, and a dedicated
   `dws-orchestrator` Deployment.
3. `dws-orchestrator` loads the definition once at startup and interprets it: `call` tasks invoke
   the corresponding step service via Dapr service invocation, `switch`/`set` are evaluated with
   `jq`, `wait`/`listen`/`emit` map to Dapr timers, external events, and pub/sub.

Each deployed workflow gets its own orchestrator plus one step service per `call` task. See the
root [`README.md`](README.md) for the full deployment diagram.

## Commands

There is no top-level build — run these from inside each component directory.

### dws-controller (Java 25, Quarkus, Maven)

```shell
cd dws-controller
./mvnw quarkus:dev                                   # dev mode, Dev UI at localhost:8080/q/dev/
./mvnw test                                          # unit tests
./mvnw test -Dtest=WorkflowCompilerTest               # single test class
./mvnw test -Dtest=WorkflowCompilerTest#versionIsStable # single test method
./mvnw package                                       # target/quarkus-app/quarkus-run.jar
./mvnw verify                                        # package + integration tests (*IT.java)
```

Windows: use `mvnw.cmd` instead of `./mvnw`. See `dws-controller/CLAUDE.md` for the compiler/apply
architecture, key invariants, and cdk8s build quirks.

### dws-orchestrator (Java 25, Spring Boot, Maven)

```shell
cd dws-orchestrator
./mvnw verify                                        # compile + test
./mvnw test -Dtest=JqEvaluatorTest                    # single test class
```

### dws-call-http (Go 1.26)

```shell
cd dws-call-http
make build          # compile bin/dws-call-http
make test           # go test -race ./...
go test ./internal/runner/ -run TestInterpolate  # single test
make vet            # go vet ./...
make lint           # vet + gofmt check (+ golangci-lint if installed)
make docker         # build registry.io/dws/dws-call-http:1.0
```

CI gate: `go vet ./... && go test ./...`.

### dws-call-openapi (Node 24, TypeScript, pnpm)

```shell
cd dws-call-openapi
pnpm install
pnpm lint            # eslint .
pnpm test            # vitest run
pnpm test:watch      # vitest watch mode
pnpm build           # tsc -p tsconfig.json
pnpm dev             # node --watch --experimental-strip-types src/index.ts
```

CI gate: `pnpm lint && pnpm test && pnpm build`. To run a single test file, use vitest directly:
`pnpm vitest run test/auth.test.ts`.

## Cross-cutting architecture

These conventions span multiple components — understanding them requires reading more than one
codebase, so they're captured here rather than in a single component's docs.

- **Task → resource mapping** (applied by `dws-controller` at compile time): `call http` /
  `call openapi` / `run` tasks each become a `StepService` (a deployed Knative Service); `emit` /
  `listen` become a topic binding only (nothing deployed); `switch` / `set` / `wait` / `for` /
  `try` / `raise` deploy nothing — they're interpreted in-process by the orchestrator.
- **Task name → Dapr app-id (kebab-case adapter)**: `dws-orchestrator` resolves a `call` task's
  target purely from its task name — `checkInventory` → Dapr app-id `check-inventory`, invoked at
  `POST /run`. The `with.endpoint` field in the DSL is schema-required but **ignored** for
  routing. This convention is implicit and independently relied on by both `dws-controller`
  (which names the Knative Service/app-id) and `dws-orchestrator` (which derives the same name
  from the task) — keep the two in sync if this logic changes.
- **Content-addressed versioning**: workflow versions are `<name>@v<sha256-8>` of the
  *canonicalized* (parsed, key-sorted, re-serialized) definition. Identical content re-posted is a
  no-op. Definition storage is immutable; Knative Service names are **not** version-suffixed
  (they're the stable Dapr app-id), so a new version updates the same object in place and old,
  dropped steps are garbage-collected by label.
- **Shared step-service HTTP contract**: `dws-call-http` and `dws-call-openapi` independently
  implement the *same* contract — `POST /run` (body = current workflow data JSON, empty body =
  `{}`), `GET /healthz`, `OUTPUT=replace|merge` response shaping, and `502` (not `400`/`500`) for
  upstream/transport failures specifically so the orchestrator's retry policy re-invokes the step.
  A new call-type step image should follow this same contract.
- **No persistence layer anywhere**: `dws-controller` answers every `GET` from live cluster state
  selected by `dws.io/*` labels; `dws-orchestrator` holds only its one pinned, immutable
  definition for the pod's lifetime (no config-change subscription, no DB).
- **CI**: each component has its own path-filtered GitHub Actions workflow
  (`.github/workflows/<component>.yml`) — test/lint/build gates on every push and PR, image
  build+push to `ghcr.io/tonylibs/<component>` only on merge to `main` (PRs build the image to
  validate the Dockerfile but don't push).
