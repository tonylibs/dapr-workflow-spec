## Context

`dws-admin` is a new, fifth component in the monorepo. Unlike the other four (Java/Quarkus,
Java/Spring, Go, Fastify), it has no existing Node/Nest convention to follow in-repo — the closest
sibling, `dws-call-openapi`, is plain Fastify + vitest and is explicitly not a pattern to reuse
(per root `CLAUDE.md`, components are independently toolchained by design). This design covers only
the skeleton: project scaffold, read-model schema, and event ingestion. No REST read/write API
ships in this change (Epic 3).

The two named integration libraries were verified against their public source (in-repo `node_modules`
isn't available to inspect directly in this environment, so behavior below is confirmed from each
package's README on GitHub, not from a local install):

- **`@knaadh/nestjs-drizzle-postgres`** (`DrizzlePostgresModule`): `register`/`registerAsync`
  (`useFactory`/`useClass`) take `{ tag, postgres: { url, config? }, config?: { schema, ...drizzle
  options } }`; the typed `PostgresJsDatabase<typeof schema>` client is injected elsewhere via
  `@Inject(tag)`.
- **`@dbc-tech/nest-dapr`** (`DaprModule`): `register`/`registerAsync` take
  `{ serverHost?, serverPort?, daprHost?, daprPort?, communicationProtocol?, clientOptions? }`.
  `@DaprPubSub(pubsubName, topicName, route?)` decorates a method on any `@Injectable()` provider
  registered in a module; the module registers handlers on Nest's `onApplicationBootstrap`. The
  real `@dapr/dapr` `DaprClient` is also directly injectable (constructor param typed `DaprClient`)
  for later outbound use (`.invoker.invoke()`, `.pubsub.publish()`).

## Goals / Non-Goals

**Goals:**
- Stand up `dws-admin` as a buildable, testable NestJS project with the module layout the epic
  specifies.
- Land the Postgres read-model schema and migration discipline (Drizzle, checked-in migrations
  only).
- Subscribe to every event type in `docs/events.md` and turn them into idempotent, order-tolerant
  upserts.
- Make local dev reproducible: `docker-compose up` + `pnpm start:dev` + `dapr run`.

**Non-Goals:**
- Any read/write REST endpoint beyond `/health` (Epic 3).
- The `commands` audit-log table (Epic 5).
- CI workflow wiring for `dws-admin` (separate task).
- Multi-tenancy, auth, or rate limiting.

## Decisions

### D1: Module layout mirrors the proposal's package boundaries
`ConfigModule` (env schema via `@nestjs/config`, validated at boot) is imported first and feeds
`StoreModule` (`DrizzlePostgresModule.registerAsync`, tag `'DB'`) and the Dapr wiring
(`DaprModule.registerAsync`) via each module's `useFactory` + `inject: [ConfigService]`, so neither
integration hardcodes connection details. `StoreModule` re-exports the `'DB'` provider (via
`exports: ['DB']` on the dynamic module it wraps) so repositories elsewhere just `@Inject('DB')`.
`DaprEventsModule` imports `StoreModule` and hosts the `@DaprPubSub` handler providers.
`WorkflowsModule`/`InstancesModule` get empty `@Controller()` scaffolds with no routes registered
yet, deferring to Epic 3 rather than stubbing placeholder endpoints that would need to be reworked.

- *Alternative — one flat `AppModule`*: rejected; the proposal's module boundaries map directly to
  future epics' ownership (E3 fills in Workflows/Instances, E4+ may add more), so splitting now
  avoids a later forced refactor.

### D2: Envelope unwrapping — two levels of `data`
Per `docs/events.md`, Dapr wraps the published bytes in its own transport-level CloudEvent; the
*body* of that CloudEvent's `data` field is **our** documented envelope (`id`, `source`, `type`,
`time`, `datacontenttype`, `data`), and *that* envelope's `data` is the per-type payload (e.g.
`{workflow, version, createdAt}`). A `@DaprPubSub` handler in `@dbc-tech/nest-dapr` receives the
message body Dapr's SDK hands it — this is the outer Dapr CloudEvent's `data`, i.e. **our** envelope
directly (Dapr's own transport wrapper is unwrapped by the SDK before the handler runs). So a
handler reads `envelope.id` (idempotency key), `envelope.type` (routing), and
`envelope.data.<field>` (payload). All event handlers share a small `EventEnvelope` DTO/type-guard
so this double-`data` shape is decoded in exactly one place, not re-parsed per handler.
- **Verified** against the installed packages (`@dbc-tech/nest-dapr@0.8.0`'s `dapr.loader.js`,
  `@dapr/dapr`'s `DaprPubSubCallback.type.d.ts`): `DaprLoader` calls
  `daprServer.pubsub.subscribe(name, topic, (data) => instance[methodKey].call(instance, data),
  route)` — the decorated method receives exactly one argument, the pubsub message's `data`, which
  is our envelope directly (no extra nesting), confirming the shape assumed above. One correction:
  `@dapr/dapr`'s own callback type documents `data` as "typically string or object," so the decoder
  also accepts a raw JSON string and parses it before validating, rather than assuming it's always
  already an object.

### D3: One `@DaprPubSub` subscriber for the whole topic, dispatching to two processor services
**Corrected during implementation** (originally this design called for two separate
`@DaprPubSub('pubsub', 'dws.events')`-decorated classes — one per publisher). Booting the app
surfaced a real error from `@dapr/dapr`'s `SubscriptionManager`: *"The topic 'dws.events' is
already subscribed to PubSub 'pubsub', there can be only one topic registered"* — the SDK allows
only one subscription per `(pubsubName, topic)` pair with no matching `route`, so two decorated
classes both targeting the bare topic crash at startup. The actual shape: `DwsEventsSubscriber`
owns the single `@DaprPubSub(pubsubName, topic)` method, decodes the envelope, runs it through the
idempotency guard (D4), and dispatches by `envelope.type` to whichever of two plain (non-decorated)
`@Injectable()` services — `ControllerEventsHandler` or `OrchestratorEventsHandler` — reports
`canHandle(type)` true, calling its `process(tx, envelope)`. This keeps the S2.3/S2.4 split the
epic describes (one service per publisher's event set) without violating the one-subscription
constraint. `pubsubName`/`topic` come from `process.env` read at class-definition time (the
decorator's arguments must be statically resolvable, not DI-resolved) — documented inline since
it's the one place config bypasses the DI container.
- *Alternative considered before the crash — one decorated method per event type*: also rejected;
  same one-subscription-per-topic constraint applies regardless of how many classes or methods
  target the bare `(pubsubName, topic)` pair with no `route`.
- *Alternative — give each handler class a distinct Dapr `route`*: rejected; `route` is for
  content-based routing rules Dapr evaluates against CloudEvent metadata, which would require
  publishers to tag events accordingly — out of scope for a read-only consumer and unrelated to our
  actual dispatch key (`envelope.type`, which we already have in hand after decoding).

### D4: Idempotency via `processed_events` inside a Drizzle transaction
Every handler's write path is: open a Drizzle transaction (`db.transaction(async (tx) => {...})`
using the injected `'DB'` client) → `INSERT INTO processed_events (event_id, processed_at) VALUES
(...) ON CONFLICT (event_id) DO NOTHING RETURNING event_id` → if no row returned, the event was
already processed, return without touching domain tables → otherwise perform the domain upsert in
the same transaction, then let the transaction commit both writes atomically. A crash between the
`processed_events` insert and the domain write rolls back both (transactional), so replay after a
crash re-attempts the full write — this is the "insert-and-check in the same transaction" behavior
the epic requires.

### D5: Out-of-order-safe upserts — monotonic status, "first/last known" timestamps
At-least-once, unordered delivery means `instance.completed` can arrive before `instance.started`
is durably written, and any event can be redelivered. Each domain upsert therefore uses Postgres
`ON CONFLICT (<key>) DO UPDATE` with per-column merge rules, not a blind overwrite:
- **Identity columns** (`workflow`, `version`, `app_id`/`orchestrator_app_id`, `task_name`, etc.):
  always safe to overwrite with the incoming value — they're immutable for a given key across all
  events that reference it.
- **`started_at`**: `COALESCE(<table>.started_at, excluded.started_at)` — set once, from whichever
  event (in arrival order) carries it first; never overwritten once known.
- **`ended_at`**: `COALESCE(excluded.ended_at, <table>.ended_at)` — only `completed`/`failed`
  events carry it; a later `started`-only write (there isn't one, but the pattern generalizes) must
  never null it back out.
- **`status`**: statuses are ranked (`started` < `completed`/`failed`, both terminal and mutually
  exclusive in practice). The `DO UPDATE SET status = ...` clause is a `CASE` that keeps the
  existing status if it's already terminal, otherwise takes the incoming value:
  `CASE WHEN <table>.status IN ('completed','failed') THEN <table>.status ELSE excluded.status END`.
  This makes the upsert commutative regardless of arrival order: `completed` then `started` ends
  with `status='completed'`; `started` then `completed` also ends with `status='completed'`.
- Same pattern applies to `deployments.status`/`drained_at` (`applied`/`failed` vs. `drained` are
  independent axes — `drained_at` uses the same `COALESCE` rule, `status` uses the same ranked
  `CASE`) and to `workflow_definitions.status`.
- `task_events` is append-only (one row per received task-lifecycle event, primary-keyed on the
  CloudEvent `id` itself — no separate id generator needed since it's already globally unique), so
  it needs no merge logic: `INSERT ... ON CONFLICT (id) DO NOTHING` is sufficient, and the
  `processed_events` guard already prevents a duplicate insert attempt in the common case.
  `type` stores the payload's `taskType` (`call`/`switch`/`set`/…); `status` stores the lifecycle
  phase (`started`/`completed`/`failed`) derived from the event's `type` suffix.

### D6: Migrations — `drizzle-kit generate` only, run on boot in dev
`dws-admin/drizzle/` holds only generated SQL migration files (never hand-edited). A small boot
step (`main.ts`, gated by `RUN_MIGRATIONS_ON_BOOT=true`, default true in `docker-compose`/dev,
false in a hypothetical prod deploy) runs `drizzle-orm`'s `migrate()` against the same Postgres
connection before `NestFactory.listen`, plus a standalone `pnpm db:migrate` script for CI/manual
use. `drizzle-kit push` stays a documented local-dev-only shortcut for iterating on the schema
before generating a migration, never a shipped step.

### D7: Health check — DB only, for this epic
`@nestjs/terminus` exposes `GET /health` running one indicator: a raw `SELECT 1` through the
injected `'DB'` client. A Dapr sidecar-reachability check is left out — Dapr's own `/v1.0/healthz`
is the sidecar's own liveness surface, and a false-negative app health check from a slow sidecar
would be more disruptive than useful this early.

### D8: Two HTTP ports — Nest's own app port and a separate Dapr callback port
**Discovered during S2.5's manual verification.** `@dbc-tech/nest-dapr`'s `DaprModule` runs its own
`DaprServer` (from `@dapr/dapr`) as a second, independent HTTP listener in the same process — it is
not mounted into Nest's Express app. Configuring both to the same port silently loses the race (one
binds, `GET /health` then hits whichever server won and 404s). `ConfigModule` therefore exposes two
distinct values: `port` (Nest's app — `/health`, later the read API) and `dapr.appPort` (the
`DaprServer`'s own port, env `DAPR_APP_PORT`, default `3001`). `dapr run --app-port` must target
`dapr.appPort`, not `port` — documented in the README's local-dev section and `.env.example`.
A second, related finding: `@dapr/dapr`'s `DaprClient.awaitSidecarStarted` polls until a real Dapr
sidecar answers before `DaprLoader.onApplicationBootstrap()` resolves, and Nest's `app.listen()`
runs that lifecycle hook *before* actually binding its own HTTP port — so **the whole app,
including `/health`, is unreachable until a Dapr sidecar is present**, even though `/health` itself
doesn't depend on Dapr. This was verified directly: `node dist/main.js` alone hangs with `/health`
connection-refused; the same binary under `dapr run` starts fully within ~1s of the sidecar
answering, and `GET /health` returns `200`. This is inherent to the library, not a bug to fix here;
called out so a future SRE doesn't mistake it for a startup-probe failure.
- *Alternative — merge the two servers*: not possible without forking `@dbc-tech/nest-dapr`;
  `DaprServer` is a framework-agnostic raw HTTP server with no knowledge of Nest's router.

## Risks / Trade-offs

- **[D2's envelope-unwrapping shape]** → verified two ways: against the installed
  `@dbc-tech/nest-dapr`/`@dapr/dapr` source (see D2), and live — a real `daprd` (installed via the
  Dapr CLI, `pubsub.in-memory` component) publishing a `definition.created` and an
  `instance.started` CloudEvent to a running `dws-admin` instance, both landing correctly in the
  read model, including a same-`id` replay producing no duplicate row. No further action needed.
- **[Six-plus event types funneled through one `@DaprPubSub` entry point per handler class]** →
  a bug in the type-switch silently drops an event type. Mitigation: one unit test per event type
  asserting it reaches the correct upsert method; exhaustive switch with a default branch that logs
  and acks unknown types (never throws — an unknown/future event type must not crash the
  subscription).
- **[Migrations run on boot by default]** → concurrent replica boot could race on the same
  migration. Mitigation: `drizzle-orm`'s `migrate()` takes an advisory-lock-backed migrations table
  by default (Postgres), so concurrent runners serialize safely; documented in the README's local
  dev section since this epic targets single-instance local dev only.
- **[No dead-letter or retry-exhaustion handling]** → a handler that keeps throwing (e.g. DB down)
  relies entirely on Dapr's own redelivery/backoff. Mitigation: acceptable for this epic (no SLA
  yet); explicitly called out as an Open Question below for a later epic.

## Migration Plan

1. Scaffold `dws-admin/` (S2.1) — `pnpm build`/`pnpm test` green on an empty-but-wired project
   before adding schema or handlers, so later steps land on a known-good base.
2. Schema + first migration (S2.2) — `drizzle-kit generate`, commit the output, verify
   `pnpm db:migrate` applies cleanly against a fresh `docker-compose` Postgres.
3. Controller event handlers + idempotency/out-of-order tests (S2.3).
4. Orchestrator event handlers, reusing the same shared decoder/transaction helper (S2.4).
5. Health check + `docker-compose.yml` + README local-dev section (S2.5).
- **Rollback**: this is a new, additive component with no consumers yet (no REST API ships); if
  reverted, deleting `dws-admin/` and its migrations has no impact on `dws-controller`/
  `dws-orchestrator`, which remain unaware of any subscriber.

## Open Questions

- Whether unknown/future `envelope.type` values should be logged at `warn` or `debug` — left as an
  implementation-time call, not a contract decision.
- Retry/dead-letter policy beyond Dapr's own redelivery is deferred to whichever later epic adds
  operational SLAs for the read model.
- Whether `dws-admin` needs its own Dapr `Subscription` CRD/manifest checked into the repo (the
  other components don't ship one either) — left out of scope here, consistent with Epic 1's choice
  to document the `pubsub` component as a deployment prerequisite rather than provision it.
