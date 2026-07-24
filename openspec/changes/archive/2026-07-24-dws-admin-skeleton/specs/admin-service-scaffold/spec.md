## ADDED Requirements

### Requirement: dws-admin is an independently toolchained NestJS component
`dws-admin` SHALL exist as a top-level directory sibling to `dws-controller`, `dws-orchestrator`,
`dws-call-http`, and `dws-call-openapi`, with its own `package.json`, its own dependency set, and
no shared build step with any other component — consistent with the monorepo's "no shared build
system" convention.

#### Scenario: Building dws-admin in isolation
- **WHEN** a developer runs `pnpm install && pnpm build` inside `dws-admin/` on a clean checkout
- **THEN** the build succeeds without requiring any other component's directory to be built first

#### Scenario: Standard Nest CLI and Jest conventions
- **WHEN** a developer inspects `dws-admin/package.json` scripts
- **THEN** `build` invokes `nest build`, a `start:dev` script invokes `nest start --watch`, and
  `test` invokes Jest via `@nestjs/testing` — not `dws-call-openapi`'s Fastify/vitest script names

### Requirement: Module layout separates config, store, events, and read APIs
`dws-admin` SHALL be composed of a `ConfigModule`, a `StoreModule`, a `DaprEventsModule`, a
`WorkflowsModule`, and an `InstancesModule`, each a distinct Nest module, so that later epics can
extend the read-API modules without touching event ingestion or storage wiring.

#### Scenario: Config drives store and event wiring, not hardcoded values
- **WHEN** `StoreModule` and `DaprEventsModule` are constructed at application bootstrap
- **THEN** the Postgres connection URL, the Dapr pub/sub component name, and the `dws.events` topic
  name are all resolved from `ConfigModule`-sourced environment variables, not literals in the
  module source

#### Scenario: Read-API modules exist without exposing routes
- **WHEN** the application starts with `WorkflowsModule` and `InstancesModule` imported
- **THEN** no HTTP route under either module is registered (no controller methods decorated with
  `@Get`/`@Post`/etc. yet), and starting the app does not error because of their emptiness

### Requirement: Health check reports database connectivity
`dws-admin` SHALL expose `GET /health` using `@nestjs/terminus`, and that endpoint SHALL report
unhealthy when the read-model database is unreachable.

#### Scenario: Database reachable
- **WHEN** `GET /health` is called while the configured Postgres instance is reachable
- **THEN** the response has HTTP status 200 and indicates the database indicator is up

#### Scenario: Database unreachable
- **WHEN** `GET /health` is called while the configured Postgres instance is unreachable
- **THEN** the response has HTTP status 503 and indicates the database indicator is down

### Requirement: Local development is reproducible via docker-compose and dapr run
`dws-admin/` SHALL include a `docker-compose.yml` that starts a local Postgres instance suitable
for `pnpm start:dev`, and the component's README SHALL document the `dapr run` invocation
(app-id, app port, Dapr HTTP/gRPC ports) needed to receive events from a locally-running
`dws-controller`/`dws-orchestrator`.

#### Scenario: docker-compose provides a working local database
- **WHEN** a developer runs `docker-compose up -d` inside `dws-admin/` on a clean checkout
- **THEN** a Postgres instance becomes reachable on the connection details the README documents,
  with no manual database creation step required beyond that command

#### Scenario: README documents the dapr run invocation
- **WHEN** a developer follows the README's local-dev section
- **THEN** it specifies the exact `dapr run` command (app-id, `--app-port`, Dapr ports) needed to
  start `dws-admin` under a Dapr sidecar, mirroring the pattern already documented for
  `dws-orchestrator`
