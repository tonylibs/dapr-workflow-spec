# Tasks

Gates are the per-package commands from the root `CLAUDE.md`; run each from its own directory.
`dws-flow` needs the .NET 10 SDK and the chart's render test needs Helm 4.3.0 (the pinned
`HELM_VERSION`); install both before the final integration group.

## 1. Contract reference (docs)

- [x] 1.1 Add `docs/log-format.md` stating the contract from `design.md` D2 (six keys, `exception.*`, severity map, `DWS_LOG_FORMAT` semantics, omission rule) and the canonical golden cases (plain, in-span, out-of-span, exception, multi-line, bearer token, `password=` pair) as JSON lines. Verify the file renders and each golden line parses with `jq -e .`.

## 2. dws-controller (Quarkus) — own logs, `DWS_LOG_FORMAT`, orchestrator stamping

- [ ] 2.1 Spike: with the Quarkus 3.37.4 JSON console formatter, check whether MDC keys can be emitted as top-level keys and whether `key-overrides` can produce `timestamp`/`severity`/`logger`/`message`. Record the finding and the chosen mechanism (extension config, else a custom formatter) in a short note appended to `design.md` D7. Verify by a throwaway test that logs with MDC `trace_id`/`span_id` and prints the line.
- [ ] 2.2 Implement the chosen mechanism so `DWS_LOG_FORMAT=json` yields the contract lines for the whole process lifetime including boot, and unset/unknown yields today's output; include the scrubber (`design.md` D8). Verify with `./mvnw test -Dtest=LogFormatContractTest` covering every golden case from 1.1.
- [ ] 2.3 Read `DWS_LOG_FORMAT` as controller configuration (default `text`) and, in `StackSynthesizer.orchestratorDeployment`, stamp `DWS_LOG_FORMAT=json` on the `orchestrator` container only when `ObservabilitySettings.enabled()` and the controller's value is `json`, never overwriting an existing value. Verify in `StackSynthesizerTest`: off-settings equality still holds, instrumented + json stamps the variable, instrumented + text/absent does not, existing value kept.
- [ ] 2.4 Extend `OrchestratorEnvContractTest` so it fails when `dws-orchestrator`'s `application.yaml` does not reference `DWS_LOG_FORMAT`. Verify by temporarily renaming the key in the sibling file and seeing the test fail, then restoring it.
- [ ] 2.5 Audit the controller's own log call sites (`StackSynthesizer`, `StackApplier`, `ObservabilityFlags`, request handling) for secret-bearing arguments; add a test that a logged bearer token never appears. Verify by `./mvnw verify` (Spotless runs on `process-sources`).

## 3. dws-orchestrator (Spring Boot)

- [x] 3.1 Add the custom structured-log formatter class and wire `logging.structured.format.console` from `DWS_LOG_FORMAT` in `application.yaml` (confirm a blank value leaves plain logging; otherwise add the `EnvironmentPostProcessor` fallback from `design.md` D7). Keep the existing `logging.level.io.dws.orchestrator: INFO`. Verify with a `LogFormatContractTest` running every golden case, including MDC `trace_id`/`span_id` present and absent.
- [x] 3.2 Audit the call sites in `WorkflowDefinitionLoader`, `WorkflowRuntimeBootstrap`, `CallServiceActivity`, `EmitEventActivity`, `AdminEventActivity` for secrets and exception messages that can embed URLs or headers; add a test that a run with `use.secrets` configured logs no resolved `SECRET_*` value. Verify by that test passing.
- [x] 3.3 Record in `dws-orchestrator/CLAUDE.md` the rule that no log call goes inside workflow-function code (ADR 0009 decisions 1–2) and the `DWS_LOG_FORMAT` contract. Verify the text is present and names the ADR.
- [x] 3.4 Run the package gate: `cd dws-orchestrator && ./mvnw verify` passes.

## 4. dws-step (Spring Boot)

- [x] 4.1 Add the same formatter and `application.yaml` mapping as 3.1 in `dws-step` (own copy; no shared artifact) and a `LogFormatContractTest` over the golden cases. Verify the test passes.
- [x] 4.2 Audit `StepRuntimeBootstrap` log calls for secrets and add the bearer-token/`password=` test. Verify by the test passing.
- [x] 4.3 Run the package gate: `cd dws-step && ./mvnw spotless:check && ./mvnw verify` passes.

## 5. dws-admin (NestJS)

- [ ] 5.1 Add `@opentelemetry/api` and a JSON `LoggerService` that reads `trace.getActiveSpan()`, installed with `bufferLogs`/`app.useLogger` only when `DWS_LOG_FORMAT=json`, with the scrubber and the severity mapping from `design.md` D2. Verify with a Jest spec that runs every golden case and asserts key names, omission when no active span, and one line per record.
- [ ] 5.2 Confirm in the built image (or a local run with the Node auto-instrumentation `--require`) that the app's `@opentelemetry/api` resolves the same global as the agent, so an HTTP request's log lines carry the request span's IDs. Verify by an integration-style Jest test using an in-memory tracer provider, and record the image-level check for the live run in group 9.
- [ ] 5.3 Audit `dws-events.subscriber.ts`, `orchestrator-events.handler.ts` and `controller-events.handler.ts` messages (they interpolate `err.message` and event types) for secrets; add the bearer-token test. Verify by the spec passing.
- [ ] 5.4 Run the package gate: `cd dws-admin && pnpm lint && pnpm test && pnpm build` passes.

## 6. dws-flow (.NET)

- [x] 6.1 Spike: determine whether `Activity.Current` is set inside a Dapr Workflow activity under `Dapr.Workflow` 1.18.10 and record the answer in `design.md` D7. Verify with a small test host or recorded reasoning from the SDK source, written into the note.
- [x] 6.2 Add a named `ConsoleFormatter` implementing the contract (TraceId/SpanId from `Activity.Current`, omitted when default; severity map; exception keys; scrubber) and register it in `Program.cs` only when `DWS_LOG_FORMAT=json`. Verify in `test/` with a golden-case test over every case in 1.1.
- [x] 6.3 Document in `dws-flow/CLAUDE.md` the `DWS_LOG_FORMAT` contract and the 6.1 finding. Verify the text is present.
- [x] 6.4 Run the package gate: `cd dws-flow && dotnet test test/dws-flow.Tests.csproj` passes (name the test project; a bare `dotnet test` runs nothing).

## 7. charts/dws

- [x] 7.1 Replace the "reserved" comment on `observability.logs.format` in `values.yaml` with the real semantics (json|text, default json, effective only with `observability.enabled` and `observability.logs.enabled`), and make `dws.observability.validate` fail on any other value naming `json` and `text`. Verify with `helm template dws charts/dws --set observability.enabled=true --set observability.logs.format=yaml` failing with that message.
- [x] 7.2 Add a helper that yields the `DWS_LOG_FORMAT` env entry when `observability.enabled` and `observability.logs.enabled` are true and use it in the controller and admin Deployments. Verify the default render is byte-identical (the render test's baseline leg) and that `observability.enabled=true` renders the variable on both containers.
- [x] 7.3 Set `OTEL_LOGS_EXPORTER` to `none` unconditionally in `templates/observability/instrumentation.yaml` and update the comment. Update the render test's signal-toggle assertion (it currently expects two `otlp` entries) to the new truth. Verify `bash charts/dws/tests/observability-render-test.sh charts/dws` passes under Helm 4.3.0.
- [x] 7.4 Extend `observability-render-test.sh` with the format matrix: default json, `text`, invalid value fails, `logs.enabled=false` renders no variable, controller and admin both carry it, default render unchanged. Verify each assertion fails when its template line is reverted.
- [x] 7.5 Rework `tests/fixtures/otel-collector-dev.yaml`: scope `filelog` to the release namespace, exclude the `daprd` and collector containers, add the `container` → conditional `json_parser` → `trace_parser`/`span_parser` → body/`logger.name` operators, add a Loki Deployment/Service and an `otlphttp` exporter on the logs pipeline beside `debug`; update the header comment and `tests/fixtures/README.md`. Verify `kubectl apply --dry-run=client -f` accepts it and, if a collector binary is available, `otelcol-contrib validate --config` on the extracted `config.yaml`.
- [x] 7.6 Add a render-test leg that reads the fixture and asserts the receiver keeps the exclusions and the trace-context operators, so they cannot be dropped silently. Verify by deleting an operator locally and seeing the leg fail.
- [x] 7.7 Document in `charts/dws/README.md` the format value, the single log path (stdout → `filelog`, agent log export off), the reference `filelog` block, the next-deploy-only semantics for compiled workflows, the unsampled-trace caveat and the `text` escape. Verify the README block matches the fixture's receiver.
- [x] 7.8 Run the full chart gate from the repository root under Helm 4.3.0: `helm lint charts/dws`, `helm template dws charts/dws`, and the five scripts in the root `CLAUDE.md` (`values-schema`, `api-gateway-render`, `auth-pipeline-placement`, `observability-render`, `orchestrator-wiring-render`) all pass.

## 8. Roadmap and project docs

- [x] 8.1 Update the Phase 4 row of `docs/roadmaps/observability.md` (status, what shipped, Track B remainder) and the 2a′ row to require `DWS_LOG_FORMAT` stamping in runtime-v2 Phase 4 synthesis; add a Findings-log entry for the decisions that changed (agent log export off, collector fixture as the only collector config). Verify by reading the rendered rows.
- [x] 8.2 Add the `DWS_LOG_FORMAT` and six-field contract to the root `CLAUDE.md` "Cross-package contract-change etiquette" section. Verify the entry names every package that reads it.

## 9. Integration and live evidence

- [ ] 9.1 On Tony's local cluster, follow `observability-phase2a-evidence.md` "Reproducing the probe" with: the reworked collector fixture, `observability.enabled=true`, `observability.traces.samplingRate=1`, default `observability.logs.format`, one deployed workflow with a call step, one started instance. Verify the `dws-controller`, `dws-admin` and orchestrator pods run with `DWS_LOG_FORMAT=json` and their `kubectl logs` lines parse with `jq -e .`.
- [ ] 9.2 Take the instance's trace ID from Jaeger and query it in Loki (LogQL on the structured-metadata `trace_id`). Verify lines from at least the orchestrator activity spans are returned with the matching `span_id`, no line is duplicated, and no line carries a bearer token or `SECRET_*` value (`grep` over the returned lines).
- [ ] 9.3 Record the run in `docs/roadmaps/observability-phase4-evidence.md`: trace ID, the log query and its output, the pod env, deviations from the procedure, and the limits (no cluster evidence for `dws-flow`/`dws-step`; unsampled-trace behaviour). Verify the file exists and the trace ID in it matches the Jaeger and Loki captures.
- [ ] 9.4 Final pass: every touched package's gate from groups 2–6 and the chart gate from 7.8 pass on the final commit. Verify by running them once more from a clean checkout of the branch.

## Workflow follow-up

- Archive the change after review (`openspec archive`), then reconcile `helm-log-format` with `helm-observability` once `observability-template` has been archived.
