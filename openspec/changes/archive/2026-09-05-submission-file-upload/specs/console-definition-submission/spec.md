## MODIFIED Requirements

### Requirement: Definition editor is available to authenticated console users
The `dws-console` SHALL provide a dedicated workflow-definition editor route for an authenticated
operator. The route SHALL contain a writable raw-text buffer for a DSL 1.0 definition and a
submission control, and SHALL be reachable from the console's workflow navigation. The editor
SHALL obtain its draft text and selected source format from the persisted definition-draft
management capability.

#### Scenario: Operator opens the definition editor
- **WHEN** an authenticated operator selects the workflow-definition authoring entry point
- **THEN** the console renders the dedicated editor route with a writable DSL buffer and a submit
  control
