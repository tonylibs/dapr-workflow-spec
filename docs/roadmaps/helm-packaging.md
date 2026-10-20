# Helm Chart Packaging Roadmap

Roadmap for packaging DWS as an installable Helm chart (`charts/dws`), so a cluster operator
can bring up the platform's control plane — `dws-controller`, `dws-admin` (+ its Postgres read
model) — with one `helm install`, optionally including Dapr and/or Knative Serving.

## Scope

Only the long-running platform components are chart-managed:

| Component | In chart? | Why |
|---|---|---|
| `dws-controller` | Yes | Persistent control-plane Deployment |
| `dws-admin` | Yes | Persistent read-model service, needs Postgres |
| Postgres (for `dws-admin`) | Yes, optional built-in | Bitnami PostgreSQL subchart by default; swappable for an external DB |
| Redis (backs Dapr Components: `dws-definitions` and `dws-controller-config` Configuration stores today, actor/workflow state store once `dws-orchestrator` calls the Dapr Workflow runtime) | Yes, built-in — **done** (Phase 5) | Bitnami Redis subchart, but unlike Postgres it has no independent toggle: `condition: dapr.enabled`, since it exists solely to back the Dapr Redis Components. External Redis via `redis.external.host` (retargets the Components; does not uninstall the subchart — see open items) |
| `dws-console` | Yes, optional | Deployment/Service rendered against `ghcr.io/tonylibs/dws-console`, gated behind `console.enabled` (default `false`). Routing moved to the shared `apiGateway.*` Gateway API front door; the console-only Ingress was removed. See [`dws-console` roadmap](dws-console.md) |
| `dws-orchestrator` | No | Deployed dynamically, per-workflow, by the controller at runtime — not a static install target |
| `dws-call-http` / `dws-call-openapi` / `dws-run-*` | No | Same as above — controller stamps these out per workflow |
| Dapr | Optional dependency | User-toggleable — see below |
| Knative Serving | Optional dependency | User-toggleable — see below |
| Dex (in-chart IdP for `dws-console` login) | Yes, optional — **done**, toggle `dex.enabled` (default `false`) | Landed via the separate [`dws-auth` roadmap](dws-auth.md) (Phase 0), not tracked as a phase here — noted because it now ships inside this chart. No DWS service consumes its tokens yet |

## User-facing install options

Both cluster-wide prerequisites are offered, not assumed:

| `dapr.enabled` | `knative.enabled` | Result |
|---|---|---|
| true | true | Chart installs both control planes + DWS platform — single-command bootstrap |
| false | false | Chart installs only the DWS platform, assumes both pre-exist |
| true | false | Mixed — org already runs shared Knative |
| false | true | Mixed — org already runs shared Dapr |

- Dapr installs via the official `dapr/dapr` chart as a conditional dependency (`condition: dapr.enabled` in `Chart.yaml`). Ships in **Phase 4**.
- Knative Serving has no comparable official Helm chart, so it installs via a Helm hook Job that `kubectl apply`s the pinned CRD + core manifests (mirrors the existing pinned bundles in `dws-controller/k8s/{dapr-crds,serving-crds}.yaml`). Split out of Phase 4 and deferred to **Phase 11** (see below) — not on the critical path for Phase 5's event wiring, which only needs Dapr.
- A preflight check (`Capabilities.APIVersions.Has`, evaluated against the real cluster at `helm install`/`upgrade` time) fails the install fast if a prerequisite is toggled off but its CRDs aren't actually present — instead of deploying workloads against a missing control plane. Ships per-prerequisite: the Dapr check lands with Phase 4, the Knative check with Phase 11.

## Chart layout

```
charts/dws/
├── Chart.yaml                # dependencies: postgresql, dapr, redis, dex, apisix (all
│                             # conditional; redis follows dapr.enabled — no independent toggle,
│                             # apisix follows its own apisix.enabled)
├── README.md                 # partial, Phase 10 — operator docs: pre-Gateway Ingress→Gateway API
│                             # migration runbook + "validating changes to this chart"
├── values.yaml
└── templates/
    ├── controller/           # serviceaccount, rbac (role+rolebinding), deployment, service
    ├── admin/                # deployment, service, secret (db conn resolution)
    ├── postgres/             # chart-owned admin DSN Secret — toggle: postgresql.enabled
    ├── pubsub-component.yaml # done, Phase 5 — Dapr Component "pubsub" (pubsub.redis),
    │                         # topic dws.events, toggle: dapr.enabled
    ├── definitions-component.yaml  # done, Phase 5 — Dapr Component "dws-definitions"
    │                               # (configuration.redis); chart-managed replacement for the
    │                               # hand-applied dws-orchestrator/k8s/configuration-component.yaml
    │                               # (kept in that repo for non-chart deployments), toggle: dapr.enabled
    ├── actor-statestore-component.yaml  # done, Phase 5 — Dapr Component (state.redis,
    │                                    # actorStateStore: "true"); provisioned ahead of
    │                                    # dws-orchestrator adopting the Dapr Workflow runtime,
    │                                    # toggle: dapr.enabled
    ├── console/              # done, Phase 6 — deployment + service against
    │                         # ghcr.io/tonylibs/dws-console, toggle: console.enabled
    │                         # (default false); the Phase 6 console-only ingress.yaml was
    │                         # REMOVED — routing now comes from api-gateway/ below
    ├── dex/secrets.yaml      # done, dws-auth Phase 0 — chart-managed bootstrap-admin Secret
    │                         # (generated password + bcrypt hash for Dex's staticPasswords),
    │                         # toggle: dex.enabled (default false); see dws-auth.md
    ├── knative-install-job.yaml   # hook Job, toggle: knative.enabled (Phase 11)
    ├── dapr-ready-hook.yaml  # done, Phase 4 — post-install/upgrade Job, self-heals a missed
    │                         # Dapr sidecar injection on the admin Pod, toggle: dapr.enabled
    ├── preflight.yaml + _preflight.tpl  # done, Phase 4 — fails install/upgrade fast if
    │                                    # dapr.enabled=false but Dapr CRDs aren't present
    ├── api-gateway/          # done — shared Gateway API front door (gatewayclass, gateway,
    │                         # gatewayproxy, console-httproute, admin-httproute with the
    │                         # /dws-admin → admin Dapr sidecar rewrite), toggle: apiGateway.enabled;
    │                         # backed by the bundled APISIX dependency or an external GatewayProxy
    ├── admin/auth-{component,configuration}.yaml     # done, dws-auth — bearer middleware +
    ├── controller/auth-{component,configuration}.yaml  # sidecar Configuration CRDs, toggle: auth.enabled
    ├── controller/config-component.yaml  # done — `dws-controller-config` (configuration.redis),
    │                                     # carries the v1/v2 compiler flag (see ADR 0002)
    ├── NOTES.txt             # post-install operator hints
    ├── tests/admin-db-connection.yaml        # done, Phase 8 — `helm test` DB connectivity check
    ├── tests/controller-auth-negative.yaml   # done, dws-auth — `helm test` unauthenticated-reject check
    └── _helpers.tpl          # includes dws.redis.host / secretName / secretKey — resolve to
                              # in-chart Bitnami Redis by default, or redis.external.* when set;
                              # also dws.console.legacyIngress.validate (the Phase 6 Ingress trap)

charts/dws/tests/            # shell render-assertions run in CI: api-gateway-render-test.sh,
                             # auth-pipeline-placement-test.sh, values-schema-test.sh
```

Both the admin and controller Deployments carry `dapr.io/enabled`/`dapr.io/app-id` pod
annotations. Admin's are gated behind `dapr.enabled` (Phase 4) because it also renders
`DAPR_PUBSUB_*` env vars tied to the same toggle. Controller's are rendered unconditionally
(Phase 5) — the annotations are inert without a sidecar-injector, and always-on keeps the
controller ready to publish the moment a Dapr control plane appears (chart-installed or
external). The controller intentionally has no `dapr.io/app-port` — it only publishes outbound
via its Dapr sidecar and never receives Dapr-routed inbound traffic.

## Design direction: feature layers and bring-your-own infra (proposed 2026-10-05)

Two ideas for how operators configure the chart. Nothing here is built yet — see Phases 12
and 13 below. Items marked **open** are not decided.

### Feature layers — enable from the core outward

Goal: whoever configures the chart turns DWS features on layer by layer, starting from the core
engine. Each outer layer needs the layers inside it.

```
┌────────────────────────────────────────────────────────┐
│ L4  Console members & roles (multi-user)               │
│ ┌────────────────────────────────────────────────────┐ │
│ │ L3  Console  (dws-console + login (OIDC)           │ │
│ │              + API gateway + Admin API)            │ │
│ │ ┌────────────────────────────────────────────────┐ │ │
│ │ │ L2  Events  (Knative Broker + lifecycle events)│ │ │
│ │ │ ┌────────────────────────────────────────────┐ │ │ │
│ │ │ │ L1  Core: engine + step runtimes           │ │ │ │
│ │ │ │     + triggers + Knative Serving           │ │ │ │
│ │ │ └────────────────────────────────────────────┘ │ │ │
│ │ └────────────────────────────────────────────────┘ │ │
│ └────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────┘

Engine auth (machine callers of the engine): a separate option on L1, not a layer.
```

| Layer | Contains | Today's toggles (closest match) |
|---|---|---|
| L1 Core | Everything an OWS workflow needs to run: the engine, the step runtimes and triggers. Not optional | `controller.*`, `controller.images.*`, `dapr.enabled` (+ its Redis), Knative Serving (Phase 11, pulled into Phase 12 — not built) |
| L2 Events | A Knative Eventing Broker; DWS publishes its lifecycle events (definition, deployment, instance, task) to it as CloudEvents; consumers attach Triggers. The Dapr `pubsub` Component + Redis stay in Core (OWS `emit`/`listen` use them) | none — lifecycle events go over Dapr pub/sub `dws.events`, always on today |
| L3 Console | `dws-console`, its login (console auth), the API gateway that fronts it, and the Admin API (`dws-admin` + its Postgres) | `console.enabled`, `apiGateway.*`, `apisix.*`, `auth.*`, `dex.*`, `admin.*`, `postgresql.*` |
| L4 Members & roles | Multi-user console with roles | none — not built |
| *(not a layer)* Engine auth | Auth for machine callers of the engine (deploy, start, events) | none — not designed yet |

Decided:

| # | Decision |
|---|---|
| 1 | Core = everything an OWS workflow needs to work. Step runtimes and triggers are part of core, not optional layers |
| 2 | The API gateway (APISIX) fronts the console only, so it belongs to the Console layer |
| 3 | Console auth (authorizing console users) is part of the Console layer: the console always requires login, there is no console without auth. Replaces the earlier "Console sits outside a Security layer" idea (2026-10-20) |
| 4 | The console starts solo: one admin account. Multi-user "members with roles" is its own layer wrapping Console, not a mode inside it |
| 5 | The solo admin account also logs in through OIDC |
| 6 | Engine auth is a different thing from console auth: an option on Core, not a layer (2026-10-20) |
| 7 | `dws-admin` belongs to the Console layer, not Core, and is called the **Admin API** (2026-10-20) |
| 8 | Phase 12 assumes every component in a layer is **bundled** and on by default. Swapping in the operator's own infra is Phase 13, not part of layering (2026-10-20) |
| 9 | **`console.enabled` is the Console layer switch.** On → console, Admin API, Postgres, APISIX gateway, Dex and console login all come up. The only other required input is the hostname. Per-component `enabled` flags (`admin`, `postgresql`, `apisix`, `apiGateway`, `dex`, `auth`) stop being operator settings; their blocks keep tuning only (image, resources, bootstrap admin). Ships as chart **2.0**: the default install becomes Core only (no Admin API, no Postgres) (2026-10-20) |
| 10 | **Turning the Console layer on later must work.** `helm upgrade --set console.enabled=true` on a Core-only release is a supported path, so Phase 12 fixes today's bundled-APISIX upgrade hang (etcd `pre-upgrade` hook waits on a Secret the upgrade never creates; Helm skips `crds/` on upgrade) rather than restricting the Console layer to fresh installs (2026-10-20) |
| 11 | **With the Console layer on, the controller accepts only console (Dex) tokens.** Turning on the console's login also gates the controller API; scripts and CI calling the controller directly need a Dex token until engine auth is designed. With it off the controller stays open, as today (2026-10-20) |
| 12 | Observability (`observability.*`) is an add-on beside the layers, not a layer (2026-10-20) |
| 13 | **Events is its own layer (L2), between Core and Console.** Core alone publishes **nothing**. The layer switches DWS's lifecycle publishing only; the `pubsub` Component and Redis stay in Core because OWS `emit`/`listen` use them. The Console layer needs Events (the Admin API reads `dws.events`), so turning the console on turns Events on. Layers renumbered: Events L2, Console L3, Members & roles L4 (2026-10-20) |
| 14 | **Events layer v1 = (1) lifecycle event stream** — every definition/deployment/instance/task event published as a CloudEvent, so operators plug their own consumers in without polling the controller; **(2) stable, versioned event contract** — documented `io.dws.*` types with a version on each, so consumers survive DWS upgrades (2026-10-20) |
| 15 | Workflow chaining on DWS events is **not** an Events-layer feature: the mechanism is Core (OWS `listen` / `schedule.on` triggers). Events only makes lifecycle events available to listen to (2026-10-20) |
| 16 | **The Events layer is a Knative Eventing Broker, in-memory for v1** (channel-based Broker on InMemoryChannel — live only, events lost on restart, no replay). Controller and orchestrator POST lifecycle CloudEvents to the Broker address that a Knative **SinkBinding** injects as `K_SINK`: Events on → `K_SINK` present → publish; off → absent → nothing published (this is the on/off contract, no extra controller → orchestrator env var). The Admin API consumes through its own Trigger; operators wire their systems with their own Triggers (any URL, Camel K Kamelets, Knative sinks). Lifecycle events leave Dapr pub/sub, which stays Core-only for OWS `emit`/`listen`. Chosen over a home-made HTTP relay for the standard CloudEvents/Trigger model. Durable broker (Kafka, or a Redis stream in front of the Broker — Knative has no Redis Broker, only beta RedisStreamSource/Sink) is parked with retention (2026-10-20) |
| 17 | **Knative Serving is part of Core**, bundled (the controller already deploys steps as Knative Services, so Core silently assumed it). Phase 11 is pulled into Phase 12. Knative Eventing installs only with the Events layer (2026-10-20) |

Console layer switch, from the operator's side:

| | Console off (`console.enabled=false`, default) | Console on (`console.enabled=true`) |
|---|---|---|
| Installed | controller, Dapr, Redis | + console, Admin API, Postgres, APISIX, Dex |
| Entry point | Controller `/workflows` API, in-cluster only (port-forward / Dapr invoke) | `https://<hostname>`, OIDC login via bundled Dex |
| Run history / queries | none | Admin API + Postgres |
| Controller API | open | Dex bearer token required |
| Install notes | how to reach the controller API | console URL + how to read the bootstrap admin password |

```yaml
console:
  enabled: true              # Console layer switch
  hostname: dws.example.com  # required when enabled
```

Open:

- Engine auth design — gate on starting instances, machine tokens, per-action scopes; and how the
  controller's one bearer check accepts both console user tokens (relayed by `dws-admin`) and
  machine tokens (one IdP with two audiences, or two IdPs).
- Events layer values shape (`events.enabled` sketched).
- Knative install path. Proposed: the official **Knative Operator Helm chart** as a dependency,
  a `KnativeServing` resource always (Core), a `KnativeEventing` resource only with Events.
  To verify: Helm creating the Operator's resources in the same release as its CRDs (same class of
  problem as the APISIX upgrade hang); Serving networking layer (Kourier vs Gateway API); per-pod
  SinkBinding on controller-created orchestrator Deployments.
- Console layer details — revisit after the Events layer (2026-10-20).

Parked Events-layer features (later discussion, 2026-10-20):

| Feature | Benefit |
|---|---|
| Bounded retention + replay (last N events / T hours) | A consumer that was down catches up; Redis memory stays capped |
| Webhook sinks with retries + dead-letter | Alerts on `instance.failed` (Slack, PagerDuty, CI) with zero code |
| Per-sink filters (event type, workflow, namespace) | Each consumer gets only what it needs |
| Durable / your own broker (Kafka, NATS, RabbitMQ, or a Redis stream in front of the Broker) | Events survive restarts; fits the org's existing event backbone — overlaps Phase 13 |

### Bring-your-own infra — swap bundled pieces for the operator's own

Goal: an operator can replace each bundled infra piece with their own.

| Piece | Bundled today | Override today | Gap |
|---|---|---|---|
| Postgres (`dws-admin`) | Bitnami subchart, `postgresql.enabled` | `admin.database.url` / `existingSecret` | none — full swap already works |
| Redis (state + configuration stores) | Bitnami subchart, follows `dapr.enabled` | `redis.external.host` | one host for all Components; bundled Redis still installs (see Open items) |
| Broker (pub/sub `dws.events`) | `pubsub.redis` on the same Redis | only by moving all Redis via `redis.external.host` | no separate host, no other broker type |
| OTLP endpoint | none (collector reserved, `observability.collector`) | `observability.otlp.endpoint` | none — already points at the operator's collector |

Decided:

| # | Decision |
|---|---|
| 1 | Pub/sub and state-store overrides accept only an **allowlist of tested Dapr component types**, not any type |
| 2 | First slice supports **separate hosts** for the pub/sub broker and the state store |

Open:

- Which component types go on the allowlist.
- Whether the configuration-store Components (`dws-definitions`, `dws-controller-config`) are in
  scope or stay on Redis.

## Phased roadmap

Status legend: ✅ done · ⚠️ partial/stubbed · ❌ not started. Updated 2026-09-19 — Phase 10
(docs) reclassified ❌ → ⚠️: a substantial operator-facing `charts/dws/README.md` has since landed
(pre-Gateway upgrade/migration runbook + chart-validation guide) and `CLAUDE.md` gained a
"Releasing" section pointing at `docs/release-process.md`, but the repository root `README.md`
still contains no `helm` reference at all — no install/upgrade/uninstall quickstart, no values
table. Phase 11 (Knative) remains untouched: no `knative-*` template, value or Chart.yaml entry
exists. Chart layout below refreshed to include the API Gateway / auth templates added since the
previous update.

| Phase | Status | Goal | Key tasks |
|---|---|---|---|
| 0. Prep | ✅ | Confirm scope | Bundling vs. docs-only for prerequisites decided; dws-admin/console-placeholder confirmed in-chart |
| 1. Scaffold | ✅ | `helm create charts/dws`, strip unused boilerplate | Chart.yaml, values.yaml skeleton; leftover `helm create` boilerplate removed |
| 2. Controller | ✅ | Port `dws-controller/k8s/*.yaml` to templates | serviceaccount, rbac, deployment, service — namespace/RBAC/`DWS_IMAGES_*` templatized |
| 3. Admin + DB | ✅ | Deployment/Service/Secret for dws-admin | Bitnami PostgreSQL subchart toggle (`postgresql.enabled`) done; external DSN via `admin.database.url`/`existingSecret` done |
| 4. Dapr prerequisite | ✅ | Dapr as chart dependency | `Chart.yaml` declares `dapr/dapr` as a conditional dependency (`condition: dapr.enabled`); `values.yaml` exposes `dapr.enabled` (default `true`). The admin Deployment's `dapr.io/enabled`/`dapr.io/app-id` annotations and `DAPR_PUBSUB_NAME`/`DAPR_PUBSUB_TOPIC`/`DAPR_APP_PORT` env vars are now gated behind `.Values.dapr.enabled`. A preflight check (`templates/_preflight.tpl`, `Capabilities.APIVersions.Has "dapr.io/v1alpha1"`) fails `helm install`/`upgrade` fast when `dapr.enabled=false` but Dapr CRDs aren't present. CI's `integration` job now installs Dapr via the chart on the default `dapr.enabled=true` leg, and runs a second matrix leg with `dapr.enabled=false` against a pre-installed Dapr to prove both toggle states. **Knative stays split out — see Phase 11** |
| 5. Event wiring | ✅ | Wire Dapr Components chart-side: pub/sub (controller→admin) **and Redis-backed Components** | Controller Deployment now carries `dapr.io/enabled`/`dapr.io/app-id` unconditionally (no `dapr.enabled` gate — annotations are inert without a Dapr sidecar-injector, and always-on keeps the controller ready when Dapr later appears; no `dapr.io/app-port` — controller only publishes outbound). Chart.yaml pulls in the Bitnami Redis subchart under `condition: dapr.enabled` (Redis has no independent enable toggle — it exists solely to back the three Dapr Redis Components, so its lifecycle follows Dapr's; external Redis is expressed via `redis.external.host`, which overrides the Components' `redisHost`/`redisPassword` while the built-in subchart still installs alongside — a documented trade-off). Three chart-managed Dapr Component templates render whenever `dapr.enabled`: `pubsub-component.yaml` (`pubsub.redis`, topic `dws.events`), `definitions-component.yaml` (`configuration.redis`, replacing the hand-applied `dws-orchestrator/k8s/configuration-component.yaml` which stays in that repo for non-chart deployments), and `actor-statestore-component.yaml` (`state.redis`, `actorStateStore: "true"`, ahead of `dws-orchestrator` adopting the Dapr Workflow runtime). CI's `integration` job now waits for the Redis rollout, asserts all three Components are present, and runs an end-to-end pub/sub delivery check (a Dapr-enabled publisher/subscriber Pod pair with a `Subscription` CRD on topic `dws.events`) — transport-only, no controller/orchestrator/admin round-trip. Depends only on Phase 4 (Dapr, done) — not on Phase 11 (Knative) |
| 6. Console | ✅ | Add Deployment/Service/Ingress once an image exists | `templates/console/{deployment,service,ingress}.yaml` render behind `console.enabled` (default `false`) against the `ghcr.io/tonylibs/dws-console` image on container port 3000, health path `/healthz`. Skips Dapr sidecar annotations and the postgres/redis init-container waits by design — the console talks to `dws-admin`/`dws-controller` browser-direct over CORS (see [`dws-console` roadmap §5](dws-console.md#5-open-items)). `dws.console.fullname`/`dws.console.selectorLabels` added to `_helpers.tpl`. Ingress was chart-first: single-rule (host/path/className/annotations/tls) routing the configured host to the console Service, `console.ingress.enabled` off by default; it did **not** front `dws-admin`. **Superseded** by the API Gateway change, which deleted `templates/console/ingress.yaml` in favour of the shared Gateway API front door. |
| 7. Values design | ✅ | Finalize `values.yaml` | `defaults.{resources,nodeSelector,tolerations,affinity}` now consistently configures every chart-owned Deployment (controller, admin, console), with deep-merged per-component overrides and a rendered CI assertion. PostgreSQL retains the matching upstream `postgresql.primary.*` StatefulSet schema. Controller deliberately has no route because it is outbound-only. Console/admin routing is now chart-owned via the optional `apiGateway.*` Gateway API front door (same-origin `/dws-admin` rewrite to the admin Dapr sidecar), which settles the previously deferred routing question independently of the `VITE_DWS_ADMIN_URL` runtime-config shim. |
| 8. Testing | ✅ | `helm lint`, `helm template`, install test on kind | `.github/workflows/helm.yml`: lint + template (default/disabled/overridden) + kind server-dry-run in `verify`; a real kind install of admin+postgres+Dapr with `helm test` in `integration` (a hand-rolled kind pipeline instead of the `ct` tool, but covers the same ground) |
| 9. Publish | ✅ | OCI chart repo | `release` job in `helm.yml` packages and pushes to `oci://ghcr.io/tonylibs/charts` on merge to `main` |
| 10. Docs | ⚠️ | Update README + CLAUDE.md | **Partial.** Landed: `charts/dws/README.md` — chart-scoped operator docs covering the pre-Gateway `Ingress` → Gateway API migration (bundled vs. external APISIX, the bundled-etcd `pre-upgrade`-hook deadlock on `helm upgrade`, required values, upgrade/rollback steps, cross-linked to `scripts/verify-console-ingress-migration.sh`) plus a "Validating changes to this chart" section; `CLAUDE.md` §Releasing points at `docs/release-process.md` for chart cutting and the `charts/dws/values.yaml` pins. Still owed: root `README.md` has **zero** helm mentions — needs a `helm install oci://ghcr.io/tonylibs/charts/dws` quickstart (install/upgrade/uninstall + `helm test`) and a values reference table for the top-level toggles (`dapr.enabled`, `postgresql.enabled`, `console.enabled`, `dex.enabled`, `apisix.enabled`, `apiGateway.*`, `auth.*`, `redis.external.*`, `defaults.*`) |
| 11. Knative prerequisite | ❌ | Knative Serving via hook Job | **Pulled into Phase 12 (2026-10-20): Knative Serving is part of Core; proposed install via the Knative Operator Helm chart rather than a hook Job — see Design direction decision 17 and Open.** Not started — split out of the original combined "Phase 4: Prerequisites" so Dapr (needed by Phase 5) isn't blocked on Knative design work. `knative-install-job.yaml` (post-install/post-upgrade hook Job), `knative.enabled`/`knative.version` values, preflight CRD check for Knative. Independent of every other phase — can land whenever, in parallel with anything above |
| 12. Feature layers | ❌ | Operators enable features layer by layer, core outward (see Design direction) | Design only so far (decisions 8–17, 2026-10-20). Layers: Core (incl. Knative Serving — Phase 11 pulled in) → Events → Console → Members & roles, everything bundled. Core alone publishes nothing; Events v1 = in-memory Knative Broker fed via SinkBinding `K_SINK`, consumers attach Triggers, versioned `io.dws.*` CloudEvents (durability, retention, webhooks, filters, own broker parked). `console.enabled` switches the Console layer (and Events with it); chart 2.0 defaults to Core only; upgrading into Console must work; controller accepts only Dex tokens when Console is on. Next: Events layer design detail, then Console, then sub-phases |
| 13. Bring-your-own infra | ❌ | Operators swap bundled Postgres / Redis / broker / OTLP for their own (see Design direction) | First slice: separate pub/sub and state-store hosts, with an allowlist of tested Dapr component types. Pick the allowlist. Related: the bundled-Redis-still-installs trade-off under Open items |

## Open items

- **Dex landed but isn't a phase here.** `dex.enabled` (default `false`), the upstream `dexidp`
  chart dependency, and the chart-managed bootstrap-admin Secret (`templates/dex/secrets.yaml`)
  are done — tracked as Phase 0 of [`dws-auth.md`](dws-auth.md), which also covers the console
  OIDC login (Phase 1, done) and the still-unstarted Dapr bearer-auth phases (2–7) that would let
  any DWS service actually consume Dex's tokens. This roadmap only notes Dex's presence in the
  chart layout/scope above; its own progress lives in `dws-auth.md`.
- **Console admin-URL configurability (deferred, Phase 6 follow-up)**: `VITE_DWS_ADMIN_URL` is a Vite build arg — it is inlined into the client bundle at image-build time (see `dws-console/Dockerfile`), so it is *not* a runtime env var the chart can flip per install. The published `ghcr.io/tonylibs/dws-console` image ships with the default `/dws-admin` (same-origin), which assumes an ingress in front routes `/dws-admin/*` to the `dws-admin` Service. Operators needing a different admin URL currently have to rebuild the image (`docker build --build-arg VITE_DWS_ADMIN_URL=https://admin.example .`). A proper per-install knob needs a runtime-config shim — e.g. `dws-console/server.js` serving a small `/config.json` the SPA reads at boot instead of the baked-in `VITE_*`. Not started; the current chart's `console` values expose `image.{repository,tag,pullPolicy}` so operators can pin a rebuilt image in the meantime.
- ~~**Console ingress does not front `dws-admin`**~~ **(resolved by the API Gateway change)**: the chart now owns a same-origin convention. `apiGateway.enabled=true` renders one shared Gateway API `Gateway` whose admin HTTPRoute matches `/dws-admin` and rewrites to the admin sidecar's Dapr invoke path, with a console root HTTPRoute on the same listener — so the baked default `VITE_DWS_ADMIN_URL=/dws-admin` works out of the box. Both the Phase 6 console-only Ingress and the nginx admin gateway were removed; `console.ingress.enabled` is retained only as a validation trap that fails the upgrade with explicit host/TLS/OIDC/APISIX migration steps. See [`dws-auth.md` §2c](dws-auth.md).
- Built-in Postgres is dev/eval-grade (single replica, no backup) — production users should set `postgresql.enabled: false` and point `admin.database` at a managed instance. Same caveat will apply to the built-in Redis once it exists (`redis.enabled: false` for production, point at a managed instance).
- `dws-orchestrator/k8s/configuration-component.yaml` — the hand-applied Redis-backed `dws-definitions` Component from before this chart existed — is intentionally kept in that repo for non-chart / local deployments. The chart's `templates/definitions-component.yaml` is the equivalent for chart-managed installs; the two describe the same Component shape against potentially different Redis hosts, with no shared templating.
- **External Redis when Dapr is enabled** (in scope for Phase 13): setting `redis.external.host` retargets the three Dapr Component templates but does not disable the in-chart Bitnami Redis subchart (Helm dependency `condition:` fields can't AND two values). An operator running production against a managed Redis therefore still gets an unused in-chart Redis instance unless they separately trim its footprint; documented, accepted trade-off.
- Knative install-via-Job (Phase 11) needs a pinned release version (`knative.version`) kept in sync with the `serving-crds.yaml` bundle already checked into `dws-controller/k8s/`. Not started; deliberately deprioritized behind Dapr since nothing else in the roadmap currently depends on it.
- **Next up (as of 2026-09-19):** only two phases remain open, and they are independent of each other.
  1. **Finish Phase 10** — the smallest remaining slice of this roadmap. `charts/dws/README.md` already covers migration and chart-dev validation; what is missing is a *first-install* story in the repository root `README.md`: `helm install` from `oci://ghcr.io/tonylibs/charts`, the `helm upgrade`/`uninstall`/`helm test` commands, and a values reference table for the top-level toggles. Recommended first, because it is self-contained, unblocks nothing else, and closes the gap between what the chart can do and what an operator can discover.
  2. **Phase 11 (Knative)** — still entirely unstarted: no `knative-install-job.yaml`, no `knative.enabled`/`knative.version`, no Knative preflight branch in `_preflight.tpl`, no Knative reference anywhere under `charts/`. Needs a pinned release version kept in sync with `dws-controller/k8s/serving-crds.yaml`. Nothing else in the roadmap depends on it.
  - Also still open, outside the phase list: the console runtime-config shim for `VITE_DWS_ADMIN_URL` (Phase 6 follow-up above). The unified console-plus-admin routing follow-up is resolved by the API Gateway change. The actor-statestore Component ships ready but unused until `dws-orchestrator` adopts the Dapr Workflow runtime (tracked separately, not on this roadmap).
