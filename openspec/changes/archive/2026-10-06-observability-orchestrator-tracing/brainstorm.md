<!--
Raw capture of superpowers:brainstorming output.
design.md reorganizes this into structured sections; the two complement each other.
-->

# Brainstorm — Observability Phase 2a: trace the compiled orchestrator

**Mode:** auto (the requester could not be consulted mid-run). The handoff supplied purpose,
constraints and acceptance criteria, so no clarifying round was needed. The one open seam the
handoff explicitly delegated (item 6, the sidecar Configuration source) was decided here and is
recorded with its rejected alternatives.

**Path classification:** architectural. It crosses `dws-controller` (Java) and `charts/dws`
(Helm) through a new shared contract (config-store keys plus a Dapr Configuration name).

## Understanding written back

- **Outcome:** with observability on, the pod `dws-controller` creates per workflow
  (`dws-orchestrator`) is traced and named after its workflow, so one trace runs controller ->
  orchestrator -> each step call. With observability off, the orchestrator Deployment is
  byte-for-byte what it is today.
- **Stated:** off by default, absent flag and unreachable store both mean off and never fail or
  slow a deploy; flags come from the existing `dws-controller-config` store, no new controller env
  vars; one sampling root (app agent), sidecar pinned to 1; reuse Phase 1's translation, no second
  copy; replay finding recorded, stop if replay is not clean.
- **Assumed:** the flag is read at deploy time (that is what "affects a workflow only on its next
  deploy" means); orchestrator `node.id` / `node.kind` attributes are omitted; the orchestrator
  container is named `orchestrator` (`StackSynthesizer`).

## Recon findings that changed the plan

Facts established by read-only exploration and a primary-source docs check.

1. **The handoff's "key names Phase 1 already added" do not exist.** `observability-template`
   added no keys to `dws-controller-config`; the roadmap (lines 626-630) says the controller-side
   read belongs to Phase 2a. This change must define the keys. *(Deviation, recorded.)*
2. **No orchestrator `dapr.io/config` exists today.** `orchestratorAnnotations()` stamps only
   `dapr.io/enabled`, `dapr.io/app-id`, `dapr.io/app-port`. Auth does not touch the orchestrator.
   There is therefore nothing to merge with today; `dapr.io/config` is single-valued (Dapr
   injector passes it as one `--config` value), so "merge" can only mean "never overwrite an
   existing value".
3. **Phase 1's translation is Helm-only** (`dws.observability.daprTracing` and friends in
   `_helpers.tpl`). Java has no copy. A Java port would be the forbidden second copy.
4. **The chart Role grants `dapr.io` on `components` only** — no `configurations` verb at all.
5. **OTel Operator 0.159.0 overrides `service.name` from `OTEL_RESOURCE_ATTRIBUTES`.** It injects
   `OTEL_SERVICE_NAME` (derived from the Deployment name) unless the container already defines
   `OTEL_SERVICE_NAME`; per the SDK spec `OTEL_SERVICE_NAME` beats a `service.name` resource
   attribute. ADR 0005 Decision 1 says "stamp `service.name` in `OTEL_RESOURCE_ATTRIBUTES`" — that
   would silently report the Deployment name (`<workflow>-v<hash>`) instead of the app-id.
   **Stamp `OTEL_SERVICE_NAME` on the container.** The operator appends to a container-supplied
   `OTEL_RESOURCE_ATTRIBUTES`, so the `dws.workflow.*` attributes survive.
6. **`container-names: orchestrator`** restricts injection to that container; `daprd` untouched.
   Allowed charset is `[a-zA-Z0-9-,]`.
7. **daprd's sampler is `ParentBased(TraceIDRatioBased(rate))`**, so `samplingRate: "1"` follows
   the agent's decision. One sampling root holds.
8. **A missing referenced Configuration crashloops daprd** (operator returns an error, daprd
   exits). So the Configuration must exist before the pod does, and the controller must not stamp
   a reference to one that is absent.
9. **Replay:** durabletask-go persists the workflow span ID in `ExecutionStarted` and reuses it on
   every replay, publishing on completion; by design no duplicates. The Java SDK 1.18.0
   `ActivityRunner` starts an `activity:<name>` span and `OrchestratorRunner` starts none. Whether
   the caller's trace links to the workflow root span is unresolved for SDK 1.18.0 (SDK PR #1783).
   This is exactly what the live probe was to settle.
10. **The orchestrator image is a real JVM** (`eclipse-temurin:25-jre`, `java -jar`). The Java
    agent bundled with Operator 0.159.0 is 2.31.1, whose support table lists Temurin 25 and Spring
    Boot 4.

## Environment constraints discovered (not design inputs, but they bound verification)

The sandbox had no JDK, no Helm, no Docker and no Kubernetes cluster, and no privileged capabilities
to create one (no `CAP_SYS_ADMIN`, no `/dev/kvm`). A JDK 25 and Helm 3.19.0 were installed under
`/home/projects/.jdk` and `/home/projects/.tools`. **A live cluster run is not possible here**, so the
live-trace and replay acceptance items cannot be satisfied in this environment and must be reported
as not done rather than fabricated.

## Decision chain

**Q1. Where does the orchestrator sidecar's tracing Configuration come from?** (handoff item 6)

- A. **Chart renders one standalone, tracing-only Dapr `Configuration` named `dws-tracing`**,
  rendered by a new template that calls the existing `dws.observability.daprTracing` helper
  verbatim. The controller only stamps `dapr.io/config: dws-tracing`. *Chosen.*
  - Exists at install time, before any compiled pod, satisfying "exists before the pod needs it".
  - Carries `tracing` only: no `appHttpPipeline`, no `httpPipeline`, so it cannot disturb auth or
    any other sidecar.
  - Reuses Phase 1's endpoint/protocol/security translation by construction (same helper, no
    Java copy).
  - Fixed name matches the roadmap's `dws-tracing` and the existing fixed `dws-controller-config`.
- B. Controller synthesizes a per-workflow `Configuration` (label-GC'd like the oauth one).
  *Rejected.* Needs a Java port of the endpoint/protocol translation (second copy), the endpoint
  and protocol in the store, an extra RBAC verb set, and an apply-order dependency. More moving
  parts for no benefit over A.
- C. Point the orchestrator at the controller's own `<fullname>-config`. *Rejected.* With auth on
  it carries the controller's `appHttpPipeline` bearer handler, which would then apply to the
  orchestrator sidecar — disturbs auth.
- D. A chart-rendered `Configuration` per component type created by hook. *Rejected.* Hook ordering
  adds nothing over A; A is a plain resource.

**Q2. How does the controller learn "observability is on" and the injection reference?**
Store keys in `dws-controller-config` (Dapr Configuration API), read at deploy time:

- `observability.enabled` — `true` (case-insensitive, trimmed) turns it on; anything else, or
  absent, is off.
- `observability.instrumentation` — the value to stamp in `inject-java`. Optional; default
  `"true"`; accepts `<name>` or `<ns>/<name>` as the Operator does.

The sidecar Configuration name is the fixed constant `dws-tracing` (see Q1), not a key: a key would
only add a way for the store and the chart to disagree. Both keys are read in one round trip with a
bounded timeout so an unreachable store cannot stall a deploy.

*Rejected:* controller env vars (forbidden by the handoff); storing the OTLP endpoint/protocol in
the store (would need a Java translation copy and a drift path).

**Q3. What if the store says on but the chart did not render `dws-tracing`?** daprd would crashloop
(finding 8), failing the workflow's deploy health. Decision: before stamping, the controller checks
that Configuration `dws-tracing` exists in the workflow namespace (one Kubernetes GET). If it is
absent, forbidden, or the lookup errors, the orchestrator is rendered exactly as today and a single
WARN is logged. "Never fail or slow a deploy" therefore holds for a half-configured install too.
This needs `get` on `configurations.dapr.io` in the chart Role and raw RBAC manifest.
*Rejected:* trusting the flag blindly (crashloop risk); a startup-time readiness gate (cannot see
later chart changes).

**Q4. Workflow identity.** Container env on `orchestrator`:
`OTEL_SERVICE_NAME=<appId>` and `OTEL_RESOURCE_ATTRIBUTES=dws.workflow.name=<name>,dws.workflow.version=<versionId>`
(percent-encoded per the SDK spec; current values are kebab-case and `vXXXXXXXX`, so encoding is a
no-op in practice but implemented for safety). `dws.node.id` / `dws.node.kind` omitted — the
orchestrator is not a compiled node. Deviation from ADR 0005's literal wording (`service.name` in
`OTEL_RESOURCE_ATTRIBUTES`), forced by finding 5; ADR 0005 gets an addendum note.
The app-id for the orchestrator is the workflow name, so `service.name == dws.workflow.name`.

**Q5. Merge, not replace.** The annotation is applied with put-if-absent semantics: if a future
change gives the orchestrator its own `dapr.io/config`, the synthesizer leaves it alone and does not
stamp tracing (and logs); the chart's `dws-tracing` would then need to be merged by that change.
Documented in design.md. Env var stamping appends to any existing `OTEL_RESOURCE_ATTRIBUTES` rather
than replacing it.

**Q6. Where is the stamping decision made?** `StackSynthesizer` stays pure: it receives an
`ObservabilitySettings` value (default `OFF`). A separate CDI reader resolves settings from the
store + existence check once per deploy and hands them to the synthesizer. Off => the synthesizer
takes the exact current code path, so the "identical Deployment" requirement is testable by
equality with today's output.

**Q7. Chart gate for `dws-tracing`.** Render when `observability.enabled` and
`observability.workflows.enabled` (the existing per-workflow gate, currently "reserved for Phase
2b"; this change gives it its first consumer, defaulting true). `observability.enabled=false`
renders nothing and the default render stays byte-identical to the Phase 1 baseline.

**Q8. Replay evidence.** Needs a real cluster, the OTel Operator, cert-manager, the fixture
Collector and a locally built orchestrator image. Not obtainable here. Record in
`observability-phase2a-evidence.md` as **not executed**, with the exact procedure and what to look
for, and do **not** write the replay ADR (it must come from evidence). Report plainly.

## Delegation partition

| Unit | Owner | Paths |
|---|---|---|
| Controller: settings reader, stamping, tests, raw RBAC, controller docs | `quarkus-developer` | `dws-controller/` |
| Chart: `dws-tracing` Configuration, Role `configurations` get, values/README/comments, render tests | `platform-deployment-developer` | `charts/`, `.github/workflows/helm.yml` if needed |

Contract between them: Configuration name `dws-tracing` in the controller's namespace; store keys
`observability.enabled`, `observability.instrumentation`; RBAC `get` on `configurations.dapr.io`.
No overlapping paths. `dws-orchestrator` needs no code change.
