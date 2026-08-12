## ADDED Requirements

### Requirement: Configurable dws-admin base URL
The `dws-console` SHALL resolve the `dws-admin` base URL from the `VITE_DWS_ADMIN_URL` environment
variable through a single centralized builder and SHALL NOT hardcode any host. When the variable is
unset, the console SHALL fall back to a documented default.

#### Scenario: Base URL from environment
- **WHEN** `VITE_DWS_ADMIN_URL` is set and a route issues a read request
- **THEN** the request targets that base URL with the endpoint path appended

#### Scenario: Unset base URL falls back
- **WHEN** `VITE_DWS_ADMIN_URL` is not set
- **THEN** the console uses the documented default base URL rather than failing to build a request

### Requirement: Workflow list wired to GET /workflows
The workflow list route (`routes/workflows/index.tsx`) SHALL populate its table from
`GET /workflows` via a TanStack Query hook, mapping each `WorkflowSummaryDto` to the `WorkflowRow`
view model (`name`, `latestVersion`, `status`, and `updated` derived from `createdAt`).

#### Scenario: Live workflows rendered
- **WHEN** `dws-admin` returns workflow summaries
- **THEN** the list renders one row per returned item with its name, latest version, status badge, and derived updated time

#### Scenario: Empty read model
- **WHEN** `GET /workflows` returns an empty `items` array
- **THEN** the route renders the shared empty state instead of an empty table

### Requirement: Workflow detail wired to versions and deployments
The workflow detail route (`routes/workflows/$name.tsx`) SHALL populate its version-history table
from `GET /workflows/:name` and its deployment cards from `GET /workflows/:name/deployments`,
mapping `WorkflowVersionDto` and `DeploymentDto` to the `WorkflowVersion` and `WorkflowDeployment`
view models (deployment `orchestrator` from `orchestratorAppId`).

#### Scenario: Versions and deployments rendered
- **WHEN** both endpoints return data for a known workflow name
- **THEN** the version-history tab lists versions newest-first and the deployments tab renders one card per deployment with its orchestrator and step services

#### Scenario: Unknown workflow name returns 404
- **WHEN** `GET /workflows/:name` responds `404`
- **THEN** the route renders the existing not-found view rather than an error banner

### Requirement: Instance list wired to GET /instances with server-side filters
The instance list route (`routes/instances/index.tsx`) SHALL populate its table from
`GET /instances` via a TanStack Query hook, mapping `InstanceSummaryDto` to `InstanceRow`
(`id` from `instanceId`), and SHALL pass the selected workflow and status filters as query
parameters included in the query key so a filter change refetches.

#### Scenario: Filtered instance list
- **WHEN** a workflow filter and a status filter are selected
- **THEN** the request includes those parameters and the table renders only the returned matching instances

#### Scenario: No instances match
- **WHEN** `GET /instances` returns an empty `items` array for the active filters
- **THEN** the route renders the shared empty state

### Requirement: Instance detail wired to summary and task events
The instance detail route (`routes/instances/$id.tsx`) SHALL render its header from
`GET /instances/:id` (`orchestrator` from `appId`) and its task timeline from
`GET /instances/:id/tasks`. The adapter SHALL group task events by task name into one timeline row
per task, deriving row status, offset, and duration, and SHALL leave retry/attempt/catch fields
unset when the API provides no source for them.

#### Scenario: Instance header and timeline rendered
- **WHEN** both endpoints return data for a known instance id
- **THEN** the header shows workflow, version, orchestrator app id, and started/ended timestamps, and the timeline shows one row per task with a status badge

#### Scenario: Task events lag behind
- **WHEN** `GET /instances/:id/tasks` returns an empty `items` array
- **THEN** the timeline renders the shared "task events not yet reported" empty state

#### Scenario: Unknown instance id returns 404
- **WHEN** `GET /instances/:id` responds `404`
- **THEN** the route renders the existing not-found view

### Requirement: Cursor pagination on list endpoints
The workflow list and instance list routes SHALL page through results using the admin cursor
envelope (`{ items, nextCursor }`) via TanStack Query infinite queries, requesting a bounded
`limit`, and SHALL wire the existing "Load more" control to fetch the next page, disabling it when
`nextCursor` is null.

#### Scenario: Load more fetches the next page
- **WHEN** a list response has a non-null `nextCursor` and the operator activates "Load more"
- **THEN** the next page is requested with that cursor and its items are appended without overlap

#### Scenario: Last page disables Load more
- **WHEN** a list response has a null `nextCursor`
- **THEN** the "Load more" control is disabled

### Requirement: Query-driven loading, empty, and error states
Every wired route SHALL derive its render state from TanStack Query status using the existing
`states.tsx` and `skeleton.tsx` components: pending → skeleton, empty result → empty state,
error → error banner (list) or not-found view (detail 404). The demo `StateSwitch` control SHALL be
removed from the wired routes.

#### Scenario: Loading state
- **WHEN** a query is pending
- **THEN** the route renders the shared skeleton rows rather than an empty or stale table

#### Scenario: dws-admin unreachable
- **WHEN** a list request fails with a transport or non-404 error
- **THEN** the route renders the shared error banner with a way to retry

#### Scenario: Bad pagination or filter parameter
- **WHEN** a list request responds `400`
- **THEN** the route renders the existing warn banner rather than crashing
