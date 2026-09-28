# nightona

Self-hosted [Nightona](https://github.com/nightona-co/nightona) stack, wired in as Omnigent's
`daytona` cloud sandbox provider, so sandboxed Omnigent sessions run on infrastructure you host
instead of Daytona's cloud API.

## Why Nightona, and why through the `daytona` provider

Omnigent's server-managed sandbox providers (`sandbox.provider` in its server config) are
`modal`, `daytona`, `blaxel`, `islo`, `e2b`, `gensee` — there is no native `docker` or
`opensandbox` provider yet (tracked upstream: [omnigent-ai/omnigent#8372](https://github.com/omnigent-ai/omnigent/issues/8372)).

Daytona went closed-source in June 2026; its last open release (v0.190.0, AGPL-3.0) is frozen
and unmaintained. [Nightona](https://github.com/nightona-co/nightona) is a community fork
continuing from that same release under the same API. Omnigent's `daytona.py` launcher does not
hardcode Daytona's cloud endpoint — it just constructs `daytona.Daytona()`, which reads
`DAYTONA_API_KEY` / `DAYTONA_API_URL` / `DAYTONA_TARGET` from the process environment. Pointing
those at a self-hosted Nightona instance instead of `https://app.daytona.io/api` makes Omnigent
talk to Nightona with zero Omnigent code changes.

**Not yet verified**: that Nightona's HTTP API has stayed byte-compatible with the frozen
v0.190.0 protocol as the fork has evolved. Confirm against Nightona's own release notes before
relying on this outside local testing. The known upstream limitation also carries over
unchanged: Omnigent's launcher hardcodes sandbox size at 2 CPU / 4 GiB regardless of which
`daytona`-compatible server answers it ([omnigent-ai/omnigent#7460](https://github.com/omnigent-ai/omnigent/issues/7460)).

## Files here

| File | Purpose |
|---|---|
| `docker-compose.yaml` | Vendored from `nightona-co/nightona` `docker/docker-compose.yaml` (main, pinned 2026-09-28). Dev-only: hardcoded secrets, per upstream's own README. |
| `dex/config.yaml`, `otel/otel-collector-config.yaml`, `pgadmin4/servers.json`, `pgadmin4/pgpass` | Supporting config the compose file mounts. Also vendored as-is. |
| `omnigent-sandbox.yaml` | The `sandbox:` block to merge into the Omnigent **server's** own `/data/config.yaml` (not this repo's `.omnigent/*/config.yaml` — those configure per-agent process sandboxing, a separate, unrelated concept). |
| `.env.example` | Env vars the Omnigent server process needs, read directly by the `daytona` SDK client. |

## Bring up the local Nightona stack

```sh
cd agent-sandbox/nightona
docker compose up -d
```

Resolve `*.proxy.localhost` to `127.0.0.1` if you'll use sandbox preview URLs (not required just
to create/run a sandbox):

```sh
# from the upstream nightona repo's scripts/setup-proxy-dns.sh, or manually add a
# dnsmasq/hosts entry for proxy.localhost -> 127.0.0.1
```

Wait for containers to report healthy, then open the dashboard:

- Dashboard: http://localhost:3000/dashboard — login `dev@daytona.io` / `password`
- Confirm the default snapshot is active: http://localhost:3000/dashboard/snapshots
- PgAdmin: http://localhost:5050 (`dev@daytona.io` / `pgadmin`)
- Registry UI: http://localhost:5100
- MinIO console: http://localhost:9001 (`minioadmin` / `minioadmin`)
- Jaeger: http://localhost:16686

In the dashboard, create an API key (organization settings → API keys) — this is your
`DAYTONA_API_KEY`.

## Configure Omnigent

1. Copy `.env.example` to wherever the Omnigent server process reads its environment from,
   fill in the API key from the step above, and export it before starting the server:

   ```sh
   export DAYTONA_API_KEY=<key from Nightona dashboard>
   export DAYTONA_API_URL=http://localhost:3000/api
   ```

2. Merge `omnigent-sandbox.yaml`'s `sandbox:` block into the Omnigent server's
   `/data/config.yaml`, setting `sandbox.server_url` to that Omnigent server's own reachable
   URL (not Nightona's) and `sandbox.daytona.env` to whatever credentials your sandboxed agent
   needs inside the sandbox itself (e.g. `ANTHROPIC_API_KEY` for Claude Code).

3. Restart the Omnigent server so it picks up both the new env vars and the config change.

## Test it

From a machine that can reach both the Nightona API and the Omnigent server:

```sh
omnigent sandbox create --provider daytona
```

This should create a sandbox via Nightona instead of Daytona's cloud API. Confirm it landed in
your local stack, not the real Daytona cloud:

```sh
# Dashboard: http://localhost:3000/dashboard/sandboxes should show the new sandbox.
```

Then run a real session against it:

```sh
omnigent sandbox connect --provider daytona --sandbox-id <id-from-create> --server https://your-omnigent-server-host
```

Sanity checks if something's off:

- `docker compose ps` in this directory — all services `Up`/healthy, especially `api`, `db`,
  `dex`, `runner`.
- `docker compose logs -f api` — auth or DB errors show up here first.
- Sandbox creation fails immediately with an auth error → re-check `DAYTONA_API_KEY` /
  `DAYTONA_API_URL` are exported in the *Omnigent server's* environment, not just your shell.
- Sandbox creates but the agent can't reach it → check the `*.proxy.localhost` DNS step above.
- Tear down: `docker compose down -v` (the `-v` also drops the Postgres/MinIO/Dex volumes —
  drop it if you want to keep data across restarts).

## Confirm before use

- Nightona API compatibility with the frozen Daytona v0.190.0 protocol, as noted above.
- `DEFAULT_SNAPSHOT` in `docker-compose.yaml` (`ghcr.io/nightona-co/sandbox:0.6.0-slim`) is
  reachable from wherever the `runner` container pulls images from.
- Resource limits: the compose file sets `RESOURCE_LIMITS_DISABLED=true` on the runner (upstream
  note: cgroups can't be partitioned in a DinD environment without the Docker socket mounted) —
  sandboxes here are not resource-isolated from each other the way a production Nightona/Daytona
  deployment would be.
