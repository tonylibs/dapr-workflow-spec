# OWS DSL Feature Roadmap

Gap analysis of [Open Workflow Spec DSL 1.0](https://github.com/open-workflow-specification/specification/blob/main/dsl.md) vs. current `dws-orchestrator`/`dws-controller` support, phased into build order.

## 1. Current task-type coverage

Two different readiness axes get conflated below — worth separating:

- **Control-flow tasks** (`switch`, `set`, `wait`, `listen`, `emit`) run **in-process** in `dws-orchestrator` (jq eval, Dapr timer/external-event/pub-sub primitives). No prebuilt image was ever needed for these, so they're genuinely done despite no new image work.
- **I/O tasks** (`call`, `run`) compile to a deployed **StepService backed by a prebuilt image**. `call: http`, `call: openapi`, and `run` (`shell`/`script`) all have real images now (`dws-call-http`, `dws-call-openapi`, `dws-run`); `run: container`/`run: workflow` are rejected at compile time — no deployable image exists for either.

| Task | Status | Notes |
|---|---|---|
| `call` (http) | ✅ | StepService via `dws-call-http` (built) |
| `call` (openapi) | ✅ | StepService via `dws-call-openapi` (built) |
| `call` (grpc/asyncapi/a2a) | ❌ | not started |
| `run` (shell/script) | ✅ | StepService via `dws-run` (`dws-run-shell`/`dws-run-script-js`/`dws-run-script-python`); shipped in `2026-07-26-dws-run` |
| `run` (container/workflow) | ❌ | rejected at compile time — no deployable image for either |
| `switch` | ✅ | jq eval in a local in-process activity, no image needed |
| `set` | ✅ | jq eval in a local in-process activity, no image needed |
| `wait` | ✅ | Dapr timer, no image needed |
| `listen` | ✅ | single external event only, no correlation (`one`/`any`/`all`); no image needed |
| `emit` | ✅ | pub/sub, no image needed |
| `for` | ✅ | in-process, no image needed — collection resolved via `EvaluateForActivity`, body scoped through `runTaskList` per iteration with `$<each>`/`$<at>` bound, optional `while` early-exit via `EvaluateWhileActivity`; shipped in `for-task` (new capability `workflow-iteration`) |
| `try` | ✅ | full `try`/`catch`/`retry`: static (`catch.errors.with`) + dynamic (`catch.when`/`exceptWhen`) filtering, error object bound under `catch.as`, retry with backoff/jitter/limits (inline or named via `use.retries`), `catch.do` recovery — shipped in `try-catch-retry`, merged to `main` |
| `fork` | ✅ | in-process orchestration, one Dapr child workflow instance per branch — join (`compete: false`, default) via `ctx.allOf` returns branch outputs as an array in declared order, race (`compete: true`) via `ctx.anyOf` returns the first branch to settle and abandons the rest; shipped in `fork-task` (new capability `workflow-parallelism`) |
| `raise` | ✅ | in-process, no image needed — the author's five-field error is evaluated then thrown, surviving classification unmodified and caught by the same `catch.errors.with`/`when` machinery as any real failure; shipped in `raise-task` |
| nested `do` | ✅ | scope-aware task-list runner generalized to every container task type — `try`/`catch.do`, `for.do`, and `fork` branches; `dws-controller`'s compile-time walk covers all three, so `call`/`run` nested in any of them deploys the expected step services; shipped across `try-catch-retry`, `for-task`, and `fork-task` |

## 2. Cross-cutting spec features — status

| Feature | Status |
|---|---|
| Data flow (`input.from/schema`, `output.as/schema`, `export.as/schema`) | ❌ raw data passed through untransformed |
| Errors as Problem Details (RFC 7807) + standard error types | ✅ |
| Timeouts (workflow/task) | ✅ |
| Authentication (basic/bearer/oauth2) | ❌ |
| Secrets | ❌ |
| Catalogs / custom functions | ❌ |
| Extensions (`before`/`after` hooks) | ❌ |
| External resources | ❌ |
| Scheduling (`every`/`cron`/`after`/`on`) | ❌ controller deploys on `POST` only |
| Lifecycle events (CloudEvents) | ✅ done — controller + orchestrator publish to `dws.events` (Epic 1, merged) |

## 3. Phase dependency graph

```mermaid
flowchart TD
  P0[Phase 0: Lifecycle Events ✅] --> P8[Phase 8: dws-admin read model ✅]
  P05[Phase 0.5: dws-run image ✅]
  P1[Phase 1: Data Flow Pipeline ✅] --> P2a[Phase 2.1: try/catch/retry ✅]
  P2a --> P2b[Phase 2.2: raise ✅]
  P2b --> P2c[Phase 2.3: for ✅]
  P2c --> P2d[Phase 2.4: fork parallel +<br/>generalize nested do ✅]
  P1 --> P3[Phase 3: Fault Tolerance<br/>Problem Details, timeouts ✅]
  P2d --> P3
  P3 --> P4[Phase 4: Authentication + Secrets<br/>current]
  P4 --> P5[Phase 5: Protocol Expansion<br/>gRPC, AsyncAPI, A2A]
  P1 --> P6[Phase 6: Scheduling<br/>cron/every/after/on]
  P4 --> P7[Phase 7: Catalogs + Extensions]
```

Data flow is the foundation: retry/catch, extensions, and error handling all read/write through `input`/`output`/`context`, so it must land before Phases 2–7 are worth building correctly.

## 4. Phased roadmap

| Phase | Scope | Components | Route |
|---|---|---|---|
| **0** ✅ | Lifecycle CloudEvents publishing | controller, orchestrator | done — Epic 1, merged |
| **0.5** ✅ | Build `dws-run` prebuilt images (shell/script); container/workflow rejected at compile time | `dws-run` component | done — `2026-07-26-dws-run`, merged |
| **1** ✅ | `input.from/schema`, `output.as/schema`, `export.as/schema`, validation faults | orchestrator | done — `2026-07-27-data-flow-pipeline`, merged |
| **2** ✅ | `try`/`catch`/`retry`, `raise`, `for`, `fork` (parallel), nested `do` | orchestrator, controller | done — `try-catch-retry`, `raise-task`, `for-task`, `fork-task` |
| **3** ✅ | RFC 7807 error model, standard error types, task/workflow timeouts | orchestrator | complete |
| **4** **(current)** | `basic`/`bearer`/`oauth2` auth, secrets resolution | controller, orchestrator, call-http, call-openapi | opsx — new capability |
| **5** | gRPC, AsyncAPI, A2A call protocols | new `dws-call-grpc`/`dws-call-asyncapi`/`dws-call-a2a` images | opsx — new components |
| **6** | `schedule.every/cron/after/on` triggers | controller (Dapr Jobs API / cron binding) | opsx — new capability |
| **7** | Catalogs, custom functions, extensions (`before`/`after`), external resources | controller, orchestrator | opsx — new capability |
| **8** ✅ | `dws-admin` consumes lifecycle events into read model, exposes read API | dws-admin | done — Epics 2–3, merged |

## 4a. Phase 2 slice detail

Phase 2 shipped as separate opsx changes rather than one, because `try`/`catch` alone justified
introducing the scope-aware task-list runner (`runTaskList`) that every later slice reuses:

| Slice | Scope | Status |
|---|---|---|
| 2.1 | `try`/`catch`/`retry`, the scope-aware runner (`runTaskList`), scope-local flow directives (`exit` vs `end`), depth guard, recursive task lookup and compile-time nesting into `try`/`catch.do` | ✅ done — `openspec/changes/try-catch-retry` (all tasks checked, code merged to `main`; **not yet run through `/opsx:archive`**) |
| 2.2 | `raise` — explicit error construction/throw from a task, matched by the same `catch.errors.with`/`when` machinery slice 2.1 built | ✅ done — `openspec/changes/raise-task`; no controller change was needed, and `raise.error.status` is literal-only (the pinned SDK models no expression variant) |
| 2.3 | `for` — currently recognized, throws `UnsupportedOperationException`; reuses `runTaskList` for the loop body the same way `try` does | ✅ done — `openspec/changes/for-task`; no controller change was needed, and `DefinitionLookup` gained the `for.do` recursion branch |
| 2.4 | `fork` (parallel branches) + generalizing nested `do` to any task type that nests a list | ✅ done — `openspec/changes/fork-task`; each branch runs as its own Dapr child workflow instance (`ForkBranchWorkflow`), joined via `ctx.allOf` (`compete: false`) or raced via `ctx.anyOf` (`compete: true`); `WorkflowCompiler.walk()`/`collectTaskNames()` extended to both `fork` branches and `for.do` |

## 5. Rationale for ordering

- **1 before 2/3**: retry/catch and error handling are meaningless without a real input/output/context pipeline to operate on.
- **2 before 3**: `try`/`raise` define the fault surface that timeouts and Problem Details formatting attach to.
- **4 before 5/7**: new protocols and catalogs both need auth to call real external services.
- **6 is independent**: scheduling only touches the controller's trigger path, not the interpreter — can be pulled forward if needed.
- **8 last**: read model is a pure consumer of Phase 0's event contract; no orchestrator/controller changes required once events exist.

## 5a. Phase 4 secret extension

Phase 4 adds the DWS-specific `$secrets` scope to `set` and `switch`. Unlike the upstream DSL,
this can expose secret material through assigned workflow data or selected branches, so authors
must treat it as potentially leaking data.

## Future spikes

- **Static-credential Dapr Wasm middleware:** Evaluate a workflow-scoped Wasm filter that creates
  Basic or Bearer `Authorization` headers before Dapr invokes an external endpoint. This is not
  Phase 4 scope, which retains runner-local Basic/Bearer construction. A spike must assess Wasm
  artifact ownership and supply chain, secret-derived configuration, request-path isolation, and
  compatibility with the Dapr 1.18.1 runtime pinned by `charts/dws`.
