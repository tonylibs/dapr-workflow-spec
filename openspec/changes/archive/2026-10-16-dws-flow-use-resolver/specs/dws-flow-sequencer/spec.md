## MODIFIED Requirements

### Requirement: Task timeout
A task with `timeout` whose child call has not completed in time SHALL fail with
`task '<name>' timed out after <ISO-8601 duration>`. The `timeout` MAY be an inline duration (ISO-8601
string or duration object, as before) or a name of a `use.timeouts` entry resolved through the
`UseResolver` loaded at boot. A named timeout SHALL use the same duration parsing, the same ISO-8601
rendering (`PT24H`, never `P1D`) and the same failure wording as an inline one.

#### Scenario: Timer wins
- **WHEN** a child does not complete within the timeout
- **THEN** the node fails with the timed-out message

#### Scenario: Named timeout fires
- **WHEN** a task has `timeout: fastCall`, the loaded `timeouts.fastCall` is `{"after":{"seconds":10}}`, and the child does not complete within 10 seconds
- **THEN** the node fails with `task '<name>' timed out after PT10S`

#### Scenario: Named timeout renders one day as hours
- **WHEN** the loaded entry is `{"after":{"days":1}}` and the timer wins
- **THEN** the message ends with `timed out after PT24H`

#### Scenario: Inline timeout unchanged
- **WHEN** a task has `timeout: PT5S` or `timeout: {"seconds": 5}`
- **THEN** it behaves exactly as before, with no store call
