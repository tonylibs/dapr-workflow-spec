# ADR 0004: `call: a2a` Runner Design

- **Status:** Accepted
- **Date:** 2026-09-15
- **Context:** [`docs/roadmaps/openworkflow-features.md`](../roadmaps/openworkflow-features.md)
  Phase 5.5 (A2A protocol), split out of Phase 5 by §4d
- **Related:** [ADR 0001](0001-workflow-runtime-v2-decisions.md) Decision 2 (uniform Step layer —
  every function image presents plain HTTP), whose "new function images are born plain-HTTP"
  corollary in
  [`docs/roadmaps/workflow-runtime-architecture-roadmap.md`](../roadmaps/workflow-runtime-architecture-roadmap.md)
  this is the first instance of; Phase 4's `workflow-auth` (`use.secrets` → `secretKeyRef`
  projection, OAuth2 via Dapr middleware), which Decision 4 builds on

## Context

`call: a2a` is the last unimplemented call protocol in Open Workflow Specification DSL 1.0. Phase 5
deliberately deferred it (§4d) on the grounds that its shape was "genuinely different (agent
task/artifact lifecycle, not a single request/response or publish)" and that it had received none of
the design work gRPC and AsyncAPI got.

Reading the actual specifications inverts that assessment. The OWS `a2a` call is a **thin JSON-RPC
passthrough**: `with` carries `method` (an enum of 11 A2A JSON-RPC methods), one of `agentCard`
(`externalResource`) or `server` (`endpoint`), and free-form `parameters` passed through
unvalidated. Per the spec, the output is the JSON-RPC result; failures raise the standard
`…/errors/runtime` type; and for `message/send`/`message/stream` runtimes must default
`message.messageId` to a uuid and `message.role` to `user`.

Crucially, **there is no runtime-managed task lifecycle**. A2A tasks do progress through states
(`submitted` → `working` → `input-required`/`auth-required` → `completed`/`failed`/`canceled`), but
OWS binds one RPC per task invocation. Polling, resumption after `input-required`, and escalation on
`auth-required` are composed by the workflow author out of `switch`, `wait`, and
`then: <taskName>` — machinery Phases 2 and 3 already shipped.

That makes `call: a2a` the *cheapest* remaining protocol, not the most expensive: no document
parsing, no payload schema validation, and — unlike every other call kind — no Kubernetes resource
to synthesize.

## Decision 1: Python 3 / FastAPI, plain HTTP, official SDK

`dws-call-a2a` is a Python 3 image built on FastAPI, serving the standard step-service contract
(`POST /run`, `GET /healthz`, `OUTPUT=replace|merge`, empty body treated as `{}`), with no Dapr
Workflow SDK and no `Run` activity registration.

Python because the official `a2a-sdk` owns protocol conformance we would otherwise reimplement —
and reimplementation is the specific risk here, since the protocol's wire details are documented
unevenly (see Decision 3). This makes Python a sixth stack in a repo that already carries Java, Go,
Node/TypeScript and .NET; the polyglot one-image-per-protocol pattern is established, so the cost is
toolchain and CI, not architectural.

FastAPI rather than Flask because the SDK client is async end to end (`httpx.AsyncClient`,
`async for chunk in client.send_message(...)`, `await client.close()`); a WSGI server would force an
`asyncio.run()` per request, losing connection reuse and making any future streaming work painful.
The SDK ships a `a2a-sdk[fastapi]` extra.

Plain HTTP rather than a Dapr Workflow activity worker per ADR 0001 Decision 2's forward-looking
corollary. A plain-HTTP image is dispatched by `dws-orchestrator`'s existing `CallServiceActivity`
under v1 and by `dws-step` under v2, so it needs none of the dual-interface migration window the
Go images require.

**Card resolution is explicit.** `create_client(agent: str | AgentCard, …)` accepts a bare URL, in
which case the SDK resolves the agent card using an `httpx` client the caller does not control. The
runner therefore always resolves the card itself via `A2ACardResolver` over a runner-owned client,
then passes the resulting `AgentCard`. This is the only place card-fetch credentials can be applied
(Decision 4), so the bare-URL form is prohibited.

## Decision 2: `message/send` and `tasks/get` only

Of the 11 methods in the OWS enum, this version supports `message/send` and `tasks/get`. Everything
else — `message/stream`, `tasks/resubscribe`, `tasks/list`, `tasks/cancel`, the four
`tasks/pushNotificationConfig/*` methods, and `agent/getAuthenticatedExtendedCard` — is rejected at
compile time.

These two cover the dominant workflow shape: dispatch work, then poll for completion in an
author-written loop. The streaming pair is the expensive exclusion: OWS requires their output be "a
sequentially ordered array of all the result objects", which means aggregating a Server-Sent Events
stream until the terminal event and holding a connection open for the duration — the only long-hold
case in the protocol, and the largest single piece of implementation work.

Rejecting at compile time rather than at runner startup gives the author the error when they submit
the definition, not when the pod fails to become ready.

## Decision 3: Normalize the protocol dialect; the wire form is lowercase

Three spellings of the same concepts coexist and must not be conflated:

| Layer | Spelling |
|---|---|
| OWS `method` enum | v0.3 — `message/send`, `tasks/get` |
| SDK symbols | v1.0 — `SendMessage`; `Role.ROLE_USER` is a Python enum *member name* |
| **JSON-RPC wire** | **lowercase** — `"working"`, `"input-required"`, `"auth-required"`, and an `"unknown"` state the prose never mentions; parts carry a `kind` discriminator (`"text"`/`"file"`/`"data"`); `role` is `"user"`/`"agent"` |

The A2A specification's prose is generated from protobuf and renders state names as
`TASK_STATE_WORKING`; the generated TypeScript types in `a2a-js` give the wire union explicitly.
Workflow authors write `switch` conditions against the **wire** form.

The runner selects the entry of `supportedInterfaces` whose `protocolBinding` is `JSONRPC`, emits
the dialect that entry's `protocolVersion` declares, and fails as a configuration error when no
JSONRPC interface exists rather than attempting a transport it cannot speak.

`"unknown"` is a real state; author-facing documentation must show a default branch, and a `switch`
over task states without one will silently fall through.

## Decision 4: Authentication — the OWS policy applies, the agent card validates

Credentials always come from the OWS `authentication` policy, resolved through Phase 4's
`use.secrets` → `secretKeyRef` → env machinery, and applied by a runner-owned request interceptor.
The agent card is consulted only to validate that configuration.

This follows from a property of the protocol: **an agent card never carries credentials.** Its
`securitySchemes`/`security` fields declare *which* scheme is required — the A2A spec states
credentials are obtained "through an out-of-band process" and sent "in protocol-appropriate headers
or metadata for every A2A request". OWS's `authentication` block is that out-of-band channel. A card
therefore cannot be a credential source, only a statement of requirements.

The alternative — resolve the OWS policy onto a card-declared scheme name and drive the SDK's
`AuthInterceptor`, which looks credentials up by scheme name through a `CredentialService` — was
rejected on four grounds: `AuthInterceptor` emits Bearer and apiKey-in-header only and will not emit
HTTP **Basic**, which OWS's `authenticationPolicy` permits; Phase 4 already routes OAuth2
`client_credentials` through Dapr middleware, so the runner sees an injected token and an
interceptor would contend for the same header; a card declaring two schemes of one type leaves the
choice ambiguous; and it inverts this repo's established shape, in which the compiler decides auth
and the runner applies it.

Card validation is a boot-time decision table:

| Case | OWS `authentication` | Card `security[]` | Behavior |
|---|---|---|---|
| 1 | declared | a scheme of the same type | apply; proceed |
| 2 | declared | only schemes of another type | apply; **warn**; proceed |
| 3 | absent | empty or absent | proceed unauthenticated |
| 4 | absent | non-empty | **fail at boot**, naming the required scheme |
| 5 | any | no card (`server` used) | proceed; no check possible |

Case 4 is the value of the check: it converts a runtime 401 buried inside a workflow error into a
Knative readiness failure at deploy time, matching how `dws-call-openapi`/`dws-call-asyncapi`
already fail fast on a `DOC_SHA256` mismatch. Case 2 warns rather than fails because `security` is
an array of requirement objects with OR semantics, and strictness would reject valid multi-scheme
agents. The check lives in the runner, not the controller, because the controller never fetches the
card.

**Two endpoints, one credential by default.** The card fetch and the RPC calls are separate HTTP
conversations, and the OWS schema can authenticate each independently
(`agentCard.endpoint.authentication` and `server.authentication`). In practice an organization that
protects its card protects both with the same token, so the runner reuses the RPC credential for the
card fetch unless card-specific credentials are explicitly declared — a documented default rather
than an accident.

## Decision 5: `call: a2a` synthesizes no Kubernetes resource

`call: asyncapi` synthesizes a version-scoped Dapr binding `Component`; `call: http`/`call: openapi`
under an OAuth2 policy synthesize an `HTTPEndpoint`, a middleware `Component`, and a scoped
`Configuration`. `call: a2a` synthesizes none of these. The agent card is resolved by the runner at
boot over ordinary HTTPS, and nothing about the protocol requires a cluster-side object.

The controller's compile branch therefore does only: validate the method against Decision 2's
subset, require exactly one of `agentCard`/`server` (treating `agentCard` as ignored when `server`
is set, per the OWS schema), project credentials as secret references, and pin the step's
environment.

## Decision 6: Result shaping — strip `history`, return non-terminal states as data

A returned `Task` carries `status`, `artifacts[]`, and `history[]`. `history` is omitted from the
runner's output unless explicitly opted into, for two reasons: it replays the dispatched message,
which — if `parameters` were built with Phase 4's `$secrets` jq extension — would place credentials
into the workflow data document and into `dws-admin`'s read model; and artifacts plus history can
carry base64 file parts, and the whole object becomes workflow state in the Dapr state store.

A task whose `status.state` is `input-required` or `auth-required` is returned as a **successful
result**, not a step failure. Neither is a JSON-RPC failure, so per OWS the result object is the
output, and the author's `switch` decides whether to resume the task with a second `message/send`,
escalate via `listen`, or fail. Translating them into step failures would remove that ability; this
is recorded explicitly so a later change does not reclassify them as errors.

## Decision 7: The agent card is not integrity-pinned

`dws-call-openapi` and `dws-call-asyncapi` pin their documents with a SHA-256 because an API
contract is versioned and stable. An agent card is a live discovery document that changes whenever
the agent redeploys, so a hard pin would break every consuming workflow on an unrelated agent
release. The card is fetched unpinned by default, with an optional hash for callers wanting stricter
behavior, and cached at boot — a changed card requires a pod restart, as with the sibling runners.

`A2ACardResolver.get_agent_card()` also accepts a `signature_verifier` callable for the card's JWS
`signatures`. Verifying card signatures is a follow-up, not part of this decision.

## Consequences

- **A sixth language stack.** Python brings its own toolchain choice, package `CLAUDE.md`, gate
  command, Dockerfile, CI workflow, `release-please-config.json` entry, `component-release.yml`
  dropdown entry, and `charts/dws/values.yaml` pin.
- **Knative cold start is worse than Go or Node.** Step services scale to zero, so cold start is
  per-step latency. It must be measured, with `minScale: 1` as the documented mitigation if
  unacceptable.
- **`dws-orchestrator` needs no change.** OWS mandates the standard `runtime` error type for `a2a`
  failures, which the existing step-service `502`/`500` split already produces — unlike
  `call: asyncapi`, which required a new `ErrorKind.VALIDATION` marker.
- **Fully verifiable without a cluster.** Unlike Phase 4's tasks 6.2/6.3 and Phase 5's task 8.2,
  every behavior above can be tested in CI: unit tests with a mock transport, an in-repo scripted
  fake A2A server for multi-turn state, and — importantly — a conformance job running the official
  `a2a-sdk` server. That third tier exists because a hand-written mock encodes the same
  misreading as the runner and goes green; the SDK is the only oracle that breaks that circularity.
- **Open: retry idempotency.** OWS `try`/`retry` re-invokes the step. A fresh `messageId` per
  attempt most likely makes the agent start a **new task**, so a retried step can run the agent's
  work — and bill for it — twice. The spec requires defaulting `messageId` to a uuid but does not
  say whether it must be fresh per attempt. Candidates: a fresh uuid (simplest, duplicates on
  retry); a deterministic `messageId` derived from workflow instance, task name, and attempt, giving
  agents a dedupe key; or a `tasks/get` probe before resend, which costs a round trip and needs a
  `taskId` the runner does not carry across attempts. **Not decided by this ADR** — it must be
  settled before the request path is written.

## Non-goals

Does not implement `message/stream`/`tasks/resubscribe` or SSE aggregation. Does not implement
push-notification configuration, task cancellation, or the authenticated extended card. Does not
verify agent-card JWS signatures. Does not resolve retry idempotency. Does not change
`dws-orchestrator`, the OWS error taxonomy, or any existing runner's behavior.
