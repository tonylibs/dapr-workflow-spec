## ADDED Requirements

### Requirement: Environment-driven step configuration
The `dws-run` images SHALL be configured entirely from the environment, with no per-workflow code
and no configuration file, mirroring `dws-call-http`. The recognized variables are `TASK`, `PORT`,
`COMMAND`, `SCRIPT`, `ARGUMENTS`, `ENVIRONMENT`, `RETURN`, `OUTPUT`, and `TIMEOUT`. Any invalid or
missing required value MUST cause the process to fail at startup with a descriptive error rather
than at first invocation.

#### Scenario: Shell image requires COMMAND
- **WHEN** `dws-run-shell` starts without `COMMAND` set
- **THEN** the process exits non-zero with an error naming `COMMAND`

#### Scenario: Script images require SCRIPT
- **WHEN** `dws-run-script-js` or `dws-run-script-python` starts without `SCRIPT` set
- **THEN** the process exits non-zero with an error naming `SCRIPT`

#### Scenario: Valid configuration starts the server
- **WHEN** all required variables for the image are set to valid values
- **THEN** the server starts and `GET /healthz` responds `200`

### Requirement: ARGUMENTS is a JSON object
`ARGUMENTS` SHALL be a JSON object mapping argument names to values, matching DSL 1.0.0, which
models `run.shell.arguments` and `run.script.arguments` as key/value maps rather than lists. A
value that is not a JSON object MUST fail startup validation. An unset or empty `ARGUMENTS` MUST be
treated as no arguments.

#### Scenario: Object is accepted
- **WHEN** `ARGUMENTS` is `{"message":"hello","count":3}`
- **THEN** the configuration loads successfully with two arguments

#### Scenario: Array is rejected
- **WHEN** `ARGUMENTS` is `["hello","3"]`
- **THEN** the process exits non-zero with an error stating `ARGUMENTS` must be a JSON object

#### Scenario: Unset means no arguments
- **WHEN** `ARGUMENTS` is unset or empty
- **THEN** the configuration loads with no arguments

### Requirement: Shell renders arguments as flags
`dws-run-shell` SHALL render each entry of `ARGUMENTS` as a `--<key> <value>` pair appended to the
command, preserving the object's key order. Argument values MUST be passed as distinct argv entries
rather than concatenated into the command string, so that a value containing shell metacharacters
cannot alter the command's structure.

#### Scenario: Arguments become flags in key order
- **WHEN** `COMMAND` is `deploy.sh` and `ARGUMENTS` is `{"env":"prod","region":"eu"}`
- **THEN** the subprocess receives `--env prod --region eu` after the command, in that order

#### Scenario: Metacharacters in values do not alter the command
- **WHEN** an argument value is `; rm -rf /`
- **THEN** the value is passed as a single argv entry to the command
- **AND** it is not interpreted as an additional shell command

### Requirement: Script images inject arguments as in-scope variables
`dws-run-script-js` and `dws-run-script-python` SHALL make each `ARGUMENTS` entry available to the
script as an in-scope variable bound to its JSON value — a `const` binding for JavaScript, a
module-level global for Python — matching the DSL's framing of script arguments as values passed to
the script rather than as `argv` entries.

#### Scenario: JavaScript sees arguments as constants
- **WHEN** `dws-run-script-js` runs with `ARGUMENTS` `{"count":3}` and a script referencing `count`
- **THEN** the script observes `count` with the value `3`

#### Scenario: Python sees arguments as globals
- **WHEN** `dws-run-script-python` runs with `ARGUMENTS` `{"name":"ada"}` and a script referencing
  `name`
- **THEN** the script observes `name` with the value `"ada"`

#### Scenario: Argument values preserve their JSON types
- **WHEN** `ARGUMENTS` is `{"n":3,"flag":true,"items":[1,2]}`
- **THEN** the script observes a number, a boolean, and an array — not their string renderings

### Requirement: ENVIRONMENT extends the subprocess environment
`ENVIRONMENT` SHALL be a JSON object of string-to-string entries added to the spawned subprocess's
environment. A value that is not a JSON object of strings MUST fail startup validation. Entries
MUST extend rather than replace the service's own environment, so the runtime's `PATH` and
interpreter discovery keep working.

#### Scenario: Entries reach the subprocess
- **WHEN** `ENVIRONMENT` is `{"API_TOKEN":"abc"}`
- **THEN** the subprocess environment contains `API_TOKEN=abc`

#### Scenario: Existing environment is preserved
- **WHEN** `ENVIRONMENT` is set
- **THEN** the subprocess still inherits the service's `PATH`

#### Scenario: Non-string values are rejected
- **WHEN** `ENVIRONMENT` is `{"PORT":8080}`
- **THEN** the process exits non-zero with an error stating `ENVIRONMENT` must be a JSON object of
  strings

### Requirement: RETURN accepts exactly the DSL's process return types
`RETURN` SHALL accept exactly `stdout`, `stderr`, `code`, `all`, and `none` — the values of DSL
1.0.0's `ProcessReturnType` — and MUST default to `stdout` when unset, matching the DSL default.
Any other value MUST fail startup validation.

#### Scenario: Default is stdout
- **WHEN** `RETURN` is unset
- **THEN** the configuration resolves `RETURN` to `stdout`

#### Scenario: Each DSL value is accepted
- **WHEN** `RETURN` is any of `stdout`, `stderr`, `code`, `all`, or `none`
- **THEN** the configuration loads successfully

#### Scenario: Unknown value is rejected
- **WHEN** `RETURN` is `exitcode`
- **THEN** the process exits non-zero with an error listing the accepted values

### Requirement: OUTPUT and TIMEOUT follow the existing step conventions
`OUTPUT` SHALL accept `replace` or `merge` and default to `replace`, and `TIMEOUT` SHALL be a Go
duration string defaulting to the same value `dws-call-http` uses. Both MUST be validated at
startup with the same error style as the other step images.

#### Scenario: OUTPUT defaults to replace
- **WHEN** `OUTPUT` is unset
- **THEN** the configuration resolves `OUTPUT` to `replace`

#### Scenario: Invalid OUTPUT is rejected
- **WHEN** `OUTPUT` is `append`
- **THEN** the process exits non-zero with an error naming the accepted values

#### Scenario: TIMEOUT parses a Go duration
- **WHEN** `TIMEOUT` is `45s`
- **THEN** the configuration resolves a 45-second timeout

#### Scenario: Invalid TIMEOUT is rejected
- **WHEN** `TIMEOUT` is `45 seconds` or a non-positive duration
- **THEN** the process exits non-zero with a descriptive error

### Requirement: Three images from one codebase
The `dws-run` component SHALL produce three images — `dws-run-shell`, `dws-run-script-js`, and
`dws-run-script-python` — from a single Go module and a shared Go build stage. The images MUST
differ only in their final-stage base image and their exec command. Each image MUST be published to
`ghcr.io/tonylibs/<image-name>` on merge to `main`, and MUST NOT be pushed from a pull-request
build.

#### Scenario: Dockerfiles differ only in base and runtime selection
- **WHEN** the three image definitions are compared
- **THEN** the Go build stage is identical across all three
- **AND** the differences are confined to the final-stage base image and the setting that selects
  which interpreter the binary execs

#### Scenario: CI is path-filtered
- **WHEN** a commit changes files outside `dws-run/`
- **THEN** the `dws-run` workflow does not run

#### Scenario: Images publish only on main
- **WHEN** a pull request builds the images
- **THEN** the images are built but not pushed
- **WHEN** a commit merges to `main`
- **THEN** all three images are built and pushed to `ghcr.io/tonylibs/`
