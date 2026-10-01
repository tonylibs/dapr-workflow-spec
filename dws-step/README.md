# dws-step

`dws-step` is the generic Dapr Workflow Activity host for one immutable `kind: "step"`
single-node definition. Every instance registers the constant Activity name `Step`; the pinned
definition determines which task it represents.

Phase 2a (this state) routes the activity by task kind and defines the input envelope and failure
contract. Every kind's handler still fails with a "not implemented yet" configuration failure; later
phases replace handlers without touching routing.

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

## Task kinds and routing

`StepActivity` resolves the pinned node's task kind and delegates to the `TaskHandler` registered for
it in `TaskHandlerRegistry` (`TaskKind` is the single source of supported kinds):

| Kind | Handler today |
|---|---|
| `set`, `switch`, `emit`, `raise`, `call`, `run` | fails: `step '<nodeId>' config failure: task kind '<kind>' is not implemented yet` (non-retryable) |

Startup validation (`SingleNodeDefinitionLoader`) fails the process with a `DefinitionLoadException` for:

- `wait` or `listen` — they run as `dws-flow` controller nodes, see
  [ADR 0006](../docs/adr/0006-wait-and-listen-as-flow-controllers.md);
- a task with no supported kind key, or with more than one.

## Activity envelope

The activity runs under the constant name `Step`. Input is `StepInput`; output is the new workflow
data. There are no other inputs, and no data-flow transforms (`input.from`, `output.as`, schemas).

| Field | Type | Meaning |
|---|---|---|
| `data` | JSON | current workflow data (JSON `null` if absent) |
| `variables` | map of name to JSON | scope-local variables, e.g. the caught error inside `catch` (empty if absent) |
| `workflowInstanceId` | string | **root** workflow instance ID |
| `iterationIndex` | string or `null` | opaque encoding of the enclosing `for` iteration(s); `null` outside a loop |

Output: a JSON document, the new workflow data. Both survive Dapr's workflow data converter
(`JacksonDataConverter`); unknown input fields are ignored, as in v1's `CallRequest`.

## Failure contract

Only a failure's message crosses the Dapr activity boundary, and `dws-orchestrator`'s
`WorkflowErrors.classify` classifies by that wording, so these messages are byte-identical to v1's.
`<task>` is the node's `nodeId` (equal to the Dapr app ID).

| Exception | Retryable | v1 category | Message | Marker v1 matches |
|---|---|---|---|---|
| `StepUpstreamException` | yes | communication | `step '<task>' upstream failure: <detail>` | `upstream failure:` |
| `StepConfigException` | no | runtime | `step '<task>' config failure: <detail>` | `config failure:` |
| `StepValidationException` | no | validation | `validation failed: <detail>` | `validation failed:` |

v1 matches markers with `contains`, in this order: `timed out after`, `data flow failed:`,
`validation failed:`, `config failure:`, then `step '` prefix / `upstream failure:`. Keep `<detail>`
free of another category's marker, or the failure is classified as that category.
Retryability is carried by the exception type; mapping it to a Dapr retry policy is the Flow's job.

## Expressions

`io.dws.step.expr.JqEvaluator` is a port (not a shared module) of `dws-orchestrator`'s evaluator:
jackson-jq in jq 1.6 mode, `${ .foo }` and bare `.foo` accepted, named variables bound as `$name`.
