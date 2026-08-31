# dws-flow

`dws-flow` is the generic Dapr Workflow host for one immutable `kind: "flow"` single-node
definition. Every instance registers the constant Workflow name `Flow`; the pinned definition
determines the scope it represents. Phase 0 only validates and logs the scope and task count.

## Build and test

```bash
dotnet test test/dws-flow.Tests.csproj
```

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
