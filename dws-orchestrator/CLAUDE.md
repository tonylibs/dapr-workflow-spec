# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this package.

Root-level cross-cutting rules (commits, contract-change etiquette, per-package gate commands) are in
the repo root [`CLAUDE.md`](../CLAUDE.md). This file is dws-orchestrator-specific idioms only,
inferred from the actual source — not generic Spring Boot advice.

## Commands

```shell
cd dws-orchestrator
./mvnw verify                      # compile + test
./mvnw test -Dtest=JqEvaluatorTest # single test class
```

Windows: `mvnw.cmd` instead of `./mvnw`.

## Style guide

### Null handling: `Optional.ofNullable(...).map(...).orElse(...)` chains are the idiom, use them liberally

This package leans much more heavily on chained `Optional` than `dws-controller` does — not just as
a finder return type, but as the default way to thread a possibly-absent value through
transformations. This is the repo-wide standard going forward; keep using it here.

```java
// DataFlowPipeline.java:48-67 pattern — chain, don't nest ifs
Object resolved = Optional.ofNullable(task.getInput())
        .map(input -> jq.evaluate(input, data))
        .orElse(data);
```

Reserve explicit `if (x == null)` for early-exit guard clauses and interpreter-loop internals where
a chain would obscure control flow, not for general value threading:

```java
// WorkflowSupport.java:156 — guard clause, correctly left as an explicit check
if (after == null) {
    return Duration.ZERO;
}
```

### Loops: index-based `for` for interpreter/program-counter logic, StreamEx for transforms

The interpreter's replay semantics depend on an explicit program counter, so those loops stay
classic `for` — don't refactor them into a stream:

```java
// InterpreterWorkflow.java:155 — pc must be an explicit mutable index, streams can't replay this
for (int steps = 0; pc >= 0 && pc < items.size(); steps++) {
    // ...
}
```

For actual transforms, this package uses **StreamEx** (`one.util.streamex.StreamEx`), not plain
`java.util.stream.Stream` — match that, don't introduce plain streams alongside it:

```java
// CallServiceActivity.java:57-61
int status = StreamEx.iterate(failure, Objects::nonNull, CallServiceActivity::nextCause)
        .select(DaprException.class)
        .mapToInt(DaprException::getHttpStatusCode)
        .findFirst(s -> s > 0)
        .orElse(0);
```

### DI: constructor injection, beans wired via `@Bean` factory methods in `@Configuration`

Zero `@Autowired`/`@Inject` usages in this package. Beans that hand off to the reflectively-constructed
Dapr workflow runtime are built with `@Bean` methods, not `@Component` scanning:

```java
// WorkflowRuntimeConfig.java:27-70 pattern
@Configuration
public class WorkflowRuntimeConfig {
    @Bean
    public InterpreterWorkflow interpreterWorkflow(@Qualifier("stepDefinition") StepDefinition def) {
        return new InterpreterWorkflow(def);
    }
}
```

### DTO/model: records for request/response I/O; `OrchestratorProperties` is the one deliberate exception

Everything that crosses an activity or HTTP boundary is a `record` (`CallRequest`, `EmitRequest`,
`InstanceStatusResponse`). `OrchestratorProperties` (`@ConfigurationProperties`) is a plain mutable
class with hand-written getters/setters — that's required because Spring binds config props via
setters, not a style regression; don't "fix" it into a record.

`lombok` is a declared dependency but used exactly once, `@lombok.experimental.UtilityClass` on
`WorkflowSupport` — don't reach for `@Data`/`@Getter`/`@Builder` here, it's not this package's
convention.

### Exceptions: `@ResponseStatus` on the exception class, no `@ControllerAdvice`

No global exception handler exists. Each HTTP-facing exception is annotated directly and Spring
MVC's default handling takes care of the response:

```java
// NotFoundException.java:7
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
```

Errors crossing the Dapr activity boundary are a different, deliberate pattern — only the exception's
**message** survives that boundary, so `StepInvocationException`/`RaisedErrorException` bake structured
info (app-id, HTTP status, error JSON) into `getMessage()` as marker-prefixed strings, decoded back by
`WorkflowErrors.classify`/`WorkflowErrors.of` (`WorkflowErrors.java:79-105,140-167`). If you add a new
activity-boundary failure mode, follow this string-marker convention rather than inventing a new
exception-passing mechanism — Dapr won't preserve anything else.

### Tests: AssertJ only, no Hamcrest; framework-free unit tests separate from `*IntegrationTest`

```java
// WorkflowErrorsTest.java:3 — only AssertJ import, no Hamcrest
import static org.assertj.core.api.Assertions.assertThat;
```

Plain unit tests (`WorkflowErrorsTest`, `SchemaValidatorTest`) carry no Spring test annotations —
keep pure-logic tests framework-free. Tests that need the actual workflow runtime are named
`*IntegrationTest` (`InterpreterWorkflowIntegrationTest`) — don't blur that naming boundary.

### Comments: multi-paragraph rationale comments are the convention here, not a package-level style doc

There's no CONTRIBUTING.md for this package — non-obvious decisions (`WorkflowErrors`,
`DataFlowPipeline`, `WorkflowSupport`) are explained with class/method-level comments in place. When
you add a similarly non-obvious mechanism, document it the same way rather than a separate doc file.

### Formatting

Same Spotless + `googleJavaFormat` 1.32.0 config as `dws-controller`, auto-applied on
`process-sources` — no separate format command needed.
