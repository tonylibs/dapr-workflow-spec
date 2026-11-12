## 1. Spike: cross-app start and Jobs API (blocks groups 4–5)

- [x] 1.1 On a local cluster with Dapr 1.18.7, run a throwaway .NET app (`Dapr.Workflow` 1.18.10). Using `DaprWorkflowClient`, try to schedule a workflow hosted by a v1 orchestrator app-id. Record whether it works, how the target app-id is addressed, and whether `WorkflowAccessPolicy` is involved.
- [x] 1.2 In the same spike, check duplicate-ID semantics. Call `scheduleNewWorkflow` with an existing ID while the instance is running and again after it has completed. Record whether each call errors or replaces the instance, for both the Java SDK (orchestrator) and the .NET SDK.
- [x] 1.3 Check the Jobs API with `Dapr.Jobs`:
  - the callback route and payload, and whether it carries the scheduled due time;
  - the `@every` and 6-field cron formats;
  - `GetJob`/overwrite behaviour;
  - delivery to exactly one of 2 replicas;
  - the failure-policy retry.
- [x] 1.4 Write the findings into `docs/adr/0013-time-triggers-in-dws-schedule-time.md` as an `Amended:` line: either the start mechanism (a) is confirmed, or fallback (b) is kept. Adjust the design's fire-key derivation if the due time is available.

## 2. dws-orchestrator: idempotent start with caller-supplied instance ID

- [ ] 2.1 Write failing tests in `WorkflowController` tests:
  - no `instanceId` → `202` with a generated ID (unchanged);
  - new `instanceId` → `202` with that ID;
  - existing `instanceId` → `200` with the existing instance, and no second start;
  - invalid `instanceId` (empty, more than 256 chars, `/`, space) → `400`;
  - a concurrent already-exists error from Dapr → `200`;
  - an unknown `instanceId` is not mistaken for an existing one (the Java SDK returns a non-null state with empty name and epoch `createdAt` for it; spike 1.2);
  - a completed, terminated or failed instance with that ID is reported as existing, not restarted (Dapr would replace it).
- [ ] 2.2 Implement the optional `instanceId` query parameter in `WorkflowController.start`: validate it, check existence with `getWorkflowState(id, false)` (not-found is empty name / epoch `createdAt`, never `null`), call `scheduleNewWorkflow(name, input, instanceId)`, and map already-exists to `200`.
- [ ] 2.3 Update `dws-orchestrator/README.md` to document the start API.
- [ ] 2.4 Gate: `cd dws-orchestrator && ./mvnw verify` (`mvnw.cmd` on Windows).

## 3. dws-controller: schedule validation and trigger ConfigMap

- [x] 3.1 Add the `com.cronutils:cron-utils` dependency (Unix 5-field definition) and verify the license is compatible.
- [x] 3.2 Write failing compiler tests for every `workflow-schedule-compilation` validation scenario:
  - valid cron accepted; duration object accepted;
  - malformed, 4-field and 6-field cron rejected;
  - duration below 1 s rejected;
  - runtime expression rejected;
  - more than one trigger rejected;
  - `on` rejected; `read` without `on` rejected; `{}` rejected.
- [x] 3.3 Implement the `schedule` checks in `V1OrchestratorCompiler.semanticErrors`. Add duration normalization to canonical ISO-8601 (object → string).
- [x] 3.4 Write a failing test, then make `V2StructuralCompiler` reject any `schedule` with the "not yet supported on runtime v2" error.
- [x] 3.5 Add a `TriggerSpec` (workflow, version, appId, kind, schedule) to `DeploymentPlan` (nullable when there is no schedule) and `Names.triggerResource(w, versionId)`.
- [x] 3.6 Write failing `StackSynthesizer` tests:
  - a scheduled plan renders one ConfigMap with the standard labels plus `dws.io/trigger=time` and the exact `trigger.json`;
  - an unscheduled plan renders none.
- [x] 3.7 Implement the trigger ConfigMap synthesis and apply. Verify that `StackApplier` teardown deletes it with the stack, and add a test for that.
- [x] 3.8 Add a test asserting the version ID for an unscheduled definition is unchanged. Add a test pinning `Labels.DRAIN` = `dws.io/drain` and `dws.io/trigger`, because the scheduler depends on both. *Done in `SchedulerContractTest`, which also pins that drain is an annotation, not a label.*
- [x] 3.9 Export a sample `trigger.json` per kind as a shared fixture for the scheduler's parser test (`dws-schedule-time/test/fixtures/`).
- [x] 3.10 Update `dws-controller/k8s/controller-rbac.yaml` and the chart controller Role only if ConfigMap create/delete is not already granted (check first). *Checked: both already grant configmaps `create`/`delete`/`deletecollection`; the trigger ConfigMap is immutable and create-only, so no change.*
- [x] 3.11 Gate: `cd dws-controller && ./mvnw spotless:check && ./mvnw verify`.

## 4. dws-schedule-time: package scaffold

- [ ] 4.1 Create `dws-schedule-time/` in the `dws-flow` layout:
  - `dws-schedule-time.csproj` (net10.0, Nullable enable) that reads `version.txt`;
  - `test/dws-schedule-time.Tests.csproj` (xUnit, FluentAssertions);
  - `.slnx`, `Dockerfile` (non-root, `/healthz`), `README.md`, `CHANGELOG.md`.
- [ ] 4.2 Write `dws-schedule-time/CLAUDE.md`: commands (always name the test project), the style rules inherited from `dws-flow`, the cross-package contracts it consumes, and "Environment. only in Program.cs".
- [ ] 4.3 Port `DwsJsonConsoleFormatter` from `dws-flow` and its 12 golden-case tests (`docs/log-format.md`). Wire `DWS_LOG_FORMAT` in `Program.cs`.
- [ ] 4.4 Gate: `cd dws-schedule-time && dotnet test test/dws-schedule-time.Tests.csproj`.

## 5. dws-schedule-time: trigger model, active version and job reconciliation

- [ ] 5.1 Write failing tests, then implement `TriggerConfig` parsing of `trigger.json` against the controller fixtures (task 3.9). Reject unknown kinds and invalid durations or cron as configuration errors.
- [ ] 5.2 Write failing tests, then implement `ActiveTriggers`: from a set of trigger ConfigMaps and orchestrator Deployments, pick each workflow's trigger from its non-`dws.io/drain` version. Cover the takeover, schedule-dropped and deleted cases.
- [ ] 5.3 Write failing tests, then implement `JobSpecs`:
  - `every` ISO → `@every` Go-duration;
  - cron 5-field → `0 <fields>`;
  - job name `dws-sched-<workflow>`;
  - job data including the anchor;
  - failure policy: constant, 3 retries, 10 s.
- [ ] 5.4 Define an `IJobRegistry` seam (get, schedule-with-overwrite, delete) with a `DaprJobRegistry` implementation. Write failing tests, then implement the `Reconciler`, which overwrites only when version, kind or schedule differ and deletes jobs for workflows that are gone. Use a recording registry stand-in.
- [ ] 5.5 Implement the Kubernetes watch (`KubernetesClient` informers on labeled ConfigMaps and managed Deployments in the pod namespace). Run an initial list, then reconcile on every change.

## 6. dws-schedule-time: firing and the start contract

- [ ] 6.1 Write failing tests, then implement `InstanceIds`:
  - `dws-sched:<w>:<yyyyMMddTHHmmssZ>` (cron: truncate to the minute; every: anchor tick);
  - `dws-after:<w>:<seq>`.
  Use the due time from the job event instead if the spike (1.3) found it.
- [ ] 6.2 Define `IWorkflowStarter` (`StartAsync`, `GetStatusAsync`). Write failing tests, then implement `OrchestratorInvokeStarter` (Dapr service invocation; `202`/`200` mean success; 5xx/429/transport mean retryable; other 4xx are non-retryable). Implement `CrossAppWorkflowStarter` only if task 1.4 confirmed option (a).
- [ ] 6.2a Write failing tests, then implement `StartClaim` (design Decision 6) over the state store, with ETag first-write and a TTL (`DWS_SCHEDULE_TIME_CLAIM_TTL`, default 24 h):
  - won claim → start → `started`;
  - lost to `started` → success without starting;
  - lost to a fresh `claiming` → retryable failure;
  - lost to a `claiming` older than 30 s → take over and start;
  - two concurrent callers with the same instance ID start exactly once.
- [ ] 6.3 Write failing tests, then implement the `every`/`cron` job handler (starts go through `StartClaim`):
  - resolve the active trigger at fire time;
  - with no active trigger, delete the job and start nothing;
  - start with input `{}`;
  - return failure on retryable errors;
  - log and succeed on non-retryable errors.
- [ ] 6.4 Write failing tests, then implement the `after` lifecycle with ETag-guarded state in `DWS_SCHEDULE_TIME_STATE_STORE`:
  - first activation starts seq 1;
  - the poll job checks status;
  - a terminal status (including failed) schedules the one-shot start at now + delay;
  - the one-shot starts seq+1 on the currently active version;
  - a restart resumes from the stored seq.
- [ ] 6.5 Map the job handlers (`/job/<name>`) and `/healthz` in `Program.cs`. Read `DWS_SCHEDULE_TIME_STATE_STORE` and `DWS_SCHEDULE_TIME_AFTER_POLL_INTERVAL` there only.
- [ ] 6.6 Gate: `cd dws-schedule-time && dotnet test test/dws-schedule-time.Tests.csproj`, plus `docker build` and a `/healthz` smoke test.

## 7. charts/dws: scheduler in the Core layer

- [ ] 7.1 Add `scheduleTime.*` values (`enabled: true`, `image`, `replicas`, `afterPollInterval: PT15S`, `resources`) and matching `values.schema.json` entries.
- [ ] 7.2 Add `templates/schedule-time/` with ServiceAccount, Role (`get`/`list`/`watch` on configmaps and apps/deployments only), RoleBinding and Deployment. The Deployment gets the Dapr annotations (app-id `dws-schedule-time`, app port), `DWS_SCHEDULE_TIME_STATE_STORE` and `DWS_SCHEDULE_TIME_AFTER_POLL_INTERVAL`, and `DWS_LOG_FORMAT` through `dws.observability.logFormatEnv`.
- [ ] 7.3 Add the `<release>-schedule-time-dapr-api-token` Secret to `templates/auth/api-token-secrets.yaml`. Add the `dapr.io/api-token-secret` annotation and the `DAPR_API_TOKEN` `secretKeyRef` (key `token`) under `auth.enabled=true`. Extend `charts/dws/tests/api-tokens-render-test.sh` to cover the scheduler.
- [ ] 7.4 Add `charts/dws/tests/schedule-time-render-test.sh`: default render, disabled render, Role rules, and the env entries. Add it to `.github/workflows/helm.yml` and to the root `CLAUDE.md` chart gate list.
- [ ] 7.5 Add the scheduler image key to the release process (`charts/dws/values.yaml` pin) per `docs/release-process.md`.
- [ ] 7.6 Gate (from the repo root, Helm 4.3.0): `helm lint charts/dws`, `helm template dws charts/dws`, then every `charts/dws/tests/*.sh` listed in the root `CLAUDE.md`, plus the new scheduler test.

## 8. CI and release packaging

- [ ] 8.1 Add `.github/workflows/dws-schedule-time.yml`, modelled on `dws-flow.yml`: test, image build, `/healthz` smoke test, push on main.
- [ ] 8.2 Add `dws-schedule-time` to `release-please-config.json` and `.release-please-manifest.json`. Extend `component-release.yml` if it lists components explicitly.

## 9. Live verification

- [ ] 9.1 On a local cluster with the chart installed, deploy workflows with `every: PT1M`, `cron: "*/2 * * * *"` and `after: PT30S`. Check that instances start with the expected IDs and that `after` waits for completion plus the delay.
- [ ] 9.2 Takeover: deploy a new version with a different cron and check that the next fire starts on the new version. Delete the workflow and check that its job is gone.
- [ ] 9.3 Restart the `dws-schedule-time` pod. Check that no unchanged job was re-anchored and that the `after` sequence continued.
- [ ] 9.4 Scale `dws-schedule-time` to 2 replicas and check that there are no duplicate instances per fire key. Also stop both pods for several intervals, restart them, and check that the replayed burst starts exactly one instance.
- [ ] 9.5 Repeat 9.1 with `auth.enabled=true` and check that there are no 401s in either sidecar (draft D11).
- [ ] 9.6 Record the evidence under `docs/roadmaps/` (as `observability-phase*-evidence.md` does) and link it from the ADR.

## 10. Docs and close-out

- [ ] 10.1 Update the root `CLAUDE.md`:
  - package map row for `dws-schedule-time` and its gate command;
  - cross-package contracts (trigger ConfigMap, drain label, start `instanceId`, instance-ID format, scheduler `DAPR_API_TOKEN`);
  - a sixth package in the `DWS_LOG_FORMAT` contract.
  Update `docs/log-format.md` for the new package.
- [ ] 10.2 Update `docs/roadmaps/openworkflow-features.md`: the Phase 6 row is split into 6a/6b, and the scheduling coverage row is updated. Mark `docs/draft-scheduler-event-trigger-split.md` as superseded by ADR 0013 for 6a.
- [ ] 10.3 Add a release note and a chart `NOTES.txt` line: definitions with `schedule` start firing, or may newly fail validation, on their next deploy.
- [ ] 10.4 Run `openspec validate scheduling-phase-6a-time-triggers --type change --strict` and fix any findings before archive.
