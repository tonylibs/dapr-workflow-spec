## ADDED Requirements

### Requirement: The Step activity routes by the node's task kind
`dws-step` SHALL host exactly one node's task. When the Flow invokes the `Step` activity, the app SHALL
resolve the node's task kind from the pinned definition and invoke the handler registered for that kind.
Routing SHALL be independent of handler behavior so a handler can be implemented without changing routing.

#### Scenario: Each supported kind is routed
- **WHEN** the `Step` activity runs on a node whose task kind is `set`, `switch`, `emit`, `raise`, `call` or `run`
- **THEN** the handler registered for that kind is invoked, and no other handler is

#### Scenario: Handlers are not implemented yet
- **WHEN** any supported kind's handler runs in this change
- **THEN** it fails with a non-retryable configuration failure whose detail states the kind is not implemented yet

### Requirement: wait and listen are rejected at startup
`dws-step` SHALL fail startup, with a message naming ADR 0006, when the pinned task is `wait` or `listen`.
An unknown task kind SHALL continue to fail startup.

#### Scenario: wait definition
- **WHEN** the definition's task is `wait`
- **THEN** startup fails with a `DefinitionLoadException` whose message mentions ADR 0006

#### Scenario: listen definition
- **WHEN** the definition's task is `listen`
- **THEN** startup fails with a `DefinitionLoadException` whose message mentions ADR 0006

#### Scenario: unknown kind
- **WHEN** the definition's task has no supported kind key
- **THEN** startup fails with a `DefinitionLoadException`

### Requirement: Activity input and output envelope
The activity SHALL receive the current workflow data, the scope-local variables (for example a caught
error inside a `catch` block), the root workflow instance ID and the iteration index (nullable), and
SHALL return the new workflow data. No other inputs SHALL be added. Data-flow transforms
(`input.from`, `output.as`, schemas) are out of scope.

#### Scenario: Serialisation round trip
- **WHEN** an input envelope or an output value is serialised and deserialised with Dapr's workflow data converter
- **THEN** the result equals the original, including a null iteration index and empty variables
