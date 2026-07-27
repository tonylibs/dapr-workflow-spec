## 1. Dependency & schema registry

- [x] 1.1 Pin `com.networknt:json-schema-validator:2.0.0` as a direct dependency in `dws-orchestrator/pom.xml` (version property matching the transitive version so the classpath is unchanged)
- [x] 1.2 Add a process-wide `SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)` to `WorkflowSupport` (built once at class load rather than in `init` — it takes no per-workflow configuration — exposed as `schemaRegistry()` like `jq()`/`mapper()`)
- [x] 1.3 Confirm `./mvnw verify` is still green with the new dependency wired and no behavior change yet

## 2. Expression evaluation (`$context` + object form)

- [x] 2.1 Add a `JqEvaluator` overload that binds named variables into the child scope via `Scope.setValue("context", node)` before `query.apply(...)`, so `$context` is readable in an expression
- [x] 2.2 Add a helper that evaluates the object form of `from`/`as`: recurse objects/arrays, evaluate a string leaf as a jq expression only when it is `${ }`-wrapped, keep other scalars/strings as literals
- [x] 2.3 Unit tests in `JqEvaluatorTest`: reading `$context`, string-form transform, and a `${ }`-gated object literal (wrapped value evaluated, plain string kept literal)

## 3. Validation & fault shape

- [x] 3.1 Add `DataFlowException extends RuntimeException` carrying `taskName`, a `phase` (`INPUT`/`OUTPUT`/`EXPORT`), and a self-contained message (task, phase, and joined `instanceLocation: message` detail) so the detail survives the activity boundary
- [x] 3.2 Add a `SchemaValidator` helper: resolve the inline schema from `SchemaUnion.getSchemaInline().getDocument()` to a `JsonNode`, validate via `registry.getSchema(schema).validate(instance)`, and map a non-empty `List<Error>` to a `DataFlowException`
- [x] 3.3 Reject `schema.external` (`SchemaExternal`) and non-`json` `schema.format` with a `DataFlowException` naming the unsupported form (no silent skip)
- [x] 3.4 Unit tests: conforming instance passes, non-conforming instance fails naming the offending field, external/unknown-format schema is rejected

## 4. Data-flow activity

- [x] 4.1 Add the data-flow activities reading the task's `Input`/`Output`/`Export` off `TaskBase` via `DefinitionLookup.taskByName(...)` — shared logic in `DataFlowPipeline`, with a thin `DataFlowInputActivity`/`DataFlowOutputActivity` per phase (an activity is dispatched by class name and takes one input type, so the two phases cannot share one class); both registered in `WorkflowRuntimeBootstrap`
- [x] 4.2 Input phase: `{taskName, rawInput, context}` → evaluate `input.from` (default identity), validate against `input.schema`, return the transformed input
- [x] 4.3 Output phase: `{taskName, rawOutput, context}` → evaluate `output.as`, validate against `output.schema`, then evaluate `export.as` over the transformed output (current `$context` in scope), validate the new context against `export.schema`, and return `{data, context}`
- [x] 4.4 Unit tests for both phases, including `export.as` writing a new context and a validation failure raising `DataFlowException`

## 5. Interpreter wiring

- [x] 5.1 Thread a `context` `JsonNode` (initialised to an empty object) through `InterpreterWorkflow.execute()` alongside `data`, updated only from the output-phase activity result; ensure it is not passed to `ctx.complete(...)`
- [x] 5.2 In `dispatch()`, guard on presence (D3): when the task declares `input`, call the input-phase activity before the body and run the body on the transformed input; when it declares `output`/`export`, call the output-phase activity after the body; otherwise behave exactly as today
- [x] 5.3 Keep the pipeline strictly outside each existing branch body (`switch`/`set`/`call`/`run`/`wait`/`listen`/`emit` bodies unchanged)

## 6. Integration test & gate

- [x] 6.1 Extend `InterpreterWorkflowIntegrationTest` (and/or a test definition) to exercise: input transform feeding a body, output transform flowing to the next task, `export.as` written by one task and read via `$context` by a later task, and a task with no data flow unchanged
- [x] 6.2 Add an integration case where `output.schema` validation fails and assert the instance fails through the `taskFailed`/`instanceFailed` path with the offending field named
- [x] 6.3 Run `./mvnw verify` in `dws-orchestrator/` and confirm green (44 tests, BUILD SUCCESS; run locally with `-Djava.version=21` because only a JDK 21 is installed in this environment and the network policy blocks a JDK 25 download — CI still builds against the pinned Java 25)
