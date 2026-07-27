## 1. Dependency & schema registry

- [ ] 1.1 Pin `com.networknt:json-schema-validator:2.0.0` as a direct dependency in `dws-orchestrator/pom.xml` (version property matching the transitive version so the classpath is unchanged)
- [ ] 1.2 Add a process-wide `SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)` to `WorkflowSupport` (built once in `init`, exposed like `jq()`/`mapper()`)
- [ ] 1.3 Confirm `./mvnw verify` is still green with the new dependency wired and no behavior change yet

## 2. Expression evaluation (`$context` + object form)

- [ ] 2.1 Add a `JqEvaluator` overload that binds named variables into the child scope via `Scope.setValue("context", node)` before `query.apply(...)`, so `$context` is readable in an expression
- [ ] 2.2 Add a helper that evaluates the object form of `from`/`as`: recurse objects/arrays, evaluate a string leaf as a jq expression only when it is `${ }`-wrapped, keep other scalars/strings as literals
- [ ] 2.3 Unit tests in `JqEvaluatorTest`: reading `$context`, string-form transform, and a `${ }`-gated object literal (wrapped value evaluated, plain string kept literal)

## 3. Validation & fault shape

- [ ] 3.1 Add `DataFlowException extends RuntimeException` carrying `taskName`, a `phase` (`INPUT`/`OUTPUT`/`EXPORT`), and a self-contained message (task, phase, and joined `instanceLocation: message` detail) so the detail survives the activity boundary
- [ ] 3.2 Add a `SchemaValidator` helper: resolve the inline schema from `SchemaUnion.getSchemaInline().getDocument()` to a `JsonNode`, validate via `registry.getSchema(schema).validate(instance)`, and map a non-empty `List<Error>` to a `DataFlowException`
- [ ] 3.3 Reject `schema.external` (`SchemaExternal`) and non-`json` `schema.format` with a `DataFlowException` naming the unsupported form (no silent skip)
- [ ] 3.4 Unit tests: conforming instance passes, non-conforming instance fails naming the offending field, external/unknown-format schema is rejected

## 4. Data-flow activity

- [ ] 4.1 Add `DataFlowActivity` (in-process `WorkflowActivity`, mirroring `EvaluateSetActivity`) reading the task's `Input`/`Output`/`Export` off `TaskBase` via `DefinitionLookup.taskByName(...)`
- [ ] 4.2 Input phase: `{taskName, rawInput, context}` → evaluate `input.from` (default identity), validate against `input.schema`, return the transformed input
- [ ] 4.3 Output phase: `{taskName, rawOutput, context}` → evaluate `output.as`, validate against `output.schema`, then evaluate `export.as` over the transformed output (current `$context` in scope), validate the new context against `export.schema`, and return `{data, context}`
- [ ] 4.4 Unit tests for both phases, including `export.as` writing a new context and a validation failure raising `DataFlowException`

## 5. Interpreter wiring

- [ ] 5.1 Thread a `context` `JsonNode` (initialised to an empty object) through `InterpreterWorkflow.execute()` alongside `data`, updated only from the output-phase activity result; ensure it is not passed to `ctx.complete(...)`
- [ ] 5.2 In `dispatch()`, guard on presence (D3): when the task declares `input`, call the input-phase activity before the body and run the body on the transformed input; when it declares `output`/`export`, call the output-phase activity after the body; otherwise behave exactly as today
- [ ] 5.3 Keep the pipeline strictly outside each existing branch body (`switch`/`set`/`call`/`run`/`wait`/`listen`/`emit` bodies unchanged)

## 6. Integration test & gate

- [ ] 6.1 Extend `InterpreterWorkflowIntegrationTest` (and/or a test definition) to exercise: input transform feeding a body, output transform flowing to the next task, `export.as` written by one task and read via `$context` by a later task, and a task with no data flow unchanged
- [ ] 6.2 Add an integration case where `output.schema` validation fails and assert the instance fails through the `taskFailed`/`instanceFailed` path with the offending field named
- [ ] 6.3 Run `./mvnw verify` in `dws-orchestrator/` and confirm green
