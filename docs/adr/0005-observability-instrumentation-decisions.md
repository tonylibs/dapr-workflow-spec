# ADR 0005: Observability Instrumentation Decisions

- **Status:** Accepted
- **Date:** 2026-09-19
- **Context:** [`docs/roadmaps/observability.md`](../roadmaps/observability.md) — Phase 1 (chart
  surface) encodes Decision 2 directly as a template value, and Phase 2a/2b encode Decisions 1
  and 3. Recorded before Phase 1 implementation so none of the three arrives in the codebase as
  an unexplained constant.
- **Related:** ADR 0004 *Scope Nodes Carry Their Own Configuration* (Decision 1 is the telemetry
  analogue of that decision and should stay consistent with it);
  [ADR 0002](0002-workflow-compiler-strategy-split.md) (the `CompiledNode` shape Decision 1 reads
  identity from); [ADR 0003](0003-fork-as-a-flow-node.md) (`CompiledNode.nodeId()`, the source of
  `dws.node.id`).
- **Note on the reference above:** two ADRs in this repo are numbered 0004 —
  `0004-scope-nodes-carry-their-own-configuration.md` (2026-09-08) and
  `0004-call-a2a-runner-design.md` (2026-09-15). This ADR references the former, by title, because
  the number alone is ambiguous. The collision predates this ADR and is not resolved by it;
  renumbering would break inbound links from the roadmap docs, their Notion mirrors and the
  tracker. Flagged as a tracked follow-up.

## Context

The observability roadmap wires three independent telemetry layers into one picture: an
application agent (OTel Operator auto-injection, or an in-code SDK), the Dapr sidecar, and —
for Knative Services — Knative Serving's own native OTLP output. A planned Istio integration
would add a fourth.

Three decisions taken while designing that wiring are non-obvious enough that a future reader
will either misread them as oversights or "fix" them into breakage. All three are cheap to state
and expensive to rediscover, so they are recorded here together rather than left as roadmap prose.

None of the three is a new design. Decisions 1 and 3 were settled on 2026-09-12 when the roadmap
was written; Decision 2 was extended on 2026-09-19 when Knative's native telemetry was found to
be a third sampling claimant. This ADR is the write-up those decisions never got.

## Decision 1: Compiled nodes carry their own telemetry identity

`dws-controller` stamps `OTEL_RESOURCE_ATTRIBUTES` onto every pod it compiles, carrying identity
the pod cannot derive for itself:

| Attribute | Source |
|---|---|
| `service.name` | the node's Dapr app ID (already computed — see `AGENTS.md`, "Task name → Dapr app-id") |
| `dws.workflow.name` | definition name |
| `dws.workflow.version` | definition version (the immutable versioned definition already exists) |
| `dws.node.id` | `CompiledNode.nodeId()` (ADR 0003) |
| `dws.node.kind` | `flow` / `step` |

`service.name` deliberately reuses the Dapr app ID rather than the image name. That makes a
trace's service graph line up one-to-one with the compiled node graph an operator already sees in
the console — the same identifier in both views, no mental translation.

**Rationale.** This is compile-time knowledge nothing else in the system holds. The image is
generic by design: one `dws-step` image serves every Step node in every workflow. Without
stamped identity, every workflow's pods report as the same handful of anonymous services and a
trace becomes unreadable at exactly the moment it matters. The controller is the only component
that knows which node a given pod *is*.

This is the telemetry analogue of ADR 0004 *Scope Nodes Carry Their Own Configuration*: the same
principle — a generic image, pinned at deploy time to the one node it represents — applied to
observability rather than to workflow definition.

## Decision 2: One sampling root; every downstream layer pinned to 1

The application agent (or in-code SDK) is the **sampling root**, using
`parentbased_traceidratio`. Every other layer honours the inbound parent flag instead of deciding
for itself:

| Layer | Setting |
|---|---|
| App agent / in-code SDK | `parentbased_traceidratio`, rate from `observability.traces.samplingRate` |
| Dapr `Configuration` CRD | `samplingRate: "1"` |
| Knative `config-observability` | `tracing-sampling-rate: 1.0` |
| Istio, if it lands | `meshConfig.defaultConfig.tracing.sampling: 100` |

**Rationale.** Sampling decisions compose multiplicatively. Two layers each independently
sampling at 10% do not produce 10% of traces — they produce complete traces for roughly 1% of
requests and *partial* traces, with holes in the middle, for the rest. A span whose parent was
dropped is orphaned; a trace whose middle was dropped looks like a service that stopped
responding.

The failure mode is what makes this worth an ADR: it is **silent and misattributed**. Nothing
errors. The traces that arrive look plausible. The gaps read as a broken exporter, a dropped
sidecar, or a flaky backend — not as a sampling misconfiguration three layers away. Teams lose
days to this.

It is also **easy to break with a reasonable-sounding change.** "Span volume is too high, lower
the Dapr sampling rate" is the obvious lever, and it is precisely the wrong one. The only correct
lever is `observability.traces.samplingRate` at the root; the escape hatches for volume are
`observability.traces.daprSpans` and `observability.workflows.enabled`, both of which turn
layers *off* cleanly rather than sampling them independently.

Knative's claimant is the easiest to miss, because `config-observability` lives in the
`knative-serving` namespace, which the DWS chart does not own — see the roadmap's chart-boundary
note.

## Decision 3: Go rejects `inject-go`; in-code OTel SDK instead

`dws-call-http`, `dws-call-grpc` and the three `dws-run-*` images do **not** use the OTel
Operator's `inject-go` annotation. They bootstrap `otel-go` + `autoexport` in code, reading the
same standard `OTEL_*` environment variables the controller stamps for every other stack.

**Rationale.** `inject-go` is eBPF-based. It requires a privileged sidecar and
`CAP_SYS_PTRACE`, and runs one agent process per instrumented container. Every Go image here is
a scale-to-zero Knative Service, where that cost is paid per cold start and the privilege
escalation is difficult to justify for a function pod. The other three mechanisms (`inject-java`,
`inject-nodejs`, `inject-dotnet`) are bytecode/profiler-based and carry none of that.

**The asymmetry is deliberate and must read that way.** Four stacks auto-inject and one does not;
without this record that looks like an unfinished migration. It is not — it is a considered
rejection of a mechanism that does not suit the workload.

**The config surface stays identical either way.** Because the in-code SDK reads the same
`OTEL_*` variables auto-injection would have supplied, nothing in the chart, the controller's
stamping logic, or an operator's mental model changes between an injected stack and a
self-instrumented one.

## Consequences

- **Stamped config does not retro-fit deployed stacks.** Identity and endpoint reach a pod at
  compile time, so changing `observability.otlp.endpoint` affects a given workflow only on its
  next deploy. Accepted: a workflow version redeploys its whole pod-set anyway. Must be stated
  prominently in the operator guide, or it will be filed as a bug.
- **Decision 2 creates a cross-namespace obligation.** Pinning Knative's rate means touching a
  ConfigMap the DWS chart does not own. Whether that is a documented prerequisite or a chart
  hook is a separate open question (observability roadmap, ADR candidate 5).
- **Decision 3's path is also the contingency for other stacks.** If the Knative init-container
  spike (roadmap Phase 0-B(a)) or the `inject-python` decision (0-B(d)) comes back hostile, the
  Node and Python step images adopt this same in-code pattern. That is a known, already-designed
  fallback, not a re-plan.
- **`dws.*` attribute names are now a compatibility surface.** Operator dashboards and alerts
  will key off `dws.workflow.name` and friends. Renaming them post-release breaks those silently,
  same class of problem as renaming a metric.

## Non-goals

- **This ADR does not decide trace-context behaviour across Dapr Workflow replay.** That question
  is deliberately deferred to observation during Phase 1/2a testing rather than spiked up front
  (roadmap Phase 0-A(c)). It gets its own ADR once real replay traces exist.
- **It does not decide whether all call-step egress routes through Dapr.** That changes failure
  semantics for every call step and belongs to the Workflow Runtime Architecture roadmap; it is
  currently deferred pending the Istio decision (roadmap ADR candidate 6).
- **It does not settle OTel Operator packaging** — prerequisite versus chart dependency is
  answered in the roadmap's Phase 0-A(b) findings and belongs with candidate 5, not here.
