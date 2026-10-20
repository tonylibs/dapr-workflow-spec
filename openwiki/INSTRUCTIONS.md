# OpenWiki brief — dapr-workflow-spec (DWS)

This file is the user-authored brief for this repository's OpenWiki. It is control metadata, not a
generated page: read it for scope and priorities on every run, and do not rewrite it unless asked.

## Audience and focus

Write for **people who use DWS**, not for people who change its code:

| Reader | Wants to know |
|---|---|
| Workflow author | What DSL they can write, how to deploy it, how to run and watch an instance |
| Operator | How to install, which features to switch on, what each switch changes, what to watch out for |
| Integrator | Which events/APIs DWS exposes, their contracts, and how to plug their own systems in |

Lead every page with **user-facing behavior**: what you do, what you see, what you get back,
what can go wrong. Code paths, class names and test commands are secondary — put them in a short
"Under the hood" note at the end of a page, or in the `architecture/` and `integrations/` pages.

## Organize by feature layer

The Helm chart is moving to features you switch on in layers, from the core outward
(source of truth: `docs/roadmaps/helm-packaging.md`, "Design direction" and "Phase 12 breakdown").
The user guide lives in `openwiki/user-guide/` with one page per layer:

| Page | Layer | Covers |
|---|---|---|
| `feature-layers.md` | overview | The layer model, today's toggles vs the planned switches |
| `core-engine.md` | L1 Core | Install, submit a workflow, run an instance (orchestrator API and Dapr Workflow API), step runtimes, triggers |
| `events.md` | L2 Events | Lifecycle event stream, how to consume it, the event contract |
| `console.md` | L3 Console (+ L4 Members & roles) | Turning the console on, sign-in, what the console and Admin API do |
| `add-ons.md` | beside the layers | Observability, bring-your-own infra, engine auth, service mesh |

Keep that structure. Add a page only for a new layer or add-on.

## Today vs planned — always label it

Most of the layer model is **designed, not built** (Phase 12). Every behavior on a user-guide page
must say whether it is:

- **Today** — works with the currently released chart (1.x); cite the real value names.
- **Planned** — decided in the roadmap but not built; say which roadmap task delivers it.

Never describe planned values (`console.enabled` as the layer umbrella, `console.hostname`,
`events.enabled`, Knative bundling, the Knative Broker) as if they work today. When a Phase 12 task
lands, move the matching rows from "Planned" to "Today" and delete the old instructions.

## Style

- Short sentences. Prefer tables and Mermaid diagrams over paragraphs.
- Show runnable commands (`helm`, `kubectl`, `curl`) with realistic values.
- Call out traps (upgrade hangs, stale history, lost events) in a "Watch out" list.
- Link to `docs/` sources (`docs/events.md`, `docs/roadmaps/*.md`, `docs/adr/*.md`,
  `charts/dws/README.md`) instead of copying them wholesale.
- Do not copy the stale ADR 0007 layer numbering (L0 Core, L1 Security…); the roadmap's
  L1 Core → L2 Events → L3 Console → L4 Members & roles is current.
