# ADR 0007: Helm Chart Feature Layers

- **Status:** In progress — under discussion. Nothing below is decided until this line says Accepted.
  The layering, the auth split and the Members layer are the current working proposal; the open
  questions at the end are what the next discussion round has to settle.
- **Date:** 2026-10-05
- **Context:** [`charts/dws`](../../charts/dws) — the chart today exposes many independent
  `*.enabled` switches with no stated relationship between them. This ADR proposes one layered model
  so an operator turns features on from the core engine outward.
- **Related:** [`docs/roadmaps/dws-auth.md`](../roadmaps/dws-auth.md) (auth and console phases this
  model regroups), [`docs/roadmaps/helm-packaging.md`](../roadmaps/helm-packaging.md),
  [`docs/roadmaps/observability.md`](../roadmaps/observability.md),
  [ADR 0005](0005-observability-instrumentation-decisions.md) (the `observability.enabled`
  default-off pattern this model generalises).

## Context

A chart user should be able to say "I want the engine", then "plus the console", then "plus
multi-user", without learning how the pieces depend on each other. Today the relationships live in
template comments and a few `fail` calls, not in a model.

Verified against the chart as of this date:

| Today | Behavior |
|---|---|
| `controller.enabled`, `dapr.enabled`, `redis.enabled` | Engine pieces, on by default |
| `controller.images.*` | All nine step images are mandatory controller config; every key must stay populated |
| `admin.enabled`, `postgresql.enabled` | **On by default** — admin and Postgres ship even without a console |
| `console.enabled` | Off by default; the UI only |
| `apiGateway.enabled`, `apisix.enabled` | Off by default; the gateway routes **only** admin and console, never the controller |
| `auth.enabled` | **One switch** gates both the controller and admin (Dapr bearer middleware on both) |
| `auth.issuer` / `audience` / `jwksURL`, `auth.dex.enabled`, `dex.enabled` | Issuer config; bundled Dex is labelled dev/quickstart |
| `observability.enabled` | Off by default, own sub-toggles |

There is no `values.schema.json`. Today's guards are `fail` calls in `_preflight.tpl` and
`_helpers.tpl`.

## Proposed decision

**Group features into layers. Each layer is enabled by a toggle and may depend only on the layers
below it. The core is everything an OWS workflow needs to run; everything else is optional and off
by default.**

```
┌────────────────────────────────────────────────────────────────────────────┐
│ L5  Mesh / Edge     istio                                                  │
│ ┌────────────────────────────────────────────────────────────────────────┐ │
│ │ L4  Observability   OTel, tracing                                      │ │
│ │ ┌────────────────────────────────────────────────────────────────────┐ │ │
│ │ │ L3  Members         users, roles, admin CMS                        │ │ │
│ │ │ ┌────────────────────────────────────────────────────────────────┐ │ │ │
│ │ │ │ L2  Console         admin, Postgres, UI, gateway,              │ │ │ │
│ │ │ │                     one admin account                          │ │ │ │
│ │ │ │ ┌────────────────────────────────────────────────────────────┐ │ │ │ │
│ │ │ │ │ L1  Security        core auth, API tokens                  │ │ │ │ │
│ │ │ │ │ ┌────────────────────────────────────────────────────────┐ │ │ │ │ │
│ │ │ │ │ │ L0  CORE            engine + steps + triggers          │ │ │ │ │ │
│ │ │ │ │ └────────────────────────────────────────────────────────┘ │ │ │ │ │
│ │ │ │ └────────────────────────────────────────────────────────────┘ │ │ │ │
│ │ │ └────────────────────────────────────────────────────────────────┘ │ │ │
│ │ └────────────────────────────────────────────────────────────────────┘ │ │
│ └────────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────────┘
```

Shared foundation, not a layer: the **issuer** (`external` OIDC or `bundled` Dex), consumed by L1
and L2.

| Layer | Adds | Needs | Roadmap |
|---|---|---|---|
| **L0 Core** | Engine (`dws-controller`, orchestrator, flow, step; definitions and config stores), every step runtime (`call` http/grpc/openapi/asyncapi/a2a, `run` shell/script), triggers (`schedule.*`, event/pubsub). Prerequisites: Dapr, Knative, state store — bundled or bring-your-own | – | Phases 0–6 |
| **L1 Security** | Core auth (bearer on the controller), Dapr API token, App API token | issuer | Console Auth Phases 2, 10 |
| **L2 Console** | `dws-admin`, Postgres, `dws.events` read model, UI, APISIX gateway and routes, one admin account logged in via OIDC | L0, issuer; L1 recommended | Console Auth Phases 0–6 |
| **L3 Members** | Multiple users, roles (RBAC), user management, admin CMS | L2 with auth on; an issuer that supplies more than one identity | Phases 7, 9 |
| **L4 Observability** | OTel `Instrumentation`, tracing config, per-workflow pods | L0 (L2 only for the console trace link) | Observability roadmap |
| **L5 Mesh / Edge** | Istio | L1 | Phase 11 (exploratory) |

L1 and L4 each need only L0; their nesting order is cosmetic.

### Auth is split in two

| | Core auth (L1) | Console auth (L2) |
|---|---|---|
| Callers | Machines: CI, API clients, services | Humans in a browser |
| Protects | `dws-controller`; Dapr API token and App API token on core apps | `dws-admin`, console and admin routes at the gateway, App API token guard on admin |
| Needs | Issuer | Issuer plus login flow |

Console auth uses OIDC for every user, including the single solo admin, so solo and members share
one login path and one pipeline placement. Only authorization differs. This keeps the settled
pipeline placement structural: each layer owns its own pipeline entry (`dws-admin` on
`httpPipeline`, `dws-controller` stays on `appHttpPipeline`), so no cross-layer conditional is
needed.

### Console and Members

- **Console (L2):** one admin account, authenticated by OIDC. Zero-config shape: bundled Dex with
  one static user seeded from a Secret.
- **Members (L3):** a separate layer wrapping Console, not a mode flag inside it. Enabling it adds
  multi-user and roles. Upgrading from solo is one toggle; the first admin is bootstrapped by
  **email claim**, not `sub`, so a simultaneous move from bundled Dex to an external IdP does not
  orphan the admin.

### Guards (fail at render time, with an error naming the fix)

- A layer enabled without a layer it needs.
- Console enabled without console auth, unless an explicit dev-only opt-out is set.
- Members enabled without an issuer that can supply more than one identity (Dex with only static
  users does not qualify).
- Console or core auth enabled without an issuer.

### Defaults and presets

Everything above L0 defaults to off. Presets (`minimal`, `standard`, `full`) are shortcuts that set
the toggles; an individual toggle always overrides its preset.

## Rationale

- **The gateway belongs to the console.** Its only routes are admin and console, so it is
  console infrastructure, not security.
- **Console sits outside security.** Security protects core's own API and works headless (CI, no
  UI); the console is a client that plugs into it.
- **Auth is split because the callers differ** (machines vs humans), and the split removes the
  conditional pipeline wiring the `/dapr/subscribe` blocker would otherwise force across layers.
- **Members is a layer, not a mode,** so "who may log in" can grow without changing what Console
  is.

## Consequences

| Today | Proposed | Impact |
|---|---|---|
| `admin.enabled`, `postgresql.enabled` default **true** | Part of L2, default off | **Breaking default** for existing releases |
| `auth.enabled` (one switch) | Core auth (L1) + console auth (L2) | Existing value must map to both for current releases |
| `controller.images.*` all mandatory | Per-kind pruning in L0 | Needs a controller-side change; see Q3 |
| `console.enabled` = UI only | L2 umbrella | Meaning widens; see Q1 |
| `auth.dex.enabled` / `dex.enabled` dev/quickstart | Default console identity | Positioning change; see Q4 |
| Guards are template `fail` calls | Same mechanism, one place | No new mechanism required |
| `auth.enabled` default false | Console auth default on when console is on | Default change; see Q5 |

## Open questions

1. **Umbrella or granular?** One `console.enabled` that brings admin, Postgres, gateway and UI, or
   separate toggles for each?
2. **Default flip.** Moving admin and Postgres to off-by-default breaks existing releases. Flip
   with a release note, or keep today's defaults and let presets carry the layering?
3. **Pruning step kinds and triggers.** Is it needed at all (core always full), or worth the
   controller change so a workflow using a pruned kind fails at compile time rather than runtime?
4. **Bundled Dex as the default console identity.** It is dev/quickstart today. Acceptable for a
   production solo admin? The upstream limit on bundled-IdP sessions and RP logout (Phase 8)
   applies even to solo.
5. **Console auth default.** On by default when the console is on, versus today's `auth.enabled`
   default of false for compatibility.
6. **Admin-to-controller hop.** When core auth is on, `dws-admin` forwards the user's token through
   Dapr invocation. Same issuer and audience for both, or a separate service credential?
7. **Dapr API token vs gateway.** The token applies to all callers, so the console gateway must
   inject it. Record as a soft dependency between L1 and L2?
8. **Guards.** Keep `fail` calls only, or also add `values.schema.json`?
9. **Presets.** A `profile` key, or shipped values files?
10. **Members scope.** Role set, claim/group-to-role mapping, and the line between Phase 7 and
    Phase 9.

## Not decided here

Key names (the toggles above are illustrative; the implementing change fixes them), the Istio design
(Phase 11 needs its own ADR), and any chart implementation. Handoff prompts follow once this is
Accepted.

## Discussion log

| Step | Change |
|---|---|
| 1 | First cut: core, step runtimes, triggers, console, security, observability, edge as separate layers |
| 2 | Step runtimes and triggers folded into core (they are what makes a workflow run) |
| 3 | Console placed outside Security |
| 4 | Gateway moved into Console (verified: it only routes admin and console) |
| 5 | Auth split into core auth and console auth |
| 6 | Console given a single admin account; Members added as its own layer outside Console |
| 7 | Solo admin logs in via OIDC too, making the issuer a shared foundation |
