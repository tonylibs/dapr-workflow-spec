# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this package.

Root-level cross-cutting rules (commits, contract-change etiquette, per-package gate commands) are
in the repo root [`CLAUDE.md`](../CLAUDE.md). This file is dws-call-a2a-specific idioms only.

`dws-call-a2a` is the **first Python package in this repository** — there is no prior Python
convention to inherit here. Everything in "Toolchain" below is this package's own decision, not
an established repo-wide standard (yet).

Design authority: [`docs/adr/0004-call-a2a-runner-design.md`](../docs/adr/0004-call-a2a-runner-design.md).
Read it before changing behavior here — this file only covers idioms and things the ADR doesn't
say (or says slightly differently than the shipped `a2a-sdk` actually behaves — see "ADR
verification notes" below).

## Toolchain

| Concern | Choice | Why |
|---|---|---|
| Dependency/venv management | [`uv`](https://docs.astral.sh/uv/) | Single fast tool for venv + lockfile + running scripts; no separate `pip`/`venv`/`pip-tools` juggling. `uv sync` reproduces the exact pinned environment from `pyproject.toml` + `uv.lock`. |
| Lint + format | [`ruff`](https://docs.astral.sh/ruff/) | One tool replaces flake8 + isort + black; fast enough to run on every file edit. `ruff format` is Black-compatible. |
| Type checking | [`pyright`](https://microsoft.github.io/pyright/) | Faster than mypy on this codebase's size, and understands `a2a-sdk`'s protobuf-generated `a2a_pb2` module's dynamic attribute shape well enough in `standard` mode; `reportAttributeAccessIssue` is downgraded to a warning specifically for pb2 message attribute access, which is descriptor-driven and not fully statically visible either way. |
| Test framework | [`pytest`](https://docs.pytest.org/) + `pytest-asyncio` (`asyncio_mode = "auto"`) + `pytest-cov` | Standard choice; `asyncio_mode = "auto"` means `async def test_...()` functions don't need `@pytest.mark.asyncio` explicitly (kept on tests anyway here as a readability marker, not a requirement). |
| Mock-transport tier | [`respx`](https://lundberg.github.io/respx/) is a declared dev dependency, but tier (a) tests mostly use plain `httpx.MockTransport` directly | `httpx.MockTransport` gives full control via a single handler function and needs no extra API surface for this package's small number of mocked endpoints (card fetch, RPC call); `respx` is kept available for anyone adding pattern-based multi-route mocking later, but wasn't needed for what's here. |
| jq binding | [`jq` (PyPI) 1.12.0](https://pypi.org/project/jq/), i.e. `mwilliamson/jq.py` | Bundles `libjq` statically with prebuilt wheels for manylinux/macOS/Windows — no C toolchain needed in the runtime image, unlike binding options that compile against a system `libjq`. Chosen over shelling out to a system `jq` CLI binary (`subprocess`) to avoid a second runtime dependency in the Docker image and avoid string-escaping a jq program through a shell. |
| Web framework | FastAPI 0.141.1 + `uvicorn[standard]` | Matches ADR 0004 Decision 1 exactly: the a2a-sdk client is async end to end, so a WSGI framework would force `asyncio.run()` per request. `a2a-sdk[fastapi]` extra is pinned as a dependency (used only by the *test* tier's conformance agent, `test/conformance/sdk_agent.py`, to build a real SDK-backed server — the runner itself never imports `a2a.server.*`). |

Python version: `>=3.13`. Matches this repo's general "run the current stable major" convention
(Go 1.26, Node 24, Java 25, .NET 10) without pinning to the very newest (3.14) given the a2a-sdk
dependency tree's `culsans` backport shim is conditioned on `python_full_version < '3.13'` — 3.13
is the first version where that shim isn't pulled in at all.

## Commands

```shell
cd dws-call-a2a
uv sync                                    # create .venv, install pinned deps + dev deps
uv run ruff check .                        # lint
uv run ruff format --check .               # format check (use `ruff format .` to apply)
uv run pyright                             # type check
uv run pytest                              # all three test tiers
uv run pytest test/unit                    # tier (a) only -- unit tests, mock transports
uv run pytest test/fake_server             # tier (b) only -- hand-scripted fake agent, real HTTP via ASGITransport
uv run pytest test/conformance             # tier (c) only -- real a2a-sdk server components as the oracle
uv run pytest --cov=src/dws_call_a2a --cov-report=term-missing   # coverage
uv run pytest test/unit/test_config.py -k test_auth_scheme_basic  # single test
```

**Gate**: `uv run ruff check . && uv run ruff format --check . && uv run pyright && uv run pytest`.
All green as of this writing (163 tests, 94% line coverage on `src/`).

`uv` wasn't available as a system binary in the environment this package was authored in; the
commands above were verified against an equivalent `pip`-managed venv with pinned versions
matching `pyproject.toml` exactly. If `uv sync` behaves differently once actually run, trust `uv`'s
lockfile resolution over this note.

## ADR verification notes

Several claims in `docs/adr/0004-call-a2a-runner-design.md` were re-checked against the actual
pinned `a2a-sdk==1.1.2` package (introspected directly, not from memory or docs) before writing any
code against them, per the task brief's instruction to verify rather than trust ADR prose. Two
held exactly; three needed adjustment to how the code is structured (not to what the ADR decided —
its policy decisions all still hold, just not always via the API surface its prose implies).

### Held exactly as described

- `A2ACardResolver(httpx_client, base_url, agent_card_path=...)` and
  `get_agent_card(relative_card_path=None, http_kwargs=None, signature_verifier=None)` — signature
  matches Decision 1's description exactly, including the `signature_verifier` hook Decision 7
  mentions as a future follow-up.
- `create_client(agent: str | AgentCard, ...)` — does accept either form, and passing a bare URL
  does resolve the card via an internal, caller-uncontrolled `httpx.AsyncClient`, exactly as
  Decision 1 warns. (This package never calls `create_client`/`ClientFactory` at all in the end —
  see below — but the specific claim about bare-URL behavior was checked and holds.)

### `a2a.types` is 100% protobuf-generated; the pydantic vocabulary lives elsewhere

The ADR's Decision 3 table already anticipated a version split ("SDK symbols v1.0 — `SendMessage`;
`Role.ROLE_USER` is a Python enum *member name*"), but reading the prose, it's easy to assume the
pydantic models matching OWS's wire vocabulary (`Message.messageId`, `MessageSendParams`,
`TaskQueryParams`, `TaskState.working == "working"`) are still the primary, top-level `a2a.types`
surface. They are not, in `a2a-sdk` 1.1.2: `a2a.types` re-exports only protobuf-generated classes
from `a2a.types.a2a_pb2` (`SendMessageRequest.message`/`.configuration`, not `.params.message`;
`TaskState.TASK_STATE_WORKING`, not `"working"`). The pydantic models matching the wire vocabulary
live at `a2a.compat.v0_3.types` — a module the SDK itself brands, in its own README, as "backward
compatibility... foundational types and translation layers necessary for modern v1.0 clients and
servers to interoperate with legacy v0.3 A2A systems."

**Resolution used in this codebase** (`wire.py`): build and *validate* the JSON-RPC request/params
against `a2a.compat.v0_3.types.MessageSendParams`/`TaskQueryParams` (this doubles as the "no schema
validation at all" guard Decision 1 calls for — a `pydantic.ValidationError` on the evaluated
PARAMETERS *is* the guard), then convert to the core protobuf request via
`a2a.compat.v0_3.conversions.to_core_message`/`to_core_send_message_configuration` to actually drive
the `Client`. Results are converted back the same way (`to_compat_task`/`to_compat_message`) before
`model_dump(by_alias=True, ...)`, which is what actually produces the lowercase
`working`/`input-required`/`unknown` wire-form JSON the workflow's `switch` reads.

**Risk this introduces, not discussed by the ADR**: `a2a.compat.v0_3` is explicitly the SDK's own
"backward compatibility for legacy systems" module. Depending on it for *this runner's own*
request/response shape (not just for talking to an actually-legacy agent) means a future SDK major
that drops v0.3 compatibility entirely would remove the exact module this package's request
construction and result shaping is built on. There is no currently-visible deprecation signal for
this — flagging it here so a future SDK bump notices before it silently breaks.

### The JSON-RPC wire dialect is not uniformly lowercase — it depends on the agent's declared version

Decision 3 states "the wire form is lowercase" as if unconditional. In `a2a-sdk` 1.1.2, the SDK's
own `ClientFactory` picks between **two different JSON-RPC wire dialects** per agent, based on the
`protocolVersion` the agent's card declares for its JSONRPC interface:

- `protocolVersion` in `[0.3, 1.0)` (`a2a.compat.v0_3.versions.is_legacy_version`) →
  `CompatJsonRpcTransport`: `method: "message/send"`, states as `"working"` — the dialect Decision 2
  and Decision 3 describe, and the one every currently-deployed real-world A2A agent this package's
  authors are aware of actually speaks.
- `protocolVersion: "1.0"` (or unset) → the *modern* `JsonRpcTransport`: `method: "SendMessage"`
  (a gRPC-service-style RPC name, not a JSON-RPC-spec-shaped one), states as `"TASK_STATE_WORKING"`
  (the raw protobuf enum name via `google.protobuf.json_format.MessageToDict`).

**Resolution used in this codebase**: read "the wire form is lowercase" as a promise about what
this runner hands the *workflow* (the OWS-visible JSON in the `/run` response), not about the
literal bytes on the agent-facing wire. `client.py` lets the SDK pick whichever dialect actually
matches the target agent's declared version (mirroring exactly what `ClientFactory` would have
chosen, via the same `is_legacy_version` call), and `wire.py`'s result-shaping step normalizes
*either* dialect's result back to the same lowercase JSON before it ever reaches the workflow. This
is verified end to end by `test/conformance/test_conformance.py::test_modern_v1_0_dialect_against_real_sdk_server`,
which runs the *same* runner config against a real SDK server speaking the modern dialect and
asserts the workflow-visible JSON is identical to the legacy-dialect case.

One consequence: `client.py` does **not** use `create_client()`/`ClientFactory` at all, even though
both held up under verification. Two independent reasons forced constructing `BaseClient` +
a transport directly instead:

1. `ClientFactory`'s own JSONRPC-interface selection (`_find_best_interface`) would duplicate
   `card.select_jsonrpc_interface`'s Decision 3 selection, and its failure mode
   (`ValueError('no compatible transports found.')`) doesn't name which bindings *were* found, which
   Decision 3 explicitly asks for.
2. oauth2 auth (Decision 4) requires rewriting the RPC URL to go through Dapr's sidecar invoke proxy
   (`auth.rpc_target_url`) *before* the transport is built — `ClientFactory` has no hook for
   rewriting a destination URL it resolves from the card itself.

### `TaskState`'s `"unknown"` is real, but it's a fallback value, not a first-class enum member at the protobuf layer

Decision 3's claim that `"unknown"` is a real wire state holds — confirmed via
`a2a.compat.v0_3.conversions._CORE_TO_COMPAT_TASK_STATE`, whose `.get(core_state, TaskState.unknown)`
default is exactly what produces it. But the *mechanism* is that the protobuf `TaskState` enum's
zero/default value (`TASK_STATE_UNSPECIFIED`) is what maps to `"unknown"` on the wire — there is no
`TaskState.TASK_STATE_UNKNOWN` protobuf member. A state string genuinely outside the fixed wire
vocabulary entirely (neither a known state nor `"unknown"`) fails client-side pydantic validation
inside `CompatJsonRpcTransport.send_message` with a plain `pydantic.ValidationError`, which
`runner.py` catches and maps to `UpstreamError` (502) — a non-conformant agent's problem, not this
runner's. See `test/fake_server/test_fake_server_e2e.py::test_non_conformant_state_string_is_upstream_failure`.

### Two real bugs found only by tier (c) (the conformance job), not by unit tests or the hand-written fake server

Both are documented in the code (`test/conformance/sdk_agent.py`, `src/dws_call_a2a/client.py`) and
repeated here because they're exactly the kind of thing ADR 0004's Consequences section predicts
tier (c) exists to catch:

1. The official `AgentExecutor`/`TaskUpdater` framework rejects the *first* `TaskStatusUpdateEvent`
   for a brand-new task unless the executor has already enqueued an actual `Task` object first —
   `TaskUpdater.submit()`/`.start_work()` only ever enqueue status-update events, never a `Task`
   itself. Not documented anywhere obvious in the SDK; found by running the scripted conformance
   agent and reading `InvalidAgentResponseError: Agent should enqueue Task before
   TaskStatusUpdateEvent event`.
2. The modern (non-compat) `JsonRpcTransport` relies on the shared `httpx.AsyncClient`'s default
   `A2A-Version` header (normally set once by `ClientFactory.__init__` via
   `httpx_client.headers.setdefault(VERSION_HEADER, PROTOCOL_VERSION_CURRENT)`) to negotiate the
   "1.0" dialect with the server's version-validation middleware. Because this package builds its
   own `httpx.AsyncClient` directly (see above), that header was never set, and every modern-dialect
   call failed server-side with `Version mismatch: actual='0.3', expected='1.0'` until `client.py`
   was fixed to set the same default header `ClientFactory` sets.

## `messageId` dedupe (ADR 0004 Decision 8): the cross-component gap is closed, with one residual limitation

Decision 8's formula is `uuid5(DWS_NAMESPACE, f"{workflowInstanceId}/{taskName}/{iterationIndex}")`.
This package does **not** invent a workaround that pretends to be correct. `message_id.py` defines
the identifiers the formula needs as two optional inbound headers this runner is *willing* to read
— `X-Dws-Workflow-Instance-Id` / `X-Dws-Iteration-Index` — a documented, forward-compatible
extension point.

`derive_message_id` only *requires* `workflow_instance_id` to derive deterministically —
`iteration_index` folds into the seed when present, and a fixed `"-"` placeholder segment stands in
for it when absent, so the seed is always `f"{instance}/{task}/{segment}"` either way (stable across
retries, distinct across iterations, and distinct from a call that *does* carry a real index — see
`test/unit/test_message_id.py`). `"-"` is a safe sentinel because a real header value is always a
dot-joined string of non-negative integers (`DispatchContext.iterationIndexEncoded()`, e.g. `"0"`,
`"1.0"`), never empty and never `"-"`. This is deliberately *not* requiring both headers the way an
earlier version of this module did: a call not nested in any `for` loop (the common case — a
top-level `call: a2a` wrapped directly in `try`/`catch.retry`) genuinely, correctly never receives
`X-Dws-Iteration-Index` at all, and for that case `(workflow_instance_id, task_name)` alone is
already sufficient — no loop means no "many items share a task name" collision risk, so there is no
reason to give up on dedupe entirely just because one optional header is absent.

Only a missing `workflow_instance_id` falls back to a fresh random `uuid4()` per call, logging a
warning — deliberately *not* a deterministic fallback derived from just the task name, which would
be actively worse than random: it would produce the *same* `messageId` for every invocation of a
task with that name across every workflow instance and every iteration, causing an agent that does
dedupe on `messageId` to conflate unrelated invocations. A random fallback is honest about "dedupe
doesn't work yet" instead of silently pretending it does. In practice `workflow_instance_id` is
never actually missing (see below), so this branch is a defensive fallback, not a real operating
mode.

`dws-orchestrator`'s `CallServiceActivity` now sends both headers on every outbound call it
dispatches (verified against the code, not assumed):
`dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/activity/CallServiceActivity.java`'s
`httpExtensionOf` sets `X-Dws-Workflow-Instance-Id`/`X-Dws-Iteration-Index` from the new
`CallRequest.workflowInstanceId`/`iterationIndex` fields
(`.../workflow/activity/CallRequest.java`), which `InterpreterWorkflow.invokeStepService` populates
from `ctx.getInstanceId()` and `iterationIndexOf(variables)` respectively
(`.../workflow/InterpreterWorkflow.java`). Sent for every call kind on that activity's path
(openapi/grpc/asyncapi/a2a), not only a2a — harmless for the others. See
`docs/adr/0004-call-a2a-runner-design.md`'s Consequences section for the authoritative description
of this orchestrator-side change.

What that means for the two headers in practice, precisely — don't overstate either direction:

- `X-Dws-Workflow-Instance-Id` is **always populated**: it comes from
  `WorkflowContext.getInstanceId()`, which is never null, so this header is present on every call
  this package receives from `dws-orchestrator`.
- `X-Dws-Iteration-Index` is populated whenever the call task is nested inside ANY `for` loop,
  regardless of what jq variable name the author binds the index under (`for.at: index` or a custom
  name) — confirmed by re-reading the current, already-committed
  `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/DispatchContext.java` and
  `InterpreterWorkflow.dispatchFor`: the header is no longer derived by looking up a fixed jq
  variable name at all (that was an earlier cut of the mechanism, since replaced). `DispatchContext`
  now carries a structural `iterationPath` — the sequence of loop positions the current dispatch is
  nested inside, appended purely positionally by `dispatchFor` at each nesting level, independent of
  `for.at` naming entirely. `InterpreterWorkflowIntegrationTest.
  innerLoopWithCustomAtNameStillProducesDistinctIterationIndices` (in the already-committed
  orchestrator code) is the regression test proving this: a custom `at` name nested inside a
  default-named outer loop still produces four distinct encoded indices (`"0.0"`, `"0.1"`, `"1.0"`,
  `"1.1"`), not a missing header. So there is no "custom `at` name" collision case on this package's
  side — the only two states are "absent" (top-level, not nested in any `for` loop at all — handled
  correctly by the `"-"` placeholder above) and "present" (any `for` loop nesting, any `at` name,
  always a distinct dot-joined path). Don't assume a missing header means anything other than
  "genuinely not nested in a `for` loop" — that assumption now holds unconditionally.

## Env var contract

Exact names — do not deviate; `dws-controller`'s a2a compile branch is implemented against this
same contract independently. Three different runners now use three genuinely different naming
conventions for "the document/resource this step depends on and its integrity hash" — do not assume
any of them transfers to another:

| Package | Document/resource location | Integrity hash |
|---|---|---|
| `dws-call-openapi` | `DOCUMENT_URL` | `DOCUMENT_SHA256` (**required**, controller-computed) |
| `dws-call-asyncapi` | `DOC_ENDPOINT` | `DOC_SHA256` (**required**, controller-computed) |
| `dws-call-a2a` (this package) | `AGENT_CARD_URL` or `SERVER_URL` | `AGENT_CARD_SHA256` (**optional**, operator-set only — see below) |

### Target (exactly one of the first two)

- `AGENT_CARD_URL` — set when `with.agentCard` is declared. Resolved via `A2ACardResolver` at boot.
- `SERVER_URL` — set when `with.server` is declared instead. Used directly, no card fetch (ADR
  Decision 4 case 5).
- `AGENT_CARD_SHA256` — optional, **never set by the controller**. There is no field in the OWS DSL
  schema for a card checksum (verified against the actual SDK types: `AgentCard`/`ExternalResource`
  carry no hash field at all), unlike `dws-call-openapi`'s `DOCUMENT_SHA256`, which the controller
  computes automatically from a document it fetches at compile time. This is a manual, operator-set
  knob for a stricter boot check only (hash the fetched card bytes, fail boot on mismatch) — not
  something a DSL author can express. Do not copy `DOCUMENT_SHA256`'s "always present" assumption
  onto this field.
- Exactly one of `AGENT_CARD_URL`/`SERVER_URL` must be present; both or neither is a boot-time
  `ConfigError`.

### RPC

- `TASK` — **not in the original task brief's env var list, added here by this package**. Every
  sibling runner has an equivalent (`dws-call-openapi`'s `TASK`) for logging/`/healthz`, and this
  package additionally needs a task name for the `messageId` derivation (Decision 8). Since it's a
  static per-deployment value the controller already knows (the task's own name), it was added by
  analogy rather than left unresolved — flagged here since it wasn't in the literal list handed
  down for this package.
- `METHOD` — exactly `message/send` or `tasks/get` (ADR Decision 2). Validated defensively at boot
  even though the controller compile-time-rejects anything else.
- `PARAMETERS` — JSON-encoded string of the raw `with.parameters` DSL value (object or string form).
  **Always set now**: `dws-controller`'s `V1OrchestratorCompiler.a2aParameters` emits it
  unconditionally, using the `"{}"` literal when `with.parameters` is absent — `_parse_parameters`
  accepts an empty object, it is not a `ConfigError`.
  - **String form** (`StringParameters`) is an unmodified port of `dws-call-openapi`'s
    `evaluateParameters`: the whole configured string *is* one jq program, and its result *is* the
    entire params object. No `${...}` wrapper convention applies here — the ADR says the entire
    string form is a jq expression directly.
  - **Object form** (`ObjectParameters`) is *not* the same porting choice, and getting this wrong
    was a real bug caught after the fact: `dws-call-openapi`'s `HEADERS`/`QUERY` type every
    top-level value as a full jq-expression string by schema construction (compiled that way by
    `dws-controller`'s `V1OpenApiCompiler`), so combining every value into one jq program and
    running it once was correct there. a2a's `with.parameters` schema (`WithA2AParameters`) permits
    arbitrary literal JSON instead — `V1OrchestratorCompiler.a2aParameters` emits a **verbatim
    nested JSON dump** of `with.parameters`, not a flattened map of expression strings (the standard
    `message/send` shape is
    `{"message": {"role": "user", "parts": [{"kind": "text", "text": "${ .userQuestion }"}]}}` —
    `"role": "user"` is a literal sitting right next to an expression two levels deep). `config.py`'s
    `ObjectParameters.value: dict[str, object]` stores that structure verbatim, and
    `jq_eval.py`'s `evaluate_parameters` recursively walks it: a string leaf is evaluated as jq
    *only* if the **entire string**, anchored, is `${ ... }` (this repo's runtime-expression
    convention — see `dws-controller`'s `SECRET_REFERENCE` regex, which anchors the same way; *not*
    partial/template interpolation — a string that merely contains `${` without wrapping the whole
    value stays a literal, unchanged), and the jq result (any JSON type) replaces it; every other
    leaf (plain literal strings, numbers, booleans, `null`) passes through unchanged. The
    `env`/`$ENV` boot-time guard (`validate_no_env_access`) walks the same recursive structure, so
    an expression nested at any depth is still caught, not just at the top level.
- `AUTH_SCHEME`/`AUTH_USERNAME`/`AUTH_PASSWORD`/`AUTH_TOKEN`/`OAUTH_ENDPOINT`/`DAPR_HTTP_PORT` — same
  shape and names as `dws-call-openapi`'s generated-auth contract (`parseGeneratedAuth` in
  `dws-call-openapi/src/config/config.ts`), ported faithfully including HTTP Basic support the
  SDK's own `AuthInterceptor` doesn't provide (ADR Decision 4).
- `CARD_AUTH_SCHEME`/`CARD_AUTH_USERNAME`/`CARD_AUTH_PASSWORD`/`CARD_AUTH_TOKEN` — basic/bearer only
  (no oauth2 — the card fetch is plain HTTPS, not a Dapr service invocation, so there is no
  sidecar-middleware-injection path for it). Absent means "reuse `AUTH_*` for the card fetch too"
  (the documented two-endpoints-one-credential default).
- `INCLUDE_HISTORY` — boolean, default `false`. **Never set by the controller** — there is no OWS
  DSL field backing it (verified). Pure operator/debug knob (ADR Decision 6).
- `TIMEOUT` — duration string (`30s`, `1m`, `500ms`, or a bare millisecond integer), default `30s`.
- `PORT` — default `8080`.

### No `OUTPUT` env var

Every HTTP-shaped sibling runner (`dws-call-http`, `dws-call-openapi`, `dws-call-asyncapi`) has
`OUTPUT=replace|merge` because those call kinds have a data-flow output concept in the OWS schema
to shape. `A2AArguments` doesn't: the schema types `call: a2a`'s `with` as `method`/`agentCard`
or `server`/`parameters` only, with no output-shaping property, and Decision 6 already specifies a
single *fixed* runner policy (history-stripped JSON-RPC result, `input-required`/`auth-required` as
success) with no per-step override. Adding a `replace|merge` knob on top would contradict that fixed
policy rather than extend it, so this package doesn't invent one. `runner.py`'s module docstring
says the same thing in code.

## Style guide

- **DTOs**: frozen `@dataclass(slots=True)` for this package's own config types (`config.py`) —
  no pydantic for *this package's own* domain types, even though pydantic is an unavoidable
  transitive (and, via `a2a.compat.v0_3.types`, directly-used) dependency for speaking the A2A wire
  vocabulary itself. Keep that separation: pydantic models are the A2A-SDK's vocabulary, plain
  dataclasses are this package's own.
- **Errors**: three exception classes (`errors.py`) — `ConfigError` (boot-time, never HTTP-mapped,
  crashes the process), `RequestValidationError` (400), `UpstreamError` (502) — mapped once, at the
  FastAPI route boundary in `main.py`, by `isinstance` checks. Same shape as
  `dws-call-openapi/src/runner.ts`'s `BindingError`/`UpstreamError`/`TransportError` split, ported.
- **DI seams**: every function that talks to the network (`card.resolve_agent_card`,
  `client.build_client`, `app_state.build_app_state`, `main.create_app`) takes an optional
  `transport: httpx.AsyncBaseTransport | None = None` parameter. Production leaves it `None`;
  tests inject `httpx.MockTransport` (tier a) or `httpx.ASGITransport` pointed at a fake/real SDK
  agent app (tiers b/c). `runner.run`/`AppState.client` are typed against `protocols.RpcClient` (a
  `Protocol` with just `send_message`/`get_task`), not the concrete `a2a.client.BaseClient` class,
  so tests can supply a duck-typed double instead of a real SDK client.
- **Test layout**: `test/` (not `tests/`), matching `dws-call-openapi`'s convention for consistency
  across the repo's step-service packages — Python idiom leans `tests/`, but there was no strong
  reason to diverge from the established sibling convention for a package that otherwise shares so
  much shape with `dws-call-openapi`. Split into three tier directories (`test/unit`,
  `test/fake_server`, `test/conformance`) matching the three required tiers exactly, each with its
  own `pytest.mark` (`fake_server`, `conformance`; unit tests carry no custom marker). Every
  directory (including `test/` itself) has an `__init__.py` — without it, `test.conftest` collides
  with the CPython standard library's own bundled `test` package on `sys.path` and fails to import.
