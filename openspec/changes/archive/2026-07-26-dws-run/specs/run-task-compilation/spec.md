## ADDED Requirements

### Requirement: Run subtypes map to distinct task kinds and images
`dws-controller` SHALL compile `run.shell` to a `StepService` of kind `RUN_SHELL` using
`images.runShell()`, `run.script` with `language: js` to kind `RUN_SCRIPT_JS` using
`images.runScriptJs()`, and `run.script` with `language: python` to kind `RUN_SCRIPT_PYTHON` using
`images.runScriptPython()`. The single `TaskKind.RUN` and `ImageCatalog.run()` MUST be replaced by
these three, so that a deployed step's runtime is identifiable from its `dws.io/step-type` label
alone — the controller has no persistence layer and answers every read from live cluster state.

#### Scenario: Shell task compiles to the shell image
- **WHEN** a definition contains a task with `run.shell`
- **THEN** the compiled `StepService` has kind `RUN_SHELL` and the `runShell` image reference
- **AND** its name is the kebab-cased task name

#### Scenario: JavaScript script task compiles to the js image
- **WHEN** a definition contains a task with `run.script` and `language: js`
- **THEN** the compiled `StepService` has kind `RUN_SCRIPT_JS` and the `runScriptJs` image reference

#### Scenario: Python script task compiles to the python image
- **WHEN** a definition contains a task with `run.script` and `language: python`
- **THEN** the compiled `StepService` has kind `RUN_SCRIPT_PYTHON` and the `runScriptPython` image
  reference

#### Scenario: Step type is readable from cluster state
- **WHEN** a `run` step is deployed
- **THEN** its `dws.io/step-type` label distinguishes shell, js, and python steps from one another

### Requirement: Shell configuration is forwarded to the step environment
For `run.shell`, the compiler SHALL forward `command` to `COMMAND`, `arguments` to `ARGUMENTS` as a
JSON object, and `environment` to `ENVIRONMENT` as a JSON object. `arguments` MUST be serialized as
a JSON object preserving the definition's key order, because DSL 1.0.0 models it as a key/value map
and the shell image renders it as ordered `--key value` pairs.

#### Scenario: Command is forwarded
- **WHEN** a `run.shell` task sets `command: ./deploy.sh`
- **THEN** the compiled env contains `COMMAND` = `./deploy.sh`

#### Scenario: Arguments are forwarded as an ordered JSON object
- **WHEN** a `run.shell` task sets `arguments` with `env` then `region`
- **THEN** the compiled env contains `ARGUMENTS` as a JSON object with `env` before `region`

#### Scenario: Environment is forwarded as a JSON object
- **WHEN** a `run.shell` task sets `environment` with `API_TOKEN`
- **THEN** the compiled env contains `ENVIRONMENT` as a JSON object containing `API_TOKEN`

#### Scenario: Omitted optional fields produce no env entries
- **WHEN** a `run.shell` task sets only `command`
- **THEN** the compiled env contains `COMMAND` and no `ARGUMENTS` or `ENVIRONMENT` entry

### Requirement: Script configuration is forwarded to the step environment
For `run.script`, the compiler SHALL forward the inline `code` to `SCRIPT` and forward `arguments`
and `environment` exactly as for `run.shell`. The script's `language` MUST select the image and
MUST NOT be forwarded as an environment variable, since each image already embeds exactly one
runtime.

#### Scenario: Inline code is forwarded
- **WHEN** a `run.script` task sets `language: python` and inline `code`
- **THEN** the compiled env contains `SCRIPT` with that code verbatim

#### Scenario: Language selects the image, not the env
- **WHEN** a `run.script` task sets `language: js`
- **THEN** the compiled env contains no `LANGUAGE` entry
- **AND** the image reference is `runScriptJs`

### Requirement: Return type is forwarded with the DSL default
The compiler SHALL forward `run.return` to the `RETURN` environment variable using the DSL's
lowercase value (`stdout`, `stderr`, `code`, `all`, `none`). When `return` is unset, the compiler
SHALL forward `stdout`, matching the DSL default, so the deployed step's behavior is explicit in
its manifest rather than dependent on an image default.

#### Scenario: Explicit return is forwarded
- **WHEN** a `run` task sets `return: all`
- **THEN** the compiled env contains `RETURN` = `all`

#### Scenario: Unset return defaults to stdout
- **WHEN** a `run` task omits `return`
- **THEN** the compiled env contains `RETURN` = `stdout`

### Requirement: Unsupported run subtypes are rejected at compile time
The compiler SHALL throw a `CompilationException` naming the offending subtype for `run.container`
and `run.workflow`. It MUST NOT emit a `StepService` for either. Falling through to a
`StepService` with an empty environment is prohibited, because it defers a definition error to a
post-deployment failure with nothing pointing back at the definition.

#### Scenario: Container subtype is rejected
- **WHEN** a definition contains a task with `run.container`
- **THEN** compilation fails with a `CompilationException`
- **AND** the message states that `run: container` is not yet supported

#### Scenario: Workflow subtype is rejected
- **WHEN** a definition contains a task with `run.workflow`
- **THEN** compilation fails with a `CompilationException`
- **AND** the message states that `run: workflow` is not yet supported

#### Scenario: No partial stack is produced
- **WHEN** compilation fails on an unsupported subtype
- **THEN** no `StepService` is emitted for that task

### Requirement: Unsupported script forms are rejected at compile time
The compiler SHALL throw a `CompilationException` when a `run.script` task declares a `language`
other than `js` or `python`, and when it supplies an external `source` instead of inline `code`.
Both messages MUST identify what was rejected so the author can correct the definition.

#### Scenario: Unsupported language is rejected
- **WHEN** a `run.script` task sets `language: ruby`
- **THEN** compilation fails with a `CompilationException` naming the unsupported language

#### Scenario: External script source is rejected
- **WHEN** a `run.script` task supplies `source` rather than `code`
- **THEN** compilation fails with a `CompilationException` stating that external script sources are
  not supported

#### Scenario: Argument names must be valid identifiers for script tasks
- **WHEN** a `run.script` task declares an argument name that is not a valid identifier in the
  target language
- **THEN** compilation fails with a `CompilationException` naming the invalid argument
- **AND** the failure occurs at post time rather than as a syntax error inside a deployed container

### Requirement: Run tasks are dispatched over the existing service-invocation path
`run` tasks SHALL be invoked through the same Dapr service-invocation path as `call` tasks: the
orchestrator resolves the target from the kebab-cased task name and invokes `POST /run`, reusing the
existing `CallServiceActivity` rather than a new activity. Reaching that activity requires an
explicit `run` branch in the interpreter's dispatch — a `run` task satisfies no other branch — so
`dws-orchestrator` SHALL recognize `run` tasks and MUST NOT fail them as an unsupported type. The
step-service contract and the `call` path MUST be unchanged.

#### Scenario: Routing is name-derived
- **WHEN** a workflow contains a `run` task named `syncInventory`
- **THEN** the orchestrator invokes Dapr app-id `sync-inventory` at `POST /run`
- **AND** it uses the same activity path it uses for `call` tasks

#### Scenario: A run task is dispatched rather than rejected
- **WHEN** the interpreter reaches a `run` task
- **THEN** it dispatches the task and the workflow instance continues
- **AND** it does not fail the instance with an unsupported-type error

#### Scenario: Lifecycle events label the task type correctly
- **WHEN** a `run` task starts or completes
- **THEN** the emitted `io.dws.task.*` event reports its task type as `run`
- **AND** not as `unknown`
