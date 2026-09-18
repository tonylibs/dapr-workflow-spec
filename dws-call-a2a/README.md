# dws-call-a2a

Prebuilt step image for `call: a2a` tasks in the DWS platform. One image serves every `call: a2a`
step; all behavior is defined by environment configuration (see `CLAUDE.md` "Env var contract" for
the full table). Design authority:
[`docs/adr/0004-call-a2a-runner-design.md`](../docs/adr/0004-call-a2a-runner-design.md).

## Contract

Standard step-service HTTP contract:

- `POST /run` — body is the current workflow data JSON; an empty body is treated as `{}`. Response
  body is the new workflow data.
- `GET /healthz` — `{"status": "ok", "task": "<TASK>"}`.
- `502` for retryable upstream/transport failures (connection errors, JSON-RPC error responses,
  agent responses that fail protocol validation). `400` for request-time validation failures (a
  malformed request body, or a `PARAMETERS` jq evaluation that doesn't produce a usable object).
  `500` for anything else unexpected.

**No `OUTPUT=replace|merge`.** Unlike the HTTP-shaped call kinds (`call: http`/`openapi`/
`asyncapi`), the OWS `A2AArguments` schema carries no data-flow output concept to shape — `with`
is just `method`/`agentCard-or-server`/`parameters`. Result shaping here is a single *fixed* policy
(see "Result shaping" below), not an author-configurable choice, so this runner always returns the
shaped JSON-RPC result as the new workflow data, in place.

## Target resolution

Exactly one of these two env vars is set (never both, never neither — the process fails to start
otherwise):

- `AGENT_CARD_URL` — the runner resolves the agent's discovery card itself at boot (via
  `A2ACardResolver`, over its own authenticated HTTP client — never by handing a bare URL to the
  SDK's `create_client`, which would resolve the card over a client this runner doesn't control),
  then talks to whichever interface in the card's `supportedInterfaces` declares the `JSONRPC`
  protocol binding. Boot fails, naming the bindings that *were* found, if no JSONRPC interface
  exists.
- `SERVER_URL` — talks to this URL directly. No card fetch, no auth-requirement check (there's
  nothing to check the requirement against).

`AGENT_CARD_SHA256` (optional, operator-set only, never set by the controller — there is no field
in the OWS DSL for a card checksum) adds a stricter boot check: hash the fetched card bytes, fail
boot on mismatch. Unlike `dws-call-openapi`/`dws-call-asyncapi`'s document-hash env vars, this one
is not something a workflow author can express in the DSL — it's a manual, deploy-time knob for
operators who want to pin a specific card version.

## Authentication

Two separate HTTP conversations can each be authenticated independently: the card fetch, and the
JSON-RPC RPC calls. By default the same credential is reused for both (`CARD_AUTH_*` absent);
declare `CARD_AUTH_SCHEME` explicitly only when the card endpoint needs different credentials than
the RPC endpoint.

RPC auth (`AUTH_SCHEME=none|basic|bearer|oauth2`) is applied by a runner-owned mechanism, not the
SDK's built-in `AuthInterceptor` (which never emits HTTP Basic). `oauth2` is handled entirely by
Dapr: the outbound RPC request is rerouted through the sidecar's service-invocation proxy
(`http://localhost:{DAPR_HTTP_PORT}/v1.0/invoke/{OAUTH_ENDPOINT}/method...`) so Dapr's OAuth2
client-credentials middleware injects the real token — this runner never sees or manages one.

Card auth (`CARD_AUTH_SCHEME=none|basic|bearer`, no `oauth2` — the card fetch is plain HTTPS, not a
Dapr service invocation) is validated against the resolved card's declared security requirements at
boot, per a 5-case decision table (see `docs/adr/0004-call-a2a-runner-design.md` Decision 4 and
`CLAUDE.md`'s notes on the one case the ADR's table doesn't explicitly cover). A card that requires
authentication the runner isn't configured to provide fails the process at boot, not on the first
request.

## Result shaping

The response body is the JSON-RPC result (a `Task` or a bare `Message`), normalized to the OWS/A2A
wire vocabulary's lowercase state strings (`working`, `input-required`, `auth-required`, ...,
including the real-but-underdocumented `unknown` state — see `CLAUDE.md`), with `history` stripped
unless `INCLUDE_HISTORY=true` (a debug-only escape hatch; never set by the controller). A task in
`input-required` or `auth-required` state is a **successful** `/run` response (HTTP 200) — the
author's own `switch`/`then`/`listen` decides what to do next; only a genuine transport or protocol
failure is a step failure.

## Concurrency and cold start

**No concurrency cap.** A `for` over a large collection with a `call: a2a` body fans out to one
agent invocation per item, and Knative scales this step's pods out to meet demand with no
`maxScale`/`containerConcurrency` limit imposed by this image or its deployment. Pacing is left
entirely to the workflow author's `for`/`fork` shape (ADR 0004 Consequences: "not decided by this
ADR" whether the controller should cap this — as of writing, it doesn't). Agent calls are typically
slower, more expensive, and more likely to be externally rate-limited than the HTTP APIs the
sibling call kinds target, so an unbounded fan-out here is a more direct cost/throttling risk than
for `call: http`/`openapi`.

**Cold start.** Like every scale-to-zero step service, the first invocation after a scale-to-zero
period pays Python interpreter + dependency import + boot-time card resolution latency. The
documented mitigation, if this proves unacceptable for a given deployment, is pinning `minScale: 1`
on the step's Knative Service (a chart/controller-level setting, not something this image controls)
— matching the same escape hatch the other step images document.

## Retry idempotency (`messageId`)

Retries (the author's `try`/`retry`, or the step-service contract's own `502`-triggers-orchestrator-
retry) can invoke the same logical `message/send` more than once. A deterministic `messageId` gives
a *cooperative* agent the ability to recognize a retry as the same request rather than a second,
independent one — see `docs/adr/0004-call-a2a-runner-design.md` Decision 8 for the full rationale,
and `CLAUDE.md`'s "Known gap" section for why this doesn't fully work yet: the identifiers the
derivation needs (workflow-instance-id, `for`-loop iteration index) aren't currently threaded onto
a step service's `/run` request by `dws-orchestrator`, for any runner, not just this one. This
runner reads them from two optional headers (`X-Dws-Workflow-Instance-Id`, `X-Dws-Iteration-Index`)
it defines as a forward-compatible extension point; until the orchestrator sends them, calls fall
back to a fresh random id per attempt (logged), meaning retries are **not currently deduplicated**
against a cooperative agent. This is a known, documented gap, not a silent one.

## Local development

```shell
cd dws-call-a2a
uv sync
uv run pytest                 # all three test tiers
uv run python -m dws_call_a2a # requires AGENT_CARD_URL/SERVER_URL etc. set in the environment
```

See `CLAUDE.md` for the full toolchain rationale, gate command, and env var contract.
