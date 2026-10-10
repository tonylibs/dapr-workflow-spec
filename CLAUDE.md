# CLAUDE.md

See [`AGENTS.md`](AGENTS.md) for repository/architecture guidance. This file is the style-guide
index: a package map plus rules that apply across every package. Each package also has its own
`CLAUDE.md` with that stack's idioms only — read the package one before writing code there.

## Package map

| Path | Stack | Note |
|---|---|---|
| [`dws-controller`](dws-controller) | Java 25, Quarkus | DSL 1.0 compiler + Kubernetes apply pass. |
| [`dws-orchestrator`](dws-orchestrator) | Java 25, Spring Boot | Generic Dapr workflow interpreter. |
| [`dws-step`](dws-step) | Java 25, Spring Boot | Minimal single-node step activity host. |
| [`dws-call-http`](dws-call-http) | Go 1.26 | `call: http` step image. |
| [`dws-call-grpc`](dws-call-grpc) | Go 1.26 | `call: grpc` step image (unary only). |
| [`dws-run`](dws-run) | Go 1.26 | `run: shell` / `run: script` step images (3 variants, 1 codebase). |
| [`dws-call-openapi`](dws-call-openapi) | Node 24, TypeScript, Fastify | `call: openapi` step image. |
| [`dws-call-asyncapi`](dws-call-asyncapi) | Node 24, TypeScript, Fastify | `call: asyncapi` step image. |
| [`dws-call-a2a`](dws-call-a2a) | Python 3.13, FastAPI | Prebuilt step image for `call: a2a` tasks. |
| [`dws-admin`](dws-admin) | Node, TypeScript, NestJS, Drizzle | Admin/query API + lifecycle-event projection. |
| [`dws-console`](dws-console) | TypeScript, React, TanStack Start/Router, Vite | Admin console frontend. |
| [`dws-flow`](dws-flow) | .NET 10 | Generic `kind: flow` single-node host (early phase). |
| [`charts/dws`](charts/dws) | Helm | Chart packaging the control plane (`dws-controller`, `dws-admin` + Postgres, optional console/Dapr/Redis/Dex/APISIX). Not a code package — see its own [`README.md`](charts/dws/README.md). |

`dws-call-grpc`, `dws-call-asyncapi`, `dws-call-a2a`, `dws-admin`, `dws-console`, and `dws-flow` are
not yet listed in `AGENTS.md`'s component table — that table predates them. Don't treat its absence
as "this package doesn't exist."

## Cross-cutting style rules

### Commit messages

`<type>(<scope>): <description>`, scope = the package directory name minus `dws-` prefix (`controller`,
`orchestrator`, `console`, `ci`, `openspec`, ...) or a cross-cutting area name. Matches actual repo
history:

```
fix(ci): assert admin's served /dapr/subscribe contract in helm e2e
feat(controller): select compiler strategy from Dapr Configuration flag
refactor(controller): extract WorkflowCompiler interface
docs(openspec): archive phase-1-compiler-strategy-split, sync capability spec
```

Types in use: `feat`, `fix`, `refactor`, `docs`, `chore`, `perf`, `ci`, `test`.

### Cross-package contract-change etiquette

- **Task-name → Dapr app-id resolution** is independently implemented in `dws-controller` (names
  the Knative Service) and `dws-orchestrator` (derives the same name to invoke it) — see
  `AGENTS.md` "Task name → Dapr app-id" section. Changing this logic in one without the other
  breaks routing silently (wrong app-id, 404 at runtime, not a compile-time error).
- **Orchestrator environment contract** (`dws-controller` → `dws-orchestrator`): the controller
  stamps env vars on every orchestrator Deployment (`V1OrchestratorCompiler.orchestratorEnv`) and
  the orchestrator reads them through `dws-orchestrator/src/main/resources/application.yaml`
  (`DAPR_CONFIG_STORE`, `DEFINITION_KEY`, `SECRET_*`). A name that differs on either side is not a
  compile error: the orchestrator silently falls back to its defaults.
  - **Drift, fixed**: the controller used to stamp `DEFINITION_STORE`, which the orchestrator never
    read, so it looked in its default `dws-definitions` store ("configuration store
    dws-definitions not found"). The controller now stamps `DAPR_CONFIG_STORE` — the orchestrator's
    documented name (its README, `application.yaml` and `k8s/deployment.yaml` all use it, and the
    controller was its only producer). `OrchestratorEnvContractTest` in `dws-controller` reads the
    sibling `application.yaml` and fails if a stamped non-secret name is not one the orchestrator
    reads; keep it green rather than editing the name on one side only.
  - The orchestrator pod's **ServiceAccount** is part of the same contract: the definition
    Component reads its ConfigMap as the pod's service account, so the controller stamps
    `serviceAccountName` from `dws.orchestrator.service-account`
    (`DWS_ORCHESTRATOR_SERVICE_ACCOUNT`, default `dws-orchestrator`), and the chart
    (`templates/controller/orchestrator-rbac.yaml`) and `dws-controller/k8s/controller-rbac.yaml`
    must create an account of that name with configmaps `get`/`list`/`watch`. Renaming it in one
    place only fails at runtime (pods are rejected, or the sidecar crash-loops), not at render time.
- **Step-service request/response shape** (`dws-call-http`, `dws-call-grpc`, `dws-call-openapi`,
  `dws-call-asyncapi`, `dws-run`) is consumed by `dws-orchestrator`'s retry/error-classification
  logic (`WorkflowErrors.classify`). If you change a step-service's error/response contract,
  check `dws-orchestrator`'s `WorkflowErrors` and the sibling step-service packages for the same
  concept — they've drifted before (see note below) and are easy to leave inconsistent.
  - **Known drift, not yet fixed**: the Go step-services' READMEs (`dws-call-http`, `dws-call-grpc`,
    `dws-run`) describe an HTTP `POST /run` contract (200/400/502) that the code no longer
    implements — invocation is a Dapr Workflow `activity.Handler` callback now, the only real HTTP
    route left is `GET /healthz`. The TypeScript step-services (`dws-call-openapi`,
    `dws-call-asyncapi`) still genuinely serve `POST /run` over HTTP. Don't assume the Go and TS
    step-services share a live HTTP contract just because `AGENTS.md` says so — verify against the
    Go package's `internal/activity`/`internal/worker` code, not its README, before changing it.
  - `dws-call-openapi` and `dws-call-asyncapi` also disagree on env var naming for the same
    concept — `DOCUMENT_URL`/`DOCUMENT_SHA256` vs `DOC_ENDPOINT`/`DOC_SHA256`. Don't copy one
    package's env var name into the other assuming they match.

### Before calling a change done, run that package's own gate

There is no root-level build; always `cd` into the package first. Exact commands, pulled from
each package's own config:

| Package | Command |
|---|---|
| `dws-controller` | `./mvnw verify` (Spotless auto-formats on `process-sources`; `./mvnw spotless:check` to check without applying) |
| `dws-orchestrator` | `./mvnw verify` |
| `dws-step` | `./mvnw verify` (Spotless auto-formats on `process-sources`; `./mvnw spotless:check` first to see drift, as CI does) |
| `dws-call-http` | `make lint && make test` |
| `dws-call-grpc` | `make lint && make test` |
| `dws-run` | `make lint && make test` |
| `dws-call-openapi` | `pnpm lint && pnpm test && pnpm build` |
| `dws-call-asyncapi` | `pnpm lint && pnpm test && pnpm build` |
| `dws-call-a2a` | `uv run ruff check . && uv run ruff format --check . && uv run pyright && uv run pytest` |
| `dws-admin` | `pnpm lint && pnpm test && pnpm build` |
| `dws-console` | `pnpm check && pnpm typecheck && pnpm test && pnpm build` (`check` = Biome lint+format) |
| `dws-flow` | `dotnet test test/dws-flow.Tests.csproj` (name the test project: a bare `dotnet test` in `dws-flow/` only finds the app project, runs no tests and exits 0). No lint/format gate configured yet — no `.editorconfig`, no analyzers |

Windows: Java packages use `mvnw.cmd` instead of `./mvnw`.

`charts/dws` has no build, but it has its own gate — run it from the repository root before
calling a chart change done:

The observability render test byte-compares a fixture and therefore requires Helm 3.19.0, matching
the pinned version in `.github/workflows/helm.yml`.

```sh
helm lint charts/dws
helm template dws charts/dws
bash charts/dws/tests/values-schema-test.sh charts/dws
bash charts/dws/tests/api-gateway-render-test.sh charts/dws
bash charts/dws/tests/auth-pipeline-placement-test.sh charts/dws
bash charts/dws/tests/observability-render-test.sh charts/dws
bash charts/dws/tests/orchestrator-wiring-render-test.sh charts/dws
```

`helm lint`/`helm template` do not run Kubernetes' apimachinery validation, so a manifest that
renders cleanly can still be rejected by a real API server (this has bitten the APISIX Service's
`externalTrafficPolicy` before). For anything touching the Gateway/auth path, rehearse against a
live cluster with `scripts/verify-console-ingress-migration.sh`.

### Releasing

Component versions bump automatically (release-please), but tagging a component image and
cutting the Helm chart are deliberate, manually-triggered steps. See
[`docs/release-process.md`](docs/release-process.md) for the end-to-end runbook before
touching `release-please-config.json`, the `*-release.yml` workflows, or
`charts/dws/values.yaml` pins.

<!-- OPENWIKI:START -->

## OpenWiki

This repository uses OpenWiki for recurring code documentation. Start with `openwiki/quickstart.md`, then follow its links to architecture, workflows, domain concepts, operations, integrations, testing guidance, and source maps.

The scheduled OpenWiki GitHub Actions workflow refreshes the repository wiki. Do not hand-edit generated OpenWiki pages unless explicitly asked; prefer updating source code/docs and letting OpenWiki regenerate.

<!-- OPENWIKI:END -->
