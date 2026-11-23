## MODIFIED Requirements

### Requirement: Missing or invalid entry fails the boot

The `dws-flow` host SHALL stop before registering its workflow with a message naming the kind and the
name when any of the following conditions applies. A referenced name is absent from the store, a name
is outside `[-._a-zA-Z0-9]+`, an entry is invalid, or references exist and no store name is configured.
Entries MUST be valid JSON objects. For `timeouts`, the entry MUST also parse under the task timeout
duration rules. If timeout duration validation fails, the host SHALL report a `DefinitionLoadException`
with the prefix `use.timeouts '<name>' is not a valid entry:` and include the underlying reason.

#### Scenario: Unknown name
- **WHEN** the node references `timeouts` / `nope` and the store has no `timeouts.nope`
- **THEN** startup fails with a message naming `timeouts` and `nope`

#### Scenario: Name outside the charset
- **WHEN** the node references the name `bad name`
- **THEN** startup fails with a message naming the kind and `bad name`, without calling the store

#### Scenario: Unparseable entry
- **WHEN** `timeouts.fastCall` holds `{"after": "soon"}`
- **THEN** startup fails with a message naming `timeouts` and `fastCall`

#### Scenario: Empty or mistyped referenced timeout
- **GIVEN** a task references `fastCall` and `timeouts.fastCall` holds `{}`, `{"after":{}}` or `{"after":{"second":10}}`
- **WHEN** the Flow host loads its referenced use entries
- **THEN** startup fails before workflow registration with a message starting with `use.timeouts 'fastCall' is not a valid entry:`
- **AND** the message identifies the missing duration fields or the unrecognized `second` property

#### Scenario: Explicit zero in a referenced timeout
- **GIVEN** a task references `fastCall` and `timeouts.fastCall` holds `{"after":{"seconds":0}}`
- **WHEN** the Flow host loads the entry and the sequencer reaches the task
- **THEN** startup validation succeeds and the task uses a zero-duration timeout

#### Scenario: References without a store name
- **WHEN** the node references a name and `DWS_FLOW_USE_STORE` is unset
- **THEN** startup fails naming `DWS_FLOW_USE_STORE`, the kind and the name
