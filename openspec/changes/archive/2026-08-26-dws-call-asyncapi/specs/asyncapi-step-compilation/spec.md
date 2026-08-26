## ADDED Requirements

### Requirement: The controller compiles `call: asyncapi` to a binding-backed step service

The controller SHALL compile a `call: asyncapi` task into a `StepService` using the prebuilt
`dws-call-asyncapi` image. It SHALL fetch the referenced AsyncAPI document, pin it by content hash,
and pin `DOC_ENDPOINT`, `DOC_SHA256`, `OPERATION_ID`, `BINDING_NAME`, and `OPERATION` as the step's
environment. The step's Dapr app-id SHALL derive from the task name exactly as every other I/O task.

#### Scenario: AsyncAPI call becomes a step service
- **WHEN** a definition declares a `call: asyncapi` task named `publishOrder` over a supported
  protocol
- **THEN** the plan contains one `StepService` named `publish-order` using the `dws-call-asyncapi`
  image with `DOC_ENDPOINT`, `DOC_SHA256`, `OPERATION_ID`, `BINDING_NAME`, and `OPERATION` set

#### Scenario: Document is content-pinned
- **WHEN** the AsyncAPI document is fetched at compile time
- **THEN** `DOC_SHA256` in the step env equals the SHA-256 of the fetched bytes

### Requirement: The controller selects a Dapr binding type from the server protocol

The controller SHALL read the AsyncAPI document's first server `protocol` and `host` and the
operation's channel `address`, and select the Dapr binding component type: `kafka`→`bindings.kafka`,
`amqp`→`bindings.rabbitmq`, `mqtt`/`mqtt5`→`bindings.mqtt3`, `sqs`→`bindings.aws.sqs`,
`googlepubsub`→`bindings.gcp.pubsub`. It SHALL reject any other protocol at compile time with a
message naming the unsupported protocol and the supported set.

#### Scenario: Supported protocol selects its binding type
- **WHEN** the document's server protocol is `kafka`
- **THEN** the synthesized binding Component has type `bindings.kafka`

#### Scenario: Unsupported protocol is rejected
- **WHEN** the document's server protocol is `nats`, `pulsar`, or `solace`
- **THEN** the controller rejects the workflow before deployment with a supported-protocol message

### Requirement: The controller synthesizes a version-scoped binding Component

For each `call: asyncapi` step the controller SHALL synthesize one Dapr binding `Component`,
version-scoped and scoped to the step's app-id, carrying the broker connection host and the
destination derived from the channel address as binding-type-specific metadata. Broker credentials
SHALL be projected as `secretKeyRef` metadata from declared `use.secrets` names; the controller SHALL
NOT serialize credential values into the Component, the definition ConfigMap, or the step service.

#### Scenario: Binding Component is scoped and secret-backed
- **WHEN** an AsyncAPI call over `kafka` references a declared secret for its credentials
- **THEN** the synthesized Component is scoped to the step app-id and references the secret via
  `secretKeyRef` with no plaintext credential in any compiled artifact

#### Scenario: Channel address becomes the destination
- **WHEN** the resolved channel address is `orders`
- **THEN** the Component metadata carries `orders` as the binding-type-specific destination key

### Requirement: Existing definitions are unaffected

Definitions without a `call: asyncapi` task SHALL compile and deploy exactly as before, with no
binding Component synthesized and no new environment variables added to other steps.

#### Scenario: Non-AsyncAPI workflow is unchanged
- **WHEN** a definition contains only `call: http`, `call: openapi`, and `run` tasks
- **THEN** the plan contains no binding Component and the existing steps are byte-for-byte unchanged
