## Why

`WorkflowCompiler.runStep()` already compiles `run` tasks, but `images.run()` defaults to the
placeholder `sw-run:1.0` and no component in this repo builds it — every `run` workflow compiles
cleanly and deploys a Knative Service that can never pull an image. Two of the DSL's four `run`
subtypes (`container`, `workflow`) fall through both branches and produce an empty-env StepService,
turning a definition error into a runtime mystery. This change makes `run: shell` and `run: script`
genuinely deployable by building the missing step image, and converts the two unsupported subtypes
into clear compile-time rejections.

## What Changes

**Missing `run` step image**
- From: `dws.images.run` points at the placeholder `sw-run:1.0`; nothing builds it.
- To: a new Go component `dws-run` publishes three images —
  `ghcr.io/tonylibs/dws-run-shell`, `dws-run-script-js`, `dws-run-script-python` — from one shared
  codebase, differing only in final-stage base and exec command.
- Reason: `run` tasks are currently undeployable.
- Impact: non-breaking; no existing workflow deploys `run` successfully today.

**`TaskKind.RUN` / `ImageCatalog.run()` granularity**
- From: one `RUN` kind and one `run()` image reference for all `run` subtypes.
- To: `RUN_SHELL` / `RUN_SCRIPT_JS` / `RUN_SCRIPT_PYTHON` and `runShell()` / `runScriptJs()` /
  `runScriptPython()`.
- Reason: `dws.io/step-type` labels are derived from `TaskKind`, and `StackReader` answers every
  `GET` from live cluster state — a single `RUN` kind makes a step's runtime unreadable.
- Impact: breaking for the `dws.images.run` config key, which becomes three keys.

**Unsupported `run` subtypes**
- From: `run.container` and `run.workflow` compile to a `StepService` with an empty env map.
- To: `CompilationException` naming the offending subtype. Same for `run.script` with an
  unsupported `language`, and for `run.script.source` (external resource) instead of inline `code`.
- Reason: silent half-implementations fail after deployment with no pointer back at the definition.
- Impact: breaking for definitions that previously compiled but could never run.

Additions to the compiled env, all sourced from the DSL: `ARGUMENTS` (a JSON **object** — DSL
1.0.0 models `arguments` as a key/value map, not a list), `ENVIRONMENT`, and `RETURN` (`stdout` |
`stderr` | `code` | `all` | `none`, default `stdout`).

**Out of scope**: `run.container` and `run.workflow` (separate design efforts — `container` needs a
Kubernetes-API security boundary, `workflow` belongs in `dws-orchestrator` as a child-workflow
activity); `run.script.source`; `await: false` (orchestrator-side); any change to `call: http` /
`call: openapi` behavior; any change to `dws-orchestrator`.

## Capabilities

### New Capabilities
- `run-step-execution`: the `dws-run` component's runtime obligations — the shared step-service
  HTTP contract (`POST /run`, `GET /healthz`, `502` for failures), subprocess spawning with the
  workflow data on stdin, `RETURN`/`OUTPUT` composition, and exit-code semantics that depend on
  what the author asked to observe.
- `run-step-configuration`: the env-var contract between compiler and image — `COMMAND`, `SCRIPT`,
  `ARGUMENTS`, `ENVIRONMENT`, `RETURN`, `OUTPUT`, `TIMEOUT` — including how a DSL `arguments` map
  renders per runtime (shell `--key value`, script in-scope variables).
- `run-task-compilation`: `dws-controller`'s obligation to map each `run` subtype to the right
  image and `TaskKind`, and to reject the unsupported subtypes at compile time rather than at
  deploy time.

### Modified Capabilities
<!-- None. No spec under openspec/specs/ (admin-*) changes its requirements. -->

## Impact

- **New component**: `dws-run/` — Go 1.26, `internal/config`, `internal/runner`, `internal/server`,
  three Dockerfile targets, `Makefile`, `k8s/knative-service.yaml` (one example per image),
  `README.md`.
- **New CI**: `.github/workflows/dws-run.yml`, path-filtered on `dws-run/**`; `go vet` + `go test`
  gate on every push/PR; build+push all three images only on merge to `main`.
- **Modified code** (`dws-controller`): `TaskKind`, `ImageCatalog`, `DwsConfig.Images`,
  `WorkflowCompiler.runStep()`, `application.yaml` (`dws.images.run` → three keys), and
  `WorkflowCompilerTest`.
- **Unchanged**: `dws-orchestrator` — `run` tasks reach `dws-run-*` through the same
  `CallServiceActivity` / Dapr service-invocation path as `call` tasks, since routing is derived
  from the kebab-cased task name, not the task kind. An empty orchestrator diff is an acceptance
  criterion.
- **Dependencies**: none added to `dws-controller`; `dws-run` uses the Go standard library only,
  matching `dws-call-http`.
- **Deployment**: operators overriding `dws.images.run` must migrate to `dws.images.run-shell`,
  `run-script-js`, and `run-script-python`.
