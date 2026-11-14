## Why

DWS ignores the DSL's `schedule` block today: a workflow runs only when something calls its
orchestrator's `POST /workflows/{name}/instances` (roadmap: "controller deploys on `POST` only").
The Phase 6 prompt put scheduling inside `dws-controller`, but the controller is a compiler and
apply pass. It should not also fire workflows on a clock, scale with trigger load, or fail with it.
`docs/draft-scheduler-event-trigger-split.md` proposes moving triggers into their own components.
This change is **Phase 6a** of that split: time triggers (`every`, `cron`, `after`) in a new
`dws-schedule-time` component, plus the start contract that Phase 6b (`dws-schedule-event`,
`schedule.on`) will reuse.

## What Changes

- **New component `dws-schedule-time`** (.NET 10, Dapr .NET SDK, same stack as `dws-flow`). It watches
  the cluster for deployed workflow versions that have a time trigger and registers one Dapr Jobs
  API job per workflow. When a job fires, it starts a new instance of the workflow's current
  version through the start contract.
  - `schedule.every`: fires at a fixed interval and may overlap a still-running instance.
  - `schedule.cron`: fires on a 5-field cron match, evaluated in UTC.
  - `schedule.after`: starts the next instance a fixed delay after the previous one completes. The
    scheduler finds completion by polling instance status, not by lifecycle events.
  - It rebuilds its jobs from the cluster after a restart, moves a workflow's trigger to a newly
    deployed version (takeover), and removes the job when the workflow is deleted.
- **Start contract** (runtime-neutral, shared with 6b). A trigger starts an instance by naming the
  workflow, its input, and a **deterministic instance ID**. A repeated delivery of the same fire
  starts at most one instance.
  - First task is a **spike**: can a .NET app start a workflow hosted by another app-id with Dapr
    1.18 (`DaprWorkflowClient` cross-app)? If not, the scheduler uses the fallback: the
    orchestrator's existing `POST /workflows/{name}/instances` via Dapr service invocation.
- **`dws-orchestrator`**: the start endpoint accepts a caller-supplied instance ID. Starting an ID
  that already exists returns the existing instance instead of a second one. Calls without an ID
  behave exactly as today.
- **`dws-controller`**: validates `schedule` at submit and rejects what 6a cannot run (bad
  durations or cron, runtime-expression durations, `read` without `on`). It stamps a normalized
  trigger config next to the definition so the scheduler never parses the DSL. `schedule.on` is
  rejected with a "not yet supported" error until 6b ships.
- **`charts/dws`**: renders `dws-schedule-time` in the Core layer (ADR 0007 "triggers are Core") with a
  Deployment, ServiceAccount, Role/RoleBinding (read Deployments and ConfigMaps), Dapr annotations
  and, with `auth.enabled=true`, the `DAPR_API_TOKEN` `secretKeyRef`.
- **Packaging**: CI workflow, release-please component, image build and the chart image key for
  `dws-schedule-time`.
- An ADR records the trigger-component split, Jobs API over cron binding, the start contract and
  the timing rules.

No breaking changes. Definitions without `schedule` compile, deploy and run as before.

## Capabilities

### New Capabilities
- `workflow-schedule-compilation`: `dws-controller` validates the `schedule` block at submit and
  stamps a normalized, version-scoped trigger config that the scheduler reads.
- `workflow-start-contract`: the runtime-neutral way a trigger component starts an instance
  (workflow, input, deterministic instance ID, duplicate-start safety). Owned by `dws-schedule-time`
  (caller) and `dws-orchestrator` (v1 target).
- `time-trigger-scheduling`: `dws-schedule-time` behaviour for `every`, `cron` and `after`: job
  registration from cluster state, firing, takeover, cleanup, restart rebuild, failure handling.
- `helm-schedule-time-deployment`: the chart's rendering of `dws-schedule-time` in the Core layer.

### Modified Capabilities
- None. The orchestrator's start endpoint has no spec under `openspec/specs/`; its new optional
  instance-ID behaviour is specified in `workflow-start-contract`.

## Impact

- **New package** `dws-schedule-time/` (.NET 10; its own `CLAUDE.md`, test project and gate). Built
  independently like every other package; there is still no root build.
- **`dws-controller`**: `V1OrchestratorCompiler` semantic validation, plan model and
  `StackSynthesizer`/`StackApplier` (one extra labeled ConfigMap per scheduled version, deleted with
  the stack). No change to the content-addressed version ID.
- **`dws-orchestrator`**: `WorkflowController.start` gains an optional instance ID. The existing
  API stays compatible.
- **`charts/dws`**: new `templates/schedule-time/`, values, schema and a render test. Changes the
  `auth.enabled` token wiring (see the machine-auth token env contract in the root `CLAUDE.md`).
- **CI/release**: `.github/workflows/dws-schedule-time.yml`, `release-please-config.json`,
  `.release-please-manifest.json`.
- **Dapr**: first use of the Jobs API (Dapr Scheduler control-plane service, on by default in Dapr
  1.18). Uses the existing Core state store (ADR 0007) for `after` bookkeeping.
- **Docs**: `docs/roadmaps/openworkflow-features.md` Phase 6 row, root `CLAUDE.md` package map and
  cross-package contracts, new ADR in `docs/adr/`.

### Non-goals

- `schedule.on`, `read`, `until` and `correlate`, event matching and `dws-schedule-event` (Phase 6b).
- Starting v2 (`dws-flow`) workflows. The contract stays runtime-neutral, but 6a only targets the v1
  orchestrator.
- Replaying fires missed while `dws-schedule-time` or the target was down, beyond Dapr's own job
  delivery retry.
- Per-workflow timezones and runtime-expression durations.
- The `listen` task. It stays in the workflow runtime (ADR 0006).

### Compatibility

- Existing definitions without `schedule` are unaffected: no trigger ConfigMap, no job, same
  version ID.
- Definitions that already carry a `schedule` block are accepted today and silently never fire.
  After this change, a valid time trigger starts firing on the next deploy. An invalid one, or one
  using `on`, is rejected at submit. Redeploying such a definition may therefore newly fail
  validation. This is intended, and the release notes call it out.
- Orchestrator start calls without an instance ID keep today's behaviour and response shape.
