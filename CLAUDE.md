# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

DWS (dapr-workflow-spec) is a config-driven workflow platform for Kubernetes built on
[Dapr](https://dapr.io/) and the [Open Workflow Specification](https://open-workflow-specification.org/)
DSL 1.0. Workflow definitions are plain YAML/JSON — **no per-workflow code is ever written or
generated**. A definition is `POST`ed to the controller, which compiles it and deploys the
corresponding Dapr-backed resources; a generic orchestrator then interprets the definition at
runtime.

This is a **monorepo of five independently-built components**, each with its own toolchain,
`Dockerfile`, and path-filtered CI workflow. There is no shared build system — always `cd` into
the component directory before running its commands.

| Component | Language/Framework | Role |
|---|---|---|
| [`dws-controller`](dws-controller) | Java 25, Quarkus | Accepts DSL 1.0 definitions, compiles them, deploys one stack per definition (definition ConfigMap, Dapr Configuration component, Knative Services per I/O task, orchestrator Deployment). Has its own [`dws-controller/CLAUDE.md`](dws-controller/CLAUDE.md) — read it before working in that directory. |
| [`dws-orchestrator`](dws-orchestrator) | Java 25, Spring Boot | Generic, config-driven Dapr workflow orchestrator (interpreter pattern). Loads one workflow definition at startup and walks its task list. |
| [`dws-call-http`](dws-call-http) | Go 1.26 | Prebuilt step image for `call: http` tasks. One image serves every HTTP call step; behavior is entirely environment-configured. |
| [`dws-call-openapi`](dws-call-openapi) | Node 24, TypeScript, Fastify | Prebuilt step image for `call: openapi` tasks. Loads an OpenAPI document, resolves an operation, executes it via `swagger-client` + `undici`. |
| [`dws-run`](dws-run) | Go 1.26 | Prebuilt step images for `run: shell` and `run: script` tasks. One codebase produces three images (`dws-run-shell`, `dws-run-script-js`, `dws-run-script-python`) differing only in base layer and interpreter. |

### How it fits together

1. A client `POST`s a DSL 1.0 definition to `dws-controller`.
2. The controller validates and compiles it, then deploys: an immutable, versioned definition
   (stored in a Dapr Configuration component), one scale-to-zero Knative Service per I/O (`call`
   or `run`) task using the prebuilt `dws-call-http`/`dws-call-openapi`/`dws-run-*` images, and a
   dedicated `dws-orchestrator` Deployment.
3. `dws-orchestrator` loads the definition once at startup and interprets it: `call` and `run`
   tasks invoke the corresponding step service via Dapr service invocation, `switch`/`set` are
   evaluated with `jq`, `wait`/`listen`/`emit` map to Dapr timers, external events, and pub/sub.

Each deployed workflow gets its own orchestrator plus one step service per `call`/`run` task. See
the root [`README.md`](README.md) for the full deployment diagram.

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

### dws-run (Go 1.26)

```shell
cd dws-run
make build          # compile bin/dws-run
make test           # go test -race ./...
make vet            # go vet ./...
make fmt-check      # gofmt check
make lint           # vet + fmt-check (+ golangci-lint if installed)
make docker         # build all three images (dws-run-shell, dws-run-script-js, dws-run-script-python)
```

One Go codebase produces three images (`dws-run-shell`, `dws-run-script-js`,
`dws-run-script-python`) from three Dockerfiles sharing an identical Go build stage and differing
only in final-stage base image and exec command; `MODE` selects the interpreter at runtime.

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
  `call openapi` / `run shell` / `run script` tasks each become a `StepService` (a deployed
  Knative Service) using the corresponding prebuilt image — `dws-call-http`, `dws-call-openapi`,
  or one of `dws-run-shell` / `dws-run-script-js` / `dws-run-script-python` (chosen by the
  script's `language`); `run container` and `run workflow` are rejected at compile time (no
  deployable image exists for either). `emit` / `listen` become a topic binding only (nothing
  deployed); `switch` / `set` / `wait` / `for` / `try` / `raise` deploy nothing themselves — they're
  interpreted in-process by the orchestrator. A `try` task's nested `try` / `catch.do` lists **are**
  walked at compile time, so tasks inside them map to resources exactly as at top level; lists
  nested under `for` / `fork` are not walked.
- **Task name → Dapr app-id (kebab-case adapter)**: `dws-orchestrator` resolves a `call` task's
  target purely from its task name — `checkInventory` → Dapr app-id `check-inventory`, invoked at
  `POST /run`. Task names are therefore **unique across the whole definition at every depth**, and
  `dws-controller` rejects duplicates at compile time. The `with.endpoint` field in the DSL is
  schema-required but **ignored** for
  routing. This convention is implicit and independently relied on by both `dws-controller`
  (which names the Knative Service/app-id) and `dws-orchestrator` (which derives the same name
  from the task) — keep the two in sync if this logic changes.
- **Content-addressed versioning**: workflow versions are `<name>@v<sha256-8>` of the
  *canonicalized* (parsed, key-sorted, re-serialized) definition. Identical content re-posted is a
  no-op. Definition storage is immutable; Knative Service names are **not** version-suffixed
  (they're the stable Dapr app-id), so a new version updates the same object in place and old,
  dropped steps are garbage-collected by label.
- **Shared step-service HTTP contract**: `dws-call-http`, `dws-call-openapi`, and `dws-run`
  independently implement the *same* contract — `POST /run` (body = current workflow data JSON,
  empty body = `{}`), `GET /healthz`, `OUTPUT=replace|merge` response shaping, and `502` (not
  `400`/`500`) for upstream/transport failures specifically so the orchestrator's retry policy
  re-invokes the step. A new step image should follow this same contract.
- **No persistence layer anywhere**: `dws-controller` answers every `GET` from live cluster state
  selected by `dws.io/*` labels; `dws-orchestrator` holds only its one pinned, immutable
  definition for the pod's lifetime (no config-change subscription, no DB).
- **CI**: each component has its own path-filtered GitHub Actions workflow
  (`.github/workflows/<component>.yml`) — test/lint/build gates on every push and PR, image
  build+push to `ghcr.io/tonylibs/<component>` only on merge to `main` (PRs build the image to
  validate the Dockerfile but don't push).

## Workflow routing (read on session start)

This repo uses [`superpowers-bridge`](https://github.com/JiangWay/openspec-schemas/tree/main/superpowers-bridge) to bridge OpenSpec and Superpowers. Integration rules (language, artifact paths, PRECHECK) follow that bridge's README; this section is the routing guidance for Claude.

### Entry routing

| Trigger you observe | What to do |
|---|---|
| User starts a narrative "design discussion / let's brainstorm" | Run verbal `superpowers:brainstorming`, but **do NOT** write to `docs/superpowers/specs/`. Once the conversation converges per the 5 criteria below, promote to `/opsx:propose` |
| User invokes `/opsx:new` / `/opsx:ff` / `/opsx:propose` directly | Follow the schema's flow; artifact instructions inject at each step |
| User explicitly says bug fix / typo / config tweak / doc update | Direct PR — **do NOT** open a change (see skip rules below) |
| User is mid-change | Advance with `/opsx:continue`, `/opsx:apply`, `/opsx:verify`, or `/opsx:archive` |

### When NOT to use opsx (direct PR)

| Scenario | Direct PR? |
|---|---|
| New feature / new capability / architectural change / breaking change | ❌ Use opsx |
| Bug fix (no contract change) / test backfill / linter tweak / non-breaking upgrade / typo / docs / config value tweak | ✅ Direct PR |

Principle: **process ceremony scales with risk**. External contracts / schema / cross-system integration / compliance → opsx. Otherwise → direct PR.

### Verbal brainstorm → opsx promotion criteria

All 5 must hold before promoting (any missing → keep brainstorming, **never** write to `docs/superpowers/specs/`):

1. **Scope locked** — one sentence describes what's in / out
2. **Major design forks resolved** — alternatives weighed; remaining TBDs have an owner and impact-scope statement
3. **Cross-system dependencies mapped** — ready / mockable / genuinely unknown — pick one per dep
4. **Acceptance criteria stateable** — concrete pass conditions (e.g., `./mvnw clean verify` passes + N deliverables)
5. **Conversation converging** — recent turns are confirmations, not new alternatives

When all 5 hold → proactively suggest "ready to `/opsx:propose`?" — wait for user ack. Never auto-trigger.

### Front-door anti-patterns (don't do)

- Letting brainstorming write to `docs/superpowers/specs/`
- Letting writing-plans write to `docs/superpowers/plans/`
- Promoting to opsx with unresolved blocking TBDs
- Opening a change for bug fix / typo

Full detail: [superpowers-bridge README §Entry & exit gates](https://github.com/JiangWay/openspec-schemas/blob/main/superpowers-bridge/README.md#entry--exit-gates).

<!-- OPENWIKI:START -->

## OpenWiki

This repository uses OpenWiki for recurring code documentation. Start with `openwiki/quickstart.md`, then follow its links to architecture, workflows, domain concepts, operations, integrations, testing guidance, and source maps.

The scheduled OpenWiki GitHub Actions workflow refreshes the repository wiki. Do not hand-edit generated OpenWiki pages unless explicitly asked; prefer updating source code/docs and letting OpenWiki regenerate.

<!-- OPENWIKI:END -->
