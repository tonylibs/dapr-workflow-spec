## Why

`dws-flow` and `dws-schedule-event` must evaluate jq (loop `while`, catch conditions, `listen` and
`schedule.on` filters) but have no jq engine, and ADR 0014 (Accepted 2026-11-24) decided that every
DWS jq evaluation converges on one per-expression Go function, `dws-jq`. Roadmap row 2j builds it:
3b, 3c, 3f, 3g and 6b are blocked on it, and row 2.0d needs its EX-3 latency numbers.

## What Changes

- **New component `dws-jq`** (Go 1.26.9, gojq v0.12.20): evaluates exactly one jq expression fixed
  at deploy.
  - Configured by `JQ_EXPRESSION`, `JQ_VARIABLES`, `JQ_TIMEOUT` (default `5s`), `JQ_SECRET_STORE` and
    `JQ_SITE`. These names are a documented contract, pinned by fixtures.
  - At startup it accepts `${ … }` or bare jq. It rejects the ADR 0012 Decision 9 builtins by walking
    the AST, rejects undeclared variables and dynamic `$secrets` access, and exits without becoming
    ready on any of these.
  - It reads only the `$secrets.<name>` values its expression names, through the Dapr secrets API.
  - `POST /run` answers `200 {result, truthy}` with v1 semantics, `400` for a bad request or
    undeclared variable, and `422 {fault: {type: expression, …}}` for evaluation failures and the
    time limit. Secret values are redacted as `***`.
  - `GET /healthz` answers `503` until the function is ready, then `200`.
  - Logs follow `DWS_LOG_FORMAT`.
- **New corpus `conformance/expressions/`**: a language-neutral expression-vector corpus with case
  schema, README, runner rules and per-engine divergence lists. It is seeded with the 13 builtin
  cases, the ADR 0014 Decision 5 dialect rows, v1's `for` / `while` / catch expressions, and
  combined-template cases. A `dws-jq` runner runs it in the package tests.
- **Packaging**: Dockerfile, path-filtered CI workflow `dws-jq.yml`, a release-please package (first
  release `0.1.0`), and a `component-release` option.
- **Docs**:
  - `dws-jq/CLAUDE.md` and `dws-jq/README.md`.
  - Root `CLAUDE.md`: package map, gate table, and contract bullets.
  - `docs/log-format.md`: adds `dws-jq` as the seventh package.
  - `docs/release-process.md`.
  - ADR 0014: notes on Decision 5 confirmations, the O-5 dynamic-access choice, and the EX-3 result.
- **EX-3**: cold-start and warm-latency measurements on a local cluster if one is reachable;
  otherwise recorded as not measured.

There is no behavior change for existing workflows. Nothing calls `dws-jq` yet, and the controller
does not deploy it.

## Capabilities

### New Capabilities
- `jq-expression-function`: the `dws-jq` component — its deploy-time configuration contract,
  startup checks (parse, builtin policy, variables, `$secrets` access), the `POST /run` and
  `GET /healthz` HTTP contract, secret retrieval and redaction, and structured logging.
- `jq-expression-conformance`: the `conformance/expressions/` corpus — case format, seed coverage,
  runner rules, verdicts, and per-engine divergence lists.

### Modified Capabilities
- None. `structured-log-correlation` is not rewritten. `dws-jq`'s obligation to follow it is stated
  in `jq-expression-function`, and `docs/log-format.md` lists the package.

## Impact

- **Components**:
  - New `dws-jq/`, with its own gate: `make lint && make test`.
  - New `conformance/expressions/`.
  - No change to `dws-controller`, `dws-orchestrator`, `dws-step`, `dws-flow`, function images or
    the chart. Each keeps its independent build.
- **DSL behavior**: none today. This change fixes the dialect future Flow and trigger expressions run
  on: gojq minus the Decision 9 builtins, with dynamic `$secrets` access rejected.
- **Deployed resources**: none yet. The image and the configuration contract are what 2m.2 will
  render: one Knative Service per expression site, scale to zero.
- **Runtime interpretation**: none yet. The callers are 3b, 3c, 3f, 3g and 6b.
- **Cross-package contracts added**:
  - The `dws-jq` configuration env (pinned by `dws-jq/testdata/contract/`).
  - The `/run` request and response shape.
  - The `DWS_LOG_FORMAT` contract, which gains a Go implementation.
- **Dependencies**:
  - `github.com/itchyny/gojq` v0.12.20.
  - Test-only: `gopkg.in/yaml.v3` and `github.com/santhosh-tekuri/jsonschema/v6`.
- **CI / release**:
  - New workflow `dws-jq.yml`.
  - `release-please-config.json`, `.release-please-manifest.json` and `component-release.yml` gain
    `dws-jq`.
- **Compatibility**:
  - Existing definitions are unaffected.
  - Step and v1 expressions still run on jackson-jq until 2k and 5.3, so the documented two-engine
    differences (ADR 0014 Decision 5) remain.

## Non-goals

- Any caller change (`dws-flow`, `dws-step`, `dws-schedule-event`, v1, function images).
- Controller emission, access policy, secret scoping and `min-scale` rules (2m, Phase 4, 6b).
- A jackson-jq runner for the new corpus (EX-1).
- Deciding which expressions may use `$secrets`, and which sites count as busy (2.0d follow-ups;
  EX-3 only supplies numbers).
- Notion.
