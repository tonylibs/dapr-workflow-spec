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
- **Verification risk**: this shape is inferred from the library's documented examples, not a local
  install (no `node_modules` in this environment). Task list includes an explicit step to confirm
  the exact runtime payload shape against the installed package once `pnpm install` runs, and to
  adjust the shared decoder if the outer/inner nesting differs from what's documented here.

### D3: One `DaprEventsModule` provider per publisher, one `@DaprPubSub` handler per event type
Two providers, `ControllerEventsHandler` and `OrchestratorEventsHandler`, each with one method per
`type` decorated `@DaprPubSub('pubsub', 'dws.events')` — `@dbc-tech/nest-dapr` dispatches by
Dapr subscription route, and since every event lands on the same topic, we route on `envelope.type`
inside a single shared entry method per handler class rather than registering six-plus decorated
methods that would each receive *every* message and filter internally. Concretely: one
`@DaprPubSub(pubsubName, topic)` method per handler class that switches on `envelope.type` and
dispatches to a private per-type method. `pubsubName`/`topic` come from `ConfigService`, read once
at module init and passed as decorator arguments (the decorator's arguments must be
statically resolvable at class-definition time, so `ConfigModule` values are read via a small
`daprConfig()` helper evaluated at import time from `process.env`, not through DI — documented
inline since it's the one place config bypasses the DI container).
- *Alternative — one decorated method per event type*: rejected; `@DaprPubSub` subscribes per
  `(pubsubName, topicName, route)`, and Dapr subscriptions are per-topic, not per-message-type, so
  N decorated methods on the same topic would each need their own `route`, which doesn't correspond
  to anything in our single-topic contract — a type-name filter inside one handler matches the
  actual routing key (`envelope.type`) we have.

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

## Risks / Trade-offs

- **[D2's envelope-unwrapping shape is unverified against a real install/runtime]** → if wrong,
  every handler silently reads `undefined` fields. Mitigation: single shared decoder (D2) so a
  fix is one-file; task list requires a test that constructs the exact JSON shape from a captured
  `docs/events.md` example and asserts the decoder extracts `id`/`type`/payload correctly, plus a
  manual `dapr publish` smoke test against a running instance before calling S2.3/S2.4 done.
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
