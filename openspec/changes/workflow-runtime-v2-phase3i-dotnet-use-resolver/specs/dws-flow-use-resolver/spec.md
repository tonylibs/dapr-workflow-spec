## ADDED Requirements

### Requirement: Collect referenced names
The Flow host SHALL derive, from its own node definition, the set of `errors`, `retries` and
`timeouts` names its tasks reference: a task `timeout` that is a name, a `raise.error` that is a name,
and a `catch.retry` that is a name. Inline values SHALL NOT be collected.

#### Scenario: Named and inline timeouts
- **WHEN** one task has `timeout: fastCall` and another has `timeout: PT5S`
- **THEN** only `timeouts` / `fastCall` is collected

#### Scenario: No references
- **WHEN** no task references a name
- **THEN** the collected set is empty

### Requirement: Boot-time load from the version's configuration store
At startup, before registering its workflow, the Flow host SHALL fetch exactly the collected names from
the configuration store named by `DWS_FLOW_USE_STORE`, using keys `<kind>.<name>`, in one call, and hold
the entries in an immutable in-memory map.

#### Scenario: Only referenced keys requested
- **WHEN** the node references `timeouts.fastCall` and the store also holds `timeouts.other`
- **THEN** the store is asked for `timeouts.fastCall` only

#### Scenario: Store name from configuration
- **WHEN** `DWS_FLOW_USE_STORE` is `order-flow-3f9c2a-use`
- **THEN** the store call uses that store name

### Requirement: No references, no store
A node with no collected references, or whose definition has no `use` references at all, SHALL start
without calling the store and without requiring `DWS_FLOW_USE_STORE`.

#### Scenario: Node without references
- **WHEN** the node has no references and no store name is configured
- **THEN** startup succeeds and the store is never called

### Requirement: Missing or invalid entry fails the boot
The Flow host SHALL stop before registering its workflow with a message naming the kind and the name
when: a referenced name is absent from the store; a name is outside `[-._a-zA-Z0-9]`; an entry is not
a valid JSON object or, for `timeouts`, does not parse as a duration; or references exist and no store
name is configured.

#### Scenario: Unknown name
- **WHEN** the node references `timeouts` / `nope` and the store has no `timeouts.nope`
- **THEN** startup fails with a message naming `timeouts` and `nope`

#### Scenario: Name outside the charset
- **WHEN** the node references the name `bad name`
- **THEN** startup fails with a message naming the kind and `bad name`, without calling the store

#### Scenario: Unparseable entry
- **WHEN** `timeouts.fastCall` holds `{"after": "soon"}`
- **THEN** startup fails with a message naming `timeouts` and `fastCall`

#### Scenario: References without a store name
- **WHEN** the node references a name and `DWS_FLOW_USE_STORE` is unset
- **THEN** startup fails naming `DWS_FLOW_USE_STORE`, the kind and the name

### Requirement: One inline-or-name resolution rule
`UseResolver` SHALL be the only place that decides whether a reference is inline or a name. Given a
kind and a reference it SHALL return the inline value as written, or the named entry, or nothing for an
absent reference. An object is inline. For `errors` and `retries` a string is a name. For `timeouts` a
string beginning with an ISO-8601 duration designator (`P` followed by a digit or `T`, optionally
signed) is an inline duration; any other string is a name.

#### Scenario: Inline object returned as written
- **WHEN** the reference is `{"after": {"seconds": 10}}`
- **THEN** the same value is returned and the store is not consulted

#### Scenario: Name looked up
- **WHEN** the reference is `fastCall` and the loaded `timeouts.fastCall` is `{"after":{"seconds":10}}`
- **THEN** that entry is returned

#### Scenario: ISO string is inline for timeouts
- **WHEN** the reference is the string `PT5S` for kind `timeouts`
- **THEN** it is returned as written

#### Scenario: A string error reference is a name
- **WHEN** the reference is the string `PT5S` for kind `errors`
- **THEN** it is looked up as a name

### Requirement: Replay safety
Workflow code SHALL read named entries only from the in-memory map built at boot. Resolving a
reference SHALL NOT call the store, read the clock, or use randomness, and SHALL return equal results
on every replay.

#### Scenario: Replay with a named timeout
- **WHEN** a recorded run containing a named task `timeout` is replayed
- **THEN** the replay issues the same commands and returns the same result as the live run

### Requirement: Dapr store adapter
The Flow host SHALL read the store through one interface, `IUseStore`, with a Dapr configuration-API
adapter as its production implementation. The adapter SHALL NOT create a connection when there are no
references.

#### Scenario: Fake store in tests
- **WHEN** tests supply a fake `IUseStore`
- **THEN** the whole boot-time load and resolution is exercised without a sidecar
