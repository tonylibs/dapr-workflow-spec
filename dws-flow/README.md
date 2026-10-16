# dws-flow

`dws-flow` is the generic Dapr Workflow host for one immutable `kind: "flow"` single-node
definition. Every instance registers the constant Workflow name `Flow`; the pinned definition
determines the scope it represents. A `main`/`do` node's tasks run in order through the Sequencer
(`SequencerRunner`), which resolves each task's `then` directive and dispatches it to a Step activity
or Flow child workflow by task shape; `for`/`try-catch`/`fork` scopes are not implemented yet and fail
fast with a configuration-failure message.

## Named `use` entries

A task's `timeout` (and, once their consumers land, `raise.error` / `catch.retry`) may name an entry
instead of writing the value inline. Names are resolved once at boot from the version's Dapr
configuration store; nothing is read at run time.

| Setting | Meaning |
|---|---|
| `DWS_FLOW_USE_STORE` | Configuration store name. Required only when the node references a name; a node with only inline values starts without it and never contacts the sidecar. |

- Store keys are `<kind>.<name>` with kind `errors`, `retries` or `timeouts`; names match
  `[-._a-zA-Z0-9]+`. Each value is a JSON object, for example `timeouts.fastCall` =
  `{"after":{"seconds":10}}`.
- A `timeout` string beginning with an ISO-8601 designator (`P` followed by a digit or `T`, for
  example `PT5S`) is an inline duration, never a name; any other string is a name.
- Boot fails (process exits) when a referenced name is missing or empty in the store, is not a JSON
  object, is an invalid duration for `timeouts`, has characters outside the charset above, or when
  `DWS_FLOW_USE_STORE` is unset while the node references a name.

## Build and test

```bash
dotnet test test/dws-flow.Tests.csproj
```

Name the test project: a bare `dotnet test` in this directory only sees `dws-flow.csproj`, runs no
tests and still exits 0. CI (`.github/workflows/dws-flow.yml`) runs the same command. The version
is in `version.txt` (bumped by release-please, read by the build).

## Run locally with Dapr

Start the app against the hand-written main-flow fixture. It needs the standard local Dapr workflow
state store installed by `dapr init`.

```bash
DWS_FLOW_DEFINITION_PATH=../openspec/schemas/examples/flow-main.json \
  dapr run --app-id order-fulfillment-main --app-port 8080 \
    --dapr-http-port 3500 --dapr-grpc-port 50001 \
    -- dotnet run
```

After startup, `GET http://localhost:8080/healthz` returns `{ "status": "ok" }`. The process
exits during startup if its definition is missing or invalid.
