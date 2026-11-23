## MODIFIED Requirements

### Requirement: Task timeout

In `dws-flow`, a task with `timeout` whose child call has not completed in time SHALL fail with
`task '<name>' timed out after <ISO-8601 duration>`. The timeout can be an inline ISO-8601 duration
string, an inline duration object, or a name of a `use.timeouts` entry resolved through the
`UseResolver` loaded at boot. A named timeout SHALL use the same duration parsing, the same ISO-8601
rendering (`PT24H` for one day) and the same failure wording as an inline one.

A direct duration object MUST contain one or more of `days`, `hours`, `minutes`, `seconds` and
`milliseconds`, with a numeric value for each property. An `after` wrapper MUST contain only
`after`, and its value MUST parse as a duration under the same rules. Explicit numeric zero SHALL
remain valid. When the sequencer reaches a task whose inline timeout object violates these shape
or value-type rules, it SHALL fail with an `InvalidOperationException` whose message starts with
`config failure:` and explains the timeout error before it invokes the child or schedules a timer.

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
- **THEN** each form configures a five-second timeout without a store call

#### Scenario: Wrapped duration retains its value
- **WHEN** a task uses `{"after":{"minutes":1,"seconds":30}}` or `{"after":"PT1M30S"}` as its timeout
- **THEN** each form configures a 90-second timeout

#### Scenario: Explicit zero remains valid
- **WHEN** a task uses `{"seconds":0}` or `{"after":{"seconds":0}}` as its timeout
- **THEN** each form configures a zero-duration timeout, with the duration rendered as `PT0S` if the timer wins

#### Scenario: Invalid inline timeout fails before dispatch
- **GIVEN** a task has an inline timeout of `{}`, `{"after":{}}` or `{"after":{"second":10}}`
- **WHEN** the sequencer reaches the task
- **THEN** it fails with a configuration error identifying the missing duration fields or the unrecognized `second` property
- **AND** it neither dispatches the child nor schedules a timer

#### Scenario: A valid component cannot mask an invalid property
- **WHEN** the sequencer reaches a task whose timeout is `{"seconds":10,"second":1}` or `{"after":"PT10S","seconds":1}`
- **THEN** it fails with a configuration error identifying the unexpected property

#### Scenario: Null values cannot supply a duration
- **WHEN** the sequencer reaches a task whose timeout is `{"seconds":null}` or `{"after":null}`
- **THEN** it fails with a configuration error identifying the invalid field
