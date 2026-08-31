# dws-step

`dws-step` is the generic Dapr Workflow Activity host for one immutable `kind: "step"`
single-node definition. Every instance registers the constant Activity name `Step`; the pinned
definition determines which task it represents. Phase 0 only validates and logs the task kind.

## Build and test

```bash
./mvnw verify
```

Requires Java 25.

## Run locally with Dapr

Start the app against the hand-written HTTP-call fixture. It needs the standard local Dapr workflow
state store installed by `dapr init`.

```bash
./mvnw -DskipTests package
DWS_STEP_DEFINITION_PATH=../openspec/schemas/examples/step-call-http.json \
  dapr run --app-id reserve-items --app-port 8080 \
    --dapr-http-port 3500 --dapr-grpc-port 50001 \
    -- java -jar target/dws-step.jar
```

After startup, `GET http://localhost:8080/healthz` returns `{ "status": "ok" }`. The process
exits during startup if its definition is missing or invalid.
