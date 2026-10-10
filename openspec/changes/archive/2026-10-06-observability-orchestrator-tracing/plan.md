# Observability Phase 2a: Trace the Compiled Orchestrator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** With observability on, the orchestrator Deployment `dws-controller` produces is Java-injected for the app container only, points its Dapr sidecar at a tracing-only Configuration, and carries workflow identity; with it off, the Deployment is identical to today's.

**Architecture:** A pure `ObservabilitySettings` value (default `OFF`) is passed into `StackSynthesizer.orchestratorDeployment`; with `OFF` the code path is unchanged. A small CDI reader (`ObservabilityFlags`) resolves the settings once per deploy from the `dws-controller-config` store plus a GET of Dapr Configuration `dws-tracing`, falling back to `OFF` on any problem. The chart renders `dws-tracing` (tracing only) by reusing the existing `dws.observability.daprTracing` helper.

**Tech Stack:** Java 25, Quarkus 3.37, Fabric8 Kubernetes client, Dapr Java SDK `DaprClient`, JUnit 5, AssertJ, Mockito; Helm 3.19.0 and bash render tests.

**Spec:** `openspec/changes/observability-orchestrator-tracing/` (`design.md`, `specs/workflow-orchestrator-tracing/spec.md`, `specs/helm-workflow-tracing-configuration/spec.md`, `tasks.md`).

## Global Constraints

- Environment: `source /home/projects/.tools/env.sh` (JDK 25 at `/home/projects/.jdk/jdk-25.0.4.1+1`, Helm `v3.19.0`). Run commands from the package directory; there is no root build. Windows irrelevant.
- Store: `dws-controller-config`; keys exactly `observability.enabled` and `observability.instrumentation`; on only when `enabled` trimmed equals `true` case-insensitively.
- Configuration name exactly `dws-tracing`; Dapr `samplingRate` literal string `"1"`; orchestrator container name exactly `orchestrator`.
- Annotations: `instrumentation.opentelemetry.io/inject-java`, `instrumentation.opentelemetry.io/container-names: orchestrator`, `dapr.io/config: dws-tracing` (put-if-absent). `inject-java` default value `"true"`.
- Env on the `orchestrator` container: `OTEL_SERVICE_NAME` = Dapr app ID, `OTEL_RESOURCE_ATTRIBUTES` = `dws.workflow.name=<workflow>,dws.workflow.version=<versionId>` (percent-encode `,` and `=`; append to existing value). No `dws.node.*`, no sampling setting.
- Store read bounded to 1 second; never throws; never fails a deploy.
- Off-path output must equal the pre-change output. Never edit anything under `openspec/changes/archive/`. Never edit `docs/roadmaps/*` (except creating the new `observability-phase2a-evidence.md`), the tracker, or Notion. Do not hand-edit `openwiki/`.
- `dws-orchestrator` is NOT modified. Do not add controller env vars. Do not write a Java copy of the endpoint/protocol translation.
- Conventional commits `<type>(<scope>): <description>`; scopes `controller`, `chart`, `docs`, `openspec`. Controller gate: `cd dws-controller && ./mvnw verify` (needs `npx`/Node on PATH). Chart gate is in the root `CLAUDE.md`.
- Controller owner: `quarkus-developer` (paths `dws-controller/`). Chart owner: `platform-deployment-developer` (paths `charts/`, `.github/workflows/helm.yml` only if needed). The two units touch disjoint paths and can run in parallel; the contract between them is the Global Constraints above.

## Review Focus

- Store returns `observability.enabled=" TRUE "` (whitespace, case): must be on. Tested in Task 1.
- Store returns the key but a `null`/empty `ConfigurationItem` value, or the call returns a `Map` missing one key: off for `enabled`, default `"true"` for a missing/blank `instrumentation`. Tested in Task 1.
- `DaprClient` is `null` (no sidecar) or the `Mono` never completes: off within the 1 s bound, no exception. Tested in Task 1.
- Orchestrator container already has `OTEL_RESOURCE_ATTRIBUTES` (or `OTEL_SERVICE_NAME`): resource attributes appended; existing `OTEL_SERVICE_NAME` is left untouched. Tested in Task 2.
- Workflow name or version containing `,` or `=`: percent-encoded, never splits the attribute list. Tested in Task 2.
- Instrumentation reference is blank or whitespace: falls back to `"true"`. Tested in Task 1.
- Chart: `auth.enabled=true` with `observability.enabled=true`: controller/admin Configurations and annotations unchanged, `dws-tracing` has no pipeline. Tested in Task 4.

---

## Task 1: Controller settings reader (`quarkus-developer`)

**Files:**
- Create: `dws-controller/src/main/java/io/dws/controller/k8s/ObservabilitySettings.java`
- Create: `dws-controller/src/main/java/io/dws/controller/k8s/ObservabilityFlags.java`
- Test: `dws-controller/src/test/java/io/dws/controller/k8s/ObservabilityFlagsTest.java`

**Interfaces:**
- Produces: `public record ObservabilitySettings(boolean enabled, String instrumentation)` with `public static final ObservabilitySettings OFF = new ObservabilitySettings(false, "true")` and constants `STORE = "dws-controller-config"`, `ENABLED_KEY = "observability.enabled"`, `INSTRUMENTATION_KEY = "observability.instrumentation"`, `TRACING_CONFIGURATION = "dws-tracing"`, `ORCHESTRATOR_CONTAINER = "orchestrator"`.
- Produces: `@ApplicationScoped public class ObservabilityFlags` with constructor `ObservabilityFlags(DaprClient daprClient, KubernetesClient client)` and `public ObservabilitySettings resolve(String namespace)` that never throws.
- Consumes: `io.dapr.client.DaprClient`, `io.dapr.client.domain.ConfigurationItem`, `io.fabric8.kubernetes.client.KubernetesClient`, `ResourceContexts.DAPR_CONFIGURATION` (existing, `k8s/ResourceContexts.java`).

- [ ] **Step 1: Write the failing tests.** In `ObservabilityFlagsTest` (JUnit 5 + AssertJ + Mockito, same style as `CompilerStrategyTest`): helper `stubStore(Map<String,String>)` makes `daprClient.getConfiguration(STORE, ENABLED_KEY, INSTRUMENTATION_KEY)` return `Mono.just(Map.of(...ConfigurationItem...))`; a `KubernetesClient` mock (or Fabric8 `KubernetesMockServer`/`@EnableKubernetesMockClient` in CRUD mode) controls whether `dws-tracing` exists. Cases, each with `assertThat(flags.resolve("dws")).isEqualTo(...)`:
  - enabled `true` + Configuration present -> `new ObservabilitySettings(true, "true")`.
  - enabled `" TRUE "` -> on (Review Focus 1).
  - enabled `false`, `yes`, `1`, absent key, null value -> `OFF`.
  - instrumentation `dws-system/dws-instrumentation` -> carried through; blank/whitespace/absent -> `"true"`.
  - store `Mono.error(new RuntimeException())`, `Mono.empty()`, `Mono.never()` (assert it returns in under ~3 s), and a `null` `DaprClient` -> `OFF`, no exception.
  - enabled `true` but Configuration absent, or the Kubernetes lookup throws (including 403 `KubernetesClientException`) -> `OFF` and no exception.
- [ ] **Step 2: Run to verify failure.** `cd dws-controller && ./mvnw -q -Dtest=ObservabilityFlagsTest test` -> FAIL (classes missing).
- [ ] **Step 3: Implement minimally.** Verify against the Dapr SDK 1.18 jar which `getConfiguration` overload returns `Mono<Map<String, ConfigurationItem>>` for several keys (the `(String, String...)` form with two keys; a single key resolves to the `Mono<ConfigurationItem>` overload used by `CompilerProducer`). Call it with `.block(Duration.ofSeconds(1))`, wrapped in `try/catch (Exception)`; parse `enabled` as `value != null && "true".equalsIgnoreCase(value.trim())`; `instrumentation` blank -> `"true"`. Only when enabled, look up `client.genericKubernetesResources(ResourceContexts.DAPR_CONFIGURATION).inNamespace(namespace).withName(TRACING_CONFIGURATION).get()`; a null result or any exception -> `LOG.warnf(...)` once and return `OFF`. Debug-level logging when the store is unreadable (matches `CompilerProducer`).
- [ ] **Step 4: Run to verify pass.** Same command -> PASS.
- [ ] **Step 5: Commit.** `git add dws-controller/src && git commit -m "feat(controller): read observability flags from the config store"`

## Task 2: Orchestrator stamping in the synthesizer (`quarkus-developer`)

**Files:**
- Modify: `dws-controller/src/main/java/io/dws/controller/k8s/StackSynthesizer.java` (`orchestratorDeployment`, `orchestratorAnnotations`, env assembly near lines 477-525)
- Test: `dws-controller/src/test/java/io/dws/controller/k8s/StackSynthesizerTest.java` (orchestrator section, lines ~139-226)

**Interfaces:**
- Consumes: `ObservabilitySettings` from Task 1.
- Produces: `public Deployment orchestratorDeployment(DeploymentPlan plan, String namespace, ObservabilitySettings settings)`; the existing two-argument method delegates with `ObservabilitySettings.OFF` and its output stays identical.

- [ ] **Step 1: Write the failing tests** in `StackSynthesizerTest`, using the existing helpers that build a `DeploymentPlan` with an `OrchestratorSpec`:
  - `offSettingsEqualTodaysDeployment`: `assertThat(s.orchestratorDeployment(plan, NS, ObservabilitySettings.OFF)).isEqualTo(s.orchestratorDeployment(plan, NS))`, and also assert the exact current annotation keys `dapr.io/enabled`, `dapr.io/app-id`, `dapr.io/app-port` and no `OTEL_*` env.
  - `enabledStampsTargetedInjection`: annotations contain `instrumentation.opentelemetry.io/inject-java=true`, `instrumentation.opentelemetry.io/container-names=orchestrator`, `dapr.io/config=dws-tracing` and the three existing ones; only the container named `orchestrator` exists in the pod.
  - `enabledCarriesCustomInstrumentationReference`: settings `(true, "dws-system/dws-instrumentation")` -> `inject-java` equals it.
  - `enabledAssertsIdentityValues`: plan workflow `order-fulfilment`, versionId `v1a2b3c4d`, appId `order-fulfilment` -> env `OTEL_SERVICE_NAME=order-fulfilment` and `OTEL_RESOURCE_ATTRIBUTES` equals `dws.workflow.name=order-fulfilment,dws.workflow.version=v1a2b3c4d`; no env/annotation names containing `dws.node`, `SAMPLER`, or `sampling`.
  - `identityValuesArePercentEncoded`: workflow `a,b=c` -> `dws.workflow.name=a%2Cb%3Dc`.
  - `appendsToExistingResourceAttributes`: spec env already has literal `OTEL_RESOURCE_ATTRIBUTES=team=x` -> `team=x,dws.workflow.name=...`; and an existing `OTEL_SERVICE_NAME` is not overwritten and not duplicated.
  - `existingDaprConfigIsPreserved`: build a spec where the annotation map already holds `dapr.io/config=other` (exercise via the package-private annotation helper) -> value stays `other`, no `dws-tracing`.
  - `otherGeneratedResourcesUnaffected`: nothing else on the synthesizer takes settings (compile-time guarantee; assert `knativeServices`/`definitionConfigMap` outputs are unchanged by construction in the same test file).
  - Keep `noSecretWorkflowKeepsLiteralOrchestratorEnvironment` untouched and passing.
- [ ] **Step 2: Run to verify failure.** `./mvnw -q -Dtest=StackSynthesizerTest test` -> FAIL (overload missing).
- [ ] **Step 3: Implement minimally.** Add the three-arg method; build annotations via a helper `orchestratorAnnotations(OrchestratorSpec, ObservabilitySettings)` that returns today's map when `!settings.enabled()` and otherwise `putIfAbsent`s the three keys. Build env as `envVars(orchestrator.env())` and, when enabled, add `OTEL_SERVICE_NAME` (skip if present) and merge `OTEL_RESOURCE_ATTRIBUTES` (append with `,` if a literal value exists; if it is a `SecretKeyRef`, leave it and skip stamping resource attributes with a debug log). Percent-encode with a tiny private method that encodes only `%`, `,` and `=` (and leave other characters). Identity: `plan.workflow()`, `plan.versionId()`, `orchestrator.appId()`.
- [ ] **Step 4: Run to verify pass.** Same command -> PASS; then `./mvnw -q -Dtest='StackSynthesizerTest,V2GoldenTest' test`.
- [ ] **Step 5: Commit.** `git commit -am "feat(controller): stamp orchestrator tracing annotations and workflow identity"`

## Task 3: Wire into the apply pass and RBAC (`quarkus-developer`)

**Files:**
- Modify: `dws-controller/src/main/java/io/dws/controller/k8s/StackApplier.java` (constructor and the `orchestratorDeployment` call, ~line 100)
- Modify: `dws-controller/k8s/controller-rbac.yaml`
- Test: `dws-controller/src/test/java/io/dws/controller/k8s/StackApplierTest.java` (near `createsOrchestratorDeployment`, ~lines 194-220)

**Interfaces:**
- Consumes: `ObservabilityFlags.resolve(String)` and `StackSynthesizer.orchestratorDeployment(plan, ns, settings)`.

- [ ] **Step 1: Write the failing tests** in `StackApplierTest` (`@QuarkusTest @WithKubernetesTestServer`): (a) with an `@InjectMock ObservabilityFlags` (or the `MockDaprClientProducer` stubbed to return `observability.enabled=true`) and a `dws-tracing` Configuration created in the mock server, applying a plan stores an orchestrator Deployment whose pod template has `dapr.io/config=dws-tracing`, `container-names=orchestrator` and env `OTEL_SERVICE_NAME`; (b) with the flag on but no `dws-tracing`, the stored Deployment equals today's (annotations are exactly the original three, no `OTEL_*` env) and `apply` still succeeds; (c) with the store throwing, same as (b); (d) the existing tests still pass unchanged.
- [ ] **Step 2: Run to verify failure.** `./mvnw -q -Dtest=StackApplierTest test` -> FAIL.
- [ ] **Step 3: Implement.** Inject `ObservabilityFlags` into `StackApplier` (update the constructor; fix any direct `new StackApplier(...)` call sites in tests). Replace the Deployment line with `synthesizer.orchestratorDeployment(plan, namespace, flags.resolve(namespace))`. Add to `controller-rbac.yaml` a rule `apiGroups: ["dapr.io"]`, `resources: ["configurations"]`, `verbs: ["get"]`, matching the file's existing style; do not add other verbs.
- [ ] **Step 4: Run to verify pass.** Same command -> PASS.
- [ ] **Step 5: Controller gate and commit.** `cd dws-controller && ./mvnw verify` -> BUILD SUCCESS. Then `git add dws-controller && git commit -m "feat(controller): instrument the compiled orchestrator when observability is on"`. Also add a short "Observability" note (keys, `dws-tracing`, next-deploy semantics, fixed container name) to `dws-controller/README.md` in the same commit, or a separate `docs(controller)` commit.

## Task 4: Chart `dws-tracing` Configuration and Role rule (`platform-deployment-developer`)

**Files:**
- Create: `charts/dws/templates/observability/workflow-tracing-configuration.yaml`
- Modify: `charts/dws/templates/controller/rbac.yaml` (the `dapr.io` rule near lines 132-135)
- Modify: `charts/dws/values.yaml` (comment on `observability.workflows.enabled`, lines ~458-462)
- Modify: `charts/dws/templates/controller/config-component.yaml` (header comment), `charts/dws/README.md` (observability section)
- Test: `charts/dws/tests/observability-render-test.sh`

**Interfaces:**
- Consumes: helper `dws.observability.daprTracing` (`_helpers.tpl`, ~lines 630-640) exactly as the controller/admin Configuration templates call it; `dws.namespace`.
- Produces: Configuration `dws-tracing` (`apiVersion: dapr.io/v1alpha1`, `kind: Configuration`) with `spec.tracing` only.

- [ ] **Step 1: Write the failing assertions** in `observability-render-test.sh` (match its existing helper functions and four-way matrix): with `observability.enabled=true` exactly one `Configuration` named `dws-tracing` renders; its spec has `tracing` and neither `appHttpPipeline` nor `httpPipeline`; `samplingRate: "1"`; `endpointAddress`, `isSecure`, `protocol` equal the controller Configuration's tracing block (assert for an `https://` endpoint with `grpc` and with `http/protobuf`); with `observability.workflows.enabled=false` none renders; default render has none and still byte-matches `fixtures/default-render-baseline.yaml`; in all four auth/observability combinations the controller and admin Configurations and `dapr.io/config` annotations are unchanged; the controller Role has a `dapr.io` rule with resource `configurations` and verb `get`.
- [ ] **Step 2: Run to verify failure.** `source /home/projects/.tools/env.sh && bash charts/dws/tests/observability-render-test.sh charts/dws` -> FAIL.
- [ ] **Step 3: Implement.** Template: gate `{{- if and .Values.observability.enabled .Values.observability.workflows.enabled }}`; metadata name `dws-tracing`, namespace `{{ include "dws.namespace" . }}`, the chart's standard labels; `spec:` followed by the single helper include, indented as the existing templates do; include the ADR 0005 comment explaining `samplingRate: "1"` and why this Configuration has no pipeline. Add the Role rule. Update the values comment (it is no longer "reserved for Phase 2b" for the orchestrator), the config-store comment (keys `observability.enabled`, `observability.instrumentation`, set out of band) and the README (enable procedure from design.md's Migration Plan, next-deploy-only semantics, `dws-tracing` description). Do not touch the Phase 1 baseline fixture.
- [ ] **Step 4: Run to verify pass.** The render test passes; also run the full chart gate from the repo root: `helm lint charts/dws`, `helm template dws charts/dws`, and the four `bash charts/dws/tests/*-test.sh charts/dws` scripts (values-schema, api-gateway-render, auth-pipeline-placement, observability-render).
- [ ] **Step 5: Commit.** `git add charts && git commit -m "feat(chart): render tracing-only dws-tracing Configuration for compiled workflows"`

## Task 5: ADR addendum and evidence record (`quarkus-developer` for the addendum only if not delegated elsewhere; coordinator reviews)

**Files:**
- Modify: `docs/adr/0005-observability-instrumentation-decisions.md` (append an "Addendum 2026-10-06" section only; do not rewrite existing text)
- Create: `docs/roadmaps/observability-phase2a-evidence.md`

- [ ] **Step 1:** Append the addendum: the OTel Operator 0.159.0 injects `OTEL_SERVICE_NAME` from the Deployment name and `OTEL_SERVICE_NAME` outranks a `service.name` resource attribute, so for the orchestrator the controller stamps `OTEL_SERVICE_NAME` (the app ID) and carries `dws.workflow.*` in `OTEL_RESOURCE_ATTRIBUTES`; `dws.node.id`/`dws.node.kind` are omitted because the orchestrator is not a compiled node.
- [ ] **Step 2:** Create the evidence file in the style of `observability-phase1-evidence.md`: environment, what was verified by tests (spec-to-test table), and a clearly labelled **NOT EXECUTED** live section with the exact procedure (Operator 0.159.0, cert-manager, fixture Collector, locally built orchestrator image, store keys, deploy a workflow with at least one call step, force a replay by restarting the orchestrator pod mid-run, what to compare: span counts per `activity:<name>`, workflow span ID stability, trace ID continuity, controller-to-orchestrator linkage), the decision rule (clean -> write the replay ADR; duplicate/restart -> stop and report), and the statement that no span exists inside step pods (Track B). No fabricated trace IDs.
- [ ] **Step 3: Commit.** `git commit -am "docs(observability): record phase 2a sidecar decision and evidence status"` (the new file needs `git add`).
