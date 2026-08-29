# ADR 0001: Workflow Runtime v2 — Per-Node Deployment and Uniform Step Transport

- **Status:** Accepted
- **Date:** 2026-08-29
- **Context:** [`docs/roadmaps/workflow-runtime-architecture-roadmap.md`](../roadmaps/workflow-runtime-architecture-roadmap.md)
  — Phase 0's deliverable is "write the ADR covering both decisions below"; this is that ADR.
- **Related:** [`docs/roadmaps/workflow-runtime-architecture.md`](../roadmaps/workflow-runtime-architecture.md)
  (target-state spec)

## Context

Today `dws-controller` compiles a definition into **one** `dws-orchestrator` Deployment (a generic
Java interpreter that loads a pinned definition at startup) which runs `switch`/`set`/`wait`/
`listen`/`emit`/`for`/`try`/`catch`/`retry`/`fork`/`raise` in-process, and only farms `call`/`run`
tasks out to prebuilt Knative functions (`dws-call-*`, `dws-run-*`). The Workflow Runtime
Architecture spec turns every task in its classification table into its own deployable Flow or
Step component, cross-app-invoked. That's a structural rewrite of the runtime (`dws-orchestrator`
and `dws-controller`'s `WorkflowCompiler`/`StackSynthesizer` are both in scope), and it left two
open structural questions the spec itself doesn't pin down. Both had to be resolved before Phase 1
(the structural compiler) could start.

## Decision 1: One Dapr app per graph node

Every compiled Flow scope (`main`, each `for`, `try`, `catch`, fork branch) gets its own deployed
`dws-flow` instance, and **every compiled Step task, with no exceptions**, gets its own deployed
`dws-step` instance — each addressed by its derived identifier as the Dapr app ID. Not a shared app
multiplexing scopes as registered workflow types, and no carve-out for the Go-backed task kinds.

**Rationale:** the closest literal reading of the spec ("can be scheduled from its parent with a
target app ID"), and it lines up with the convention `dws-controller` already uses — `call`/`run`
task names become kebab-case Dapr app IDs for their Knative Services (root `CLAUDE.md` §
*Task name → Dapr app-id*). Per-node app IDs generalize that same rule to every task type.

**Consequences:**
- Pod count scales with graph size on *both* axes: Flow pods with scope nesting (a `for` inside a
  `try` inside a `fork` branch is 4+ Flow pods on its own), and Step pods 1:1 with every Step task.
- A `call`/`run` task becomes *two* Dapr apps, not one: the new `dws-step` proxy plus its existing,
  unchanged Knative Service underneath.
- **Naming-collision corollary, resolved 2026-08-29:** `dws-step`'s natural app ID for a `call`/`run`
  task is the same derived name (e.g. `reserve-item`) the *existing* Knative Service already uses.
  Resolution: `dws-step` keeps the plain, unsuffixed, task-derived name — it's the node the rest of
  the graph addresses. The underlying function's own Knative Service app ID instead gets an
  explicit `-fn` suffix (`reserve-item` → `reserve-item-fn`), since it's the pre-existing side and
  `dws-step` becomes the only thing calling it directly going forward.
- `fulfillOrder.catch`'s `.` isn't a legal Kubernetes Service/app-ID character — Phase 1 must
  sanitize dots to dashes when turning a derived identifier into a Dapr app ID (the same kind of
  transform `checkInventory` → `check-inventory` already does for `call`/`run` task names today,
  extended to dotted identifiers).
- Open capacity question (not blocking, deferred to Phase 4): Flow/Step apps hold or front runtime
  state, so they aren't an obvious scale-to-zero fit the way the Knative functions already are —
  worth a capacity check once Phase 4 has a real compiled graph to measure, and worth reading the
  existing `step-service-scaling` spec (repo: `openspec/specs/step-service-scaling/spec.md`) for
  prior art on whether `dws-step` itself can follow a similar scale-to-zero path.

## Decision 2: Uniform Step layer — every function image goes back to plain HTTP

All six prebuilt function images (`dws-call-http`, `dws-run-shell`/`-script-js`/`-script-python`,
`dws-call-grpc`, `dws-call-openapi`, `dws-call-asyncapi`) present a plain Dapr-sidecar HTTP
interface — `POST /run`, `GET /healthz` — with no Dapr Workflow SDK and no registered activity.
`dws-step` becomes the one uniform Java Activity boundary in front of every I/O step; no task kind
is special-cased.

**Rationale:** today three of the six images (the Go group: `call: http`, `run: shell`,
`run: script`, `call: grpc`) are already Dapr Workflow activity workers, letting
`dws-orchestrator` skip a hop via `CallActivityAsync` straight to the function's app-id. Keeping
that shortcut in v2 would mean building, testing, and reasoning about two dispatch paths instead
of one. Trading it away for a single uniform path — `dws-flow` → `CallActivityAsync` →
`dws-step` Activity → Dapr service invocation → function's `POST /run` — is simpler, and matches
the spec's own Step-to-function delegation section exactly.

**Consequences:**
- `dws-call-http`, `dws-run-*`, and `dws-call-grpc` need a real code change: a plain HTTP
  `POST /run` handler added, matching `dws-call-openapi`/`dws-call-asyncapi`'s existing shape. The
  step-execution logic itself (`runner.Run` internals) is untouched — only the transport surface
  around it changes, not what a step *does*.
- **Sequencing hazard, resolved:** v1 (`dws-orchestrator`) depends on those three staying activity
  workers — that's literally how it dispatches them today (`StepActivity.java`,
  `activity-step-dispatch` spec). Resolution: add the plain-HTTP handler in Phase 2 *without*
  removing the activity-worker registration yet; only delete the now-unused activity-worker code
  as a cleanup step once Phase 5 retires `dws-orchestrator` for good. This keeps v1 working
  throughout the Phase 5 side-by-side parity-testing window.

## Non-goals

Same as the roadmap's own non-goals: no DSL semantic changes; no reimplementing what the functions
*do* (only how they're invoked); no `dws-console` changes — its DSL-based workflow display is
unaffected by this backend runtime/deploy rewrite.

## Status

Both decisions above, including the naming-collision corollary of Decision 1, are fully resolved —
no open design questions remain blocking Phase 1. Phase 0's remaining work is the scaffolding half
of its deliverable: `dws-flow` (.NET) / `dws-step` (Java/Spring) component templates and the
language-neutral single-node definition JSON contract.
