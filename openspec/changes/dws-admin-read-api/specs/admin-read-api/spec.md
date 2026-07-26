## ADDED Requirements

### Requirement: List workflow definitions
The system SHALL expose `GET /workflows` returning one entry per workflow definition name, each
carrying that name's latest version (by `created_at`) and that version's status. The endpoint SHALL
be read-only and SHALL NOT mutate any state.

#### Scenario: Multiple names, latest version each
- **WHEN** the read model holds two versions of `order-flow` and one of `ship-flow`
- **THEN** the response contains exactly two items, one per name
- **AND** the `order-flow` item shows the version with the most recent `created_at` and its status

#### Scenario: Empty read model
- **WHEN** no workflow definitions exist
- **THEN** the response is `200` with `items` empty and `nextCursor` null

### Requirement: Workflow version history
The system SHALL expose `GET /workflows/:name` returning the full version history (every stored
version and status) for the named workflow, newest first.

#### Scenario: Known workflow with multiple versions
- **WHEN** `order-flow` has three stored versions
- **THEN** the response lists all three versions, ordered newest first, each with its status and `created_at`

#### Scenario: Unknown workflow name
- **WHEN** no workflow definition exists for the requested name
- **THEN** the system returns `404`

### Requirement: Workflow deployments
The system SHALL expose `GET /workflows/:name/deployments` returning the deployments recorded for
the named workflow, including each deployment's version, status, step services, orchestrator app id,
and drained-at timestamp when present.

#### Scenario: Workflow with deployments
- **WHEN** `order-flow` has deployment rows in the read model
- **THEN** the response lists those deployments with their version and status

#### Scenario: Unknown workflow name
- **WHEN** no workflow definition exists for the requested name
- **THEN** the system returns `404`

### Requirement: List workflow instances
The system SHALL expose `GET /instances` returning workflow instances, most-recent first, and SHALL
support optional filtering by workflow name and by status via query parameters. Filters SHALL be
combinable.

#### Scenario: No filter
- **WHEN** instances exist for multiple workflows
- **THEN** the response lists instances ordered most-recent first

#### Scenario: Filter by workflow and status
- **WHEN** the request supplies a workflow name and a status filter
- **THEN** the response contains only instances matching both the workflow name and the status

#### Scenario: Invalid pagination parameter
- **WHEN** the request supplies a `limit` outside the accepted range
- **THEN** the system returns `400`

### Requirement: Workflow instance detail
The system SHALL expose `GET /instances/:id` returning the full detail of a single workflow instance,
modeling lifecycle-derived timestamp fields (`startedAt`, `endedAt`) as nullable to reflect the
eventually-consistent read model.

#### Scenario: Known instance
- **WHEN** an instance with the requested id exists
- **THEN** the response returns that instance's workflow, version, app id, status, and started/ended timestamps

#### Scenario: Unknown instance id
- **WHEN** no instance exists for the requested id
- **THEN** the system returns `404`

### Requirement: Instance task events
The system SHALL expose `GET /instances/:id/tasks` returning the `task_events` for the instance,
ordered by event timestamp ascending.

#### Scenario: Instance with task events
- **WHEN** the instance has recorded task events
- **THEN** the response lists them ordered by timestamp ascending, each with task name, type, status, timestamp, and error when present

#### Scenario: Unknown instance id
- **WHEN** no instance exists for the requested id
- **THEN** the system returns `404`

### Requirement: Cursor pagination on list endpoints
Every list endpoint SHALL paginate using an opaque cursor and a bounded `limit`, returning a page
envelope of the form `{ items, nextCursor }` where `nextCursor` is null on the last page. Pages
SHALL be stable under concurrent ingestion (no skipped or duplicated items across pages).

#### Scenario: Paging through results
- **WHEN** more items exist than the requested `limit`
- **THEN** the response returns `limit` items and a non-null `nextCursor`
- **AND** requesting the next page with that cursor returns the following items with no overlap

#### Scenario: Last page
- **WHEN** the requested page reaches the final items
- **THEN** `nextCursor` is null

#### Scenario: Paging across a null sort-key boundary
- **WHEN** instances include rows with a null `startedAt` alongside rows with a non-null `startedAt`
- **THEN** paging traverses all rows exactly once across the null/non-null boundary

### Requirement: Validated DTOs and OpenAPI document
Every endpoint SHALL define a validated request and response DTO, and the service SHALL publish an
`@nestjs/swagger` OpenAPI document describing the full read API so a typed client can be generated
from it. Request DTOs SHALL reject unknown or out-of-range parameters.

#### Scenario: OpenAPI document served
- **WHEN** the service is running
- **THEN** an OpenAPI document is served covering all workflow and instance read endpoints and their DTO schemas

#### Scenario: Nullable fields documented
- **WHEN** the OpenAPI schema for an instance is inspected
- **THEN** lifecycle-derived timestamp fields are marked nullable
